@echo off
REM ---------------------------------------------------------------
REM build.bat -- builds the CL Compiler via Maven
REM
REM   - generates the parser/lexer/AST from CL.jjt (javacc-maven-plugin)
REM   - compiles every .java in src/main/java + the generated sources
REM   - packages a self-contained fat jar at target/cl-compiler-1.0.0.jar
REM ---------------------------------------------------------------

setlocal
set MVN=mvn
where %MVN% >nul 2>&1
if errorlevel 1 (
    if exist "C:\Users\samee\apache-maven-3.9.9\bin\mvn.cmd" (
        set "MVN=C:\Users\samee\apache-maven-3.9.9\bin\mvn.cmd"
    ) else (
        echo [error] mvn not on PATH and bundled maven not found.
        echo install Apache Maven 3.9+ or set MVN_HOME and try again.
        exit /b 1
    )
)

call "%MVN%" -B clean package
if errorlevel 1 exit /b 1

echo.
echo build ok.
echo   fat jar:  target\cl-compiler-1.0.0.jar
echo   GUI:      run.bat
echo   CLI:      run-cli.bat
endlocal
