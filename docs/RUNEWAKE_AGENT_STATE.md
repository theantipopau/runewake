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
