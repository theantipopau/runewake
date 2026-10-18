# Runewake — GATE 0 Audit & Baseline

**Date:** 2026-10-18 · **Branch:** `develop` @ `4b0636d39`
**Method:** read-only inspection of the tree plus one live world boot.
No source was changed for this audit.

> Where the master prompt's "Current State" (§1) has drifted, this doc says
> so explicitly rather than re-doing finished work.

## 1. Secrets check

- **`.env` is NOT tracked.** Only `.env.example` is committed. The local
  `.env` holds 5 keys (`MARIADB_*`, `MYSQL_DUMPS_DIR`); values were never
  read into this audit.
- **Tracked confs are clean:** `server/connections.conf` ships an empty
  `db_pass` with a "keep production secrets out of this file" comment;
  `docker-compose.yml` reads everything from `${...}` with `:?` guards.
- **History still holds secrets** (known debt S3: 18 historical secrets,
  redacted in `.gitleaks.baseline.json`; rotation checklist in
  `docs/security/secret-rotation.md`). Per the prompt, no history rewrite
  was attempted — **rotation remains Matt's action.**
- Verdict: tip is clean; the URGENT item is rotation-of-compromised-values,
  not removal-from-tip.

## 2. Repo bloat (tracked weight)

| Path | Role | Note |
|---|---|---|
| `Portable_Windows/` (~168 MB: Zulu 8u275, Ant 1.10.5, HeidiSQL) | Vendored toolchain | Licensed bundle; do NOT remove until a packaging replacement is tested (debt S5) |
| `Backups/` (1 tracked artifact) | Backup | Contents need review; candidate for Release assets |
| `Packaging/` (~128 MB installer resources) | Installer build | Stays — it is the installer source |
| `dist/` (~1.1 GB) | Local scratch | **Gitignored**, safe to delete; includes this session's `gate0_boot.log` |

Prompt assumption "`.env` committed" is stale (fixed 2026-09-16).
Moving `Backups/`/portable runtime to Releases needs Matt's call (§8 Q3).

## 3. Toolchain table (verified in build files)

| Module | Build | Java source/target | Notes |
|---|---|---|---|
| server core + plugins | Ant (`server/build.xml`) | 1.8 | `server/build.gradle` is an unused mirror (debt M2) |
| Client_Base + PC_Client (one compile) | Ant (`Client_Base/build.xml`) | 1.8 | `srcdir=${src}:${pc_client}` (debt M6) |
| PC_Launcher | Ant (`PC_Launcher/build.xml`) | 1.8 | |
| Android (nested Gradle project) | Gradle 7.4, AGP 7.3.0, compileSdk/targetSdk 31 | — | **Unverified** — needs JDK 11+, no build here (debt R6) |
| Vendored toolchain | Ant 1.10.5, Zulu JDK 8u275 | — | CI uses Temurin 8 + apt Ant instead |
| Database | MariaDB 11.4 (compose, LTS line) | — | Latest MariaDB LTS is 12.3; Java LTS is 25 (21 previous) — server still targets 8 |

## 4. Dependency table (bundled → latest stable, Oct 2026 web check)

| Jar (server/lib) | Bundled | Latest | Flag |
|---|---|---|---|
| netty-all | 4.1.67.Final | 4.1.139.Final | **stale — check CVEs before any network work** |
| JDA (+deps) | 4.0.0_55 | 6.7.0 | two majors behind (discord paths only) |
| guice (+deps) | 5.0.2 | 7.0.0 | |
| gitlab4j-api | 4.12.17 | 6.3.0 | |
| mysql-connector-j | 9.4.0 | 26.7.0 (per Maven Central listing) | verify coordinate before acting |
| guava | 33.4.8-jre | 33.7.2-jre | near-current |
| sqlite-jdbc | 3.50.3.0 | 3.53.4.0 | near-current |
| commons-compress | 1.28.0 | 1.28.0 | current |
| commons-io | 2.20.0 | 2.22.0 | near-current |
| commons-lang3 | 3.18.0 | 3.21.0 | near-current |
| commons-codec | 1.19.0 | 1.23.0 | |
| commons-collections4 | 4.5.0 | 4.6.0 | |
| commons-lang 2.x | 2.6 | 2.6 (line EOL) | legacy, inherited |
| log4j2 | 2.25.2 | 2.26.1 | near-current |
| xstream | 1.4.21 | 1.4.21 | current |
| disruptor | 3.4.4 | 4.0.0 | |
| emoji-java | 5.1.1 | 5.1.1 | current |
| json (org.json) | 20250517 | 20260814 | |
| xpp3 | 1.1.4c | 1.1.4c | legacy, inherited |
| PC_Client/lib discord-rpc.jar | vendored | — | client voice glue |

No upgrade attempted in GATE 0 — several of these ride the fat-`core.jar`
contract (debt L4). Upgrades belong to Gate 5b with compile+boot proof.

## 5. UI_SCALING_PLAN status (verified in code, not checkboxes)

- **Done:** steps 1 (inventory), 2 (minimap), 3 (chat tabs), 4a
  (magic/social/stats), 5 (trade/duel/shop), 6 (report-abuse +
  recovery/login panels), font scaling (**better than Option A:** damped
  `sqrt(uiScale)` in `GraphicsController`, metrics kept in sync),
  render-scalar hitbox, top menu-bar tab strip, interface-scale **cap**
  setting (Auto–250%, `ui_scale_cap`, desktop-only). 1650 `ui(` call sites
  in `mudclient.java`; `uiScale` derived in `reposition()`.
- **Partial:** 4b interiors (Settings sub-tabs fully scaled; Quest/Clan/
  Task container-level only).
- **Open (largest gap):** the `NComponent` family + ~15 plain-class legacy
  windows (`BankPinInterface`, `SkillGuideInterface`, `OnlineListInterface`,
  …) — unscaled boxes vs already-scaled glyphs. Deliberately deferred:
  no display here to confirm a blind 15-file pass.
- **Deferred by decision:** Font Option B, texture resolution
  (code-only levers done: bilinear sprite path, pre-quantize blur with the
  black-marker fix; true fixes need art or a rasterizer rework).

## 6. Feature-flag inventory (naming deviation noted)

- The prompt assumes an `S_*` server-flag convention. **It does not exist:**
  server flags are lowercase `want_*`/`spawn_*`/toggles in conf
  (`ServerConfiguration` reads `WANT_*` constants from snake_case keys);
  client flags are `C_*` in `Config.java` (~25: `C_CUSTOM_UI=false`,
  `C_EXPERIENCE_DROPS`, `C_HIDE_ROOFS`, `C_GROUND_ITEM_NAMES`,
  `C_PREMIUM_THEME`, …) plus server-driven `S_*` ints the server pushes
  (`S_PLAYER_LEVEL_LIMIT`, … — different meaning from the prompt's `S_*`).
- Presets (`server/*.conf`, diffed stripped vs `default.conf`):
  preservation 96 lines differ, 2001scape 210, rsccabbage 294,
  rsccoleslaw 322, openpk 182, uranium 134. Custom surface is the
  `custom_features:` block (all-off in default; cabbage enables sprites,
  custom quests, auction house, iron-man NPCs, nametags, clans — each
  marked `# MODIFIED`). Authentic presets stay custom-off. Gate 4 must
  follow this shape, not invent `S_*` keys.

## 7. Baseline measurements (this session)

- **Server boot (live):** `default.conf` on SQLite, world up in **1800 ms**,
  `/healthz → ok`, `/status` (RuneWake, 0/2000 players), clean shutdown
  after. Log: `dist/gate0_boot.log` (gitignored).
- **Server metrics are thin:** `/metrics` exposes only
  `runewake_players_online/max` and `runewake_uptime_seconds` — **no tick
  time, no memory gauges.** Loaded-tick measurement (20 bots) has no
  harness at all. Both gaps are Gate 5e work; recorded here so Gate 5
  doesn't assume the plumbing exists.
- **Client screenshots + FPS: BLOCKED.** No display in this environment
  (`$DISPLAY` empty, headless JDK). Baseline frames at 512x346 / 1280x720
  / 1920x1080 / 2560x1440 and the 1080p busy-area FPS number need Matt's
  machine. `docs/screenshots/baseline/` intentionally not created with
  filler.
- Prior verified state reused, not re-run: 467 characterization checks,
  141 theme-parity, visual-asset guard, boot smoke (all green on `develop`).

## 8. Prompt-assumption reconciliation (docs already ahead of §1)

| Prompt claims | Actual |
|---|---|
| README still upstream text | **Rebranded** (hero, features, AGPL notice, honest visual-status box) |
| No GitHub Actions | **Exists** (5 jobs); GitLab file mirrored but never run (R5) |
| SECURITY.md points upstream | **Rewritten for RuneWake** 2026-10-06 |
| `.env` committed | Untracked since 2026-09-16; history only |
| CONTRIBUTING.md | Still upstream OpenRSC text — flag for Gate 6 rebrand, not GATE 0 |

## 9. Parking-lot seeds for RUNEWAKE_ROADMAP.md

- `server/build.gradle` vs `build.xml` drift (M2); Makefile argv passwords
  (secret-safe `backup_mariadb.sh` exists); Gradle/AGP far behind latest
  (only matters if Android is revived); MariaDB 11.4 vs 12.3 LTS;
  netty/JDA/guice staleness (table above).

---
*Not verified live: client screenshots, client FPS, loaded server tick —
needs Matt's check (no display / no bot harness here). Everything else
above was read from the tree or measured this session.*
