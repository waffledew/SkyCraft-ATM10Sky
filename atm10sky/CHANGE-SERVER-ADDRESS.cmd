@echo off
setlocal
title SkyCraft ATM10Sky - Change Server Address
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Change-Server-Address.ps1"
set "RESULT=%ERRORLEVEL%"
echo.
if not "%RESULT%"=="0" echo The server address was not changed.
pause
exit /b %RESULT%
