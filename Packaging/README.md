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

## Known blocker before this is safe to actually distribute

`PC_Launcher`'s `Defaults.java` still points `_GAME_FILES_SERVER` at
`rsc.vet/downloads/` - the *original OpenRSC project's* live file server, not
anything RuneWake controls. Until that's re-pointed at a RuneWake-owned host
(with its own MD5 manifest + client files uploaded there), a real install
would download someone else's game files under the RuneWake name on first
launch. Don't ship `RuneWake-Setup.exe` to real users until that's sorted.

For local testing without touching the network at all, pass `--no-update` /
`-n` as an argument to `RuneWake.exe` (or edit the Run entry in
`installer.iss`) - this now genuinely skips all update/download activity
(previously it only skipped the launcher's own self-update prompt, not the
client/cache download - fixed this session).
