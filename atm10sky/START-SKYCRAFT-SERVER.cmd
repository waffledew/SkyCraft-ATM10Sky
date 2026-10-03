@echo off
setlocal
title SkyCraft ATM10Sky Server
cd /d "%~dp0"

if not exist "%~dp0startserver.bat" (
    echo ERROR: startserver.bat was not found beside this file.
    echo Run Prepare-Server.ps1 first, then use the copy placed in the server folder.
    echo.
    pause
    exit /b 1
)

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
