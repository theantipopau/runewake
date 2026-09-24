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

	/**
	 * On-screen picker list entries (player-mode / xp-rate selectors on the
	 * character-creation screen). Classic mode preserves the inherited literal
	 * colours exactly; premium mode reads selected in the rune-blue accent and
	 * hovered rows brighter than idle ones.
	 */
	public static int appearanceListEntry(boolean altColor, boolean hovered, boolean selected) {
		if (!Config.C_PREMIUM_THEME) {
			// Classic: inherited values, in both colour schemes.
			if (selected) {
				return altColor ? 16711680 : 12582912;
			}
			return hovered ? (altColor ? 8421504 : 16777215) : (altColor ? 16777215 : 0);
		}
		if (selected) {
			return ACCENT_PRIMARY;
		}
		return hovered ? TEXT_PRIMARY : TEXT_MUTED;
	}

	/**
	 * Border of the character-creation colour chips (display-only swatches of
	 * the current hair/top/bottom/skin choice). -1 = draw nothing, so classic
	 * keeps the inherited arrow-only presentation.
	 */
	public static int appearanceSwatchBorder() {
		return Config.C_PREMIUM_THEME ? TEXT_PRIMARY : -1;
	}

	// ------------------------------------------------------------------
	// Side-panel / HUD tints (social + clan tabs, stats, magic, combat
	// styles, xp counter). Classic values are the inherited 220/160-grey
	// tab language drawn translucent over the world - distinct from the
	// transaction-dialog family, so they get their own tokens.
	// ------------------------------------------------------------------
	private static final int CLASSIC_SOCIAL_BODY = GenUtil.buildColor(220, 220, 220);
	private static final int CLASSIC_SOCIAL_INSET = GenUtil.buildColor(220, 220, 220);
	private static final int CLASSIC_SPELL_INFO = GenUtil.buildColor(160, 160, 160);
	private static final int CLASSIC_COMBAT_ROW = GenUtil.buildColor(190, 190, 190);
	private static final int CLASSIC_COMBAT_SELECTED = GenUtil.buildColor(255, 0, 0);

	/**
	 * Translucent list-body backdrop of the social/clan and stats side panels
	 * (drawn over the world; alpha is applied at the call site).
	 */
	public static int socialBodyFill() {
		return Config.C_PREMIUM_THEME ? PANEL_INSET : CLASSIC_SOCIAL_BODY;
	}

	/** Inset sub-panel inside a side panel (clan info boxes). */
	public static int socialInsetFill() {
		return Config.C_PREMIUM_THEME ? PANEL_ELEVATED : CLASSIC_SOCIAL_INSET;
	}

	/** 1px structural separators between side-panel tabs and rows. */
	public static int sidePanelSeparator() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK_MID : 0;
	}

	/** Tab label text on the side-panel tab strips (drawn black on the light classic tabs). */
	public static int sidePanelTabText() {
		return Config.C_PREMIUM_THEME ? TEXT_PRIMARY : 0;
	}

	/** Fill of the clan action buttons (Leave Clan / Clan Setup / Clan Search); hover lightens the idle navy. */
	public static int clanActionFill(boolean hovered) {
		return Config.C_PREMIUM_THEME ? (hovered ? HOVER : PANEL_ELEVATED) : (hovered ? 0x263751 : 0x0A2B56);
	}

	/** Bronze border around the clan action buttons. */
	public static int clanActionBorder() {
		return Config.C_PREMIUM_THEME ? BORDER_LIGHT_MID : 0xBFA086;
	}

	/** Label text of the clan action buttons. */
	public static int clanActionText() {
		return Config.C_PREMIUM_THEME ? TEXT_PRIMARY : 0xFFFFFF;
	}

	/** Recessed spell-description area of the magic panel. */
	public static int spellInfoFill() {
		return Config.C_PREMIUM_THEME ? PANEL_ELEVATED : CLASSIC_SPELL_INFO;
	}

	/** Highlighted combat-style row. */
	public static int combatStyleSelected() {
		return Config.C_PREMIUM_THEME ? DANGER : CLASSIC_COMBAT_SELECTED;
	}

	/** Unhighlighted combat-style row. */
	public static int combatStyleRow() {
		return Config.C_PREMIUM_THEME ? PANEL_ELEVATED : CLASSIC_COMBAT_ROW;
	}

	/** Red overlay marking an inventory slot whose item is equipped (equipment-tab legacy view). */
	public static int inventoryEquippedWarning() {
		return Config.C_PREMIUM_THEME ? DANGER : 0xFF0000;
	}

	/** Translucent backdrop of the xp-counter pill and its submenu. */
	public static int xpCounterFill() {
		return Config.C_PREMIUM_THEME ? OVERLAY_SCRIM : 0x989898;
	}

	// ------------------------------------------------------------------
	// Android on-screen control overlays (keyboard toggle, chat-command
	// buttons, cast-last-spell widget). Drawn translucent over the world
	// on touch devices only; classic values are the inherited literals.
	// ------------------------------------------------------------------

	/** Keyboard-toggle button and cast-last-spell widget body fill. */
	public static int androidControlFill() {
		return Config.C_PREMIUM_THEME ? OVERLAY_SCRIM : 0x989898;
	}

	/** Black outline around the Android overlay controls (invisible on the premium dark fills). */
	public static int androidControlBorder() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK_MID : 0;
	}

	/** On-screen chat-command buttons (Global / Wiki / ...) on Android. */
	public static int androidCommandFill() {
		return Config.C_PREMIUM_THEME ? PANEL_ELEVATED : 0x659CDE;
	}

	/** "Tap to Cast" header band of the cast-last-spell widget. */
	public static int androidCastHeaderFill() {
		return Config.C_PREMIUM_THEME ? SELECTION : 0x6b8e23;
	}

	/** "Remove" button of the cast-last-spell widget (destructive action). */
	public static int androidCastRemoveFill() {
		return Config.C_PREMIUM_THEME ? DANGER : GenUtil.buildColor(255, 0, 0);
	}

	// ------------------------------------------------------------------
	// Social GUI windows (ClanInterface / PartyInterface). These two
	// interfaces are forked from a common template and share one palette:
	// warm-brown chrome, translucent list bodies and a five-button scheme.
	// Off-path values below reproduce the inherited literals exactly;
	// premium values reuse the established Runewake palette.
	// ------------------------------------------------------------------
	private static final int CLASSIC_SOCIALGUI_BODY = 0x1D1711;
	// === Auction house (AuctionHouse.java) ===

	private static final int CLASSIC_AUCTION_PANEL_BORDER = 0x343434;
	private static final int CLASSIC_AUCTION_LIST_BAND_LINE = 0x222222;
	private static final int CLASSIC_AUCTION_INPUT_FILL = 0x0C0C0C;
	private static final int CLASSIC_AUCTION_INPUT_BORDER = 0x35231B;
	private static final int CLASSIC_AUCTION_HEADER_BAND_FILL = 0x6b8e23;
	private static final int CLASSIC_AUCTION_TABLE_HEADER_FILL = 0x3E557C;
	private static final int CLASSIC_AUCTION_LIST_BAND_FILL = 0x192638;
	private static final int CLASSIC_AUCTION_LIST_BAND_BORDER = 0x292D30;
	private static final int CLASSIC_AUCTION_LIST_ROW_SELECTED_DANGER = 0xff0000;
	private static final int CLASSIC_AUCTION_LIST_ROW_ARMED_DANGER = 0x45454545;
	private static final int CLASSIC_AUCTION_CANCEL_CONFIRM_FILL = 0x980000;
	private static final int CLASSIC_AUCTION_CANCEL_BUTTON_FILL = 0x980000;
	private static final int CLASSIC_AUCTION_CANCEL_BUTTON_HOVER_FILL = 0x500000;
	private static final int CLASSIC_AUCTION_CANCEL_BUTTON_BORDER = 0xC8C7BE;
	private static final int CLASSIC_AUCTION_BUTTON_IDLE_FILL = 0x333333;
	private static final int CLASSIC_AUCTION_FANCY_BUTTON_IDLE_FILL = 0x0A2B56;
	private static final int CLASSIC_AUCTION_BUTTON_CHECKED_FILL = 0x659CDE;
	private static final int CLASSIC_AUCTION_BUTTON_HOVER_FILL = 0x263751;
	private static final int CLASSIC_AUCTION_BUTTON_BORDER = 0x242424;
	private static final int CLASSIC_AUCTION_FANCY_BUTTON_BORDER = 0xBFA086;
	private static final int CLASSIC_AUCTION_TEXT_HIT_IDLE = 0xffffff;
	private static final int CLASSIC_AUCTION_TEXT_HIT_ACTIVE = 0x6b8e23;
	private static final int CLASSIC_AUCTION_TEXT_HIT_HOVER = 0xFF0000;
	private static final int CLASSIC_AUCTION_HEADING = 0xFFFF00;
	private static final int CLASSIC_AUCTION_SELECTOR_PANEL_FILL = 0x454545;
	private static final int CLASSIC_AUCTION_SPINNER_BORDER = 0x555555;
	private static final int CLASSIC_AUCTION_SEARCH_FILL = 0x222222;
	private static final int CLASSIC_AUCTION_SEARCH_BORDER = 0x474843;
	private static final int CLASSIC_AUCTION_PRICE_LABEL = 0xc1b575;
	private static final int CLASSIC_AUCTION_INVENTORY_SLOT = 0xd0d0d0;

	/** Outer frames of the auction window, list boxes and tabs. */
	public static int auctionPanelBorder() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK_MID : CLASSIC_AUCTION_PANEL_BORDER;
	}

	/** Alternating-list separator lines. */
	public static int auctionListBandLine() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK : CLASSIC_AUCTION_LIST_BAND_LINE;
	}

	/** Input-box fill for price and quantity fields. */
	public static int auctionInputFill() {
		return Config.C_PREMIUM_THEME ? PANEL_INSET : CLASSIC_AUCTION_INPUT_FILL;
	}

	/** Frame around price and quantity input boxes. */
	public static int auctionInputBorder() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK_MID : CLASSIC_AUCTION_INPUT_BORDER;
	}

	/** Header bands: categories column and buy/sell mode selector. */
	public static int auctionHeaderBandFill() {
		return Config.C_PREMIUM_THEME ? PANEL_ELEVATED : CLASSIC_AUCTION_HEADER_BAND_FILL;
	}

	/** Header row of the auction listing table. */
	public static int auctionTableHeaderFill() {
		return Config.C_PREMIUM_THEME ? PANEL_ELEVATED : CLASSIC_AUCTION_TABLE_HEADER_FILL;
	}

	/** Alternating-list row band. */
	public static int auctionListBandFill() {
		return Config.C_PREMIUM_THEME ? PANEL_INSET : CLASSIC_AUCTION_LIST_BAND_FILL;
	}

	/** Border around the alternating-list block. */
	public static int auctionListBandBorder() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK : CLASSIC_AUCTION_LIST_BAND_BORDER;
	}

	/** Row fill of the selected (about-to-be-cancelled) auction entry. */
	public static int auctionListRowSelectedDanger() {
		return Config.C_PREMIUM_THEME ? DANGER : CLASSIC_AUCTION_LIST_ROW_SELECTED_DANGER;
	}

	/** Row fill of the auction entry armed for cancellation. */
	public static int auctionListRowArmedDanger() {
		return Config.C_PREMIUM_THEME ? PRESSED : CLASSIC_AUCTION_LIST_ROW_ARMED_DANGER;
	}

	/** Confirm strip under the cancel button. */
	public static int auctionCancelConfirmFill() {
		return Config.C_PREMIUM_THEME ? DANGER : CLASSIC_AUCTION_CANCEL_CONFIRM_FILL;
	}

	/** Cancel-auction button fill. */
	public static int auctionCancelButtonFill() {
		return Config.C_PREMIUM_THEME ? DANGER : CLASSIC_AUCTION_CANCEL_BUTTON_FILL;
	}

	/** Cancel-auction button fill while hovered. */
	public static int auctionCancelButtonHoverFill() {
		return Config.C_PREMIUM_THEME ? PRESSED : CLASSIC_AUCTION_CANCEL_BUTTON_HOVER_FILL;
	}

	/** Border of the cancel-auction button. */
	public static int auctionCancelButtonBorder() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK_MID : CLASSIC_AUCTION_CANCEL_BUTTON_BORDER;
	}

	/** Idle fill of plain auction buttons. */
	public static int auctionButtonIdleFill() {
		return Config.C_PREMIUM_THEME ? PANEL_ELEVATED : CLASSIC_AUCTION_BUTTON_IDLE_FILL;
	}

	/** Idle fill of the fancy (framed) auction buttons. */
	public static int auctionFancyButtonIdleFill() {
		return Config.C_PREMIUM_THEME ? OVERLAY_SCRIM : CLASSIC_AUCTION_FANCY_BUTTON_IDLE_FILL;
	}

	/** Fill of a checked/active auction button. */
	public static int auctionButtonCheckedFill() {
		return Config.C_PREMIUM_THEME ? SELECTION : CLASSIC_AUCTION_BUTTON_CHECKED_FILL;
	}

	/** Fill of a hovered unchecked auction button. */
	public static int auctionButtonHoverFill() {
		return Config.C_PREMIUM_THEME ? PRESSED : CLASSIC_AUCTION_BUTTON_HOVER_FILL;
	}

	/** Border of plain auction buttons. */
	public static int auctionButtonBorder() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK : CLASSIC_AUCTION_BUTTON_BORDER;
	}

	/** Border of the fancy (framed) auction buttons. */
	public static int auctionFancyButtonBorder() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK_MID : CLASSIC_AUCTION_FANCY_BUTTON_BORDER;
	}

	/** Clickable category/text heading in its idle state. */
	public static int auctionTextHitIdleColor() {
		return Config.C_PREMIUM_THEME ? TEXT_MUTED : CLASSIC_AUCTION_TEXT_HIT_IDLE;
	}

	/** Clickable category/text heading while active. */
	public static int auctionTextHitActiveColor() {
		return Config.C_PREMIUM_THEME ? ACCENT_SECONDARY : CLASSIC_AUCTION_TEXT_HIT_ACTIVE;
	}

	/** Clickable category/text heading while hovered. */
	public static int auctionTextHitHoverColor() {
		return Config.C_PREMIUM_THEME ? DANGER : CLASSIC_AUCTION_TEXT_HIT_HOVER;
	}

	/** Yellow headings drawn over panel insets. */
	public static int auctionHeadingColor() {
		return Config.C_PREMIUM_THEME ? ACCENT_SECONDARY : CLASSIC_AUCTION_HEADING;
	}

	/** Large inset panel behind the item selector list. */
	public static int auctionSelectorPanelFill() {
		return Config.C_PREMIUM_THEME ? PANEL_INSET : CLASSIC_AUCTION_SELECTOR_PANEL_FILL;
	}

	/** Border of the quantity spinner box. */
	public static int auctionSpinnerBorder() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK_MID : CLASSIC_AUCTION_SPINNER_BORDER;
	}

	/** Search box fill in the browse tab. */
	public static int auctionSearchFill() {
		return Config.C_PREMIUM_THEME ? PANEL_INSET : CLASSIC_AUCTION_SEARCH_FILL;
	}

	/** Search box border. */
	public static int auctionSearchBorder() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK_MID : CLASSIC_AUCTION_SEARCH_BORDER;
	}

	/** Price labels ("Buyout:", "Each:"). */
	public static int auctionPriceLabelColor() {
		return Config.C_PREMIUM_THEME ? ACCENT_SECONDARY : CLASSIC_AUCTION_PRICE_LABEL;
	}

	/** Inventory slot count text. */
	public static int auctionInventorySlotColor() {
		return Config.C_PREMIUM_THEME ? TEXT_PRIMARY : CLASSIC_AUCTION_INVENTORY_SLOT;
	}

	/** General white body text on the darkened auction panels. */
	public static int auctionTextPrimaryColor() {
		return Config.C_PREMIUM_THEME ? TEXT_PRIMARY : 0xffffff;
	}

	/** Green amount text on auction listings. */
	public static int auctionCountGreen() {
		return Config.C_PREMIUM_THEME ? SUCCESS : 65280;
	}
	private static final int CLASSIC_SOCIALGUI_BACKDROP = 0x1D1915;
	private static final int CLASSIC_SOCIALGUI_TABLE_HEADER = 0x432C26;
	private static final int CLASSIC_SOCIALGUI_TABLE_HEADER_BORDER = 0x4C4445;
	private static final int CLASSIC_SOCIALGUI_ROW_EVEN = 0x1C1B19;
	private static final int CLASSIC_SOCIALGUI_ROW_ODD = 0x232220;
	private static final int CLASSIC_SOCIALGUI_ROW_HIGHLIGHT = 0x202F39;
	private static final int CLASSIC_SOCIALGUI_ROW_BORDER = 0x343434;
	private static final int CLASSIC_SOCIALGUI_SEARCHROW_EVEN = 0xD9DCD6;
	private static final int CLASSIC_SOCIALGUI_SEARCHROW_ODD = 0xC2C8C3;
	private static final int CLASSIC_SOCIALGUI_SEARCHROW_HIGHLIGHT = 0x90E05B;
	private static final int CLASSIC_SOCIALGUI_SEARCHROW_BORDER = 0x716F6C;
	private static final int CLASSIC_SOCIALGUI_HEADER_BAND = 0x957357;
	private static final int CLASSIC_SOCIALGUI_INNER_CARD = 0x544B40;
	private static final int CLASSIC_SOCIALGUI_INNER_CARD_BORDER = 0x7D7161;
	private static final int CLASSIC_SOCIALGUI_CARD_SHADOW = 0x060607;
	private static final int CLASSIC_SOCIALGUI_OUTER_BORDER = 0x5F5147;
	private static final int CLASSIC_SOCIALGUI_SEPARATOR = 0x6E5D4E;
	private static final int CLASSIC_SOCIALGUI_DETAIL_SEPARATOR = 0x4C4638;

	private static final int CLASSIC_MINIMAP_BACKDROP = 0x000000;
	private static final int CLASSIC_MINIMAP_BORDER = 0x000000;

	/** Main window body of the clan/party settings window (alpha applied at the call site). */
	public static int socialGuiBodyFill() {
		return Config.C_PREMIUM_THEME ? OVERLAY_SCRIM : CLASSIC_SOCIALGUI_BODY;
	}

	/** Translucent backdrop of the list boxes and content panels inside the window. */
	public static int socialGuiBackdropFill() {
		return Config.C_PREMIUM_THEME ? PANEL_INSET : CLASSIC_SOCIALGUI_BACKDROP;
	}

	/** Backdrop behind the rotating minimap terrain, visible outside the terrain diamond. */
	public static int minimapBackdropFill() {
		return Config.C_PREMIUM_THEME ? PANEL_INSET : CLASSIC_MINIMAP_BACKDROP;
	}

	/** Frame drawn around the minimap viewport box. */
	public static int minimapFrameColor() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK_MID : CLASSIC_MINIMAP_BORDER;
	}

	/** Translucent inner plate behind the compass dial. */
	public static int minimapCompassBackdropFill() {
		return Config.C_PREMIUM_THEME ? PANEL_ELEVATED : CLASSIC_MINIMAP_BACKDROP;
	}

	/** Bevel ring around the compass backdrop plate. */
	public static int minimapCompassRingColor() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK : CLASSIC_MINIMAP_BORDER;
	}

	/** Backing fill of the clanmate/party table column headers. */
	public static int socialGuiTableHeaderFill() {
		return Config.C_PREMIUM_THEME ? PANEL_ELEVATED : CLASSIC_SOCIALGUI_TABLE_HEADER;
	}

	/** Border of the table column headers. */
	public static int socialGuiTableHeaderBorder() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK_MID : CLASSIC_SOCIALGUI_TABLE_HEADER_BORDER;
	}

	/** Alternating body fill of the clanmate/party list rows (altColor = even rows). */
	public static int socialGuiRowFill(boolean altColor) {
		return Config.C_PREMIUM_THEME ? (altColor ? PANEL_INSET : PANEL_ELEVATED) : (altColor ? CLASSIC_SOCIALGUI_ROW_EVEN : CLASSIC_SOCIALGUI_ROW_ODD);
	}

	/** Hovered or selected clanmate/party row. */
	public static int socialGuiRowHighlight() {
		return Config.C_PREMIUM_THEME ? SELECTION : CLASSIC_SOCIALGUI_ROW_HIGHLIGHT;
	}

	/** Border around each clanmate/party row. */
	public static int socialGuiRowBorder() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK : CLASSIC_SOCIALGUI_ROW_BORDER;
	}

	/** Alternating body fill of the clan/party search-result rows (altColor = even rows). */
	public static int socialGuiSearchRowFill(boolean altColor) {
		return Config.C_PREMIUM_THEME ? (altColor ? PANEL_INSET : PANEL_ELEVATED) : (altColor ? CLASSIC_SOCIALGUI_SEARCHROW_EVEN : CLASSIC_SOCIALGUI_SEARCHROW_ODD);
	}

	/** Hovered or selected search-result row. */
	public static int socialGuiSearchRowHighlight() {
		return Config.C_PREMIUM_THEME ? SELECTION : CLASSIC_SOCIALGUI_SEARCHROW_HIGHLIGHT;
	}

	/** Border (and member-column separator) of each search-result row. */
	public static int socialGuiSearchRowBorder() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK_MID : CLASSIC_SOCIALGUI_SEARCHROW_BORDER;
	}

	/** Solid title band above the window body and the invite pop-up header. */
	public static int socialGuiHeaderBandFill() {
		return Config.C_PREMIUM_THEME ? PANEL_ELEVATED : CLASSIC_SOCIALGUI_HEADER_BAND;
	}

	/** Raised card inside the setup view ("My clan" plaque). */
	public static int socialGuiInnerCardFill() {
		return Config.C_PREMIUM_THEME ? PANEL_ELEVATED : CLASSIC_SOCIALGUI_INNER_CARD;
	}

	/** Border of the inner setup card. */
	public static int socialGuiInnerCardBorder() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK_MID : CLASSIC_SOCIALGUI_INNER_CARD_BORDER;
	}

	/** Dark shadow outline wrapping inner cards and select buttons. */
	public static int socialGuiCardShadowBorder() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK : CLASSIC_SOCIALGUI_CARD_SHADOW;
	}

	/** Outer border wrapping the window, its content boxes and the invite pop-up. */
	public static int socialGuiOuterCardBorder() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK_MID : CLASSIC_SOCIALGUI_OUTER_BORDER;
	}

	/** Separator under the window title band. */
	public static int socialGuiSeparator() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK_MID : CLASSIC_SOCIALGUI_SEPARATOR;
	}

	/** Separators between setup-column groups and the close-button top edge. */
	public static int socialGuiColumnSeparator() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK_MID : CLASSIC_SOCIALGUI_OUTER_BORDER;
	}

	/** Thin rules between the search-detail stat rows. */
	public static int socialGuiDetailSeparator() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK : CLASSIC_SOCIALGUI_DETAIL_SEPARATOR;
	}

	/** Window title ("Clan Settings", "Party Invitation!"). */
	public static int socialGuiTitleText() {
		return Config.C_PREMIUM_THEME ? ACCENT_SECONDARY : 0xE5D8C0;
	}

	/** Standard body text (labels, instructions, table values). */
	public static int socialGuiText() {
		return Config.C_PREMIUM_THEME ? TEXT_PRIMARY : 0xf1f1f1;
	}

	/** Bright white emphasis text (invite lines, close-button label). */
	public static int socialGuiBrightText() {
		return Config.C_PREMIUM_THEME ? TEXT_PRIMARY : 0xffffff;
	}

	/** Warm orange accent (usernames, totals). */
	public static int socialGuiAccentText() {
		return Config.C_PREMIUM_THEME ? ACCENT_SECONDARY : 0xEFB063;
	}

	/** Lime-green stat labels in the search-detail view. */
	public static int socialGuiLabelAccent() {
		return Config.C_PREMIUM_THEME ? SUCCESS : 0xB5DC4F;
	}

	/** Off-white detail values (search-result titles, point totals). */
	public static int socialGuiDetailText() {
		return Config.C_PREMIUM_THEME ? TEXT_PRIMARY : 0xFBFBF9;
	}

	/** Tan helper hints ("Right-click on a box to change options."). */
	public static int socialGuiHintText() {
		return Config.C_PREMIUM_THEME ? TEXT_MUTED : 0xD9CD98;
	}

	/** Muted taupe line ("Settings for: <name>"). */
	public static int socialGuiMutedText() {
		return Config.C_PREMIUM_THEME ? TEXT_MUTED : 0xB39684;
	}

	/** Orange join-policy line on each search-result row. */
	public static int socialGuiSearchTitleText() {
		return Config.C_PREMIUM_THEME ? ACCENT_SECONDARY : 0xF2A967;
	}

	/** Orange value text ("My clan:", select-button primary labels). */
	public static int socialGuiValueText() {
		return Config.C_PREMIUM_THEME ? ACCENT_SECONDARY : 0xEA9F59;
	}

	/** Submit-button label ("Accept", "Submit", "Send Clan Request"). */
	public static int socialGuiSubmitLabel() {
		return Config.C_PREMIUM_THEME ? ACCENT_SECONDARY : 0xFF9530;
	}

	/** Pale secondary text on input/nav buttons. */
	public static int socialGuiSecondaryLabel() {
		return Config.C_PREMIUM_THEME ? TEXT_PRIMARY : 0xE3CCCF;
	}

	/** Close bar under the window; hover lightens the header brown. */
	public static int socialGuiCloseFill(boolean hovered) {
		return Config.C_PREMIUM_THEME ? (hovered ? PRESSED : PANEL_ELEVATED) : (hovered ? 0x442C13 : CLASSIC_SOCIALGUI_HEADER_BAND);
	}

	/** Page-tab buttons (Clanmates / Clan Setup / Clan Search). */
	public static int socialGuiNavFill(boolean checked, boolean hovered) {
		return Config.C_PREMIUM_THEME ? (checked ? SELECTION : hovered ? PRESSED : PANEL_ELEVATED) : (checked ? 0x332A22 : hovered ? 0x2A221B : 0x231B15);
	}

	/** Border of the page-tab buttons. */
	public static int socialGuiNavBorder() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK_MID : 0xA68B71;
	}

	/** Recessed input buttons (Clan Name / Clan Tag). */
	public static int socialGuiInputFill(boolean checked) {
		return Config.C_PREMIUM_THEME ? (checked ? SELECTION : OVERLAY_SCRIM) : (checked ? 0x332A22 : 0x3D3428);
	}

	/** Border of the input buttons. */
	public static int socialGuiInputBorder() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK_MID : 0x777775;
	}

	/** Search text-entry backing. Light in both modes: the Panel entry text is black (useAltColor=false) and the flag cannot change per theme without breaking classic. */
	public static int socialGuiSearchEntryFill() {
		return Config.C_PREMIUM_THEME ? TEXT_PRIMARY : 0xFBFCFE;
	}

	/** Search text-entry border. */
	public static int socialGuiSearchEntryBorder() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK_MID : 0x080809;
	}

	/** Right-click select buttons (setting pickers). */
	public static int socialGuiSelectFill() {
		return Config.C_PREMIUM_THEME ? PANEL_ELEVATED : 0x4F4841;
	}

	/** Border of the select buttons. */
	public static int socialGuiSelectBorder() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK_MID : 0x7C6C5C;
	}

	/** Submit-style buttons (Accept, Decline, Submit, Invite, Kick). */
	public static int socialGuiSubmitFill(boolean hovered) {
		return Config.C_PREMIUM_THEME ? (hovered ? PRESSED : PANEL_ELEVATED) : (hovered ? 0x423D2D : 0x403020);
	}

	/** Border of the submit-style buttons. */
	public static int socialGuiSubmitBorder() {
		return Config.C_PREMIUM_THEME ? BORDER_DARK_MID : 0x474745;
	}
}
