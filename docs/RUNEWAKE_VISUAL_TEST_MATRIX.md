# RuneWake Visual Test Matrix

Rule: a cell is only marked **passed** when the check was actually run or
inspected in the listed session. Untested items stay **unverified** — an
honest "unverified" is required, not optimistic. This environment has no
display, so interactive/visual checks need a human with the client running.

Environment note: all builds verified via the bundled
`Portable_Windows/zulu8…jdk8` + `apache-ant-1.10.5` with `JAVA_HOME` pointed
at the portable JDK (the system Java is a JRE and cannot run `ant compile`).

## Compile / build matrix

| Target | Command | Status | Session |
|---|---|---|---|
| Client (base + PC sources) | `ant -f Client_Base/build.xml compile` | **passed** | 2026-09-26 (after the legacy-panel theme sweep and the texture-pipeline extraction) |
| Server core | `ant -f server/build.xml compile_core` | **passed** | 2026-09-26 (after the full dependency refresh) |
| Server plugins | `ant -f server/build.xml compile_plugins` | **passed** | 2026-09-26 (after the full dependency refresh) |
| Launcher | `ant -f PC_Launcher/build.xml compile` | **passed** | 2026-09-26 (verification build; no launcher code changed) |

## Resolution matrix (interactive — human required)

Nothing in this table has been visually verified in this environment.
Screens: launcher, login, character creation, in-game (inventory/minimap/
chat/magic/settings/social/stats/quests/clan), bank, shop, trade, duel,
dialogues, Input-X, report abuse.

| Resolution | Auto scale | Notes |
|---|---|---|
| 1024×768 | unverified | |
| 1280×720 | unverified | enforced client minimum is 1280×732 |
| 1366×768 | unverified | |
| 1600×900 | unverified | |
| 1920×1080 | unverified | |
| 1920×1200 | unverified | |
| 2560×1440 | unverified | prior sessions' screenshots came from ~this size |
| 3440×1440 (ultrawide) | unverified | check panel clamping specifically |
| 3840×2160 | unverified | |

## UI-scale settings matrix

Auto / 100 / 125 / 150 / 175 / 200 / 250: **none exposed as a user setting
yet.** Current state: `renderingScalar` (canvas integer-scaling, separate
concern) is exposed in settings; the UI design-space scale (`uiScale`) is
derived automatically from window size only. Adding a discrete UI-scale
selector remains future work (see UI_SCALING_PLAN / ROADMAP 7p notes).

## Per-screen checklist template

For each screen × resolution: clipping / overlap / legibility / hitboxes /
hover states / modal click-through / focus / branding. All currently
**unverified** for the 2026-09-16 pass; the items below are the ones most
worth checking first after this pass's changes:

1. Settings tab (all 4 sub-tabs) at 2 sizes — this pass touched its theme
   colours (flag off ⇒ should be pixel-identical to before).
2. Loading screen + Discord presence — branding strings changed.
3. Android cold start — manifest label changed (reinstall required to see).
4. Bank + inventory drag at ≥2560 wide — flagged higher-risk in ROADMAP 7.

## Additions from the 2026-09-17 pass (all unverified visually)

First priorities for the human pass after the bank/login/character-creation
slices (commits 1e46ff66c, 23fb70257, d71159921):

1. **Bank (custom interface)**: classic mode should look unchanged; premium
   mode check slot/selected/hover/destructive contrast, context menu
   legibility, search focus border.
2. **Login**: focused-field rune-blue underline (premium only); status/error
   text fits the (now taller) status scrim at high font scale; BLUEBAR
   bottom strip scaling.
3. **Registration**: heading + Android keyboard hint no longer overlap
   instruction rows (Android only for the hint).
4. **Character creation**: bronze panel chrome; player-mode/xp-rate picker
   states (accent selected, muted idle) in premium; colour chips visible
   inside each picker box and not colliding with the box sprites; classic
   mode should render exactly as before (no chips, inherited list colours).
5. Per-resolution matrix above still applies to these screens.

## Additions from the 2026-09-24 pass (all unverified visually)

First priorities for the human pass after the minimap/compass slice
(commit e614ab6e8):

1. **Minimap (custom UI, premium mode)**: viewport shows a subtle inset
   backdrop with a bronze frame instead of raw black; the frame's
   corners/edges do not collide with the terrain diamond; the compass dial
   sits on a translucent plate with a bevel ring and stays readable over
   bright terrain (desert, snow, cliffs).
2. **Minimap (classic mode)**: must be pixel-identical to previous builds
   (all new colours resolve to black and the added draws are no-ops).
3. Per-resolution matrix above still applies; check the frame at 1024×768
   (minimum) and 3840×2160 (max scale) specifically.

## Additions from the 2026-09-24 second pass (all unverified visually)

First priorities for the human pass after the zoom, supersample and
auction-house slices (commits 33c6df17d, 0c9321db1, 94b8d3541):

1. **Minimap zoom**: settings row cycles 100/150/200/75%; at each level
   the map fills sensibly, dots stay visible, and clicking a tile walks
   to the tile under the cursor (accuracy matters most at 200%).
2. **Supersampled map pixels**: walls read as continuous soft lines
   rather than single pixels; terrain colours unchanged overall (they
   are averaged, so check for washed-out water/roads); zoom 100% should
   look like the old map but smoother.
3. **Auction house (premium mode)**: tab/filter/checked button states
   legible (hover uses the pressed token under white text — verify
   contrast); armed-cancel rows clearly red; yellow headings now aged
   gold; item names and prices readable on inset panels.
4. **Auction house (classic mode)**: must be pixel-identical to previous
   builds (every accessor returns the inherited literal, including the
   0x45454545 and 0xfffffff quirks).
5. Per-resolution matrix above still applies to all of the above.

## Additions from the 2026-09-24 video pass (fixes 04f01db7f)

Human re-test requested at the recording's window (2496x1482, custom UI):

1. Settings tab: rows must be legible over bright terrain (alpha now 224).
2. Inventory: slot grid sits on an opaque backdrop; world shows only
   through slot cells, items still render inside cells.
3. Map tab: box fills the tab area (scaled), dots/compass visible and
   proportioned, clicking a tile walks to the tile under the cursor,
   zoom setting still composes with the scale.
4. Classic UI (authentic tab bar): unchanged — map position, size and
   click region must be pixel-identical to previous builds.

## Additions from the 2026-09-24 site and side-panel pass

All items below are unverified in the agent environment (no native display).
The landing page can be checked in a browser separately from the Java client.

1. **Custom social/clan/ignore panel** (`24ff007f0`): the new opaque
   `panelFill` backdrop must cover the tab strip and list body without
   changing the clan-tab stacked action-box height; world bleed should be
   limited to intentional inset cells.
2. **Custom magic/prayer panel** (`24ff007f0`): backdrop must include the
   Android cast-last-spell block when present and must not cover neighbouring
   tabs or clip the bottom edge.
3. **Custom stats/quests panel** (`24ff007f0`): backdrop must align with the
   OpenPK height override and remain opaque enough over bright terrain.
4. **Classic mode regression**: the three new backdrop draws are guarded by
   `C_CUSTOM_UI`; compare the inherited sprite-backed panels for pixel
   identity.
5. **Static landing page**: check `web/site/index.html` at narrow phone,
   tablet, and desktop widths; verify the hero image, document flow, and
   links to `../server-browser/` load when served from `web/`. The browser
   page is documentation, not a client visual test.
6. **Landing-page captures**: `frame-0120.jpg` and `frame-0221.jpg` are
   pre-fix gameplay captures used only as illustrative site art. They must
   not be treated as evidence that the current client panels look correct.

## Additions from the 2026-09-25 pass (all unverified visually)

The latest client working-tree pass adds a premium-only console frame behind
the welcome, existing-user, registration, and password-recovery forms. The
frame uses a dark translucent fill, a two-step bronze bevel, and restrained
rune-blue horizontal rules. The existing login status scrim now uses the same
Theme token family. No control geometry or hitbox was changed.

1. **Premium login**: verify the frame is centred behind each form, leaves
   the existing controls readable over `login.png`, and does not cover the
   rotating fallback background or bottom BLUEBAR. The registration heading
   should switch to light text in premium mode while remaining classic-black.
2. **Registration and recovery**: check the taller registration frame against
   both `wantEmail()` layouts and Android keyboard-hint spacing; check the
   recovery frame at the minimum and maximum UI scales for clipping.
3. **Status/error states**: exercise invalid credentials, connection failure,
   and account-creation responses; the status scrim should remain behind both
   status rows without making the rune-blue focus underline disappear.
4. **Classic mode**: the frame helper exits before drawing, and the status
   scrim resolves to its inherited black fill/alpha; compare against the
   previous build for pixel identity.
5. Per-resolution matrix above still applies; the human pass should include
   1280x732, 1920x1080, 2560x1440, and 3440x1440.

## Additions from the 2026-09-26 adaptive-console pass (all unverified visually)

The premium console frame is now measured from the form's own control bounds
(`Panel.getContentBounds()`) rather than per-screen hard-coded rectangles, and
the existing-user status scrim tracks the console width instead of spanning the
whole window. Classic mode still exits before drawing, so its output should be
byte-identical.

1. **Frame tracks the form**: on each of the welcome (free and members),
   existing-user, registration (`wantEmail()` true and false), and recovery
   screens, the console edges should sit an even padding outside the outermost
   control, with no control touching or crossing the bevel at 100% and at the
   maximum UI scale.
2. **Long text**: set a deliberately long `SERVER_NAME_WELCOME`/`WELCOME_TEXT`
   and confirm the welcome console clamps to the envelope rather than running
   off-screen, and that its text is still centred.
3. **Status scrim**: trigger an invalid-credential and a connection-failure
   status. The premium scrim should read as a band inside the console (not a
   full-width stripe across the splash art) and still back the whole status
   line; the focused-field underline must remain visible.
4. **Android layout**: confirm the measured frame follows the keyboard-offset
   welcome/login forms and does not clip the bottom of the password row.
5. **Classic regression**: with `C_PREMIUM_THEME` off, compare the login,
   registration, and recovery screens against the previous build; both the
   frame (never drawn) and the scrim (inherited full-width black) must be
   pixel-identical.

## Additions from the 2026-09-26 theme-migration pass (all unverified visually)

The Ironman setup window and the skill guide are now drawn from `Theme`
families. Classic values are preserved literal-for-literal (and the new
`scripts/check_theme_parity.sh` asserts this), so only premium mode changes.

1. **Ironman setup window** (`C_PREMIUM_THEME` on): confirm the window body,
   outer border, heading, inset plate, radio badges, sub-menu plate, close
   button (idle and hover) and both choice-box rows read as one coherent dark
   panel, that hover states are still distinguishable from idle, and that the
   orange heading/description text is legible on the premium plate.
2. **Skill guide window** (premium): confirm the translucent body still lets
   the world through where intended, the Level/Advancement header band and
   table text contrast, and that the selected tab, hovered tab and per-skill
   active button are each visually distinct from idle.
3. **Classic regression**: with `C_PREMIUM_THEME` off, open both windows and
   compare against the previous build; they must be pixel-identical (the
   parity guard covers the values, not the rendering).

## Additions from the 2026-09-26 legacy-panel theme sweep (all unverified visually)

The six legacy custom windows that draw straight onto the game surface
(Points, Points-to-GP, Territory Signup, Experience Config, Quest Guide,
Lost on Death) plus the achievement window now route their plate, border, text
and control colours through `Theme.legacy*` / `Theme.points*` /
`Theme.achievement*`. Off-path values are the exact inherited literals, so
classic mode should be unchanged.

1. **Premium appearance**: open each window with `C_PREMIUM_THEME` on. The
   0x989898 light-grey plate should now be a dark `PANEL_ELEVATED` plate, the
   black border and rules should still separate the plate from the world, and
   body text must stay legible against the darker fill (the inherited text was
   white on light grey; the premium text token is parchment on charcoal).
2. **Controls**: in each window, hover and press the close `X` and the
   checked/unchecked buttons. Idle/hover/checked must remain three visually
   distinct states - the premium palette moves all three, and the inherited
   "hover" was blue with "checked" red.
3. **Points window specifics**: the brown title band and yellow title text are
   the two colours with the least headroom for a dark theme; confirm the
   premium accent title still reads as a heading rather than blending into the
   plate.
4. **Achievement window**: confirm the header band, the reward-slot backing and
   the close-footer rule are still distinguishable from each other, and that
   the `@gre@`/`@yel@` status prefixes in the title still contrast.
5. **Classic regression**: with `C_PREMIUM_THEME` off, open all seven and
   compare against the previous build; they must be pixel-identical.
