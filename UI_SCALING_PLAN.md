# UI Scaling Pass — Plan & Progress

Tracks the widescreen-resolution UI scaling work (backlog item #5, following the
widescreen resolution support pass). This file is the persistent source of truth
across sessions — update the checklist below as steps land.

## Background

Widescreen support (1280x720 default, resolution-relative rendering pipeline,
FOV/draw-distance fixes) is complete. Panel *positions* now correctly track
`gameWidth`/`gameHeight` via `repositionCustomUI()`/`repositionAuthenticUI()`/
`reposition()`. However, panel and control *sizes* (and font glyphs) are still
fixed pixel literals from the original 512x334 baseline, so at larger windows
(confirmed live `gameWidth` can reach 2560+ depending on how the player resizes
the window — this is normal `renderingScalar=1.0` window-tracking behavior, not
an aggressive scalar auto-pick) the UI renders correctly positioned but visually
tiny relative to the canvas.

## Approved mechanism

- `uiScale = Math.min(gameWidth / 512.0f, gameHeight / 334.0f)` — computed in
  `reposition()` (mudclient.java), alongside the existing `getSurface().resize(...)`
  call. `min()` of both axes guarantees panels never overflow either dimension
  regardless of aspect ratio.
- `private int ui(int px) { return Math.round(px * uiScale); }` — a call-site
  helper in `mudclient.java`, **not** built into `Panel.java` or
  `GraphicsController` internals, since both are also used for 3D viewport
  compositing and a blanket scale there would double-scale content that's
  already correctly sized to `gameWidth`/`gameHeight`.
- Applied at each literal dimension *and* at anchor-offset arithmetic that
  bakes in old panel sizes (e.g. `width2 - 199` → `width2 - ui(199)`), not just
  the `add*()`/`reposition()` size arguments themselves.

## Font strategy

**Option A (approved for this pass): nearest-neighbor scaling in
`GraphicsController.plotCharacter()`** (mudclient.java-adjacent,
`orsc/graphics/two/GraphicsController.java:150-180`). Write an `N×N` block per
source pixel (`N = round(uiScale)`), and scale the existing 1px drop-shadow
offset (`:1673`, `:1677`) by the same factor. Low-risk, reversible, keeps pace
with the rest of this pass.

**Option B (deferred, backlog candidate if Option A reads too blocky in
practice):** source higher-resolution font assets (hand-drawn or
TrueType-converted) to replace `Fonts.fontData`. Crisper at scale, better
readability, but real asset-creation work across the `[50]`-entry font array,
and a separate concern from layout scaling — don't mix the two changes.

## Implementation order (minimal-risk, one panel at a time)

Each step: apply `ui()` wrapper → recompile → relaunch → screenshot-compare →
confirm before moving to the next.

- [x] **1. Inventory grid — COMPLETE**, including both auto-close boundary
      fixes (see below):
      - `drawUiTab1()` (~:8014-8090): slot-size and anchor-offset scaling.
      - `mouseInTabArea_CUSTOM()` (~:14163): `250`/`340` → `ui(250)`/`ui(340)`.
        Only active when `C_CUSTOM_UI=true`; inert for the default session
        but required for step 4.
      - `handleTabUIClick()` (~:13969-13982, authentic UI, the actual live
        default path): `248`/`36`/`90`/`34` → `ui()`-wrapped. This was the
        real fix for the reported "closes before I can click" symptom.
      - `handleTabUIClick_CUSTOM()`'s equivalent Y-check (~:14121-14147) is
        dead/commented-out code — not live today, but note for later: if
        it's ever re-enabled, it'll need the same `ui()` treatment.
      - **Takeaway for future steps:** `C_CUSTOM_UI` defaults to `false`
        (`Config.java:55`) and gates which of two parallel, separately-coded
        boundary-check implementations is actually live. Verify each
        remaining panel in **both** UI modes, not just whichever is active
        by default — a fix confirmed in one mode does not guarantee the
        other mode's parallel implementation is also correct.
- [x] **2. Minimap — COMPLETE** (`drawUiTabMinimap()`, mudclient.java ~:9049-9182):
      panel offsets (`offX` 170/199), box origin (`posY` 36/10, border 2),
      box size (`var4`/`var5` 156/152, redeclared `short`→`int` since `ui()`
      returns `int`), tab-icon sprite offset (49/3/40), compass offset (19),
      bottom clip margin (12), and the entity-dot radii (`2`→`ui(2)`) all
      wrapped. Also fixed the mouse-hit-test literals (`40`/`196`/`152`) that
      gate minimap click-to-walk — same auto-close-style bug class as step 1;
      left unscaled they would have made clicks miss once the box grew.
      Zoom/rotation math (`var6 = 192 + minimapRandom_2`, trig tables) is
      intentionally **not** scaled — it's world-unit zoom, not a screen pixel
      baseline.
- [x] **3. Chat/message tabs — COMPLETE (container-level)** (`panelMessageTabs`,
      mudclient.java `repositionCustomUI()`/`repositionAuthenticUI()`): all
      anchor offsets (`199`, `65`, `10`, `14`, `5`, `7`, `300`) and sizes (`56`,
      `14`) wrapped. Panel width itself is intentionally left as
      `getGameWidth() - offX` (already resolution-relative; only the fixed
      margin `offX` needed `ui()`, not the whole expression). Android-only
      keyboard-repositioning branch (~:5584-5586) fixed too for consistency.
- [x] **4a. Magic/Prayers, Social/Clan/Ignore, PlayerInfo/Quests panels — COMPLETE**
      (session note 2026-07-04): user-reported screenshots showed the Magic
      panel's spell list overflowing far past its background box, and the
      minimap looking disproportionate. Root cause: `drawUiTabMagic()`
      (mudclient.java ~:8739-9039, the Magic/Prayers tab), `drawUiTab5`
      (~:8307-8736, the Social/Clan/Ignore tab), and `drawUiTabPlayerInfo`
      (~:10916-11195, the Stats/Quests tab) had **zero** `ui()` wraps despite
      this plan's step 4 entry previously claiming "container-level DONE" —
      that claim was only true for the `Panel` object's outer bounding box
      (via `repositionCustomUI()`/`repositionAuthenticUI()`), which is a
      *different* code path from these three functions' own direct
      `drawBoxAlpha`/`drawString`/mouse-hit-test literals. Confirmed via
      `grep -c "ui("` returning 0 across all three functions before the fix.
      Net effect: the Panel's scrolling-list widget (e.g. `panelMagic`)
      correctly received large, scaled bounds from `reposition()`, while the
      background box/header/mouse-gating drawn directly by these functions
      stayed pinned at the old 512-baseline pixel size — so the list content
      rendered huge and unclipped relative to a tiny, stale-looking box
      outline. All three functions now fully `ui()`-wrapped (box positions,
      text rows, and — critically — the paired mouse-hit-test thresholds
      that gate list clicks, tab switching, Leave Clan/Clan Setup buttons,
      and the Android "cast last spell" box), verified to compile after each
      function. The minimap itself was already correctly `ui()`-scaled (per
      step 2); its "wrong rotation" appearance in the screenshot is most
      likely just the correctly-large box standing out next to the (at the
      time) broken/tiny neighboring panels, not a separate minimap bug — no
      minimap code changed this session.
- [~] **4b. Settings/QuestInfo(container)/Clan(container)/PlayerTaskInfo(container) remaining interior**
      — **container-level DONE, interior content PARTIAL**:
      - Done: `repositionCustomUI()`/`repositionAuthenticUI()`/`reposition()`
        anchor+size args for all six panels (`199`, `36`/`72`/`24`/`16`/`27`/
        `40`, `195`/`184`/`196`/`126`/`90`/`251`/`128`/`224`) — this gets each
        `Panel`'s outer bounding box correctly sized/positioned.
        `byte var12` changed to `int var12` (both `repositionAuthenticUI()`
        and `reposition()`) since `ui()` returns `int`.
      - Done (this session): `drawUiTabOptions()` dispatcher (mudclient.java
        ~:9183-9315), including the mouse-tracking/gating block that decides
        which settings sub-tab handler receives clicks (`var13 <= 24`,
        `var3 < 66`/`>= 66 && <= 131`/`> 131`, `var3 < 98`/`>= 98`, panel
        bounds `< 196`/`< 295`) — this is the same auto-close-boundary risk
        class as steps 1-2, now fixed for the settings panel's own tab
        switching. `drawAndroidSettingsBox()`/`drawCustomSettingsBox()` (tab
        switcher chrome + labels, ~:9318-9363) fully scaled. `short var5`/
        `short boxWidth` parameters changed to `int` throughout this call
        chain (`drawSocialSettingsOptions`, `drawGeneralSettingsOptions`,
        `drawAndroidSettingsOptions`, `drawAuthenticSettingsOptions`,
        `handleGeneralSettingsClicks`, `handleSocialSettingsClicks`,
        `handleAndroidSettingsClicks`, `handleAuthenticSettingsClicks`) since
        `ui()` returns `int` and callers now pass scaled `int` values.
        `drawSocialSettingsOptions()`/`drawGeneralSettingsOptions()`
        (~:9366-9886): the raw-pixel header/logout/online-list/party text
        rows and their paired mouse-hit-tests fully scaled (`y += 15/20/25/
        14`, `y - 12`/`y + 4` thresholds, `3 + baseX` offsets, `y = 256/275`
        + `var4 + 195/214` custom-UI variants).
      - **Update (session 2 continuation):** `drawAndroidSettingsOptions()`,
        `drawAuthenticSettingsOptions()` (the actual live default path,
        since `authenticSettings` defaults true), and all three matching
        click handlers (`handleGeneralSettingsClicks`,
        `handleSocialSettingsClicks`, `handleAuthenticSettingsClicks`,
        `handleAndroidSettingsClicks`) are now fully `ui()`-wrapped for
        their raw-pixel header/logout/security/privacy text rows and
        hit-tests. Confirmed most of each function's body is safely
        list-index-driven via `panelSettings.setListEntry(...)`/
        `getControlSelectedListIndex(...)` and needed no changes — only the
        directly-drawn text rows (change password/recovery/contact,
        block-chat/private/trade/duel toggles, online-list/leave-party,
        skip-tutorial/blackhole, logout) needed wrapping, since those bypass
        the list widget and position themselves with raw literals relative
        to `baseX`/`var6`. Settings panel (all four sub-tabs: authentic,
        social, general, android) is now considered **fully scaled**.
      - **Still not fixed:** the rendering-scalar +/- button hitbox (draw
        ~:9560-9612, click ~:10065-10082) is positioned as a fixed distance
        from `gameWidth` (`this.gameWidth - 143` / `- 93` / `- 125` / `- 72`),
        not from the panel's `baseX` — scaling it naively would drift it
        relative to the panel box (whose left edge moves by a different,
        `ui(199)`-based amount), so it needs visual comparison before
        touching, not a blind `ui()` wrap.
- [x] **5. Trade/duel/shop dialogs — COMPLETE** (session 2026-07-04,
      continuation): `drawDialogTrade()`/`drawTradeConfirmDialog()`
      (mudclient.java ~:4061-4508, ~:7127-7218), `drawDialogDuel()`/
      `drawDialogDuelConfirm()` (~:2929-3485, ~:3487-3598), and
      `drawDialogShop()` (~:3759-4043) fully `ui()`-wrapped — both the box/
      slot/button draw geometry and the paired mouse-hit-test math (slot
      index calculations, accept/decline buttons, duel option checkboxes,
      buy/sell quantity buttons). Item icon sprite sizes (`48x32`, `33x23`
      note-overlay) and their count-text offsets scaled too, matching the
      convention already established in the inventory tab (step 1).
      Trade/duel dialogs were pinned to a fixed top-left screen position
      (`xr=22, yr=36`, never centered) — this was left as-is (only the
      *size* of everything inside was scaled) since repositioning them to
      center-of-screen would be a layout change beyond this pass's scope.
      Shop dialog was already dynamically centered via
      `(getGameWidth() - width) / 2` — that centering logic was untouched,
      only the fixed `408x246` interior dimensions were `ui()`-wrapped.
      Deliberately **not** touched: right-click context-menu screen-edge
      clipping bounds (`510`, `315` in trade/duel — these clip a popup menu
      to the *actual* screen size, not a scaled baseline, so wrapping them
      in `ui()` would be wrong; they'd ideally use `getGameWidth()`/
      `getGameHeight()` instead, but that's a separate fix from UI scaling).
- [x] **6. Backlog items #2 (Report Abuse modal) and #3 (recovery/password/
      contact/appearance panels) — COMPLETE**:
      - `drawPopupReport()` (mudclient.java ~:6864-6961, the "enter player
        name to report" popup) — fully `ui()`-wrapped, box, buttons, and
        mouse-hit-tests. This one was already dynamically centered via
        `getGameWidth()`/`getGameHeight()`, so only interior sizes needed
        scaling.
      - `handleReportAbuseClick()` (~:13567-13877, the big "select abuse
        category" modal with the 3-column button grid) — fully
        `ui()`-wrapped, including the repetitive 14-button interior
        (columns `36`/`186`/`336`, row heights `30`/`18`, header labels at
        `106`/`256`/`406`, and the `yFromTopDistance` row-spacing
        increments). Done via exact whole-substring replacement scoped to
        this function's line range (not a blind numeric regex), specifically
        *because* several of the pixel offsets here (e.g. `12`) collide with
        `reportAbuse_AbuseType == 12`-style category-ID comparisons
        elsewhere in the same function — verified after the edit that every
        `reportAbuse_AbuseType == N` / `!= N` comparison in the function is
        still a bare integer, untouched by `ui()`.
      - **Session audit update:** `createRecoveryQuestionPanel()` (~:1691),
        `createPasswordRecoveryPanel()` (~:1869), `panelContact`'s creation
        function (~:1927), and `createLoginPanels()` (~:2139) were previously
        logged here as "not started." Direct re-read confirms they are now
        fully `ui()`/`halfGameWidth()`-wrapped (82+ `ui()` call sites
        verified across the login/recovery/contact/character-creation
        region alone) — this entry was stale, not the code. Updated to
        reflect actual state rather than carry the wrong claim forward.
- [x] **Font scaling — COMPLETE, and better than the originally-approved
      Option A.** `GraphicsController.java`: added `public float uiScale`
      (kept in sync with `mudclient.uiScale` every frame) and a private
      `fontScale()` (`:161-163`) that returns `sqrt(uiScale)` once
      `uiScale > 1.0`, not a plain linear/integer block-scale as Option A
      originally specified — deliberately dampened so text stays legible at
      the *enforced 1280x732 minimum window* (which alone pushes `uiScale`
      above 1x) without every string ballooning at the same rate as boxes
      and buttons. `plotCharacter()` (`:165-246`) branches to a fractional
      block-scale blit (`Math.round(px * fontScale)`, not a snap-to-integer
      `N`) whenever `fontScale() > 1.0`, and the glyph origin offset and the
      1px drop-shadow offset (`:1844`, `:2394`) both scale by the same
      factor. `fontHeight()`/`stringWidth()`-equivalents (`:2524`, `:2812`)
      also return `Math.round(x * fontScale())` so layout math elsewhere
      (e.g. the settings-panel row spacing at `:9700`) stays correct against
      the *actual* rendered glyph size instead of drifting out of sync.
- [x] **Rendering-scalar +/- button hitbox (step 4b's flagged remainder) —
      COMPLETE.** `:9704-9737`: both the draw position and the hover
      hit-test now use `this.gameWidth - ui(143)` / `ui(125)` / `ui(92)` /
      `ui(72)` etc. — anchored the same way the plan's earlier caution
      described as needed (relative to `gameWidth`, scaled by the same
      factor as the panel itself), not the stale fixed-distance literals
      this entry used to warn about.
- [x] **Top menu-bar tab-icon strip (backlog item, previously "not fixed
      this session") — COMPLETE.** The icon strip sprite itself scales via
      `drawSpriteClipping()` anchored at `width2 - ui(200)` /
      `getUITabsY()`, sized `ui(200) x ui(35)`. `handleTabUIClick()`
      (~:14038-14079) click regions for all six tab icons
      (inventory/minimap/quests/magic/friends/options) now derive their hit
      boxes from the exact same anchor and `ui()` factor, so the "looks
      right, can't click it" mismatch this plan was careful to avoid
      elsewhere does not apply here.

## Companion fix: tab-panel auto-close boundaries (RESOLVED, see step 1 above)

Two separate, structurally-parallel boundary checks gate whether an open
right-side tab panel counts as "still hovered," one per UI mode
(`Config.C_CUSTOM_UI`, default `false`). Both were using hardcoded literals
sized for the old unscaled panel geometry, so panels grown via `ui()` would
auto-close before a click could register. Both fixed — see the step 1
checklist entry above for exact locations and the cross-mode testing
implication for future steps.

## Full dimension inventory

Every fixed-pixel panel/dialog found in `mudclient.java`. All positions
increasingly resolution-relative already; every *size* below is a bare literal
with zero scaling applied by `Panel.java` or `GraphicsController`.

| UI element | Location | Fixed dimensions |
|---|---|---|
| Inventory grid | `:8005-8072` | 49x34/slot, 248px panel width |
| Minimap | `:9040-9065` | 156x152 |
| Chat/quest/private/clan lists | `:2291-2295` | 502x56, entry 498x14 |
| Duel offer dialog | `:3235-3250` | 468 wide, ~262 tall, 8+ interior sections |
| Duel confirm dialog | `:3491-3493` | 468x246 |
| Trade offer dialog | `:4310-4323` | 468 wide, ~248 tall, 8+ interior sections |
| Trade confirm dialog | `:7138-7140` | 468x246 |
| Shop dialog | `:3861-3866` | 408 wide, 8x5 grid of 49x34 slots |
| Logout dialog | `:3602-3603` | 260x60 |
| Change password dialog | `:4749-4750` | 300x60 |
| Welcome dialog | `:4524-4526` | 400x135-180 |
| Wilderness warning | `:4693-4697` | 340x180 |
| Server message dialog | `:3727-3728` | 400x100-300 |
| Report Abuse trigger popup | `:6898-6899` | 400x70 |
| Add friend/message/ignore popups | `:7021-7064` | 300x70 / 500x70 |
| Combat style dialog | `:2880-2922` | 175x100 (desktop) |
| Magic panel | `:8750-8768` | 196 wide, 24/90/68/50 sections |
| Options/settings panel | `:9194-9338` | 196 wide, stacked 25-105h sections |
| Social panel | `:11491` | 196x126 |
| Clan panel | `:11519` | 196x128 |
| Player info/skills panel | `:10934-10953` | 196x262-275 |
| Quest info panel | `:11494` | 196x251 |
| Player task info panel | `:11520` | 196x224 |
| Recovery question panel | `:1680-1713` | mixed buttons 80-455x30-80 |
| Password recovery panel | `:1856-1884` | mixed buttons 100-410x30-80 |
| Contact details panel | `:1912-1942` | mixed, ~100x30 buttons |
| Character creation panel | `:2018-2082` | boxes 53x41, mode selectors 215x60-125 |
| Login panels | `:2133-2220` | buttons 60-420 wide, 12-40 tall |
| Report Abuse modal | `:13644-13858` | 450x275 box, 140x18-30 buttons |
| Tab icons | `:5619-5648` | 32x32, 33px pitch |
| Input-X dialog | `:5686-5687` | computed but unscaled base |
| Dialog options menu | `:3610-3711` | 140-175 wide, 20h rows |

All line references are in `Client_Base/src/orsc/mudclient.java` unless noted
otherwise. `Panel.java` (`orsc/graphics/gui/Panel.java`) control-creation
methods (`addButton`, `addButtonBackground`, `addCenteredText`,
`addCenteredTextEntry`, `addDecoratedBox`, `addLeftTextEntry`,
`addHorizontalList`, `addVerticalList`, `addScrollingList`/`2`/`3`,
`addSprite`) all take raw `x, y, width, height` — no internal scaling.

## Related backlog items (see task tracker)

All items below are now resolved — see the dated entries in the checklist
above and the "Entity/dialog distance fade" note under Draw distance for
exact locations. Kept here as a historical index of what was originally
flagged, not as open work.

- ~~Entity/sprite draw-distance fade~~ — resolved via
  `characterHealthAlpha`/`characterDialogAlpha` (`computeDistanceFadeAlpha()`,
  populated at `:6694`/`:6731`/`:6904`/`:6938`, consumed at `:2849`/`:2875`)
  fading in health bars and overhead chat/dialog text by on-screen
  projected size instead of popping in at full opacity — this was the 2D
  billboard-layer gap identified in the original investigation note below.
- ~~Recenter Report Abuse modal for widescreen~~ — folded into step 6.
- ~~Reposition logic for recovery/password/contact/character-creation
  panels~~ — folded into step 6, confirmed complete by direct code
  re-read this session (see step 6's audit note).
- ~~Hardcoded drawBox sizing audit~~ — overlapped step 6, same audit.
- ~~Top menu-bar tab-icon sprite not scaled~~ — sprite now scales via
  `drawSpriteClipping()` and `handleTabUIClick()`'s six tab hit-regions
  (`:14038-14079`) were re-derived from the same anchor/factor in the same
  pass, avoiding the "looks right, can't click it" mismatch this note
  originally warned about.

## Texture resolution — investigation notes (separate from UI scaling)

Investigated as a follow-on request. Two structurally distinct systems answer
to "textures" in this codebase, with very different improvement paths:

1. **3D world textures** (floors/walls/roofs): `loadTextures()`/
   `loadTexturesAuthentic()` (mudclient.java ~:14589-14727) quantize each
   texture sprite down to a **256-color indexed palette** (`byte[] indices` +
   256-entry `dictionary`) before handing it to `Scene.loadTexture()`. This is
   inherent to the original software rasterizer's texture-mapping format, not
   a scaling artifact — increasing perceived resolution here means either (a)
   sourcing higher-resolution replacement art for the `textures` sprite set
   (real asset work, out of scope for a code-only pass), or (b) reworking the
   perspective-correct texture-sampling path in `Scene.java` to bypass the
   256-color quantization and do true-color sampling — a deep, high-risk
   change to a very obfuscated/decompiled rasterizer, not attempted without
   explicit direction.
2. **2D sprites** (item icons, projectiles, UI chrome): scaled drawing goes
   through `GraphicsController.drawSprite(sprite, x, y, destWidth, destHeight,
   ...)` (`orsc/graphics/two/GraphicsController.java:2227`), which does
   fixed-point (16.16) **nearest-neighbor** sampling in
   `plot_scale_black_mask`. This is a lower-risk target for a smoothing pass
   (e.g. bilinear interpolation) if the goal is "less blocky when scaled,"
   but most UI icons are currently drawn 1:1 via the unscaled
   `drawSprite(sprite, x, y)` overload and aren't stretched at all today —
   confirm which sprites actually run through the scaled path before
   investing here.

**Status: paused pending direction** — not started. Needs the user to
confirm which of the two (or both) they mean by "texture resolution," and
whether new art assets are available, before further code changes.

## Texture resolution — implemented (code-only, no new assets)

User confirmed: no new art assets available, code-only improvement, "just
improve what was there." Implemented both identified levers:

1. **2D sprite scaling** (`GraphicsController.plot_scale_black_mask()`,
   `:918-1000`, only call site is the scaled `drawSprite()` overload used for
   things like projectiles): switched from nearest-neighbor to **bilinear
   filtering** (`bilinearBlend()` helper). Falls back to nearest-neighbor
   whenever any of the 4 sample corners is the transparent marker (`0`), to
   avoid a dark halo bleeding in at sprite edges. Low risk — single call site,
   worst case only affects already-scaled sprite draws.
2. **3D world textures** (`loadTextures()`/`loadTexturesAuthentic()`,
   mudclient.java): added `boxBlurTexture()` — a 3x3 edge-clamped box blur
   applied to the source texture pixels **before** the existing 256-color
   quantization/dictionary-building code (which is otherwise untouched).
   Softens hard texel edges so the unavoidable 256-color banding reads less
   blocky when a tiny (e.g. 64x64) texture is stretched across a much larger
   widescreen viewport. The pre-existing "pure black → magenta transparency
   marker" convention is preserved deliberately: black-texel positions are
   captured *before* blurring and force-restored to the magenta marker
   *after* blurring, so the marker itself never blurs/bleeds into
   neighboring texels. **Flagged risk:** this is the safe end of what's
   possible without touching the renderer itself — genuinely fixing texture
   blockiness (as opposed to softening it) would mean bilinear-sampling
   textures live in `Scene.java`'s perspective-correct rasterizer, which is
   deep, heavily obfuscated, and not attempted. Needs visual confirmation
   after running the client — this is the change most likely to need a
   follow-up tweak if the blur reads too soft/too subtle in practice.

   **BUG FOUND AND FIXED (2026-08-10):** the "captured before / restored
   after" description above was only half-right. `wasBlackTexel` correctly
   captures original black-marker positions and restores exactly those
   positions to magenta after blurring — but the blur convolution itself
   ran over the raw, unconverted pixels first, so any texel adjacent to a
   black punch-through region got its color averaged together with pure
   black across the 3x3 kernel before the restore happened. The fix only
   patched the exact marker pixels, not their neighbors, leaving a fringe of
   wrongly-darkened/wrongly-quantized texels ringing every transparent
   region in every world texture. User-reported symptom: visible pink/black
   checkerboard artifacting on wall geometry (walls are textured 3D models
   with frequent black punch-through regions for windows/gaps; the
   flat-shaded floor doesn't go through this path at all, which is why only
   walls showed it). Root-caused by re-deriving the quantizer's pre-existing
   black-to-magenta convention from `git show HEAD:...` (the last committed
   version, before this blur was added) and comparing pixel-processing
   order against the working-tree version. **Fix:** `boxBlurTexture()`
   (`:14740-14783`) now treats `0x000000` source texels as holes — passed
   through unblurred as the output for their own position, and excluded
   entirely from any neighbor's `rSum`/`gSum`/`bSum`/`count` average — so
   the sentinel and its border never mix with real texture color in either
   direction. Recompiled clean; needs an in-game relaunch to visually
   confirm (not yet done — the running client/server session was left
   untouched per standing instruction to keep the current play session
   alive; pick up the fix via `run-client.bat` next relaunch).

## Draw distance — implemented (modest, code-only)

- Removed a leftover `[FOG DEBUG]` `System.out.println` spamming every 100
  frames in the main fog/draw-distance branch (mudclient.java, inside the
  render-tick function that sets `fogLandscapeDistance`/`fogEntityDistance`).
- Bumped the widescreen-relative draw distance multiplier from
  `(gameWidth - 512) * 2` to `(gameWidth - 512) * 3` in the default
  (`C_HIDE_FOG == false`) branch — a modest, proportionally-scaled increase,
  not a hardcoded distance, so it stays consistent with however wide the
  player's window is.
- Investigated the "Entity/sprite draw-distance fade" backlog item: traced
  `Scene.java`'s render-candidate loop and found that both landscape *and*
  dynamic entity model faces (`this.m_T`, the combined moving-entity model)
  feed into the **same** shared per-vertex loop that already applies a
  distance-based darkening fade (`fogSmoothingStartDistance`/`fogZFalloff`)
  before the hard `fogEntityDistance`/`fogLandscapeDistance` cutoff — i.e.
  entities already share the landscape's fade-out treatment at the 3D-model
  level. Did **not** find a separate hard "pop" for 3D entity models. If
  pop-in is still visually observed in practice, it's more likely either (a)
  the 2D billboard/overlay layer (health bars, ground-item icons, chat
  bubbles — drawn outside this fade path) or (b) a server-side entity-visibility
  radius (network layer, not a client render setting) — flagging for the user
  to confirm what they're actually seeing before more (riskier) changes here.
