# toolbox-server

폐쇄망 반입용 로컬 서버 도구. 브라우저로 `127.0.0.1:<포트>` 를 열어 쓴다. 외부 통신이 없다.

## 현장에서 켜기

1. zip 을 **사용자 폴더**(예 `C:\Users\<이름>\toolbox-server\`)에 푼다. `Program Files` 는 안 된다 — `data/`·`out/`·`logs/` 에 쓰지 못한다
2. `run.bat` 을 더블클릭한다. 브라우저가 열린다
3. 포트를 바꾸려면 `run.bat --port 41790`, 프로필은 `run.bat --profile 사업A`

`jre/` 가 있으면 그걸로 돌고, 없으면 `JAVA_HOME`, 그것도 없으면 PATH 의 `java`(17 이상)를 쓴다.

## 만드는 쪽

계획은 [PLAN.md](PLAN.md), 작업 규칙은 [CLAUDE.md](CLAUDE.md), 진행은 [PROGRESS.md](PROGRESS.md).
