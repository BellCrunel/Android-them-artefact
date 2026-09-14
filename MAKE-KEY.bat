@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"
echo.
echo ================================================
echo   Artefact Launcher - create release signing key
echo ================================================
echo.
echo This creates artefact-release.jks so the app is signed with
echo your own permanent key instead of the debug key.
echo Play Protect blocks debug-signed apps.
echo.

if exist "artefact-release.jks" (
    echo [!] artefact-release.jks already exists.
    echo     Delete it first only if you really want a NEW key.
    echo     WARNING: a new key means the app installs as a separate
    echo     app, not as an update. You would lose your settings.
    echo.
    pause
    exit /b 0
)

rem ---------------------------------------------------------------
rem  Find keytool. It ships with every JDK, but is rarely in PATH.
rem  Android Studio bundles its own runtime - that one works fine.
rem ---------------------------------------------------------------
set "KEYTOOL="

where keytool >nul 2>nul
if not errorlevel 1 set "KEYTOOL=keytool"

if not defined KEYTOOL if defined JAVA_HOME call :try "%JAVA_HOME%\bin\keytool.exe"

if not defined KEYTOOL call :try "%ProgramFiles%\Android\Android Studio\jbr\bin\keytool.exe"
if not defined KEYTOOL call :try "%ProgramFiles%\Android\Android Studio\jre\bin\keytool.exe"
if not defined KEYTOOL call :try "%ProgramFiles(x86)%\Android\Android Studio\jbr\bin\keytool.exe"
if not defined KEYTOOL call :try "%LOCALAPPDATA%\Programs\Android Studio\jbr\bin\keytool.exe"
if not defined KEYTOOL call :try "%LOCALAPPDATA%\Programs\Android Studio\jre\bin\keytool.exe"

if not defined KEYTOOL for /d %%D in ("%ProgramFiles%\Java\*") do (
    if not defined KEYTOOL call :try "%%~D\bin\keytool.exe"
)
if not defined KEYTOOL for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\*") do (
    if not defined KEYTOOL call :try "%%~D\bin\keytool.exe"
)
if not defined KEYTOOL for /d %%D in ("%ProgramFiles%\Microsoft\jdk*") do (
    if not defined KEYTOOL call :try "%%~D\bin\keytool.exe"
)
if not defined KEYTOOL for /d %%D in ("%LOCALAPPDATA%\Programs\Eclipse Adoptium\*") do (
    if not defined KEYTOOL call :try "%%~D\bin\keytool.exe"
)

if not defined KEYTOOL (
    echo [X] keytool not found anywhere.
    echo.
    echo     It ships with the JDK. If Android Studio is installed,
    echo     look for a folder like:
    echo       C:\Program Files\Android\Android Studio\jbr\bin
    echo.
    echo     Find keytool.exe, then run this command yourself
    echo     from THIS folder, replacing the path:
    echo.
    echo       "C:\path\to\keytool.exe" -genkeypair -v -keystore artefact-release.jks -alias artefact -keyalg RSA -keysize 4096 -validity 10000
    echo.
    pause
    exit /b 1
)

echo Using: !KEYTOOL!
echo.
echo keytool will now ask for a password.
echo IMPORTANT: type the SAME password you already put into
echo keystore.properties, otherwise signing will fail.
echo The name/company questions can be left empty - just press Enter.
echo.

"!KEYTOOL!" -genkeypair -v -keystore artefact-release.jks -alias artefact -keyalg RSA -keysize 4096 -validity 10000

if not exist "artefact-release.jks" (
    echo.
    echo [X] Key was not created. See the message above.
    pause
    exit /b 1
)

echo.
echo [OK] artefact-release.jks created.
echo.
echo Now build:   gradlew.bat assembleRelease
echo APK:         app\build\outputs\apk\release\app-release.apk
echo.
echo If the file is called app-release-UNSIGNED.apk, the password in
echo keystore.properties does not match the one you just typed.
echo.
pause
exit /b 0

:try
if exist %1 set "KEYTOOL=%~1"
goto :eof
