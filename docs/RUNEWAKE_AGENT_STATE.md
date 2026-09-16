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
