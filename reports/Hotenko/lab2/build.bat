@echo off
echo Start Configuring UDP project...
cmake -S . -B build -G "MinGW Makefiles"
if %errorlevel% neq 0 (
    echo Configuration failed!
    pause
    exit /b %errorlevel%
)
echo Start Building...
cmake --build build
if %errorlevel% neq 0 (
    echo Build failed!
    pause
    exit /b %errorlevel%
)
echo Building has been ended!
pause