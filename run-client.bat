@echo off
REM Rebuilds and launches the client, starting the local game server first
REM (the client will crash with "Connection refused" on startup otherwise).
SET JDK=%~dp0Portable_Windows\zulu8.50.0.51-ca-jdk8.0.275-win_x64
SET ANT=%~dp0Portable_Windows\apache-ant-1.10.5

set JAVA_HOME=%JDK%
set ANT_HOME=%ANT%
set PATH=%JDK%\bin;%PATH%

SET /P SERVERPORT=<"%~dp0Client_Base\Cache\port.txt"

echo Starting server...
call START /min "Server" "%ANT%\bin\ant.bat" -f "%~dp0server\build.xml" runserver

:wait_for_server
echo Waiting for server to be ready on port %SERVERPORT%...
ping localhost -n 3 >NUL
netstat -an | find ":%SERVERPORT%" | find "LISTENING" >NUL
if errorlevel 1 (
    goto wait_for_server
)
echo Server is ready.

cd /d "%~dp0Client_Base"
call "%ANT%\bin\ant.bat" compile
if errorlevel 1 (
    echo Build failed.
    pause
    exit /b 1
)

"%JDK%\bin\java" -Xms312m -jar Open_RSC_Client.jar
