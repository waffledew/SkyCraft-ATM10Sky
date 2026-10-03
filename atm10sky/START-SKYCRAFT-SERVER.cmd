@echo off
setlocal EnableExtensions
title SkyCraft ATM10Sky Server
cd /d "%~dp0"

if not exist "%~dp0startserver.bat" (
    echo ERROR: startserver.bat was not found beside this file.
    echo Run Prepare-Server.ps1 first, then use the copy placed in the server folder.
    echo.
    pause
    exit /b 1
)

"%SystemRoot%\System32\netstat.exe" -ano -p tcp | "%SystemRoot%\System32\findstr.exe" /R /C:":25565 .*LISTENING" >nul
if not errorlevel 1 (
    echo SkyCraft ATM10Sky is already running on port 25565.
    echo Do not start a second copy; use the existing server window.
    echo.
    pause
    exit /b 0
)

rem SkyCraft uses Java 21 preview bytecode, which cannot run on Java 22 or newer.
rem Respect a manually configured ATM10_JAVA first, then try common Java 21 locations.
if not defined ATM10_JAVA if exist "%ProgramFiles%\Java\jdk-21\bin\java.exe" set "ATM10_JAVA=%ProgramFiles%\Java\jdk-21\bin\java.exe"
if not defined ATM10_JAVA for /d %%D in ("%ProgramFiles%\Java\jdk-21*") do if exist "%%~fD\bin\java.exe" set "ATM10_JAVA=%%~fD\bin\java.exe"
if not defined ATM10_JAVA for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-21*") do if exist "%%~fD\bin\java.exe" set "ATM10_JAVA=%%~fD\bin\java.exe"
if not defined ATM10_JAVA for /d %%D in ("%ProgramFiles%\Microsoft\jdk-21*") do if exist "%%~fD\bin\java.exe" set "ATM10_JAVA=%%~fD\bin\java.exe"
if not defined ATM10_JAVA for /d %%D in ("%ProgramFiles%\Amazon Corretto\jdk21*") do if exist "%%~fD\bin\java.exe" set "ATM10_JAVA=%%~fD\bin\java.exe"
if not defined ATM10_JAVA set "ATM10_JAVA=java"
if not defined ATM10_RESTART set "ATM10_RESTART=false"

"%ATM10_JAVA%" -XshowSettings:properties -version 2>&1 | "%SystemRoot%\System32\findstr.exe" /C:"java.version = 21." >nul
if errorlevel 1 (
    echo ERROR: SkyCraft ATM10Sky requires Java 21 exactly.
    echo The selected runtime is: %ATM10_JAVA%
    "%ATM10_JAVA%" -version
    echo Install a 64-bit Java 21 JDK, or set ATM10_JAVA to its full java.exe path.
    echo Java 22, 23, and 24 cannot load SkyCraft's Java 21 preview classes.
    echo.
    pause
    exit /b 1
)

echo Using Java 21: %ATM10_JAVA%
echo.
echo Starting the SkyCraft ATM10Sky server...
echo.
echo Wait for both of these messages before joining:
echo   Done
echo   Domain assigned: something.e4mc.link
echo.
echo Send the e4mc.link address to your friends each time the server starts.
echo To save safely, type stop in this window when everyone is finished.
echo.

call "%~dp0startserver.bat"
set "SERVER_EXIT=%ERRORLEVEL%"

echo.
if not "%SERVER_EXIT%"=="0" echo The server stopped with error code %SERVER_EXIT%.
echo This window can now be closed.
pause
exit /b %SERVER_EXIT%
