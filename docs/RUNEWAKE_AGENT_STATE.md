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
