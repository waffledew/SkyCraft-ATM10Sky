@echo off
setlocal
title SkyCraft ATM10Sky Installer
echo.
echo  SkyCraft ATM10Sky friend installer
echo  ------------------------------------
echo  This will create the hidden ATM10Sky profile used by Skyrim.
echo.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0Install-Client.ps1"
set "INSTALL_RESULT=%ERRORLEVEL%"
echo.
if not "%INSTALL_RESULT%"=="0" (
    echo Installation did not finish. Read the error above.
    echo Nothing in your original CurseForge instance was deleted.
) else (
    echo Installation finished successfully.
    echo Start Skyrim through SKSE. Minecraft will run hidden behind Skyrim.
)
echo.
pause
exit /b %INSTALL_RESULT%
