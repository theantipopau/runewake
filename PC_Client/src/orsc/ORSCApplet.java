package orsc;

import com.openrsc.client.model.Sprite;
import orsc.graphics.two.Fonts;
import orsc.multiclient.ClientPort;
import orsc.util.GenUtil;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.ByteArrayInputStream;

import static orsc.Config.S_ZOOM_VIEW_TOGGLE;
import static orsc.osConfig.C_LAST_ZOOM;

public class ORSCApplet extends Applet implements ComponentListener, ImageObserver, ImageProducer, ClientPort {
	private static final long serialVersionUID = 1L;
	public static int globalLoadingPercent = 0;
	public static String globalLoadingState = "";
	private static mudclient mudclient;
	static PacketHandler packetHandler;
	private final boolean m_hb = false;
	protected int resizeWidth;
	protected int resizeHeight;
	private Font createdbyFont = new Font("Helvetica", 1, 13);
	private Font copyrightFont2 = new Font("Helvetica", 0, 12);
	private Font loadingFont = new Font("TimesRoman", 0, 15);
	private Graphics loadingGraphics;
	private Image loadingLogo;
	private String loadingState = "Loading";
	boolean m_N = false;
	private String m_p = null;
	private int loadingPercent = 0;
	private int height = 384;
	private int width = 512;
	private DirectColorModel imageModel;
	private Image backingImage;
	private ImageConsumer imageProducer;
	private MouseHandler mouseHandler;
	private KeyHandler keyHandler;
	protected static ScaledWindow scaledWindow;
	private static BufferedImage game_image;
	private static Graphics2D g2dForGameImage;
	public static float oldRenderingScalar = 1.0f;

	public MouseHandler getMouseHandler() {
		return mouseHandler;
	}

	public KeyHandler getKeyHandler() {
		return keyHandler;
	}

	void addMouseClick(int button, int x, int y) {
		try {
		} catch (RuntimeException var6) {
			throw GenUtil.makeThrowable(var6, "e.Q(" + x + ',' + "dummy" + ',' + button + ',' + y + ')');
		}
	}

	private void drawCenteredString(Font var1, String str, int y, int x, Graphics g) {
		try {
			FontMetrics metrics = getFontMetrics(var1);
			g.setFont(var1);
			g.drawString(str, x - metrics.stringWidth(str) / 2, y + metrics.getHeight() / 4);
		} catch (RuntimeException var9) {
			throw GenUtil.makeThrowable(var9,
				"e.LE(" + (var1 != null ? "{...}" : "null") + ',' + (str != null ? "{...}" : "null") + ',' + y + ','
					+ true + ',' + x + ',' + (g != null ? "{...}" : "null") + ')');
		}
	}

	public final boolean drawLoading(int var1) {
		try {
			Graphics var2 = this.getGraphics();
			if (var2 != null) {
				this.loadingGraphics = scaledWindow.getGraphics();
				this.loadingGraphics.translate(mudclient.screenOffsetX, mudclient.screenOffsetY);
				this.loadingGraphics.setColor(Color.black);
				this.loadingGraphics.fillRect(0, 0, this.width, this.height);
				this.drawLoadingScreen("Loading...", 0, var1 ^ 103);
				return true;
			} else return false;
		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3, "e.ME(" + var1 + ')');
		}
	}

	@Override
	public boolean isDisplayable() {
		return super.isDisplayable();
	}

	private void drawLoadingScreen(String state, int percent, int var3) {
		try {
			try {
				int x = (this.width - 281) / 2;
				int y = (this.height - 148) / 2;
				this.loadingGraphics.setColor(Color.black);
				this.loadingGraphics.fillRect(0, 0, this.width, this.height);
				if (!this.m_hb) this.loadingGraphics.drawImage(this.loadingLogo, x, y, this);

				x += 2;
				this.loadingPercent = percent;
				y += 90;
				this.loadingState = state;
				if (var3 <= 97) mouseHandler.mouseReleased(null);

				this.loadingGraphics.setColor(new Color(132, 132, 132));
				if (this.m_hb) this.loadingGraphics.setColor(new Color(220, 0, 0));

				this.loadingGraphics.drawRect(x - 2, y - 2, 280, 23);
				this.loadingGraphics.fillRect(x, y, percent * 277 / 100, 20);
				this.loadingGraphics.setColor(new Color(198, 198, 198));
				if (this.m_hb) this.loadingGraphics.setColor(new Color(255, 255, 255));

				this.drawCenteredString(this.loadingFont, state, 10 + y, 138 + x, this.loadingGraphics);

				if (!this.m_hb) {
					this.drawCenteredString(this.createdbyFont, "Powered by Open RSC", 30 + y,
						x + 138, this.loadingGraphics);
					this.drawCenteredString(this.createdbyFont, "We support open source development.", y + 44, x + 138,
						this.loadingGraphics);
				} else {
					this.loadingGraphics.setColor(new Color(132, 132, 152));
					this.drawCenteredString(this.copyrightFont2, "We support open source development.", this.height - 20,
						138 + x, this.loadingGraphics);
				}

				if (null != this.m_p) {
					this.loadingGraphics.setColor(Color.white);
					this.drawCenteredString(this.createdbyFont, this.m_p, y - 120, x + 138, this.loadingGraphics);
				}
			} catch (Exception ignored) {
			}
		} catch (RuntimeException var7) {
			throw GenUtil.makeThrowable(var7,
				"e.FE(" + (state != null ? "{...}" : "null") + ',' + percent + ',' + var3 + ')');
		}
	}

	@Override
	public final void paint(Graphics var1) {
		try {
			if (mudclient != null) {
				mudclient.rendering = true;
				if (mudclient.getGameState() == 2 && this.loadingLogo != null)
					this.drawLoadingScreen(this.loadingState, this.loadingPercent, 126);
			}
		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3, "e.paint(" + (var1 != null ? "{...}" : "null") + ')');
		}
	}

	boolean reposition() {
		return false;
	}

	public final void showLoadingProgress(int percent, String state) {
		try {
			try {
				int x = (this.width - 281) / 2;
				x += 2;
				int y = (this.height - 148) / 2;
				this.loadingState = state;
				this.loadingPercent = percent;
				y += 90;
				int progress = percent * 277 / 100;
				this.loadingGraphics.setColor(new Color(132, 132, 132));
				if (this.m_hb) this.loadingGraphics.setColor(new Color(220, 0, 0));
				this.loadingGraphics.fillRect(x, y, progress, 20);
				this.loadingGraphics.setColor(Color.black);
				this.loadingGraphics.fillRect(progress + x, y, 277 - progress, 20);
				this.loadingGraphics.setColor(new Color(198, 198, 198));
				if (this.m_hb) this.loadingGraphics.setColor(new Color(255, 255, 255));
				this.drawCenteredString(this.loadingFont, state, 10 + y, 138 + x, this.loadingGraphics);
			} catch (Exception ignored) {
			}
		} catch (RuntimeException var8) {
			throw GenUtil.makeThrowable(var8, "e.EE(" + percent + ',' + (state != null ? "{...}" : "null") + ')');
		}
	}

	@Override
	public final void init() {
		try {
			mudclient = new mudclient(this);
			mudclient.packetHandler = new PacketHandler(mudclient);
			loadLogo();

			mouseHandler = new MouseHandler();
			keyHandler = new KeyHandler();

			this.addMouseListener(mouseHandler);
			this.addMouseMotionListener(mouseHandler);
			this.addKeyListener(keyHandler);
			this.setFocusTraversalKeysEnabled(false);
			this.addComponentListener(this);
			this.addMouseWheelListener(mouseHandler);
		} catch (RuntimeException var2) {
			throw GenUtil.makeThrowable(var2, "client.init()");
		}
	}

	public void loadLogo() {
		// Leaving this blank
	}

	private void startApplet() {
		try {
			System.out.println("Started applet");
			this.width = 512;
			this.height = 346;
			mudclient.startMainThread();
		} catch (RuntimeException var12) {
			throw GenUtil.makeThrowable(var12, "e.OE(" + 346 + ',' + Config.CLIENT_VERSION + ',' + 12 + ',' + 512 + ')');
		}
		try {
			// Don't load Discord on ARM
			if (!System.getProperty("os.arch").contains("aarch64")) {
				Discord.InitalizeDiscord();
			}
		} catch (Exception e) { }
	}

	@Override
	public final void stop() {
		try {
			try {
				mudclient.clientBaseThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} finally {
				System.exit(0);
			}
		} catch (RuntimeException var2) {
			throw GenUtil.makeThrowable(var2, "e.stop()");
		}
	}

	@Override
	public final void update(Graphics var1) {
		try {
			this.paint(var1);
		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3, "e.update(" + (var1 != null ? "{...}" : "null") + ')');
		}
	}

	private void updateControlShiftState(InputEvent var1) {
		try {
			int mod = var1.getModifiers();
			if (mudclient == null)
				return;
			mudclient.controlPressed = (mod & Event.CTRL_MASK) != 0;
			mudclient.shiftPressed = (mod & Event.SHIFT_MASK) != 0;
		} catch (RuntimeException e) {
			throw GenUtil.makeThrowable(e, "e.SE(" + (var1 != null ? "{...}" : "null") + ',' + "dummy" + ')');
		}
	}

	public final void start() {
		try {
			if (mudclient.threadState >= 0) {
				mudclient.threadState = 0;
			}
			startApplet();
		} catch (RuntimeException var2) {
			throw GenUtil.makeThrowable(var2, "e.start()");
		}
	}

	@Override
	public void componentShown(ComponentEvent e) {
	}

	void resizeMudclient(int width, int height) {
		mudclient.resizeWidth = width;
		mudclient.resizeHeight = height;
	}

	void resetArrowKeys() {
		mudclient.keyUp = false;
		mudclient.keyDown = false;
		mudclient.keyLeft = false;
		mudclient.keyRight = false;
	}

	@Override
	public void componentResized(ComponentEvent e) {
		mudclient.resizeWidth = e.getComponent().getWidth();
		mudclient.resizeHeight = e.getComponent().getHeight();
	}

	@Override
	public void componentMoved(ComponentEvent e) {
	}

	@Override
	public void componentHidden(ComponentEvent e) {
	}

	@Override
	public void initListeners() {
	}

	@Override
	public void crashed() {
	}

	@Override
	public void drawLoadingError() {
		Graphics g = this.getGraphics();
		if (g != null) {
			g.translate(mudclient.screenOffsetX, mudclient.screenOffsetY);
			g.setColor(Color.black);
			g.fillRect(0, 0, 512, 356);
			g.setFont(new Font("Helvetica", 1, 16));
			g.setColor(Color.yellow);
			byte var3 = 35;
			g.drawString("Sorry, an error has occured whilst loading " + Config.getServerNameWelcome(), 30, var3);
			g.setColor(Color.white);
			int var6 = var3 + 50;
			g.drawString("To fix this try the following (in order):", 30, var6);
			g.setColor(Color.white);
			var6 += 50;
			g.setFont(new Font("Helvetica", 1, 12));
			g.drawString("1: Try closing ALL open web-browser windows, and reloading", 30, var6);
			var6 += 30;
			g.drawString("2: Try clearing your web-browsers cache from tools->internet options", 30, var6);
			var6 += 30;
			g.drawString("3: Try using a different game-world", 30, var6);
			var6 += 30;
			g.drawString("4: Try rebooting your computer", 30, var6);
			var6 += 30;
			g.drawString("5: Try selecting a different version of Java from the play-game menu", 30, var6);
		}
	}

	@Override
	public void drawOutOfMemoryError() {
		Graphics g = this.getGraphics();
		if (null != g) {
			g.translate(mudclient.screenOffsetX, mudclient.screenOffsetY);
			g.setColor(Color.black);
			g.fillRect(0, 0, 512, 356);
			g.setFont(new Font("Helvetica", 1, 20));
			g.setColor(Color.white);
			g.drawString("Error - out of memory!", 50, 50);
			g.drawString("Close ALL unnecessary programs", 50, 100);
			g.drawString("and windows before loading the game", 50, 150);
			g.drawString(Config.getServerName() + " needs about 48meg of spare RAM", 50, 200);
		}
	}

	@Override
	public void drawTextBox(String line2, byte var2, String line1) {
		Graphics g = this.getGraphics();
		if (null != g) {
			g.translate(mudclient.screenOffsetX, mudclient.screenOffsetY);
			Font font = new Font("Helvetica", 1, 15);
			short width = 512;
			g.setColor(Color.black);
			short height = 344;
			g.fillRect(width / 2 - 140, height / 2 - 25, 280, 50);
			g.setColor(Color.white);
			g.drawRect(width / 2 - 140, height / 2 - 25, 280, 50);
			this.drawCenteredString(font, line1, height / 2 - 10, width / 2, g);
			this.drawCenteredString(font, line2, 10 + height / 2, width / 2, g);
		}
	}

	@Override
	public void initGraphics() {
		int width = mudclient.getSurface().width2;
		int height = mudclient.getSurface().height2;
		if (width > 1 && height > 1) {
			this.imageModel = new DirectColorModel(32, 16711680, '\uff00', 255);
			this.backingImage = createImage(this);
			this.commitToImage(true);
			prepareImage(this.backingImage, this);
			this.commitToImage(true);
			prepareImage(this.backingImage, this);
			this.commitToImage(true);
			prepareImage(this.backingImage, this);
		}
	}

	private synchronized void commitToImage(boolean var1) {
		try {
			if (null != this.imageProducer) {
				this.imageProducer.setPixels(0, 0, mudclient.getSurface().width2, mudclient.getSurface().height2,
					this.imageModel, mudclient.getSurface().pixelData, 0, mudclient.getSurface().width2);
				this.imageProducer.imageComplete(2);
			}
		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3, "ua.CA(" + true + ')');
		}
	}

	@Override
	public void addConsumer(ImageConsumer arg0) {
		try {
			this.imageProducer = arg0;
			arg0.setDimensions(mudclient.getSurface().width2, mudclient.getSurface().height2);
			arg0.setProperties(null);
			arg0.setColorModel(this.imageModel);
			arg0.setHints(14);
		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3, "ua.addConsumer(" + (arg0 != null ? "{...}" : "null") + ')');
		}
	}

	@Override
	public boolean isConsumer(ImageConsumer arg0) {
		return this.imageProducer == arg0;
	}

	@Override
	public void removeConsumer(ImageConsumer arg0) {
		if (this.imageProducer == arg0) this.imageProducer = null;
	}

	@Override
	public void requestTopDownLeftRightResend(ImageConsumer arg0) {
		try {
			System.out.println("TDLR");
		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3,
				"ua.requestTopDownLeftRightResend(" + (arg0 != null ? "{...}" : "null") + ')');
		}
	}

	@Override
	public void startProduction(ImageConsumer arg0) {
		this.addConsumer(arg0);
	}

	public final void draw() {
		this.commitToImage(true);

		// Re-scale when needed
		if (orsc.mudclient.newRenderingScalar != oldRenderingScalar) {
			updateRenderingScalarAndResize(orsc.mudclient.newRenderingScalar, mudclient.getGameWidth(), mudclient.getGameHeight());
			oldRenderingScalar = orsc.mudclient.newRenderingScalar;
		}

		g2dForGameImage.drawImage(this.backingImage, 0, 0, null);

		// Forward the image to be drawn by ScaledWindow.java
		scaledWindow.setGameImage(game_image);
	}

	/** Updates the rendering scalar and resizes the window accordingly */
	private static void updateRenderingScalarAndResize(float scalar, int newWidth, int newHeight) {
		int imageType = ScaledWindow.getBufferedImageType();

		// Reset the game image with the current type to ensure that affineOp
		// scaling will always have matching source and destination types
		game_image = new BufferedImage(newWidth, newHeight, imageType);

		// Handle rendering scalar value changes
		orsc.mudclient.renderingScalar = scalar;

		// Resize window only after it has begun rendering the game image,
		// (ie. not the loading screen)
		if (scaledWindow.isViewportLoaded()) {
			scaledWindow.resizeWindowToScalar();
		}
	}

	@Override
	public void close() {
		stop();
	}

	@Override
	public String getCacheLocation() {
		return "../OpenRSC/";
	}

	@Override
	public Sprite getBattery(int level) {
		// This would be needed to be implemented if was desired to display Battery Status Icon
		return null;
	}

	@Override
	public int getBatteryPercent() {
		// This would be needed to be implemented if was desired to display Battery Percent
		return 100;
	}

	@Override
	public boolean getBatteryCharging() {
		// This would be needed to be implemented if was desired to display Battery Charging
		return false;
	}

	@Override
	public Sprite getConnectivity(int level) {
		// This would be needed to be implemented if was desired to display Network Connectivity Status Icon
		return null;
	}

	@Override
	public String getConnectivityText() {
		// This would be needed to be implemented if was desired to display Network Connectivity Status Text
		return null;
	}

	@Override
	public void resized() {
		int newWidth = mudclient.getSurface().width2;
		int newHeight = mudclient.getSurface().height2;

		imageProducer.setDimensions(newWidth, newHeight);
		initGraphics();

		game_image = new BufferedImage(newWidth, newHeight, ScaledWindow.getBufferedImageType());
		g2dForGameImage = game_image.createGraphics();
	}

	@Override
	public Sprite getSpriteFromByteArray(ByteArrayInputStream byteArrayInputStream) {
		try {
			BufferedImage image = ImageIO.read(byteArrayInputStream);
			int captchaWidth = image.getWidth();
			int captchaHeight = image.getHeight();

			int[] pixels = new int[image.getWidth() * image.getHeight()];
			for (int y = 0; y < image.getHeight(); y++)
				for (int x = 0; x < image.getWidth(); x++) {
					int rgb = image.getRGB(x, y);
					pixels[x + y * image.getWidth()] = rgb;
				}

			Sprite sprite = new Sprite(pixels, captchaWidth, captchaHeight);
			sprite.setSomething(captchaWidth, captchaHeight);
			sprite.setShift(0, 0);
			sprite.setRequiresShift(false);
			return sprite;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public Sprite loadScaledImageSprite(String resourceName, int targetWidth, int targetHeight) {
		try {
			BufferedImage source = ImageIO.read(getClass().getResource("/res/" + resourceName));
			if (source == null || targetWidth <= 0 || targetHeight <= 0) {
				return null;
			}

			BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = scaled.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
			g.dispose();

			int[] pixels = new int[targetWidth * targetHeight];
			for (int y = 0; y < targetHeight; y++) {
				for (int x = 0; x < targetWidth; x++) {
					// The sprite renderer treats a pixel value of exactly 0 (pure black) as
					// transparent - nudge true black up by 1 so dark image regions (night
					// sky, shadows) don't punch see-through holes in what should be opaque art.
					int rgb = scaled.getRGB(x, y) & 0xFFFFFF;
					pixels[x + y * targetWidth] = rgb == 0 ? 1 : rgb;
				}
			}

			return new Sprite(pixels, targetWidth, targetHeight);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	// Target pixel height for each of the 8 font slots (0-7), matching GraphicsController's
	// fontHeight() lookup table exactly - other code reads layout/line-spacing from that table,
	// not from the actual glyph data, so generated glyphs should render close to these heights
	// or spacing will drift from what's actually drawn.
	private static final int[] FONT_TARGET_HEIGHT = {12, 14, 14, 15, 15, 19, 24, 29};
	// Bold/plain per slot, matching the original h11p/h12b/h12p/h13b/h14b/h16b/h20b/h24b loading
	// order in mudclient.loadLogo() (p = plain, b = bold).
	private static final boolean[] FONT_BOLD = {false, true, false, true, true, true, true, true};

	@Override
	public boolean regenerateFonts() {
		try {
			byte[][] generated = new byte[FONT_TARGET_HEIGHT.length][];
			for (int i = 0; i < FONT_TARGET_HEIGHT.length; i++) {
				generated[i] = buildFontData(FONT_BOLD[i] ? Font.BOLD : Font.PLAIN, FONT_TARGET_HEIGHT[i]);
			}

			// Only commit once every font has generated successfully - never leave a partial
			// mix of old (original bitmap) and new (generated) fonts across slots.
			for (int i = 0; i < generated.length; i++) {
				Fonts.setFont(i, generated[i], true);
			}
			return true;
		} catch (Exception e) {
			System.out.println("regenerateFonts: falling back to original bitmap fonts");
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Renders every character in {@link Fonts#inputFilterChars} (in that exact order - the game
	 * indexes glyphs by position in that string, not by character code) using a system font, and
	 * packs the result into the exact same binary layout the original .jf bitmap fonts use, so
	 * every existing draw/measure call site (GraphicsController.plotCharacter/stringWidth/etc.)
	 * needs no changes at all:
	 * <p>
	 * Per-character index record (9 bytes), stored first for every character in order:
	 * <pre>
	 *   +0,+1,+2: pixel-data byte offset into this same array, packed 7 bits per byte
	 *             (offset&gt;&gt;14 &amp; 0x7F, offset&gt;&gt;7 &amp; 0x7F, offset &amp; 0x7F) - the consumer does
	 *             a raw (b0&lt;&lt;14)+(b1&lt;&lt;7)+b2 with no unsigned masking, so each byte MUST stay
	 *             within 0-127 or Java's signed-byte sign-extension corrupts the address.
	 *   +3: width (0-127)
	 *   +4: height (0-127)
	 *   +5: x-offset added to the draw x (signed byte, left side bearing)
	 *   +6: y-offset subtracted from the draw y / baseline (signed byte, ascent above baseline)
	 *   +7: advance width added to the cursor after drawing (0-127, unsigned in practice - also
	 *       not masked by the consumer, so must stay under 128)
	 *   +8: unused by any current call site; written as 0
	 * </pre>
	 * followed by the concatenated per-glyph pixel data (one byte per pixel, row-major,
	 * antialiased coverage 0-255 - {@code Fonts.fontAntiAliased} is set true for these fonts so
	 * the renderer's existing alpha-blend path is used instead of a hard on/off draw).
	 */
	private byte[] buildFontData(int style, int targetPixelHeight) {
		String chars = Fonts.inputFilterChars;
		int numChars = chars.length();

		// Font point size isn't 1:1 with rendered pixel height - probe once and rescale.
		Font probe = new Font("SansSerif", style, targetPixelHeight);
		BufferedImage probeImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D probeG = probeImg.createGraphics();
		probeG.setFont(probe);
		FontMetrics probeFm = probeG.getFontMetrics();
		probeG.dispose();
		int probeHeight = probeFm.getAscent() + probeFm.getDescent();
		float ratio = probeHeight > 0 ? (float) targetPixelHeight / probeHeight : 1.0f;
		int pointSize = Math.max(6, Math.round(targetPixelHeight * ratio));

		Font font = new Font("SansSerif", style, pointSize);
		int canvasSize = pointSize * 3 + 10; // generous margin for ascenders/descenders

		BufferedImage canvas = new BufferedImage(canvasSize, canvasSize, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = canvas.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();

		int originX = canvasSize / 4;
		int originY = canvasSize / 2;

		byte[] index = new byte[numChars * 9];
		int pixelBase = index.length;
		java.io.ByteArrayOutputStream pixelBlob = new java.io.ByteArrayOutputStream();
		int[] row = new int[canvasSize];

		for (int i = 0; i < numChars; i++) {
			char ch = chars.charAt(i);

			g.setComposite(AlphaComposite.Clear);
			g.fillRect(0, 0, canvasSize, canvasSize);
			g.setComposite(AlphaComposite.SrcOver);
			g.setColor(Color.WHITE);
			g.drawString(String.valueOf(ch), originX, originY);

			int minX = canvasSize, minY = canvasSize, maxX = -1, maxY = -1;
			for (int y = 0; y < canvasSize; y++) {
				canvas.getRGB(0, y, canvasSize, 1, row, 0, canvasSize);
				for (int x = 0; x < canvasSize; x++) {
					if ((row[x] >>> 24 & 0xFF) > 8) {
						if (x < minX) minX = x;
						if (x > maxX) maxX = x;
						if (y < minY) minY = y;
						if (y > maxY) maxY = y;
					}
				}
			}

			int width, height, xOffset, yOffset;
			byte[] glyphPixels;
			if (maxX < 0) {
				// No ink (e.g. space) - zero-size glyph, but the advance below still applies.
				width = 0;
				height = 0;
				xOffset = 0;
				yOffset = 0;
				glyphPixels = new byte[0];
			} else {
				width = Math.min(127, maxX - minX + 1);
				height = Math.min(127, maxY - minY + 1);
				xOffset = clampToByte(minX - originX);
				yOffset = clampToByte(originY - minY);
				glyphPixels = new byte[width * height];
				int gi = 0;
				for (int y = minY; y < minY + height; y++) {
					canvas.getRGB(0, y, canvasSize, 1, row, 0, canvasSize);
					for (int x = minX; x < minX + width; x++) {
						glyphPixels[gi++] = (byte) (row[x] >>> 24 & 0xFF);
					}
				}
			}

			int advance = Math.max(0, Math.min(127, fm.charWidth(ch)));
			int offset = pixelBase + pixelBlob.size();
			if (offset > 0x1FFFFF) {
				// 21-bit address space (7 bits per byte, 3 bytes) - not expected to ever
				// trigger for 8 small fonts, but fail loudly rather than silently corrupt
				// addressing if it somehow did.
				throw new IllegalStateException("generated font exceeds addressable size");
			}

			int idx = i * 9;
			index[idx] = (byte) (offset >> 14 & 0x7F);
			index[idx + 1] = (byte) (offset >> 7 & 0x7F);
			index[idx + 2] = (byte) (offset & 0x7F);
			index[idx + 3] = (byte) width;
			index[idx + 4] = (byte) height;
			index[idx + 5] = (byte) xOffset;
			index[idx + 6] = (byte) yOffset;
			index[idx + 7] = (byte) advance;
			index[idx + 8] = 0;

			pixelBlob.write(glyphPixels, 0, glyphPixels.length);
		}

		g.dispose();

		byte[] result = new byte[pixelBase + pixelBlob.size()];
		System.arraycopy(index, 0, result, 0, index.length);
		byte[] blobBytes = pixelBlob.toByteArray();
		System.arraycopy(blobBytes, 0, result, pixelBase, blobBytes.length);
		return result;
	}

	private static byte clampToByte(int v) {
		if (v > 127) return 127;
		if (v < -128) return -128;
		return (byte) v;
	}

	@Override
	public void drawKeyboard() {
	}

	public void closeKeyboard() {
	}

	@Override
	public void playSound(byte[] soundData, int offset, int dataLength) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void stopSoundPlayer() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public void setTitle(String title) {
	}

	public void setIconImage(String serverName) {

	}

	public class MouseHandler implements MouseListener, MouseMotionListener, MouseWheelListener {
		@Override
		public final void mouseClicked(MouseEvent var1) {
			try {
				updateControlShiftState(var1);
			} catch (RuntimeException var3) {
				throw GenUtil.makeThrowable(var3, "e.mouseClicked(" + (var1 != null ? "{...}" : "null") + ')');
			}
		}

		@Override
		public final synchronized void mousePressed(MouseEvent var1) {
			try {
				if (var1.getButton() == MouseEvent.BUTTON2) {
					mudclient.mouseLastProcessedX = mudclient.mouseX;
					mudclient.mouseLastProcessedY = mudclient.mouseY;
					return;
				}
				updateControlShiftState(var1);
				mudclient.mouseX = var1.getX() - mudclient.screenOffsetX;
				mudclient.mouseY = var1.getY() - mudclient.screenOffsetY;

				if (!SwingUtilities.isRightMouseButton(var1)) mudclient.currentMouseButtonDown = 1;
				else mudclient.currentMouseButtonDown = 2;

				mudclient.lastMouseButtonDown = mudclient.currentMouseButtonDown;
				mudclient.lastMouseAction = 0;
				mudclient.addMouseClick(mudclient.currentMouseButtonDown, mudclient.mouseX, mudclient.mouseY);
			} catch (RuntimeException var3) {
				throw GenUtil.makeThrowable(var3, "e.mousePressed(" + (var1 != null ? "{...}" : "null") + ')');
			}
		}

		@Override
		public final synchronized void mouseReleased(MouseEvent var1) {
			try {
				if (var1.getButton() == MouseEvent.BUTTON2) {
					mudclient.mouseLastProcessedX = 0;
					mudclient.mouseLastProcessedY = 0;
					return;
				}
				updateControlShiftState(var1);
				mudclient.mouseX = var1.getX() - mudclient.screenOffsetX;
				mudclient.mouseY = var1.getY() - mudclient.screenOffsetY;
				mudclient.currentMouseButtonDown = 0;
			} catch (RuntimeException var3) {
				throw GenUtil.makeThrowable(var3, "e.mouseReleased(" + (var1 != null ? "{...}" : "null") + ')');
			}
		}

		@Override
		public final void mouseEntered(MouseEvent var1) {
			try {
				updateControlShiftState(var1);
			} catch (RuntimeException var3) {
				throw GenUtil.makeThrowable(var3, "e.mouseEntered(" + (var1 != null ? "{...}" : "null") + ')');
			}
		}

		@Override
		public final void mouseExited(MouseEvent var1) {
			try {
				updateControlShiftState(var1);
			} catch (RuntimeException var3) {
				throw GenUtil.makeThrowable(var3, "e.mouseExited(" + (var1 != null ? "{...}" : "null") + ')');
			}
		}

		@Override
		public final synchronized void mouseDragged(MouseEvent var1) {
			try {
				updateControlShiftState(var1);
				mudclient.mouseX = var1.getX() - mudclient.screenOffsetX;
				mudclient.mouseY = var1.getY() - mudclient.screenOffsetY;

				if (mudclient.mouseLastProcessedX != 0 && mudclient.mouseLastProcessedY != 0) {
					int distanceX = (mudclient.mouseX - mudclient.mouseLastProcessedX)/2;
					int distanceY = (mudclient.mouseY - mudclient.mouseLastProcessedY)/2;
					boolean touchedMessagePanelArea = mudclient.getGameHeight() - Math.max(mudclient.mouseY, mudclient.mouseLastProcessedY) <= 66;

					boolean scrollableMessagePanel = mudclient.hasScroll(mudclient.messageTabSelected) && touchedMessagePanelArea;
					boolean mayBeScrollable = mudclient.showUiTab != 0;
					boolean zoomable = (!scrollableMessagePanel && !mayBeScrollable) || osConfig.C_SWIPE_TO_SCROLL_MODE == 0;

					if (!mudclient.isInFirstPersonView() && zoomable && (S_ZOOM_VIEW_TOGGLE || mudclient.getLocalPlayer().isStaff()) && !var1.isControlDown()) {
						if (osConfig.C_SWIPE_TO_ZOOM_MODE != 0) {
							int dir = osConfig.C_SWIPE_TO_ZOOM_MODE == 2 ? -1 : 1;
							int newZoom = C_LAST_ZOOM + dir * distanceY;
							// Keep C_LAST_ZOOM aka the zoom increments on the range of [0, 255]
							if (newZoom >= 0 && newZoom <= 255) {
								C_LAST_ZOOM = newZoom;
							}
						}
					} else if (mudclient.isInFirstPersonView() && mudclient.cameraAllowPitchModification) {
						mudclient.cameraPitch = (mudclient.cameraPitch + (-distanceY * 2)) & 1023;

						// Limit on the half circled where everything is right side up
						if (mudclient.cameraPitch > 256 && mudclient.cameraPitch <= 512)
							mudclient.cameraPitch = 256;

						if (mudclient.cameraPitch < 768 && mudclient.cameraPitch > 512)
							mudclient.cameraPitch = 768;
					}
					if (osConfig.C_SWIPE_TO_ROTATE_MODE != 0) {
						// camera set to auto does not like manual like rotation
						if (!mudclient.getOptionCameraModeAuto()) {
							int dir = osConfig.C_SWIPE_TO_ROTATE_MODE == 2 ? -1 : 1;
							float clientDist = distanceX / (getWidth() / (float) mudclient.getGameWidth());
							mudclient.cameraRotation = (255 & mudclient.cameraRotation + (int) (dir * clientDist));
						} else {
							// swipe to left gives negative distanceX, to left negative
							int dir = osConfig.C_SWIPE_TO_ROTATE_MODE == 2 ? -1 : 1;
							boolean toLeft = dir * distanceX < 0;
							if (toLeft) {
								mudclient.keyLeft = true;
							} else {
								mudclient.keyRight = true;
							}
						}
					}
					if (!zoomable) {
						if (osConfig.C_SWIPE_TO_SCROLL_MODE != 0) {
							int dir = osConfig.C_SWIPE_TO_SCROLL_MODE == 2 ? -1 : 1;
							mudclient.runScroll(dir * distanceY);
						}
					}

					// To make the mouse move:
					//mudclient.mouseLastProcessedX = mudclient.mouseX;
					//mudclient.mouseLastProcessedY = mudclient.mouseY;

					// Move the mouse back to the last processed position.
					try {
						Robot robot = new Robot();
						//robot.mouseMove((int)getLocationOnScreen().getX() + mudclient.mouseLastProcessedX, (int)getLocationOnScreen().getY() + mudclient.mouseLastProcessedY);
						robot.mouseMove((int) MouseInfo.getPointerInfo().getLocation().getX() - distanceX, (int) MouseInfo.getPointerInfo().getLocation().getY() - distanceY);
					} catch (AWTException ignored) {
					}
				}
				if (SwingUtilities.isRightMouseButton(var1)) mudclient.currentMouseButtonDown = 2;
				else mudclient.currentMouseButtonDown = 1;
			} catch (RuntimeException var3) {
				throw GenUtil.makeThrowable(var3, "e.mouseDragged(" + (var1 != null ? "{...}" : "null") + ')');
			}
		}

		@Override
		public final synchronized void mouseMoved(MouseEvent var1) {
			try {
				updateControlShiftState(var1);
				mudclient.mouseX = var1.getX() - mudclient.screenOffsetX;
				mudclient.mouseY = var1.getY() - mudclient.screenOffsetY;
				mudclient.lastMouseAction = 0;
				mudclient.currentMouseButtonDown = 0;
			} catch (RuntimeException var3) {
				throw GenUtil.makeThrowable(var3, "e.mouseMoved(" + (var1 != null ? "{...}" : "null") + ')');
			}
		}

		@Override
		public final synchronized void mouseWheelMoved(MouseWheelEvent e) {
			updateControlShiftState(e);

			boolean touchedMessagePanelArea = getHeight() - e.getY() <= 75;

			boolean scrollableMessagePanel = mudclient.hasScroll(mudclient.messageTabSelected) && touchedMessagePanelArea;
			boolean mayBeScrollable = mudclient.showUiTab != 0;
			boolean zoomable = !scrollableMessagePanel && !mayBeScrollable;


			// Disables zoom while visible
			boolean inScrollable = (Config.S_SPAWN_AUCTION_NPCS && mudclient.auctionHouse.isVisible() || mudclient.onlineList.isVisible() || Config.S_WANT_SKILL_MENUS && mudclient.skillGuideInterface.isVisible()
				|| Config.S_WANT_QUEST_MENUS && mudclient.questGuideInterface.isVisible() || mudclient.clan.getClanInterface().isVisible() || mudclient.experienceConfigInterface.isVisible()
				|| mudclient.ironmanInterface.isVisible() || mudclient.achievementInterface.isVisible() || Config.S_WANT_SKILL_MENUS && mudclient.doSkillInterface.isVisible()
				|| Config.S_ITEMS_ON_DEATH_MENU && mudclient.lostOnDeathInterface.isVisible() || mudclient.territorySignupInterface.isVisible()
				|| mudclient.isShowDialogBank());

			if (!inScrollable && zoomable && (S_ZOOM_VIEW_TOGGLE || mudclient.getLocalPlayer().isStaff())) {
				e.consume();
				final int zoomIncrement = 10;
				int zoomAmount = e.getWheelRotation() * zoomIncrement;
				int newZoom = C_LAST_ZOOM + zoomAmount;
				// Keep C_LAST_ZOOM aka the zoom increments on the range of [0, 255]
				if (newZoom >= 0 && newZoom <= 255) {
					C_LAST_ZOOM = newZoom;
				}
			}

			if (inScrollable || !zoomable) {
				e.consume();
				mudclient.runScroll(e.getWheelRotation());
			}
		}
	}

	public class KeyHandler implements KeyListener {

		@Override
		public final void keyTyped(KeyEvent var1) {
			try {
				updateControlShiftState(var1);
			} catch (RuntimeException var3) {
				throw GenUtil.makeThrowable(var3, "e.keyTyped(" + (var1 != null ? "{...}" : "null") + ')');
			}
		}

		@Override
		public final synchronized void keyPressed(KeyEvent var1) {
			try {
				updateControlShiftState(var1);
				char keyChar = var1.getKeyChar();
				int keyCode = var1.getKeyCode();
				boolean hitInputFilter = false;
				mudclient.handleKeyPress((byte) 126, (int) keyChar);
				mudclient.lastMouseAction = 0;

				if (keyCode == 112) mudclient.interlace = !mudclient.interlace;
				if (keyCode == 113) Config.C_SIDE_MENU_OVERLAY = !Config.C_SIDE_MENU_OVERLAY;
				if (keyCode == KeyEvent.VK_F3) C_LAST_ZOOM = 75;
				if (keyCode == KeyEvent.VK_F4) mudclient.toggleFirstPersonView();
				if (keyCode == KeyEvent.VK_F10) mudclient.cycleScalingType(); // type
				if (keyCode == KeyEvent.VK_F11) mudclient.scaleDown(); // scale down
				if (keyCode == KeyEvent.VK_F12) mudclient.scaleUp(); // scale up
				if (keyCode == 39) mudclient.keyRight = true;
				if (keyCode == 37) mudclient.keyLeft = true;
				if (keyCode == 13 || keyCode == 10) mudclient.enterPressed = true;
				if (keyCode == KeyEvent.VK_UP) mudclient.keyUp = true;
				if (keyCode == KeyEvent.VK_DOWN) mudclient.keyDown = true;
				if (keyCode == KeyEvent.VK_PAGE_DOWN) mudclient.pageDown = true;
				if (keyCode == KeyEvent.VK_PAGE_UP) mudclient.pageUp = true;

				for (int var5 = 0; var5 < Fonts.inputFilterChars.length(); ++var5)
					if (Fonts.inputFilterChars.charAt(var5) == keyChar) {
						hitInputFilter = true;
						break;
					}

				if (hitInputFilter && mudclient.inputTextCurrent.length() < 20)
					mudclient.inputTextCurrent = mudclient.inputTextCurrent + keyChar;

				if (hitInputFilter && mudclient.chatMessageInput.length() < 80 && !mudclient.getIsSleeping())
					mudclient.chatMessageInput = mudclient.chatMessageInput + keyChar;

				// Backspace
				if (keyChar == '\b' && mudclient.inputTextCurrent.length() > 0)
					mudclient.inputTextCurrent = mudclient.inputTextCurrent.substring(0,
						mudclient.inputTextCurrent.length() - 1);

				// Backspace
				if (keyChar == '\b' && mudclient.chatMessageInput.length() > 0)
					mudclient.chatMessageInput = mudclient.chatMessageInput.substring(0,
						mudclient.chatMessageInput.length() - 1);

				if (keyChar == '\n' || keyChar == '\r') {
					mudclient.inputTextFinal = mudclient.inputTextCurrent;
					mudclient.chatMessageInputCommit = mudclient.chatMessageInput;
				}
			} catch (RuntimeException var6) {
				throw GenUtil.makeThrowable(var6, "e.keyPressed(" + (var1 != null ? "{...}" : "null") + ')');
			}
		}

		@Override
		public final synchronized void keyReleased(KeyEvent var1) {
			try {
				updateControlShiftState(var1);
				char c = var1.getKeyChar();
				int keyCode = var1.getKeyCode();

				if (keyCode == 39) mudclient.keyRight = false;
				if (keyCode == 37) mudclient.keyLeft = false;
				if (keyCode == KeyEvent.VK_UP) mudclient.keyUp = false;
				if (keyCode == KeyEvent.VK_DOWN) mudclient.keyDown = false;
				if (keyCode == KeyEvent.VK_PAGE_DOWN) mudclient.pageDown = false;
				if (keyCode == KeyEvent.VK_PAGE_UP) mudclient.pageUp = false;

				if (keyCode == KeyEvent.VK_ALT) {
					mudclient.mouseLastProcessedX = 0;
					mudclient.mouseLastProcessedY = 0;
				}
			} catch (RuntimeException var4) {
				throw GenUtil.makeThrowable(var4, "e.keyReleased(" + (var1 != null ? "{...}" : "null") + ')');
			}
		}
	}
}
