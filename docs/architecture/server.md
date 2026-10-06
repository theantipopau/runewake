# Server Architecture — RuneWake (OpenRSC fork)

Phase 2 of the modernisation charter. Sources of truth are the files cited
inline; line numbers were current at commit `c52efc589` but symbols are used
where possible so the text survives edits. Companion documents:
[client.md](client.md), [network-protocol.md](network-protocol.md),
[runtime-lifecycle.md](runtime-lifecycle.md),
[client-variants.md](client-variants.md), and the technical-debt register
[`../audit/technical-debt.md`](../audit/technical-debt.md).

## Shape of the thing

| | |
|---|---|
| Entry point | `server/src/com/openrsc/server/Server.java` → `main()` |
| Language / runtime | Java 8 (vendored Zulu 8u275 or any JDK 8), Ant build (`server/build.xml`) |
| Networking | Netty 4.1.67 (TCP game port `server_port`, default **43594**; optional WebSocket/HTTP port `ws_server_port`, default **43494**) |
| Persistence | SQLite (zero-setup default) or MySQL/MariaDB behind `GameDatabase` |
| Build outputs | `server/core.jar` (fat: server + merged `server/lib/*.jar`) and `server/plugins.jar` |
| Size | ~642 Java files / ~101.7k lines in `server/src`, plus 471 plugin files / ~124k lines in `server/plugins` |

One JVM process can host several named worlds: `main()` accepts multiple
config files (`-Dconf=a,b` or positional args, each suffixed `.conf`; the
default is `default.conf`) and keeps every `Server` in the static
`serversList`, polling `checkShutdown()` once a second.

## Configuration

`ServerConfiguration` reads each key three ways via `readSetting(env, key,
default)`: **process environment first** (so `DB_HOST`, `DB_USER`, `DB_PASS`,
`DB_NAME`, `DB_TYPE`, `DB_SSL_MODE`, `DB_CONNECT_TIMEOUT` … can be injected
without touching files), then the YAML-like `.conf` file
(`server/default.conf` and friends), then a hard default. Non-empty
environment values are returned verbatim so passwords may contain whitespace.
`server/connections.conf` holds no committed secrets — the DB user/pass lines
are empty by design and are expected to come from the environment
(see [`../security/configuration.md`](../security/configuration.md)).

Notable knobs: `game_tick` (640 ms default; 430 ms on the cabbage/coleslaw
conf files), `want_feature_websockets` (default on), `want_pcap_logging`
(explicitly `true` in `default.conf`), flood limits
`max_packets_per_second` (100), `max_connections_per_second` (20),
`max_connections_per_ip` (20), `max_logins_per_server_per_tick`.

## Construction and start sequence

`new Server(configFile)` builds the component graph but binds nothing:

1. `ServerConfiguration.initConfig`
2. `RSCPacketFilter`, `PluginHandler`, `CombatScriptLoader`, `Constants`
3. Database **type** selection — `MySqlGameDatabase` or `SqliteGameDatabase`
   from `DB_TYPE`; no type ⇒ log + `SystemUtil.exit(1)`
4. `DiscordService` (only if any Discord flag is on), `LoginExecutor`,
   `World`, `GameEventHandler`, `GameStateUpdater`, `MySqlGameLogger`,
   `PcapLoggerService`, `EntityHandler`, `AchievementSystem`,
   `PlayerService`, `I18NService`
5. Three single-thread named executors: `sqlLoggingThreadPool`,
   `sqlThreadPool`, `onlineMonitorThreadPool` (all Guava
   `ListeningExecutorService`s)
6. Message-filter word lists from disk

`start()` then runs the ordered boot (each step logs
`Loading X...` / `X Completed`):

1. Schedule `this` on `scheduledExecutor` — **every 10 ms** (the tick itself
   self-throttles, see below)
2. `database.open()` — failure ⇒ `SystemUtil.exit(1)`. This is why a fresh
   checkout without `server/inc/sqlite/*.db` dies immediately; the tracked
   schema under `server/database/sqlite/` is seeded by
   `scripts/check_boot.sh` / `scripts/build_release.py`
3. DB keep-alive every 10 minutes
4. `JDBCPatchApplier.applyPatches()` — ordered schema patches; failure exits
5. `PidShuffler.init()`, optional prerendered captchas
6. `EntityHandler.load()` (definitions) → `GameStateUpdater.load()` →
   `GameEventHandler.load()` → `CombatScriptLoader.load()` → `World.load()`
   → `PluginHandler.load()`
7. `LoginExecutor.start()`, optional `DiscordService.start()`,
   `GameLogger.start()`, `PcapLogger.start()`, `RSCPacketFilter.load()`
8. `Crypto.init()` — loads `client.pem`/`server.pem`, generating a **512-bit**
   RSA pair if absent (see technical debt: this key size is weak)
9. `maxItemId` from the database
10. Netty bootstrap: boss/worker `NioEventLoopGroup`s for TCP and (if
    enabled) WS; pipelines described in
    [network-protocol.md](network-protocol.md). If WebSockets are on and a
    cert/key path is configured, an `OptionalSslHandler` is added and the SSL
    context is refreshed hourly
11. `StartupTrigger` plugin fires, ports bind, `serversList.put(name, this)`,
    `running = true`, `serverStartedTime = now`

## The game tick

`Server.run()` (the `Runnable` scheduled at 10 ms) does nothing until
`now - lastTickTimestamp >= GAME_TICK`; then, inside one `bench(...)`
measurement:

1. `GameEventHandler.processNonPlayerEvents()`
2. `GameStateUpdater.updateWorld()`
3. `GameStateUpdater.processNpcs()`
4. Every `Player.processTick()` — optionally in shuffled PID order
   (`SHUFFLE_PID_ORDER`), with periodic `PidShuffler.shuffle()`
5. `executePidlessCatching()`, `World.processGlobalMessageQueue()`
6. Internet-reachability monitor (skipped for `MONITOR_IP=localhost`)
7. `player.processLogout()` for all players, then `player.sendUpdates()`
   for all players (state diff → outgoing packets)
8. `doCleanup()` + `GameEventHandler.cleanupEvents()`
9. A debug "vacuum service" loop over NPCs marked `TODO: remove` — runs every
   tick and is on the technical-debt register
10. `monitorTickPerformance()` (warns when a tick overran; skips ticks when
    behind), `dailyShutdownEvent()`, `resetEvent()`, `advanceTicks(1)`,
    `LoginExecutor.resetRequestsThisTick()`, and the per-opcode packet
    timing maps are cleared

`Player.processTick()` (Player.java ~line 2413) is where **incoming packets
enter gameplay**: `processIncomingPackets()` drains the per-player queue,
selects the version-specific `PayloadParser`, converts the raw `Packet` into
an `AbstractStruct`, and hands it to `PayloadProcessorManager.processed()`.
A null/failed parse force-unregisters the player with *"Malformed packet!"*.

Between ticks, if `WANT_CUSTOM_WALK_SPEED` is set, positions and walk-to
actions are advanced on the 10 ms cadence instead of the 640 ms tick.

## Shutdown and restart

- `closeProcess(seconds, message)` broadcasts a box/chat message, then
  `server.shutdown(seconds)` schedules a `ShutdownEvent`.
- `checkShutdown()` (polled from `main()`) calls `stop()` when flagged, and
  `start()` again when `restarting` — this is the in-process restart path.
- `stop()` order: `World.unloadPlayers()` → executor shutdown with 1-minute
  awaits → `LoginExecutor/Discord/GameLogger` stop → `GameUpdater`,
  `GameEventHandler`, `EntityHandler`, `PluginHandler`,
  `CombatScriptLoader`, `PacketFilter`, `PcapLogger` unload →
  `World.unload()` → `database.close()` → Netty groups
  `shutdownGracefully().sync()` → all stat fields reset. A server being
  *restarted* stays in `serversList`; otherwise it is removed.

## Subsystems worth knowing

### Persistence (`server/src/com/openrsc/server/database/`)

`GameDatabase` (abstract; `open()`/`openInternal()`, `close()`, query/build
layers) with `impl/MySqlGameDatabase` and `impl/SqliteGameDatabase`.
`JDBCPatchApplier` applies ordered patches under `database/patches` — checked-in
`core.sql`/`retro.sql` contain destructive `DROP`s and are for **first
install only**, never upgrades (see the modernisation audit). `PlayerService`
loads a player as a set of aggregates (data, skills, equipment, inventory,
bank, presets, social, quests, cache, last spell); `loadPlayerAchievements`
is commented out to match the disabled achievement load in `Server.start()`.
SQL runs on the two single-thread pools, keeping DB I/O off the game thread.

### World (`model/world/`)

`World.load()` initialises clan/party managers, the auction `Market`,
`RegionManager`, `WorldPopulator` (spawns NPCs/objects from definitions),
NPC drop tables, optional `CombatOdyssey`. Terrain comes from
`io/WorldLoader`, which unpacks JAG archives
(`conf/server/data/maps/content4_*`, `content5_*`) sector by sector — members
first, then F2P. Note `World.load()` **catches and logs** its own exceptions;
a half-loaded world does not stop the boot.

### Plugins (`server/plugins/` → `plugins.jar`)

Game content lives outside `core.jar`. `PluginJarLoader.loadJar()` opens
`./plugins.jar` with a `URLClassLoader` and collects every non-inner class;
`PluginHandler.load()` creates a cached thread pool and indexes classes by
the 34 marker interfaces in `plugins/triggers/` (`OpNpcTrigger`,
`TalkNpcTrigger`, `KillNpcTrigger`, `PlayerLoginTrigger`, `CommandTrigger`,
`StartupTrigger`, …). Dispatch is `handlePlugin(triggerType, owner, data)`,
called from ~57 sites across the server; during a reload players are told
*"Plugins are being updated, please wait."* Package layout:

- `authentic/` — OpenRSC's faithful recreation (commands, defaults,
  itemactions, minigames, misc, npcs, quests, skills)
- `custom/` — RuneWake/OpenRSC custom content, same sub-areas
- `retro/` — version-38-era behaviour (itemactions, npcs, tasks)
- `shared/` — constants and models shared by the above

Compile with `ant compile_plugins`; the server must be restarted to pick up
a rebuilt `plugins.jar` (the loader runs at boot, not per request).

### Login pipeline (`LoginExecutor` + `login/`)

`LoginPacketHandler.processLogin` parses the login block (version-specific —
see [network-protocol.md](network-protocol.md)), builds a `LoginRequest`,
and enqueues it. `LoginExecutor` runs logins and saves on separate queues
with a per-tick cap (`MAX_LOGINS_PER_SERVER_PER_TICK`), so authentication
can never stall the game thread. `ValidatedLogin` carries the outcome back
to `loginValidated(response)` (a single status byte is written to the
channel; failures above `RECONNECT_SUCESSFUL` close it), and
`loadingComplete(player)` wires the `ConnectionAttachment`, sends server
configs (`OpcodeOut.SEND_SERVER_CONFIGS` = raw opcode 19), fires
`PlayerLoginTrigger`, and calls `ActionSender.sendLogin`.

### Outbound packets (`net/rsc/ActionSender.java`)

Everything server→client goes through
`ActionSender.tryFinalizeAndSendPacket(OpcodeOut, struct, player)`:
the struct is validated against `PayloadValidator`'s opcode→class table,
the version-appropriate generator
(`generators/impl/PayloadCustomGenerator` for the RuneWake client, or
`Payload38…235Generator` for authentic clients) serialises it into a
`Packet`, and `player.write()` queues it. `Player.processOutgoingPackets()`
flushes the queue to the Netty channel each tick. A `GameNetworkException`
is logged and swallowed — the script that asked for the send continues.

### HTTP surface (`net/HttpRequestHandler.java`)

On the WS/HTTP port only: `/status` (JSON for the server browser),
`/healthz` (`ok`), `/metrics` (Prometheus text). GET/HEAD handled, 405 with
`Allow`, 404 otherwise, `Cache-Control: no-store`, no database access. This
is the surface `scripts/check_boot.sh` asserts and what
`web/server-browser/` queries — see
[`runtime-lifecycle.md`](runtime-lifecycle.md).

### Defensive surface (`net/RSCPacketFilter.java`)

IP ban/mute files (`ipbans.txt`, `ipmutes.txt`), packets-per-second,
connections-per-second and connections-per-IP limits with temporary IP bans
on violation, and password-attempt tracking. It also contains a hardcoded
host-admin exemption (`isHostAdmin` / the "Afman" log lines) — logged as
security debt rather than removed, since removing it changes ban behaviour.

## Invariants

- **Server authority.** The client is untrusted; movement, inventory,
  combat and economy claims are validated server-side (threat model,
  mission rules 9/10).
- **Text-path alpha is ignored** on the *client* (see
  [client.md](client.md#rendering-invariants)); the server does not draw.
- **The game thread owns world state.** Network threads only enqueue
  `Packet`s onto players; SQL only runs on the single-thread pools.
- **`core.jar` is a fat jar** that merges `server/lib/*.jar`; deleting a
  jar without rebuilding does not break a boot, but a fresh build of a
  tree missing a jar does (this is exactly what `bootSmoke` guards).
