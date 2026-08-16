Builds `RuneWake-Setup.exe` - a Windows installer for the RuneWake client.

## What this does

- `RuneWakeLauncher/Program.cs` - a thin native launcher stub (compiled with
  `csc.exe`, the C# compiler built into every Windows install - no extra
  tools needed). It just runs `jre\bin\javaw.exe -jar RuneWakeLauncher.jar`
  from wherever it's installed, so double-clicking `RuneWake.exe` behaves
  like a normal Windows app instead of a `.bat`/`.jar`.
- `RuneWakeLauncher.jar` is the existing `PC_Launcher` project (`OpenRSC.jar`),
  which already handles update-checking and downloading/launching the actual
  game client (`Open_RSC_Client.jar`) - nothing new was written for that part.
- The installer bundles a JRE (copied from `Portable_Windows/`) so players
  don't need Java installed separately.
- Installs per-user under `%LocalAppData%\RuneWake` (no admin rights needed),
  with Start Menu + optional Desktop shortcuts and a real uninstaller, via
  Inno Setup.

## Building

Run `build-installer.bat` from this folder. It compiles the launcher jar,
compiles `RuneWake.exe`, stages a JRE, and runs Inno Setup - output lands in
`Output/RuneWake-Setup.exe`. Requires Inno Setup 7 installed at its default
path; everything else (JDK, ant, csc.exe) is already available on a dev
machine that can already build the rest of this repo.

## Update file hosting (resolved 2026-08-16)

`Defaults.java` now points `_GAME_FILES_SERVER` at
`raw.githubusercontent.com/theantipopau/runewake/game-files/` instead of the
upstream OpenRSC project's `rsc.vet`. The `game-files` branch is an orphan
branch (like `gh-pages`) holding `Open_RSC_Client.jar` + `Cache/` + a
top-level `MD5.SUM` manifest, generated with the same `find | md5sum` recipe
`Deployment_Scripts/deploy-openrsc-client.sh` uses upstream, so it matches
`Md5Handler.java`'s expected format exactly (verified against a live launcher
run this session). `_VERSION_UPDATE_URL` (the launcher's own self-update
check) now points at this repo's `develop` branch instead of
`Open-RSC/Core-Framework`'s.

**To publish a new client build**: rebuild `Open_RSC_Client.jar`
(`ant -f Client_Base/build.xml compile`), copy it + `Client_Base/Cache/`
(minus `ip.txt`/`port.txt`/`uid.dat`/`config.txt`/`discord_inuse.txt` - those
are per-install local state, not distributable defaults) into a fresh
checkout of the `game-files` branch, regenerate `MD5.SUM` from that folder
with `find . -type f -not -name "MD5.SUM" -exec md5sum '{}' \; > MD5.SUM`,
commit, and push. Existing installs will pick up the diff automatically next
launch since the launcher only re-downloads files whose MD5 changed.

For local testing without touching the network at all, pass `--no-update` /
`-n` as an argument to `RuneWake.exe` (or edit the Run entry in
`installer.iss`) - this genuinely skips all update/download activity
(previously it only skipped the launcher's own self-update prompt, not the
client/cache download - fixed in an earlier session).
