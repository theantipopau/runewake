; Inno Setup script for the RuneWake client installer.
; Produces RuneWake-Setup.exe, which installs RuneWake.exe (a thin native
; launcher stub), a bundled JRE (so players don't need Java installed), and
; RuneWakeLauncher.jar (the existing PC_Launcher, which handles update
; checking and starting the actual game client).
;
; Build order (see build-installer.bat):
;   1. Client_Base -> Open_RSC_Client.jar (built by its own ant script)
;   2. PC_Launcher -> OpenRSC.jar, copied here as RuneWakeLauncher.jar
;   3. RuneWakeLauncher\Program.cs -> RuneWake.exe (compiled via csc.exe)
;   4. This script, compiled via ISCC.exe, bundles it all together.

#define AppName "RuneWake"
#define AppExeName "RuneWake.exe"
#define OutputDir "Output"

[Setup]
AppName={#AppName}
AppVersion=1.0
AppPublisher=RuneWake
DefaultDirName={localappdata}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
OutputDir={#OutputDir}
OutputBaseFilename=RuneWake-Setup
SetupIconFile=..\PC_Launcher\runewake.ico
Compression=lzma2
SolidCompression=yes
; Per-user install under %LocalAppData% - no admin rights required, and the
; launcher's auto-updater can freely write into its own install directory.
PrivilegesRequired=lowest
ArchitecturesInstallIn64BitMode=x64compatible

[Files]
Source: "RuneWakeLauncher\RuneWake.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\PC_Launcher\runewake.ico"; DestDir: "{app}"; Flags: ignoreversion
Source: "staging\RuneWakeLauncher.jar"; DestDir: "{app}"; Flags: ignoreversion
Source: "staging\jre\*"; DestDir: "{app}\jre"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExeName}"; IconFilename: "{app}\runewake.ico"
Name: "{group}\Uninstall {#AppName}"; Filename: "{uninstallexe}"
Name: "{userdesktop}\{#AppName}"; Filename: "{app}\{#AppExeName}"; IconFilename: "{app}\runewake.ico"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Create a &desktop shortcut"; GroupDescription: "Additional shortcuts:"

[Run]
Filename: "{app}\{#AppExeName}"; Description: "Launch {#AppName} now"; Flags: nowait postinstall skipifsilent
