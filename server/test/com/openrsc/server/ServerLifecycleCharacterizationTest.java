package com.openrsc.server;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Characterization tests for the boot -&gt; restart -&gt; reboot loop
 * (technical-debt register R4).
 *
 * The boot smoke test ({@code scripts/check_boot.sh}) only proves a cold
 * start answers HTTP probes before the process is killed; it never runs
 * {@code checkShutdown()}, so the daily/hourly restart path
 * ({@code closeProcess} -&gt; {@code shutdown}/{@code restart} -&gt;
 * {@code checkShutdown} -&gt; {@code stop()} -&gt; {@code start()}) was
 * exercised only by the scheduled events in production. This class drives
 * that path in-process and pins its CURRENT behaviour:
 *
 *   1. Construction starts nothing (no executor, no shutdown pending).
 *   2. {@code checkShutdown()} on an idle server is a no-op.
 *   3. {@code start()} boots the world: running, listed in
 *      {@code serversList}, NPCs populated (guards against the R2
 *      partial-load hazard where {@code World.load()} swallows its own
 *      exceptions), login executor up, game TCP port accepting.
 *   4. {@code restart(0)} is single-flight (second call, {@code shutdown},
 *      and {@code closeProcess} all return false while the event is
 *      pending); the countdown flips {@code shuttingDown} and
 *      {@code restarting} on the next game tick.
 *   5. {@code checkShutdown()} (the supervisor's role in {@code main()})
 *      stops and re-starts in place: still running, never left
 *      {@code serversList}, flags cleared, world repopulated, port
 *      re-bound. Repeated a second time to prove the cycle is repeatable
 *      (patch re-application, plugin reload, Netty rebind).
 *   6. {@code closeProcess(0, null)} shuts down without restarting: the
 *      server leaves {@code serversList}, the login executor stops, the
 *      port refuses connections.
 *   7. {@code start()} after a full stop (cold reboot) is legal and
 *      re-registers the server; a trailing {@code stop()} is a clean
 *      no-op the second time.
 *
 * Runs with CWD=server and a fixture conf one level up (like the
 * configuration and rate-limit tests). Needs {@code server/plugins.jar}
 * (PluginHandler opens {@code ./plugins.jar}) and a seeded SQLite database;
 * the harness script provides both. A developer-local
 * {@code server/local.conf} could override the fixture - same accepted
 * hazard as the other Server-constructing tests.
 *
 * Timing: three full world boots plus four stops (each stop waits out the
 * Netty graceful-shutdown quiet periods), so this test takes on the order
 * of two minutes - the price of exercising the real path.
 */
public class ServerLifecycleCharacterizationTest {

	private static int checks = 0;
	private static int failures = 0;

	// The Server constructor runs LogUtil, which reroutes BOTH System.out
	// (to INFO) and System.err (to ERROR) through log4j; capturing the real
	// stdout at class-init time (before main constructs the Server) keeps the
	// verdict lines clean and grep-able like the other test classes'.
	private static final PrintStream REAL_OUT = System.out;

	private static void expect(String what, boolean actual, boolean expected) {
		checks++;
		if (actual != expected) {
			failures++;
			REAL_OUT.println("FAILED: " + what + ": expected " + expected + ", got " + actual);
		}
	}

	private static void expect(String what, int actual, int expected) {
		checks++;
		if (actual != expected) {
			failures++;
			REAL_OUT.println("FAILED: " + what + ": expected " + expected + ", got " + actual);
		}
	}

	private static void expect(String what, long actual, long expected) {
		checks++;
		if (actual != expected) {
			failures++;
			REAL_OUT.println("FAILED: " + what + ": expected " + expected + ", got " + actual);
		}
	}

	private static void expect(String what, String actual, String expected) {
		checks++;
		if (!expected.equals(actual)) {
			failures++;
			REAL_OUT.println("FAILED: " + what + ": expected " + expected + ", got " + actual);
		}
	}

	private static void expectAtLeast(String what, int actual, int minimum) {
		checks++;
		if (actual < minimum) {
			failures++;
			REAL_OUT.println("FAILED: " + what + ": expected at least " + minimum + ", got " + actual);
		}
	}

	public static void main(String[] args) throws Exception {
		final Server server = new Server("../dist/char_test_work/lifecycle.conf");

		construction(server);
		idleCheckShutdownIsNoOp(server);
		boot(server);
		restartCycle(server, 1);
		restartCycle(server, 2);
		shutdownViaCloseProcess(server);
		coldReboot(server);

		REAL_OUT.println((failures == 0 ? "OK" : "FAIL") + ": " + checks
			+ " server lifecycle characterization checks, " + failures + " failed.");
		System.exit(failures == 0 ? 0 : 1);
	}

	private static void construction(Server server) {
		// The constructor wires the component graph but starts nothing;
		// Server.start() (or startServer) does.
		expect("server name from fixture conf", server.getName(), "RuneWakeLifecycleProbe");
		expect("not running after construction", server.isRunning(), false);
		expect("not shutting down after construction", server.isShuttingDown(), false);
		expect("not restarting after construction", server.isRestarting(), false);
		expect("no shutdown pending after construction", server.getTimeUntilShutdown(), -1);
		expect("executor not running after construction",
			server.getLoginExecutor().isRunning(), false);
		expect("not listed before start",
			Server.serversList.containsKey(server.getName()), false);
	}

	private static void idleCheckShutdownIsNoOp(Server server) {
		// The supervisor calls this every second; with no shutdown event it
		// must change nothing.
		server.checkShutdown();
		expect("idle checkShutdown leaves server stopped", server.isRunning(), false);
		expect("idle checkShutdown sets no flags", server.isShuttingDown(), false);
		expect("idle checkShutdown sets no restart flag", server.isRestarting(), false);
	}

	private static void boot(Server server) {
		server.start();
		assertBooted(server, "boot");
	}

	private static void assertBooted(Server server, String phase) {
		expect(phase + ": running", server.isRunning(), true);
		expect(phase + ": listed in serversList",
			Server.serversList.get(server.getName()) == server, true);
		// World.load() catches its own exceptions (debt R2), so a broken
		// world still "boots" - a populated NPC list proves the load really
		// happened rather than being swallowed.
		expectAtLeast(phase + ": world populated with NPCs",
			server.getWorld().getNpcs().size(), 1);
		expect(phase + ": login executor running",
			server.getLoginExecutor().isRunning(), true);
		expect(phase + ": no shutdown pending", server.getTimeUntilShutdown(), -1);
		expect(phase + ": game TCP port accepting",
			tcpConnectSucceeds(server.getConfig().SERVER_PORT), true);
	}

	private static void restartCycle(Server server, int cycle) throws InterruptedException {
		final String phase = "restart cycle " + cycle;
		// Single-flight: the first request wins; everything else refuses
		// while the countdown event is pending.
		expect(phase + ": restart accepted", server.restart(0), true);
		expect(phase + ": second restart refused", server.restart(0), false);
		expect(phase + ": shutdown refused while restart pending", server.shutdown(0), false);
		expect(phase + ": closeProcess refused while restart pending",
			Server.closeProcess(0, null), false);
		expectAtLeast(phase + ": shutdown countdown pending",
			(int) server.getTimeUntilShutdown(), 0);

		waitForShutdownFlag(server, phase);
		expect(phase + ": restarting flag set", server.isRestarting(), true);

		// Play the supervisor's role from main(): stop, then start again.
		server.checkShutdown();

		expect(phase + ": still running after restart", server.isRunning(), true);
		// The restart path never removes the server from the list (stop()
		// skips the removal when restarting) - this is what lets start()
		// bypass the duplicate-name guard.
		expect(phase + ": never left serversList",
			Server.serversList.get(server.getName()) == server, true);
		expect(phase + ": shutdown flag cleared", server.isShuttingDown(), false);
		expect(phase + ": restart flag cleared", server.isRestarting(), false);
		expectAtLeast(phase + ": world repopulated with NPCs",
			server.getWorld().getNpcs().size(), 1);
		expect(phase + ": login executor running again",
			server.getLoginExecutor().isRunning(), true);
		expect(phase + ": game TCP port accepting again",
			tcpConnectSucceeds(server.getConfig().SERVER_PORT), true);
	}

	private static void shutdownViaCloseProcess(Server server) throws InterruptedException {
		// The admin ::shutdown / daily-event path: broadcast-free (null
		// message), no restart.
		expect("closeProcess accepted", Server.closeProcess(0, null), true);
		expect("second closeProcess refused", Server.closeProcess(0, null), false);

		waitForShutdownFlag(server, "shutdown");
		expect("shutdown sets no restart flag", server.isRestarting(), false);

		server.checkShutdown();

		expect("stopped after shutdown", server.isRunning(), false);
		expect("removed from serversList after shutdown",
			Server.serversList.containsKey(server.getName()), false);
		expect("login executor stopped after shutdown",
			server.getLoginExecutor().isRunning(), false);
		expect("no shutdown pending after stop", server.getTimeUntilShutdown(), -1);
		expect("game TCP port refused after stop",
			tcpConnectSucceeds(server.getConfig().SERVER_PORT), false);
	}

	private static void coldReboot(Server server) {
		// start() after a full stop is legal: the name is free again, so the
		// server re-registers instead of taking the restart bypass.
		server.start();
		assertBooted(server, "cold reboot");

		server.stop();
		expect("stopped after final stop", server.isRunning(), false);
		expect("removed from serversList after final stop",
			Server.serversList.containsKey(server.getName()), false);
		// stop() on a stopped server returns early - safe to call twice.
		server.stop();
		expect("double stop stays stopped", server.isRunning(), false);
	}

	/**
	 * The restart/shutdown countdown is a FinitePeriodicEvent on the game
	 * tick (640 ms); with seconds=0 the flags flip on the first tick after
	 * the request. Poll rather than sleeping a fixed span.
	 */
	private static void waitForShutdownFlag(Server server, String phase) throws InterruptedException {
		final long deadline = System.currentTimeMillis() + 20_000L;
		while (!server.isShuttingDown() && System.currentTimeMillis() < deadline) {
			Thread.sleep(50);
		}
		expect(phase + ": shutdown flag flipped by countdown",
			server.isShuttingDown(), true);
	}

	private static boolean tcpConnectSucceeds(int port) {
		Socket socket = new Socket();
		try {
			socket.connect(new InetSocketAddress("127.0.0.1", port), 2000);
			return true;
		} catch (Exception e) {
			return false;
		} finally {
			try {
				socket.close();
			} catch (Exception ignored) {
			}
		}
	}
}
