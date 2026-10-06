package orsc.graphics.gui;

import orsc.Config;

/**
 * Headless regression check for the one hard invariant of the Theme
 * migration (see scripts/check_theme_parity.sh):
 *
 * <ul>
 *   <li>With {@code C_PREMIUM_THEME} off, every themed accessor must return
 *       its exact inherited literal, so classic rendering stays byte-identical.</li>
 *   <li>With it on, every themed role must resolve to the intended premium
 *       token (compared against the token itself, not a copied value).</li>
 * </ul>
 *
 * Only the families exercised here are asserted; extend {@link #classic()} and
 * {@link #premium()} when a new family is migrated. Plain {@code main} on
 * purpose: the project has no test framework and this must run with the JDK 8
 * bundled in {@code Portable_Windows/}.
 */
public final class ThemeParityTest {

	private static int checks = 0;
	private static int failures = 0;

	public static void main(String[] args) {
		Config.C_PREMIUM_THEME = false;
		classic();

		Config.C_PREMIUM_THEME = true;
		premium();

		System.out.println((failures == 0 ? "OK" : "FAIL") + ": " + checks + " theme-parity checks, "
			+ failures + " failed.");
		if (failures != 0) {
			System.exit(1);
		}
	}

	/** Off-path: each accessor returns the literal the migration replaced. */
	private static void classic() {
		// Login / onboarding console (premium-only presentation; inert when off).
		expect("loginFrameFill", Theme.loginFrameFill(), 0);
		expect("loginFrameBorder", Theme.loginFrameBorder(), 0);
		expect("loginFrameInnerBorder", Theme.loginFrameInnerBorder(), 0);
		expect("loginFrameAccent", Theme.loginFrameAccent(), 0);
		expect("textEntryFocusUnderline", Theme.textEntryFocusUnderline(), -1);

		// Ironman setup window.
		expect("ironmanBodyFill", Theme.ironmanBodyFill(), 0x483E33);
		expect("ironmanWindowBorder", Theme.ironmanWindowBorder(), 0x2A2926);
		expect("ironmanHeadingText", Theme.ironmanHeadingText(), 0xFF981F);
		expect("ironmanInsetFill", Theme.ironmanInsetFill(), 0x534A3F);
		expect("ironmanInsetHoverFill", Theme.ironmanInsetHoverFill(), 0x675F56);
		expect("ironmanInsetBorder", Theme.ironmanInsetBorder(), 0x777775);
		expect("ironmanText", Theme.ironmanText(), 0xFFFFFF);
		expect("ironmanBadgeFill", Theme.ironmanBadgeFill(), 0x3A3026);
		expect("ironmanMenuFill", Theme.ironmanMenuFill(), 0x524B40);
		expect("ironmanCloseButtonFill(idle)", Theme.ironmanCloseButtonFill(false), 0x5F523C);
		expect("ironmanCloseButtonFill(hover)", Theme.ironmanCloseButtonFill(true), 0x544838);
		expect("ironmanClickBoxBorder", Theme.ironmanClickBoxBorder(), 0x464644);

		// Skill guide window.
		expect("skillGuidePanelFill", Theme.skillGuidePanelFill(), 0x989898);
		expect("skillGuideBorder", Theme.skillGuideBorder(), 0x000000);
		expect("skillGuideText", Theme.skillGuideText(), 0xFFFFFF);
		expect("skillGuideHeaderBand", Theme.skillGuideHeaderBand(), 0x6580B7);
		expect("skillGuideRowFill", Theme.skillGuideRowFill(), 0x45454545);
		expect("skillGuideButtonFill(idle)", Theme.skillGuideButtonFill(false, false), 0x333333);
		expect("skillGuideButtonFill(active)", Theme.skillGuideButtonFill(true, false), 16711680);
		expect("skillGuideButtonFill(hover)", Theme.skillGuideButtonFill(false, true), 16711680);
		expect("skillGuideTabFill(idle)", Theme.skillGuideTabFill(false, false), 0x333333);
		expect("skillGuideTabFill(current)", Theme.skillGuideTabFill(true, false), 0x659CDE);
		expect("skillGuideTabFill(hover)", Theme.skillGuideTabFill(false, true), 0x6580B7);
		expect("skillGuideButtonBorder", Theme.skillGuideButtonBorder(), 0x242424);

		// Legacy custom-interface palette (points / quest / experience / death /
		// territory panels).
		expect("legacyPanelFill", Theme.legacyPanelFill(), 0x989898);
		expect("legacyPanelBorder", Theme.legacyPanelBorder(), 0x000000);
		expect("legacyPanelText", Theme.legacyPanelText(), 0xFFFFFF);
		expect("legacyControlFill", Theme.legacyControlFill(), 0x333333);
		expect("legacyControlHoverFill", Theme.legacyControlHoverFill(), 0x6580B7);
		expect("legacyControlActiveFill", Theme.legacyControlActiveFill(), 0xFF0000);
		expect("legacyControlBorder", Theme.legacyControlBorder(), 0x242424);

		// Skill-points allocation window.
		expect("pointsPanelFill", Theme.pointsPanelFill(), 0x3A2A10);
		expect("pointsPanelShade", Theme.pointsPanelShade(), 0x2A1C08);
		expect("pointsTitleFill", Theme.pointsTitleFill(), 0x4A3620);
		expect("pointsTitleText", Theme.pointsTitleText(), 0xFFFF00);
		expect("pointsControlActiveFill", Theme.pointsControlActiveFill(), 0x871E1E);

		// Achievement window.
		expect("achievementHeaderFill", Theme.achievementHeaderFill(), 0x313439);
		expect("achievementHeaderHoverFill", Theme.achievementHeaderHoverFill(), 0x263751);
		expect("achievementHeaderCheckedFill", Theme.achievementHeaderCheckedFill(), 0x659CDE);
		expect("achievementRule", Theme.achievementRule(), 0xBFA086);

		// Floating HUD controls (right-click menus, batch progress, party menu).
		expect("hudControlFill", Theme.hudControlFill(), 0x454545);
		expect("hudControlHoverFill", Theme.hudControlHoverFill(), 0xFFFFFF);
		expect("hudControlBorder", Theme.hudControlBorder(), 0xFFFFFF);
		expect("hudControlHoverBorder", Theme.hudControlHoverBorder(), 0x454545);
		expect("hudText", Theme.hudText(), 0xFFFFFF);
		expect("hudHoverText", Theme.hudHoverText(), 0xFF0000);

		// Progress bars (batch window, fishing-trawler gauge).
		expect("progressPanelFill", Theme.progressPanelFill(), 0xFFFFFF);
		expect("progressTrackFill", Theme.progressTrackFill(), 0xFFFFFF);
		expect("progressFill", Theme.progressFill(), 0x0000FF);

		// Party status health bars.
		expect("partyHealthEmptyFill", Theme.partyHealthEmptyFill(), 0xFF0000);
		expect("partyHealthFill", Theme.partyHealthFill(), 0x00FF00);

		// Online-player list title band.
		expect("onlineListTitleFill", Theme.onlineListTitleFill(), 3093151);
		expect("onlineListTitleHoverFill", Theme.onlineListTitleHoverFill(), 0x7E8D09);

		// Bank PIN window.
		expect("bankPinPanelFill", Theme.bankPinPanelFill(), 0x483E33);
		expect("bankPinPanelBorder", Theme.bankPinPanelBorder(), 0x4E4836);
		expect("bankPinAltFill", Theme.bankPinAltFill(), 0x524B31);
		expect("bankPinAltBorder", Theme.bankPinAltBorder(), 0x565040);
		expect("bankPinGold", Theme.bankPinGold(), 0xBF751D);
		expect("bankPinBrightGold", Theme.bankPinBrightGold(), 0xFF981F);
		expect("bankPinTitleText", Theme.bankPinTitleText(), 0x9B0907);
		expect("bankPinDigitFill", Theme.bankPinDigitFill(), 0x4C0E09);
		expect("bankPinDigitHoverFill", Theme.bankPinDigitHoverFill(), 0x63140B);
		expect("bankPinDigitBorder", Theme.bankPinDigitBorder(), 0xAB837F);

		// Crafting right-click menu (DoSkillInterface).
		expect("legacyMenuHeaderFill", Theme.legacyMenuHeaderFill(), 0x000000);
		expect("legacyMenuFill", Theme.legacyMenuFill(), 0x5C5548);
		expect("legacyMenuItemHoverText", Theme.legacyMenuItemHoverText(), 0xFF0000);
	}

	/** On-path: each themed role resolves to its intended premium token. */
	private static void premium() {
		expect("loginFrameFill", Theme.loginFrameFill(), Theme.OVERLAY_SCRIM);
		expect("loginFrameBorder", Theme.loginFrameBorder(), Theme.BORDER_DARK_MID);
		expect("loginFrameInnerBorder", Theme.loginFrameInnerBorder(), Theme.BORDER_DARK);
		expect("loginFrameAccent", Theme.loginFrameAccent(), Theme.ACCENT_PRIMARY);

		expect("ironmanBodyFill", Theme.ironmanBodyFill(), Theme.PANEL_ELEVATED);
		expect("ironmanWindowBorder", Theme.ironmanWindowBorder(), Theme.BORDER_DARK);
		expect("ironmanHeadingText", Theme.ironmanHeadingText(), Theme.ACCENT_PRIMARY);
		expect("ironmanInsetFill", Theme.ironmanInsetFill(), Theme.PANEL_INSET);
		expect("ironmanInsetHoverFill", Theme.ironmanInsetHoverFill(), Theme.PANEL_ELEVATED);
		expect("ironmanInsetBorder", Theme.ironmanInsetBorder(), Theme.BORDER_DARK_MID);
		expect("ironmanText", Theme.ironmanText(), Theme.TEXT_PRIMARY);
		expect("ironmanBadgeFill", Theme.ironmanBadgeFill(), Theme.BORDER_DARK);
		expect("ironmanMenuFill", Theme.ironmanMenuFill(), Theme.PANEL_ELEVATED);
		expect("ironmanCloseButtonFill(idle)", Theme.ironmanCloseButtonFill(false), Theme.PANEL_INSET);
		expect("ironmanCloseButtonFill(hover)", Theme.ironmanCloseButtonFill(true), Theme.PANEL_ELEVATED);
		expect("ironmanClickBoxBorder", Theme.ironmanClickBoxBorder(), Theme.BORDER_DARK_MID);

		expect("skillGuidePanelFill", Theme.skillGuidePanelFill(), Theme.PANEL_ELEVATED);
		expect("skillGuideBorder", Theme.skillGuideBorder(), Theme.BORDER_DARK);
		expect("skillGuideText", Theme.skillGuideText(), Theme.TEXT_PRIMARY);
		expect("skillGuideHeaderBand", Theme.skillGuideHeaderBand(), Theme.PANEL_ELEVATED);
		expect("skillGuideRowFill", Theme.skillGuideRowFill(), Theme.PANEL_INSET);
		expect("skillGuideButtonFill(active)", Theme.skillGuideButtonFill(true, false), Theme.PRESSED);
		expect("skillGuideButtonFill(hover)", Theme.skillGuideButtonFill(false, true), Theme.PRESSED);
		expect("skillGuideButtonFill(idle)", Theme.skillGuideButtonFill(false, false), Theme.PANEL_ELEVATED);
		expect("skillGuideTabFill(current)", Theme.skillGuideTabFill(true, false), Theme.SELECTION);
		expect("skillGuideTabFill(hover)", Theme.skillGuideTabFill(false, true), Theme.PANEL_INSET);
		expect("skillGuideTabFill(idle)", Theme.skillGuideTabFill(false, false), Theme.PANEL_ELEVATED);
		expect("skillGuideButtonBorder", Theme.skillGuideButtonBorder(), Theme.BORDER_DARK_MID);

		expect("legacyPanelFill", Theme.legacyPanelFill(), Theme.PANEL_ELEVATED);
		expect("legacyPanelBorder", Theme.legacyPanelBorder(), Theme.BORDER_DARK);
		expect("legacyPanelText", Theme.legacyPanelText(), Theme.TEXT_PRIMARY);
		expect("legacyControlFill", Theme.legacyControlFill(), Theme.PANEL_ELEVATED);
		expect("legacyControlHoverFill", Theme.legacyControlHoverFill(), Theme.HOVER);
		expect("legacyControlActiveFill", Theme.legacyControlActiveFill(), Theme.PRESSED);
		expect("legacyControlBorder", Theme.legacyControlBorder(), Theme.BORDER_DARK_MID);

		expect("pointsPanelFill", Theme.pointsPanelFill(), Theme.PANEL_INSET);
		expect("pointsPanelShade", Theme.pointsPanelShade(), Theme.PANEL_INSET);
		expect("pointsTitleFill", Theme.pointsTitleFill(), Theme.PANEL_ELEVATED);
		expect("pointsTitleText", Theme.pointsTitleText(), Theme.ACCENT_SECONDARY);
		expect("pointsControlActiveFill", Theme.pointsControlActiveFill(), Theme.PANEL_INSET);

		expect("achievementHeaderFill", Theme.achievementHeaderFill(), Theme.PANEL_ELEVATED);
		expect("achievementHeaderHoverFill", Theme.achievementHeaderHoverFill(), Theme.PANEL_INSET);
		expect("achievementHeaderCheckedFill", Theme.achievementHeaderCheckedFill(), Theme.SELECTION);
		expect("achievementRule", Theme.achievementRule(), Theme.BORDER_LIGHT_MID);

		expect("hudControlFill", Theme.hudControlFill(), Theme.PANEL_ELEVATED);
		expect("hudControlHoverFill", Theme.hudControlHoverFill(), Theme.HOVER);
		expect("hudControlBorder", Theme.hudControlBorder(), Theme.BORDER_LIGHT);
		expect("hudControlHoverBorder", Theme.hudControlHoverBorder(), Theme.BORDER_DARK_MID);
		expect("hudText", Theme.hudText(), Theme.TEXT_PRIMARY);
		expect("hudHoverText", Theme.hudHoverText(), Theme.DANGER);

		expect("progressPanelFill", Theme.progressPanelFill(), Theme.PANEL_ELEVATED);
		expect("progressTrackFill", Theme.progressTrackFill(), Theme.PANEL_INSET);
		expect("progressFill", Theme.progressFill(), Theme.ACCENT_PRIMARY);

		expect("partyHealthEmptyFill", Theme.partyHealthEmptyFill(), Theme.DANGER);
		expect("partyHealthFill", Theme.partyHealthFill(), Theme.SUCCESS);

		expect("onlineListTitleFill", Theme.onlineListTitleFill(), Theme.PANEL_ELEVATED);
		expect("onlineListTitleHoverFill", Theme.onlineListTitleHoverFill(), Theme.HOVER);

		expect("bankPinPanelFill", Theme.bankPinPanelFill(), Theme.PANEL_ELEVATED);
		expect("bankPinPanelBorder", Theme.bankPinPanelBorder(), Theme.BORDER_DARK);
		expect("bankPinAltFill", Theme.bankPinAltFill(), Theme.PANEL_INSET);
		expect("bankPinAltBorder", Theme.bankPinAltBorder(), Theme.BORDER_DARK_MID);
		expect("bankPinGold", Theme.bankPinGold(), Theme.ACCENT_SECONDARY);
		expect("bankPinBrightGold", Theme.bankPinBrightGold(), Theme.ACCENT_PRIMARY);
		expect("bankPinTitleText", Theme.bankPinTitleText(), Theme.DANGER);
		expect("bankPinDigitFill", Theme.bankPinDigitFill(), Theme.PANEL_INSET);
		expect("bankPinDigitHoverFill", Theme.bankPinDigitHoverFill(), Theme.PANEL_ELEVATED);
		expect("bankPinDigitBorder", Theme.bankPinDigitBorder(), Theme.BORDER_LIGHT_MID);

		expect("legacyMenuHeaderFill", Theme.legacyMenuHeaderFill(), Theme.PANEL_INSET);
		expect("legacyMenuFill", Theme.legacyMenuFill(), Theme.PANEL_ELEVATED);
		expect("legacyMenuItemHoverText", Theme.legacyMenuItemHoverText(), Theme.DANGER);
	}

	private static void expect(String name, int actual, int expected) {
		checks++;
		if (actual != expected) {
			failures++;
			System.out.println("FAIL " + name + ": expected 0x" + Integer.toHexString(expected)
				+ " but got 0x" + Integer.toHexString(actual));
		}
	}
}
