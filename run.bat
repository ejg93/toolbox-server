@echo off
rem toolbox-server 서버 기동(= toolbox.bat serve). 인자는 serve 로 그대로 — run.bat --port 41790 --profile 사업A
rem 더블클릭한 사람이 사유를 읽게 실패하면 멈춘다. 스크립트에서 부를 때는 TOOLBOX_NO_PAUSE=1
setlocal
call "%~dp0toolbox.bat" serve %*
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" if not defined TOOLBOX_NO_PAUSE pause
exit /b %RC%
