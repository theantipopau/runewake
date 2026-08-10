@echo off
REM Simple dedicated server launcher - plain JDK/Ant, no Docker required.
REM Works on a bare Windows server: just clone the repo and double-click this.
REM
REM Usage:
REM   run-server.bat            (uses default.conf)
REM   run-server.bat rsccabbage (uses rsccabbage.conf)
REM   run-server.bat openpk     (uses openpk.conf)
REM See server\*.conf for the full list of available configs.

SET JDK=%~dp0Portable_Windows\zulu8.50.0.51-ca-jdk8.0.275-win_x64
SET ANT=%~dp0Portable_Windows\apache-ant-1.10.5

set JAVA_HOME=%JDK%
set ANT_HOME=%ANT%
set PATH=%JDK%\bin;%PATH%

SET CONF=%1
IF "%CONF%"=="" SET CONF=default

cd /d "%~dp0server"

echo Compiling server (config: %CONF%.conf)...
call "%ANT%\bin\ant.bat" compile_core compile_plugins
if errorlevel 1 (
    echo Build failed.
    pause
    exit /b 1
)

echo Starting server with %CONF%.conf ...
echo (Ctrl+C to stop)
call "%ANT%\bin\ant.bat" runserver -DconfFile=%CONF%

pause
