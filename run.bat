@echo off
rem toolbox-server 기동. 인자는 serve 로 그대로 넘긴다 — run.bat --port 41790 --profile 사업A
setlocal
chcp 65001 >nul
cd /d "%~dp0"

rem java: jre\ → JAVA_HOME → PATH
set "JAVA=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
if exist "%~dp0jre\bin\java.exe" set "JAVA=%~dp0jre\bin\java.exe"

set "JAR=%~dp0app.jar"
if not exist "%JAR%" set "JAR=%~dp0target\app.jar"
if exist "%JAR%" goto :jar_ok
echo [오류] app.jar 가 없다. zip 을 다시 풀거나 빌드하시오.
pause
exit /b 1
:jar_ok

call :writable data || exit /b 1
call :writable out || exit /b 1
call :writable logs || exit /b 1

"%JAVA%" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -jar "%JAR%" serve %*
exit /b %ERRORLEVEL%

:writable
if not exist "%~1\" mkdir "%~1" 2>nul
(echo.> "%~1\.w") 2>nul
if exist "%~1\.w" goto :writable_ok
echo [오류] %CD%\%~1 에 쓸 수 없다. Program Files 같은 보호 폴더에 풀면 이렇게 된다.
echo        사용자 폴더(예 C:\Users\이름\toolbox-server)에 풀고 다시 실행하시오.
pause
exit /b 1
:writable_ok
del "%~1\.w" >nul 2>&1
exit /b 0
