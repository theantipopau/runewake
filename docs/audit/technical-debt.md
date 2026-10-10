# Technical Debt Register — RuneWake (OpenRSC fork)

Phase 2 of the modernisation charter. Collected 2026-10-06 from a full
read of the server, desktop client, launcher, build scripts and CI on
branch `develop` @ `c52efc589`. Companion documents:
[architecture/](../architecture/) (how the system works),
[repository-state.md](repository-state.md) (what is in the tree),
[`../security/`](../security/) (threat model and secret handling).

**Categories** (per charter): immediate defect · security · reliability ·
performance · maintainability · docs · optional · intentional-legacy.
Each item names where it lives so it can be acted on without rediscovery.
Nothing here is a surprise to the guards: several items exist *because* a
guard script or CI job now trips on them.

---

## 1. Immediate defects

| # | Item | Where | Notes |
|---|---|---|---|
| D1 | `server/inc/ant/` is scripts-only: `inc/ant/bin/ant` fails with `Could not find or load main class org.apache.tools.ant.launch.Launcher` (no `lib/`), so `Client_Base/compile_client.cmd` and `server/compile_core.cmd` / `compile_plugins.cmd` are broken | `server/inc/ant/`, `Client_Base/compile_client.cmd` | Verified by running it. Use `scripts/dev.sh` or the vendored `Portable_Windows` Ant. Either delete the dead scripts or vendor Ant properly. |
| D2 | `WebSocketFrameHandler` echoes text frames back uppercased (`request.toUpperCase`) — Netty example leftover, not a protocol | `server/src/com/openrsc/server/net/WebSocketFrameHandler.java:31` | Binary frames (the real webclient path) are fine; text frames should get a real handler or a clean 400/close. |
| D3 | Client reads at most **one** incoming packet per frame (`checkConnection`), with an in-code `// TODO: Inauthentic to loop through packets like this.` | `mudclient.java:1653-1656` | Under load the client's read queue can lag the server's burst (e.g. login world-state flood). |
| D4 | `mudclient.login()` sets `this.m_Zb = 0;` then immediately branches `if (this.m_Zb > 0)` — dead "server full" branch that can never run | `mudclient.java:15155-15164` | Leftover from decompilation; the intended full-server path is unreachable. |
| D5 | Decoder debug prints bypass the logger: `System.out.println("Buffer readable bytes: ...")` on the login-sniff path and an invalid-opcode printf | `RSCProtocolDecoder.java:49,210` | 70 `System.out.println` in `server/src`, 180 in the client; log4j is configured (Log4j 2.25.2). |
| D6 | Registration/recovery flows use blocking single-byte reads (`read()`) on the client while the server may take a tick queue slot — no timeout besides the doubled read-attempt counter | `mudclient.java:13052,13275` | Cosmetic today (fast local), brittle on a loaded host. |
| D7 | `Server.run()`'s debug "vacuum service" loop over all NPCs runs **every tick** with `// TODO: remove this vacuum service. It is for debugging.` | `Server.java:721-736` | See P2 below. |

## 2. Security

| # | Item | Where | Notes |
|---|---|---|---|
| S1 | **RSA keypair generated at 512 bits** if `client.pem`/`server.pem` are absent (`KeyPairGenerator.initialize(512)`) | `net/rsc/Crypto.java:105` | 512-bit RSA is factored cheaply; protects login password blocks. Bump to ≥2048 (authentic clients use the same modulus path, so verify client compatibility first — the modulus is delivered in-band at config time). |
| S2 | `RSCPacketFilter.ipBanHost` has a **hardcoded host-admin exemption** ("Do not IP ban afmans!") | `RSCPacketFilter.java:201-207` | Intentional operator carve-out; should become a config-driven allowlist rather than a name in source. |
| S3 | 18 historical secrets remain in git history (`.env` DB creds until `123eff4d3`, inherited `ca.key`, reCAPTCHA keys in forum dumps, twitch token) | history only; redacted in `.gitleaks.baseline.json` | Rotation is the owner's action — checklist in [`../security/secret-rotation.md`](../security/secret-rotation.md). History rewrite was considered and declined. |
| S4 | Login-block details string (`workdir/jarName`) is logged verbatim: `LOGGER.info("Login details for " + username + ": " + loginDetails)` | `LoginPacketHandler.java:502` | Minor fingerprint/PII in logs; truncate or drop to DEBUG. |
| S5 | The vendored **Zulu 8u275** runtime (late 2020) ships with years of unpatched JDK CVEs | `Portable_Windows/` | Licensed bundle; do NOT remove until a Windows-packaging replacement is tested (see `docs/DEPENDENCIES.md`, `docs/security/configuration.md`). |
| S6 | Websocket port serves HTTP routes on the **same port** as game traffic when `want_feature_websockets: true` (default) | `Server.start()`, `RSCMultiPortDecoder` | By design (see network-protocol.md), but it widens the exposed surface: `/status`, `/metrics`, WS upgrade all reachable wherever the game port is. Operators who don't need it should turn it off; the boot smoke asserts the routes when on. |
| S7 | No automated test asserts the login rate limits (`MAX_LOGINS_PER_SERVER_PER_TICK`, password-attempt throttle) actually bound an attacker | `LoginExecutor`, `RSCPacketFilter` | Phase 4 characterization-test candidate. |

## 3. Reliability

| # | Item | Where | Notes |
|---|---|---|---|
| R1 | `server/inc/sqlite/*.db` are **gitignored**, and `SqliteGameDatabaseConnection.open()` hard-exits when the file is absent → a fresh clone cannot boot without seeding | `server/inc/sqlite/`, `scripts/check_boot.sh`, `scripts/build_release.py` | Mitigated: both the smoke test and the release builder seed from tracked `server/database/sqlite/`. A human running `java -jar core.jar` directly on a fresh clone still hits it — worth a friendlier startup message. |
| R2 | `World.load()` **catches and logs** its own exceptions, so a partially loaded world (no regions, no NPCs) still comes up | `model/world/World.java:427-429` | Server then advertises healthy on `/healthz`. Consider failing fast or surfacing degraded state in `/status`. |
| R3 | Achievement system is half-wired: `Server.start()` has the load commented out and `PlayerService.loadPlayerAchievements` is commented out, but `AchievementSystem` is still constructed | `Server.java:426-428`, `PlayerService.java:61` | Either finish the wiring or delete the object; today it's a standing "why doesn't anything happen" trap. |
| R4 | Restart path (`closeProcess` → `stop()` → `start()`) is exercised only by the daily/hourly scheduled events; **no test boots, restarts, and reboots** a world | `Server.checkShutdown()` | Phase 4 candidate; the boot smoke covers cold start only. |
| R5 | No GitLab runner executes this fork's `.gitlab-ci.yml` (8 jobs written, never run) | `.gitlab-ci.yml` | GitHub Actions (5 jobs) is the live pipeline; the GitLab file is verified by YAML parse + local command equivalence only. |
| R6 | Android build unverified (needs JDK 11+ and an Android SDK); macOS path documented but unexercised | `Android_Client/`, `docs/development/building.md` | Recorded honestly in the build matrix; not claimed green. |
| R7 | Client visual states have **no display-backed verification** here (login console frame, 7 HUD windows, settings tab) | [`../RUNEWAKE_VISUAL_TEST_MATRIX.md`](../RUNEWAKE_VISUAL_TEST_MATRIX.md) | Compile + guard verified only; one expected classic delta (Party-button hover red) noted. |
| R8 | `SystemUtil.exit(1)` appears 23 times in server startup paths — most are correct failsafes, but any late failure kills a running world without a drain | `server/src` | Consider graceful `closeProcess` for post-bind failures. |

## 4. Performance

| # | Item | Where | Notes |
|---|---|---|---|
| P1 | Per-packet parser allocation: `Player.processIncomingPackets` constructs a **new `Payload*Parser` object for every packet** inside the tick | `Player.java:2486-2514` | 13 `else if` branches, each `new ...Parser()` per packet. Make them static singletons; trivial win on a busy world. |
| P2 | NPC debug vacuum loop runs unconditionally every tick over all NPCs (iterating combat events) | `Server.java:721-736` | Marked `TODO: remove`; at minimum gate it behind a debug flag. |
| P3 | `GameStateUpdater` (1,421 lines) carries 15 TODOs and does per-player full scans of known players/objects each tick; incoming/outgoing per-opcode timing `HashMap`s are rebuilt-and-cleared every tick | `GameStateUpdater.java`, `Server.java:756-759` | Acceptable at current population; the first real bottleneck when worlds grow. `updateWorld()` is the documented hot spot. |
| P4 | Client `run2()` busy-spins `update()` up to 100× per burst to fill 256 work units, then sleeps 25–256 ms — adaptive but coarse | `mudclient.java:1027-1110` | Inherited RSC loop; modernising it risks timing-sensitive gameplay (walk/combat cadence), so it is deliberate-legacy until Phase 11 measures it. |
| P5 | `PcapLoggerService` is enabled by default in `default.conf` (`want_pcap_logging: true`) — every packet of every player written to disk | `server/default.conf:38` | Doubles as a privacy/performance knob; default-on is inherited upstream behaviour. Operators should opt out; consider flipping the shipped default. |

## 5. Maintainability

| # | Item | Where | Notes |
|---|---|---|---|
| M1 | `mudclient.java` is **18,848 lines** — game loop, input, UI, login, rendering in one class; `PacketHandler` is 2,948 | `Client_Base/src/orsc/` | The single biggest obstacle to safe changes. Any Phase 6+ UI work should keep carving panels out into `com/openrsc/interfaces/*` (27 windows already live there). |
| M2 | Two build systems per component: Ant is authoritative, `server/build.gradle` + `gradlew` are unused mirrors (versions aligned in the 0.1.1 arc but not machine-enforced) | `server/build.gradle`, `docs/DEPENDENCIES.md` | `dependencyGuard` checks jars on disk, not the Gradle strings. Pick one: either delete Gradle or add a drift check. |
| M3 | Tracked IDE state: **61 `.idea/` files** (root and nested Android project) plus `Backups/.gitsave` | `.idea/`, `Android_Client/.../.idea/` | Should be untracked with a `.gitignore` rule; harmless but noisy in every diff. |
| M4 | `Payload*Parser`/`Payload*Generator` families (13 each) are copy-paste per client version; adding a version means editing ~4 files by hand | `net/rsc/parsers/impl/`, `net/rsc/generators/impl/` | Known cost of multi-version support (client-variants.md §4). A table-driven approach would collapse them. |
| M5 | TODO inventory: **152 in server**, 21 in client, 6 `@Deprecated` — concentrated in `GameStateUpdater` (15), `ActionSender` (9), `LoginPacketHandler` (6), `Bank`/`NpcDrops`/`Inventory` (6/6/5) | `server/src`, `Client_Base/src` | This register supersedes none of them; the top files are where to start. |
| M6 | The desktop jar is built from **two source trees compiled as one** (`Client_Base/src` + `PC_Client/src` via `srcdir="${src}:${pc_client}"`) — undiscoverable without reading `build.xml` | `Client_Base/build.xml` | Now documented in [architecture/client-variants.md](../architecture/client-variants.md); still a footgun for tooling (IDE imports see half the code). |
| M7 | Six upstream commits remain untriaged (`df86308af`…`d8664375e`: commands docs, FA fix, ipban/ipmute by username, grammar, forced SSL refresh) | `upstream/develop` | Small and mostly docs/commands; the SSL-refresh one may interact with `refreshWebsocketSSLContext`. |
| M8 | `runewake_frame_analysis.html` and `.freebuff/` sit untracked at the repo root by policy | `.gitignore` / never-commit | Not debt per se — recorded so future agents don't "clean them up". |

## 6. Documentation

| # | Item | Notes |
|---|---|---|
| DOC1 | **Done this phase:** `docs/architecture/{client,server,network-protocol,runtime-lifecycle,client-variants}.md` and this register | — |
| DOC2 | Charter docs still to write: `docs/audit/{security,ui-ux,gameplay,performance}.md` (security content exists in [`../security/`](../security/) — decide whether to move or cross-link), `docs/design/ui-system.md`, `docs/GAMEPLAY_VISION.md`, `docs/ASSET_REGISTER.md`, `docs/COMPATIBILITY.md`, `docs/KNOWN_ISSUES.md`, `CHANGELOG.md`, `docs/MODERNISATION_REVIEW.md` | Phases 7–14 |
| DOC3 | `docs/ROADMAP.md` charter name vs existing root `ROADMAP.md` (1,885-line session log) — resolve the naming/location collision when writing the charter ROADMAP | Phase 14 |
| DOC4 | Operations docs exist but are provider-neutral templates (`deployment/systemd/`, `server/SIMPLE_HOSTING.md`, `server/CENTRALIZED_DATABASE.md`) — no doc describes a *rehearsed* deploy (because none happened) | Phase 11/14 |

## 7. Optional (nice-to-have, no owner yet)

| # | Item | Notes |
|---|---|---|
| O1 | Webclient: the server's WS transport is ready (binary frames → RSC decoder), but no web client source exists in-tree | `web/` holds only the server browser and static site |
| O2 | `DecoderMode.MIXED` (auto-detect TCP vs WS on one port) is implemented but never wired into either bootstrap | `RSCMultiPortDecoder.java:94` |
| O3 | Side-panel icon scaling: `drawSpriteClipping` draws nothing when sprite something1/2 metadata is zero, and that metadata is unverifiable without a display | deferred in the modernisation audit item 9 |
| O4 | Texture resolution / step 4b `[~]` interiors partial — paused in `UI_SCALING_PLAN.md` | owner decision to resume |
| O5 | Multi-world login server (OpenRSC #2943) — prototype only after one-world operations are proven | modernisation audit priority 6 |
| O6 | Launcher artwork + identity (blocked on art) and publishing the `game-files` branch so installed clients receive builds (branch exists, last bundle 2026-08-16) | modernisation audit priority 5 |

## 8. Intentional legacy (do **not** "fix")

These look like bugs or smells but are load-bearing. Changing them breaks
behaviour that is verified elsewhere.

| # | Item | Why it stays |
|---|---|---|
| L1 | **Text rendering ignores alpha**; sprite rendering honours it | The screen is a `DirectColorModel(32,0xFF0000,0xFF00,0xFF)` with no alpha mask. The inventory-count ladder (`0x00FFFFFF` shifted per 15 items → white/yellow/red) and exp-gain labels (`0x00FF0000`/`0x0000FF00`) carry `alpha=0x00` and would become **invisible** if the text path honoured alpha. Full analysis: modernisation audit item 27. |
| L2 | Sprite dimming literals `0x60FFFFFF` / `0x80FFFFFF` / `0xC0FFFFFF` / `0xCCFFFFFF` | `drawSpriteClipping`'s `colourTransform` reads the alpha byte — these are correct, not typos. |
| L3 | Classic-mode raw colour literals (132 pairs in `scripts/theme_literal_baseline.txt`) | They *are* the byte-identical classic renderer; the guard proves each `Theme` off-path reproduces them. Removing them means changing classic pixels. |
| L4 | Fat `core.jar` merges `server/lib/*.jar`, producing a benign "multiple SLF4J bindings" warning (both binders are the same one) | Making it thin would change the shipped `java -jar core.jar` contract; documented in `docs/DEPENDENCIES.md`. |
| L5 | The client's commented-out ISAAC seed exchange and 512-bit-era protocol quirks mirroring authentic RSC | Authentic-client compatibility depends on the wire format; the custom path simply doesn't use ISAAC (network-protocol.md §Framing). |
| L6 | Single-packet-per-frame client read (D3 above) is *acknowledged* legacy: changing it alters client timing behaviour that current worlds are tuned around | Fix only with a Phase 11 measurement and a gameplay sign-off. |

---

### How this register is used

- **Phase 4**: packet I/O, ISAAC, crypto (incl. the RSA login-block
  round trip) and config loading are now pinned by
  `scripts/check_characterization_tests.sh` (4 plain-main classes under
  `server/test/`, run in CI's `server-boot`/`bootSmoke` jobs). R4 and S7
  remain open characterization targets.
- **Phase 5 (logging)** owns D5 and S4.
- **Phase 11 (performance)** owns P1–P3, P5 with measurements.
- **Phase 14 (release)** owns DOC2, DOC3 and the M7 upstream triage.
- Items are closed by PR reference; delete the row, don't strike it.
