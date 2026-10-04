@echo off
setlocal
title SkyCraft ATM10Sky Update
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0Update-Client.ps1"
set "UPDATE_RESULT=%ERRORLEVEL%"
if not "%UPDATE_RESULT%"=="0" echo Update did not finish. Read the error above.
pause
exit /b %UPDATE_RESULT%
