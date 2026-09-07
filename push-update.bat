@echo off
chcp 65001 >nul 2>nul
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0push-update.ps1"
echo.
pause
