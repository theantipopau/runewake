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
| Client (base + PC sources) | `ant -f Client_Base/build.xml compile` | **passed** | 2026-09-16 (×3: baseline, Theme migration, branding pass) |
| Server core | `ant -f server/build.xml compile_core` | **passed** | 2026-09-16 (baseline + after leak fixes) |
| Server plugins | `ant -f server/build.xml compile_plugins` | **passed** | 2026-09-16 (baseline + after leak fixes) |
| Launcher | `ant -f PC_Launcher/build.xml compile` | unverified this pass | — |

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
