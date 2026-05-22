@echo off
REM run.bat -- launches the JavaFX GUI playground
setlocal
set JAR=%~dp0target\cl-compiler-1.0.0.jar
if not exist "%JAR%" (
    echo [error] %JAR% not found. run build.bat first.
    exit /b 1
)
javaw -jar "%JAR%"
endlocal
