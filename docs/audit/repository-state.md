# Repository State Audit (Phase 0)

**Inspected commit:** `17a185b9f90b8e4fc03b7dbea978dd0593e885ea`
(`ci: seed the smoke-test SQLite database on fresh checkouts`, committed
2026-10-06T12:23:02+10:00)
**Audit date:** 2026-10-06
**Branch:** `develop` (origin: `github.com/theantipopau/runewake`)
**Upstream:** `gitlab.com/openrsc/openrsc`, `upstream/develop` at
`df86308af` (2026-10-02)

No code was modified to produce this audit. All commands were read-only apart
from `git fetch` and the builds described below.

---

## 1. Fetch, status and latest changes

- `git fetch origin --prune`: local `develop` is **0 ahead / 0 behind**
  `origin/develop` — fully synchronised at the inspected commit.
- `git status`: clean, except two deliberately untracked paths that must stay
  uncommitted: `.freebuff/` (agent workspace metadata) and
  `runewake_frame_analysis.html` (one-off rendering analysis).
- `origin/gh-pages` moved to `2f2bc5313` — the Pages deployment triggered by
  today's push, confirming the CI → Pages path works end to end.

### Recent commits (ours)

```
17a185b9f 2026-10-06 ci: seed the smoke-test SQLite database on fresh checkouts
aad70f295 2026-10-06 docs: record the 2026-10-05 theme sweep and CI session
fb498a344 2026-10-06 ci: add a world-boot smoke test and a GitHub Actions pipeline
fb6c6f775 2026-10-06 feat(client): finish the HUD-window theme sweep
8f7e40d1a 2026-09-26 docs: document the 0.1.1 rebuild command and its build-order trap
```

### What changed in the latest push

`8f7e40d1a..origin/develop`: **21 files, +1164 / −163** — the HUD-window theme
sweep (Theme.java +26 accessors, 7 windows migrated, parity 89→141, literal
guard rewritten, baseline 164→132), the boot smoke test, the GitHub Actions
workflow, and the SQLite seed fix. Two Actions runs were observed:

- `37403288521` — 3/4 jobs green; `server-boot` **failed** because a fresh
  checkout has no `server/inc/sqlite/*.db` (gitignored) and the server exits
  when the file is absent. This was a real portability defect that local runs
  masked.
- `37403854998` — **4/4 green** after `17a185b9f`; the runner log confirms the
  seed path executed (`Seeding absent … OK seeded`).

### Upstream divergence

- Fork point: merge-base `e08bf9264` (2026-06-29).
- `develop` is **59 commits ahead / 6 behind** `upstream/develop`.
- The 6 upstream commits not yet evaluated for merge (2026-09-26 → 10-02):
  `df86308af`/`cd923956b` commands docs, `bbd87f00e` "FA fix",
  `e5b86c9a4` ipban/ipmute by username, `315a265ed` grammar,
  `d8664375e` forced manual SSL cert refreshes.

---

## 2. Build and validation results (FIRST ACTION)

Run 2026-10-06 with the vendored toolchain (Zulu 8u275 + Ant 1.10.5):

| Check | Result |
|---|---|
| `ant -f server/build.xml compile_core compile_plugins` | BUILD SUCCESSFUL (23 s) |
| `ant -f Client_Base/build.xml compile` | BUILD SUCCESSFUL (4 s) |
| `ant -f PC_Launcher/build.xml compile` | BUILD SUCCESSFUL (1 s) |
| `scripts/check_theme_parity.sh` | OK — 141 checks, 0 failed |
| `scripts/check_theme_literals.sh` | OK — 132 pairs |
| `scripts/check_dependencies.sh` | OK — every referenced jar present |
| `scripts/check_hosting_config.sh` | OK — SQLITE/preservation defaults safe |
| `scripts/build_pages.sh` | OK |
| `scripts/check_boot.sh` (world boot + /healthz, /status, /metrics, 404) | OK — world booted on 43494 |

The tree builds and boots green at the inspected commit. No display is
available in this environment, so all client-side results are compile- and
guard-level only.

---

## 3. Repository inventory

**2 287 tracked files.** By extension (top): 1 285 `.java`, 170 `.png`,
117 `.xml`, 79 `.jar`, 70 `.exe`, 60 `.dll`, 44 `.sql`, 44 `.json`, 37 `.wav`,
28 `.md`, 12 `.sqlite`, 11 `.iml`.

### Top-level directories (tracked files / working-tree MB)

| Directory | Tracked | Disk MB | Role |
|---|---:|---:|---|
| `server/` | 1376 | 123 | Game server (Java, Ant build, conf, SQL schemas, vendored libs) |
| `Portable_Windows/` | 330 | 168 | Vendored JDK 8 + Ant 1.10.5 + HeidiSQL portable |
| `PC_Launcher/` | 208 | 30 | Desktop launcher/updater (builds `OpenRSC.jar`) |
| `Client_Base/` | 187 | 23 | Game client sources, resources, Ant build |
| `Android_Client/` | 107 | 2 | Android client (Gradle; **unverified**, needs JDK 11+) |
| `web/` | 10 | 1 | Static server-browser + Pages sources |
| `docs/` | 9 | 1 | RuneWake documentation + audits |
| `scripts/` | 9 | 1 | Guard/CI scripts, release + pages builders |
| `PC_Client/` | 8 | 3 | Desktop shell sources (window, sound, Discord) |
| `Packaging/` | 5 | 128 | Windows installer build (InnoSETUP-style resources) |
| `Deployment_Scripts/` | 6 | 1 | Deployment helpers (partly `unmaintained/`) |
| `assets/`, `deployment/`, `Backups/` | 3/3/1 | — | Branding images, hosting deployment, one backup artifact |
| `dist/` | 0 (ignored) | **1112** | Scratch/build output — largest consumer of disk |
| `build/` | 0 (ignored) | — | Root build output |

Tracked top-level files include `README.md`, `ROADMAP.md`, `UI_SCALING_PLAN.md`,
`SECURITY.md`, `.env.example`, `LICENSE`, `Makefile`, `docker-compose.yml`,
`run-client.bat`, `run-server.bat`, `Start-Linux.sh`, `Start-Windows.cmd`, and
the platform "Getting Started" guides.

### Generated files (gitignored — never to be committed)

`build/`, `dist/`, `Client_Base/build/`, `Client_Base/Open_RSC_Client.jar`,
`PC_Launcher/OpenRSC.jar`, `PC_Launcher/bin/`, `server/*.jar` (fat
`core.jar`/`plugins.jar`), `server/.gradle/`, `server/inc/sqlite/*.db`,
`server/client.pem` (locally generated RSA key), `Android_Client/build/`,
`Packaging/Output/`, `Packaging/staging/`, `.env`, `Client_Base/clientSettings.conf`,
`Client_Base/Cache/` per-install state, `Client_Base/client_log*.txt`.

### Large tracked files (top of the list)

| Size | File |
|---:|---|
| 60.4 MB | `Portable_Windows/zulu8.50.0.51-…/jre/lib/rt.jar` |
| 17.5 MB | `…/lib/tools.jar` |
| 16.3 MB | `…/lib/ct.sym` |
| 13.7 MB | `server/lib/sqlite-jdbc-3.50.3.0.jar` |
| 9.7 MB | `server/lib/JDA-4.0.0_55-withDependencies.jar` |
| 8.6 MB | `…/jre/bin/server/jvm.dll` |
| 7.7 MB | `PC_Launcher/Resource …` (launcher artwork) |
| 7.2 MB | `Portable_Windows/HeidiSQL_11.1_64_Portable/heidisql.exe` |

The portable Windows runtime (JDK + Ant + HeidiSQL ≈ 168 MB) dominates the
repository's binary weight. **Do not remove it** until the Windows packaging
flow has a tested replacement (Phase 1 will review version, licence and
release-time-acquisition options; Zulu 8u275 is a late-2020 build).

---

## 4. Duplicate and overlapping source trees

1. **`Client_Base/src` + `PC_Client/src` are two halves of one client, not
   duplicates.** `Client_Base/build.xml` compiles
   `srcdir="${src}:${pc_client}"` (i.e. `Client_Base/src` **plus**
   `../PC_Client/src`) with libs from `PC_Client/lib` into the fat jar
   `Client_Base/Open_RSC_Client.jar`. The 7 `PC_Client` files (window/applet
   shell, sound, Discord, osConfig) have **zero path overlap** with the 118
   `Client_Base` java files.
2. **Two Gradle projects inside `Android_Client/`**: `Android_Client/` itself
   and the nested `Android_Client/Open RSC Android Client/` (with its own
   wrapper **and tracked `.idea/`/`.iml` IDE metadata**). Only 12 java files;
   shares no source path with `Client_Base` — desktop/Android divergence is a
   real maintenance risk (mission rule 19).
3. **Two Ant installations**: `Portable_Windows/apache-ant-1.10.5` (complete,
   the one every working build uses) and `server/inc/ant/` — 13 tracked files,
   **scripts only, no `lib/`**. `sh server/inc/ant/bin/ant -version` fails with
   `Could not find or load main class org.apache.tools.ant.launch.Launcher`,
   so `Client_Base/compile_client.cmd` (`../server/inc/ant/bin/ant compile`) is
   a **broken, inactive build path** (inherited from OpenRSC).
4. **Two server build definitions**: `server/build.xml` (Ant — used by CI,
   `run-server.bat`, `Makefile` docs) and `server/build.gradle` (Gradle
   `application` plugin, version 0.1.1 — referenced by **no** CI job, Makefile
   or start script). Ant is authoritative today.

---

## 5. Build scripts: active vs inactive

**Active (verified working or exercised by CI):**
`server/build.xml` (`compile_core`, `compile_plugins`, `runserver`),
`Client_Base/build.xml` (`compile`, `runclient`), `PC_Launcher/build.xml`,
`scripts/check_*.sh` (7 guards), `scripts/build_release.py` (0.1.x bundles),
`scripts/build_pages.sh`, `run-client.bat` / `run-server.bat`,
`Start-Linux.sh` / `Start-Windows.cmd` (menu front-ends),
`.gitlab-ci.yml` (themeGuard, dependencyGuard, hostingConfigGuard,
themeParityGuard, bootSmoke, pagesBuild, build, launcherUpload),
`.github/workflows/ci.yml` (guards, client-parity, server-boot, build),
`Packaging/build-installer.bat`, `docker-compose.yml` (MariaDB only — the
game-server-in-Docker path was removed).

**Legacy / inactive / suspect:**
`Deployment_Scripts/unmaintained/*` (explicitly parked), `server/build.gradle`
(duplicate definition), `server/inc/ant/` + `Client_Base/compile_client.cmd`
(broken launcher scripts), Android Gradle (cannot be built in this
environment — JDK 11+ required; unverified), top-level `Makefile` (MariaDB
backup targets only; it does `include .env`, so `make` fails on a clone
without a local `.env`, and its legacy targets pass root passwords on
argv — header already steers users to `scripts/backup_mariadb.sh`).

---

## 6. Component relationships

```
Client_Base/src  ──┐
                   ├── Client_Base/build.xml ──► Client_Base/Open_RSC_Client.jar (fat jar)
PC_Client/src    ──┘        ▲  libs from PC_Client/lib (discord-rpc.jar)
                            │ java -jar (via PC_Launcher/…/ClientLauncher.java)
PC_Launcher ── build.xml ──► PC_Launcher/OpenRSC.jar  (the launcher itself;
                            _LAUNCHER_FILENAME = "OpenRSC.jar", excluded from
                            its own download set)
server ── build.xml ──► server/core.jar + server/plugins.jar (fat jars) ── run-server.bat / ant runserver
Android_Client ── Gradle (standalone; no shared source path with Client_Base)
Portable_Windows ── JDK 8 + Ant + HeidiSQL used by the .bat/.cmd entry points
Deployment_Scripts ── server deployment helpers (partly unmaintained)
```

**Canonical desktop client:** `Client_Base` (+ `PC_Client` as its second
source root) producing `Open_RSC_Client.jar`. The launcher downloads game
files and executes the client jar with `java -jar`.

---

## 7. Inherited from OpenRSC vs RuneWake-specific

Method: fork point = merge-base `e08bf9264` (2026-06-29). `git diff
--name-status e08bf9264 develop`: **65 added, 21 deleted, 109 modified,
1 renamed**. Cross-tree against current `upstream/develop`: 113 files differ,
21 exist only in ours, 65 only upstream; ≈ **2 150 of 2 287 tracked files
(~94 %) are byte-identical to upstream** — the fork changes a small surface.

Changed surface by area: `Client_Base` 71, `server` 60, `web` 10, `scripts` 9,
`docs` 9, `PC_Launcher` 6, `Packaging` 5, `PC_Client` 4, `Android_Client` 4,
`deployment` 3, `assets` 3, plus root files (README, ROADMAP, UI_SCALING_PLAN,
Makefile, `.gitignore`, `.gitlab-ci.yml`, docker-compose, run-*.bat).

**RuneWake-specific areas:** the `Theme` design-token system
(`Client_Base/src/orsc/graphics/gui/Theme.java` + `ThemeParityTest`), the
`scripts/` guard suite and CI pipelines, `docs/RUNEWAKE_*` audits and the
visual test matrix, `UI_SCALING_PLAN.md`, `ROADMAP.md`, the rewritten README,
`web/` + `deployment/` free-hosting work, and the release builder
(`scripts/build_release.py`).

**Inherited and still untouched:** the majority of `server/src` and the
client's `mudclient` game logic, the SQL schemas, `PC_Launcher` internals,
most resources/sprites/sounds, and all platform getting-started guides.

---

## 8. Unfinished work already present

1. **UI scaling is partial** — `UI_SCALING_PLAN.md`: steps 1–6, font scaling,
   the render-scale hitbox and the menu-bar tab strip are `[x]`; step **4b is
   `[~]`** (Settings/Quest/Clan/Task panel *interiors* still need per-literal
   `ui()` wrapping; containers are done). Geometry/anchor scaling of the
   remaining table windows continues file-by-file; `C_CUSTOM_UI` gates a
   parallel second implementation of each panel that must be checked too.
   Texture resolution: **paused pending direction** (explicit user decision).
2. **Visual test matrix pass outstanding** — 7 freshly themed HUD windows need
   a human screenshot review (premium + classic pixel-identity); only
   expected classic delta: Party-button hover red. No display here.
3. **Upstream merge pending** — 6 upstream commits (§1) not yet triaged.
4. **Android parity unverified** — cannot build (JDK 11+, no Docker here);
   nested duplicate Gradle project + tracked IDE metadata to resolve.
5. **Launcher identity work blocked on artwork** (per agent-state log).
6. **`core.jar` fatness decision deferred** (`zipgroupfileset` makes jar
   deletion a no-op unless rebuilt; affects dependency testing).
7. **Audit deliverables** — this file is the first of the `docs/audit/`,
   `docs/architecture/`, `docs/design/` set required by the mission.

---

## 9. Findings handed to Phase 1 (security) — no secret values shown

- **`.env` is no longer tracked** (removed by `123eff4d3`, 2026-09-16,
  `security: actually stop tracking .env`) **but remains in git history** in
  at least 7 earlier commits. Its *key names* include `MARIADB_ROOT_USER`,
  `MARIADB_ROOT_PASSWORD`, `MARIADB_USER`, `MARIADB_PASS`, `MYSQL_DUMPS_DIR`.
  Rotation + a history-cleaning decision are required; removal from the tip
  alone does not make committed secrets safe. Values were deliberately not
  read into this audit.
- `.env.example` and `SECURITY.md` already exist and are tracked — Phase 1
  must verify they are complete and accurate rather than assume they are.
- Legacy `Makefile` targets pass `MARIADB_ROOT_PASSWORD` on the process
  command line (visible in `ps`); a secret-safe alternative
  (`scripts/backup_mariadb.sh`) exists.
- `server/client.pem` (generated RSA key) is correctly gitignored; confirm no
  key/cert material is tracked anywhere (Phase 1 sweep + Gitleaks).
- IDE metadata is tracked: 11 `.iml` files and
  `Android_Client/Open RSC Android Client/.idea/`.
- `Backups/` contains 1 tracked backup artifact — contents need review.

---

## 10. Method and limitations

- All inventory numbers come from `git ls-files` / `git diff` / `du` at the
  inspected commit; ignored files enumerated via
  `git status --porcelain --ignored=matching`.
- Secret **values** were never printed; only key names were listed.
- No display → no visual verification; no `actionlint`/`shellcheck`/
  `yamllint`/`gitleaks` installed yet; Android and Docker builds not run.
- `dist/` (1.1 GB) is local scratch from this and previous sessions; it is
  gitignored and safe to delete.
