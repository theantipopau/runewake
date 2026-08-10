# RuneWake Client Roadmap

Tracks the broader client-quality pass following the widescreen/UI-scaling work
(see `UI_SCALING_PLAN.md` for the detailed panel-by-panel technical log of that
specific effort). This file is the higher-level, cross-cutting to-do list —
update checkboxes as items land, and add new findings as they come up in
testing.

Project ships as **RuneWake**.

## Status legend
`[x]` done and compiled · `[~]` in progress / partially done · `[ ]` not started

---

## 1. Startup / tooling

- [x] **`run-client.bat` now boots the local server first** — was crashing
  with `Connection refused` on `localhost:43594` because it only built/ran the
  client. Now mirrors `Portable_Windows/run.cmd`: starts the server minimized,
  polls the configured port until it's listening, then builds and launches
  the client.

## 2. Functional bugs found this pass

- [x] **Right-click context menus (and trade/duel item menus) closing the
  instant the mouse drifts off them** — root cause: three separate
  "is the mouse still over this menu" checks (`drawMenu()` for the main
  "Choose option" menu, plus the trade-offer and duel-offer item menus) used
  a hardcoded `10`px tolerance margin around the menu box. As the window
  grew via widescreen support, that fixed 10px became a proportionally tiny
  target, so a normal mouse move off the menu exceeded it almost
  immediately. Fixed by wrapping all three margins in `ui(10)` (mudclient.java
  ~:3221, ~:4229, ~:6244).

## 3. Font scaling (implemented, tuned once, needs re-verification)

- [x] **Implemented** — `GraphicsController` now carries a `uiScale` field
  (kept in sync with `mudclient.uiScale` once per frame in `draw()`).
  `plotCharacter()` has a new block-scaling path (`plotLetterScaled()`) that
  activates whenever `uiScale > 1.0`; at exactly 1x it's byte-for-byte the
  original renderer (zero regression risk at baseline resolution).
  `stringWidth()` and `fontHeight()` scale the same way, so every existing
  centering/wrapping/button-fit call site should stay internally consistent.
- [x] **Tuned (2026-07-05): "font is a little too big"** — the first cut
  snapped to the nearest *whole* scale multiple (`Math.round(uiScale)`), so
  e.g. an actual `uiScale` of 2.7x rendered text at a full 3x — up to ~11%
  oversized relative to every other `ui()`-scaled element on screen, which
  all use precise fractional rounding (`Math.round(px * uiScale)`, no
  integer snap). Rewrote `plotCharacter()`/`plotLetterScaled()` to map
  destination pixels back to source glyph pixels via the `destWidth/srcWidth`
  ratio (same idea `spriteClipping` already uses for images), so text now
  scales at the *exact* same continuous rate as boxes/panels. Also fixed
  `stringWidth()`/`fontHeight()`/the per-character advance step to use the
  same precise rounding instead of the integer-multiple version.
- [x] **Tuned again (2026-07-05): "still too big, and doesn't scale between
  windowed/fullscreen — it's just all large"** — root cause: the window has
  an enforced minimum size of 1280x732 (`ScaledWindow.runInit()`), which
  alone already produces `uiScale = min(1280/512, 732/334) ≈ 2.15`. Since
  every window size the user can actually reach is at or above that
  minimum, `uiScale` never gets close to 1x in practice — so scaling font
  1:1 with `uiScale` (matching how boxes/panels scale) made text look
  uniformly oversized at every window size, with no small end of the range
  to contrast against. Added a separate, deliberately dampened
  `fontScale()` (`sqrt(uiScale)`, only used for text) — at the enforced
  minimum this gives ~1.47x instead of ~2.15x, still growing on bigger
  windows but sub-linearly rather than 1:1. `plotCharacter()`,
  `stringWidth()`, `fontHeight()`, the char-advance step, and
  `drawShadowText()` all switched from `uiScale` to `fontScale()`.
- [ ] **Needs live re-verification** — no display here. Please check size
  at both the minimum window and a larger one — it should now look
  noticeably smaller than before, and should visibly grow (not stay
  static) as you enlarge the window, since `fontScale()` still tracks
  `uiScale`, just more gently.

## 4. Entity/text "pop-in"

Investigated `Scene.java`'s render-candidate loop (~:2698):
`if (this.rot1024_zTop < var13 && var13 < this.fogEntityDistance)` is a
**hard binary cutoff** — an entity's model faces are either fully in the
render candidate list or not considered at all beyond `fogEntityDistance`.
Faces that *are* rendered do get a smooth darkening fade as they approach
that cutoff (via `fogSmoothingStartDistance`, confirmed in an earlier session
pass). But the 2D overlay layer drawn on top of each entity — name text,
health bars, hit-splat damage numbers, chat bubbles, all populated inside
`drawNPC()`/`drawPlayer()` (mudclient.java :6385 / :6636, invoked once per
frame per visible entity via the `MudClientGraphics.drawEntity()` callback)
— had **no fade of its own** and was only drawn at all once the entity's
model made it into that frame's render list. Net effect: as an entity
crosses into view distance, its model fades in gradually, but its overlay
text/bars snapped in at full brightness with no transition.

- [x] **Health bars now fade in with distance** (2026-07-05) — no true
  world-space distance value is available at this 2D layer (only the
  already-perspective-projected on-screen `width`/`height` passed into
  `drawNPC`/`drawPlayer`), so used that as the fade signal instead: added
  `computeDistanceFadeAlpha(projectedWidth)` (mudclient.java, near
  `drawCharacterOverlay()`) — 0 alpha below 4px projected width, ramping
  linearly to the original full 192 alpha by 20px. Threaded a new
  `characterHealthAlpha[]` parallel array through both population sites
  (drawNPC ~:6602, drawPlayer ~:6809) into `drawCharacterOverlay()`'s
  `drawBoxAlpha()` calls, replacing the old hardcoded `192`. Low risk: only
  touches an already alpha-capable primitive (`drawBoxAlpha`), no new
  drawing code path.
- [x] **Chat-bubble text and hit-splat damage numbers now fade too
  (2026-07-05)** — rather than adding true alpha-blended text rendering
  (which would mean new draw-method overloads in the same renderer code
  just changed for font scaling, and no way to verify it visually here),
  used a lower-risk approximation: `fadeColorByAlpha()`/`fadeColorByDistance()`
  blend the text's *color* toward black in proportion to the same 0-192
  fade value the health bar uses, rather than true transparency. Reads as
  "fading in" against the game's dark 3D backdrop without touching the text
  renderer at all — just passes an already-dimmed color into the exact same
  `drawColoredStringCentered()`/`drawWrappedCenteredString()` calls that
  were already there. Added `characterDialogAlpha[]` (mirrors
  `characterHealthAlpha[]`) for chat-bubble text, populated at the same two
  sites as the health bar fade; hit-splat damage numbers (`drawNPC` ~:6680,
  `drawPlayer` ~:6886) fade inline since they're drawn immediately rather
  than through the deferred overlay pass.
  **Known limitation of this approach**: since it's a color blend, not real
  transparency, fully-faded text blends toward *black* specifically — it'll
  read as "fading in" against typical dark terrain/sky, but could look like
  a faint black smudge over a bright background (e.g. snow). If that reads
  wrong in practice, the real fix is true alpha-blended text via the
  antialiased draw path, which is a larger, separate change.
- [x] **Player floating name/clan tags — found and faded too** (`drawPlayer`
  ~:6843, gated by `S_SHOW_FLOATING_NAMETAGS`/`C_NAME_CLAN_TAG_OVERLAY`).
  Same `fadeColorByDistance()` applied to both the name (`drawShadowText`)
  and clan tag (`drawColoredString`). NPCs don't have an equivalent floating
  name tag in this codebase (only players show names/clan tags), so nothing
  else needed there.

## 5. World geometry / wall texture pop-in

- [x] **Fixed (2026-07-05): "wall texture is still popping in and out"** —
  same root cause class as section 4, but for static landscape/wall
  geometry rather than entities. `Scene.java`'s landscape culling (~:2613)
  also does a hard cutoff at `fogLandscapeDistance`, with a fade zone before
  it (`fogSmoothingStartDistance`) meant to darken faces gradually as they
  approach the edge. That fade zone was a **fixed 100-unit buffer**
  (mudclient.java ~:5253/5258/5264: `fogSmoothingStartDistance = fogDistance
  - 100`), regardless of how large `fogDistance` itself grew with window
  width. At a wide window `fogDistance` can be 7000+ units out, so a fixed
  100-unit fade zone is under 1.5% of the visible range — the fade
  completes almost instantly once a wall crosses into it, reading as a hard
  pop rather than a fade, and got more noticeable after the earlier
  widescreen pass increased the distance multiplier. Fixed by making both
  the fade-zone width and its falloff rate scale with `fogDistance`
  (`smoothingZone = max(100, fogDistance / 5)`, `fogZFalloff = max(1,
  smoothingZone / 100)` — the falloff divisor is scaled down proportionally
  so the *total* darkening intensity at the cutoff edge stays the same as
  the original design, only spread over a proportionally wider distance).
  Applied to all three fog-distance branches (`C_HIDE_FOG` on/off,
  interlaced/not). This affects `fogEntityDistance` too (computed alongside
  `fogLandscapeDistance` in the same branches), so it should also make the
  section-4 entity pop-in read less abruptly even before the text-alpha
  follow-up lands.
- [ ] **Needs live re-verification** — no display here. Walls/floors should
  now fade into view over a noticeably longer stretch as you approach the
  edge of your draw distance, rather than snapping into existence.

## 6. Texture quality

- [x] Box-blur pre-pass on 3D world textures (before 256-color quantization)
  and bilinear sprite scaling — both already implemented in an earlier
  session, confirmed still present and wired up (`boxBlurTexture()` called
  from both `loadTextures()` and `loadTexturesAuthentic()`).
- [x] **Bumped blur strength (2026-07-05)** — user reported "textures
  haven't been updated," consistent with the single-pass blur being too
  subtle to register. Changed `loadTextures()`/`loadTexturesAuthentic()` to
  run `boxBlurTexture()` **twice** in sequence (both passes still happen
  before the transparency-marker restore, so the magenta transparency
  marker still never gets blurred into neighboring texels — same invariant
  as before, just checked it still holds with two passes). Reads closer to
  a small gaussian blur than a single 3x3 box. Cheap (runs once at texture
  load, not per-frame) and easy to dial back to one pass if two reads as
  too soft/blurry in practice.
- [ ] The bigger lever — true higher-resolution textures — still needs
  either new art assets, or a deep, risky rewrite of `Scene.java`'s
  perspective-correct rasterizer to bypass the 256-color quantization
  entirely; not attempted without explicit direction given the risk/effort.
- [ ] **"NPC quality" and "models"** (from your "keep improving" list) —
  didn't act on these yet; both are too open-ended to safely guess at
  without more specifics (NPC quality could mean combat AI, dialogue
  variety, pathing, or animation smoothness; "models" could mean 3D mesh
  detail, which would need new assets, or something else entirely). Rather
  than make a risky guess and possibly do the wrong thing, flagging for you
  to point at something concrete — happy to dig in once I know which.
- [ ] **Menu-bar tab-icon sprite** — re-investigated this session: traced
  the exact low-level draw call (`GraphicsController.a(sprite, ..., 128,
  ...)`) down to its pixel-blend formula and confirmed the `128` isn't a
  size parameter at all — it's a 50%-alpha blend weight (`256 - alpha`
  formula against the background). Switching to the scaled `drawSprite()`
  overload used elsewhere would use a *different* masking parameter and
  almost certainly drop that translucency, with no way for me to see the
  result and confirm. Staying deferred — this one specifically needs a
  human with a display, not more code-reading.

## 7. UI scaling remainder (carried over from `UI_SCALING_PLAN.md`)

- [x] **Fixed (2026-07-05): login screen overlapping/cut-off buttons** —
  confirmed by screenshot ("Ok"/"Cancel"/"Forgot password" overlapping,
  "...rgot passwo" truncated at the panel edge). Root cause: `createLoginPanels()`
  (mudclient.java ~:2121-2289, builds the welcome/"New or Existing User"
  screen, the username+password login screen, and the account-registration
  form) had never been touched by the widescaling pass — every button/box/
  text-entry dimension was a bare 512-baseline pixel literal. This was
  survivable when font rendering was also unscaled (both box and text were
  consistently tiny), but became actively broken once font scaling shipped
  this session: the *text* got bigger while the *boxes* stayed the original
  size, so labels started overflowing their buttons and overlapping
  neighboring rows. Fully `ui()`-wrapped all three panels — carefully, since
  `addCenteredTextEntry`/`addLeftTextEntry`/`addScrollingList2` take a
  character-limit argument (e.g. max username length) alongside their pixel
  arguments, and those must **not** be scaled. Also fixed `createMessageTabPanel()`
  (the chat box, same unscaled-since-creation pattern) as a precaution, even
  though its interior controls also get corrected via `repositionCustomUI()`/
  `repositionAuthenticUI()` on a resize event.
- [x] **Recovery/password/contact/character-creation panels — done
  (2026-07-05).** Bigger job than originally scoped: these
  (`createRecoveryQuestionPanel()` ~:1680, `createPasswordRecoveryPanel()`
  ~:1856, `createContactDetailsPanel()` ~:1910, `createAppearancePanel()`
  ~:2014, plus the character-preview sprite renderer
  `drawAppearancePanelCharacterSprites()` ~:2677) didn't use
  `halfGameWidth()`/`halfGameHeight()` for centering at all — they were
  hardcoded to absolute positions like `256`/`151`/`361`/`170`/`455` that
  only made sense on the original fixed 512-wide canvas, unlike the login
  panels (which at least centered dynamically and just needed offset
  scaling). Converted every absolute position to
  `halfGameWidth() ± ui(offset-from-center)` first, then scaled sizes —
  e.g. `151` → `halfGameWidth() - ui(105)`, derived from `256 - 151 = 105`.
  Character-creation's 3D preview sprite positioning
  (`drawAppearancePanelCharacterSprites`) had to be fixed in lockstep with
  the panel's own box positions (`createAppearancePanel`) since they share
  the same `(140+116)/factor` center-column math — fixing one without the
  other would have made the character preview visually misaligned with its
  labels/arrow-buttons. Left the sprite-clip mask/blend byte constants
  (`(byte) 105`, `(byte) 110`) alone — confirmed via the draw method's
  signature these aren't pixel dimensions, just color/blend parameters, so
  scaling them would have been wrong. Character creation was called out as
  worth doing before launch (every new account hits it) — now done.
  Recovery/contact panels are rare account-recovery flows but got the same
  fix at no extra cost since the pattern and risk were identical.
- [x] **Remaining unscaled dialogs/popups — done (2026-07-06).** Systematic
  audit of `mudclient.java` via `ui()`-density sampling across every
  `drawDialog*`/`drawPopup*` function turned up a batch that had never been
  touched by any scaling pass (all sat at effectively 0% `ui()` coverage,
  same "boxes stayed 512-baseline, text grew" failure mode as the login
  screen). Fixed and compile-verified each:
  - `drawDialogCombatStyle()`, `drawDialogOptionsMenu()`,
    `drawDialogWildWarn()`, `drawDialogServerMessage()`,
    `drawDialogLogout()`, `drawDialogueChangePassword()`, `showItemModX()`
    / `drawInputX()`, `drawDialogWelcome()` (welcome + security-tip-of-day
    screen), `drawPopupSocial()` (add friend/message/add ignore popups).
  - Found and fixed a few latent (non-scaling) bugs along the way:
    `drawDialogueChangePassword()`'s click-outside-to-close hit-test used
    stale hardcoded bounds that didn't match the actual (dynamically
    positioned) box; `drawInputX()` assumed its box was always exactly
    400px wide via a hardcoded half-width when `showItemModX()` actually
    grows it based on content; `drawDialogWelcome()`'s "cancel security
    request" hit-test used screen-absolute bounds (`106`/`406`) instead of
    being relative to the dialog's own (dynamic) position, unlike its
    sibling hit-tests in the same function — only correct at the original
    512-wide baseline.
  - Also fixed the always-on-screen chat backlog overlay (bottom-left
    message history, `drawUiTab0()` draw call + its click-to-open-menu
    hit-test) — position/line-spacing/bottom-offset (`7`/`12`/`18`/`30`)
    were bare pixel literals with no `ui()` at all, so at wide/tall windows
    the now-larger scaled font would start overlapping between lines.
  - Spot-checked the remaining right-click tab panels
    (`drawUiTab1`/`drawUiTab5`/`drawUiTabMagic`/`drawUiTabMinimap`) and
    `drawDialogDuel`/`drawDialogDuelConfirm`/`drawDialogShop`/
    `drawDialogTrade`/`drawPopupReport` — all already well-covered by
    `ui()` from earlier passes; the low raw hit-count on some of these was
    just sprite-heavy code with fewer literal-bearing lines, not a gap.
    `drawDialogBank()` is a thin delegate to `Bank.onRender()` (a separate
    class, out of scope for this file-scoped audit).
- [x] **Bank interface — done (2026-07-06).** Following up on the
  `drawDialogBank()` note above: both bank UI classes it delegates to were
  completely unscaled (0 `ui()` calls each) — the single biggest remaining
  gap found this session, since the bank is one of the most-used screens in
  the game.
  - `BankInterface.java` (`Client_Base/src/com/openrsc/interfaces/misc/`)
    — the **authentic/default bank UI** (active whenever `want_custom_banks`
    is `false`, which is the default in every shipped `.conf`). Fully
    scaled: box/slot grid positions, the withdraw/deposit quantity-button
    row, page-selector tabs, and their hit-tests all rewrapped in `ui()`,
    keeping click targets pixel-matched to what's actually drawn.
  - `CustomBankInterface.java` — the opt-in richer bank UI (drag-and-drop
    reordering, search, tabs, presets, equipment view, right-click
    withdraw/deposit menus). Same treatment across ~1400 lines: every
    drawn box/sprite/text position and its matching hit-test scaled
    together, while leaving list capacities (e.g. the 40-items-per-page,
    500-slot scroll list), alpha/color values, and keycodes untouched
    since those aren't pixel dimensions.
  - Enabler: `mudclient.ui()`/`halfGameWidth()`/`halfGameHeight()` were
    `private`, so these two classes (a different package) couldn't call
    them — widened to `public` (pure visibility change, no behavior change)
    rather than duplicating the scale math in two more places.
  - Compiled clean after every change. **Higher risk than the dialogs
    fixed above and not yet live-tested** — this interface has real
    drag-and-drop and multi-step right-click menus I can't exercise here,
    so please specifically test: withdrawing/depositing at various window
    sizes, the reorganize-mode drag, and (if `want_custom_banks: true`)
    presets and the equipment view.
- [x] **`drawUiTab1()` equipment-tab sub-branch — done (2026-07-06).**
  Follow-up on the "found but not fixed" note above: turned out to be the
  whole `tabEquipmentIndex == 1` branch (~:8235-8340 — equipment slot
  grid, the "kept on death" preview icon, per-slot stat text, and their
  click hit-tests) plus the equipment/inventory sub-tab selector footer
  below it (~:8343-8372), not just the preview icon. All were raw
  512-baseline literals with no `ui()`. Fixed both, including wrapping
  `equipIconXLocations[]`/`equipIconYLocations[]` (the per-slot icon
  offset tables) the same way the bank preset-view fix did. Compiled
  clean.
- [x] **Friends/ignore/clan list truncation + column position — done
  (2026-07-06).** Found while scanning `drawUiTab5()` (social/clan panel)
  for the same class of bug: the long-name truncation threshold
  (`stringWidth(...) > 120`, three call sites — friends list, ignore
  list, clan member list) and the "Remove" button's column position
  (embedded as `getGameWidth() - 73` in the list-entry string) were both
  bare pixel literals. At high `uiScale` this made names truncate more
  aggressively than intended and put the Remove button in the wrong
  column. Also fixed `maxWidth`/`minWidth` (`getGameWidth() - 23/83`),
  the click-bound literals used to detect clicks on that Remove button
  across all three lists. Compiled clean.
- [x] **Always-visible gameplay HUD — done (2026-07-06).** Widened the
  search to `getGameWidth()/getGameHeight() ± literal` across the whole
  file (the pattern that already caught the social/clan list bug above)
  and found a cluster of core HUD elements — shown during ordinary
  gameplay, not just opt-in screens — that had never been touched by any
  scaling pass:
  - **Context-menu screen-edge clamping** (`this.menuY = this.mouseY - 7`
    and the `getGameHeight()-19`/`getGameWidth()-2` clamp bounds) — the
    right-click "Choose option" menu could run off-screen at an
    increasingly wrong margin as window size grew.
  - **Bottom chat-tab bar** (`drawChatMessageTabs()` — the "All messages
    / Chat history / Quest history / Private history / Clan history or
    Report Abuse" tab strip shown at the bottom of the screen at all
    times) — both the draw positions and, separately, the click
    hit-tests for switching tabs (found further down in the input-handling
    code) were on entirely separate unscaled literals that happened to
    agree only at the original 512-wide baseline.
  - **Server-restart countdown, XP-elixir timer, and the kill-feed
    overlay** (`C_KILL_FEED` — top-right death announcements) — all
    positioned with bare offsets from `getGameWidth()`/`getGameHeight()`.
  - **Wilderness skull + level indicator** (top-right, shown whenever the
    player is in the wilderness) — same issue.
  - **XP-drop notifications** (`XPNotification`'s initial `x`/`y` and the
    fade-out/removal thresholds that check `xpdrop.y`) — the spawn
    position and its own despawn-distance checks were on different scales
    from each other, so at high `uiScale` a drop could visually still be
    on-screen while the code already treated it as past the removal
    threshold.
  - **`getUITabsY()`** — this helper (used as the `maxY` base position by
    every right-click UI tab: inventory, magic, social, minimap, etc. —
    see section 7's bank/equipment-tab fixes above) itself returned a raw
    unscaled `getGameHeight() - 32 - 10` / `3`, while every caller already
    added its own `ui()`-wrapped offset on top. Fixing this one helper
    corrects the base position for all of those tabs at once when
    `C_CUSTOM_UI` is enabled.
  - Inventory item-count badge (`"n/30"` top-right of the inventory tab)
    — position offsets only; `/30` is the max-slot count, left alone.
  - Compiled clean after each fix.
  - **Not touched (lower priority, gated behind non-default flags):** the
    `Config.DEBUG`/`S_SIDE_MENU_TOGGLE` developer debug overlay (FPS,
    camera position, tile coords) and an Android-specific settings-button
    block — both real but only visible with flags off by default, so
    deprioritized versus the always-on HUD above.
- [x] **Sleep screen, login-screen viewport backdrops, and login status
  bar — done (2026-07-06).** Found continuing the same
  `getGameWidth/Height ± literal` sweep further across the file:
  - The full-screen **"You are sleeping"** overlay (`isSleeping` branch —
    text positions, the input box, and the border around the "click here
    for a new word" sprite) was entirely unscaled, as was its separate
    click hit-test (`didClickForNewCaptcha()`, a different function using
    its own independent hardcoded bounds that only lined up with the
    drawn text at the original baseline).
  - `renderLoginScreenViewports()` — builds the three rotating 3D
    background snapshots shown behind the login screen. Its top/bottom
    letterbox gradient bars and corner logo placement were unscaled. Also
    fixed a **copy-paste inconsistency** in the source: of its three
    near-identical blocks (one per snapshot), the 2nd differed slightly
    from the 1st and 3rd (a `- var9` term present in one, missing in the
    other two) — preserved that existing asymmetry exactly rather than
    "correcting" it to match, since it's unclear whether it was intentional
    or itself a pre-existing minor bug, and this pass is about scaling,
    not changing behavior.
  - Login status-message background bar (`loginScreenNumber == 2`).
  - Right-click "Choose option" menu's screen-edge clamping (separate
    from the earlier context-menu tolerance-margin fix in section 2 —
    this is the initial spawn-position offset and clamp bounds, not the
    "stays open" tolerance).
  - All compiled clean; also caught and fixed one self-inflicted bug from
    this same pass — `replace_all` silently skipped two of three
    identical-looking code blocks in `renderLoginScreenViewports()`
    because it matched a substring shared by only one of them, so the
    other two were left unscaled until a follow-up grep caught it.

## User-reported bugs after live testing (2026-07-06)

- [x] **Minimap renders as a small diamond adrift in an oversized square.**
  Root cause, different from every other scaling bug this session: the
  minimap *terrain* is a rotating native-resolution sprite
  (`drawMinimapSprite()` in `GraphicsController`) with no scale factor of
  its own — it always renders at its original pixel size no matter what.
  An earlier pass (not this session) had scaled the minimap *viewport
  box* around it (`var4`/`var5`, the clip rectangle) to grow with the
  window, so at any resolution above baseline the box grew but the map
  graphic inside it stayed the same small size — exactly "a diamond
  trying to fit into a square." Properly fixing the rotation math itself
  to also scale would mean touching a complex trig-based routine I can't
  verify visually, so instead reverted the viewport box back to its fixed
  native size (156x152, matching the sprite) in `drawUiTabMinimap()` —
  the box's *position* (offset from the screen edge) still scales, only
  its size no longer does. Same trade-off already made for the top
  menu-bar icons: position scales, native-resolution art doesn't.
  Compiled clean.
- [x] **Settings (wrench) menu — and by extension every top-bar tab —
  closed almost as soon as you hovered it; fixed, then partially reverted
  once its cause turned out to be the opposite of what it looked like.**
  First pass: `handleTabUIClick()`/`handleTabUIClick_CUSTOM()` (open a
  top-bar tab on click, auto-close it once the mouse leaves) had every
  click hit-box and "did the mouse leave" bound as a bare 512-baseline
  literal, so I `ui()`-wrapped all of them, matching every other fix this
  session. That fixed the premature-close symptom, but you then reported
  the icons themselves hadn't grown, so hovering *empty scaled space*
  (not the actual icon) now opened tabs. Investigated why: the top-bar
  icon strip (`GUIPARTS.MENUBAR`, one composite bitmap drawn via
  `Surface.a()`) is a fixed-position, fixed-size alpha-blended blit that —
  unlike the minimap sprite — genuinely never moves or resizes in this
  codebase, in *either* UI mode. My `ui()`-wrap had scaled the click
  regions to grow with the window while the art underneath stayed pinned
  at its original raw position and size, so the two drifted apart instead
  of the click zone finally matching the art. **Reverted** the click
  regions for the closed icon strip specifically back to their original
  unscaled literals in both functions (and in `mouseInTabArea_CUSTOM()`'s
  closed-state check), temporarily matching the fixed art exactly while
  a real fix for the art itself was worked out (below) — then re-applied
  the `ui()` scaling once that fix landed, so the final state has both
  the click regions *and* the art scaling together from the same anchor.
- [x] **Top menu-bar tab-icon sprite: attempted to make it grow with the
  window, but reverted after live testing showed corrupted colors.** You
  asked for the full fix rather than leaving it fixed-size or making it
  opaque. Root cause of *why* scaling wasn't already possible: the icon
  strip (`GUIPARTS.MENUBAR`) is drawn via `Surface.a(sprite,x,y,alpha,y)`,
  which does genuine alpha-blending (confirmed: `alpha` feeds a real
  per-pixel blend formula) but has no destination-size parameter, and the
  one existing **scaled** `drawSprite(...,destWidth,destHeight,var5)`
  overload (unused anywhere in this file) has no blending at all — traced
  its trailing `var5` and confirmed it's dead code, never read. Wrote two
  new methods (`plot_scale_black_mask_alpha` — a blended twin of the
  existing bilinear-scaled sampling routine, using the exact blend
  formula read out of the existing native-size blend path rather than
  guessed — and `drawSpriteScaledAlpha`, a public entry point mirroring
  the existing scaled `drawSprite()`'s clipping setup) and wired the
  `MENUBAR` draw call to them, with the click regions re-scaled to match.
  Compiled clean, but **you tested it live and the icon strip rendered
  with badly corrupted/garbled colors** — screenshots showed
  blocky magenta/pink/red noise instead of clean icons. Since this was
  new rendering code with no prior working call sites to model exact
  correctness from (the base scaled `drawSprite()` it's modeled on had
  *never* been used anywhere either, so its own correctness was unproven
  too), and pixel-level fixed-point sampling bugs aren't something safely
  diagnosable by re-reading code without a fast visual test loop, **fully
  reverted**: removed both new methods, restored the `MENUBAR` draw call
  to the original native-size `Surface.a()`, and reverted the click
  regions/highlight box back to their fixed, unscaled values (same
  end-state as the temporary revert described above). Compiled clean.
- [x] **Icon strip scaling — solved properly (2026-07-06), third attempt.**
  You reported still having to "hover over empty space" to hit the tabs —
  expected, since the strip stayed fixed-size after the revert above.
  Realized the previous attempt's mistake: I wrote a **brand new** scaled
  + alpha-blend pixel routine from scratch instead of reusing
  `drawSpriteClipping()`, which already does scaling *and* opacity
  blending together via its `colourTransform` parameter and has been used
  successfully all session (bank/equipment slot dimming uses the exact
  same `0x80FFFFFF`/`0xC0FFFFFF` convention). Switched the `MENUBAR` draw
  call to `drawSpriteClipping(..., ui(200), ui(35), 0, 0, 0, false, 0, 0,
  0x80FFFFFF)` and re-scaled the click regions/highlight box in
  `handleTabUIClick()`, `handleTabUIClick_CUSTOM()`, and
  `mouseInTabArea_CUSTOM()` to match. Compiled clean. **Not yet
  live-tested** — please check the strip now grows with the window, the
  translucency still looks right, and clicks land correctly.
- [x] **The old "RUNESCAPE" logo scaling issue is moot on the main path.**
  Re-checked after the icon-strip fix: the small `GUIPARTS.MAINLOGO` sprite
  that didn't scale was only ever drawn inside `renderLoginScreenViewports()`
  — the rotating-3D-background baking routine that `login.png` (section 7b)
  already replaced as the primary login background. It only still runs as
  the fallback path (load failure / Android). Not worth spending more
  effort scaling a rarely-hit fallback's cosmetic logo; flagging as
  low-priority rather than doing it, unless the fallback path actually
  gets hit for you in practice.
- [x] **Login screen button/border re-theming (2026-07-06).** You asked to
  improve the login buttons/text/UI now that the RuneWake background is
  in. Found that button backgrounds, borders, and the decorated-box style
  used for text-entry fields are all **procedurally drawn colors**
  (`Panel.java`'s `colorA`-`colorL` fields — a gradient + bevel effect,
  not sprite images), so this was safe to restyle without touching any
  art assets. Added `Panel.setButtonColorScheme(highlight, highlightMid,
  shadowMid, shadow)` and applied a warm gold/bronze bevel (matching the
  "RUNEWAKE" logo's lettering color) to just the login-flow panels
  (`panelLoginWelcome`, `panelLogin`, `menuNewUser`, registration/recovery
  screens) — deliberately scoped to login only, not the shared `Panel`
  default, so in-game UI (bank, settings, chat) keeps its original
  blue-gray look unless you want that changed too. Compiled clean, not
  live-tested.
  - **Font**: did not attempt a typeface change. Text renders through a
    fixed bitmap font system (`Fonts.java`, sizes 0-7) baked from the
    original game's cache assets — swapping the actual typeface would
    need a new bitmap font asset built in that same format, which is a
    separate, larger asset-pipeline task, not a quick styling change.
    Flagging rather than guessing; let me know if you want to scope that.
- [ ] **Wall texture blockiness — still present, same root cause as
  before.** You flagged this again from a screenshot. This isn't a
  regression — it's the same limitation noted earlier in section 6: the
  source wall textures are low native resolution (designed for the
  original 512x334 canvas) and now get stretched across a much larger
  window. The existing 2x box-blur pass softens per-texel noise but can't
  manufacture detail that isn't in the source art. Didn't change anything
  here this round since the real fix is one of the two already-flagged
  options: new higher-resolution texture art, or a genuinely risky rewrite
  of `Scene.java`'s rasterizer to support smoother (e.g. bilinear-filtered)
  texture sampling instead of the current 256-color nearest-neighbor
  lookup — given how the last "let's just rewrite the rendering path"
  attempt went (the icon strip above), I don't want to start that blind
  without you explicitly weighing in on the risk first.
- [x] **Rendering-scalar +/- button hitbox fixed (2026-07-07).** Two real
  bugs found on inspection, both in the "rendering scalar" settings row
  (`drawGeneralSettingsOptions()`/`handleGeneralSettingsClicks()`,
  mudclient.java):
  1. The row's Y position was computed with a hardcoded `* 15` per-row
     step. That happened to match the real scrolling-list row height
     (`fontHeight(1) + spaceHeight` = 14+1 = 15) *only* at 1x scale — but
     `fontHeight()` itself scales with `uiScale` (section 3's font-scaling
     work), while this literal never did, so the row drifted out of sync
     with its own list at any other scale. Fixed by computing the same
     real, already-scaled row height (`getSurface().fontHeight(1) + ui(1)`)
     instead of guessing with a constant.
  2. Every X-offset in this block (`gameWidth - 143`, `-93`, `-116/121`,
     the `125`/`143`/`72`/`92` hitbox bounds) was a raw unscaled literal,
     unlike every other control in the same function which offsets from a
     raw dimension via `ui(offset)` (e.g. `width2 - ui(199)` right above
     it) — so the buttons stayed pinned near the screen edge instead of
     tracking the panel as it resizes with scale. Wrapped every offset in
     `ui()` to match the function's own established convention.
  3. Separately, found the click-handler's vertical hit-test band
     (`yPos+3` to `yPos+14`) didn't match the draw-time hover-highlight
     band (`yPos-7` to `yPos+4`) at all — barely overlapping by 1px. That
     meant the button visibly highlighted green in a zone where clicking
     mostly did nothing, functionally clickable a good ten-odd pixels
     below where it looked clickable. Made both bands identical.
  Compiled clean. Not live-tested — please check the scale +/- buttons
  line up with their row and respond to clicks precisely at a few
  different window sizes/scales.

## 7b. RuneWake branding assets (2026-07-06)

You dropped `login.png` (login screen splash art) and `runewakelogo.png`
(logo mark) into `/assets/`. Used both, but this needed a small
architecture piece first, not just a config change:

- **Why it wasn't a one-line change**: `Client_Base/src/orsc/` (mudclient,
  GraphicsController, etc.) is compiled directly into *both* the desktop
  client (via `Client_Base/build.xml`, which also pulls in `PC_Client/src`)
  *and* `Android_Client` (confirmed via its `build.gradle`, which adds
  `Client_Base/src` straight into its own source set). That means shared
  code can't use `java.awt`/`javax.imageio` — those don't exist on
  Android — so a plain "just call ImageIO here" fix would compile fine for
  desktop and silently break the Android build.
- **The fix**: added `Sprite loadScaledImageSprite(resourceName, w, h)` to
  the existing `ClientPort` interface (the seam this codebase already uses
  for every other platform difference — battery/connectivity icons,
  keyboard, window icon, etc.), following the exact pattern of the
  existing `getSpriteFromByteArray()` (used for server-sent captcha
  images) so the pixel-format assumptions are proven, not new. Implemented
  it for desktop in `PC_Client/src/orsc/ORSCApplet.java` using
  `ImageIO`/`Graphics2D` (scales via Java's own proven image scaling, not
  custom pixel math — deliberately avoiding the class of bug from the
  reverted icon-strip attempt above). Nudges any exact-black pixel
  (`0x000000`) up by 1, since this renderer treats pixel value `0` as
  transparent and a dark night-sky background would otherwise punch
  see-through holes. Android's implementation returns `null` (not
  attempted — different asset-packaging model, and I have no way to test
  on that platform), with the caller falling back gracefully.
- **`login.png`** → copied into `Client_Base/src/res/` (same
  bundling mechanism as the existing window icons) and wired into
  `drawLogin()`: loads and scales to the current window size once, caches
  it, and draws it in place of the old rotating-3D-scene login
  background. Falls back to that original animation automatically if the
  load ever fails or returns null (e.g. on Android).
- **`runewakelogo.png`** → cropped to a centered square and resized to
  128x128, replacing `Client_Base/src/res/icon.png` (the app's
  window/taskbar icon, loaded via standard Java `Window.setIconImage`,
  unrelated to the legacy sprite cache). The logo's solid orange
  background carries through as a square backdrop behind the mark at
  small sizes — flagging since I can't preview how that actually reads in
  a real taskbar.
- Compiled clean (both `Client_Base` and the bundled `PC_Client` sources).
  **Not live-tested** — please check: the login screen shows the new
  static art at the right size/position, and the taskbar/window icon
  looks acceptable at actual OS icon sizes.

## 7c. Text still pixelated at scale, and audio investigation (2026-07-06)

- [x] **Scaled text now bilinearly anti-aliased instead of blocky.** Root
  cause: `Fonts.fontAntiAliased[]` is `false` for every single font, so all
  text — including login screen headers — rendered through the plain
  on/off glyph path, then got nearest-neighbor block-scaled by
  `plotLetterScaled()` whenever `fontScale` is above 1x (which, given the
  enforced 1280x732 minimum window, is essentially always). Nearest-
  neighbor scaling of hard-edged glyphs is what "pixelated" text actually
  looks like magnified. Fix: `plotLetterScaled()` now bilinearly
  interpolates the glyph's coverage across the 4 nearest source texels
  (using the exact same fixed-point approach already proven for scaled
  sprites) instead of sampling one nearest pixel, and always feeds that
  into the file's *existing* alpha-blend formula (previously only reached
  by antialiased-flagged fonts) rather than the hard on/off draw. A
  non-antialiased source byte is first normalized to 0/255 coverage, so
  the smoothing comes from the interpolation itself — no new font asset
  needed. **Caveat worth being direct about**: since every font's
  `antiAliased` flag is false, that existing blend formula had *never
  actually executed in production* before this change (dead code, not
  just an unused method like the icon-strip case — the same underlying
  formula, just gated off). I didn't invent new pixel math this time
  (reused the existing formula verbatim, only changed what feeds into
  it), which is a meaningfully smaller risk than the icon-strip mistake,
  but please still check text closely for any color fringing at glyph
  edges before treating this as fully done.
- [x] **Audio investigated.** Sound effects are actually implemented and
  working (`soundPlayer.playSoundFile()`, standard `javax.sound.sampled
  .Clip` playback, 38 legitimate `.wav` files present in
  `Cache/audio/`) — they were just **off by default**
  (`mudclient.optionSoundDisabled = true`). Flipped that default to
  `false` per your direction. Checked the server side too: a brand-new
  player's settings (`PlayerSettings.gameSettings`, a plain
  `boolean[3]`) already default to `false` (sound *enabled*) for anyone
  who's never explicitly saved a preference, so new accounts should now
  get working sound end-to-end without any server change needed.
  - Also found and ruled out a red herring: `ClientPort.playSound(byte[],
    ...)` throws `UnsupportedOperationException` in both `ORSCApplet` and
    `OpenRSC` — looked alarming on first read, but nothing actually calls
    it (it's a leftover/incomplete alternate path; the real sound system
    goes through `soundPlayer` instead), so it's dead code, not a bug.
  - **Not investigated further**: there's no background music/jukebox
    system in this codebase at all, only short SFX. The 38 existing
    sound effects are old/low-bitrate (era-appropriate for original RSC)
    — genuinely improving their quality would mean new audio recordings,
    the same kind of asset-pipeline wall as the wall-texture and font
    issues. Didn't attempt since no replacement audio was provided:
    tell me if you want that scoped once you have source material, or if
    something *specific* about the existing sounds (volume, missing
    effects, etc.) is the actual complaint.
  - Compiled clean. Not live-tested — please confirm you now hear sound
    effects by default on a fresh client/account.

## 7d. Audio quality pass, texture rasterizer (declined), rename, and font generator (2026-07-06/07)

- [x] **Audio normalize/resample pass applied.** Inspected all 38
  `Cache/audio/*.wav` files directly (Python `wave` module): 8000Hz mono
  16-bit (one file, `mix.wav`, was 8-bit). 8000Hz is telephone-call
  bandwidth (~4kHz max per Nyquist) — that ceiling is the real, unfixable-
  without-new-recordings cause of "tinny." What *is* fixable: ran a batch
  pass (peak-normalized to ~29000/32767 with a 4x max boost cap so near-
  silent clips don't just amplify noise, resampled 8000→22050Hz, and
  correctly widened `mix.wav`'s 8-bit unsigned samples to 16-bit signed).
  All 38 files are git-tracked, so this was safe to try — first attempt
  crashed on the 8-bit file, reset via `git checkout` and reran clean.
  This won't make the SFX sound "modern," just cleaner/more consistent
  volume — genuinely fixing the tinniness needs new source recordings.
- [~] **Texture rasterizer scaling: investigated, then explicitly declined
  despite earlier go-ahead.** You'd said "go ahead and attempt it" for
  smoothing the blocky wall textures, but once I actually traced
  `Scene.java`'s texture system it turned out to be a much bigger blast
  radius than what I'd described when asking: textures are palette-indexed
  (`byte[]` index + `int[]` palette), fixed at exactly 64×64 or 128×128,
  expanded at load into a buffer that *also* bakes in 3 pre-darkened
  lighting variants, read by 6+ separate rasterizer dispatch paths that
  almost certainly hardcode power-of-2 width addressing. That's the single
  most complex/fragile part of the codebase — a mistake there risks
  corrupting the entire 3D world, not one screen. Walked this back rather
  than attempting it on the strength of a stale risk estimate. Left
  untouched; would need a much more surgical, narrower plan (and probably
  new art) to revisit safely.
- [x] **RuneScape → RuneWake text rename pass.** Live/user-facing branding
  and in-universe flavor text renamed: window title, default server name
  (client `Config.java`, server `ServerConfiguration.java` fallback +
  `server/default.conf`), NPC/item dialogue and descriptions referencing
  "runescape" as the in-world name (`EntityHandler.java`,
  `BankPinInterface.java`, `Functions.java` bank NPC dialogue), and the
  server-browser README's sample JSON. **Deliberately left alone**: other
  servers' own distinct configs (`cabbage`/`coleslaw`/`uranium`/`openpk`/
  `2001scape`/`preservation` `.conf` files — different products, not
  RuneWake), the internal protocol constant `OpcodeOut.RUNESCAPE_UPDATED`
  (historical RSC opcode name, not user-facing, 6-file touch for zero
  value), citation URLs in code comments (tip.it/runescape,
  ngrunescape.com — research references), and the abuse-report reason
  string "buy/sell a RuneScape account" in `Constants.java` (sits beside
  "Impersonating Jagex Staff" — both are preserved historical report
  categories, not live branding). Compiled clean.
- [~] **Font modernization: generated-font pipeline built, compiles clean,
  not yet live-tested — this is the riskiest change this round.** Reverse-
  engineered the game's proprietary bitmap-font format first (9-byte
  per-glyph index: 3-byte 7-bit-packed pixel offset + width + height +
  x/y offset + advance, all unmasked so every byte must stay 0-127, plus
  a per-pixel antialiased coverage blob) by reading every consumer in
  `GraphicsController.java` before writing a generator, specifically to
  avoid repeating the icon-strip mistake of insufficient verification on
  high-blast-radius rendering code. Added `ClientPort.regenerateFonts()`
  with a real desktop implementation in `ORSCApplet.java` (renders each
  glyph via Java2D/`SansSerif` at the original 8 fonts' target pixel
  heights `{12,14,14,15,15,19,24,29}`, tight-crops the antialiased alpha
  bounding box, packs it into the exact byte format above) and a graceful
  `return false` stub on Android (`GameActivity.java`, no `java.awt.Font`
  there — falls back to the original bitmap fonts). Gated behind
  `Config.S_WANT_MODERN_FONT = true` — **flip that one line to `false` for
  an instant, total revert** if anything looks wrong. Also added
  `Fonts.setFont()` since the generator lives in a different package than
  `Fonts`'s package-private fields.
  - **Verification done so far (no game engine involved)**: wrote a
    standalone harness that runs the *exact same* encoding logic, then
    decodes the result back using the *exact same* math
    `GraphicsController.plotCharacter()` uses, and rendered it to a PNG —
    confirmed glyph shapes, spacing/advance widths, and antialiasing all
    round-trip correctly for both a plain 14px and bold 24px sample
    string. This checks the byte-format math is self-consistent; it does
    **not** confirm the in-game blend path (`plotLetterScaled()`,
    drop-shadow behavior, login-screen rendering) looks right — please
    test live.
  - **Known tradeoff**: switching a font to `antiAliased=true` (needed
    for smooth glyphs) disables that font's manual drop-shadow effect
    (`drawColoredString()` only draws the shadow for non-antialiased
    fonts) — accepted in favor of smoother text, but watch for text that
    now looks a bit flatter/harder to read over busy backgrounds.

## 7e. Remaining RuneScape mentions in intro/login flow (2026-07-07)

You flagged the intro still said "RuneScape Classic" in places — the earlier
rename pass had covered branding/NPC text but missed several login/welcome-
window strings actually seen right at intro. Found and renamed 5 more, all in
`mudclient.java`:

- [x] Under-13 login rejection: "Under 13 accounts cannot access RuneScape
  Classic" → "...RuneWake".
- [x] Veteran-account login rejection: "That is not a veteran RS-Classic
  account." → "...RuneWake account."
- [x] Contact-details panel: "...to locate future RuneScape servers." →
  "...RuneWake servers."
- [x] Welcome-window tip of the day (#2): "Don't use RuneScape cheats,
  helpers, or automaters." → "Don't use RuneWake cheats..."
- [x] Welcome-window tip of the day (#5): "If possible only play runescape
  from your own computer" → "...play RuneWake from your own computer".

**Deliberately left alone** (same reasoning as the first rename pass):
the `::wiki` chat command's URLs (`classic.runescape.wiki`) — that's a real
external community wiki, not a RuneWake-branded page, so pointing it
anywhere else would actually break the feature; and three fully commented-
out (`//`) debug draw calls referencing "Open RSC" that never execute.
Also re-checked `server/src` — no new live strings found, just the same
already-reviewed protocol constant/citations/report-reason text from the
first pass. Compiled clean.

## 7f. Panel "overlay" opacity fixed from video test (2026-07-07)

You sent a 17s video (`E:\Runescape Classic\testing\bideo.mp4`) showing the
settings, spellbook, inventory, and friends panels all looking like the 3D
world was glitching/bleeding through them. Pulled frames out with VLC's
scene filter to look closely (no ffmpeg on this machine, VLC's built-in
frame export worked fine) rather than guess from a description.

- **Root cause, confirmed by reading the actual blend math
  (`GraphicsController.drawBoxAlpha`)**: every one of these panel/tab
  backgrounds has *always* been drawn at `alpha = 128` (out of 256, i.e.
  exactly 50% blend) — this is original, intentional "classic RSC custom
  UI" styling, not a regression. The reason it now reads as a glitch: the
  tint colors used (light gray/cream, ~181-220 brightness) are similar in
  brightness to the sunlit tan/yellow terrain and pale rooftops in this
  scene, so a 50/50 blend between two similarly-bright colors barely
  changes what you see — the panel ends up looking almost like an
  unfiltered view of the 3D world with text floating on top, rather than a
  readable panel. This was always mathematically true, but only became
  visually obvious/distracting once widescreen support made these panels
  physically much larger on screen, showing far more world detail behind
  them than the original small client ever did.
  - Also specifically checked the minimap for a similar "static noise"
    look in one frame — turned out to be real minimap dot-markers (players/
    NPCs) tightly clustered, not corruption. Cropped and inspected at full
    resolution to confirm before ruling it out.
- **Fix**: raised alpha from 128 → 210 (out of 256, ~82% opaque) on every
  background box that uses this exact pattern — settings, spellbook,
  inventory slots, friends/ignore/clan, combat style selector, and the
  player-info panel (28 call sites total, all sharing the identical
  `drawBoxAlpha(..., 128)` signature, confirmed via search before editing
  so nothing unrelated got touched). Panels still read as slightly
  translucent (keeping a bit of the classic look) but the tint color now
  dominates over whatever's rendered behind it, so text stays readable
  regardless of what's in the 3D scene behind the panel.
  Compiled clean. Not live-tested — please check the panels read clearly
  now, and let me know if 210 is too opaque/too subtle once you see it.

## 7g. More unscaled-literal bugs + fog darkening ceiling (2026-07-07)

You reported "still have ui scaling issues, texture popping in, and low
resolution images/textures" after the panel-opacity fix. Clarified via
follow-up: the UI issue is panel/box positioning specifically, and the
popping is the same fog-distance effect as before (not sprites/textures
flickering). Used an Explore agent to hunt for more instances of the exact
bug class already found once (raw pixel literals left unwrapped in an
otherwise-`ui()`-scaled function) rather than guess blindly, then verified
and fixed the two real hits it found:

- [x] **Inventory/bag tab icon** (`drawUiTab1()`, mudclient.java) — drawn at
  a bare `3` for its Y position while every sibling tab icon (options,
  minimap, player info) uses `ui(3)` for the exact same coordinate. At any
  scale above 1x the bag icon sits at a different height than the icons
  immediately next to it in the same row. Fixed to `ui(3)`.
- [x] **Duel-offer and trade-offer right-click sub-menus** (`drawDialogDuel()`/
  `drawDialogTrade()`, 6 call sites total) — these popup menus (right-click
  an item while staking/trading) copied the main context menu's positioning
  logic but never got updated when that logic was scaled: `mouseY - 7`
  instead of `mouseY - ui(7)`, and edge-clamping against literal `510`/`315`
  instead of `getGameWidth() - ui(2)` / `getGameHeight() - ui(19)` (confirmed
  these literals are exactly the unscaled design-resolution equivalents of
  those same two calls, just never converted). At scale, this popup would
  spawn offset from the cursor and clamp against the wrong edge — a
  concrete "box positioned wrong" bug matching your report. Fixed to mirror
  the main context menu's already-correct logic exactly.
- [x] **Fog-fade darkening ceiling raised** — re-examined the fade-to-fog
  math in `Scene.java` behind the section-5 fade-zone fix. Found the
  smoothing zone's rate constant capped the *total* darkness a face could
  gain by the time it reached the cutoff at a fixed +100 (out of a 0-255
  shade clamp) regardless of window size — so a brightly-lit face could
  still be reaching the cutoff only partially darkened, still visibly lit
  right before it's culled, reading as a hard pop no matter how gradual the
  fade *rate* is. Raised that ceiling (divisor changed from `/100` to
  `/300` in the `fogZFalloff` calculation, all 3 call sites) so faces get
  pushed much closer to fully black before they're culled. This is a
  distinct fix from the earlier fade-zone-width change: that one controlled
  how gradually the fade happens, this one controls how dark it gets.
  Compiled clean. Not live-tested — please check if the pop-in at your draw
  distance is meaningfully softer now, since I'm tuning this blind without
  being able to see the render.
- **Low-resolution textures**: unchanged since section 6 — still the same
  known, larger-scope limitation (native art is low-res, real fix needs
  new textures or the same risky rasterizer rewrite already discussed and
  declined). Nothing new to try here without new direction from you.

## 7h. Working through the "easy wins" list (2026-07-07)

You asked to work through the 4 candidates I'd suggested. Results:

- [x] **Another unscaled-literal sweep** — ran the same Explore-agent search
  approach as before across bank interface, report-abuse, recovery-
  questions/change-password/contact-details, and character creation. Came
  back clean this time — no more instances of the scale-drift bug class
  found, suggesting the 3 already fixed were the bulk of it. It did
  surface a different, real bug though:
  - [x] **`drawPopupSocial()` (Add Friend / Message / Add Ignore popups)** —
    the "click outside the box closes it" check computed its own `y` at
    the top of the function (`ui(145)`, or `ui(75)` on Android), but the
    box actually gets *drawn* at a completely different, separately-
    computed `y` further down (`(getGameHeight() - ui(70)) / 2`, vertically
    centered) — so the outside-click check was testing against a box
    position that had nothing to do with where the box actually was, and
    the bottom bound was comparing against a bare `ui(70)` (absolute
    distance from the top of the screen) instead of `y + ui(70)` (distance
    from the box's own top edge). Consolidated to one `y` computation,
    reused consistently for both the click-check and the draw, and fixed
    the bottom bound. This one wasn't scale-dependent - it was likely
    subtly wrong at every window size, just not obvious since the
    consequence (clicking near this small popup does the wrong thing) is
    easy to miss.
- [x] **Character creation panel** — checked thoroughly (Explore agent +
  manual read of `createAppearancePanel()`). Already fully `ui()`-scaled,
  no drift bugs, and it uses `Panel.java`'s decorated-box components
  rather than the raw `drawBoxAlpha` pattern that needed the opacity fix
  in 7f. Nothing to fix - the earlier "worth doing before launch" note
  looks to already be satisfied by scaling work done earlier in the
  session.
- [ ] **UI icon blur — investigated, declined.** The world-texture blur
  (section 6) works because it softens 256-color banding on large, tiled,
  viewed-from-a-distance surfaces. Inventory/spell/equipment icons are a
  different situation: they're small (as little as ~20x33px on screen),
  viewed up close, and their whole job is to stay instantly recognizable
  at a glance - blurring them would make similar-looking items *harder*
  to tell apart, not smoother-looking. There's also no single dedicated
  "load icon sprites" function to hook the blur into (icons load through
  a generic multi-purpose sprite loader used for many unrelated sprite
  categories), so it'd be a much less contained change than the texture
  case for a change that's likely to look worse, not better. Skipped
  rather than doing it anyway - flag if you specifically want icons
  softened despite the legibility tradeoff.
- [x] **Antialiased fonts now keep their drop-shadow.** Root cause (noted
  as a tradeoff back in section 7c): `drawColoredString()`'s manual
  drop-shadow only fired when `!Fonts.fontAntiAliased[font]` - so turning
  on antialiasing for smoother glyphs silently killed the shadow entirely.
  `plotCharacter()` already handles `color=0` (the shadow's color)
  correctly for both antialiased and non-antialiased fonts - the shadow
  draw calls just weren't being reached. Removed the antialiased
  exclusion from both shadow gates (`GraphicsController.java`); the
  shadow now draws through the exact same already-correct blend path,
  just with antialiasing on. Text over busy/bright backgrounds (e.g. the
  wall-texture pop-in scenes) should read more clearly now.
  Compiled clean. Not live-tested - please check text still looks right
  (no odd fringing where the shadow and glyph overlap) since this
  re-enables a code path that's been dark since the antialiasing fix.

## 7i. Dead-flag audit + first server-side pass (2026-07-07)

You asked about packaging as an installed app (`RuneWake.exe` + auto-updater)
and to keep working the "easy wins" list. Packaging findings (no code changed
yet, informational): there's an existing `PC_Launcher` project
(`launcher.Main` → `OpenRSC.jar`) with a working MD5-manifest-diff
auto-updater (`ClientUpdater`/`Downloader`/`Md5Handler`) that already
downloads and syncs the whole asset cache, not just the client jar - it's
just pointed at the original OpenRSC project's file host
(`rsc.vet/downloads/`) and carries update logic for other servers' "extras"
RuneWake doesn't need. No `.exe`/installer tooling exists anywhere in the
repo yet (no launch4j/jpackage/Inno Setup/NSIS). Realistic path: launch4j +
Inno Setup (or jpackage) to wrap the existing jar, pointed at the
already-bundled portable JDK so users don't need Java installed - mechanical,
no source changes needed. The updater needs re-pointing at a RuneWake-owned
file host once one exists. Not started - awaiting direction on hosting/
packaging tool choice.

Then continued the "easy wins" list:

- [x] **Dead-feature-flag audit (client)** — used the same investigative
  approach that found the font-shadow bug to look for more "boolean gate is
  always one value" bugs. Found one concrete hit:
  - [x] **`isInFirstPersonView()` (mudclient.java) had a copy-paste bug**:
    `direction != COMBAT_B && direction != COMBAT_B` (duplicated) instead of
    `!= COMBAT_A && != COMBAT_B`. Meant the method never actually excluded
    `COMBAT_A`-facing players from "in first person" - `toggleFirstPersonView()`
    would set the wrong camera pitch (0 instead of the default) whenever the
    player happened to be facing that specific direction when toggling.
    Fixed the duplicated comparison.
  - Two other candidates surfaced but weren't worth changing: `Panel.m_t`
    (a boolean always `true` — harmless no-op, not disabling anything) and
    `Config.CUSTOM_CACHE_DIR_ENABLED` (a `final false` — reads as an
    intentional dev-only override switch, not a player-facing regression).
- [x] **First server-side pass** — hadn't looked at `server/src` at all this
  session; sampled the highest-traffic areas (database layer, login/config,
  inventory containers) for the same bug classes that paid off client-side.
  Found and fixed 3 real issues:
  - [x] **`JDBCDatabase.executeQuery()` handed callers an already-closed
    `ResultSet`** — it built the `ResultSet` via a helper that wraps the
    `PreparedStatement` in try-with-resources, so the statement (and per the
    JDBC spec, its `ResultSet`) was closed before the caller's consumer
    lambda ever touched it. Concretely broke `queryLoadPlayerBankPresets()` —
    any server running with `WANT_BANK_PRESETS` enabled would fail to load
    bank presets every time. Fixed by keeping both the statement and result
    set open for the duration of the consumer call (single try-with-resources
    wrapping both), matching the pattern already used correctly elsewhere in
    the MySQL implementation.
  - [x] **`ServerConfiguration.readGlobalRules()` leaked a `BufferedReader`**
    every time it ran (no close on any path). Only runs once at boot when
    `want_global_rules_agreement` is on, so low-impact, but a real leak.
    Switched to try-with-resources.
  - [x] **`ItemContainer.insert()` dereferenced `list.get(slot)` before
    validating the index**, unlike the equivalent `Bank.insert()` which
    checks bounds first — a negative `slot`/`to` (e.g. from a malformed
    client packet) would throw `IndexOutOfBoundsException` before the
    existing bounds check ever ran. Moved the bounds check (now also
    rejecting negative indices, which the old check didn't) before touching
    the list.
  Compiled clean via direct `javac` (server jar was locked by a running
  server process, so verified by compiling straight to a temp output dir
  rather than interrupting it - same pattern used earlier this session).
  Not live-tested against a running server - please restart your test
  server when convenient so these take effect, especially if you use bank
  presets.

## 7j. Windows installer built end-to-end (2026-07-07)

Built the `.exe`/installer packaging discussed in 7i - working, tested,
producing a real `RuneWake-Setup.exe`. New `Packaging/` folder:

- [x] **`RuneWakeLauncher/Program.cs`** — a thin native launcher stub,
  compiled with `csc.exe` (built into every Windows install via .NET
  Framework - no launch4j/jpackage download needed, since neither was found
  on this machine). It just runs `jre\bin\javaw.exe -jar
  RuneWakeLauncher.jar` from its own install directory. Embeds a
  `runewake.ico` (generated from the existing `Client_Base/src/res/icon.png`
  via a small one-off Java ICO encoder, since no `.ico` existed yet).
- [x] **`installer.iss`** (Inno Setup, already installed on this machine) —
  installs per-user under `%LocalAppData%\RuneWake` (no admin rights
  needed), bundles a copy of the JRE from `Portable_Windows/`, Start
  Menu + optional Desktop shortcuts, real uninstaller.
- [x] **`build-installer.bat`** — one command builds the launcher jar (ant),
  compiles `RuneWake.exe`, stages the JRE, and runs Inno Setup. Ran it
  end-to-end successfully: produces a working `Output/RuneWake-Setup.exe`
  (~31MB, mostly the bundled JRE).
- [x] **Verified the exe actually works**, not just "compiles" — staged a
  test install folder and ran `RuneWake.exe` directly; confirmed it spawns
  `javaw` with the launcher jar and the launcher initializes correctly.
- **Important, disclosing directly**: that verification run inadvertently
  downloaded real files (`Open_RSC_Client.jar`, `MD5.SUM`, asset folders)
  from `rsc.vet` — the *original OpenRSC project's* live server — because
  `--no-update` turned out to only skip the launcher's own self-update
  *prompt*, not the actual client/cache download
  (`ClientUpdater.updateOpenRSCClient()`), which ran unconditionally. This
  wasn't something malicious or destructive (it's a normal public download
  endpoint, and downloading a client update is exactly what it's there
  for), but it was a real, unplanned hit on a third party's infrastructure
  I want you to know about. Fixed the underlying gap right after finding
  it: `--no-update`/`-n` now actually skips *all* update/download activity,
  matching its own help text, and used it for further testing (no more
  network calls after the fix). Cleaned up all downloaded test files
  afterward.
- **Hard blocker before this can go to real users** (same one flagged in
  7i, now confirmed by hitting it directly): `Defaults.java` still points
  at `rsc.vet` — until it's re-pointed at a RuneWake-owned file host (with
  RuneWake's own MD5 manifest and client files uploaded there), a real
  install would download the wrong game under the RuneWake name. Documented
  clearly in `Packaging/README.md`. Not resolved - needs your decision on
  where RuneWake will host update files.

## 7k. Broader server-side sweep (2026-07-08)

Continued the server-side audit from 7i with wider coverage (same approach,
more files). Found and fixed 6 more real issues, all the same "resource
never closed on some/all paths" shape as the `ServerConfiguration` leak
found earlier, plus one off-by-one:

- [x] **`util/PersistenceManager.java`** — all three methods (`load()`,
  `setupAliases()`, `write()`) opened file streams that were never closed.
  `load()` alone is called 30+ times at every server startup (every
  `defs/*.xml` load via `EntityHandler`), so this was the highest-traffic
  leak found this session. Switched all three to try-with-resources.
- [x] **`net/RSCPacketFilter.java`** — `loadIpBans()`/`loadIpMutes()` both
  leaked a `BufferedReader` on `ipbans.txt`/`ipmutes.txt`. Both are
  admin-reloadable at runtime (`reloadIpBans()`/`reloadIpMutes()`), so
  repeated reloads would compound the leak. Fixed both.
- [x] **`database/impl/mysql/ScriptRunner.java`** — `runScript(String)` and
  the private `runScriptFile()` (used for recursive SQL `SOURCE` includes)
  both leaked a `FileReader`/`BufferedReader` per script file during DB
  creation/migration. Fixed both entry points; left the public
  `runScript(Reader)` overload alone since it doesn't own the reader it's
  handed (a caller could reasonably pass a reader they manage themselves).
- [x] **`net/PcapLogger.java`** — `exportPCAP()` only closed its output
  stream chain on the success path; if writing a packet mid-loop threw, the
  file handle leaked and left a truncated `.pcap.gz` on disk. Switched to
  try-with-resources so it closes (and the exception still gets caught) on
  every path.
- [x] **`model/Shop.java` `restock()` off-by-one** — removing a depleted
  custom shop item mid-iteration (`shopItems.remove(i)`) without adjusting
  the loop index meant the item that shifted into slot `i` got skipped that
  tick - on a shop with multiple custom items running low, they'd only
  restock/decay every other tick instead of every tick. Decremented `i`
  after the removal to counteract the loop's own `i++`.
  Verified all 5 files compile clean (direct `javac`, server jar still
  locked by your running test server - same workaround as before). Same
  caveat as 7i: needs a server restart to take effect, and none of this is
  live-tested since I can't run the server myself.

## 7l. Wall-texture checkerboard corruption bug (2026-08-10)

Distinct from the "still blocky, low native res" limitation noted in section 6
and 7g/7h — this was a real correctness bug in the box-blur pre-pass itself,
found from a fresh screenshot showing pink/black checkerboard artifacting on
wall geometry specifically (not floors).

- [x] **`boxBlurTexture()` was blurring the reserved black transparency
  sentinel together with real texture color.** World textures use pure black
  (`0x000000`) as a punch-through/transparent marker (walls have frequent
  window/gap regions using it; the flat-shaded floor render path doesn't go
  through texture quantization at all, which is why only walls showed the
  bug). The existing code captured which source texels were originally black
  and force-restored *exactly those positions* to the magenta marker after
  blurring — but the blur convolution itself ran over the raw, unconverted
  pixels first, so texels bordering a punch-through region got averaged
  together with pure black before the restore ever happened, leaving a fringe
  of wrongly-darkened/wrongly-quantized pixels ringing every transparent
  region in every wall texture. Root-caused by diffing pixel-processing order
  against the last committed version (`git show HEAD:...`, from before the
  blur was added) rather than guessing. **Fix**: `boxBlurTexture()`
  (`mudclient.java:14740-14783`) now treats `0x000000` source texels as holes
  — passed through unblurred at their own position and excluded from every
  neighbor's averaging sum/count — so the sentinel and its border never mix
  with real color in either direction. Compiled clean. **Not yet live-tested**
  — the running client/server session was deliberately left untouched per
  standing instruction to keep the current play session alive; pick up the
  fix via `run-client.bat` on next relaunch.

## 7m. Server browser `/status` endpoint: found and fixed a real crash (2026-08-10)

Deployed the server browser to GitHub Pages this session
(`https://theantipopau.github.io/runewake/`), then started a local test
server to verify the `/status` endpoint end-to-end for the first time (it
had only been compile-checked before, never actually run — see section A's
original "not yet tested against a live running server" note).

- [x] **`RSCMultiPortDecoder.addWebHandlerStack()` crashed every websocket-port
  connection when no SSL certificate is configured** — which is the
  documented default/simple-hosting case (`Server.java:490-492` logs a WARN
  and intentionally proceeds without SSL when `SSL_SERVER_CERT_PATH`/
  `SSL_SERVER_KEY_PATH` are empty). The handler unconditionally built a
  `new OptionalSslHandler(this.server.getSSLContext())` — but
  `OptionalSslHandler` requires a non-null `SslContext` to do its job
  (auto-detecting TLS vs. plaintext), so a null context threw
  `NullPointerException: sslContext` on the very first byte of *every*
  connection to the WS port, including `/status`. Confirmed by actually
  running the server and curling the endpoint (`curl` got "Empty reply from
  server"; the log showed the NPE). This silently broke the server browser
  entirely for anyone following `SIMPLE_HOSTING.md`'s plain no-cert path —
  exactly the path the docs describe as the easy default.
  **Fix**: only add the `OptionalSslHandler` when a real SSL context exists;
  without one, every connection on that port is necessarily plaintext
  ws/http anyway, so skip straight to the plain HTTP/WS stack. Verified
  fixed by rebuilding and re-testing: `/status` now returns
  `{"serverName":"RuneWake","players":0,"uptimeMillis":...}` with no
  exception logged. This was blocking both the WS game connection path and
  the HTTP status endpoint whenever no cert is configured — likely the
  actual reason websocket/webclient connections never worked for anyone
  running the simple no-SSL setup, not just a server-browser-specific bug.

## 8. Suggested next session

Pick based on what actually bothered you most after testing this build:
1. Confirm the login screen now looks right (no more overlapping buttons).
2. If font size and wall/entity fade now look right → extend the same
   distance-fade approach to name/hit-splat/chat-bubble text (section 4).
3. If font size is still off → send specifics (too big/small/not scaling
   at what window size), that's a quick targeted tune.
4. If the fade zone is now too gradual/too abrupt → the `fogDistance / 5`
   (20%) and `smoothingZone / 100` constants in section 5 are easy single-
   number tunes once you can see how it reads.
5. Character creation panel is worth doing before launch (every new account
   hits it); recovery/contact are fine to leave for later.

See **Phase 2** below for the dedicated-server / server-browser / AI-players
epics — those are tracked separately since they're a different kind of work
(new systems, not client bugfixes) and need your direction on a few open
questions before implementation starts in earnest.

---

# Phase 2: RuneWake Server Ecosystem

Three requests: a simple dedicated server, a web-based server browser, and
AI players that make the world feel lived-in — all using free APIs/systems
only. Researched the existing server (`server/`, Java + Netty + Guice,
SQLite by default, already supports multiple independent world configs) and
protocol (`Client_Base/src/orsc/{PacketHandler,net/Opcodes}.java`) before
writing this, so the plans below are grounded in what's actually there, not
guessed. Ordered easiest → hardest, which is also implementation order.

## A. Web server browser — implemented (starter version)

- [x] **Server-side status endpoint** (`server/src/com/openrsc/server/net/HttpRequestHandler.java`)
  — this class already existed as a stub (any HTTP request just got an empty
  `200 OK`); it's wired into the Netty pipeline specifically for the
  websocket upgrade path. Added a `/status` route returning JSON
  (`{serverName, players, uptimeMillis}`), reusing only already-computed,
  already-public server state (`server.getWorld().getPlayers().size()`,
  `server.getServerStartedTime()`, `server.getConfig().SERVER_NAME`) — the
  same numbers the in-game `::online`/`::uptime` commands already report,
  so this can't drift out of sync with them. Sends
  `Access-Control-Allow-Origin: *` so a webpage hosted anywhere can fetch it.
  `RSCMultiPortDecoder.java` updated to pass the `Server` instance through
  (previously `HttpRequestHandler` had no way to reach game state at all).
  **Verified**: compiled the two changed files directly against the
  server's existing jars/classpath (couldn't run the full `ant compile_core`
  target — your server/client were still running and had `core.jar` locked,
  didn't want to kill a session you might still be using) — clean compile,
  no errors. **Not yet tested against a live running server** — please
  rebuild the server and hit `http://localhost:<ws_server_port>/status`
  (default config: `43494`) once you're back to confirm the JSON comes back.
- [x] **Static server-browser webpage** (`web/server-browser/`) — a
  completely standalone `index.html` + `servers.json`, zero backend, fetches
  each listed server's `/status` directly from the visitor's browser. Free
  to host anywhere static (GitHub Pages costs nothing). Handles unreachable
  servers gracefully (shows "offline", doesn't break the rest of the list).
  `servers.json` is meant to be community-editable (server operators add
  their own entry via PR) since there's no central server registry today —
  each world is an independent process/port per the research. Full usage
  doc in `web/server-browser/README.md`.
- [ ] **Important caveat found during research**: the game server actually
  runs the TCP game port and the websocket port through *different* decoder
  modes (`Server.java` ~:501/528 use `DecoderMode.TCP` for the main game
  port, ~:544 uses `DecoderMode.WS` for the websocket port) — only the `WS`
  port's pipeline includes the HTTP handler stack at all. So `/status` is
  **only reachable on `ws_server_port`, never on `server_port`** — this is
  now documented in the browser's README, but worth knowing if you extend
  this further.
- [x] **Ping + one-click-feeling connect added (2026-07-05)**, per direction
  ("simple way to see server count, latency, click to connect, like OSRS"):
  - **Latency**: measured client-side as the round-trip time of the same
    `/status` fetch already being made — no extra request needed. Shown per
    server, color-coded (green under 150ms, red over 400ms). Documented that
    this measures `ws_server_port` specifically, not the exact port/protocol
    the game client's TCP connection uses, so it's a proxy, not an exact
    in-game ping.
  - **Play button**: a static webpage can't launch a native Swing client or
    write into its folder directly (browser sandboxing prevents that,
    regardless of hosting) — so true single-click launch isn't achievable
    without either an installed protocol handler or a companion app, neither
    of which exists yet. Implemented the closest free/static equivalent
    instead: clicking **Play** downloads a small generated
    `connect-<server>.bat` that writes the server's host/port into
    `Client_Base/Cache/ip.txt` / `Cache/port.txt` (verified these are the
    exact files `ClientPort.loadIP()`/`loadPort()` already read — this
    override mechanism already existed, just wasn't exposed anywhere) and
    then runs `run-client.bat`. User drops it next to `run-client.bat` once;
    after that it's genuinely one click per server.
- [ ] Not done: any actual hosting/deployment of the browser page, and no
  real servers in `servers.json` beyond a localhost placeholder — that's
  for you to fill in once there's something running to list.

## B. Simple dedicated server — done (plain JDK, no Docker)

Direction received: plain JDK approach specifically, so it can run on a
bare Windows server — no Docker. Matches the research finding that the
server mostly *already is* simple to self-host (file-based SQLite by
default, multi-conf support already exists) — this really was a
packaging/tooling gap, not a rewrite.

- [x] **`run-server.bat`** (repo root) — mirrors `run-client.bat`'s existing
  pattern (bundled `Portable_Windows` JDK/Ant, no separate install needed).
  Usage: `run-server.bat` (uses `default.conf`) or `run-server.bat
  rsccabbage` / `run-server.bat openpk` / etc. to pick any other `.conf`.
  Compiles (`compile_core` + `compile_plugins`) then runs `ant runserver
  -DconfFile=<name>` in the foreground so server output/errors are visible
  in the window (not hidden behind Linux's `screen`, which the existing
  `ant_launcher.sh` assumes and Windows doesn't have).
- [x] **`server/SIMPLE_HOSTING.md`** — quick-start, port-forwarding
  requirements (`server_port` + `ws_server_port`, and why the latter
  matters for the server browser above), how to customize a `.conf`, and
  honestly documents what this simple approach *doesn't* cover (no
  auto-restart-on-crash, closing the window stops the server) with free
  follow-up options (NSSM for a real Windows service, Task Scheduler) for
  when that matters.
- **Not verified live**: same caveat as the server browser — didn't want
  to kill your running server/client session to test-launch this. The
  script logic mirrors `run-client.bat`, which you've already confirmed
  works, so confidence is reasonably high, but please run it once when
  you're free to confirm.
- [x] **`server/CENTRALIZED_DATABASE.md` (2026-07-06)** — added after you
  asked whether accounts carry between servers (they don't by default: each
  `.conf`'s SQLite file is local and isolated). Documents switching
  `connections.conf` to `db_type: mysql` (already supported, zero code
  changes needed) so multiple server instances share one account database
  — written for your Proxmox setup specifically: MariaDB running on the
  same Windows VM as the dedicated server (plain `localhost` connection, no
  firewall changes) as the default path, plus a "connecting from another
  machine" section (scoped DB user + WireGuard-or-firewall-allowlist
  guidance, not a wide-open `db_user@'%'`) for if a second instance ends up
  on separate hardware later. Flagged but did not attempt: migrating any
  existing SQLite account data into the new schema — need to know first
  whether there are real accounts worth preserving before writing that.
- [x] **Expanded `server/SIMPLE_HOSTING.md` with concrete networking steps
  (2026-07-06)** — you asked for "what ports to open etc." specifically.
  Added a Proxmox-specific networking section covering all three layers a
  connection has to clear: Windows Firewall on the VM (`New-NetFirewallRule`
  PowerShell commands for both `server_port`/`ws_server_port`), the VM's
  Proxmox network mode (bridged vs NAT — recommends bridged as the simple
  default so the router only has to forward to the VM's own LAN IP, with a
  static-IP/DHCP-reservation note so the mapping survives reboots), and
  router port-forwarding for home connections. Also added a "verifying
  it's reachable" section (test from outside your own LAN, e.g. mobile
  data) and noted which layer to check first if it fails.

## C. AI players ("make the world feel lived in") — scoped only, not started

This is the hardest of the three, and I deliberately did not start
implementation blind — building a game-protocol client with no way to
launch a live server and watch it connect in this environment is exactly
the kind of unverifiable, high-blast-radius work worth pausing on for your
input first.

**What exists today**: nothing that connects as a real player. `WANT_PK_BOTS`
(`NpcLocsPkBots.json` + `WorldPopulator`/`NpcBehavior`) spawns server-side
**NPCs**, not actual client connections — different thing entirely (NPCs
are simpler, no login/protocol needed, already fully server-side).

**What "AI players" actually requires**: a headless bot client that speaks
the real client-server protocol — i.e., a program that does the RSA login
handshake (`mudclient.java` ~:14873: username + RSA-encrypted password +
RSA-encrypted login-details string, opcode 0), then sends/receives the same
opcodes a real client does (`Client_Base/src/orsc/net/Opcodes.java`:
`WALK_TO_POINT`, `CHAT_MESSAGE`, `PLAYER_ATTACK`, `NPC_TALK_TO`, etc., plus
incoming `PLAYER_COORDS`/`UPDATE_PLAYERS`/`SEND_MESSAGE`). The existing
client classes (`PacketHandler`, `RSBuffer`, `Opcodes`) are logically
reusable but tightly coupled to the Swing GUI (`mudclient`) — extracting a
clean headless subset is itself real work, not a one-liner.

**Recommended architecture** (free tools throughout):
1. **Movement/combat/decision-making: rule-based, not LLM-driven.** An LLM
   call per game tick is both too slow (network round-trip vs. a ~600ms
   game tick) and unnecessary — "walk to X, attack nearest goblin, eat food
   below 50% HP" is a simple state machine, not a reasoning problem. This
   also means bots keep working with zero ongoing API cost/dependency even
   if an LLM provider's free tier changes terms later.
2. **"Lived-in" flavor: periodic LLM-generated chat only**, on a slow cadence
   (e.g., one message per bot every few minutes, not per-tick) — this is
   the actual point of contact with an LLM, and at that request volume any
   free tier comfortably covers it. Options, all free at reasonable volume:
   - **Groq** (free tier, very low latency, good for short chat lines).
   - **Google Gemini** (free tier via AI Studio).
   - **Ollama**, fully free/unlimited/local, if the server operator has
     spare CPU/GPU — no external dependency at all, at the cost of needing
     local compute.
   Recommend starting with Groq or Gemini's free tier for simplicity, with
   Ollama as a documented self-hosted alternative — don't hardcode a single
   provider, since "free tier" terms shift over time.
3. **Personality**: a small JSON/YAML "persona" per bot (name, a short
   system-prompt-style description, a chat cooldown) fed into whichever LLM
   call generates that bot's next line — cheap to add variety without
   per-bot custom code.

**Phased implementation order** (once you confirm direction):
1. Build a minimal headless login+walk client as a standalone proof of
   concept — no AI yet, just "can a bot log in and walk in a circle,"
   because that alone proves out the RSA handshake and packet I/O, which is
   the actual hard/unverified part.
2. Add basic behaviors (wander, respond to being attacked, pick up nearby
   items) — still fully rule-based.
3. Layer in periodic LLM-generated chat as flavor, behind a feature flag
   and provider abstraction (so swapping Groq ↔ Gemini ↔ Ollama is a config
   change, not a code change).
4. Only after 1-3 work and are visually confirmed by you: consider whether
   any *decisions* (not just chat) should be LLM-influenced (e.g., "this bot
   decides to go fishing" as a higher-level goal picked occasionally by an
   LLM call, still executed by the same rule-based movement/combat from
   step 1) — deliberately last, since it's the least necessary part for
   "feels lived in" and the most likely to feel janky if rushed.

**Direction received (2026-07-05)**: ~20-30 bots, clustered around main
areas (start zones, towns), free tools only. Still not started — this
scale (20-30 real network connections, each needing the RSA login
handshake proven out first) is exactly the kind of thing worth getting the
proof-of-concept (phase 1 below) right before scaling to a full population,
and I don't have a live server to test the handshake against in this
environment. Concretely, the plan is now:
1. Build the minimal headless login+walk proof-of-concept (single bot) —
   this is the actual unknown (does the RSA handshake extraction work at
   all outside the Swing client). Needs a live server to test against, so
   best done in a session where you can confirm "yes it logged in" quickly.
2. Once proven, scale to a small batch (5-10) with simple wander/idle
   behavior around one town, before committing to the full 20-30 across
   multiple areas — cheaper to catch a systemic issue (e.g. server-side
   rate-limiting/anti-bot detection kicking in) at small scale.
3. Layer in periodic free-tier LLM chat (Groq or Gemini to start — will
   build the provider abstraction so this isn't a hardcoded choice) once
   the population itself is stable.
4. Real accounts vs. flagged bot accounts still an open question — leaning
   toward **real accounts** by default (since "make the world feel lived
   in" implies indistinguishable from real players), but flag if you'd
   rather have a visibly-marked bot account type instead; that would
   simplify server-side handling at the cost of the "indistinguishable"
   goal.

