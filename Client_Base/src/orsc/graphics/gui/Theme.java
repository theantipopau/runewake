package orsc.graphics.gui;

import orsc.Config;
import orsc.util.GenUtil;

/**
 * Centralised colour tokens for the "premium" (non-authentic) presentation pass.
 * Authentic-mode rendering must keep using its own existing literals/defaults -
 * this class only exists so new premium styling has one place to look instead
 * of scattering fresh RGB literals across mudclient.java/Panel.java.
 *
 * Palette direction: retro dark-fantasy - weathered charcoal/stone surfaces,
 * restrained rune-blue/teal accents, muted bronze/aged-gold borders, parchment
 * text. No gloss/bloom.
 */
public final class Theme {

	private Theme() {
	}

	// Surfaces
	public static final int BACKGROUND = GenUtil.buildColor(24, 24, 28);
	public static final int PANEL_ELEVATED = GenUtil.buildColor(46, 44, 42);
	public static final int PANEL_INSET = GenUtil.buildColor(30, 29, 27);
	public static final int OVERLAY_SCRIM = GenUtil.buildColor(10, 10, 12);

	// Accents
	public static final int ACCENT_PRIMARY = GenUtil.buildColor(78, 158, 168);
	public static final int ACCENT_SECONDARY = GenUtil.buildColor(198, 170, 112);

	// Text
	public static final int TEXT_PRIMARY = GenUtil.buildColor(224, 216, 196);
	public static final int TEXT_MUTED = GenUtil.buildColor(150, 142, 128);

	// Status
	public static final int SUCCESS = GenUtil.buildColor(112, 168, 96);
	public static final int WARNING = GenUtil.buildColor(206, 168, 78);
	public static final int DANGER = GenUtil.buildColor(176, 66, 58);

	// Interaction states
	public static final int SELECTION = GenUtil.buildColor(78, 158, 168);
	public static final int HOVER = GenUtil.buildColor(96, 176, 184);
	public static final int PRESSED = GenUtil.buildColor(58, 122, 130);
	public static final int DISABLED = GenUtil.buildColor(90, 88, 84);

	// Borders (aged-gold/bronze bevel, matches the existing login button scheme)
	public static final int BORDER_LIGHT = GenUtil.buildColor(198, 170, 112);
	public static final int BORDER_LIGHT_MID = GenUtil.buildColor(150, 122, 76);
	public static final int BORDER_DARK_MID = GenUtil.buildColor(96, 74, 44);
	public static final int BORDER_DARK = GenUtil.buildColor(56, 42, 24);

	/** Applies the bronze/gold bevel scheme (login, recovery, contact, character-creation panels). */
	public static void applyBronzeButtonScheme(Panel panel) {
		panel.setButtonColorScheme(BORDER_LIGHT, BORDER_LIGHT_MID, BORDER_DARK_MID, BORDER_DARK);
	}

	// Classic modal-dialog literals (trade/duel/shop family). Kept as named
	// constants so the "off" path is still self-documenting.
	private static final int CLASSIC_DIALOG_HEADER = GenUtil.buildColor(0, 0, 192);
	private static final int CLASSIC_DIALOG_BODY = 10000536; // 0x989898 grey
	private static final int CLASSIC_DIALOG_INSET = 13684944; // 0xD0D0D0 light grey

	// Classic values these replace when C_PREMIUM_THEME is off - kept as named
	// constants (not re-literals) so the "off" path is still self-documenting.
	private static final int CLASSIC_SLOT_FILL = GenUtil.buildColor(181, 181, 181);
	private static final int CLASSIC_SLOT_HIGHLIGHT = GenUtil.buildColor(220, 220, 220);
	private static final int CLASSIC_SETTINGS_MAIN = GenUtil.buildColor(181, 181, 181);
	private static final int CLASSIC_SETTINGS_ALT = GenUtil.buildColor(201, 201, 201);
	private static final int CLASSIC_TAB_CHOSEN = GenUtil.buildColor(220, 220, 220);
	private static final int CLASSIC_TAB_UNCHOSEN = GenUtil.buildColor(160, 160, 160);

	/** Unselected/normal inventory-grid slot backing colour (inventory, equipment tab). */
	public static int slotFill() {
		return Config.C_PREMIUM_THEME ? PANEL_INSET : CLASSIC_SLOT_FILL;
	}

	/** Selected/highlighted slot or sub-tab backing colour. */
	public static int slotHighlight() {
		return Config.C_PREMIUM_THEME ? SELECTION : CLASSIC_SLOT_HIGHLIGHT;
	}

	/** Settings-panel main box fill (the 181-grey rows of the settings tab). */
	public static int panelFill() {
		return Config.C_PREMIUM_THEME ? PANEL_INSET : CLASSIC_SETTINGS_MAIN;
	}

	/** Settings-panel alternate/bottom box fill (the 201-grey rows). */
	public static int panelFillAlt() {
		return Config.C_PREMIUM_THEME ? PANEL_ELEVATED : CLASSIC_SETTINGS_ALT;
	}

	/** Backing colour of the selected settings sub-tab. */
	public static int tabSelectedFill() {
		return Config.C_PREMIUM_THEME ? SELECTION : CLASSIC_TAB_CHOSEN;
	}

	/** Backing colour of an unselected settings sub-tab. */
	public static int tabUnselectedFill() {
		return Config.C_PREMIUM_THEME ? DISABLED : CLASSIC_TAB_UNCHOSEN;
	}

	/**
	 * Colour for plain section-header text drawn directly on settings-panel
	 * boxes. Classic draws these black on light grey; the premium panels are
	 * dark, so the header follows the theme to stay readable.
	 */
	public static int settingsHeaderColor() {
		return Config.C_PREMIUM_THEME ? TEXT_PRIMARY : 0;
	}

	/** Transaction-dialog (trade/duel/shop) title-bar fill. */
	public static int dialogHeaderBar() {
		return Config.C_PREMIUM_THEME ? PANEL_ELEVATED : CLASSIC_DIALOG_HEADER;
	}

	/** Transaction-dialog translucent body fill drawn over the game world. */
	public static int dialogBodyFill() {
		return Config.C_PREMIUM_THEME ? PANEL_INSET : CLASSIC_DIALOG_BODY;
	}

	/** Transaction-dialog item-grid / inset panel fill. */
	public static int dialogInsetFill() {
		return Config.C_PREMIUM_THEME ? PANEL_ELEVATED : CLASSIC_DIALOG_INSET;
	}

	/** Highlight fill for the currently selected item slot in a transaction dialog. */
	public static int dialogSelectedSlot() {
		return Config.C_PREMIUM_THEME ? SELECTION : 0xFF0000;
	}
}
