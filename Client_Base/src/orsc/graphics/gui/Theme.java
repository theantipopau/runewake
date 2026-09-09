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

	// Classic values these two replace when C_PREMIUM_THEME is off - kept as named
	// constants (not re-literals) so the "off" path is still self-documenting.
	private static final int CLASSIC_SLOT_FILL = GenUtil.buildColor(181, 181, 181);
	private static final int CLASSIC_SLOT_HIGHLIGHT = GenUtil.buildColor(220, 220, 220);

	/** Unselected/normal inventory-grid slot backing colour (inventory, equipment tab). */
	public static int slotFill() {
		return Config.C_PREMIUM_THEME ? PANEL_INSET : CLASSIC_SLOT_FILL;
	}

	/** Selected/highlighted slot or sub-tab backing colour. */
	public static int slotHighlight() {
		return Config.C_PREMIUM_THEME ? SELECTION : CLASSIC_SLOT_HIGHLIGHT;
	}
}
