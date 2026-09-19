# RuneWake Modernisation Audit

Snapshot: 2026-09-16, branch `develop` @ `3c804b8fd` (post README rebrand).
Companion docs: BRANDING_AUDIT, ASSET_INVENTORY, VISUAL_TEST_MATRIX,
ROADMAP.md (session log), UI_SCALING_PLAN.md (scaling source of truth).

## Current architecture

| Module | Build | Notes |
|---|---|---|
| `Client_Base/` | Ant (`build.xml`), JDK 1.8 target | Game client core (`orsc.*`) + shared `com/openrsc/client` entity/model code; compiles `PC_Client/src` into the desktop jar. Android compiles `Client_Base/src` directly into its own source set. |
| `PC_Client/` | (compiled via Client_Base) | Desktop shell: `ORSCApplet` (applet/lifecycle, `ClientPort` implementation), `OpenRSC` (JFrame wrapper), `ScaledWindow` (windowing, min 1280×732), `Discord` rich presence. |
| `server/` | Ant (`core.jar` + `plugins.jar`) | Netty + Guice + SQLite/MySQL; plugins compile against `core.jar`. |
| `PC_Launcher/` | Ant → `OpenRSC.jar` | Swing launcher; MD5-manifest updater against `game-files` branch; wrapped by `Packaging/RuneWake.exe` (csc.exe stub) + Inno Setup installer. |
| `Android_Client/` | Gradle | Reuses `Client_Base/src`; `ClientPort` stubs for platform differences. |
| `web/server-browser/` | static | Zero-backend; queries servers' `/status` directly. |
| `Portable_Windows/` | — | Bundled zulu8 JDK + Ant 1.10.5 used by every `.bat`/`.sh` script. |

Rendering flow: software rasterizer (`Scene.java`) → 2D overlay
(`GraphicsController`) with per-frame `uiScale` sync; UI drawn in design
space via `ui()` helpers; input coordinates transformed by the same factor
(verified in settings/trade/minimap paths — UI_SCALING_PLAN).

## Verified current state (this pass)

- Baseline + post-change builds all green (client, server core, plugins).
- `uiScale`/`ui()` exist and are correct; do not re-add (plan §4 rule).
- Theme token system exists (`Theme.java`); settings panel migrated this
  pass behind `C_PREMIUM_THEME` (default off ⇒ no visual change).
- Font scaling: dampened `sqrt(uiScale)` fractional glyph scaling +
  bilinear AA — implemented, better than plan's original Option A.
- Widescreen, draw-distance fade, entity/text fade, fog fixes: implemented
  and documented in ROADMAP 3–7n.

## Gaps found this pass

1. `.env` tracked in git (contains DB creds per Makefile/docker-compose
   variables) — fixed this pass: untracked, ignored, `.env.example` added.
2. Loading screen + Discord presence still said "Open RSC"/"Open source RSC
   MMO"; Android app label "OpenRSC" — fixed this pass (see branding audit).
3. Visual/interactive verification impossible here (no display) —
   matrix documents this honestly rather than claiming passes.
4. Launcher visual identity still inherited OpenRSC artwork (open, needs
   art); launcher compile not verified this pass (Android/launcher Gradle
   toolchain not exercised).
5. ~~Social/clan tab + remaining `drawBoxAlpha` tints not yet on `Theme`
   tokens~~ DONE this pass (`16f025d27`): social/clan, Stats/Quests and
   Magic/Prayer tab strips, clan action buttons, combat-style rows,
   equipped-slot warning, xp-counter pill, tab labels and separators, and
   the Android on-screen overlays (keyboard button, chat-command buttons,
   cast-last-spell widget) are all on Theme tokens.
6. ~~Discrete UI-scale selector (Auto/100–250%)~~ DONE in
   `ebfc99afe`: settings row cycles Auto→100→125→150→175→200→250%,
   persists `ui_scale_cap` in `clientSettings.conf`, loads with a safe
   Auto fallback on invalid values, and clamps the auto-derived scale in
   `reposition()`. The cap only shrinks the scale, so panels always fit
   the window.
7. Social GUI family (clan `ClanInterface` + forked
   `PartyInterface`, ~90 draw-layer literals) DONE in `11f31b6ba`:
   one shared `Theme.socialGui*` family now serves both files — window
   structure, table headers, alternating list rows, search-result rows,
   text roles and the five-button scheme (nav / input / search-entry /
   select / submit). Classic mode returns the inherited literals;
   premium uses the established tokens. Note: the search text entry
   keeps a light fill in premium because its Panel text is black
   (`useAltColor=false`) and flipping that flag would break classic.
   Remaining baseline population is the smaller misc panels
   (AuctionHouse, IronMan, SkillGuide, AchievementGUI, ...), inventoried
   in `scripts/theme_literal_baseline.txt`.

## Remaining priorities (ordered)

1. Human visual pass per `RUNEWAKE_VISUAL_TEST_MATRIX.md` (settings tab,
   login, Android cold start first).
2. ~~Theme migration: social/clan tab, then bank/magic tints~~ DONE
   (bank `1e46ff66c`, onboarding `23fb70257`+`d71159921`, side panels
   `16f025d27`, Android overlays `b3ae7c791`, clan/party GUIs
   `11f31b6ba`).
3. Launcher artwork + identity (blocked on art).
4. Discrete UI-scale setting with persisted safe fallback (design-space
   scale separate from `renderingScalar`).
5. `game-files` branch publish so installed clients pick up the build.
