# RuneWake releases

RuneWake uses GitHub Releases for downloadable player/operator bundles. The
source repository remains the canonical project history; a release archive is
a separately built distribution artifact.

## 0.1.1

Release tag: [`v0.1.1`](https://github.com/theantipopau/runewake/releases/tag/v0.1.1)

Same clean player/operator bundle shape as 0.1.0, with a server dependency
refresh, a broad client presentation pass, and the free-tier hosting and
observability work.

### What changed

- **Vendored server dependencies moved forward.** Netty 4.1.33 → 4.1.67 (the
  last release of `netty-all` that is still a real uber-jar), Log4j 2.17.0 →
  2.25.2, commons-compress 1.18 → 1.28.0, commons-lang3 3.12.0 → 3.18.0,
  commons-collections4 4.0 → 4.5.0, commons-codec 1.14 → 1.19.0,
  xstream 1.4.18 → 1.4.21, json 20190722 → 20250517,
  guava 30.1.1-jre → 33.4.8-jre, sqlite-jdbc 3.34.0 → 3.50.3.0, and
  disruptor 3.3.11 → 3.4.4. `commons-io-2.20.0.jar` is newly vendored because
  commons-compress 1.27+ requires it at runtime. `server/build.gradle` was
  brought back in step with the vendored filenames, and the unused
  `repo.spring.io` repository was dropped. See
  [`docs/DEPENDENCIES.md`](DEPENDENCIES.md).
- **Discord/SLF4J logging fixed.** The 1.7 SLF4J binder replaces the
  `log4j-slf4j18-impl` 1.8 adapter and the competing `slf4j-nop` alpha, so the
  JDA-bundled SLF4J API now routes to Log4j instead of falling back to NOP.
- **Premium-theme sweep completed for the legacy custom windows.**
  `PointInterface`, `PointsToGpInterface`, `TerritorySignupInterface`,
  `ExperienceConfigInterface`, `QuestGuideInterface` and `LostOnDeathInterface`
  — which draw straight onto the game surface and still used raw colour
  literals — now share new `Theme.legacy*` / `Theme.points*` tokens, as does
  the achievement window. With the premium theme off, every one of them still
  renders its exact inherited literals.
- **World-texture preparation de-duplicated.** The blur / transparency-sentinel
  / 256-colour-quantisation block was byte-identical in `loadTextures` and
  `loadTexturesAuthentic`; it is now one `prepareTexturePalette` helper, so the
  sentinel handling cannot be fixed in only one of the two paths again
  (it previously had to be).
- **Free-tier hosting path.** Environment-first database configuration with
  `DB_SSL_MODE`/`DB_CONNECT_TIMEOUT`, a MariaDB `docker-compose.yml` with
  required secrets and a healthcheck, `deployment/systemd/` units, `Makefile`
  targets, `scripts/backup_mariadb.sh`, and `scripts/check_hosting_config.sh`
  (wired into CI, and into the systemd unit's `ExecStartPre`).
- **Server observability.** `/status`, `/healthz` and `/metrics` on the
  WebSocket port, plus a player-count readout in the server browser.
- **Update reliability.** The launcher, desktop gameupdater and Android updaters
  gained timeouts, staged `.part` downloads with atomic moves, disconnect in
  `finally`, and divide-by-zero guards.
- **Premium login console.** A measured premium login frame, a themed status
  scrim, and a bronze button scheme for the onboarding panels; the
  unreachable third login screen is now null-guarded instead of a latent NPE.

### Verified for this release

- `ant compile_core`, `ant compile_plugins`, the `Client_Base` compile (which
  also compiles `PC_Client/src`) and the `PC_Launcher` compile all succeed on
  the bundled JDK 8.
- The world was booted on the bundled JDK 8 against SQLite with the upgraded
  dependency set. `/healthz` returned `200 ok`, `/status` returned the expected
  JSON, `/metrics` returned Prometheus text, `HEAD` sent headers with no body,
  `POST` returned `405` with `Allow`, and an unknown path returned `404`.
- `scripts/check_dependencies.sh`, `scripts/check_theme_literals.sh`
  (164 baseline pairs, down from 254 before this line of work),
  `scripts/check_theme_parity.sh` (89 checks), `scripts/check_hosting_config.sh`
  and `scripts/build_pages.sh` all pass.

### Rebuild the bundle

From a clean checkout at the `v0.1.1` tag, with the portable Windows JDK/Ant
available:

```sh
python scripts/build_release.py --version 0.1.1
```

The command rebuilds the client, server core/plugins, and launcher before
staging the distribution, and records the source commit it was built from in
`RELEASE.txt` inside the bundle. Build the bundle *after* the last commit for
the release: `RELEASE.txt` is captured from `HEAD` at build time, so building
from a dirty or stale tree produces an archive that names the wrong commit.
It writes `dist/RuneWake-0.1.1/` and `dist/RuneWake-0.1.1.zip`. It does not
commit, tag, push, or publish.

### Still unverified in this release

- **No human visual pass.** There is no display available in the build
  environment, so every client presentation change above — premium panels,
  login console, interface-scale cap, texture blur — is compile- and
  guard-verified only. See
  [`docs/RUNEWAKE_VISUAL_TEST_MATRIX.md`](RUNEWAKE_VISUAL_TEST_MATRIX.md).
- **Discord** was not exercised against a live session. The SLF4J binding is
  correct now, but JDA itself is still the unchanged 4.0.0_55 shade.
- **MySQL/MariaDB** was not exercised against a real server in this
  environment; the boot and observability checks used SQLite.
- **Docker Compose** (and therefore the `hostingConfigGuard`-protected compose
  stack) was not run: no Docker daemon is available here.
- **Android** was not compiled: the Android Gradle Plugin in use requires
  JDK 11+, and only JDK 8 is available.
- The interface-scale cap, the chat-tab strip and the Input-X dialog still have
  the deferred scaling caveats recorded in `UI_SCALING_PLAN.md`.

## 0.1.0

Release tag: [`v0.1.0`](https://github.com/theantipopau/runewake/releases/tag/v0.1.0)

The first packaged RuneWake release is a Windows-friendly player/operator
bundle. It contains:

- the built desktop client and launcher jars;
- the distributable client cache, with per-install state removed;
- the portable JDK 8 and Apache Ant runtime used by the Windows batch files;
- the built server core and plugin jars;
- the server library jars, server configuration, maps, definitions, and
  database schema;
- a newly generated empty `preservation.db` for the default server world;
- the `run-client.bat` and `run-server.bat` convenience entry points; and
- the server-browser documentation and static browser source.

The archive is not a Git checkout and does not contain `.env`, logs, local
player data, Discord webhook values, IDE metadata, or the developer's local
SQLite databases. A private server's own player database should be backed up
and treated as data, not copied from a public release.

### Verify a download

Each archive contains `MANIFEST.sha256`, which lists SHA-256 hashes for every
other file in the bundle. The release page also records the archive's own
SHA-256 hash. On a machine with `sha256sum`:

```sh
sha256sum RuneWake-0.1.0.zip
unzip -t RuneWake-0.1.0.zip
```

Then compare the archive hash with the release page before extracting it.
After extraction, the manifest can be checked from the bundle directory:

```sh
cd RuneWake-0.1.0
sha256sum -c MANIFEST.sha256
```

### Rebuild the bundle

From a clean `develop` checkout with the portable Windows JDK/Ant available:

```sh
python scripts/build_release.py --version 0.1.0
```

The command rebuilds the client, server core/plugins, and launcher before
staging the distribution. It writes `dist/RuneWake-0.1.0/` and
`dist/RuneWake-0.1.0.zip`. It does not commit, tag, push, or publish.

## Release policy

- Release tags use `vMAJOR.MINOR.PATCH`.
- Release notes must state what was built and what remains unverified.
- Native client appearance is not marked verified without a human visual
  pass; see [`docs/RUNEWAKE_VISUAL_TEST_MATRIX.md`](RUNEWAKE_VISUAL_TEST_MATRIX.md).
- The release archive and the source repository are both AGPLv3-licensed;
  preserve the license and historical attribution files when redistributing.
