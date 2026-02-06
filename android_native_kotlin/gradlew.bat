@echo off
setlocal EnableDelayedExpansion

set DIR=%~dp0
set GRADLE_ZIP=%DIR%gradle-wrapper.zip
set GRADLE_URL=https://services.gradle.org/distributions/gradle-8.5-bin.zip

rem Validates Java version
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Java is not installed or not in your PATH.
    exit /b 1
)

echo URL: %GRADLE_URL%

if exist "%GRADLE_ZIP%" goto SkipDownload

echo Downloading Gradle...
powershell -ExecutionPolicy Bypass -File "%DIR%gradle\wrapper\download_gradle.ps1" "%GRADLE_URL%" "%GRADLE_ZIP%"
if %errorlevel% neq 0 (
    echo Download failed.
    exit /b 1
)

:SkipDownload

echo Extracting...
if exist "%DIR%gradle_temp" (
    rmdir /s /q "%DIR%gradle_temp"
)
powershell -Command "Expand-Archive -Path '%GRADLE_ZIP%' -DestinationPath '%DIR%gradle_temp' -Force"

rem Find gradle.bat
set GRADLE_BAT_FOUND=
for /d %%D in ("%DIR%gradle_temp\gradle-*") do (
    if exist "%%D\bin\gradle.bat" (
        set GRADLE_BAT_FOUND=%%D\bin\gradle.bat
    )
)

if defined GRADLE_BAT_FOUND (
    echo Gradle executable found at: !GRADLE_BAT_FOUND!
    call "!GRADLE_BAT_FOUND!" %*
    exit /b %errorlevel%
) else (
    echo Could not find gradle.bat inside the extracted archive.
    exit /b 1
)
