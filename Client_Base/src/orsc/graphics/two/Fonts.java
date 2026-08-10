package orsc.graphics.two;

public class Fonts {

	static byte[][] fontData = new byte[50][];
	static int[] inputFilterCharFontAddr;
	public static String inputFilterChars;
	static boolean[] fontAntiAliased = new boolean[]{false, false, false, false, false, false, false, false, false,
		false, false, false};
	private static int tmpFontDataHead = 0;

	static {
		Fonts.inputFilterChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!\"\u00a3$%^&*()-_=+[{]};:\'@#~,<.>/?\\| ";
		Fonts.inputFilterCharFontAddr = new int[256];
		for (int code = 0; code < 256; ++code) {
			int index = Fonts.inputFilterChars.indexOf(code);
			if (index == -1) {
				index = 74;
			}

			Fonts.inputFilterCharFontAddr[code] = index * 9;
		}
	}

	public static int addFont(byte bytes[]) {
		fontData[tmpFontDataHead] = bytes;
		return tmpFontDataHead++;
	}

	/**
	 * Replaces an already-loaded font slot's data in place (e.g. with a generated modern font -
	 * see ClientPort#regenerateFonts()). Only ever called after all original fonts finished
	 * loading successfully, so a partial failure elsewhere never leaves a slot in a broken state.
	 */
	public static void setFont(int index, byte[] bytes, boolean antiAliased) {
		fontData[index] = bytes;
		fontAntiAliased[index] = antiAliased;
	}
}
