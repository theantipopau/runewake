# RuneWake Modernisation Audit

Snapshot: 2026-09-26, branch `develop` @ `e81e64a6` plus a large uncommitted
working tree: the hosting/database phase, the login console frame and its
measured, data-driven version, a runtime-verified server observability surface,
a complete refresh of the vendored server dependency set, a de-duplicated world
texture pipeline, the legacy custom-window theme sweep, and a headless
theme-parity guard. Targets the 0.1.1 release.
Companion docs: BRANDING_AUDIT, ASSET_INVENTORY, VISUAL_TEST_MATRIX,
ROADMAP.md (session log), UI_SCALING_PLAN.md (scaling source of truth),
FREE_HOSTING.md, and OPENRSC_ISSUE_TRIAGE.md.

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
| `deployment/systemd/` | systemd | Provider-neutral unattended Linux service template with protected environment file and restart policy. |
| `scripts/` + Compose | shell/YAML | Secret-safe hosting validation, private MariaDB Compose service, and checksummed backups. |

Rendering flow: software rasterizer (`Scene.java`) → 2D overlay
(`GraphicsController`) with per-frame `uiScale` sync; UI drawn in design
space via `ui()` helpers; input coordinates transformed by the same factor
(verified in settings/trade/minimap paths — UI_SCALING_PLAN).

## Verified current state (this pass)

- Baseline + post-change server and desktop launcher builds are green; the
  Android Gradle build remains environment-dependent.
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
   art); the desktop launcher compiles, but the Android Gradle build still
   needs a JDK 11+ environment and an installed Android SDK/device check.
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
   AuctionHouse, IronMan and SkillGuide have since migrated too (see items
   9 and 17). Remaining baseline population is the smaller misc panels
   (AchievementGUI, BankPinInterface, PointInterface, ...), inventoried
   in `scripts/theme_literal_baseline.txt`.

8. Minimap/compass chrome (draw-layer) — done in `e614ab6e8`: the custom-UI minimap backdrop/frame and the compass plate/ring now come from `Theme.minimap*` tokens; classic is byte-identical and the viewport stays native-size because `drawMinimapSprite` cannot scale. Zoom and 2x supersampling since landed in `33c6df17d` and `0c9321db1`; scaled side-panel icons deferred (see item 9).
9. Minimap zoom (`33c6df17d`), 2x supersampled minimap raster (`0c9321db1`) and AuctionHouse theme migration (`94b8d3541`, baseline 254->228) all done draw-layer only. Side-panel icon scaling deferred: `drawSpriteClipping` draws nothing for sprites whose something1/2 metadata is zero and that metadata is unverifiable without a display.
10. Video-driven fixes (`04f01db7f`): custom-UI settings/inventory panels made legible (opaque-enough fills), map tab now scales with the UI in custom mode with working full-box click region. Needs human re-test at 2496x1482.
11. Custom side-panel backdrops (`24ff007f0`): social/clan/ignore, magic/prayer (including Android cast-last-spell), and stats/quests now draw an opaque `Theme.panelFill()` backdrop only when `C_CUSTOM_UI` is enabled. Geometry follows each branch's stacked controls; classic sprite-backed rendering remains guarded and unchanged. Compile and theme guard pass; human visual retest is pending.
12. Static GitHub landing page (`web/site/index.html`): responsive dark-fantasy page with bronze/rune-blue/parchment tokens matching the premium client theme, local owner-supplied artwork derivatives, and a link to the server browser. WebP/JPEG copies are derived from `assets/` and the owner's gameplay recording; all are documented in the asset inventory and are not current visual-test evidence.
13. Dependency maintenance: `docs/DEPENDENCIES.md` inventories the vendored jars, stale server references are repaired in `server/build.xml`, and `scripts/check_dependencies.sh` plus the GitLab `dependencyGuard` job fail on missing named jars. Server core and plugins compile with the corrected classpaths. The shaded JDA/SLF4J 1.7-vs-2.x runtime mismatch remains explicitly documented for a separate integration migration.
14. Premium login console frame (working tree, 2026-09-25): `mudclient.drawLogin()` now draws a scaled, presentation-only console behind the welcome, existing-user, registration, and password-recovery forms. `Theme` owns the dark fill, bronze bevels, rune-blue rules, and status-scrim colour; the registration heading selects light text only in premium mode. Classic mode exits before drawing and retains the inherited status fill/text; no hitboxes or control geometry changed. Client compile and static guards pass, but contrast/clipping remain unverified without a display.
15. Adaptive login console (working tree, 2026-09-26): the item-14 frame is now measured instead of hard-coded. `Panel.getContentBounds()` reports the axis-aligned bounds of a panel's visible controls (with live font metrics for text) without mutating any state, and `drawPremiumLoginFrame(Panel, padX, padY)` wraps the form it backs, clamped to an envelope. The existing-user status scrim tracks the console width on the premium path while classic keeps the inherited full-width black scrim, and the unreachable screen-3 `panelLoginOptions` draw is null-guarded. Classic rendering remains a no-op path; compile and static guards pass, human visual verification still pending.
16. Server observability surface (working tree, 2026-09-26): `HttpRequestHandler` now serves `/status` (unchanged fields plus `maxPlayers`/`uptimeSeconds`), `/healthz`, and `/metrics` (Prometheus gauges) with correct `GET`/`HEAD` handling, `405`+`Allow`, `404`, and `Cache-Control: no-store`, all from already-public state and without database access. **Verified at runtime** by booting the world locally on JDK 8/SQLite and exercising every route with `curl`, and by loading `web/server-browser/` (now showing `players / maxPlayers`) against the running world. This is the first end-to-end runtime verification recorded in this audit.
17. Ironman + Skill guide theme migration (working tree, 2026-09-26): `IronManInterface` (12 literals) and `SkillGuideInterface` (8 literals) now draw through new `Theme.ironman*` and `Theme.skillGuide*` families. Off-path values reproduce the inherited literals exactly, so classic is unchanged; premium resolves to the established palette. Baseline 228 -> 208 pairs.
18. Executable theme-parity guard (working tree, 2026-09-26): `Client_Base/test/orsc/graphics/gui/ThemeParityTest.java` plus `scripts/check_theme_parity.sh` compile and run a headless check that asserts, for the login, Ironman and Skill-guide families, that the off-path returns the exact inherited literal and the on-path resolves to the intended premium token (57 checks). This turns the migration's "off path is unchanged" promise into something executable rather than asserted. The check needs the client jar, so CI runs it as `themeParityGuard` in the `build` stage.
19. Server dependency refresh (working tree, 2026-09-26): every vendored server runtime jar moved to a current, still-Java-8-compatible release - netty-all 4.1.33 -> 4.1.67 (last uber-jar release of the artifact), log4j 2.17.0 -> 2.25.2, commons-compress 1.18 -> 1.28.0, commons-lang3 3.12.0 -> 3.18.0, commons-collections4 4.0 -> 4.5.0, commons-codec 1.14 -> 1.19.0, xstream 1.4.18 -> 1.4.21, json 20190722 -> 20250517, guava 30.1.1-jre -> 33.4.8-jre, sqlite-jdbc 3.34.0 -> 3.50.3.0, disruptor 3.3.11 -> 3.4.4. `commons-io-2.20.0.jar` was added because commons-compress 1.27+ declares it at compile scope and reaches `CloseShieldInputStream` while loading world data; without it the server compiled but aborted with `NoClassDefFoundError` at world load. `slf4j-nop` was removed. **Verified at runtime** by booting the world on the new stack and re-exercising `/status`, `/healthz` and `/metrics`, which is how the missing `commons-io` was caught. This is the first dependency change in this audit with runtime evidence rather than compile-only evidence.
20. Gradle/Ant dependency drift removed (working tree, 2026-09-26): `server/build.gradle` was asking for `netty-all:4.1.107.Final`, `xstream:1.4.9`, `guice:5.0.1` and `emoji-java:4.0.0` while `server/lib/` and `server/build.xml` held 4.1.33, 1.4.18, 5.0.2 and 5.1.1, so the two build systems compiled against different artifacts. Every version string now matches the vendored filename, the unused `repo.spring.io/libs-release` repository is gone, and the JUnit test dependencies are pinned. `docs/DEPENDENCIES.md` now also documents that a vendored jar's own POM dependencies must be checked, and that a replacement must stay Java 8 compatible (base class-file major <= 52, `module-info.class` excepted).
21. Discord/SLF4J binding fixed (working tree, 2026-09-26): the previously vendored `log4j-slf4j18-impl-2.17.0.jar` is a 1.8/2.x binding and could never satisfy the SLF4J 1.7 API shaded inside `JDA-4.0.0_55-withDependencies.jar`, so JDA always fell back to the no-op logger. `log4j-slf4j-impl-2.25.2.jar` (the 1.7 binder) replaces it and the competing `slf4j-nop` alpha is removed; a boot now reports `SLF4J: Actual binding is of type [org.apache.logging.slf4j.Log4jLoggerFactory]`. The remaining "multiple SLF4J bindings" line comes from `core.jar` being a fat jar built by merging `server/lib/*.jar` while the loose directory is still on the classpath; both binders it lists are the same one. Making `core.jar` thin would silence it but changes the shipped artifact contract for undocumented `java -jar core.jar` use, so it was left as is and documented.
22. Texture-preparation de-duplication (working tree, 2026-09-26): `loadTextures` and `loadTexturesAuthentic` held byte-identical box-blur + transparency-sentinel + 256-colour-quantisation blocks, which is why the 0x000000 sentinel bug had to be fixed in two places. Both now call one `prepareTexturePalette(Sprite, byte[])` helper, so the two texture paths cannot drift. Compile-verified; the visual result is unchanged by construction.
23. Legacy custom-window theme sweep (working tree, 2026-09-26): `PointInterface`, `PointsToGpInterface`, `TerritorySignupInterface`, `ExperienceConfigInterface`, `QuestGuideInterface` and `LostOnDeathInterface` all draw directly onto the game surface (not through `Panel`) and shared the same raw-literal idiom: a 0x989898 translucent plate, black border and rules, white text, and 0x333333 controls that turn blue on hover and red when checked. They now use new `Theme.legacy*` and `Theme.points*` accessors, as does the achievement window (`Theme.achievement*`). Off-path values reproduce the inherited literals exactly, so classic rendering is byte-identical. Literal baseline 254 -> 208 -> 164 pairs across this arc, all pure removals; the parity test grew from 57 to 89 assertions.

## Current operational state

The repository is now deployable in principle, but no public RuneWake world
or managed database account is created by this checkout. The server reads
`DB_*` process-environment overrides before `server/connections.conf`, uses an
explicit Connector/J `sslMode`, and keeps SQLite as the zero-setup default.
The Compose MariaDB service binds to loopback by default and has a health
check and named volume. The systemd unit and backup helper are templates for
an operator; they do not provision cloud resources.

Database initialization remains a first-install operation. The checked-in
MySQL `core.sql`/`retro.sql` files contain destructive drops; production
upgrades use the ordered patch mechanism and verified backups. A remote
database must use a private network or verified TLS (`VERIFY_IDENTITY` is the
production default), and a restore test is required before claiming a world
is recoverable.

## Remaining priorities (ordered)

1. **Hosting rehearsal** — provision one OCI/owned host, initialize one empty
   database, run the systemd service, publish HTTPS status, test external game
   reachability, and restore a backup off-host.
2. **Database integrity** — reproduce OpenRSC #3385's `prayer` truncation,
   audit skill column ranges, and test every patch against production-shaped
   data.
3. **Observability** — the server-side surface is done and runtime-verified
   (item 16: `/status` + `/healthz` + `/metrics`). What remains is operational:
   provision a host, publish the port, and attach a real external uptime alert
   and a metrics scrape, which needs provider credentials.
4. **Human visual pass** per `RUNEWAKE_VISUAL_TEST_MATRIX.md` (adaptive login
   console frame, settings tab, Android cold start, and the newly themed
   Ironman and Skill-guide windows in premium mode first).
5. **Launcher artwork + identity** (blocked on art) and publish the
   `game-files` branch so installed clients receive the build.
6. **Optional multi-world architecture** — prototype OpenRSC #2943's login
   server only after one-world operations are proven; it is not a prerequisite
   for the first public world.
