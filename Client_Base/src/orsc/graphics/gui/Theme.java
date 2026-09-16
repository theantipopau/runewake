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

	// ------------------------------------------------------------------
	// CustomBank interaction palette.
	//
	// The custom bank is its own self-consistent visual system (weathered
	// slate controls with dark-red selection chrome) and predates the
	// premium-theme pass. Off-path values below reproduce its inherited
	// literals exactly; the premium values reuse the established Runewake
	// dark-fantasy palette so both modes stay visually coherent.
	// ------------------------------------------------------------------
	private static final int CLASSIC_BANK_SLOT_FILL = 0x989898;
	private static final int CLASSIC_BANK_SLOT_UNUSABLE = 0x101010;
	private static final int CLASSIC_BANK_CONTROL_FILL = 0x5A5A55;
	private static final int CLASSIC_BANK_CONTROL_SELECTED = 0x7E1F1C;
	private static final int CLASSIC_BANK_CONTROL_HOVER = 0x6E6E68;
	private static final int CLASSIC_BANK_TAB_BORDER_OUTER = 0x2D2C24;
	private static final int CLASSIC_BANK_TAB_BORDER_INNER = 0x706452;
	private static final int CLASSIC_BANK_SEARCH_FILL = 0x222222;
	private static final int CLASSIC_BANK_SEARCH_BORDER = 0x474843;
	private static final int CLASSIC_BANK_MENU_FILL = 0x5C5548;
	private static final int CLASSIC_BANK_LABEL_ORANGE = 0xF89922;
	private static final int CLASSIC_BANK_WARNING = 0xFFFF00;

	/** Bank item-grid slot backing fill (also the preset-edit inventory slots). */
	public static int bankSlotFill() {
		return Config.C_PREMIUM_THEME ? PANEL_ELEVATED : CLASSIC_BANK_SLOT_FILL;
	}

	/** Backing fill for bank slots that cannot be used in the current mode (e.g. non-wieldables in equipment view). */
	public static int bankSlotUnusable() {
		return Config.C_PREMIUM_THEME ? OVERLAY_SCRIM : CLASSIC_BANK_SLOT_UNUSABLE;
	}

	/** Neutral backing fill for an unselected bank control (tabs, presets, mode buttons, context menu). */
	public static int bankControlFill() {
		return Config.C_PREMIUM_THEME ? PANEL_ELEVATED : CLASSIC_BANK_CONTROL_FILL;
	}

	/** Backing fill for the bank control under the cursor (hover state). */
	public static int bankControlHover() {
		return Config.C_PREMIUM_THEME ? PRESSED : CLASSIC_BANK_CONTROL_HOVER;
	}

	/** Backing fill for the currently selected bank control (page tab, preset tab, mode toggle, context-menu row). */
	public static int bankControlSelected() {
		return Config.C_PREMIUM_THEME ? SELECTION : CLASSIC_BANK_CONTROL_SELECTED;
	}

	/** Outer border of bank page tabs and mode controls. */
	public static int bankTabBorderOuter() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK : CLASSIC_BANK_TAB_BORDER_OUTER;
	}

	/** Inner bevel border of bank page tabs and mode controls. */
	public static int bankTabBorderInner() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK_MID : CLASSIC_BANK_TAB_BORDER_INNER;
	}

	/** Border of a selected/hovered bank control. */
	public static int bankControlSelectedBorder() {
		return Config.C_PREMIUM_THEME ? ACCENT_PRIMARY : CLASSIC_BANK_TAB_BORDER_INNER;
	}

	/** Bank item-slot border. Classic leaves slots separated only by the translucent body underneath. */
	public static int bankSlotBorder() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK : -1; // -1 = draw nothing (classic behaviour)
	}

	/** Backing fill of the search text entry. */
	public static int bankSearchFill() {
		return Config.C_PREMIUM_THEME ? OVERLAY_SCRIM : CLASSIC_BANK_SEARCH_FILL;
	}

	/** Border of the search text entry; highlights with the accent while it holds keyboard focus. */
	public static int bankSearchBorder(boolean focused) {
		return Config.C_PREMIUM_THEME ? (focused ? ACCENT_PRIMARY : BORDER_DARK_MID) : CLASSIC_BANK_SEARCH_BORDER;
	}

	/** Fill of the withdraw/deposit right-click context menu. */
	public static int bankContextMenuFill() {
		return Config.C_PREMIUM_THEME ? PANEL_ELEVATED : CLASSIC_BANK_MENU_FILL;
	}

	/** Header strip of the right-click context menu (drawn black in both modes). */
	public static int bankContextMenuHeader() {
		return Config.C_PREMIUM_THEME ? OVERLAY_SCRIM : 0x000000;
	}

	/** Context-menu text: normal rows; hovered rows take the classic yellow highlight in both modes. */
	public static int bankMenuRowText(boolean hovered) {
		return Config.C_PREMIUM_THEME ? (hovered ? WARNING : TEXT_PRIMARY) : (hovered ? 0xFDFF21 : 0xFFFFFF);
	}

	/** Orange section labels ("Rearrange mode:", "Withdraw as:"). */
	public static int bankLabelAccent() {
		return Config.C_PREMIUM_THEME ? ACCENT_SECONDARY : CLASSIC_BANK_LABEL_ORANGE;
	}

	/** "Total wealth" header value. */
	public static int bankWealthText() {
		return Config.C_PREMIUM_THEME ? WARNING : CLASSIC_BANK_WARNING;
	}

	/** Main panel text (titles, item names, control labels). */
	public static int bankText() {
		return Config.C_PREMIUM_THEME ? TEXT_PRIMARY : 0xFFFFFF;
	}

	/** Bank item-stack counts. */
	public static int bankAmountText() {
		return Config.C_PREMIUM_THEME ? SUCCESS : 65280;
	}

	/** "Close Window" text; turns red while hovered in both modes. */
	public static int bankCloseText(boolean hovered) {
		return Config.C_PREMIUM_THEME ? (hovered ? DANGER : TEXT_PRIMARY) : (hovered ? 16711680 : 0xFFFFFF);
	}

	// ------------------------------------------------------------------
	// Login / character-creation (onboarding) presentation.
	// ------------------------------------------------------------------

	/**
	 * Underline drawn under the keyboard-focused text entry (login username /
	 * password, registration fields). Classic mode keeps the inherited asterisk
	 * only (no underline); premium mode marks focus with the rune-blue accent.
	 */
	public static int textEntryFocusUnderline() {
		return Config.C_PREMIUM_THEME ? ACCENT_PRIMARY : -1; // -1 = draw nothing (classic behaviour)
	}
}
