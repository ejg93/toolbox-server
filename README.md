# toolbox-server

폐쇄망 반입용 로컬 서버 도구. 브라우저로 `127.0.0.1:<포트>` 를 열어 쓰고, 같은 기능을 배치 명령(`toolbox.bat`)으로도 부른다. 외부 통신이 없다 — DB 접속(JDBC)과, 조회를 누를 때의 `svn` 명령만 사용자가 지정한 서버에 닿는다.

## 현장에서 켜기

1. zip 을 **사용자 폴더**(예 `C:\Users\<이름>\toolbox-server\`)에 푼다. `Program Files` 는 안 된다 — `data/`·`out/`·`logs/` 에 쓰지 못한다
2. `toolbox.bat selftest` — 자바·폴더 쓰기·화면 파일·규칙·템플릿·사전·드라이버·git·svn 을 잰다. `[실패]` 가 없으면 된다(`[경고]` 는 그 기능만 꺼진다)
3. 프로필을 만든다 — `profiles\example.yaml` 을 `profiles\사업A.yaml` 로 복사하고 `name`·`connections`(접속 주소·계정)·`scope.schemas`(볼 스키마)를 고친다. 비밀번호는 적지 않는다(화면·콘솔에서 받는다)
4. 명령 창에서 `run.bat --profile 사업A` 로 켠다. 브라우저가 열린다. 마지막 프로필을 기억해 다음부터는 `run.bat` 더블클릭만으로 된다. 포트는 `run.bat --port 41790`. 없는 프로필 이름이면 켜지 않고 끝 코드 2

자바는 `jre\`(동봉 JDK 17) → `JAVA_HOME` → PATH 순으로 찾고, 17 미만이면 한글 사유를 내고 멈춘다.

### JDBC 드라이버

`drivers\` 바로 아래 jar 를 전부 등록한다. 같은 벤더 jar 는 **하나만** 둔다 — 둘이면 이름 순 첫째가 이긴다.

| 폴더 | 들어 있는 것 |
|---|---|
| `drivers\` | ojdbc11 · postgresql · mariadb-java-client · mssql-jdbc |
| `drivers\alt\` | ojdbc8(JDK 8 현장 WAS·Oracle 12c~) · ojdbc6(Oracle 11g) · mysql-connector-j |

다른 판이 필요하면 `drivers\alt\` 의 jar 를 `drivers\` 의 같은 벤더 jar 와 바꿔 넣는다. 현장 WAS 의 `lib` 에 있는 드라이버도 같은 방식이다. Tibero 드라이버는 동봉하지 않는다 — 개발자판 `tibero*.jar` 를 `drivers\` 에 넣는다.

## 배치 명령

`toolbox.bat <명령> --help` 가 옵션을 보인다. 결과는 stdout(`--json` 이면 응답 JSON 그대로), 진행·오류는 stderr. 서버(`run.bat`)가 켜져 있으면 같은 `data\` 를 쓰지 못한다 — 서버를 끄거나 `--data-dir` 을 따로 준다.

| 명령 | 하는 일 |
|---|---|
| `snapshot --conn <접속>` | DB 메타 스냅샷(표·컬럼·제약·코멘트) |
| `snapshots` · `diff --from <id> --to <id>` | 스냅샷 목록 · 두 스냅샷의 차이 |
| `deliverable --snapshot <id·latest>` | 산출물 xlsx(01~11)를 양식에 기입 |
| `ddl --snapshot … --target <방언>` | 대상 방언의 CREATE 스크립트(실행 안 함) |
| `dto --snapshot … --tables a,b` | DTO·VO 자바 소스 |
| `generate --snapshot … --tables a,b` | CRUD 소스(컨트롤러·서비스·매퍼·JSP). 있는 파일은 안 덮고 `.gen` 옆 파일 |
| `logical comments·candidates·audit·masking` | COMMENT DDL · 표준 후보 CSV · 미준수 리포트 · 개인정보 마스킹 UPDATE(실행 안 함) |
| `check <폴더>` | 코드 검사. `--fail-on error` 면 그 등급 이상이 있을 때 끝 코드 3 |
| `analyze <폴더>` | 프로그램 분석(CRUD 매트릭스·프로그램 목록, `--xlsx`) |
| `deploy-list <폴더> --from --to` | git·svn 두 리비전 사이 바뀐 파일 |
| `api <METHOD> <경로>` | 화면이 쓰는 API 를 그대로(본문은 JSON). 위에 없는 기능은 이것으로 |
| `selftest` | 현장 점검. `--conn <접속>` 이면 DB 연결까지 |

끝 코드: 0 성공 · 1 실패 · 2 사용법·프로필·비밀번호 없음 · 3 `check --fail-on`.

**DB 비밀번호**는 콘솔에서 묻거나 환경변수로 받는다 — 접속별 `TOOLBOX_DB_PASSWORD_<접속ID 대문자>`, 없으면 `TOOLBOX_DB_PASSWORD`. 명령줄 옵션으로는 받지 않는다(셸 이력에 남는다). 비밀번호는 메모리에만 있고 파일·로그에 쓰지 않는다.

## 고쳐서 다시 빌드

소스(`src\`)와 오프라인 저장소(`m2\`)가 같이 들어 있다. 네트워크 없이:

```
build.bat          app.jar 다시 만들기
build.bat test     화면 스모크(브라우저 엔진으로 JS 실행까지)
```

## 만드는 쪽

반입 zip 은 빌드 PC(네트워크 있음)에서:

```
bash scripts/bundle-fetch.sh            JDK·드라이버·javadoc 받기(지문 bundle/MANIFEST)
bash scripts/package.sh [--with 경로]   toolbox-server-<날짜>.zip — 실제 사업 프로필은 --with
bash scripts/rehearse.sh <zip>          저장소 밖에 풀어 동봉 JDK 만으로 version·selftest·오프라인 빌드·기동
```

계획은 [PLAN.md](PLAN.md). 반입 전날 체크리스트는 PLAN 16장 끝. 작업 규칙(`CLAUDE.md`)·진행(`PROGRESS.md`)은 저장소에만 있다 — 반입 zip 밖.
