@echo off
rem toolbox-server CLI(8-8). 인자를 그대로 넘긴다 — toolbox.bat selftest · toolbox.bat check D:\src --fail-on error · toolbox.bat --help
rem java 는 jre\ → JAVA_HOME → PATH. 17 미만이면 한글 사유. 사람 없는 배치가 멈추지 않게 pause 를 하지 않는다(run.bat 이 한다).
rem 부른 폴더를 TOOLBOX_CWD 로 넘긴다 — 서버 쪽은 jar 폴더 기준 상대 경로를 쓰고, 사용자가 준 상대 경로(폴더·--csv·--out)는 CLI 가 이것으로 푼다.
setlocal
set "TOOLBOX_CWD=%CD%"
chcp 65001 >nul
cd /d "%~dp0"

rem java: jre\ → JAVA_HOME → PATH
set "JAVA=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
if exist "%~dp0jre\bin\java.exe" set "JAVA=%~dp0jre\bin\java.exe"

rem java 17 이상인지 — 낮으면 영어 UnsupportedClassVersionError 로 끝나 원인을 못 읽는다(0-14).
rem 출력은 임시 파일로 받는다(for /f 안의 따옴표·파이프 이스케이프를 피한다). version 줄만 본다(Picked up … 줄 대비)
set "JVER="
set "JVFILE=%TEMP%\toolbox-jver-%RANDOM%.txt"
"%JAVA%" -version 2> "%JVFILE%"
for /f "usebackq tokens=3" %%v in (`findstr /i "version" "%JVFILE%"`) do if not defined JVER set "JVER=%%~v"
del "%JVFILE%" >nul 2>&1
if defined JVER goto :java_found
echo [오류] java 를 찾지 못했다. jre\ 폴더를 넣거나 JAVA_HOME 을 java 17 이상으로 맞추시오.
exit /b 1
:java_found
set "JMAJ="
set "JMIN="
for /f "tokens=1,2 delims=." %%a in ("%JVER%") do set "JMAJ=%%a" & set "JMIN=%%b"
rem 1.8.0_x 형식은 둘째 토큰이 주 버전
if "%JMAJ%"=="1" set "JMAJ=%JMIN%"
set /a JMAJN=%JMAJ% 2>nul
if %JMAJN% GEQ 17 goto :java_ok
echo [오류] java 17 이상이 필요하다. 지금 잡힌 java 는 %JVER% 이다: %JAVA%
echo        jre\ 폴더를 넣거나 JAVA_HOME 을 17 로 맞추시오.
exit /b 1
:java_ok

set "JAR=%~dp0app.jar"
if not exist "%JAR%" set "JAR=%~dp0target\app.jar"
if exist "%JAR%" goto :jar_ok
echo [오류] app.jar 가 없다. zip 을 다시 풀거나 빌드하시오.
exit /b 1
:jar_ok

call :writable data || exit /b 1
call :writable out || exit /b 1
call :writable logs || exit /b 1

"%JAVA%" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -jar "%JAR%" %*
exit /b %ERRORLEVEL%

:writable
if not exist "%~1\" mkdir "%~1" 2>nul
(echo.> "%~1\.w") 2>nul
if exist "%~1\.w" goto :writable_ok
echo [오류] %CD%\%~1 에 쓸 수 없다. Program Files 같은 보호 폴더에 풀면 이렇게 된다.
echo        사용자 폴더(예 C:\Users\이름\toolbox-server)에 풀고 다시 실행하시오.
exit /b 1
:writable_ok
del "%~1\.w" >nul 2>&1
exit /b 0
