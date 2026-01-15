@echo off
echo Checking Java version...

REM Get Java version
for /f "tokens=3" %%i in ('java -version 2^>^&1 ^| findstr "version"') do set JAVA_VERSION=%%i
set JAVA_VERSION=%JAVA_VERSION:"=%

REM Extract major version
for /f "tokens=1 delims=." %%i in ("%JAVA_VERSION%") do set MAJOR_VERSION=%%i

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
java -cp "StockAccounting.jar;lib\*" cz.datesoft.stockAccounting.Main
pause
