@echo off
cd /d "%~dp0"
echo.
echo ================================================
echo   Bell Launcher - upload changes ^& build APK
echo ================================================
echo.

where git >/dev/null 2>nul
if errorlevel 1 (
    echo [X] Git not found. Install: https://git-scm.com/download/win
    echo.
    pause
    exit /b 1
)

echo Adding files...
git add -A

echo Committing...
git commit -m "update"

echo.
echo Pushing to GitHub...
git push origin main

echo.
if errorlevel 1 (
    echo [X] Push failed. Copy the text above and send it to Claude.
) else (
    echo [OK] Done. Build started.
    echo.
    echo Open: https://github.com/BellCrunel/Android-them-artefact/actions
    echo Wait 4-6 min, then download artifact BellLauncher-debug-apk
)
echo.
pause
