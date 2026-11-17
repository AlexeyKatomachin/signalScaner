@echo off
setlocal
cd /d "%~dp0"

call mvn -Dtest=visual.IndicatorVisualTest,reference.ReferenceIndicatorLogicTest,reference.StructureIndicatorLogicTest,reference.IndicatorTimingTest test
if errorlevel 1 (
    echo.
    echo Indicator validation FAILED. Check the Maven output above.
    exit /b 1
)

if exist "target\indicator-tests\index.html" (
    start "" "target\indicator-tests\index.html"
) else (
    echo Report was not generated: target\indicator-tests\index.html
)
