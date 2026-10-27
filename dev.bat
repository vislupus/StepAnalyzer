@echo off
REM Windows equivalent of dev.sh. Usage: dev run | build | install | test | analyze FILE.csv [FILE.gpx] | logs | clean
setlocal
cd /d "%~dp0"
set APP_ID=com.vislupus.stepanalyzer
if "%1"=="run" (
  call gradlew.bat :app:installDebug && adb shell am start -n %APP_ID%/.MainActivity
) else if "%1"=="build" (
  call gradlew.bat :app:assembleRelease && echo APK: app\build\outputs\apk\release\app-release.apk
) else if "%1"=="install" (
  adb install -r app\build\outputs\apk\debug\app-debug.apk && adb shell am start -n %APP_ID%/.MainActivity
) else if "%1"=="test" (
  call gradlew.bat -PcoreOnly :core:test
) else if "%1"=="analyze" (
  call gradlew.bat -PcoreOnly -q :core:run --args="%2 %3 %4 %5 %6"
) else if "%1"=="logs" (
  adb logcat -v time *:S StepAnalyzer:V AndroidRuntime:E
) else if "%1"=="clean" (
  call gradlew.bat clean
) else (
  echo Usage: dev run ^| build ^| install ^| test ^| analyze FILE.csv [FILE.gpx] ^| logs ^| clean
)
endlocal
