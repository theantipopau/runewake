# Client Architecture — RuneWake (OpenRSC fork)

Phase 2 of the modernisation charter. Companion documents:
[server.md](server.md), [network-protocol.md](network-protocol.md),
[runtime-lifecycle.md](runtime-lifecycle.md),
[client-variants.md](client-variants.md), and
[`../audit/technical-debt.md`](../audit/technical-debt.md).
UI scaling rules live in [`../../UI_SCALING_PLAN.md`](../../UI_SCALING_PLAN.md);
visual verification status in
[`../RUNEWAKE_VISUAL_TEST_MATRIX.md`](../RUNEWAKE_VISUAL_TEST_MATRIX.md).

## Canonical desktop build

The desktop client is **two source trees compiled as one**:

- `Client_Base/src` — the game itself (`orsc.*` ~68 files/41.6k lines, plus
  `com/openrsc/*` ~49 files/20.9k lines: entity handling, interfaces, data)
- `PC_Client/src` — the desktop shell (7 files: `OpenRSC`, `ORSCApplet`,
  `ScaledWindow`, `Discord`, `osConfig`, `soundPlayer`, `util/Utils`)

`Client_Base/build.xml` compiles `srcdir="${src}:${pc_client}"` against
`PC_Client/lib` and packages `Client_Base/Open_RSC_Client.jar` (a fat jar —
it merges `lib/*.jar`). The launcher runs it with `java -jar`. The one test
in the repo, `Client_Base/test/orsc/graphics/gui/ThemeParityTest.java`, is
compiled and executed by `scripts/check_theme_parity.sh` (141 assertions).

## Process start-up

`orsc.OpenRSC.main` (`PC_Client/src/orsc/OpenRSC.java`):

1. **OS-scaling overrides first, before anything else** — on non-macOS sets
   `sun.java2d.uiScale.enabled=false`, `sun.java2d.uiScale=1`,
   `sun.java2d.dpiaware=true`, and `GDK_SCALE=1` off-Windows; macOS instead
   gets `apple.awt.application.appearance=system`. The client does its own
   scaling, so OS-level DPI scaling must be off.
2. Reads `./clientSettings.conf` if present: `minimap_zoom`,
   `scaling_type`, `scaling_scalar`, `ui_scale_cap`. Invalid values fall
   back safely (Auto/100 %) — a bad setting must never prevent startup.
3. `SwingUtilities.invokeLater(createAndShowGUI)`:
   - `JFrame` titled `Config.WINDOW_TITLE`, min/preferred size
     512 × 346 (512 × 334 + 12 for the skip-tutorial line), icon from
     `Utils.getImage("icon.png")`
   - `applet = new OpenRSC()` (an `ORSCApplet`), embedded in the frame
   - `applet.init()` → `new mudclient(this)` + `new PacketHandler(mudclient)`
   - `applet.start()` → `startMainThread()` → `new Thread(this).start()`
     (the client-base thread running `mudclient.run()`)
   - `scaledWindow.launchScaledWindow()` and `resizeMudclient(512, 346)`

`ORSCApplet` (`PC_Client`) implements `ClientPort` — the seam between the
portable game code and the platform (draw, keyboard focus, sound, resize).
`stop()` joins `clientBaseThread`.

## The client game loop

`mudclient.run()` (line ~970) is a state machine on `gameState`:

- **state 1** → wait for the window to be displayable, `loadLogo()`,
  `startGame()`, → state 2
- **state 2** → spin until `gotInitialConfigs` (set by `PacketHandler`
  when the server-configs packet arrives), then `gameState = 0` and
  `run2()`
- `closeProgram()` sets `threadState = -2` and unwinds; `threadState > 0`
  counts down to a clean exit

`run2()` (~line 1027) is the classic RSC adaptive loop: per iteration it
computes a sleep of 25–256 ms from a rolling frame-time history
(`m_F[10]`, `sleepModifier`), then calls `update()` repeatedly until 256
"work units" accumulate (each `update()` is one simulation step, capped at
100 per burst), then handles resize/`reposition()` and rendering. It is a
fixed-work, variable-sleep loop rather than a fixed-timestep engine.

`update()` (~line 17843): frame counter, mode-specific input
(`handleLoginScreenInput` in `GameMode.LOGIN`, `handleGameInput` in
`GameMode.GAME`), camera drift, message-tab activity timers, then rendering
via `drawGame(13)` (~line 4969), which branches on death screen, appearance
panel, sleep, tutorial, etc., and ends with `clientPort.draw()`.

### Network pump

`handleGameInput()` calls `checkConnection()` every frame
(~line 1632):

- sends a keep-alive (`newPacket(67)` heartbeat) if nothing has been
  written for ~5 s
- `flush(0, true)` — one `IOException` ⇒ `lostConnection(123)`
- reads **one** incoming packet per frame via
  `readIncomingPacket(...)` then `packetHandler.handlePacket(opcode, len)`
  (a `TODO` in-code notes the single-packet-per-frame loop is inauthentic)
- re-asserts combat style if no style packet for 1 s

`PacketHandler` (~2.9k lines) is one big opcode switch split across
`handlePacket1`/`handlePacket2`, with an `incomingOpcodeMap` used for debug
naming. Server configs land in `setServerConfiguration()` (~30 `S_*` flags),
which flips `gotInitialConfigs` and calls `continueStartGame()`.

### Login

`mudclient.login()` (~line 15152) and the register/recover paths open a
fresh `Network_Socket` from `Config.SERVER_IP/SERVER_PORT` (code override)
or the cached `Config.getServerIp()/getServerPort()`, then send opcode 0
(login): reconnect byte, `CLIENT_VERSION` (10010), username, encryption
version byte (1 = RSA), an RSA-blocked 20-char padded password, a second
RSA-blocked `workdir/jar` details string, a UID long, and the
`tellLimitations()` capability block (max IDs, map hash, Android flag). It
then blocks on `read()` for the single status byte. RSA modulus/exponent
arrive from the server at config time (`PacketHandler` reads them into
`MiscFunctions.RSA_*`).

## Networking classes

| Class | Role |
|---|---|
| `orsc/net/Network_Base` | framing: `newPacket(opcode)` reserves 2 bytes, `finishPacket()` back-patches the big-endian length, `flush()` hands bytes to the socket; incoming side is 2-byte length + payload, `readIncomingPacket` returns 0 until a whole frame arrives |
| `orsc/net/Network_Socket` | `Socket` + dedicated writer thread (5000-byte ring `writeBuffer`, wait/notify), `read`/`available` pass-through, `m_d` read-attempt timeout doubling |
| `orsc/net/Isaac.java` | ISAAC stream cipher (present; the desktop client's login currently comments out the ISAAC seed exchange — see [network-protocol.md](network-protocol.md)) |
| `orsc/net/Opcodes.java` | client-side opcode name table |
| `orsc/buffers/RSBuffer*` | serialisation primitives (`RSBuffer_Bits` for packets) |

The client sends raw opcode numbers with a 2-byte length prefix — the
"inauthentic"/RSCL-style framing the server's
`RSCProtocolDecoder`/`RSCProtocolEncoderMain` detect and mirror.

## Rendering pipeline

```
Scene.java (orsc/graphics/three, ~3.1k lines)   software 3D rasterizer — world
        ↓
GraphicsController (orsc/graphics/two, ~3.3k)   2D overlay: sprites, text, tabs
        ↓ (per-frame uiScale sync)
offscreen viewport image (512×346 design space, ui() helpers)
        ↓
ScaledWindow.paintComponent (PC_Client)          scales viewportImage by
                                                renderingScalar: integer
                                                nearest-neighbour or
                                                interpolated (AffineTransformOp)
```

- **UI scale**: design-space coordinates via `ui()` helpers; input
  coordinates are transformed by the same factor; `ui_scale_cap`
  (Auto/100–250 %) only ever shrinks the auto-derived scale so panels fit.
  `ScaledWindow` enforces a minimum window of 1280 × 732.
- **Font scaling**: `sqrt(uiScale)` dampened fractional glyph scaling with
  bilinear AA (better than the plan's original Option A).

### Rendering invariants (do not "fix")

1. **Sprite path honours alpha; text path ignores it.**
   `drawSpriteClipping`'s `colourTransform` reads the alpha byte
   (`opacity = colourTransform >> 24 & 0xFF`), so the dimming literals
   `0x60FFFFFF` / `0x80FFFFFF` / `0xC0FFFFFF` / `0xCCFFFFFF` in
   `CustomBankInterface` and `mudclient` are correct.
   `plotLetter` writes the colour raw into a buffer reaching
   `DirectColorModel(32, 0xFF0000, 0xFF00, 0xFF)` — **no alpha mask**. Two
   call sites depend on this: the inventory-count ladder
   `((0x00FFFFFF << (count / 15) * 8) & 0x00FFFFFF)` in `mudclient`
   (white/yellow/red — all carry alpha 0x00 and would be invisible if
   alpha were honoured) and the exp-gain labels (`0x00FF0000` /
   `0x0000FF00`). Never add alpha to the text renderer.
2. **Classic mode is byte-identical.** Every `Theme` token's off-path value
   reproduces the inherited literal exactly; `scripts/check_theme_literals.sh`
   (baseline: `scripts/theme_literal_baseline.txt`, 132 pairs) and
   `scripts/check_theme_parity.sh` (141 assertions) enforce both halves.

## Theming

`orsc/graphics/gui/Theme.java` (~1.3k lines, ~192 public accessors) owns
every custom-window colour; windows draw through `Theme.*` families
(`bankPin*`, `legacy*`, `hud*`, `socialGui*`, `minimap*`, `ironman*`,
`skillGuide*`, …) behind feature flags (`C_PREMIUM_THEME`, `C_CUSTOM_UI`).
Raw draw-layer literals outside `Theme` are guarded down to a reviewed
baseline; classic mode returns the inherited values.

## Key files

| File | Lines (approx) | Role |
|---|---|---|
| `Client_Base/src/orsc/mudclient.java` | 18,848 | game loop, input, UI, login, rendering — the monolith (see technical debt) |
| `Client_Base/src/orsc/PacketHandler.java` | 2,948 | inbound opcode dispatch |
| `Client_Base/src/orsc/graphics/two/GraphicsController.java` | 3,250 | 2D drawing |
| `Client_Base/src/orsc/graphics/three/Scene.java` | 3,081 | 3D world rasterizer |
| `Client_Base/src/orsc/graphics/gui/Theme.java` | 1,328 | colour tokens |
| `Client_Base/src/orsc/Config.java` | — | `CLIENT_VERSION = 10010`, server name/IP/port, `S_*` server-sent flags |
| `PC_Client/src/orsc/ScaledWindow.java` | — | window, scaling algorithms, `paintComponent` |
| `PC_Client/src/orsc/OpenRSC.java` | — | `main()`, DPI overrides, settings load |

## Platform seams

`isAndroid()` checks and `ClientPort` calls are peppered through
`mudclient` (keyboard open/close, on-screen overlays, font size); the
Android build compiles `Client_Base/src` directly into its own source set —
see [client-variants.md](client-variants.md).
