@echo off
rem 현장 빌드(8-8) — jre\(JDK 17)와 m2\ 만으로, 네트워크 없이(-o). 소스를 고친 뒤 다시 만든다.
rem   build.bat        app.jar 다시 만들기(테스트 건너뜀)
rem   build.bat test   화면 스모크(HtmlUnit — JS 실행까지)
setlocal
chcp 65001 >nul
cd /d "%~dp0"
if exist "%~dp0jre\bin\javac.exe" goto :jdk_ok
echo [오류] jre\ 에 JDK 가 없다 — 현장 빌드는 JDK 17 이 있어야 한다(javac.exe).
exit /b 1
:jdk_ok
set "JAVA_HOME=%~dp0jre"
set "MAVEN_USER_HOME=%~dp0m2\.mvn-home"
if /i "%~1"=="test" goto :test
call "%~dp0mvnw.cmd" -o -B -q "-Dmaven.repo.local=%~dp0m2" -DskipTests package
if errorlevel 1 goto :build_fail
copy /y "%~dp0target\app.jar" "%~dp0app.jar" >nul
if errorlevel 1 goto :build_fail
echo 빌드 끝 — app.jar
exit /b 0
:build_fail
echo [오류] 빌드 실패 — 위 메시지를 본다. m2\ 에 없는 의존성이면 반입 묶음이 낡은 것이다.
exit /b 1
:test
call "%~dp0mvnw.cmd" -o -B "-Dmaven.repo.local=%~dp0m2" -DexcludedGroups=db,corpus -Dtest=SmokeHtmlUnitTest -Dsurefire.failIfNoSpecifiedTests=false test
if errorlevel 1 goto :test_fail
echo 화면 스모크 통과
exit /b 0
:test_fail
echo [오류] 화면 스모크 실패
exit /b 1
