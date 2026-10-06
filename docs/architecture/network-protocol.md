# Network Protocol — RuneWake (OpenRSC fork)

Phase 2 of the modernisation charter. Companion documents:
[server.md](server.md), [client.md](client.md),
[runtime-lifecycle.md](runtime-lifecycle.md),
[client-variants.md](client-variants.md), and
[`../audit/technical-debt.md`](../audit/technical-debt.md).

This documents the wire protocol **as implemented** — it is descriptive,
not a specification claim. Anything marked *custom* is the OpenRSC/RuneWake
extension of the 2001 RSC protocol; anything marked *authentic* belongs to
the historical Jagex protocol family the server also still speaks.

## Ports and transports

| Port (default) | Config key | Transport | Handler stack |
|---|---|---|---|
| **43594** | `server_port` | raw TCP | `RSCMultiPortDecoder(TCP)` → `RSCProtocolDecoder` + `RSCProtocolEncoder` → `RSCConnectionHandler` |
| **43494** | `ws_server_port` | HTTP + WebSocket (TLS optional) | `RSCMultiPortDecoder(WS)` → `HttpServerCodec` → `HttpObjectAggregator` → `HttpRequestHandler` → WebSocket handshake/compression → `WebSocketFrameHandler` → `RSCProtocolDecoder` + `RSCProtocolWebEncoder` → `RSCConnectionHandler` |

- WebSocket support is `want_feature_websockets` (default **on**). The WS
  port doubles as the **HTTP observability surface**: `/status`,
  `/healthz`, `/metrics` (see [server.md](server.md)). After a successful
  WS handshake the `HttpRequestHandler` is removed from that pipeline.
- TLS: if `ssl_server_cert_path` / `ssl_server_key_path` are set, an
  `OptionalSslHandler` fronts the WS stack (context refreshed hourly);
  without them the port is plaintext `ws://` and a WARN is logged at boot.
- `RSCMultiPortDecoder` is a one-shot: on first bytes it removes itself and
  splices in the concrete stack. Its `MIXED` mode (size-based detection) is
  currently unused by either bootstrap — only `TCP` and `WS` are wired.

## Framing

Three framing families coexist; the decoder chooses per connection based on
`ConnectionAttachment.authenticClient`, which is discovered heuristically
before login (see *Client sniffing* below).

### 1. Custom / RSCL-style (the RuneWake desktop client)

- **Client → server:** 2-byte big-endian length, then opcode byte, then
  payload (`Network_Base.newPacket`/`finishPacket` writes exactly this).
- **Server → client:** `RSCProtocolEncoderMain` with
  `authenticClient == null`, `== -1`, or for opcode 19 (server configs):
  `writeShort(capacity)`, `writeByte(opcode)`, payload — a 2-byte length
  followed by the raw opcode.
- No ISAAC: the desktop client's login currently comments out the ISAAC
  seed exchange (`mudclient.login`, `// this.clientStream.seedIsaac(...)`),
  and the custom login branch never installs `attachment.ISAAC`, so
  opcodes travel in the clear after RSA-wrapped credentials.

### 2. Authentic ≥183 — ISAAC opcode encryption

- **Client → server:** 1-byte length (or 2 bytes when length ≥ 160,
  encoded as `256 * first - (40960 - second)`), then an **ISAAC-encoded**
  opcode decoded via `isaacContainer.decodeOpcode(...)`. Quirk faithfully
  mirrored from the authentic client: for 1-byte lengths the **last payload
  byte sits between the length byte and the encoded opcode**, so the
  decoder rotates it back into place before parsing.
- **Server → client:** same layout in `RSCProtocolEncoderMain`
  (`authenticClient >= 183`), including the last-byte-before-opcode quirk
  and the ≥160 two-byte length form.
- ISAAC keys come from the login block (`loginInfo.keys`, 4 ints); after
  `loadingComplete` the server holds an `ISAACContainer` with independent
  inbound/outbound ciphers.

### 3. Authentic 93–182 and legacy <93

Same length/opcode layout as above **without** ISAAC
(`authenticClient >= 93`), and for `>= 14` a simpler `readUnsignedShort`
length prefix. The `>= 175` branch carries
`// TODO: implement the actual non-isaac opcode encryption scheme` — a
known gap on the technical-debt register.

## Client sniffing

Before login the server cannot know which protocol a connection speaks, so
`RSCProtocolDecoder` inspects the first bytes with
`ConnectionAttachment.authenticClient` initially null:

- A 1-byte packet length whose payload starts with opcode 19 is taken as a
  custom client asking for server configs ⇒ `authenticClient = -1`.
- A 2-byte-length packet whose opcode maps (via `ReverseOpcodeLookup`) to
  `LOGIN` with ≥38 readable bytes, or `REGISTER_ACCOUNT` with <80, or an
  opcode >2 that is not `RELOGIN` ⇒ also `-1` (inauthentic OpenRSC client).
- Otherwise the login packet itself decodes the version (below) and stores
  it; subsequent frames branch on `authenticClient >= 183 / >= 175 /
  >= 93 / >= 14 / == -1`.

`ReverseOpcodeLookup` maps only the pre-login opcodes:
`0 → LOGIN`, `2 → REGISTER_ACCOUNT`, `4 → FORGOT_PASSWORD`,
`8 → RECOVERY_ATTEMPT`, `19 → RELOGIN`.

## Login handshake (custom client, version 10010)

The RuneWake desktop client (`mudclient.login`):

1. **opcode 0 (LOGIN)** payload: reconnect flag (byte), `CLIENT_VERSION`
   (int, 10010), username (string), `loginEncryptionVersion` (byte,
   1 = RSA), then:
   - **RSA block A** — 20-char space-padded password, encrypted with the
     server's public key (modulus/exponent delivered earlier in the config
     packet; `Crypto.decryptRSA` server-side)
   - **RSA block B** — `"workdir/jarName"` client-details string (≤63
     chars), logged server-side as *Login details*
   - UID (long), then the **`tellLimitations()` capability block**: max
     animation/item/npc/scenery/prayer/spell/skill/roof/texture/tile/
     boundary IDs, colour counts, quest ID, sound count, mod-sprite flag,
     dialogue options, bank slots, `mapHash`, and finally an Android flag
2. Server replies with a **single status byte** (`loginValidated`);
   `& 0x40` marks failure and closes the channel. The client prints
   `login response:<n>` and decodes the reconnect/tutorial bits.
3. On success `loadingComplete` runs: `attachment.player` set,
   **server configs** sent (`OpcodeOut.SEND_SERVER_CONFIGS` → raw opcode
   19), first-time players get the appearance screen,
   `PlayerLoginTrigger` plugin fires, `ActionSender.sendLogin` follows.
4. The client's `PacketHandler.setServerConfiguration()` populates the
   `S_*` flags, sets `gotInitialConfigs = true`, and calls
   `continueStartGame()` — unblocking `mudclient.run()`'s state-2 wait.

The server's `LoginPacketHandler` also supports the **authentic** login
families in the same switch: version detection from packet length
(30 → 38, 34 → 61, 38 → 74, else the version bytes), the ≥205 RSA+XTEA
block (4 ISAAC keys, password at offset 17, nonces), the 177–204 "initial
ISAAC" block, and the ≥93 hashed-username variants. All funnel into the
same `LoginRequest` → `LoginExecutor` queue (rate-limited per tick).

Registration (opcode 2) and password recovery (opcodes 4/8/19 flows) are
handled in the same class; the client blocks on a one-byte response for
each (`Registration response:N`).

## Opcode space

| Direction | Count | Definition | Numeric mapping |
|---|---|---|---|
| In (client → server) | **93** | `net/rsc/enums/OpcodeIn.java` | `PayloadCustomParser.toOpcodeEnum` switch (raw numbers) for the custom client; `Payload38/69/115/140/177/196/198/199/201/202/203/235Parser` for authentic versions |
| Out (server → client) | **103** | `net/rsc/enums/OpcodeOut.java` | `generators/impl/PayloadCustomGenerator` map (e.g. `SEND_SERVER_CONFIGS → 19`, `SEND_WORLD_INFO → 25`, `SEND_EXPERIENCE → 33`), plus one generator per authentic version |

Dispatch on the way in: `Player.processIncomingPackets()` picks the parser
from `player.isUsing*CompatibleClient()` predicates (38/39 → `Payload38`,
… 233–235 → `Payload235`, else `PayloadCustomParser` for versions
10000–19999), produces an `AbstractStruct`, then
`PayloadProcessorManager.processed()` routes it to one of the **88
`bind(OpcodeIn, Handler)` registrations** → handler classes in
`net/rsc/handlers/` (44 files: `WalkRequest`, `ChatHandler`,
`ItemActionHandler`, `BankHandler`, `PlayerTradeHandler`,
`PlayerDuelHandler`, `CommandHandler`, `SleepHandler`, `Heartbeat`, …).
A parse/process failure unregisters the player with *"Malformed packet!"*.

On the way out, `ActionSender.tryFinalizeAndSendPacket` validates the
struct class against `PayloadValidator`'s opcode→struct table before the
version's generator serialises it.

A few notable handlers: `Heartbeat` is intentionally empty (any packet
refreshes last-seen), `BlackHoleHandler`/`TutorialHandler` implement custom
RuneWake flows, and `RSCPacketFilter` gates everything by PPS/CPS/limits.

## Packet path inside the server

```
Netty IO thread                    Game thread (per 640 ms tick)
─────────────────                  ─────────────────────────────
RSCProtocolDecoder                 Player.processIncomingPackets()
  → Packet (opcode + ByteBuf)   →    → PayloadParser.parse
RSCConnectionHandler.channelRead      → PayloadProcessorManager.processed
  → player.incomingPackets queue      → handler (plugins, world mutations)
                                     Player.processOutgoingPackets()
                                     → channel.writeAndFlush(Packet)
                                       → RSCProtocolEncoder(Main)
```

Incoming packets are only parsed on the game thread (`Player.processTick`),
so handlers run single-threaded per player with no cross-thread world
access. Optional `PcapLoggerService` records both directions when
`want_pcap_logging` is true (it is `true` in `default.conf`).

## HTTP endpoints (WS port)

| Route | Method | Response |
|---|---|---|
| `/healthz` | GET/HEAD | `ok` (text/plain) |
| `/status` | GET/HEAD | JSON server-browser document (`players`, `maxPlayers`, `uptimeSeconds`, …) |
| `/metrics` | GET/HEAD | Prometheus text exposition of the same gauges |
| anything else | GET/HEAD | 404; non-GET/HEAD ⇒ 405 + `Allow` |

All responses carry `Cache-Control: no-store`, are served from
already-public in-memory state, and touch no database. WS **text** frames
are currently echoed uppercased by `WebSocketFrameHandler` (Netty example
leftover — flagged as debt); **binary** frames are forwarded into the RSC
decode path, which is what the webclient uses.

## Hardening and known gaps

- `RSCPacketFilter`: `max_packets_per_second` (100),
  `max_connections_per_second` (20), `max_connections_per_ip` (20), with
  temporary IP bans (`NETWORK_FLOOD_IP_BAN_MINUTES`) written to
  `ipbans.txt`; password-attempt throttling; a hardcoded host-admin
  exemption from IP bans (debt).
- RSA keypair is generated as **512-bit** if `client.pem`/`server.pem` are
  absent (`Crypto.init`) — cryptographically weak by modern standards
  (debt; the private key never leaves the server and `*.pem` is
  gitignored).
- The authentic `>= 175` non-ISAAC opcode-encryption scheme is unimplemented
  (TODO in `RSCProtocolDecoder`).
- Decoder debug output uses `System.out.println` (e.g. buffer-length
  prints) rather than the logger — noisy on hot paths (debt).
- Client trust boundary: everything the client sends is untrusted; server
  authority is the standing rule (see
  [`../security/threat-model.md`](../security/threat-model.md)).
