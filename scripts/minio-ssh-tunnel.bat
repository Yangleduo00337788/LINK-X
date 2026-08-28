@echo off
setlocal EnableExtensions

if /i not "%~1"=="run" (
    start "LinkX MinIO SSH Tunnel" cmd /k "%~f0" run
    exit /b 0
)

cd /d "%~dp0"
title LinkX MinIO SSH Tunnel

set SSH_HOST=103.236.77.188
set SSH_PORT=25820
set SSH_USER=root
set LOCAL_API=19000
set LOCAL_CONSOLE=19001
set REMOTE=127.0.0.1
set SSH_EXE=

if exist "%SystemRoot%\System32\OpenSSH\ssh.exe" (
    set "SSH_EXE=%SystemRoot%\System32\OpenSSH\ssh.exe"
) else (
    where ssh >nul 2>&1
    if not errorlevel 1 set "SSH_EXE=ssh"
)

echo.
echo ========================================
echo  LinkX MinIO SSH Tunnel
echo ========================================
echo  Server: %SSH_USER%@%SSH_HOST%:%SSH_PORT%
echo  Local API:     http://127.0.0.1:%LOCAL_API%
echo  Local Console: http://127.0.0.1:%LOCAL_CONSOLE%
echo ========================================
echo.
echo Keep this window open while developing.
echo Close this window to stop the tunnel.
echo.

if not defined SSH_EXE (
    echo ERROR: ssh not found.
    echo Install: Settings - Apps - Optional features - OpenSSH Client
    goto end
)

netstat -ano | findstr /C:":%LOCAL_API% " | findstr /C:"LISTENING" >nul 2>&1
if not errorlevel 1 (
    echo NOTE: Port %LOCAL_API% is already in use.
    echo Close the old tunnel window and try again.
    echo.
)

start "" "http://127.0.0.1:%LOCAL_CONSOLE%/"

echo Connecting SSH tunnel. Enter server password when prompted...
echo.

"%SSH_EXE%" -p %SSH_PORT% -o ServerAliveInterval=30 -o ServerAliveCountMax=3 -N -L %LOCAL_API%:%REMOTE%:9000 -L %LOCAL_CONSOLE%:%REMOTE%:9001 %SSH_USER%@%SSH_HOST%

set EXIT_CODE=%ERRORLEVEL%
echo.
if %EXIT_CODE% equ 0 (
    echo SSH tunnel closed.
) else (
    echo ERROR: SSH failed with exit code %EXIT_CODE%
    echo Check password, network, or whether ports are already in use.
)

:end
echo.
echo Press any key to close...
pause >nul
endlocal
