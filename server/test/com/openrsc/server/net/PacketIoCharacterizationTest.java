package com.openrsc.server.net;

import com.openrsc.server.login.ISAACCipher;
import com.openrsc.server.net.rsc.ISAACContainer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

/**
 * Phase 4 characterization test for the network framing layer (see
 * scripts/check_characterization_tests.sh and
 * docs/architecture/network-protocol.md).
 *
 * These tests PIN CURRENT BEHAVIOUR. They are not a statement of what the
 * protocol ought to be. Every expectation below was captured by running the
 * real encoder/decoder and recording the bytes; where the recorded behaviour
 * is surprising (the resync loop accepting a misdecoded opcode, the two
 * length conventions differing per direction), that is documented in the
 * assertion comment so a future change trips the test and forces a conscious
 * update rather than a silent drift.
 *
 * Framing families covered:
 *   - custom / RSCL-style (authenticClient == null or -1)
 *   - authentic 93-182 (opcode after the "last payload byte" quirk, no ISAAC)
 *   - authentic >=183 (ISAAC opcode encryption, quirk retained)
 *   - raw packets (opcode -1, buffer passed through untouched)
 *
 * Plain {@code main} on purpose: the project has no test framework and this
 * must run with the JDK 8 bundled in Portable_Windows/, exactly like
 * Client_Base/test ThemeParityTest.
 */
public final class PacketIoCharacterizationTest {

	private static int checks = 0;
	private static int failures = 0;

	private static final int[] SEED = {1, 2, 3, 4};

	public static void main(String[] args) {
		customFamily();
		authenticLegacyFamily();
		authenticIsaacFamily();
		rawFamily();
		clientSniffing();
		isaacResyncMisdecode();

		System.out.println((failures == 0 ? "OK" : "FAIL") + ": " + checks
			+ " packet-io characterization checks, " + failures + " failed.");
		if (failures != 0) {
			System.exit(1);
		}
	}

	// ------------------------------------------------------------------
	// Custom / RSCL-style framing
	// ------------------------------------------------------------------

	private static void customFamily() {
		// Server -> custom client: the length field is the FULL frame size
		// including the 2-byte length itself (buffer.capacity() == payload + 3).
		// The client subtracts 2 in Network_Base.readIncomingPacket, which is
		// why this convention works. Client -> server uses the opposite
		// convention (length excludes the header; see below).
		expectHex("custom encode op20 {1,2,3}",
			enc(custom(null, null), 20, new byte[]{1, 2, 3}), "00 06 14 01 02 03");

		// Client-style frame: 2-byte length = opcode + payload (4), then the
		// frame body. The decoder's -1 branch reads exactly that.
		Packet p = decode(customDec((short) -1, null), "00 04 14 01 02 03");
		expect("custom decode opcode", p == null ? -1 : p.getID(), 20);
		expectHex("custom decode payload", p == null ? Unpooled.buffer() : p.getBuffer(), "01 02 03");
	}

	// ------------------------------------------------------------------
	// Authentic 93-182: opcode follows the last payload byte
	// ------------------------------------------------------------------

	private static void authenticLegacyFamily() {
		// Quirk pinned: for frames < 160 bytes the LAST payload byte is written
		// BETWEEN the length byte and the opcode. Authentic clients depend on it.
		expectHex("182 encode op20 {1,2,3}",
			enc(custom((short) 182, null), 20, new byte[]{1, 2, 3}), "04 03 14 01 02");

		// >= 160 bytes: 2-byte length (0xA0 | high, low), opcode, full payload
		// with NO reordering.
		ByteBuf big = enc(custom((short) 182, null), 20, new byte[200]);
		expectHex("182 encode >=160 prefix", slice(big, 0, 3), "A0 C9 14");
		expect("182 encode >=160 total", big.readableBytes(), 203);

		Packet p = decode(customDec((short) 182, null), "04 03 14 01 02");
		expect("182 decode opcode", p == null ? -1 : p.getID(), 20);
		expectHex("182 decode payload (last byte restored)", p == null ? Unpooled.buffer() : p.getBuffer(), "01 02 03");

		// Round trip: the encoder's own output decodes back to the same packet.
		Packet rt = decode(customDec((short) 182, null), enc(custom((short) 182, null), 20, new byte[]{1, 2, 3}));
		expect("182 round-trip opcode", rt == null ? -1 : rt.getID(), 20);
		expectHex("182 round-trip payload", rt == null ? Unpooled.buffer() : rt.getBuffer(), "01 02 03");
	}

	// ------------------------------------------------------------------
	// Authentic >=183: same quirk plus ISAAC opcode encryption
	// ------------------------------------------------------------------

	private static void authenticIsaacFamily() {
		// Fixed seed {1,2,3,4}: first ISAAC value low byte is 0x3E, so
		// encode(67) = 67 + 62 = 129 = 0x81. Empty payload -> single byte frame.
		// One encoder channel throughout: each encode consumes the next value
		// of the out-cipher, so order matters (second value 0x37: 153 + 55 = 208).
		EmbeddedChannel e = enc235First();
		expectHex("235 encode heartbeat (op67, empty)",
			enc(e, 67, new byte[0]), "01 81");
		expectHex("235 encode op153 {AA,BB}",
			enc(e, 153, new byte[]{(byte) 0xAA, (byte) 0xBB}), "03 BB D0 AA");

		// >= 160: 2-byte length, encoded opcode, full payload, no reordering.
		ByteBuf big = enc(e, 187, new byte[200]);
		expectHex("235 encode >=160 prefix", slice(big, 0, 3), "A0 C9 2E");
		expect("235 encode >=160 total", big.readableBytes(), 203);

		// Round trip with mirrored ciphers (client out-seed == server in-seed,
		// as established by the login handshake).
		Packet p = decode(dec235(), "01 81");
		expect("235 decode heartbeat opcode", p == null ? -1 : p.getID(), 67);
		expect("235 decode heartbeat payload empty", p == null ? -1 : p.getBuffer().readableBytes(), 0);

		Packet p2 = decode(dec235Next(), "03 BB D0 AA");
		expect("235 decode op153", p2 == null ? -1 : p2.getID(), 153);
		expectHex("235 decode op153 payload", p2 == null ? Unpooled.buffer() : p2.getBuffer(), "AA BB");
	}

	// ------------------------------------------------------------------
	// Raw packets pass through untouched
	// ------------------------------------------------------------------

	private static void rawFamily() {
		expectHex("raw op-1 passthrough",
			enc(custom((short) 235, null), -1, new byte[]{9, 8}), "09 08");
	}

	// ------------------------------------------------------------------
	// Client sniffing: unknown client is classified from its first bytes
	// ------------------------------------------------------------------

	private static void clientSniffing() {
		// Custom client's first frame is the server-configs request: length 1,
		// opcode 19. The decoder emits packet 19 and pins the connection as
		// inauthentic (-1).
		EmbeddedChannel ch = customDec(null, null);
		Packet p = decode(ch, "00 01 13");
		expect("sniff configs-request opcode", p == null ? -1 : p.getID(), 19);
		expect("sniff marks client inauthentic", (int) att(ch).authenticClient.get(), -1);

		// Login frame: 2-byte length 0x0029 (41), opcode 0, 40-byte RSA block.
		// Emitted as opcode 0 with the 40-byte payload, also pinned as -1.
		byte[] login = new byte[43];
		login[0] = 0x00;
		login[1] = 41;
		login[2] = 0x00;
		EmbeddedChannel ch2 = customDec(null, null);
		Packet lp = decode(ch2, "00 29 00 " + zeros(40));
		expect("sniff login opcode", lp == null ? -1 : lp.getID(), 0);
		expect("sniff login payload", lp == null ? -1 : lp.getBuffer().readableBytes(), 40);
		expect("sniff login marks inauthentic", (int) att(ch2).authenticClient.get(), -1);
	}

	// ------------------------------------------------------------------
	// ISAAC resync loop: CURRENT behaviour accepts a misdecoded opcode
	// ------------------------------------------------------------------

	private static void isaacResyncMisdecode() {
		// A frame whose encoded opcode really is 67 (HEARTBEAT) but whose
		// payload length (3) is invalid for it. The decoder's resync loop keeps
		// consuming ISAAC values looking for the first (opcode, length) pair
		// that passes the 235 validity table, then EMITS THAT PAIR. Captured
		// behaviour: after 38 rejected tries it accepts the pair decoding to
		// opcode 46 and emits it with the scrambled payload. The intended
		// heartbeat is lost and a foreign opcode is delivered - this is the
		// documented ISAAC-desync fragility (technical-debt register), pinned
		// here so any change to the loop is a conscious one.
		EmbeddedChannel ch = dec235();
		Packet p = decode(ch, "04 CC 81 11 22");
		expect("resync does not deliver the intended opcode", p == null || p.getID() != 67, true);
		expect("resync emits the first table-valid misdecode", p == null ? -1 : p.getID(), 46);

		// The stream stays desynced afterwards: a genuine heartbeat from the
		// same client cipher surfaces as opcode 212, not 67.
		Packet p2 = decode(ch, "01 7A");
		expect("post-resync desync persists", p2 == null ? -1 : p2.getID(), 212);
	}

	// ------------------------------------------------------------------
	// helpers
	// ------------------------------------------------------------------

	private static ISAACContainer isaac() {
		ISAACCipher in = new ISAACCipher();
		in.setKeys(SEED);
		ISAACCipher out = new ISAACCipher();
		out.setKeys(SEED);
		return new ISAACContainer(in, out);
	}

	private static EmbeddedChannel custom(Short version, ISAACContainer c) {
		EmbeddedChannel ch = new EmbeddedChannel(new RSCProtocolEncoder());
		ch.attr(RSCProtocolEncoderMain.attachment).set(att(version, c));
		return ch;
	}

	private static EmbeddedChannel enc235First() {
		return custom((short) 235, isaac());
	}

	private static EmbeddedChannel customDec(Short version, ISAACContainer c) {
		EmbeddedChannel ch = new EmbeddedChannel(new RSCProtocolDecoder());
		ch.attr(RSCProtocolDecoder.attachment).set(att(version, c));
		return ch;
	}

	private static EmbeddedChannel dec235() {
		return customDec((short) 235, isaac());
	}

	/** A decoder one encode ahead: consumes the second ISAAC value first. */
	private static EmbeddedChannel dec235Next() {
		EmbeddedChannel ch = dec235();
		att(ch).ISAAC.get().decodeOpcode(0); // burn value 1 to align with the encoder's second encode
		return ch;
	}

	private static ConnectionAttachment att(Short version, ISAACContainer c) {
		ConnectionAttachment a = new ConnectionAttachment();
		if (version != null) {
			a.authenticClient.set(version);
		}
		if (c != null) {
			a.ISAAC.set(c);
		}
		return a;
	}

	private static ConnectionAttachment att(EmbeddedChannel ch) {
		return (ConnectionAttachment) ch.attr(RSCProtocolDecoder.attachment).get();
	}

	private static ByteBuf enc(EmbeddedChannel ch, int opcode, byte[] payload) {
		ch.writeOutbound(new Packet(opcode, Unpooled.wrappedBuffer(payload)));
		return ch.readOutbound();
	}

	private static Packet decode(EmbeddedChannel ch, String frameHex) {
		ch.writeInbound(Unpooled.wrappedBuffer(hexToBytes(frameHex)));
		return ch.readInbound();
	}

	private static Packet decode(EmbeddedChannel ch, ByteBuf frame) {
		ch.writeInbound(frame);
		return ch.readInbound();
	}

	private static ByteBuf slice(ByteBuf b, int from, int count) {
		return b.slice(from, count);
	}

	private static String zeros(int n) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < n; i++) {
			sb.append("00 ");
		}
		return sb.toString().trim();
	}

	private static byte[] hexToBytes(String hex) {
		String[] parts = hex.trim().split("\\s+");
		byte[] out = new byte[parts.length];
		for (int i = 0; i < parts.length; i++) {
			out[i] = (byte) Integer.parseInt(parts[i], 16);
		}
		return out;
	}

	private static String hex(ByteBuf b) {
		StringBuilder sb = new StringBuilder();
		while (b.readableBytes() > 0) {
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(String.format("%02X", b.readByte()));
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

	private static void expect(String name, boolean actual, boolean expected) {
		checks++;
		if (actual != expected) {
			failures++;
			System.out.println("FAIL " + name + ": expected " + expected + ", got " + actual);
		}
	}

	private static void expectHex(String name, ByteBuf actual, String expectedHex) {
		checks++;
		String actualHex = hex(actual);
		if (!actualHex.equals(expectedHex)) {
			failures++;
			System.out.println("FAIL " + name + ": expected [" + expectedHex + "], got [" + actualHex + "]");
		}
	}
}
