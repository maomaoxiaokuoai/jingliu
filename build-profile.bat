@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat :app:assembleProfile --console=plain --stacktrace
if errorlevel 1 exit /b %ERRORLEVEL%
echo APK: app\build\outputs\apk\profile\app-profile.apk
