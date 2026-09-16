# RuneWake Visual Test Matrix

Rule: a cell is only marked **passed** when the check was actually run or
inspected in the listed session. Untested items stay **unverified** — an
honest "unverified" is required, not optimistic. This environment has no
display, so interactive/visual checks need a human with the client running.

Environment note: all builds verified via the bundled
`Portable_Windows/zulu8…jdk8` + `apache-ant-1.10.5` with `JAVA_HOME` pointed
at the portable JDK (the system Java is a JRE and cannot run `ant compile`).

## Compile / build matrix

| Target | Command | Status | Session |
|---|---|---|---|
| Client (base + PC sources) | `ant -f Client_Base/build.xml compile` | **passed** | 2026-09-16 (×3: baseline, Theme migration, branding pass) |
| Server core | `ant -f server/build.xml compile_core` | **passed** | 2026-09-16 (baseline + after leak fixes) |
| Server plugins | `ant -f server/build.xml compile_plugins` | **passed** | 2026-09-16 (baseline + after leak fixes) |
| Launcher | `ant -f PC_Launcher/build.xml compile` | unverified this pass | — |

## Resolution matrix (interactive — human required)

Nothing in this table has been visually verified in this environment.
Screens: launcher, login, character creation, in-game (inventory/minimap/
chat/magic/settings/social/stats/quests/clan), bank, shop, trade, duel,
dialogues, Input-X, report abuse.

| Resolution | Auto scale | Notes |
|---|---|---|
| 1024×768 | unverified | |
| 1280×720 | unverified | enforced client minimum is 1280×732 |
| 1366×768 | unverified | |
| 1600×900 | unverified | |
| 1920×1080 | unverified | |
| 1920×1200 | unverified | |
| 2560×1440 | unverified | prior sessions' screenshots came from ~this size |
| 3440×1440 (ultrawide) | unverified | check panel clamping specifically |
| 3840×2160 | unverified | |

## UI-scale settings matrix

Auto / 100 / 125 / 150 / 175 / 200 / 250: **none exposed as a user setting
yet.** Current state: `renderingScalar` (canvas integer-scaling, separate
concern) is exposed in settings; the UI design-space scale (`uiScale`) is
derived automatically from window size only. Adding a discrete UI-scale
selector remains future work (see UI_SCALING_PLAN / ROADMAP 7p notes).

## Per-screen checklist template

For each screen × resolution: clipping / overlap / legibility / hitboxes /
hover states / modal click-through / focus / branding. All currently
**unverified** for the 2026-09-16 pass; the items below are the ones most
worth checking first after this pass's changes:

1. Settings tab (all 4 sub-tabs) at 2 sizes — this pass touched its theme
   colours (flag off ⇒ should be pixel-identical to before).
2. Loading screen + Discord presence — branding strings changed.
3. Android cold start — manifest label changed (reinstall required to see).
4. Bank + inventory drag at ≥2560 wide — flagged higher-risk in ROADMAP 7.
