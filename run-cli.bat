@echo off
REM run-cli.bat -- starts the compiler in classic stdin mode
setlocal
set JAR=%~dp0target\cl-compiler-1.0.0.jar
if not exist "%JAR%" (
    echo [error] %JAR% not found. run build.bat first.
    exit /b 1
)
java -cp "%JAR%" cl.CLCompiler
echo.
pause
endlocal
