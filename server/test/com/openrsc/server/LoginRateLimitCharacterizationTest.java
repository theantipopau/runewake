package com.openrsc.server;

import com.openrsc.server.login.LoginExecutorProcess;
import com.openrsc.server.login.LoginRequest;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.RSCPacketFilter;

import java.io.PrintStream;

/**
 * Characterization tests for the login rate limits (technical-debt register S7).
 *
 * These pin CURRENT behaviour of the three throttles that bound an attacker
 * during login, plus the interactions between them:
 *
 *   1. LoginExecutor per-tick cap (MAX_LOGINS_PER_SERVER_PER_TICK, default 5):
 *      at most that many LoginRequest processes run per game tick; over-limit
 *      requests are DROPPED, not deferred; save and generic queues are
 *      unlimited; requests are rejected while the executor is not started.
 *
 *   2. RSCPacketFilter.shouldAllowLogin (MAX_LOGINS_PER_SECOND, default 2):
 *      inclusive bound on a 1000 ms sliding window; the attempt is recorded
 *      BEFORE the check, so denials extend the window; admin hosts bypass;
 *      127.0.0.1 is NOT exempt (unlike the connection limiter); a temporary
 *      IP ban blocks only when the caller passes doIpBans=true.
 *
 *   3. Password-guess counter (MAX_PASSWORD_GUESSES_PER_FIVE_MINUTES, default
 *      10): attempts accumulate per host and the LoginRequest guard is a
 *      "count >= max" comparison, so the 11th guess inside five minutes is
 *      the first one blocked.
 *
 * Two inherited carve-outs are pinned deliberately (register items S2/L5):
 * ipBanHost refuses to ban admin hosts and 127.0.0.1 outright, and
 * isHostIpBanned always answers false for admins, so the admin exemption
 * beats the IP-ban path in practice.
 *
 * Runs with CWD=server and a fixture conf one level up (like the
 * configuration test); the rate-limit knobs come from code defaults because
 * the tracked server/connections.conf and the fixture set none of them.
 * A developer-local server/local.conf could override them - same accepted
 * hazard as the configuration characterization test.
 *
 * Sleeps (~2.1 s total) exercise the real sliding windows; the assertions
 * use margins of hundreds of milliseconds against 1000 ms windows, and
 * denials keep the window alive, so the outcomes are not timing-sensitive
 * in practice.
 */
public class LoginRateLimitCharacterizationTest {

	private static int checks = 0;
	private static int failures = 0;

	// The Server constructor runs LogUtil, which reroutes BOTH System.out
	// (to INFO) and System.err (to ERROR) through log4j; capturing the real
	// stdout at class-init time (before main constructs the Server) keeps the
	// verdict lines clean and grep-able like the other test classes'.
	private static final PrintStream REAL_OUT = System.out;

	/** Counts processInternal invocations; never touches the database. */
	static final class CountingLogin extends LoginRequest {
		int processed;

		CountingLogin(final Server server, final String ip) {
			super(server, "RateLimitProbe", ip, 10010);
		}

		@Override
		protected void processInternal() {
			processed++;
		}

		@Override
		public void loginValidated(int response) { }

		@Override
		public void loadingComplete(Player loadedPlayer) { }
	}

	/** Non-login queue entry: must be unlimited per tick. */
	static final class CountingGeneric extends LoginExecutorProcess {
		int processed;

		@Override
		protected void processInternal() {
			processed++;
		}
	}

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

	private static void expect(String what, String actual, String expected) {
		checks++;
		if (!expected.equals(actual)) {
			failures++;
			REAL_OUT.println("FAILED: " + what + ": expected " + expected + ", got " + actual);
		}
	}

	public static void main(String[] args) throws Exception {
		final Server server = new Server("../dist/char_test_work/rate_limit.conf");

		configDefaults(server);
		construction(server);
		loginPerSecondWindow(server);
		denialsExtendWindowAndDecay(server);
		adminBypassesLoginLimit(server);
		localhostIsNotExemptFromLoginLimit(server);
		tempBanBlocksOnlyWhenEnforced(server);
		adminsAndLocalhostCannotBeIpBanned(server);
		passwordGuessCounter(server);
		executorTickCap(server);

		REAL_OUT.println((failures == 0 ? "OK" : "FAIL") + ": " + checks
			+ " login rate-limit characterization checks, " + failures + " failed.");
		System.exit(failures == 0 ? 0 : 1);
	}

	private static void configDefaults(Server server) {
		// The shipped fallbacks an operator gets when conf files set nothing.
		expect("default MAX_LOGINS_PER_SERVER_PER_TICK",
			server.getConfig().MAX_LOGINS_PER_SERVER_PER_TICK, 5);
		expect("default MAX_LOGINS_PER_SECOND",
			server.getConfig().MAX_LOGINS_PER_SECOND, 2);
		expect("default MAX_PASSWORD_GUESSES_PER_FIVE_MINUTES",
			server.getConfig().MAX_PASSWORD_GUESSES_PER_FIVE_MINUTES, 10);
	}

	private static void construction(Server server) {
		// The Server constructor must not start the login executor; Server.start() does.
		expect("executor not running after construction",
			server.getLoginExecutor().isRunning(), false);
		expect("server name from fixture conf", server.getName(), "RuneWakeProbe");
	}

	private static void loginPerSecondWindow(Server server) {
		final RSCPacketFilter filter = server.getPacketFilter();
		final String host = "10.9.9.1";
		// Inclusive bound: the attempt is recorded before the check, so
		// call N sees lps == N; exactly MAX_LOGINS_PER_SECOND succeed.
		expect("first login allowed", filter.shouldAllowLogin(host, false), true);
		expect("second login allowed", filter.shouldAllowLogin(host, false), true);
		expect("third login in same second denied", filter.shouldAllowLogin(host, false), false);
		expect("fourth login in same second denied", filter.shouldAllowLogin(host, false), false);
		// A different host is independent while the first is blocked.
		expect("other host unaffected", filter.shouldAllowLogin("10.9.9.2", false), true);
	}

	private static void denialsExtendWindowAndDecay(Server server) throws InterruptedException {
		final RSCPacketFilter filter = server.getPacketFilter();
		final String host = "10.9.9.11";
		expect("seed: first login allowed", filter.shouldAllowLogin(host, false), true);
		expect("seed: second login allowed", filter.shouldAllowLogin(host, false), true);
		expect("seed: third login denied", filter.shouldAllowLogin(host, false), false);
		// Denials are recorded too, so 700 ms later the window still holds
		// three attempts and a new one is denied.
		Thread.sleep(700);
		expect("denied attempt still counts 700 ms later", filter.shouldAllowLogin(host, false), false);
		// 1100 ms after the seed calls they have aged out of the 1000 ms
		// window; only the denied attempt from ~400 ms ago remains, so a
		// fresh attempt is allowed again (lps == 2, inclusive bound).
		Thread.sleep(400);
		expect("window decays after 1000 ms", filter.shouldAllowLogin(host, false), true);
	}

	private static void adminBypassesLoginLimit(Server server) {
		final RSCPacketFilter filter = server.getPacketFilter();
		final String host = "10.9.9.3";
		filter.addAdminHost(host);
		for (int i = 1; i <= 4; i++) {
			expect("admin login " + i + " allowed", filter.shouldAllowLogin(host, false), true);
		}
	}

	private static void localhostIsNotExemptFromLoginLimit(Server server) {
		// shouldAllowConnection exempts 127.0.0.1; shouldAllowLogin does not.
		final RSCPacketFilter filter = server.getPacketFilter();
		final String host = "127.0.0.1";
		expect("localhost first login allowed", filter.shouldAllowLogin(host, false), true);
		expect("localhost second login allowed", filter.shouldAllowLogin(host, false), true);
		expect("localhost third login denied", filter.shouldAllowLogin(host, false), false);
	}

	private static void tempBanBlocksOnlyWhenEnforced(Server server) {
		final RSCPacketFilter filter = server.getPacketFilter();
		final String host = "10.9.9.5";
		filter.ipBanHost(host, System.currentTimeMillis() + 60_000L, "characterization probe");
		expect("temp-banned host is ip banned", filter.isHostIpBanned(host), true);
		// LoginRequest.validateLogin calls shouldAllowLogin(ip, false), so
		// the ban gate here only bites when the caller opts in; the login
		// path enforces the ban separately via isHostIpBanned -> ACCOUNT_TEMP_DISABLED.
		expect("temp ban blocks when enforced", filter.shouldAllowLogin(host, true), false);
		expect("temp ban ignored when not enforced", filter.shouldAllowLogin(host, false), true);
	}

	private static void adminsAndLocalhostCannotBeIpBanned(Server server) {
		// S2: the hardcoded "Do not IP ban afmans!" carve-out, plus the
		// same protection for localhost: ipBanHost is a no-op for both and
		// isHostIpBanned always answers false for admins.
		final RSCPacketFilter filter = server.getPacketFilter();
		final String admin = "10.9.9.6";
		filter.addAdminHost(admin);
		filter.ipBanHost(admin, System.currentTimeMillis() + 60_000L, "characterization probe");
		expect("admin is never ip banned", filter.isHostIpBanned(admin), false);
		expect("banned-attempt admin still logs in", filter.shouldAllowLogin(admin, false), true);

		filter.ipBanHost("127.0.0.1", System.currentTimeMillis() + 60_000L, "characterization probe");
		expect("localhost is never ip banned", filter.isHostIpBanned("127.0.0.1"), false);
	}

	private static void passwordGuessCounter(Server server) {
		final RSCPacketFilter filter = server.getPacketFilter();
		final String host = "10.9.9.7";
		final int max = server.getConfig().MAX_PASSWORD_GUESSES_PER_FIVE_MINUTES;
		// LoginRequest's guard is "count >= max", so at 9 guesses the
		// attacker is still through and at 10 the next guess is blocked.
		for (int i = 1; i <= 9; i++) {
			filter.addPasswordAttempt(host);
		}
		expect("nine guesses counted", filter.getPasswordAttemptsCount(host), 9);
		expect("nine guesses still below guard",
			filter.getPasswordAttemptsCount(host) >= max, false);
		filter.addPasswordAttempt(host);
		expect("ten guesses counted", filter.getPasswordAttemptsCount(host), 10);
		expect("ten guesses trip the guard",
			filter.getPasswordAttemptsCount(host) >= max, true);
		// Counter is per host.
		expect("other host has no guesses", filter.getPasswordAttemptsCount("10.9.9.8"), 0);
	}

	private static void executorTickCap(Server server) throws InterruptedException {
		final LoginExecutor executor = server.getLoginExecutor();

		// Requests are rejected outright before start().
		expect("add before start rejected", executor.add(new CountingLogin(server, "10.9.9.20")), false);

		executor.start();

		// Batch 1: ten requests, only MAX_LOGINS_PER_SERVER_PER_TICK run.
		final CountingLogin[] batch1 = new CountingLogin[10];
		for (int i = 0; i < batch1.length; i++) {
			batch1[i] = new CountingLogin(server, "10.9.9.21");
			executor.add(batch1[i]);
		}
		Thread.sleep(300);
		int processed1 = 0;
		int processedTwice = 0;
		for (CountingLogin c : batch1) {
			processed1 += c.processed;
			if (c.processed > 1) processedTwice++;
		}
		expect("batch of 10 logins processes exactly the tick cap", processed1, 5);
		expect("no login processed twice", processedTwice, 0);

		// Over-limit requests are DROPPED, not deferred: waiting longer
		// (several more 50 ms executor cycles) processes nothing extra.
		Thread.sleep(200);
		int processedAfterWait = 0;
		for (CountingLogin c : batch1) {
			processedAfterWait += c.processed;
		}
		expect("excess logins are dropped, not deferred", processedAfterWait, 5);

		// The game tick reopens the budget (Server's tick loop calls
		// resetRequestsThisTick after every tick).
		executor.resetRequestsThisTick();
		final CountingLogin[] batch2 = new CountingLogin[10];
		for (int i = 0; i < batch2.length; i++) {
			batch2[i] = new CountingLogin(server, "10.9.9.22");
			executor.add(batch2[i]);
		}
		Thread.sleep(300);
		int processed2 = 0;
		for (CountingLogin c : batch2) {
			processed2 += c.processed;
		}
		expect("tick reset reopens the cap for the next batch", processed2, 5);

		// Non-login queues are unlimited: ten generic requests all run.
		final CountingGeneric[] generics = new CountingGeneric[10];
		for (int i = 0; i < generics.length; i++) {
			generics[i] = new CountingGeneric();
			executor.add(generics[i]);
		}
		Thread.sleep(200);
		int processedGenerics = 0;
		for (CountingGeneric g : generics) {
			processedGenerics += g.processed;
		}
		expect("generic queue is not tick-limited", processedGenerics, 10);

		executor.stop();
	}
}
