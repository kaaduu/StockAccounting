@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"
echo Checking Java version...

where java >nul 2>nul
if errorlevel 1 (
    echo ERROR: Java runtime not found in PATH.
    echo Install Java 17 or newer and run again.
    pause
    exit /b 1
)

REM Get Java version
for /f "tokens=3" %%i in ('java -version 2^>^&1 ^| findstr "version"') do set JAVA_VERSION=%%i
set JAVA_VERSION=%JAVA_VERSION:"=%

REM Extract major version
for /f "tokens=1 delims=." %%i in ("%JAVA_VERSION%") do set MAJOR_VERSION=%%i

if "%MAJOR_VERSION%"=="" (
    echo ERROR: Unable to detect Java version.
    java -version
    pause
    exit /b 1
)

if %MAJOR_VERSION% lss 17 (
    echo ERROR: Java 17 or higher is required to run StockAccounting.
    echo Current Java version: %JAVA_VERSION%
    echo.
    echo Please install Java 17 or higher:
    echo   Download from: https://adoptium.net/temurin/releases/
    echo   Or install via package manager if available
    echo.
    echo After installation, run this batch file again.
    pause
    exit /b 1
)

echo Java version check passed. Starting StockAccounting...

REM Prefer Gradle-style dist layout (dist\lib\StockAccounting.jar)
if exist "dist\lib\StockAccounting.jar" (
    echo Using JAR from dist\lib\
    java -cp "dist\lib\*" cz.datesoft.stockAccounting.Main
) else (
    REM Legacy layout
    if exist "StockAccounting.jar" (
        java -cp "StockAccounting.jar;lib\*" cz.datesoft.stockAccounting.Main
    ) else (
        echo ERROR: StockAccounting.jar was not found.
        echo Run build first, then try again.
        pause
        exit /b 1
    )
)
pause
