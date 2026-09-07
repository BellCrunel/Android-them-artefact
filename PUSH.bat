@echo off
cd /d "%~dp0"
echo.
echo ================================================
echo   Bell Launcher - upload changes and build
echo ================================================
echo.

where git >/dev/null 2>nul
if errorlevel 1 (
    echo [X] Git not found. Install: https://git-scm.com/download/win
    echo.
    pause
    exit /b 1
)

if not exist ".github\workflows" mkdir ".github\workflows"
if exist "ci-smoke-workflow.yml" (
    move /y "ci-smoke-workflow.yml" ".github\workflows\smoke.yml" >nul
    echo [OK] emulator smoke-test workflow installed
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
    echo [OK] Done. Builds started.
    echo.
    echo Open: https://github.com/BellCrunel/Android-them-artefact/actions
)
echo.
pause
