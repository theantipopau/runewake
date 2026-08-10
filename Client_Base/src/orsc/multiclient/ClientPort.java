package orsc.multiclient;

import com.openrsc.client.model.Sprite;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import orsc.Config;

public interface ClientPort {

	boolean drawLoading(int i);

	void showLoadingProgress(int percentage, String status);

	void initListeners();

	void crashed();

	void drawLoadingError();

	void drawOutOfMemoryError();

	boolean isDisplayable();

	void drawTextBox(String line2, byte var2, String line1);

	void initGraphics();

	void draw();

	void close();

	String getCacheLocation();

	Sprite getBattery(int level);

	int getBatteryPercent();

	boolean getBatteryCharging();

	Sprite getConnectivity(int level);

	String getConnectivityText();

	void resized();

	Sprite getSpriteFromByteArray(ByteArrayInputStream byteArrayInputStream);

	void playSound(byte[] soundData, int offset, int dataLength);

	void stopSoundPlayer();

	void drawKeyboard();

	void closeKeyboard();

	void setTitle(String title);

	void setIconImage(String serverName);

	/**
	 * Regenerates the game's 8 bitmap fonts from a modern system font, in place, matching the
	 * exact binary layout {@code Fonts.fontData}/{@code GraphicsController.plotCharacter}
	 * already expect (so no other code needs to change) - returns false (and must leave the
	 * original fonts completely untouched) if generation isn't supported on this platform or
	 * anything goes wrong, so callers can safely no-op on failure.
	 */
	boolean regenerateFonts();

	/**
	 * Loads a bundled image resource (from the res/ folder) and returns it as a Sprite scaled
	 * to the given dimensions, for use with the normal (unscaled) sprite draw calls. Returns
	 * null if image loading isn't supported on this platform or the resource can't be found -
	 * callers should fall back to existing behavior rather than assume this always succeeds.
	 */
	Sprite loadScaledImageSprite(String resourceName, int targetWidth, int targetHeight);

	static boolean saveHideIp(int preference) {
		FileOutputStream fileout;
		try {
			fileout = new FileOutputStream(Config.F_CACHE_DIR + File.separator + "hideIp.txt");

			OutputStreamWriter outputWriter = new OutputStreamWriter(fileout);
			outputWriter.write("" + preference);
			outputWriter.close();
			return true;
		} catch (Exception ignored) {
		}
		return false;
	}

	static int loadHideIp() {
		try {
			FileInputStream in = new FileInputStream(Config.F_CACHE_DIR + File.separator + "hideIp.txt");
			InputStreamReader inputStreamReader = new InputStreamReader(in);
			BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				sb.append(line);
			}
			in.close();

			return Integer.parseInt(sb.toString());
		} catch (Exception ignored) {
		}
		return 0;
	}

	static boolean saveCredentials(String creds) {
		FileOutputStream fileout;
		try {
			fileout = new FileOutputStream(Config.F_CACHE_DIR + File.separator + "credentials.txt");

			OutputStreamWriter outputWriter = new OutputStreamWriter(fileout);
			outputWriter.write(creds);
			outputWriter.close();
			return true;
		} catch (Exception ignored) {
		}
		return false;
	}

	static String loadCredentials() {
		try {
			FileInputStream in = new FileInputStream(Config.F_CACHE_DIR + File.separator + "credentials.txt");
			InputStreamReader inputStreamReader = new InputStreamReader(in);
			BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				sb.append(line);
			}
			in.close();

			return sb.toString();
		} catch (Exception ignored) {
		}
		return "";
	}

	static String loadIP() {
		try {
			FileInputStream in = new FileInputStream(Config.F_CACHE_DIR + File.separator + "ip.txt");
			InputStreamReader inputStreamReader = new InputStreamReader(in);
			BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				sb.append(line);
			}
			in.close();

			return sb.toString();
		} catch (Exception ignored) {
		}
		return "";
	}

	static int loadPort() {
		try {
			FileInputStream in = new FileInputStream(Config.F_CACHE_DIR + File.separator + "port.txt");
			InputStreamReader inputStreamReader = new InputStreamReader(in);
			BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				sb.append(line);
			}
			in.close();

			return Integer.parseInt(sb.toString());
		} catch (Exception ignored) {
		}
		return 0;
	}
}
