# Client Variants — RuneWake (OpenRSC fork)

Phase 2 of the modernisation charter: which client trees exist, what each
produces, and what is active vs inherited. Companion documents:
[server.md](server.md), [client.md](client.md),
[network-protocol.md](network-protocol.md),
[runtime-lifecycle.md](runtime-lifecycle.md), and
[`../audit/technical-debt.md`](../audit/technical-debt.md).

## The variant matrix

| Variant | Sources | Build | Status |
|---|---|---|---|
| **Desktop client (canonical)** | `Client_Base/src` + `PC_Client/src` compiled together | Ant `Client_Base/build.xml` → `Open_RSC_Client.jar` (fat) | **Active** — the shipped client; `java -jar` or via launcher |
| **PC launcher** | `PC_Launcher/src` (~4.7k lines) | Ant `PC_Launcher/build.xml` → `OpenRSC.jar` | **Active** — Swing updater/launcher; wrapped by `Packaging/RuneWake.exe` + Inno Setup |
| **Android client** | `Android_Client/` Gradle project compiling `Client_Base/src` via `sourceSets` | Gradle → `openrsc.apk` | **Inactive/unverified here** — needs JDK 11+ and an Android SDK/device (see gaps) |
| **Web client transport** | none checked in; the server's WS port is the seam | — | **Server-side ready, client-side absent** — binary WS frames feed the same RSC decoder; `WebSocketFrameHandler` text echo is example leftover |
| **Authentic RSC clients (38…235)** | external (Jagex-era clients) | external | **Server-supported** — versioned parsers/generators/ISAAC keep them able to log in; not built here |
| **Retro plugin behaviour** | `server/plugins/.../retro/` | `ant compile_plugins` | Server-side variant for era-38 gameplay, not a separate client build |

## 1. Desktop client — `Client_Base` + `PC_Client`

One jar, two trees (this is the detail that trips up new contributors):

- `Client_Base/build.xml` compiles `srcdir="${src}:${pc_client}"` against
  `PC_Client/lib`, then jars everything including `lib/*.jar`.
- `Client_Base/src/orsc/*` — game core: `mudclient` (18.8k-line monolith),
  `PacketHandler`, `Config`, networking (`orsc/net`), buffers, 2D/3D
  graphics, `graphics/gui/Theme` and the `Panel` widget set.
- `Client_Base/src/com/openrsc/*` — shared content: entity handling
  (items/NPCs/scenery defs), `interfaces/*` (27 custom windows:
  AuctionHouse, BankPin, IronMan, SkillGuide, clan/party, …), `data/`.
- `PC_Client/src/orsc/*` — the platform shell (7 files): `OpenRSC.main`
  (DPI overrides, settings, frame creation), `ORSCApplet` (`ClientPort`
  implementation: lifecycle, input, drawing surface), `ScaledWindow`
  (JFrame + scaling algorithms + min 1280×732), `Discord` rich presence,
  `soundPlayer`, `osConfig`, `util/Utils`.

Build: `scripts/dev.sh build-client` (delegates to `ant -f Client_Base/build.xml compile`).
Guards: `check_theme_literals.sh`, `check_theme_parity.sh` (141 assertions)
run against this jar in CI (`client-parity` / `themeParityGuard`).

Tests live in `Client_Base/test/` — today exactly one,
`ThemeParityTest.java`. `scripts/check_theme_parity.sh` wipes and
recompiles the test into the gitignored `test/build/` each run, then
executes it against the freshly built client jar, so a pass always refers
to current sources (test-framework gap noted in technical debt DOC5).

## 2. Launcher — `PC_Launcher`

- `launcher.Main` → `Launcher.initializeLauncher()`; flags `--dir/-d`
  (cache location, default `Cache/`), `--no-update/-n`, `--help/-h`.
- `Gameupdater/`: `ClientUpdater`, `ClientDownloader`, `Md5Handler`,
  `ConfigCreator` — self-update against the **`game-files` branch**
  manifest (MD5 per file; branch exists, last bundle `a27febdf5`,
  2026-08-16 — publish cadence is an open item).
- Produces `OpenRSC.jar`; `Packaging/` holds the Windows `.exe` stub
  (csc.exe) and Inno Setup installer.
- Visual identity is still inherited OpenRSC artwork (open item — needs
  art; see the branding audit).

## 3. Android — `Android_Client/`

- Nested Gradle project: `Android_Client/build.gradle` +
  `Android_Client/Open RSC Android Client/build.gradle`.
- The key line:
  `main.java.srcDirs += ['src/main/java', 'src/main/../../../../Client_Base/src']`
  — **the same `Client_Base/src` the desktop jar uses**, so gameplay code
  is shared, not forked. Platform differences go through `ClientPort`
  (`isAndroid()` branches in `mudclient` for the on-screen keyboard,
  chat-command buttons, cast-last-spell widget, font sizing).
- `namespace 'com.openrsc.client'`, `buildToolsVersion '31.0.0'`,
  `sourceCompatibility 1.8`, old `com.android.support:*:28.0.0`
  dependencies, output `openrsc.apk`.
- **Status: unverified.** Needs JDK 11+ (the repo vendors only JDK 8), an
  Android SDK, and a device/emulator. The nested project also tracks its
  own `.idea/` (and the repo root tracks `.idea/` files) — IDE state in git
  is on the technical-debt register.

## 4. Authentic and retro clients (server-side variants)

The server is deliberately multi-client:

- **Version detection at login** (`LoginPacketHandler`): packet-length
  sniffing (30 → v38, 34 → v61, 38 → v74), version bytes, RSA+XTEA blocks
  for ≥205, ISAAC-era blocks for 177–204, hashed-username logins ≥93.
- **Per-version parsers and generators**: `Payload38/69/115/140/177/196/
  198/199/201/202/203/235{Parser,Generator}` selected from
  `Player.isUsing*CompatibleClient()`; the custom client (versions
  10000–19999, we send 10010) uses `PayloadCustomParser`/`PayloadCustomGenerator`.
- **`ClientLimitations`** caps what the server will send an older client
  (max IDs, supported features, `isAndroidClient`), read from the login
  capability block.
- **Retro plugins**: `server/plugins/.../retro/` supplies era-authentic
  NPC/item/task behaviour when a retro world conf selects it.
- **World conf variants**: `server/{default,preservation,2001scape,
  rsccabbage,rsccoleslaw,uranium,openpk}.conf` each set name, ports and
  tick speed (430 ms on cabbage/coleslaw vs 640 ms default).

## 5. Non-variants (inherited, inactive, or broken)

Listed so nobody mistakes them for live build paths
(details in [`../audit/repository-state.md`](../audit/repository-state.md)
and [technical-debt.md](../audit/technical-debt.md)):

- **`server/inc/ant/`** — scripts-only Ant install with **no `lib/`**
  (`inc/ant/bin/ant` fails with *Could not find or load main class
  org.apache.tools.ant.launch.Launcher*). Therefore
  `Client_Base/compile_client.cmd` and `server/compile_core.cmd` /
  `compile_plugins.cmd` are broken; use `scripts/dev.sh` or the vendored
  `Portable_Windows` Ant instead.
- **`server/build.gradle` + `server/gradlew`** — Gradle mirror of the Ant
  build, unused by any script or CI job (versions kept in step with
  `server/lib` by policy; `dependencyGuard` checks jars, not Gradle).
- **`Android_Client/.../.idea/`, root `.idea/`** — tracked IDE state
  (61 files) that should eventually be untracked.
- **`Backups/.gitsave`** — a lone tracked marker in `Backups/`.
- **MIXED decoder mode** — `RSCMultiPortDecoder.DecoderMode.MIXED` exists
  but neither bootstrap wires it.

## Upgrade/distribution paths

| Path | Mechanism |
|---|---|
| Desktop launcher | MD5 manifest on the `game-files` branch; `--no-update` skips |
| Release bundles | `scripts/dev.sh release <ver>` → `dist/RuneWake-<ver>/` + zip + SHA-256 (see [releasing.md](../development/releasing.md)) |
| Android | APK sideload — no update channel defined |
| Authentic clients | N/A — external clients point at our ports |
| Web | Not shipped; server WS port is ready, no client is |

## Invariants across variants

- One shared gameplay source (`Client_Base/src`) — desktop and Android must
  not diverge; platform differences belong behind `ClientPort`/`isAndroid()`.
- The wire contract is versioned by `Config.CLIENT_VERSION` (10010) and
  the server's parser/generator selection — bumping the client version
  requires a matching server branch.
- Classic-vs-premium rendering rules (byte-identical classic, alpha
  invariants) apply to every variant that renders the HUD.
