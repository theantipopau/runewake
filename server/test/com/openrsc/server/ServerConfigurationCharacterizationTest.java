package com.openrsc.server;

import com.openrsc.server.database.DatabaseType;

import java.io.IOException;

/**
 * Phase 4 characterization test for server configuration loading (see
 * scripts/check_characterization_tests.sh).
 *
 * initConfig() is package-private and reads its files from the working
 * directory (connections.conf is mandatory; local.conf overrides the supplied
 * default file), so this test must run with the server/ directory as CWD.
 * DB_* environment variables take precedence over the files, so the harness
 * unsets them before launching; if one leaks in, the environment-derived
 * assertions below fail loudly rather than silently pass.
 *
 * The assertions pin the values shipped in server/default.conf and
 * server/connections.conf plus the two safety behaviours that protect a
 * production deploy: the SSL-mode default (never silently downgrade a remote
 * database) and the websocket/TCP port-collision disable.
 */
public final class ServerConfigurationCharacterizationTest {

	private static int checks = 0;
	private static int failures = 0;

	public static void main(String[] args) throws IOException {
		shippedDefaults();
		remoteHostGetsVerifyIdentity();
		portCollisionDisablesWebsockets();

		System.out.println((failures == 0 ? "OK" : "FAIL") + ": " + checks
			+ " configuration characterization checks, " + failures + " failed.");
		if (failures != 0) {
			System.exit(1);
		}
	}

	/** The checked-in default.conf/connections.conf parse to documented values. */
	private static void shippedDefaults() throws IOException {
		ServerConfiguration c = new ServerConfiguration();
		c.initConfig("default.conf");

		expect("configFile", c.configFile, "default.conf");
		expect("GAME_TICK", c.GAME_TICK, 640);
		expect("SERVER_PORT", c.SERVER_PORT, 43594);
		expect("WS_SERVER_PORT", c.WS_SERVER_PORT, 43494);
		expect("CLIENT_VERSION", c.CLIENT_VERSION, 10009);
		expect("MAX_PLAYERS", c.MAX_PLAYERS, 2000);
		expect("WORLD_NUMBER", c.WORLD_NUMBER, 1);
		expect("MAX_LOGINS_PER_SERVER_PER_TICK", c.MAX_LOGINS_PER_SERVER_PER_TICK, 5);
		expect("WANT_FEATURE_WEBSOCKETS (distinct ports)", c.WANT_FEATURE_WEBSOCKETS ? 1 : 0, 1);
		expect("SERVER_NAME strips inline comment", c.SERVER_NAME, "RuneWake");
		expect("DB_TYPE from connections.conf", c.DB_TYPE, DatabaseType.SQLITE);
		expect("DB_SSL_MODE defaults to PREFERRED for localhost", c.DB_SSL_MODE, "PREFERRED");
	}

	/** A remote db_host must never silently downgrade to a plaintext default. */
	private static void remoteHostGetsVerifyIdentity() throws IOException {
		ServerConfiguration c = new ServerConfiguration();
		c.initConfig("../dist/char_test_work/remote_host.conf");

		expect("remote DB_HOST", c.DB_HOST, "db.example.com:3306");
		expect("remote DB_SSL_MODE", c.DB_SSL_MODE, "VERIFY_IDENTITY");
	}

	/** Sharing ws_server_port with server_port must disable websockets. */
	private static void portCollisionDisablesWebsockets() throws IOException {
		ServerConfiguration c = new ServerConfiguration();
		c.initConfig("../dist/char_test_work/portclash.conf");

		expect("portclash SERVER_PORT", c.SERVER_PORT, 44000);
		expect("portclash WS_SERVER_PORT", c.WS_SERVER_PORT, 44000);
		expect("portclash disables websockets", c.WANT_FEATURE_WEBSOCKETS ? 1 : 0, 0);
	}

	private static void expect(String name, int actual, int expected) {
		checks++;
		if (actual != expected) {
			failures++;
			System.out.println("FAIL " + name + ": expected " + expected + ", got " + actual);
		}
	}

	private static void expect(String name, String actual, String expected) {
		checks++;
		if (actual == null || !actual.equals(expected)) {
			failures++;
			System.out.println("FAIL " + name + ": expected \"" + expected + "\", got \"" + actual + "\"");
		}
	}

	private static void expect(String name, DatabaseType actual, DatabaseType expected) {
		checks++;
		if (actual != expected) {
			failures++;
			System.out.println("FAIL " + name + ": expected " + expected + ", got " + actual);
		}
	}
}
