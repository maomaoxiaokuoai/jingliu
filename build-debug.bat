@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat :core:test :app:assembleDebug --stacktrace --console=plain
set "RESULT=%ERRORLEVEL%"
if "%RESULT%"=="0" (
  echo APK: app\build\outputs\apk\debug\app-debug.apk
) else (
  echo Build failed. Keep the FIRST exception and its Caused by section for diagnosis.
)
exit /b %RESULT%
