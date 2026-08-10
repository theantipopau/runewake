@echo off
REM Builds RuneWake-Setup.exe end to end: compiles the launcher jar, the
REM native RuneWake.exe stub, stages a bundled JRE, then runs Inno Setup.
REM Does NOT touch the network - this only packages what's already local.
setlocal

set ROOT=%~dp0..
set JDK=%ROOT%\Portable_Windows\zulu8.50.0.51-ca-jdk8.0.275-win_x64
set ANT=%ROOT%\Portable_Windows\apache-ant-1.10.5
set CSC=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe
set ISCC="C:\Program Files\Inno Setup 7\ISCC.exe"

set JAVA_HOME=%JDK%
set PATH=%JDK%\bin;%PATH%

echo === Building RuneWakeLauncher.jar (PC_Launcher) ===
call "%ANT%\bin\ant.bat" -f "%ROOT%\PC_Launcher\build.xml" compile
if errorlevel 1 goto :error

echo === Compiling RuneWake.exe (native launcher stub) ===
"%CSC%" /nologo /target:winexe /out:"%~dp0RuneWakeLauncher\RuneWake.exe" /win32icon:"%ROOT%\PC_Launcher\runewake.ico" /reference:System.Windows.Forms.dll /reference:System.dll "%~dp0RuneWakeLauncher\Program.cs"
if errorlevel 1 goto :error

echo === Staging files ===
if exist "%~dp0staging" rmdir /s /q "%~dp0staging"
mkdir "%~dp0staging"
copy /y "%ROOT%\PC_Launcher\OpenRSC.jar" "%~dp0staging\RuneWakeLauncher.jar" >nul
xcopy /e /i /q "%JDK%\jre" "%~dp0staging\jre" >nul

echo === Running Inno Setup ===
%ISCC% "%~dp0installer.iss"
if errorlevel 1 goto :error

echo.
echo Build succeeded: %~dp0Output\RuneWake-Setup.exe
goto :eof

:error
echo.
echo Build FAILED.
exit /b 1
