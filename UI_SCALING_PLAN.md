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

- [ ] **1. Inventory grid** (`drawUiTab1()`, mudclient.java ~:8005-8072) —
      self-contained, directly-drawn (no `Panel.java` involvement), exercises
      both slot-size and anchor-offset scaling in one bounded function.
- [ ] **2. Minimap** (`drawUiTabMinimap()`, mudclient.java ~:9040-9065) —
      same directly-drawn category, validates pattern against a masked/circular
      sprite rather than a rectangular grid.
- [ ] **3. Chat/message tabs** (`panelMessageTabs`, mudclient.java ~:2291-2295,
      :11470-:11491) — first use of the `Panel`-based abstraction, still a
      single isolated panel.
- [ ] **4. Settings/Social/Magic/PlayerInfo/QuestInfo/Clan/PlayerTaskInfo panels**
      as one batch (mudclient.java :8750-:9338, :10934-:10953, :11461-:11520) —
      share `repositionCustomUI()`/`repositionAuthenticUI()`/`reposition()`,
      mechanical once the pattern is proven.
- [ ] **5. Trade/duel/shop dialogs** (mudclient.java :3235-:3250, :3491-:3493,
      :4310-:4323, :7138-:7140, :3861-:3866) — larger, more interior sections,
      same mechanical pattern.
- [ ] **6. Backlog items #2 (Report Abuse modal) and #3 (recovery/password/
      contact/appearance panels)** — fold into this pass since they need both
      repositioning *and* the `ui()` wrapper; no reason to touch twice.
- [ ] **Font scaling** (Option A, `plotCharacter()`) — apply once layout
      scaling is validated on at least the first 2-3 panels above.

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

- Entity/sprite draw-distance fade (independent of UI scaling, sequenced after)
- Recenter Report Abuse modal for widescreen (folds into step 6 above)
- Reposition logic for recovery/password/contact/character-creation panels
  (folds into step 6 above)
- Hardcoded drawBox sizing audit (overlaps step 6)
