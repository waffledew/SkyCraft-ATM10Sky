@echo off
setlocal
echo Use this only for the Java error "Invalid argument: connect" in PipeImpl.
echo Close Skyrim, Minecraft, and the affected server first.
set "SERVER_FOLDER="
set /p "SERVER_FOLDER=For the client press Enter; for the server paste its folder path: "
if defined SERVER_FOLDER (
  powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0Enable-Windows-Pipe-Fix.ps1" -ServerDirectory "%SERVER_FOLDER%"
) else (
  powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0Enable-Windows-Pipe-Fix.ps1"
)
set "RESULT=%ERRORLEVEL%"
pause
exit /b %RESULT%
