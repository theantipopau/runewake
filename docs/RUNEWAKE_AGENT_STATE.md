# RuneWake Agent State

Live continuation record for AI-assisted development sessions. Update before
context becomes crowded. Honest states only — do not mark anything passed
that was not actually run.

## Session: 2026-09-16 (interface scale cap + hardening handoff)

### Starting point
- Branch `develop`, commit `3c804b8fd` ("Rebrand README for RuneWake…"),
  synced with `origin/develop`.
- In-progress uncommitted work inherited from the same session:
  branding edits (ORSCApplet loading screen, Discord presence, Android
  label, Config cache-dir comment), `.env` untracked + `.gitignore` +
  `.env.example`, and four `docs/RUNEWAKE_*.md` audit files.

### Completed this session
1. **Interface scale cap** (the plan's Auto/100–250% user scale):
   - `Client_Base/src/orsc/mudclient.java`: `uiScaleCap` static (0=Auto),
     clamped into `uiScale` inside `reposition()`; new General-settings row
     "Interface scale" (list id 48, desktop-only, next to the scalar rows);
     click handler id 48 → `cycleUiScaleCap()` cycles Auto→100→125→150→175→
     200→250→Auto, persists via `saveClientSetting("ui_scale_cap", …)`, then
     re-runs `reposition()` (same path as live resize).
   - `PC_Client/src/orsc/OpenRSC.java`: loads `ui_scale_cap` at startup,
     invalid/missing → Auto; never blocks startup.
   - Cap only ever shrinks the auto fit-scale → panels cannot overflow.
2. **Docs**: `UI_SCALING_PLAN.md` gained an honest section for the above;
   this file created.

### Verification actually run
- `Client_Base` `ant compile` (portable JDK8/Ant): **passes** — note this
  target also compiles `PC_Client/src`, so both modified modules are covered.
- Not run: server build (no server-side changes this session), visual
  in-game checks (needs a human or a scripted harness; recorded as pending).

### Uncommitted working tree (as of this session's end)
- Modified: `.gitignore`, `AndroidManifest.xml` (Runewake label),
  `Client_Base/src/orsc/Config.java`, `PC_Client/src/orsc/Discord.java`,
  `PC_Client/src/orsc/ORSCApplet.java`, `Client_Base/src/orsc/mudclient.java`,
  `PC_Client/src/orsc/OpenRSC.java`, `UI_SCALING_PLAN.md`
- Deleted (untracked-in-HEAD now): `.env` (kept on disk, ignored going forward)
- New: `.env.example`, `docs/RUNEWAKE_*.md` (4 audit docs),
  `docs/RUNEWAKE_AGENT_STATE.md`

### Exact next tasks
1. Review + commit this session's changes as coherent commits (brand: …,
   security: …, ui: …, docs: …) and push.
2. Manual visual matrix pass for the interface-scale cap (see
   docs/RUNEWAKE_VISUAL_TEST_MATRIX.md).
3. Rotate any credentials that were ever committed in `.env` (they are in
   git history even though the file is now untracked).
4. Next scaling slice per plan: chat/message tab scroll-bounds audit under
   scaled fonts; then transaction interfaces (trade/duel/shop/Input-X).

### Known risks / notes
- Settings list ids: 48 was verified unused before use; 49–51 also free.
- The cap row intentionally reuses the desktop-only gating of the rendering
  scalar rows (`isAndroid()` excluded) — Android density is its own system.
- `.env` values remain in git history; history rewrite was out of scope.

---

## Session: 2026-09-16 (chat/transaction scaling audit + theme polish)

### What was done
1. **Scale-cap code verification (complete, no changes needed).** Confirmed:
   Auto = no clamp; caps only shrink the fit-derived `min(w/512, h/334)`;
   invalid persisted values fall back to Auto; draw row + click handler both
   use list id 48; cycling re-runs `reposition()` via the resize path; all
   cap values (1.0/1.25/1.5/1.75/2.0/2.5) are exactly representable floats so
   label math is exact. `saveClientSetting/loadClientSettings` verified in
   mudclient.java (~line 780-816).
2. **Smoke launch (logs only).** `orsc.OpenRSC` launches cleanly on the
   bundled Zulu8 JDK: window created, cache loaded, no exceptions from prior
   sessions' changes. Exits at `getServerConfig` with `Connection refused:
   localhost:43594` (no local server running) -> `error_game_crash`. Pixel
   inspection is not possible from the agent environment (no screen capture
   of the native window), so in-game visual checks remain a manual pass.
3. **Chat/message-tab scaling audit (no code changes needed).**
   - `createMessageTabPanel()` bounds are re-applied on every resize by
     `repositionCustomUI()`/`repositionAuthenticUI()` - fresh after cap/resize.
   - `Panel.renderScrollingList2()` derives visible rows, scroll clamp,
     scrollbar geometry AND row hit-testing from `controlHeight` +
     `graphics.fontHeight(font)` - render and input share bounds.
   - Tab label centres (ui +-100/200) match click-region centres within ~1px.
   - Font metrics consistent: `fontHeight()`, `stringWidth()` and
     `plotCharacter()` all use the same `fontScale()` (sqrt(uiScale), 1.0 at
     <=1x), so glyph size, measured widths and layout line height agree.
   - Known inherited visual limitation (documented, not fixed): the CHATTABS/
     BLUEBAR sprites are unscaled bitmaps under scaled labels; needs eyes to
     tune and is deferred.
4. **Transaction UI audit (trade/duel/shop/confirm/Input-X).** All draw and
   click bounds are `ui()`-scaled and symmetric (slot pitch ui(49) x ui(34),
   same region rects on both paths). Input-X box dims computed from scaled
   font metrics at open time; noted transient: dims are stale if the window
   resizes while the dialog is open (next open recomputes).
5. **Theme polish (committed 24cd194cc).** New Theme accessors
   dialogHeaderBar/dialogBodyFill/dialogInsetFill/dialogSelectedSlot; all
   8 transaction-modal sites routed through them; fixed the duel-offer
   header using 13175581 instead of the family-standard 192.

### Commands run (results)
- `Client_Base ant compile` (also compiles PC_Client/src): PASSED (x3 this session)
- `git diff --check`: clean
- Launch `orsc.OpenRSC` 40s: starts, fails at server-config fetch (no local server) - expected
- NOT run: any visual inspection, networked two-client trade/duel/shop tests

### Exact next tasks
1. Manual visual pass: cycle cap values at 2-3 resolutions; check chat tab
   label/sprite alignment at 250% (the known limitation).
2. Verify Input-X "resize while open" staleness matters in practice; if yes,
   recompute dims on reposition.
3. Next scaling slices per plan: bank interface audit; login/character-creation polish.

---

## Session: 2026-09-16 (Input-X hardening + bank interface pass)

### What was done
1. **Input-X resize hardening (commit 1a91c772f).** The quantity dialog
   computed its box once at open time; resizing (or cycling the interface
   scale cap) while it was open left stale unscaled dimensions that could
   clip the input text. `drawInputX` now derives the identical sizing every
   frame, so the box always matches live font metrics. No input/limit changes.
2. **Bank interface audit.** Both implementations recompute their bounds
   from `ui()` every render (width/height from BASE_WIDTH/BASE_HEIGHT),
   and every click region is the same rect as the draw (slot grid
   ui(49)xui(34), quantity-button rows, page buttons, right-click menu
   boxes all pair draw-side and hit-test-side geometry). No bound defects
   found - no corrections required.
3. **Bank theme polish (commit 7f357a088).** Header `192` / body `0x989898`
   / inset-slot `0xd0d0d0` family literals in BankInterface and
   CustomBankInterface (main panel + Assign Presets dialog) now route
   through the shared Theme accessors, consistent with the trade/duel/shop
   pass. CustomBank's own interaction palette (0x5A5A55/0x7E1F1C/0x5C5548
   etc., 33 sites) deliberately left for a dedicated pass - it is a
   different, self-consistent scheme.

### Commands run (results)
- `Client_Base ant compile` (covers PC_Client/src too): PASSED x2
- `git diff --check`: clean
- NOT run: any visual inspection; networked bank withdraw/deposit tests

### Exact next tasks
1. Manual visual pass of the theme-on bank/trade/duel/shop modals.
2. Dedicated CustomBank interaction-palette theme slice (33 literals).
3. Next scaling slices per plan: login/character-creation polish.

---

## Session: 2026-09-17 (CustomBank palette + login/character-creation polish)

### What was done
1. **CustomBank interaction palette (commit 1e46ff66c).** All 33 deferred
   CustomBank-specific colour literals mapped and classified by semantic
   role (slot fills/hover/selected, tab chrome, mode toggles, preset tabs,
   quantity controls, context menu, text accents). Added ~25 `Theme.bank*`
   accessors returning the inherited classic literal exactly when
   `C_PREMIUM_THEME` is off and the Runewake dark-fantasy token when on.
   Bonus states that were previously hard-coded: search-field focus border
   (accent while focused) and selected-control border. Draw-layer only —
   packets, hitboxes and geometry untouched (121 call sites now reference
   Theme.bank*).
2. **Login presentation (commit 23fb70257).**
   - `Panel`: visible focused-field state — rune-blue underline under the
     keyboard-focused text entry in premium mode (classic keeps the
     inherited asterisk only, no underline).
   - `mudclient.drawLogin`: the BLUEBAR bottom strip now scales with
     `ui(10)`; the status scrim grew from a fixed 30px strip to
     `ui(18) + fontHeight(4)/2` so scaled-font error/status text can no
     longer outgrow it.
   - `menuNewUser` registration form gained a proper heading ("Create Your
     Character's Account") to anchor the grouped rows.
   - Auth behaviour untouched: packets, focus/keyboard handling, remembered
     credentials, password masking all unchanged.
3. **Character creation (commit d71159921).**
   - `panelAppearance` now uses `Theme.applyBronzeButtonScheme` like the
     other onboarding panels (was the only onboarding panel on default
     scheme).
   - New `Theme.appearanceListEntry(altColor, hovered, selected)`: classic
     mode preserves the inherited white/grey/red literals exactly; premium
     reads selected as rune-blue accent, hovered TEXT_PRIMARY, idle
     TEXT_MUTED. Used by `renderCenteredList`/`renderHorizontalList`
     (verified: only the appearance panel uses these two renderers).
   - New `Panel.addColorChip(x, y, color, size)`: display-only swatch of the
     currently selected hair/top/bottom/skin tint drawn inside each picker's
     decorated box; border only in premium mode (classic draws nothing, so
     classic output is unchanged). No hitbox added.
   - Fixed the Android registration keyboard-hint position introduced by the
     heading in the login commit (-ui(121) sat between the two instruction
     rows; now -ui(136), clear of both).

### Commands run (results)
- `Client_Base ant compile` (covers PC_Client/src too): PASSED x3
  (once per slice; env: portable zulu8 JDK8.0.275 + ant 1.10.5, JAVA_HOME
  must be absolute).
- `git diff --check`: clean before each commit.
- Smoke launch NOT run this session (previous session's result stands:
  window starts, exits at getServerConfig — connection refused, expected).
- NOT run: any visual inspection; no networked/bank/auth tests.

### Exact next tasks
1. Manual visual pass: bank (classic + premium), login focus underline,
   character-creation chips/list states, at 1080p and an ultrawide width.
2. Verify the new colour chips don't collide with picker sprites at
   extreme cap values (chip geometry is derived from the same ui() scale,
   so risk is low but unverified visually).
3. Next slices per plan: social/clan tab theme tokens; launcher identity
   (blocked on art).

---

## Session (side-panel theme slice) — completed 2026-09-17

### Scope
"Social/clan tab + remaining `drawBoxAlpha` tints" slice from the modernisation
audit. Draw-layer only; no packet, input, hitbox or layout changes.

### Literals classified and migrated (mudclient.java)
- Social/clan tab strip (clans enabled + disabled branches): tab fills
  160/220-grey → `Theme.tabUnselectedFill()` / `tabSelectedFill()` (reused,
  values already matched); body backdrop 220-grey → `socialBodyFill()`.
- Stats/Quests tab strip: identical pattern → same tab + body accessors.
- Magic/Prayer tab strip: tabs → same accessors; list body → `socialBodyFill()`;
  recessed spell-description area → `spellInfoFill()`.
- Clan action buttons (Leave Clan / Clan Setup / Clan Search, both in-clan and
  no-clan layouts): idle 0x0A2B56 / hover 0x263751 / border 0xBFA086 /
  label 0xffffff → `clanActionFill(hovered)` / `clanActionBorder()` /
  `clanActionText()`. Hover assignment sites migrated together with draws.
- Combat-style rows: selected red 255,0,0 / row 190,190,190 →
  `combatStyleSelected()` / `combatStyleRow()`.
- Equipment-tab legacy equipped-item slot warning 0xFF0000 →
  `inventoryEquippedWarning()`.
- XP-counter pill + gain submenu backdrops 0x989898 (x4 sites) →
  `xpCounterFill()`.
- Tab label text (Friends/Clan/Ignore/Stats/Quests/Magic/Prayers, drawn 0) and
  the 1px structural separator lines (drawn 0) → `sidePanelTabText()` /
  `sidePanelSeparator()`. Classic stays black (unchanged output); premium tabs
  are dark so labels follow TEXT_PRIMARY and separators BORDER_DARK_MID.

### Not migrated (classified, intentionally left)
- Android keyboard/status button boxes and the Android cast-last-spell box
  (0x989898 / 0x659CDE / 0x6b8e23) — Android-only, isolated; future slice.
- World HP bar red/green, XP progress bar red/green, spell rune-status text
  colours — gameplay-standard colours shared with the wider HUD.
- Shop dialog already on `dialog*` tokens from the earlier transaction pass.
- Out-of-scope untouched: duel/trade menus (already themed), settings panels
  (already themed), Android status bar.

### Theme additions
`socialBodyFill`, `socialInsetFill` (reserved), `sidePanelSeparator`,
`sidePanelTabText`, `clanActionFill(hovered)`, `clanActionBorder`,
`clanActionText`, `spellInfoFill`, `combatStyleSelected`, `combatStyleRow`,
`inventoryEquippedWarning`, `xpCounterFill`. Classic path returns the
inherited literals exactly; premium path uses the established palette
(PANEL_INSET/PANEL_ELEVATED/OVERLAY_SCRIM/SELECTION/HOVER/DANGER/BORDER_*).

### Verification
- `Client_Base ant compile`: PASSED (twice — mid-slice and final).
- `git diff --check`: clean.
- Diff reviewed line-by-line: colour-only substitutions; the combat-style
  loop suffered temporary brace/formatting damage during replacement and was
  restored to the exact original structure before commit.
- Grep confirms zero remaining in-scope literals in mudclient.java.
- No visual inspection; no smoke launch this session (previous result stands).

### Commit
- `16f025d27` — ui: theme social/clan tabs and side-panel tints
  (Theme.java, mudclient.java)

### Exact next tasks
1. Human visual pass (unchanged, still the top blocker): social/clan tabs
   classic-vs-premium, tab-label contrast, clan button hover states.
2. Android-side button/box tints (keyboard button, cast-last-spell box) —
   small isolated future slice.
3. Launcher identity work (blocked on artwork).
4. Discrete UI-scale selector (audit item 6).

---
## Session: Android overlay theme slice (2026-09-17)

Migrated the last classified draw-layer literals in `mudclient.java`: the
Android on-screen overlays. Theme additions: `androidControlFill`,
`androidControlBorder`, `androidCommandFill`, `androidCastHeaderFill`,
`androidCastRemoveFill`. Sites migrated (draw + paired border calls only;
hitboxes, command text and click behaviour untouched):
- keyboard toggle button box (was `0x989898` @160)
- Global / Wiki chat-command buttons (was `0x659CDE` @160, black border)
- cast-last-spell widget: body (`0x989898` @210), "Tap to Cast" header
  (`0x6b8e23` @210), "Remove" button (`GenUtil.buildColor(255,0,0)` @210)

Classic path returns the inherited literals exactly; premium path uses
OVERLAY_SCRIM / PANEL_ELEVATED / SELECTION / DANGER and swaps the black
overlay borders for BORDER_DARK_MID so they remain visible on dark fills.
The commented-out Clan/Online buttons inside `/*if (S_WANT_CLANS)*/` were
intentionally left untouched. No desktop-visible pixels change: every site
is guarded by `isAndroid()`. No visual inspection possible (no Android
device / no display).

Verification: `Client_Base ant compile` PASSED; `git diff --check` clean;
diff reviewed — colour-only substitutions plus the new Theme section.

Commit: `b3ae7c791` — ui: theme Android on-screen control overlays
(Theme.java, mudclient.java)

### Exact next tasks
1. Human visual pass (unchanged, still the top blocker): now includes
   Android overlays premium-vs-classic if a device is available.
2. Discrete UI-scale selector (audit item 6) — last remaining code slice.
3. Launcher identity work (blocked on artwork).

---
## Session: audit reconciliation + theme-literal tripwire (2026-09-17)

Two follow-ups after the Android overlay slice:

1. Audit item 6 (discrete UI-scale selector) was found already implemented
   in `ebfc99afe` — settings row 48 cycles Auto→100→125→150→175→200→250%,
   persists `ui_scale_cap` via `saveClientSetting`, loads in
   `OpenRSC.java` with a safe Auto fallback on invalid values, and clamps
   the auto-derived scale in `reposition()` (cap only shrinks, so panels
   always fit). The audit doc was stale; item now closed there. No code
   change was needed.

2. Added `scripts/check_theme_literals.sh`: a baseline-diff tripwire over
   the client UI layer (`Client_Base/src/orsc`, `com/openrsc/interfaces`,
   `PC_Client/src/orsc`, excluding Theme.java). It fails when any
   0xRRGGBB / 0xAARRGGBB literal is added, removed or changed without a
   baseline update, forcing conscious classification (Theme accessor or
   documented baseline regeneration via `--update-baseline`). Baseline:
   343 unique file:literal pairs, generated from the current tree.
   Detection verified with a temporary injected literal (failed with a
   precise diff, then passed after revert). Wired into `.gitlab-ci.yml`
   as a `verify` stage running before the build.

Verification: tripwire OK on clean tree; `Client_Base ant compile`
PASSED. No visual work this session.

Commits:
- `a441cbf23` — ci: add theme-literal tripwire and close stale audit item
  (scripts/check_theme_literals.sh, scripts/theme_literal_baseline.txt,
  .gitlab-ci.yml, docs/RUNEWAKE_MODERNISATION_AUDIT.md)

### Exact next tasks
1. Human visual pass (unchanged, still the top blocker).
2. Launcher identity work (blocked on artwork).
3. Optional: migrate baseline-pairs toward Theme accessors slice by slice
   (bank of classified-but-unmigrated literals is inventoried in the
   baseline file for exactly this purpose).

---

## Session: clan/party social-GUI theme family (`11f31b6ba`)

Continued the baseline-driven migration with the largest coherent family:
`ClanInterface` (45 literals) and its byte-level fork `PartyInterface`
(44 literals, plus `0xffffff` close-button label) share one template, so
a single Theme section serves both.

- **Theme additions**: `CLASSIC_SOCIALGUI_*` constants + 42
  `socialGui*` accessors — body/backdrop/table-header fills, alternating
  list rows + hover highlight + borders (mates and search variants),
  header band, inner card, card shadow, outer border, three separators,
  nine text roles (title/body/bright/accent/label-accent/detail/hint/
  muted/search-title/value/submit/secondary), and the five-button
  scheme (`NavFill(checked,hovered)`, `InputFill(checked)`,
  `SearchEntryFill/Border`, `SelectFill/Border`,
  `SubmitFill(hovered)/Border`, `CloseFill(hovered)`).
- **Migration**: 219 substitution sites (105 clan + 114 party); only
  the commented-out `SocialLists.partyListCount` dead line keeps its
  literal (same treatment as clan's commented blocks).
- **Premium defect caught pre-commit**: search text entries are created
  with `useAltColor=false` → Panel draws black text; the original
  premium fill (near-black `OVERLAY_SCRIM`) would have hidden typed
  text. Fixed to a light parchment fill (`TEXT_PRIMARY`) with the
  constraint documented on the accessor.
- **Process note**: the first migration pass (multiline str_replace)
  consumed newline runs and merged adjacent statements; restored the
  file and redid the migration with exact-token `sed` substitutions,
  then verified whitespace neutrality via whitespace-stripped diff
  against HEAD before compiling. ClanInterface (migrated pre-restart
  with the same batch approach) was re-checked the same way and found
  clean.
- **Tripwire**: baseline regenerated 343 → 254 pairs; check passes.
- **Verification**: `ant compile` passed (3 runs), `git diff --check`
  clean, full diff reviewed. No visual inspection (no display).

Commits:
- `11f31b6ba` — ui: theme clan and party interfaces
  (Theme.java, ClanInterface.java, PartyInterface.java,
  scripts/theme_literal_baseline.txt)

### Exact next tasks
1. Human visual pass (unchanged, still the top blocker).
2. Launcher identity work (blocked on artwork).
3. Continue baseline-driven migration slice by slice — next largest
   coherent families: `AuctionHouse` (~30 literals, blue-tinted family),
   `IronManInterface` (~25, own button family), then the remaining
   smaller misc panels.

## Session: minimap/compass chrome + graphics survey (2026-09-24)

### Starting point
- Branch `develop` at `9e55d0398`, tree clean (`.freebuff/` untracked as
  usual). Request: "further graphical enhancements — textures, models,
  scaling, widescreen, icons, map".

### Graphics survey (what was learned)
- `GraphicsController.drawMinimapSprite` (orsc/graphics/two) rasterises the
  rotating terrain with a **fixed 1:1 texel mapping** — the `var5` argument
  is the zoom (192 normal / 128 compass) and is already parameterised at
  every call site *including* the click-to-walk inverse in
  `drawUiTabMinimap`, so a future zoom feature is feasible but needs a
  careful visual pass (the anti-bot jitter interacts with clipping).
- `drawSpriteClipping` is the codebase's fixed-point **sprite scaler**
  (16.16 fixed point); the top MENUBAR icon strip already scales through
  it. The classic-tab side-panel icons (LEFTARROW/RIGHTARROW etc. via
  `panel.addSprite`) still draw native-size in custom UI — a candidate
  icon slice.
- Minimap terrain colours come from `Scene.resourceToColor` →
  `World.drawMinimapTile` (1 minimap px per 3 world px, corners split A/B)
  into a 512×512 offscreen `minimapSprite`.
- The premium defect class from the clan/party slice does not apply here:
  the new minimap draws are backdrop/border only, no text entries.

### Implemented this session (`e614ab6e8`)
- Theme.java: four accessors `minimapBackdropFill`, `minimapFrameColor`,
  `minimapCompassBackdropFill`, `minimapCompassRingColor` (classic
  constants 0x000000 each; premium PANEL_INSET / BORDER_DARK_MID /
  PANEL_ELEVATED / BORDER_DARK).
- `mudclient.drawUiTabMinimap`: viewport backdrop + frame themed;
  translucent compass plate (ui(10)² at the dial centre, alpha 200) plus
  bevel ring (ui(8)²), both inside the existing clip so they cannot leak.
- Classic mode byte-identical (accessors resolve to 0x000000 and the added
  draws are no-ops there); draw-layer only — no geometry, zoom, packets or
  input changes; the viewport intentionally stays native-size because
  `drawMinimapSprite` has no scale factor.

### Verification
- `ant compile` pass; smoke launch alive 12 s (log shows only the known
  no-local-server `getServerConfig` ConnectionRefused); theme tripwire
  passes at 254 pairs (the new CLASSIC_MINIMAP_* literals live in
  Theme.java accessor guards, which the tripwire ignores by design).
- Honest limitation: no display — framing/contrast unverified visually.

### Process note (tools)
- mudclient.java is **LF in the repo** (unlike Theme.java which is CRLF);
  `${CR}` sed patterns silently no-op there. GNU sed `a\`/`i\` in this
  Git Bash eats one leading tab of inserted text — check with `cat -A`
  and re-indent. A multi-line `perl -0pe` splice duplicated a line once;
  restoring via `git checkout HEAD -- <file>` and preferring whole-line
  `sed a\`/`i\` edits was the clean path.

### Exact next tasks
1. Human visual pass (unchanged top blocker; now also covers the minimap
   frame/compass plate in premium mode).
2. Minimap zoom for the map tab (zoom is parameterised; needs visual
   tuning of the click inverse and jitter) — draw-layer only.
3. Scaled side-panel tab icons via drawSpriteClipping in custom UI.
4. `World.drawMinimapTile` 2× supersampled map pixels (crisper terrain).
5. Continue baseline-driven theme migration (AuctionHouse, IronMan).

## Session: minimap zoom + supersample + auction house (2026-09-24, continued)

Same day, continuing from `58b0eccc6`. Three slices landed, one deferred.

### Slice 1: minimap zoom (`33c6df17d`)
- Survey finding from the earlier session held up: `drawMinimapSprite`'s
  texel-ratio argument (192 + anti-bot jitter) is the single zoom constant
  and the click-to-walk inverse divides by its exact reciprocal, so
  multiplying the constant by a user factor on BOTH sides keeps clicks
  true at any zoom.
- New settings row "Minimap zoom" (list id 49, next to the interface-scale
  row) cycling 100 -> 150 -> 200 -> 75 -> 100, persisted as
  "minimap_zoom", loaded by PC `OpenRSC` with sanity bounds (invalid ->
  100%, never blocks startup).
- Default 1.0 leaves the integer constant bit-identical (authentic render).

### Slice 2: minimap supersample (`0c9321db1`)
- World now rasterises the 285x285 minimap into a private 570x570 buffer
  via `mm*` helpers (clipping semantics copied from GraphicsController:
  inclusive-edge runs, boundary-clip exactness), then publishes through a
  2x2 box downsample into `minimapSprite`.
- Consumer geometry untouched by construction: sprite stays 285x285, so
  zoom setting and click inverse need no changes. Side benefit: the map
  no longer detours through the live main surface.
- The second wall-drawing block feeding the discarded WORLDMAP copy was
  left byte-identical (its output is never consumed).

### Slice 3: auction house (`94b8d3541`)
- Largest unmigrated UI file (~89 literals) migrated to a new
  `Theme.auction*` family (32 accessors, ~100 refs in AuctionHouse.java).
- Role map covered the three button schemes (plain / fancy / text-hit
  with idle-checked-hover), danger states for cancel-arming, and the
  file's quirks preserved verbatim in classic mode: the `0x45454545`
  alpha-bleed row fill, the 7-digit `0xfffffff` highlight, decimal
  red/green colours, and the shared `0x980000` behind the cancel button
  and its confirm strip.
- Self-caught defect: pass A initially mapped drawTextHit's checked TEXT
  to a FILL token (dark-on-dark) — fixed to the purpose-built
  `auctionTextHitActiveColor` before compile.
- Tripwire baseline 254 -> 228 pairs (pure removals, nothing added).

### Deferred: side-panel icon scaling
Panel sprite entries are decorative (no hit regions involved), but
`drawSpriteClipping`'s requiresShift branch returns early — draws
nothing — when the sprite's something1/2 metadata is zero. Whether the
LEFTARROW/RIGHTARROW GUI sprites carry that metadata cannot be verified
without a display; an invisible-arrow regression is worse than native-
size arrows. Revisit after the human visual pass.

### Verification
- Per slice: `ant compile` (covers Client_Base + PC_Client sources),
  smoke launch alive 10 s (log shows only the known no-local-server
  getServerConfig refusal), tripwire pass. AuctionHouse diff verified
  whitespace-neutral against HEAD. No visual inspection (no display).

### Process notes (tools)
- Confirmed again: GNU sed `a\`/`i\` in this Git Bash eats one leading
  tab of inserted text. Reliable alternative used throughout: `r file`
  inserts (append-after-match from a prepared file) plus line-scoped
  `sed Ns/…/…` for mid-block placement and one-line fixes.
- `file` reporting Theme.java as "ASCII text" (was "CRLF ...") after an
  insert is autocrlf cosmetics; committed blobs are LF in this repo and
  `git diff` shows only real content lines.
- Multi-value `sed -e`/`;`-chained literal migrations: run longer
  literals before their prefixes (0x45454545 before 0x454545) and audit
  leftovers with `grep -oP '0x[0-9A-Fa-f]+' | sort | uniq -c` afterwards.

### Exact next tasks
1. Human visual pass (top blocker; now includes zoom cycling, supersampled
   map pixels and the auction house premium scheme).
2. IronManInterface migration (~25 literals, own button family).
3. Side-panel icon scaling, deferred pending the visual pass above.

## Session: 2026-09-24 (landing page, dependency guard, README)

### Completed
1. **Dependency guard and documentation** (`5107957ec`): added
   `docs/DEPENDENCIES.md`, repaired stale named server jars in
   `server/build.xml`, added `scripts/check_dependencies.sh`, and wired
   `dependencyGuard` beside the existing theme guard in `.gitlab-ci.yml`.
   The server core and plugins both compile with the repaired classpaths.
2. **Custom side-panel backdrops** (`24ff007f0`): social/clan/ignore,
   magic/prayer, and stats/quests receive opaque `Theme.panelFill()`
   backdrops only under `C_CUSTOM_UI`; the branch-specific stacked heights
   are preserved, and classic UI remains guarded.
3. **Static landing page**: created `web/site/index.html` and
   `web/site/README.md`, using the premium dark-fantasy palette, responsive
   CSS, local links, a hero/feature/contributor/documentation structure, and
   optimised owner-supplied artwork copies. The page does not depend on a
   framework or remote font.
4. **README and documentation**: rewrote `README.md` as a project guide with
   badges, quick paths, feature map, documentation index, Pages link, and
   historical/legal attribution. Updated asset, visual, modernisation, and
   project-state records.

### Verification actually run
- `bash scripts/check_dependencies.sh`: **passed**.
- `bash scripts/check_theme_literals.sh`: **passed**, 228 pairs.
- `cd server && ant compile_core && ant compile_plugins`: **passed**.
- `Client_Base ant compile` had passed before the committed backdrop slice;
  rerun in the final verification pass before push.
- Image dimensions and output sizes were checked with Pillow.
- No native game window or display is available, so side-panel and landing
  page appearance remains explicitly unverified. The extracted gameplay
  frames are pre-fix documentation art, not visual-test evidence.

### Untracked files not for commit
- `.freebuff/` is client metadata and must remain uncommitted.
- `runewake_frame_analysis.html` is a scratch contact sheet and must remain
  out of commits.
- `.env` must remain untracked; never add it to a commit.

### Next steps
1. Run the final client compile, guards, and static-site sanity checks.
2. Human visual pass at the matrix resolutions, especially the three new
   custom-UI panel backdrops and the landing page at phone/tablet/desktop.
3. Keep the JDA/SLF4J mismatch as a separate runtime migration decision;
   do not add mismatched 2.x jars as a false fix.
4. Publish the landing page through the repository's GitHub Pages branch
   workflow after the develop changes are pushed.

---


User supplied a 26s gameplay recording (2496x1482, custom UI, live
server) plus answers: texture popping = walls/objects blinking, scaling
wrong everywhere, map tab opened briefly.

### What the frame analysis found
- No sky/terrain flicker exists: sky brightness never dips (min 27.8,
  no black frames, no alternating deltas) — the "popping" candidates in
  the scene timeline were all **tab open/close transitions**.
- Map-open runs: f83-162 (wrench), f194-221 (inventory), f345-379
  (magic); the map tab itself shows at f163-180 as a native-size tan
  square under the giant MENUBAR strip.
- Defect 1 (legibility): custom-UI panels draw translucent panelFill
  (alpha 160) boxes with no opaque backdrop — the bright 3D world bled
  straight through settings rows and inventory slots.
- Defect 2 (map scale): the minimap viewport is fixed 156x152 design px
  (the code comment says "native pixel size... otherwise the map
  renders as a small diamond adrift"), and the click region began at
  ui(40) inside the box, eating the left strip at high uiScale.
- The scaled MENUBAR strip and the tab icons render correctly; the
  "oversized icons" impression is the strip's actual design (6 icons,
  each ~30 design px at ~4.9x scale) — flagged for the human pass to
  judge whether the art needs redesign, not a code bug.
- The recording predates none of the minimap chrome issues (zoom was
  100%, tab barely opened); the 2x supersample and compass plate were
  not visible in it.

### Fixed in `04f01db7f`
1. Settings body boxes alpha 160 -> 224 (authentic-settings branch).
2. Inventory grid: opaque panelFill underlay in custom UI.
3. Map tab: viewport scales by round(uiScale) in custom mode via
   mapScale; the zoom constant carries the factor (terrain quad and all
   entity offsets derive from it), dots scale via mapViewportScale,
   compass scales to match, click inverse re-derives the same constant,
   and the custom-UI hit region spans the whole box. Classic tab hit
   region preserved byte-identical.

### Method notes
- opencv-python-headless (pip) + Windows Python reads the mp4 directly;
  MSYS /tmp is invisible to Windows Python (use $TEMP or repo paths).
- Programmatic checks that actually discriminated: per-frame sky-brightness
  runs (ruled out flicker), terrain-colour signature for the map box,
  right-edge frame diffs (localized tab transitions), black-band scan
  (found only the recording's window chrome).

### Exact next tasks
1. Human re-test at the recording's window size: settings rows legible?
   inventory slots opaque? map fills the tab area and clicking walks to
   the right tile?
2. If the tab icon strip still reads oversized, that is an art/redesign
   question, not scaling code.
3. Remaining translucency: magic/stats tab (alpha 210) and friends tab
   are darker but still translucent - candidate for the same treatment
   if the visual pass flags them.

## Session: 2026-09-25 (premium login console frame)

### Completed
1. **Login/onboarding console chrome** (working tree, not committed): added a
   premium-only frame behind the welcome, existing-user, registration, and
   password-recovery forms in `mudclient.drawLogin()`. It uses a dark
   translucent `Theme` fill, a two-step bronze bevel, and rune-blue rules.
   The registration heading now selects light text only in premium mode so it
   stays readable on that dark frame; classic retains its original black text.
   The existing login status scrim now uses the same token family.
2. **Scaling and compatibility**: all frame dimensions use `ui(...)`; the
   helper returns before drawing when `C_PREMIUM_THEME` is off. No `Panel`
   controls, coordinates, click regions, or keyboard focus indices changed.
   The status scrim's classic path still resolves to the inherited black
   fill and alpha.

### Commands run (results)
- `ant -f Client_Base/build.xml compile` with the bundled Portable_Windows
  JDK 8 / Ant 1.10.5: **passed**.
- `ant -f PC_Launcher/build.xml compile`: **passed** (verification build; no
  launcher source changed in this pass).
- `bash scripts/check_theme_literals.sh`: **passed**, 228 classified pairs.
- `bash scripts/check_dependencies.sh`: **passed**.
- `bash scripts/build_pages.sh`: **passed**, 10 staged files with valid local
  links.
- `git diff --check`: **passed**.
- No native display or game window is available, so the frame, contrast,
  clipping, and scaling remain explicitly unverified.

### Exact next tasks
1. Run the human visual pass from `docs/RUNEWAKE_VISUAL_TEST_MATRIX.md` at
   minimum and maximum scales, including both registration layouts and
   Android keyboard spacing.
2. Exercise login error/connection states and confirm the status scrim does
   not obscure either status row or the focused-field underline.
3. Compare a classic-mode login/recovery screen against the previous build;
   it should be pixel-identical because the new helper is a no-op there.
4. Keep the existing uncommitted hosting changes and untracked `.freebuff/`
   and `runewake_frame_analysis.html` separate from any future client commit.

## Session: 2026-09-26 (adaptive premium login console)

### Completed
1. **Measured console geometry** (working tree, not committed): added
   `Panel.getContentBounds()`, which returns the axis-aligned bounds
   `{left, top, right, bottom}` of every visible control a panel draws. It
   measures centred/left text with the live font metrics and reads the box
   controls directly; it mutates no control or focus state.
2. **Data-driven frame**: `mudclient.drawPremiumLoginFrame(Panel, padX, padY)`
   now derives the console rectangle from those bounds instead of four
   per-screen magic numbers, so free/members welcome layouts, both
   `wantEmail()` registration layouts, and Android offsets are covered
   automatically. Width/height clamp to an envelope so long welcome text
   cannot stretch the console past the window.
3. **Status scrim alignment**: the existing-user screen's premium scrim now
   matches the console width (never narrower than the status line) rather than
   banding full-width across the splash art. Classic still uses the inherited
   full-width scrim and black fill.
4. **Robustness**: the unreachable screen-3 `panelLoginOptions.drawPanel()`
   call is null-guarded, removing a latent NPE footgun.
5. **Server observability** (working tree, not committed): hardened
   `HttpRequestHandler` on the WebSocket port. `/status` keeps its original
   fields and adds `maxPlayers`/`uptimeSeconds`; `/healthz` returns `ok` for
   liveness probes; `/metrics` exposes Prometheus gauges
   (`runewake_players_online`, `runewake_players_max`,
   `runewake_uptime_seconds`). `GET`/`HEAD` are supported, other methods get
   `405` + `Allow`, unknown paths get `404`, and every response is
   `Cache-Control: no-store`. Query strings are tolerated. No new tracking and
   no database access.
6. **Consumer + docs**: `web/server-browser/` now renders `players /
   maxPlayers` and documents the new routes; `docs/FREE_HOSTING.md`,
   `server/SIMPLE_HOSTING.md`, and `web/server-browser/README.md` describe the
   monitor/metric endpoints.
7. **Ironman window theme migration** (working tree, not committed):
   `IronManInterface`'s 12 literals now route through a new `Theme.ironman*`
   family (body/border, heading, inset plate + hover, dividers, radio badge,
   sub-menu plate, close button idle/hover, choice-box border). The off-path
   returns the inherited literals verbatim.
8. **Skill guide theme migration**: `SkillGuideInterface`'s 8 literals move to
   `Theme.skillGuide*` (translucent body, border, text, table header band, row
   band, button/tab fill states, button border).
   `scripts/theme_literal_baseline.txt` regenerated 228 -> 208 pairs.
9. **Executable theme-parity guard** (new):
   `Client_Base/test/orsc/graphics/gui/ThemeParityTest.java` +
   `scripts/check_theme_parity.sh`. It builds the test against the client jar
   with the bundled JDK and asserts, for the login / Ironman / Skill-guide
   families, that the off-path equals the exact inherited literal and the
   on-path equals the intended premium token (57 assertions). Wired into CI as
   `themeParityGuard` (build stage, after the client compile).

### Commands run (results)
- `ant -f Client_Base/build.xml compile` with the bundled Portable_Windows
  JDK 8 / Ant 1.10.5: **passed**.
- `bash scripts/check_theme_literals.sh`: **passed**, 228 classified pairs.
- `bash scripts/check_dependencies.sh`: **passed**.
- `bash scripts/check_hosting_config.sh`: **passed**.
- `bash scripts/build_pages.sh`: **passed**, 10 staged files with valid links.
- `git diff --check`: **passed**.
- `ant -f server/build.xml compile_core`: **passed** (642 sources).
- **Runtime verification (real, not compile-only)**: booted the world locally
  on the bundled JDK 8 with the SQLite backend (`com.openrsc.server.Server
  default.conf`) and exercised the HTTP face with `curl`. Observed:
  - `/healthz` -> `200`, `content-length: 3`, body `ok`, `cache-control:
    no-store`.
  - `/status` -> `{"serverName":"RuneWake","players":0,"maxPlayers":2000,
    "uptimeMillis":1113,"uptimeSeconds":1}`; `?t=1` query returns the same
    document.
  - `/metrics` -> `text/plain; version=0.0.4` with the three gauges.
  - `HEAD /status` -> `200` with `content-length: 93` and no body.
  - `POST /status` -> `405` with `allow: GET, HEAD`.
  - `/nope` -> `404`.
  The process was stopped afterwards and ports 43494/43594/8787 were confirmed
  clear; the SQLite databases are git-ignored and no server-written file shows
  in `git status`.
- **Browser verification (real)**: served `web/` statically and loaded
  `web/server-browser/index.html` against the running world; the accessibility
  snapshot shows `RuneWake`, `uptime 0m`, `5 ms`, `0` `/ 2000` `PLAYERS
  ONLINE`, and the enabled Play button, with an empty console.
- `bash scripts/check_theme_parity.sh`: **passed**, `OK: 57 theme-parity
  checks, 0 failed.` (real headless execution, not a static check).
- `bash scripts/check_theme_literals.sh`: **passed**, 208 pairs.
- `.gitlab-ci.yml` re-parsed as YAML after adding `themeParityGuard`;
  `bash -n` clean on the new script.
- No display is available for the Java client, so the login-frame padding,
  clamping, scrim alignment, premium Ironman/Skill-guide appearance, and
  classic pixel-identity remain explicitly unverified visually.

### Exact next tasks
1. Run the human visual pass added to `docs/RUNEWAKE_VISUAL_TEST_MATRIX.md`
   (frame tracks each form; long-text clamp; scrim reads as console band;
   Android offsets; classic pixel-identity).
2. Exercise invalid-credential and connection-failure statuses and confirm
   the focused-field underline stays visible inside the scrim.
3. If the measured frame is accepted, consider reusing `getContentBounds()`
   for the other premium surfaces (e.g. a themed modal backdrop) rather than
   adding more per-screen constants.
4. Operator follow-up for observability: publish the `ws_server_port`, point a
   real external uptime monitor at `/healthz`, and scrape `/metrics`; that
   requires a host and credentials this environment lacks.
5. Extend `ThemeParityTest` with each newly migrated family (next candidates:
   AchievementGUI, BankPinInterface, PointInterface) so the parity guard keeps
   covering what the baseline tripwire only classifies structurally.
6. Keep the uncommitted hosting changes and untracked `.freebuff/` and
   `runewake_frame_analysis.html` separate from any future client commit.

## Session: 2026-09-26 (dependency refresh, texture de-duplication, legacy-panel theme sweep, 0.1.1)

### What was done
- **Server dependencies refreshed to current Java-8-compatible releases**, all
  vendored in `server/lib`: netty-all 4.1.33 -> 4.1.67 (last release of that
  artifact that is still a real uber-jar), log4j 2.17.0 -> 2.25.2,
  commons-compress 1.18 -> 1.28.0, commons-lang3 3.12.0 -> 3.18.0,
  commons-collections4 4.0 -> 4.5.0, commons-codec 1.14 -> 1.19.0,
  xstream 1.4.18 -> 1.4.21, json 20190722 -> 20250517,
  guava 30.1.1-jre -> 33.4.8-jre, sqlite-jdbc 3.34.0 -> 3.50.3.0,
  disruptor 3.3.11 -> 3.4.4.
- **`commons-io-2.20.0.jar` newly vendored** (commons-compress 1.27+ declares it
  at compile scope and reaches `CloseShieldInputStream` during world load).
  Without it the server compiled clean and then died with
  `NoClassDefFoundError` at `WorldLoader.loadWorld`. Only booting the world
  caught this.
- **`slf4j-nop-2.0.0-alpha5.jar` removed** and
  `log4j-slf4j18-impl-2.17.0.jar` replaced by `log4j-slf4j-impl-2.25.2.jar`,
  the SLF4J 1.7 binder that JDA's shaded API needs. Logs now read
  `SLF4J: Actual binding is of type [org.apache.logging.slf4j.Log4jLoggerFactory]`
  instead of falling back to NOP.
- **`server/build.gradle` un-drifted** from the vendored jars (it previously
  asked for netty 4.1.107, xstream 1.4.9, guice 5.0.1, emoji-java 4.0.0), the
  unused spring repository removed, JUnit pinned, project version set to 0.1.1.
- **World texture preparation de-duplicated** into one
  `mudclient.prepareTexturePalette(Sprite, byte[])` helper used by both
  `loadTextures` and `loadTexturesAuthentic`.
- **Legacy custom windows themed**: `PointInterface`, `PointsToGpInterface`,
  `TerritorySignupInterface`, `ExperienceConfigInterface`,
  `QuestGuideInterface`, `LostOnDeathInterface` and `AchievementGUI` now use
  new `Theme.legacy*` / `Theme.points*` / `Theme.achievement*` accessors.

### Verification actually run
- `ant -f server/build.xml compile_core compile_plugins`: **passed** (642 + 471
  sources) on the new dependency set.
- `ant -f Client_Base/build.xml compile`: **passed** (124 sources, also
  compiles `PC_Client/src`).
- `ant -f PC_Launcher/build.xml compile`: **passed**.
- Server booted on the bundled JDK 8 with SQLite: reached
  `RuneWake started in 1748ms`, `Game world is now online on TCP port 43594`.
  `curl` against the WebSocket port returned `/healthz` `200 ok`,
  `/status` JSON, `/metrics` Prometheus text, `HEAD` headers with no body,
  `405` + `Allow` for `POST`, and `404` for an unknown path.
- `bash scripts/check_dependencies.sh`: **passed**, no warnings.
- `bash scripts/check_theme_literals.sh`: **passed**, 164 pairs (was 208).
- `bash scripts/check_theme_parity.sh`: **passed**, 89 checks, 0 failed.
- `bash scripts/check_hosting_config.sh`: **passed**.
- Every replacement jar was checked for Java 8 compatibility (base class-file
  major <= 52, only the root `module-info.class` newer) and for being a real
  jar with the expected classes before installation.

### Not verified (and why)
- No human visual pass: no display. All client presentation changes are
  compile- and guard-verified only.
- Discord was not exercised live; JDA is still the unchanged 4.0.0_55 shade.
- MySQL/MariaDB was not exercised against a real server; boot checks used
  SQLite.
- Docker Compose was not run (no daemon); Android was not compiled (AGP needs
  JDK 11+).

### Exact next tasks
1. Human visual pass on the seven newly themed windows in premium mode, and a
   pixel-identity spot check in classic mode.
2. `BankPinInterface`, `DoSkillInterface`, `OnlineListInterface`,
   `ProgressBarInterface`, `FishingTrawlerInterface` and `PartyGUI` still carry
   raw draw-layer literals - migrate them, then extend `ThemeParityTest`.
3. UI scaling for the legacy custom windows. Two approaches were analysed and
   both rejected for now: centralising it in `NComponent` breaks
   `NRightClickMenu` (it sizes from already-scaled `stringWidth`/`fontHeight`)
   and the screen-space `setLocation` calls in `BankPinInterface`/
   `OnlineListInterface`/`PartyGUI`/`ProgressBarInterface`; per-file `ui()`
   wrapping is safe but needs a display to confirm, because glyphs already
   scale by `sqrt(uiScale)` while boxes do not, so the boxes are currently
   `uiScale`x larger than the text needs and the padding will change.
4. Consider making `core.jar` thin (server classes only) so the redundant
   `lib/*` + fat-jar classpath, and with it the duplicate-SLF4J-binder log
   line, disappears. Needs a decision on undocumented `java -jar core.jar` use.
5. Operator follow-up: publish `ws_server_port`, attach an external uptime
   monitor to `/healthz` and a scrape of `/metrics`.
