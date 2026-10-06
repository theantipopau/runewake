# Runtime Lifecycle — RuneWake (OpenRSC fork)

Phase 2 of the modernisation charter: the end-to-end sequences a build goes
through, from process start to teardown, on both sides of the wire.
Companion documents: [server.md](server.md), [client.md](client.md),
[network-protocol.md](network-protocol.md),
[client-variants.md](client-variants.md), and
[`../audit/technical-debt.md`](../audit/technical-debt.md).

## Server lifecycle

### 1. Process start

```
java -jar core.jar [world.conf ...]        (or scripts/dev.sh run [world])
  └─ Server.main()
       ├─ resolve config files: -Dconf=a,b and/or args, else default.conf
       ├─ for each file: startServer(file)
       │    └─ new Server(file)        component graph, no sockets yet
       │    └─ server.start()          ordered boot (below)
       └─ while serversList not empty: sleep 1 s, checkShutdown() each
```

`start()` boot order (each step logs `Loading X...`/`X Completed`):

1. Schedule `Server.run()` every **10 ms** on the `GameThread` executor
2. `database.open()` — failure ⇒ `SystemUtil.exit(1)` (missing SQLite file
   on a fresh clone is the classic failure; CI seeds it)
3. DB keep-alive task (every 10 min)
4. `JDBCPatchApplier.applyPatches()` — failure exits
5. `PidShuffler.init()`, optional prerendered captchas
6. Definitions: `EntityHandler.load()` → `GameStateUpdater.load()` →
   `GameEventHandler.load()` → `CombatScriptLoader.load()` →
   `World.load()` → `PluginHandler.load()` (opens `./plugins.jar`)
7. Services: `LoginExecutor.start()`, optional `DiscordService.start()`,
   `GameLogger.start()`, `PcapLogger.start()`, `RSCPacketFilter.load()`
8. `Crypto.init()` (RSA key load/generate), `maxItemId` read
9. Netty bootstraps: TCP always (`server_port`), WS + HTTP when
   `want_feature_websockets` (default on) with optional TLS
10. `StartupTrigger` plugin, bind ports, register in `serversList`,
    `running = true`

The boot smoke test (`scripts/check_boot.sh`, CI job `bootSmoke`) asserts
this whole sequence ends with `/healthz → ok`, `/status` JSON,
`/metrics`, and a 404 on an unknown path — then tears it down.

### 2. Steady state — one tick (default 640 ms)

The 10 ms scheduler self-throttles until `GAME_TICK` elapses, then runs one
measured tick:

```
processNonPlayerEvents → updateWorld → processNpcs
  → Player.processTick() per player (optional PID shuffle)
       ├ processIncomingPackets   (parse + dispatch, malformed ⇒ kick)
       ├ executeWalkToActions
       ├ runPlayerEvents
       ├ movePlayer
       └ processMessageQueue
  → executePidlessCatching → global message queue
  → reachability monitor → processLogout (all) → sendUpdates (all)
  → doCleanup → cleanupEvents → NPC debug vacuum loop (TODO: remove)
  → monitorTickPerformance / dailyShutdownEvent / resetEvent
  → advanceTicks(1), reset login limiter, clear per-opcode timings
```

If a tick overruns, `monitorTickPerformance()` logs it; if the process falls
behind, it **skips ticks** rather than queuing them. Between ticks, with
`WANT_CUSTOM_WALK_SPEED`, positions/walk-to actions advance on the 10 ms
cadence.

### 3. Pause / resume / restart

- `closeProcess(seconds, message)` → broadcast → `shutdown(seconds)` →
  a `ShutdownEvent` flips `shuttingDown`.
- The 1 s supervisor in `main()` calls `checkShutdown()`: `stop()`, and
  `start()` again if `restarting` (in-process world restart; the server
  never left `serversList`).
- `stop()` order: unload players → executor shutdown (1-min awaits) →
  stop Login/Discord/GameLogger → unload GameUpdater, GameEventHandler,
  EntityHandler, PluginHandler, CombatScriptLoader, PacketFilter,
  PcapLogger → `World.unload()` → `database.close()` → Netty
  `shutdownGracefully().sync()` → stat reset.
- The last world exiting empties `serversList`; `main()` logs *Exiting
  server process...* and `SystemUtil.exit(0)`.

### 4. Automated lifecycle verification

`scripts/dev.sh check` ends with the boot smoke; CI repeats it
(`server-boot` on GitHub, `bootSmoke` on GitLab). The guard exists because
the failure modes (missing DB, missing merged jar) are invisible until you
actually boot.

## Client lifecycle

### 1. Launch

Two supported entry points:

- **Direct:** `java -jar Client_Base/Open_RSC_Client.jar` →
  `orsc.OpenRSC.main`
- **Launcher:** `java -jar PC_Launcher/OpenRSC.jar` → `launcher.Main` →
  `Launcher.initializeLauncher()` (UI, optional update check against the
  `game-files` MD5 manifest) → spawns the client JVM via
  `Utils/ClientLauncher` (`java -jar` with the chosen cache dir; `--no-update`
  / `-d <dir>` flags)

`OpenRSC.main` sets the anti-DPI system properties **before any AWT class
loads**, reads `clientSettings.conf`, then on the EDT builds the `JFrame`
and `OpenRSC` applet, calls `applet.init()` (creates `mudclient` +
`PacketHandler`), `applet.start()` → `startMainThread()` (a real
`Thread` running `mudclient.run()`), and launches `ScaledWindow`.

### 2. Boot states

```
gameState 1: wait displayable → loadLogo() → startGame() → gameState 2
gameState 2: spin until gotInitialConfigs (server configs packet)
             → gameState 0 → run2()   (the game loop)
```

If the logo or start-game step fails, `closeProgram()` sets
`threadState = -2` and the thread unwinds.

### 3. Login sequence (client side)

1. User submits credentials on the login `Panel`
2. `mudclient.login()` opens a `Network_Socket` to
   `Config.getServerIp()/getServerPort()` (or the code override)
3. Opcode 0 login block (RSA password + details, version 10010,
   capability block) — see [network-protocol.md](network-protocol.md)
4. Blocking `read()` for the status byte; `0x40` ⇒ failure message,
   success ⇒ wait for server configs
5. `PacketHandler.setServerConfiguration()` → `gotInitialConfigs = true`
   → `continueStartGame()` → the state-2 spin releases into `run2()`

Reconnect re-enters `login(..., reconnecting = true)` with opcode 19
(`RELOGIN`); the server's custom branch currently returns early for
`RELOGIN` (noted as debt).

### 4. Steady state — the client loop

`run2()` runs one `update()` per work unit with an adaptive 25–256 ms
sleep, and inside `GameMode.GAME`, `handleGameInput()` →
`checkConnection()` each frame: heartbeat (`opcode 67`) if idle ~5 s,
flush, then read **one** packet per frame into `PacketHandler`. Rendering
is `drawGame(13)` → `clientPort.draw()` → `ScaledWindow.paintComponent`
scales the viewport image to the window.

### 5. Teardown

- Window close: `JFrame.EXIT_ON_CLOSE` ends the process.
- In-game: logout → `closeConnection()` → `stopSoundPlayer()` →
  `closeProgram()` (`threadState = -2`) → the base thread exits and
  `clientBaseThread` is nulled; `ORSCApplet.stop()` joins it.
- A network error in `checkConnection` raises `lostConnection(123)`,
  which drives the client back to the reconnect/login UI rather than
  crashing.

## Server ↔ client handshake summary

```
client                                   server
  │ TCP connect                             │ RSCMultiPortDecoder splices stack
  │ ── opcode 0 login (RSA blocks) ───────► │ sniff ⇒ authenticClient = -1
  │ ◄────────────── status byte ─────────── │ LoginExecutor validates (queued,
  │                                         │  capped per tick)
  │ ◄── opcode 19 SEND_SERVER_CONFIGS ───── │ loadingComplete: attachment.player,
  │                                         │  PlayerLoginTrigger, sendLogin
  │ gotInitialConfigs → run2()             │
  │ ◄── world/inventory/stats updates ──── │ each 640 ms tick
  │ ── heartbeats, actions (88 bindings) ─► │ parsed on the game thread
```

## CI lifecycle (what runs on every push)

GitHub Actions (`.github/workflows/ci.yml`, 5 jobs):
`guards` → `secret-scan` → `client-parity` → `server-boot` → `build`
(+ launcher artifact upload). GitLab (`.gitlab-ci.yml`, 8 jobs) mirrors it
with `themeGuard`, `dependencyGuard`, `hostingConfigGuard`,
`themeParityGuard`, `bootSmoke`, `secretScan`, `build`, `launcherUpload`,
`pagesBuild`. Locally the identical sequence is one command:
`scripts/dev.sh check` (guards → three compiles → 141-assertion parity →
Pages build → boot smoke).

## Known lifecycle gaps

- Restart (`stop()`/`start()`) is exercised only in theory — no test boots,
  restarts, and reboots a world (candidate for Phase 4 characterization
  tests).
- Client visual states (login console frame, HUD theme windows) have no
  automated lifecycle coverage; the
  [visual test matrix](../RUNEWAKE_VISUAL_TEST_MATRIX.md) tracks the manual
  pass that is still outstanding (no display in this environment).
- The Android lifecycle (activity ↔ `ClientPort`) and macOS behaviour are
  unverified here — see [client-variants.md](client-variants.md).
