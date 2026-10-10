package com.openrsc.server.net.rsc;

import com.openrsc.server.login.ISAACCipher;

/**
 * Phase 4 characterization test for the ISAAC keystream and the
 * encode/decode opcode symmetry (see
 * scripts/check_characterization_tests.sh).
 *
 * The keystream values below were captured from the current ISAACCipher
 * implementation with seed {1,2,3,4}. They pin the exact stream: any change to
 * the cipher (round count, mixing, result ordering) changes every client's
 * ability to talk to the server, so a diff here must be a deliberate protocol
 * decision, not a refactor side effect.
 */
public final class IsaacCipherCharacterizationTest {

	private static int checks = 0;
	private static int failures = 0;

	public static void main(String[] args) {
		keystreamIsStable();
		encodeDecodeAreSymmetric();
		maskStaysInByteRange();
		freshCipherWithSameSeedReplays();

		System.out.println((failures == 0 ? "OK" : "FAIL") + ": " + checks
			+ " ISAAC characterization checks, " + failures + " failed.");
		if (failures != 0) {
			System.exit(1);
		}
	}

	private static void keystreamIsStable() {
		ISAACCipher c = new ISAACCipher();
		c.setKeys(new int[]{1, 2, 3, 4});
		int[] expected = {
			0xDAF8863E, 0x74A5CB37, 0xAFD4ED73, 0x877C7C44,
			0x8FC83D9B, 0x606024AD, 0xFF7A07AA, 0x4FB9C0C7
		};
		for (int i = 0; i < expected.length; i++) {
			expectHex("keystream[" + i + "]", c.getNextValue(), expected[i]);
		}
	}

	private static void encodeDecodeAreSymmetric() {
		// The server derives in/out ciphers from the SAME client-chosen seed
		// (LoginPacketHandler), and the client does the same. One encode and
		// one decode consume one value from each side, so (decode ∘ encode)
		// must return the original opcode.
		ISAACCipher enc = fresh();
		ISAACCipher dec = fresh();
		int[] opcodes = {0, 19, 67, 153, 235, 255};
		for (int op : opcodes) {
			int wire = (op + enc.getNextValue()) & 0xFF;
			int back = (wire - dec.getNextValue()) & 0xFF;
			expect("round-trip opcode " + op, back, op);
		}
	}

	private static void maskStaysInByteRange() {
		ISAACCipher c = fresh();
		for (int i = 0; i < 512; i++) {
			int v = c.getNextValue() & 0xFF;
			if (v < 0 || v > 255) {
				expect("masked value in byte range (i=" + i + ")", v, 0);
				return;
			}
		}
		checks++;
	}

	private static void freshCipherWithSameSeedReplays() {
		ISAACCipher a = fresh();
		ISAACCipher b = fresh();
		for (int i = 0; i < 300; i++) { // crosses at least one internal refill
			expect("replay[" + i + "]", a.getNextValue(), b.getNextValue());
		}
	}

	private static ISAACCipher fresh() {
		ISAACCipher c = new ISAACCipher();
		c.setKeys(new int[]{1, 2, 3, 4});
		return c;
	}

	private static void expect(String name, int actual, int expected) {
		checks++;
		if (actual != expected) {
			failures++;
			System.out.println("FAIL " + name + ": expected " + expected + ", got " + actual);
		}
	}

	private static void expectHex(String name, int actual, int expected) {
		checks++;
		if (actual != expected) {
			failures++;
			System.out.println(String.format("FAIL %s: expected 0x%08X, got 0x%08X", name, expected, actual));
		}
	}
}
