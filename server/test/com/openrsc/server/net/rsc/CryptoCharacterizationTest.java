package com.openrsc.server.net.rsc;

import java.math.BigInteger;

/**
 * Phase 4 characterization test for the login crypto primitives (see
 * scripts/check_characterization_tests.sh).
 *
 * XTEA is fully deterministic, so its known-answer vector is pinned exactly.
 * RSA keys are generated per run (Crypto.init writes client.pem/server.pem
 * into the working directory when absent), so only the invariants are pinned:
 * the modulus size, and that a login-shaped 64-byte block round-trips through
 * encrypt-and-decrypt unchanged.
 *
 * The 512-bit modulus assertion deliberately pins the CURRENT, WEAK key size
 * (technical-debt register, security row: "512-bit RSA"). When the key size is
 * upgraded this test must fail, and updating it is the conscious decision to
 * ship the new size - do not relax it to a range.
 *
 * Run with a writable working directory (the harness uses dist/char_test_work)
 * so generated .pem files stay out of the repository.
 */
public final class CryptoCharacterizationTest {

	private static int checks = 0;
	private static int failures = 0;

	public static void main(String[] args) throws Exception {
		xteaKnownAnswer();
		xteaTailBytesPassThrough();
		rsaKeySizeIs512();
		rsaLoginBlockRoundTrips();

		System.out.println((failures == 0 ? "OK" : "FAIL") + ": " + checks
			+ " crypto characterization checks, " + failures + " failed.");
		if (failures != 0) {
			System.exit(1);
		}
	}

	private static void xteaKnownAnswer() {
		int[] key = {0x01234567, 0x89ABCDEF, 0xFEDCBA98, 0x76543210};
		byte[] cipher = hexToBytes("66 08 87 89 0C E9 ED E7");
		byte[] plain = Crypto.decryptXTEA(cipher, 0, cipher.length, key);
		expect("xtea known-answer", new String(plain, java.nio.charset.StandardCharsets.UTF_8), "RuneWake");
	}

	private static void xteaTailBytesPassThrough() {
		// Only whole 8-byte blocks are decrypted; trailing bytes are copied
		// through untouched. The login path relies on this for the tail of a
		// block-aligned password buffer.
		int[] key = {0x01234567, 0x89ABCDEF, 0xFEDCBA98, 0x76543210};
		byte[] input = hexToBytes("66 08 87 89 0C E9 ED E7 41 42 43");
		byte[] out = Crypto.decryptXTEA(input, 0, input.length, key);
		expect("xtea tail length", out.length, 11);
		expect("xtea tail preserved", hex(new byte[]{out[8], out[9], out[10]}), "41 42 43");
	}

	private static void rsaKeySizeIs512() {
		Crypto.init();
		expect("rsa modulus bit length (weak, pinned on purpose)",
			Crypto.getPublicModulus().bitLength(), 512);
	}

	private static void rsaLoginBlockRoundTrips() throws Exception {
		// A 64-byte login block shaped like the authentic client's:
		// checksum 10, four ISAAC key ints, then a space-padded password.
		byte[] block = new byte[64];
		block[0] = 10;
		for (int i = 0; i < 4; i++) {
			block[1 + i * 4] = (byte) (0x11 * (i + 1));
			block[2 + i * 4] = (byte) (0x22 * (i + 1));
			block[3 + i * 4] = (byte) (0x33 * (i + 1));
			block[4 + i * 4] = (byte) (0x44 * (i + 1));
		}
		byte[] pw = "hunter2             ".getBytes("UTF-8");
		System.arraycopy(pw, 0, block, 17, 20);
		for (int i = 37; i < 64; i++) {
			block[i] = (byte) (i * 7);
		}

		BigInteger m = new BigInteger(1, block);
		expect("login block fits under the modulus", m.compareTo(Crypto.getPublicModulus()) < 0 ? 1 : 0, 1);

		byte[] cipher = m.modPow(Crypto.getPublicExponent(), Crypto.getPublicModulus()).toByteArray();
		byte[] dec = Crypto.decryptRSA(cipher, 0, cipher.length);
		expect("rsa decrypt length for a 64-byte block", dec.length, 64);
		expect("rsa login block round-trips",
			new BigInteger(1, dec).equals(m) ? 1 : 0, 1);
	}

	private static byte[] hexToBytes(String hex) {
		String[] parts = hex.trim().split("\\s+");
		byte[] out = new byte[parts.length];
		for (int i = 0; i < parts.length; i++) {
			out[i] = (byte) Integer.parseInt(parts[i], 16);
		}
		return out;
	}

	private static String hex(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < bytes.length; i++) {
			if (i > 0) {
				sb.append(' ');
			}
			sb.append(String.format("%02X", bytes[i]));
		}
		return sb.toString();
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
		if (!actual.equals(expected)) {
			failures++;
			System.out.println("FAIL " + name + ": expected \"" + expected + "\", got \"" + actual + "\"");
		}
	}
}
