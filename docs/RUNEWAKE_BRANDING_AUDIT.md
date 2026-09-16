# RuneWake Branding Audit

Scope: case-insensitive scan of player-facing sources (`Client_Base/src`,
`PC_Client`, `PC_Launcher`, `Android_Client`, `Packaging`, `web`,
`Deployment_Scripts`, root metadata) for `RuneScape`, `OpenRSC`, `Open RSC`,
`RSC*`, `rsc.vet`. Per-occurrence classification: **A** player-facing identity,
**B** internal identifier safe to rename, **C** compatibility-sensitive
(retained + documented), **D** historical/legal attribution (preserved).

Last full scan: 2026-09-16 (this pass).

## Changed this pass (Category A - player-facing)

| File | Occurrence | Change |
|---|---|---|
| `PC_Client/src/orsc/ORSCApplet.java` (`drawLoadingNews`) | Loading screen credit `"Powered by Open RSC"` / `"We support open source development."` | Replaced with `"Powered by RuneWake"` / `"A community revival of RSC."` — keeps the credit concept, drops the foreign brand from the player-visible loading screen. |
| `PC_Client/src/orsc/Discord.java` (`lastUpdate` default) | Discord Rich Presence default state `"Open source RSC MMO"` | Replaced with `"RuneWake"` — this string is visible on players' Discord profiles. Activity states (`"Questing"`, `"Banking"`, etc. set from `PacketHandler`) are already neutral and untouched. |
| `Android_Client/Open RSC Android Client/src/main/AndroidManifest.xml` | `android:label="OpenRSC"` | Replaced with `"RuneWake"` — the launcher icon label on Android devices. The manifest `package` attribute and directory name remain untouched (see retained, below). |
| `Client_Base/src/orsc/Config.java` (`CUSTOM_CACHE_DIR`) | Cache dir `~/OpenRSC` (fallback when `CUSTOM_CACHE_DIR_ENABLED`; used by web-client builds) | Retained the path itself (C: changing it would orphan existing web-client caches) but **commented the change** at the site. Listed here for the next deliberate migration. |

Prior passes (already landed, re-verified this scan): window title
(`Config.WINDOW_TITLE = "RuneWake"`), login/branding strings in `mudclient.java`,
`ServerConfiguration`/`default.conf` server names, bank NPC dialogue,
launcher window title (`PC_Launcher Defaults._TITLE`), launcher update host,
server-browser sample JSON.

## Retained — compatibility-sensitive (Category C)

| File/identifier | Why retained |
|---|---|
| `com.openrsc.*` Java package declarations (~190 files) | `Android_Client` build.gradle compiles `Client_Base/src` directly into its source set; `server/plugins` javac-compiles against `core.jar`'s packages; a rename breaks both plus hundreds of imports. No player visibility. Migration = cross-module refactor with build-matrix verification; separate deliberate change. |
| `orsc.OpenRSC` class + `Main-Class: orsc.OpenRSC` manifest attribute (`Client_Base/build.xml`) | Entry-point identifier; the jar filename `Open_RSC_Client.jar` is referenced by `PC_Launcher`'s updater (`ClientUpdater`), the MD5 protocol manifest, and `Packaging/installer.iss`. Renaming requires migrating all three artifacts atomically. Internal name only; player sees "RuneWake" via `WINDOW_TITLE`. |
| `Client_Base/Open_RSC_Client.jar` artifact name | Same as above — updater protocol + installer wiring. |
| `Launcher/ClientSettingsCard.OPENRSC`, `Settings.OPENRSC` (`"openrsc"` key) | Persisted launcher preference keys (`preferredClientCabbage` etc. default to it) and the legacy multi-client picker's label for the OpenRSC client option. Renaming the key loses existing users' saved preferences. |
| `Client_Base/src/orsc/Config.java` third-party client strings (`"RSC Coleslaw"`, `"RSC Uranium"`, `"RSC Cabbage"`) | `OpenRSC.java` switch-matches these against the server-reported config name to select client behaviour; they are other products' identifiers, not RuneWake branding. |
| `PC_Client/src/orsc/ORSCApplet.java` `"../OpenRSC/"` cache path | Directory location used by a specific launch mode; changing it breaks existing installs' caches. |
| `mudclient.java` `classic.runescape.wiki` URLs (`::wiki` command, item/NPC lookup) | Real external community wiki; repointing would break the feature. |
| `OpcodeOut.RUNESCAPE_UPDATED` | Historical RSC protocol opcode name (not user-facing; 6-file touch for zero value). |
| Abuse-report category `"buy/sell a RuneScape account"` (`Constants.java`) | Preserved historical report category, sits beside "Impersonating Jagex Staff". |
| Comment citation URLs (tip.it/runescape, ngrunescape.com) | Research references in dead code/comments. |
| Commented-out debug draws referencing "Open RSC" (`mudclient.java` ~:15419/15449/15489) | Never execute; flagged for deletion in a cleanup pass rather than editing dead code. |

## Retained — historical/legal attribution (Category D)

- `README.md` — upstream OpenRSC foundation, project lineage (wL/saevion
  tooling, RSCDaemon, RSCAngel, RSCRevolution, RSCLegacy), AGPLv3, contributor
  acknowledgements. The README must distinguish RuneWake's current identity
  from these foundations without rewriting history.
- `PC_Client/src/orsc/ScaledWindow.java` — attribution comment: "Code adapted
  from RSCPlus" (provenance of adapted code; required to stay).
- `PC_Launcher` `ClientSettingsCard` third-party entries (`RSC+`, `IdleRSC`,
  `RSCx`) — other products' names in the client picker; they are not
  RuneWake branding and must not be replaced.
- `Deployment_Scripts/deploy-openrsc-client.sh`, `Packaging/README.md`
  references to upstream tooling/recipes — provenance documentation.
- `Commands.md`, `LICENSE`, guide documents — historic upstream content;
  verified no false "OpenRSC authored this" claims introduced by RuneWake.

## Scan method / reproducibility

Package declarations dominate any naive grep; exclude them first:

```bash
grep -rn '"[^"]*\(RuneScape\|OpenRSC\|Open RSC\|RSC\)[^"]*"' \
  --include="*.java" Client_Base/src PC_Client PC_Launcher
```

then manually classify hits (string literal vs identifier vs comment).
`git grep -il "rsc.vet"` should now return only `Packaging/README.md`
(provenance note) — verify after each branding pass.
