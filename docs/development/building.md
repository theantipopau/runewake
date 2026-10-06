# Building RuneWake

**One command to validate everything:**

```bash
scripts/dev.sh check
```

It runs the static guards, all three compiles, the 141-assertion theme
parity, the Pages build and the world-boot smoke test — exactly the steps the
CI pipelines run, in a sensible order. On a fresh clone it needs nothing else
installed (see [local-environment.md](local-environment.md)).

## The workflow commands

| Command | Does |
| --- | --- |
| `scripts/dev.sh check` | Full validation (guards + compiles + parity + pages + boot) |
| `scripts/dev.sh build-server` | `server/core.jar` + `server/plugins.jar` |
| `scripts/dev.sh build-client` | `Client_Base/Open_RSC_Client.jar` |
| `scripts/dev.sh build-launcher` | `PC_Launcher/OpenRSC.jar` |
| `scripts/dev.sh build` | All three compiles |
| `scripts/dev.sh run [world]` | Foreground game server (`Ctrl-C` stops) |
| `scripts/dev.sh release <ver>` | Reproducible release bundle → [releasing.md](releasing.md) |
| `scripts/dev.sh help` | Usage |

Every verb delegates to the same Ant targets and guard scripts CI uses, so a
green local `check` means a green pipeline. The script finds `$JAVA_HOME` /
`$ANT_HOME` if set, otherwise the vendored `Portable_Windows` JDK and Ant
(they work under Git Bash on Windows too).

## Build matrix

| Component | Tool | Command | Output | Verified |
| --- | --- | --- | --- | --- |
| Server core + plugins | Ant (`server/build.xml`) | `compile_core`, `compile_plugins` | `server/core.jar`, `server/plugins.jar` (fat jars) | Windows locally, GitHub `ubuntu-latest`, GitLab |
| Desktop client | Ant (`Client_Base/build.xml`) | `compile` | `Client_Base/Open_RSC_Client.jar` (fat jar) | Windows locally, GitHub, GitLab |
| PC launcher | Ant (`PC_Launcher/build.xml`) | `compile` | `PC_Launcher/OpenRSC.jar` | Windows locally, GitHub, GitLab |
| Static site (Pages) | `scripts/build_pages.sh` | — | staged Pages tree | Windows locally, both CIs |
| Release bundle | `scripts/build_release.py` | `scripts/dev.sh release <ver>` | `dist/RuneWake-<ver>/` + `.zip` + SHA-256 | Windows locally (smoke-tested 2026-10-06; 0.1.0/0.1.1 shipped this way) |
| Android client | Gradle | see `Android_Client/` | — | **Unverified** — needs JDK 11+, not exercised by CI |

The desktop client compiles **two source roots together** — `Client_Base/src`
(game) plus `PC_Client/src` (window/sound/Discord shell) — with libraries from
`PC_Client/lib`. There is no standalone `PC_Client` build.

## Active vs inherited/inactive build paths

**Active:** the Ant builds above, `scripts/dev.sh`, `run-client.bat` /
`run-server.bat`, `Start-Linux.sh` / `Start-Windows.cmd` (interactive menus),
`Packaging/build-installer.bat`, `docker-compose.yml` (MariaDB only),
`.github/workflows/ci.yml` and `.gitlab-ci.yml`.

**Inactive or broken (inherited from OpenRSC — do not use):**

- `server/inc/ant/` — launcher scripts with no `lib/`; running them fails with
  `Could not find or load main class org.apache.tools.ant.launch.Launcher`.
  `Client_Base/compile_client.cmd` depends on it and is therefore broken.
- `server/build.gradle` — a parallel Gradle definition of the server build
  that no CI job, script or start command invokes. Ant is authoritative.
- `Deployment_Scripts/unmaintained/` — parked by its own name.

**Deferred by decision:** migrating from Ant to Gradle/Maven. The dependency
graph, fat-jar packaging (`zipgroupfileset`) and Java 8 target must be fully
understood first, and any migration must keep producing identical artefacts
with a temporary fallback (mission rule).

## CI (two pipelines, same commands)

`bash -n` parses every `scripts/*.sh` individually on Linux runners, and each
job runs one workflow command's underlying step:

| GitHub job | GitLab job | Runs |
| --- | --- | --- |
| `guards` | `themeGuard`, `dependencyGuard`, `hostingConfigGuard`, `pagesBuild` | shell syntax, literal/dependency/hosting guards, pages |
| `secret-scan` | `secretScan` | Gitleaks over full history (baseline-filtered) |
| `client-parity` | `themeParityGuard` | client compile + parity |
| `server-boot` | `bootSmoke` | server compile + world boot smoke |
| `build` | `build`, `launcherUpload` | three compiles + `OpenRSC.jar` artifact |

No IntelliJ or other IDE configuration is required by any build step, and no
build step downloads dependencies — `server/lib` and `PC_Client/lib` are
vendored in the repository (`scripts/check_dependencies.sh` fails if one is
missing).

## macOS

`MacOS Getting Started Guide.md` is inherited from OpenRSC and has **not**
been verified in this environment. The Ant builds are plain Java 8 and should
work wherever JDK 8 + Ant exist (`JAVA_HOME`/`ANT_HOME` set, then
`scripts/dev.sh check`), but treat macOS support as intended, not proven.
