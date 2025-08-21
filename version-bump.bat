@echo off
setlocal enabledelayedexpansion

echo ========================================
echo    VelocityEssentials Version Manager
echo ========================================
echo.

REM Get current version using Maven
for /f "tokens=*" %%i in ('mvn help:evaluate -Dexpression^=project.version -q -DforceStdout') do set CURRENT_VERSION=%%i

REM Fallback if Maven method fails
if "%CURRENT_VERSION%"=="" (
    echo Maven version detection failed, trying direct parsing...
    REM Try PowerShell method
    for /f "tokens=*" %%i in ('powershell -Command "Select-String -Path pom.xml -Pattern '<version>(.*?)</version>' | Select-Object -First 1 | ForEach-Object { $_.Matches[0].Groups[1].Value }"') do set CURRENT_VERSION=%%i
)

REM Final fallback - manual entry
if "%CURRENT_VERSION%"=="" (
    echo Could not detect version automatically.
    set /p CURRENT_VERSION="Please enter current version manually (e.g., 1.3.0): "
)

echo Current version: %CURRENT_VERSION%
echo.

REM Parse current version
for /f "tokens=1,2,3 delims=." %%a in ("%CURRENT_VERSION%") do (
    set MAJOR=%%a
    set MINOR=%%b
    set PATCH=%%c
)

REM Handle missing version parts
if "%PATCH%"=="" set PATCH=0
if "%MINOR%"=="" set MINOR=0
if "%MAJOR%"=="" set MAJOR=1

REM Calculate next versions
set /a NEXT_PATCH=%PATCH%+1
set /a NEXT_MINOR=%MINOR%+1
set /a NEXT_MAJOR=%MAJOR%+1

echo Available version options:
echo   1) Patch Release: %MAJOR%.%MINOR%.%NEXT_PATCH%  (Bug fixes)
echo   2) Minor Release: %MAJOR%.%NEXT_MINOR%.0        (New features)
echo   3) Major Release: %NEXT_MAJOR%.0.0               (Breaking changes)
echo   4) Custom Version
echo   5) Keep current version and build
echo   0) Cancel
echo.

set /p CHOICE="Select option (0-5): "

if "%CHOICE%"=="0" (
    echo Build cancelled.
    exit /b 0
)

if "%CHOICE%"=="1" set NEW_VERSION=%MAJOR%.%MINOR%.%NEXT_PATCH%
if "%CHOICE%"=="2" set NEW_VERSION=%MAJOR%.%NEXT_MINOR%.0
if "%CHOICE%"=="3" set NEW_VERSION=%NEXT_MAJOR%.0.0
if "%CHOICE%"=="4" (
    set /p NEW_VERSION="Enter custom version (e.g., 1.4.0): "
)
if "%CHOICE%"=="5" (
    echo Keeping version %CURRENT_VERSION%
    set NEW_VERSION=%CURRENT_VERSION%
    goto :build
)

if not defined NEW_VERSION (
    echo Invalid choice!
    exit /b 1
)

echo.
echo Updating to version %NEW_VERSION%...

REM Update all pom.xml files using Maven versions plugin
echo Updating main pom.xml...
call mvn versions:set -DnewVersion=%NEW_VERSION% -DgenerateBackupPoms=false -q

echo Updating backend pom.xml...
cd backend
call mvn versions:set -DnewVersion=%NEW_VERSION% -DgenerateBackupPoms=false -q
cd ..

echo.
echo ========================================
echo Version updated to %NEW_VERSION%
echo ========================================

:build
echo.
echo Building VelocityEssentials v%NEW_VERSION%...
echo.

REM Build main plugin
echo Building Velocity plugin...
call mvn clean package

REM Build backend
echo Building Paper backend...
cd backend
call mvn clean package
cd ..

REM Copy JARs to output
if not exist output mkdir output
del output\*.jar 2>nul
copy target\velocityessentials-%NEW_VERSION%.jar output\ >nul 2>&1
copy backend\target\velocityessentials-backend-%NEW_VERSION%.jar output\ >nul 2>&1

echo.
echo ========================================
echo Build complete! Version: %NEW_VERSION%
echo ========================================
echo JARs are in the output\ directory:
dir output\*.jar /b
echo.

REM Optional: Create git tag
set /p CREATE_TAG="Create git tag for v%NEW_VERSION%? (y/n): "
if /i "%CREATE_TAG%"=="y" (
    git add -A
    git commit -m "Release version %NEW_VERSION%"
    git tag -a v%NEW_VERSION% -m "Version %NEW_VERSION%"
    echo Git tag v%NEW_VERSION% created!
    echo Don't forget to push: git push origin v%NEW_VERSION%
)

pause