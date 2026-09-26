# toolbox-server 계획

폐쇄망 반입용 로컬 서버 도구. 투입 전 한 번에 반입하고, 현장에서는 외부 통신 없이 돈다.
기존 `portfolio/frontend/public/toolbox/tools/*.html` 을 백엔드로 보강한다.

작성 2026-09-26. 계획 단계. 확정 항목은 `[확정]`, 미정은 `[미정]`.

## 1. 기반 결정

| 항목 | 결정 | 근거 |
|---|---|---|
| 웹 프레임워크 | `[확정]` Javalin 6 | 내장 Jetty, jar 하나. Maven 의존성이라 설치 없음 |
| JDK | `[확정]` 17. Temurin zip 동봉 | 현장 JDK 를 믿지 않는다 |
| 저장소 | `[확정]` 별개 저장소 `toolbox-server` | 반입 묶음 = 저장소 하나 |
| 사전·프로필 저장 | `[확정]` H2 파일(`toolbox.mv.db`, jar 옆) | 설치 없음 |
| HTML 규약 | `[확정]` 순수본·백엔드본 분리. **순수본 동결** — 버그 수정만, 기능은 백엔드본에만 | 포트 막힌 현장은 순수본을 `file://` 로 연다 |
| 바인드 | `[확정]` `127.0.0.1` 전용 | 보안 검토 논리: 루프백 전용, 외부 호출 0 |
| 포트 | `[확정]` `4xxxx` 대 하나 고정(예 `41780`), 프로필에서 변경. 점유 시 다음 포트 | `3000`·`8080`·`8088`·`9736` 회피 |
| DB 비밀번호 | `[확정]` 매 기동 입력, 메모리만 | 파일 저장 안 함 |
| 모드 배지 | `[확정]` 런처·각 도구 상단에 「백엔드 연결 / 순수」 표시 | |

### 저장소 구조

```
toolbox-server/
  pure/tools/*.html                        portfolio 에서 복사. 동결
  core/                                    메타모델·파서·규칙·리포트. UI 없음
  cli/                                     picocli. 배치용
  web/src/main/resources/tools/*.html      백엔드본. 여기서 진화
  profiles/                                사업별 YAML
  drivers/                                 ojdbc·tibero·pg·mariadb·mssql
  m2/                                      오프라인 Maven 저장소
  jre/                                     Temurin 17
  run.bat
```

### 반입 묶음에 넣을 것

- fat jar + 소스 전체 + `.m2` 스냅샷. 현장에서 고칠 수 있어야 한다
- JDBC 드라이버 5종. **Tibero 는 Maven Central 에 없다** — 개발자판·라이선스 파일을 미리
- 현장 스모크는 jar 안 HtmlUnit 테스트. Puppeteer 는 반입 안 함(11장)
- 오프라인 javadoc: JavaParser·POI·PMD
- 테스트 코퍼스는 반입 안 함. 픽스처 소량만 jar 에 동봉

## 2. 기존 도구 패치 방향

### 2.1 산출물_sql — `[확정]` C 메타모델 계층

DB → 공통 메타모델(Table·Column·Constraint·Comment) → 문서별 매핑. 방언 SQL 은 어댑터로 격리.

- 1차 산출물: `산출물 양식/` xlsx 를 POI 로 9종 한 번에 기입. 소스의 「수기」 규칙 20건을 코드화
  (`_YN`→`CHAR(1)` 도메인 추론, 최종수정자 보정, 「02와 반드시 동일」 정합성)
- 부산물: SQL 실행 대행(결과 그리드 + CSV·xlsx). 5.sql_snippets 실행 버튼이 같은 엔진을 쓴다
- 데이터 품질 진단 8종을 스키마 전수로

### 2.2 논리명_변환기 — `[확정]` A + C

**A 사전 서버 상주**
- 최신 행안부 공통표준단어를 **jar 에 기본 동봉**. 업로드 없이 시작
- 기관표준단어는 H2 에 누적. 내보내기·가져오기
- 첫 기동 시 브라우저 localStorage(`lnUserDict`·`lnSkipTok`·`lnDialect`) 가져오기 화면 한 번
- 변환 계산은 JS 유지

**B 자바 이식 — `[확정]` 한다. GraalJS 는 쓰지 않는다**
- 자바가 유일한 구현. 백엔드본 HTML 은 API 만 부른다. 순수본 JS 는 동결본
- 산출물 05·06·07(표준용어·단어·도메인) 생성과 변환기가 같은 코드를 쓴다
- 검증: `논리명_변환기_sample` 1000컬럼을 순수본 JS 와 자바에 같이 넣어 골든 파일 대조.
  기대 수치는 `regress_logicalname.js` 와 샘플 README 에 이미 있다

**C DB 직결**
- 컬럼목록 업로드 대신 스키마 읽기 → `COMMENT ON` 스크립트 내려주기 또는 직접 실행 → 표준 미준수 전수 리포트
- 2.1 메타모델을 그대로 쓴다

**대상 범위(scope) — C 의 전제 기능.** 현장은 스키마가 여럿이고 안 쓰는 테이블이 많다.
- 스키마 다중 선택
- 테이블 포함·제외: 접두사·정규식·명시 목록. 백업·임시 패턴 기본 제외(`_BAK`·`_TMP`·`_YYYYMMDD` 접미)
- 행수 0·최근 변경 없음 필터
- 선택 결과를 프로필에 저장. **산출물_sql 의 `deliv_excl` 과 같은 개념** — 프로필의 scope 하나를 두 도구가 공유

### 2.3 dev_tools

| 탭 | 결정 |
|---|---|
| 화면 표시기(주석 삭제) | `[확정]` A 폴더 일괄 + B 언어 파서(JavaParser·Jsoup. JSP·XML 은 기존 스캐너 유지). 코드 검사 도구로 옮기지 않고 탭으로 남긴다 |
| 텍스트 비교 | `[확정]` A 폴더 비교 — 두 폴더의 같은 상대 경로 파일을 짝지어 한쪽에만 있음·같음·다름으로 나누고, 다름은 기존 줄 비교 화면으로 연다. SVN·Git diff 는 여기가 아니라 3 코드 검사의 입력 방식 |
| 인코딩 판별 | `[확정]` 탭은 유지. 폴더 전수 검사는 3.1 코드 검사의 규칙 「파일 인코딩·개행이 프로필 기준과 다름」으로 흡수 |
| INSERT 생성 | `[확정]` A 테이블 메타 기반 — DB 에서 타입·길이·NOT NULL 을 읽고 FK 컬럼은 실존 값. N 행, 방언별 |
| 나머지 탭 | 유지 |

### 2.4 jsp_formatter — `[확정]` 폴더 일괄. 로직은 JS 그대로

백엔드는 파일 읽기·쓰기만: 폴더 파일 목록·내용을 내려주고, 브라우저 안 기존 JS 가 포매팅해 결과를 올리면 저장(원본 백업). 구조 변경 0건 리포트는 비교 탭 로직으로 파일마다 계산. Node·GraalJS 불필요.

### 2.5 sql_snippets — `[확정]` B

스니펫 「실행」 버튼. `#{param}` 파싱으로 바인드 입력 폼 자동 생성 → 결과 그리드. 2.1 실행 엔진 재사용.

### 2.6 table_builder — `[확정]` B

xlsx·HWP 표 내보내기(POI·hwplib).

### 2.7 특수문자_모음 — 유지

### 2.8 공통 — 프로필

스키마·방언·작성자·DB 접속·scope 를 서버 프로필(YAML + H2)로. 도구별 localStorage(`deliv_*`·`ln*`) 는 첫 기동 가져오기 뒤 사용 안 함.

## 3. 신규 기능

### 3.1 코드 검사 — `[확정]` 만든다. 순수본 런처 빈 카드 자리

- 순수본: 붙여넣기 입력, 정규식 묶음(ㄱ·ㄹ·ㅁ)만
- 백엔드본: 폴더 입력, 「내 변경분만」 체크 시 SVN·Git 워킹카피 diff 로 자동 추출(SVNKit·JGit)
- 규칙 묶음은 실행 전 체크박스로 고른다. 묶음을 펼치면 개별 규칙 on/off 와 정규식 값 수정. 체크 상태는 프로필에 저장

| 묶음 | 항목 | 구현 |
|---|---|---|
| ㄱ 공통 잔재 | `debugger`·`console.log`·`alert(`·`System.out.println`·`printStackTrace`·`TODO`·`localhost`·개발 IP·`password=` 하드코딩 | 정규식 |
| ㄴ Java 구조 | 미사용·`*` import, `@Controller`·`ServiceImpl` 명명 정규식, Service↔Impl 짝, `@Service("이름")`↔클래스명, `EgovAbstractServiceImpl` 상속 누락, 빈 catch, Impl 안 예외 삼킴, Controller→DAO 직접 호출, `@RequestMapping` URL 중복 | JavaParser |
| ㄷ MyBatis XML | `${}`, `select *`, id 중복, namespace↔인터페이스 불일치 | XML 파서 + JSqlParser |
| ㄹ JSP | `<%= %>` 출력, `escapeXml="false"`, 스크립틀릿 블록 수 | 정규식 |
| ㅁ TSX·JS | `debugger`·`console`, 미사용 import, `any`, `eslint-disable` | 정규식 |
| ㅂ 파일 | 인코딩·개행 기준 불일치, 탭·공백 혼용, 헤더 개정이력 누락 | 바이트 검사 |
| ㅅ PMD | PMD 기본 규칙셋 결과를 같은 리포트에 합침 | 라이브러리 내장 |

화면: 왼쪽 입력(폴더·변경분만·붙여넣기·프로필), 오른쪽 규칙 체크박스, 아래 결과 표(파일·줄·묶음·규칙·원문). 등급·묶음 필터, 행 클릭 시 앞뒤 5줄 미리보기, 복사·xlsx 내려받기.

### 3.2 프로그램 분석 — `[확정]`

Java → Mapper 호출 → XML SQL → 테이블 추적. 3.1 의 ㄴ·ㄷ 파서 재사용. 한 도구에서 두 표가 나온다.
- CRUD 매트릭스: 프로그램(Controller 메서드) × 테이블, 칸에 C·R·U·D
- 프로그램 목록: Controller·메서드·URL·JSP·설명

### 3.3 CRUD 생성기 — `[확정]`

테이블을 고르면 Controller·Service·ServiceImpl·DAO·Mapper XML·JSP 세트 생성. 템플릿은 사업 프로필. 2.1 메타모델 사용.

### 3.4 HWP 출력 — `[확정]` 도구가 아니라 내려받기 형식

산출물 도구·프로그램 분석의 내려받기 형식 선택: xlsx / hwp / hwpx. hwplib·hwpxlib.

### 3.5 추가 기능 — `[확정]` 전부

| 기능 | 무엇 | 어디에 | 마일스톤 |
|---|---|---|---|
| 보안약점 규칙 묶음 ㅇ | 행안부 SW 보안약점 중 정적 탐지 가능분: SQL 삽입(`${}`·문자열 조립 쿼리), XSS(`<%=`·`escapeXml="false"`), 경로 조작(`new File(request.getParameter…)`), 하드코딩 비밀번호, 예외 메시지 노출 | 코드 검사 묶음 | M5 |
| 영향도 조회 | 테이블·컬럼 → 걸리는 프로그램. CRUD 매트릭스 역방향 | 프로그램 분석 탭 | M6 |
| 로그 SQL 복원 | MyBatis·log4jdbc 로그의 `?` + 파라미터 줄 → 실행 가능한 SQL. 순수 텍스트라 순수본 dev_tools 에도 넣는다(portfolio 작업) | dev_tools 탭 | M4 |
| 엑셀 → INSERT·MERGE | 공통코드·초기 데이터 엑셀 → 방언별 INSERT. 컬럼 타입은 메타모델로 검증 | dev_tools INSERT 탭 | M4 |
| 배포 파일 목록 | SVN 리비전·Git 커밋 구간 변경 파일 → 배포 요청서 xlsx | 코드 검사 옆 탭 | M5 |
| DB 간 비교 | 접속이 다른 스냅샷끼리 diff(개발 vs 운영). 스냅샷 diff 가 접속을 가리지 않게 설계 | 스냅샷 화면 | M1 |
| DDL 생성·방언 변환 | 스냅샷 → CREATE 스크립트. 방언 간 타입 매핑표(YAML) | 산출물 옆 | M7 뒤 |
| 개인정보 마스킹 SQL | 이름·주민번호·전화 컬럼 탐지(논리명 사전 + 컬럼명 패턴) → UPDATE 문 생성 | 논리명 옆 | M7 뒤 |

### 3.6 프레임워크 세대 — `[확정]`

프로필 `framework: egov35 | egov4 | spring`. 생성기 템플릿 세트 폴더와 eGov 전용 검사 규칙(`EgovAbstractServiceImpl` 상속·`@Service("이름")`·패키지 관례)만 이 값을 본다. 나머지 기능은 무관.
지금은 `egov35` 세트만 만들고 다른 폴더는 비워 둔다. 모르는 세대를 만나면 `spring` 으로 두고 eGov 규칙을 끈다.

### 런처 카드

- 순수본 런처(portfolio `toolbox.html`): 기존 7 + 코드 검사 = 8
- 백엔드 런처: 8 + DB 브라우저(스냅샷·diff, 1-8) + 프로그램 분석 + CRUD 생성기 = 11. 「텍스트·코드」 / 「DB·산출물」 두 묶음

## 4. 검증 코퍼스

| 대상 | 출처 |
|---|---|
| Java·JSP·MyBatis XML | eGovFrame 공통컴포넌트 |
| Java 일반 | Apache Commons 계열 |
| SQL | JSqlParser 테스트 스위트, Oracle 샘플 스키마 HR·OE |
| 산출물·논리명 | 실제 DB 필요 — 로컬 Oracle XE + HR, PostgreSQL·MariaDB 컨테이너, Tibero 개발자판 |
| 검사기 양성 케이스 | 손으로 심은 픽스처 |

기대 출력은 골든 파일 → JUnit 스냅샷 비교. 순수본·백엔드본 둘 다 Puppeteer 로 회귀.

## 5. 상세 설계 결정 — 전부 `[확정]`

| # | 항목 | 결정 |
|---|---|---|
| 1 | 프로필 저장 | YAML 이 원본. H2 는 사전·스냅샷·실행 이력만 |
| 2 | 메타모델 수집 | 뼈대는 JDBC `DatabaseMetaData`, 코멘트 등 부족분은 벤더 딕셔너리 SQL 로 덧씌움 |
| 3 | 메타모델 스냅샷 | H2 에 저장. 스냅샷 간 diff 로 테이블 변경이력 산출 |
| 4 | 긴 작업 | 작업 ID 발급 + SSE 진행률·중간 결과. 취소 가능 |
| 5 | 폴더 입력 | 경로 텍스트 + 최근 목록 + 프로필 프로젝트 루트 기본값. 서버가 직접 읽는다 |
| 6 | 실행 이력 | 코드 검사·품질 진단 결과를 H2 에 저장(파일·줄·규칙만, 코드 본문 없음). 이전 실행과 비교 |
| 7 | xlsx 기입 | 양식 파일을 템플릿으로 열어 셀 좌표 매핑 기입. 매핑은 YAML |
| 8 | 규칙 정의 | 정규식 규칙 YAML(순수본도 같은 YAML 을 JSON 으로 내장) / 구조 규칙 자바 클래스 + YAML 파라미터 / PMD XPath 를 YAML 문자열로 허용 |
| 9 | 생성기 템플릿 | Freemarker. 템플릿 세트를 폴더별로 두고 프로필에서 선택(`egov35`·`spring-boot` 등) |
| 10 | 백엔드본 HTML | 공용 `common.js`(API·프로필·모드 배지·결과 표) + 도구별 HTML. 파일명 영문 |
| 11 | 빌드 | Maven 단일 모듈. 패키지 `core`·`web`·`cli`. `core` 는 `web`·`cli`·Javalin 을 import 하지 않는다 |
| 12 | API 보호 | 루프백 바인드만. 인증 없음 |
| 13 | 테스트 DB | Testcontainers(PostgreSQL·MariaDB·MSSQL·Oracle Free). Tibero 는 개발자판 수동 설치 후 골든 파일만 |
| 14 | CLI 범위 | 전부 |

## 6. 프로필 YAML

`profiles/<사업명>.yaml`. 비밀번호 항목 없음 — 기동 후 화면에서 입력, 메모리만.

```yaml
name: 사업A
project:
  root: C:\proj\src
  encoding: UTF-8
  lineEnding: CRLF
  vcs: svn            # svn | git | none
connections:
  - id: dev
    dialect: oracle   # oracle | tibero | postgresql | mariadb | mssql
    url: jdbc:oracle:thin:@host:1521/SID
    user: APP
defaultConnection: dev
scope:
  schemas: [APP, CMM]
  exclude:
    prefixes: [TMP_, BAK_]
    suffixes: [_BAK, _TMP, _OLD]
    regex: ['_\d{8}$']
    tables: []
  include:
    tables: []          # 비어 있으면 제외 규칙만 적용
  skipEmpty: false      # 행수 0 제외
deliverable:
  author: 홍길동
  org: 기관명
  templateDir: templates/deliverable/사업A/    # 양식 xlsx
  mapping: mappings/deliverable/사업A.yaml     # 셀 좌표
naming:
  controller: '^[A-Z]\w*Controller$'
  service: '^[A-Z]\w*Service$'
  serviceImpl: '^[A-Z]\w*ServiceImpl$'
  dao: '^[A-Z]\w*(DAO|Mapper)$'
  serviceImplBase: egovframework.rte.fdl.cmmn.EgovAbstractServiceImpl
  headerRequired: true  # 파일 헤더 개정이력
codecheck:
  groups: { common: true, java: true, mybatis: true, jsp: false, tsx: false, file: true, pmd: false }
  rules: {}             # 규칙 id → on/off·파라미터 덮어쓰기
  customRules: rules/사업A.yaml   # 정규식·PMD XPath 추가분
framework: egov35       # egov35 | egov4 | spring. 생성기 템플릿·eGov 전용 규칙만 본다
output:
  dir: out/             # jar 기준 상대. 하위에 <프로필>/<날짜시각>/
generator:
  templateSet: egov35
  basePackage: kr.go.xxx
  outDir: C:\proj\gen
logicalName:
  skipTokens: [TB, TBL]
```

명명 기본값은 eGov 표준(`XxxController`·`XxxService`·`XxxServiceImpl`·`XxxDAO`·`XxxMapper`). `[미정]` 현장 관례를 알면 기본값을 바꾼다.

## 7. 메타모델

### 객체

| 객체 | 필드 |
|---|---|
| `Schema` | name, tables[] |
| `Table` | schema, name, type(TABLE·VIEW), comment, columns[], pk, fks[], uniques[], indexes[], rowCount, createdAt, lastDdlAt |
| `Column` | name, ordinal, nativeType(벤더 원문), jdbcType, length, precision, scale, nullable, defaultValue, comment, domain(추론) |
| `PrimaryKey` | name, columns[] |
| `ForeignKey` | name, columns[], refTable, refColumns[] |
| `Index` | name, unique, columns[] |

### 수집 인터페이스

```java
interface MetaSource {
    List<String> listSchemas();
    List<Table> listTables(Scope scope);       // 뼈대만
    void loadColumns(Table t);
    void loadConstraints(Table t);
    void loadIndexes(Table t);
    void loadComments(Schema s);              // 벤더 SQL
    void loadStats(Schema s);                 // rowCount·createdAt·lastDdlAt. 벤더 SQL
}
```

`JdbcMetaSource` 가 JDBC 로 뼈대를 채우고, `OracleMetaSource`·`TiberoMetaSource`·`PostgresMetaSource`·`MariaMetaSource`·`MssqlMetaSource` 가 상속해 코멘트·통계만 덮어쓴다. 벤더 SQL 은 산출물_sql 의 방언 5종 쿼리를 이식한다. `db_docs/README.md` 「확인된 사실」(`LAST_DDL_TIME` 신뢰 불가 등)을 그대로 따른다.

**버전** — 벤더가 같아도 버전이 갈린다(MSSQL `TRANSLATE` 2017+, MySQL 8 `REGEXP_REPLACE`, Oracle 12c identity). 접속 시 `getDatabaseProductVersion()` 을 읽어 두고 갈리는 쿼리만 분기. 딕셔너리 SQL 은 오래된 뷰 위주로 짠다. 집에서는 각 벤더 최신 1버전만 검증하므로 분기는 보수적으로. 드라이버는 `drivers/` 폴더를 스캔해 URL 접두로 고른다.

### 스냅샷

H2 테이블 `snapshot(id, profile, connId, takenAt)` + `snap_table`·`snap_column`·`snap_constraint`·`snap_index`.
`SnapshotDiff(a, b)` → 추가·삭제·변경(컬럼 타입·길이·NULL·코멘트) 목록. 산출물 「테이블 변경이력」 의 입력.

## 8. API

전부 `127.0.0.1:<port>/api/`. 긴 작업은 `jobId` 를 돌려주고 `GET /api/jobs/{id}/events`(SSE) 로 진행률·중간 결과. `DELETE /api/jobs/{id}` 취소.

| 영역 | 엔드포인트 | 내용 |
|---|---|---|
| 공통 | `GET /ping` | version·활성 프로필. 모드 배지가 부른다 |
| 프로필 | `GET /profiles` · `GET /profiles/{name}` · `PUT /profiles/{name}` · `POST /profiles/active` | PUT 은 YAML 파일을 다시 쓴다 |
| 접속 | `POST /conn/{id}/password` · `POST /conn/{id}/test` | 비밀번호는 메모리 |
| 메타 | `POST /meta/snapshot`(job) · `GET /meta/snapshots` · `GET /meta/snapshots/{id}/tables` · `GET /meta/snapshots/{id}/tables/{name}` · `GET /meta/diff?a=&b=` | |
| SQL 실행 | `POST /sql/run` {connId, sql, binds, maxRows} · `POST /sql/export` {…, format} | sql_snippets 실행 버튼·산출물 부산물 |
| 산출물 | `POST /deliverable/build`(job) {snapshotId, docs[], format: xlsx·hwp·hwpx} → 파일 | |
| 사전 | `GET /dict/words?q=` · `GET /dict/org` · `PUT /dict/org` · `POST /dict/import`(localStorage 가져오기) · `GET /dict/export` | |
| 논리명 | `POST /logical/convert` {names[]} · `POST /logical/comments` {snapshotId} → DDL · `POST /logical/audit` {snapshotId} → 미준수 목록 | |
| INSERT | `POST /insert/generate` {connId, table, rows, dialect} | FK 실존 값 조회 포함 |
| 주석 삭제 | `POST /strip` {path 또는 text, lang} (path 면 job) | |
| 폴더 비교 | `POST /diff/folders` {a, b} · `GET /diff/file?a=&b=` | |
| 코드 검사 | `POST /check/run`(job) {path 또는 text, changedOnly, groups, ruleOverrides} · `GET /check/runs` · `GET /check/runs/{id}` · `GET /check/runs/{id}/compare?prev=` · `GET /check/rules` | |
| 프로그램 분석 | `POST /analyze/run`(job) {path} · `GET /analyze/runs/{id}/crud` · `GET /analyze/runs/{id}/programs` · `POST /analyze/runs/{id}/export` {format} | |
| 생성기 | `POST /generate` {snapshotId, tables[], templateSet, outDir} · `GET /generate/templates` | |
| 파일 | `GET /fs/recent` · `GET /fs/exists?path=` | |
| table_builder | `POST /table/export` {cells, format: xlsx·hwp} | |

## 9. 패키지

```
kr.ejg.toolbox
  core.profile     Profile 로드·저장(YAML)
  core.meta        메타모델·MetaSource·스냅샷·diff
  core.dialect     방언별 SQL·MetaSource 구현
  core.dict        표준단어 사전(H2)
  core.logical     영문명 분해·논리명 조립·COMMENT 생성 (JS 이식)
  core.sqlrun      SQL 실행·바인드·결과
  core.report      xlsx(POI)·hwp(hwplib)·hwpx 출력, 셀 매핑
  core.check       규칙 엔진·규칙 구현·PMD 래핑·실행 이력
  core.analyze     Java→Mapper→SQL→테이블 추적, CRUD·프로그램 목록
  core.gen         Freemarker 생성기
  core.vcs         SVNKit·JGit 변경분 추출
  core.strip       주석 삭제 스캐너
  core.job         작업 큐·진행률·취소
  web              Javalin 라우트·정적 파일·SSE
  cli              picocli 명령
```

`core` 는 `web`·`cli`·Javalin 을 import 하지 않는다.

## 10. 마일스톤 — 각 단계의 「끝났다」 기준

| M | 내용 | 끝났다 |
|---|---|---|
| 0 | 골격. `run.bat` 기동, `127.0.0.1`, 포트 fallback, 프로필 로드, `/ping`, 순수본 복사·동결, 백엔드본 10카드 런처(미구현 카드는 비활성), 모드 배지, `common.js` | Puppeteer 로 런처·도구 전부 열림, 콘솔 에러 0. 네트워크 끊고 `m2/` 만으로 빌드 성공 |
| 1 | 메타모델 + scope + 스냅샷 + diff + SQL 실행 | Testcontainers 4종 + 샘플 스키마 스냅샷이 골든 파일과 일치. Tibero 골든 수동 1회 |
| 2 | 산출물 xlsx 9종 + hwp·hwpx + 데이터 품질 진단 전수 | 생성 파일을 POI 로 다시 읽어 셀 값 골든 비교. 「수기」 20건이 코드에 있고 각각 테스트 있음 |
| 3 | 사전 상주 + 논리명 자바 이식 + localStorage 가져오기 + COMMENT + 미준수 리포트 | 샘플 1000컬럼 결과가 순수본 JS 출력과 동일. `regress_logicalname.js` 기대 수치 통과 |
| 4 | sql_snippets 실행 · INSERT 메타 기반 · table_builder 내보내기 · 폴더 비교 · 주석 삭제 폴더+파서 | 각각 골든 1세트. INSERT 는 생성문을 실제 실행해 성공 |
| 5 | 코드 검사 순수본·백엔드본. 규칙 ㄱ~ㅅ, SVN·Git 변경분, 이력 비교 | 픽스처 양성·음성 세트 골든. eGov 공통컴포넌트 전체를 돌려 예외 0 |
| 6 | 프로그램 분석. CRUD 매트릭스·프로그램 목록·내보내기 | eGov 공통컴포넌트 모듈 하나(예: 게시판)의 CRUD 골든 |
| 7 | CRUD 생성기 `egov35` 세트 | 생성물이 eGov 3.5 jar classpath 로 `javac` 통과 |
| 8 | CLI 전 명령 + 반입 패키징 | 11장 체크리스트 전부. 새 PC 에서 zip 풀고 `run.bat` 만으로 기동 |

portfolio 쪽 작업 하나가 따라온다 — 순수본 코드 검사 HTML 을 `toolbox/tools/` 에 추가하고 런처 카드·`개선사항_메모.md` 갱신. M5 때.

## 11. 반입 묶음 체크리스트

```
toolbox-server-<날짜>.zip
  run.bat                  포트·프로필 인자. JAVA_HOME 을 jre/ 로
  app.jar
  jre/                     Temurin 17 (x64)
  pure/tools/              순수본 8 + toolbox.html
  profiles/  mappings/  rules/  templates/
  drivers/                 벤더별 여러 버전: ojdbc6·8·11 / tibero5·6·7 / postgresql / mariadb·mysql / mssql-jdbc. 현장 WAS lib 의 드라이버도 넣어 쓸 수 있게 폴더 스캔
  m2/                      오프라인 저장소 (빌드 리허설 통과본)
  src/                     소스 전체 + pom.xml + mvnw
  data/  logs/             H2 파일·로그. jar 옆. 처음엔 비어 있음
  docs/                    JavaParser·POI·PMD·Javalin javadoc, 이 PLAN.md, db_docs/README.md
  fixtures/                픽스처 소량
```

Puppeteer 는 반입하지 않는다 — 크로미엄 150MB 실행파일이 딸려 와 검토에서 걸린다. 현장 스모크는 jar 안 HtmlUnit 테스트. Node 도 반입하지 않는다 — jsp_formatter 일괄은 2.4 방식이라 JS 실행기가 필요 없다.

## 12. 이번 주 범위와 부가 결정

- 이번 주: **M0 + M1**. M3(논리명 이식)는 다음 주 첫 작업
- 저장소 private. 실제 사업 프로필·발주처 양식은 `.gitignore`
- 순수본은 portfolio 가 원본. `scripts/sync-pure.sh` 가 복사 + 해시 비교. 순수본 버그 수정은 portfolio 에서만
- 백엔드본 프론트는 바닐라 JS. 빌드 없음
- H2·로그는 jar 옆 `data/`·`logs/`
- 패키지 `kr.ejg.toolbox`. 저장소 `toolbox-server`
- Windows 전용. `run.bat` 만. `-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8` 고정
- 출력 파일은 jar 옆 `out/<프로필>/<날짜시각>/`. 화면에 「폴더 열기」. 프로필에서 기본 경로 변경
- 1인 1서버. 잠금 없음. 탭 여러 개는 작업 ID 로 구분. 다중 사용자 미지원
- 현장 PC 는 일반 계정 가정. README 에 「사용자 폴더에 풀 것」, 기동 시 `data/`·`out/`·`logs/` 쓰기 검사 → 실패 시 한글 사유 출력 후 종료
- 개발 PC: Docker Desktop 있음, JDK 17 `C:/Program Files/Java/jdk-17.0.19`, Maven 은 `mvnw` 래퍼

## 13. M0 작업 분해

순서대로. 각 항목은 커밋 하나 크기.

| # | 작업 | 끝났다 |
|---|---|---|
| 0-1 | `git init`, `.gitignore`(`data/` `logs/` `m2/` `profiles/*.yaml` 중 예시 제외, `templates/deliverable/` 실물, `target/`), `mvnw` 생성, `pom.xml`(Java 17, Javalin 6, Jackson, SnakeYAML, H2, picocli, JUnit 5, HtmlUnit, Testcontainers) | `./mvnw -q package` 성공 |
| 0-2 | CI `.github/workflows/verify.yml` — push·PR 마다 `mvnw verify`(컨테이너 포함) + tools·docs 레인. `sync-pure.sh`·훅·verify 는 2026-09-26 부트스트랩이 끝냈다 | 첫 마무리에서 CI 초록 |
| 0-3 | `core.profile` — YAML 로드·저장, 6장 필드 전부 매핑, `profiles/example.yaml` | 예시 로드 → 객체 → 다시 저장했을 때 내용 동일(JUnit) |
| 0-4 | `web.App` — Javalin 기동, `127.0.0.1` 바인드, 포트 인자·점유 시 +1 반복·로그 출력, 기동 후 기본 브라우저 오픈, `GET /api/ping` {version, profile, mode:"backend"} | 기동 로그에 주소, ping 응답. `0.0.0.0` 으로 열리지 않음(netstat) |
| 0-5 | `run.bat` — `jre/` 있으면 그걸로, 없으면 `JAVA_HOME`, 그것도 없으면 `java`. `-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8`. 인자 `--port` `--profile` 전달. 기동 시 `data/`·`out/`·`logs/` 쓰기 검사 | 더블클릭 기동. 읽기 전용 폴더에서 한글 사유 출력 후 종료 |
| 0-6 | 정적 파일 — `resources/tools/` 서빙, `common.js`(ping 호출·모드 배지·프로필 이름 표시·`api()` 헬퍼·결과 표 렌더러) | `/tools/common.js` 200 |
| 0-7 | 백엔드 런처 `resources/tools/index.html` — 카드 10장, 「텍스트·코드」/「DB·산출물」 두 묶음. 미구현 카드는 비활성 + 「M? 예정」 표시 | 열림, 콘솔 에러 0 |
| 0-8 | 기존 7 도구를 백엔드본으로 복사·영문 파일명·`common.js` 연결·모드 배지만 추가. 기능 변경 없음 | 7 도구 각각 열림, 배지 「백엔드 연결」, 기존 기능 동작 |
| 0-9 | `core.job` — 작업 등록·진행률·취소, `GET /api/jobs/{id}/events` SSE, `DELETE /api/jobs/{id}`. 더미 작업(1초에 10% 씩)으로 배선 확인 | SSE 로 10회 이벤트 수신 후 done. 중간 DELETE 시 cancelled |
| 0-10 | `cli.Main` — picocli 루트 + `serve` 명령(0-4 호출) + `version`. jar 매니페스트 Main-Class | `java -jar app.jar serve --port 41780` 기동 |
| 0-11 | HtmlUnit 스모크 JUnit — 서버 띄우고 런처 + 7 도구 열어 JS 에러 0 확인 | `./mvnw test` 초록 |
| 0-12 | `scripts/offline-build.sh` — `mvn dependency:go-offline` 로 `m2/` 채우고 `-o` 빌드 | 네트워크 끊고 빌드 성공 |
| 0-13 | H2 초기화 — `data/toolbox.mv.db` 생성, 스키마 마이그레이션(Flyway 없이 버전 테이블 + SQL 파일 순차 실행), 사전·스냅샷·이력 테이블 DDL 만 | 기동 시 파일 생성, 재기동 시 재생성 안 함 |

M0 끝 = 13개 다 초록 + Puppeteer 로 런처·7 도구 스크린샷 확인.

## 14. M1 작업 분해

| # | 작업 | 끝났다 |
|---|---|---|
| 1-1 | `core.meta` 객체(7장) + `Scope` 필터(스키마·접두·접미·정규식·목록·행수 0) | Scope 단위 테스트 |
| 1-2 | `JdbcMetaSource` — `DatabaseMetaData` 로 테이블·컬럼·PK·FK·UNIQUE·인덱스 | PostgreSQL 컨테이너 + 샘플 DDL → 골든 |
| 1-3 | 벤더 `MetaSource` 5종 — 코멘트·rowCount·created. 산출물_sql 방언 SQL 이식. `db_docs/README.md` 「확인된 사실」 반영 | PostgreSQL·MariaDB·MSSQL·Oracle Free 4종 골든. Tibero 는 수동 1회 후 골든 고정 |
| 1-4 | 접속 관리 — `POST /conn/{id}/password`(메모리) · `/conn/{id}/test`, 드라이버 `drivers/` 동적 로드 | 잘못된 비밀번호 시 오류 메시지, 로그에 비밀번호 없음 |
| 1-5 | 스냅샷 저장·조회 API + H2 테이블 | 저장 후 조회 결과 = 수집 결과 |
| 1-6 | `SnapshotDiff` + `GET /meta/diff`. 접속이 다른 스냅샷끼리도 된다(개발 vs 운영) | 컬럼 추가·삭제·타입 변경 3케이스 골든 + 접속 둘 비교 1케이스 |
| 1-7 | `core.sqlrun` — `POST /sql/run`(binds·maxRows·타임아웃) · `/sql/export`(xlsx) | 결과 그리드 골든, maxRows 초과 시 잘림 표시 |
| 1-8 | 백엔드본 화면 — 프로필 선택·접속·비밀번호 입력·스냅샷 목록·테이블 브라우저·diff 뷰. 런처 「DB·산출물」 첫 카드 | HtmlUnit 스모크 + Puppeteer 스크린샷 |

## 15. 번들 — 실행 세션이 한 번에 치는 묶음

설계(Fable)가 행을 적고 실행(Opus)이 행대로 간다. 번들 하나 = 실행 세션 하나 = 끝의 마무리와 PR 하나. 규칙은 CLAUDE.md 「번들 모드」.

| 번들 | 순서 | 설계 | 설치 | 상태 |
|---|---|---|---|---|
| 번들 0 | `0-1`→`0-2`→`0-3`→`0-4`→`0-5`→`0-6`→`0-7`→`0-8` | 2026-09-26(Fable). 골격·CI·프로필·서버·정적 파일·런처·도구 복사. 머지 위임 | 없음(mvnw 가 Maven 3.9.9 를 첫 실행에 받는다 — 네트워크) | 완료 — PR #1 머지 `5e14026` |
| 번들 1 | `0-9`→`0-10`→`0-11`→`0-12`→`0-13`→`1-1`→`1-2` | 2026-09-26(Fable). 작업 큐·CLI·스모크·오프라인 빌드·H2·메타모델·PG 수집. 머지 위임 | Docker 켜 둠(`postgres:17-alpine` 은 이미 있다) | 7행 완료(2026-09-26 밤). PR 은 이력 |
| 번들 2 | `1-3`→`1-4`→`1-5`→`1-6`→`1-7`→`1-8` | 2026-09-26(Fable). 벤더 5종·접속·스냅샷·diff·SQL 실행·화면. 머지 위임 | 이미지 pull: mariadb·mssql·oracle-free 약 4GB(밤에 받는다 — 사용자 결정) | **밤 2026-09-26** 셋째(토큰 남으면) |

## 16. 청크 분할표

행마다 **사실·결정·번호 절차·축·강제 지점·건드리는 자리·닫힘·실패 사다리**. 절차의 마지막은 시험. 실행이 행과 다르게 가면 상태 칸에 실제로 한 것을 적는다. 완료 행은 안 고친다.
축 표기: 「규칙 n」= CLAUDE.md 절대 규칙, 「5-n」= PLAN 5장 결정, 「사실」= 라이브러리·환경 실측, 「관례」.

| # | 청크 | 설계 | 선행 | 상태 |
|---|---|---|---|---|
| 0-1 | pom + 골격 | **사실**: `mvnw`(Maven 3.9.9)·`.mvn/wrapper` 는 부트스트랩이 portfolio backend 에서 복사했다. Maven 은 PATH 에 없고 JAVA_HOME 은 11 이라 반드시 `bash scripts/mvn.sh`. **결정**(2026-09-26 설계): 단일 모듈, `kr.ejg.toolbox`, Java 17. 의존성 — `io.javalin:javalin` 6.x 최신 / `com.fasterxml.jackson.core:jackson-databind`·`jackson-dataformat-yaml` 2.17.x / `com.h2database:h2` 2.3.x / `info.picocli:picocli` 4.7.x / `ch.qos.logback:logback-classic` 1.5.x(콘솔 + `logs/toolbox.log` 롤링 — `slf4j-simple` 은 둘 중 하나만 된다) / `org.apache.poi:poi-ooxml` 5.3.x(1-7 용, 오프라인 저장소를 한 번에 채우려고 지금) / test: `org.junit.jupiter:junit-jupiter` 5.11.x, `org.htmlunit:htmlunit` 4.x, `org.testcontainers:{junit-jupiter,postgresql,mariadb,mssqlserver,oracle-free}` 1.20.x. 플러그인 — compiler 17, surefire 3.5.x(`-DexcludedGroups=db` 가 JUnit5 태그로 먹는다), shade 3.6.x → `target/app.jar`, Main-Class `kr.ejg.toolbox.cli.Main`, `ServicesResourceTransformer`, `META-INF/*.SF|DSA|RSA` 제외. `project.build.sourceEncoding=UTF-8`. 버전은 `src/main/resources/version.properties`(`version=${project.version}`, 리소스 필터링 켬)에서 읽는다 — ping·`version` 명령이 같은 값을 쓴다. **번호 절차**: ① `pom.xml` ② `src/main/java/kr/ejg/toolbox/cli/Main.java` — 인자 무시하고 `toolbox-server <version.properties 값>` 출력 ③ `src/test/java/kr/ejg/toolbox/SmokeTest.java` — `assertTrue(true)` ④ `bash scripts/mvn.sh -q -B -DexcludedGroups=db test` 초록 ⑤ `bash scripts/mvn.sh -q -B -DskipTests package` → `java -jar target/app.jar` 가 버전 출력 ⑥ `bash scripts/verify.sh`. **축**: 5-11·1장(JDK 17). **강제 지점**: 빌드 자체(0-2 CI 가 매 push 에 돈다). **건드리는 자리**: 신설 셋. **닫힘**: ④⑤. **실패 사다리**: 좌표를 못 풀면 한 단계 낮은 버전(javalin 6.3.0 / testcontainers 1.19.8 / htmlunit 3.11.0) · wrapper 가 Maven zip 을 못 받으면 네트워크 문제라 멈추고 보고 · shade 서명 충돌은 위 제외 필터 | 부트스트랩 완료 | 완료 `81dc90c` |
| 0-2 | CI | **사실**: `pr-guard` 의 머지 검사가 `gh pr checks` 종료 코드를 본다 — 체크가 하나도 없으면 exit 1 이라 **CI 없이는 머지가 막힌다**. 첫 마무리 전에 있어야 한다. GitHub 러너에 Docker 가 있어 Testcontainers 가 돈다. `.github` 는 tools 레인이다. **결정**: `.github/workflows/verify.yml` — `on: [push, pull_request]`, `permissions: contents: read`, `concurrency` cancel-in-progress, job `verify`(ubuntu-latest, `actions/setup-java` temurin 17 cache maven, `timeout-minutes: 40`): ① `./mvnw -B -q verify` ② tools 레인 — `bash -n scripts/*.sh scripts/hooks/*.sh`, `node -e JSON.parse(settings.json)`, `bash scripts/hooks-test.sh` ③ docs 레인 — `bash scripts/doc-lint.sh CLAUDE.md PLAN.md PROGRESS.md README.md`. **번호 절차**: ① yml ② `node -e "require('js-yaml')"` 는 없으니 들여쓰기만 눈으로 ③ `bash scripts/verify.sh`(tools 레인 → 훅 회귀) ④ 커밋. **축**: 관례(GitHub Actions). **강제 지점**: CI 잡 — 마무리에서 처음 돈다. **건드리는 자리**: yml 신설. **닫힘**: ③ + 번들 끝 CI 초록을 이력에. **실패 사다리**: CI 에서 `oracle-free` pull 로 40분 초과 → CI 만 `-DexcludedGroups=db` 로 낮추고 이력에(full 도장은 로컬이 든다) · `hooks-test` 가 러너에서 `perl -CS` 없음 → `java-home-guard` 케이스만 건너뛰는 분기 | 0-1 | 완료 `3811259`(CI 초록은 마무리) |
| 0-3 | 프로필 | **사실**: 6장 YAML 이 스펙. Jackson YAML 로 record 매핑. **결정**: `core.profile` — `Profile` + 하위 record(`Project`·`Connection`·`Scope`(+`Exclude`·`Include`)·`Deliverable`·`Naming`·`CodeCheck`·`Output`·`Generator`·`LogicalName`). `ProfileStore.load(Path)`·`save(Profile, Path)`·`list(Path dir)`·`active()`/`setActive(name)` — 활성 프로필 이름은 `data/active-profile` 텍스트 한 줄, CLI `--profile` 인자가 있으면 그것이 우선이고 파일도 갱신. 모르는 키는 예외(`FAIL_ON_UNKNOWN_PROPERTIES` 켬 — 오타를 조용히 삼키지 않는다). `Connection` 에 비밀번호 필드 없음(규칙 2). `profiles/example.yaml` = 6장 그대로(경로는 슬래시). **번호 절차**: ① record 들 ② `ProfileStore` ③ `profiles/example.yaml` ④ `ProfileStoreTest` — load→save→load 가 `equals`; 모르는 키 파일 → 예외; `connections` 비어도 로드 ⑤ verify. **축**: 5-1·6장·규칙 2. **강제 지점**: ④ + record 에 password 필드가 없다. **건드리는 자리**: `core/profile/*` 신설, `profiles/example.yaml`. **닫힘**: ④. **실패 사다리**: record 역직렬화가 이름을 못 맞추면 `@JsonProperty` · YAML 의 `\d` 정규식이 이스케이프 문제면 작은따옴표 문자열로 | 0-1 | 완료 `58623bb` — 하위 record 는 `Profile` 안에 중첩 |
| 0-4 | 서버 기동 + ping | **사실**: Javalin 6 — `Javalin.create(cfg -> …)`, `app.start(host, port)`, 점유 시 `JavalinBindException`. 정적 파일 `cfg.staticFiles.add(s -> { s.hostedPath="/tools"; s.directory="/tools"; s.location=CLASSPATH; })`. Jackson 이 classpath 에 있으면 JSON 매퍼 자동. **결정**: `web.App` — `App.start(AppConfig)` 반환 `Javalin`. `AppConfig{port=41780, profileName, dataDir, openBrowser}`. **host 는 상수 `127.0.0.1`** — 설정에 없다(규칙·5-12). 점유 시 +1 을 10회. `GET /api/ping` → `{version, profile, mode:"backend"}`. 브라우저 오픈은 Windows `cmd /c start "" <url>`(ProcessBuilder), 테스트는 `openBrowser=false`. 로그는 `src/main/resources/logback.xml` — 콘솔 + `logs/toolbox.log`(RollingFileAppender 10MB×5, `logs` 경로는 시스템 속성 `toolbox.logDir` 기본 `logs`), 레벨 INFO, Jetty 는 WARN. **번호 절차**: ① `web/App.java`·`AppConfig`·`logback.xml` ② ping 핸들러 ③ `AppTest` — port 0 으로 띄워 `/api/ping` 200·`mode=backend`; 같은 포트로 둘째 App → 다음 포트에 뜬다 ④ verify. **축**: 규칙 1·5-12. **강제 지점**: ③ + host 가 코드 상수. **건드리는 자리**: `web/App.java`·`AppConfig.java`·`AppTest.java` 신설. **닫힘**: ③. **실패 사다리**: Javalin 이 port 0 을 안 받으면 40000~49999 에서 빈 포트를 골라 테스트 · 정적 경로가 404 면 `hostedPath` 앞 슬래시 확인 | 0-1 | 완료 `9ef33e8` — 버전 읽기는 `core.Version` |
| 0-5 | run.bat | **사실**: 현장 PC 는 일반 계정, `Program Files` 에 풀면 `data/` 쓰기 실패(12장). Windows 콘솔 기본 CP949. `-Dstdout.encoding` 은 JDK 19+ 라 17 에선 무시된다(무해). **결정**: `run.bat` — `chcp 65001 >nul`, JAVA 결정 순서 `%~dp0jre\bin\java.exe` → `%JAVA_HOME%\bin\java.exe` → `java`; `mkdir data out logs`; 쓰기 검사(`echo.> data\.w` 실패 시 한글 사유 출력 `exit /b 1`); jar 는 `%~dp0app.jar` 없으면 `%~dp0target\app.jar`; `"%JAVA%" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -jar <jar> serve %*`. **번호 절차**: ① run.bat ② `cmd //c run.bat`(Bash 에서) — 0-1 의 Main 이 인자를 무시하고 버전 출력, 종료 0 ③ 읽기 전용 검사는 수동 1회 — `data` 를 파일로 만들어 두고 실행 → 한글 사유 출력 확인, 이력에 ④ verify(레인 밖이라 안 돎 — 커밋만). **축**: 12장. **강제 지점**: 없음 — bat 한 장이고 0-11 스모크가 jar 경로를 본다. **건드리는 자리**: `run.bat` 신설. **닫힘**: ②. **실패 사다리**: `chcp` 가 출력을 깨면 뺀다 · `%*` 가 `--port` 를 못 넘기면 `%1 %2 %3 %4` | 0-1 | 완료 — 커밋은 이력 |
| 0-6 | common.js | **결정**: `src/main/resources/tools/common.js` — `window.TB = { api(path, {method, body}) → fetch+JSON, 실패 시 `{status, message}` throw; badge() → `/api/ping` 성공이면 우상단 고정 배지 「백엔드 연결 · <프로필>」, 실패(`file://` 포함)면 「순수」; table(el, columns, rows) 결과 표 렌더; sse(url, onEvent) → EventSource 래퍼 }`. 로드되면 `DOMContentLoaded` 에 `badge()` 를 스스로 건다 — 도구 HTML 은 태그 하나만 넣는다. 배지 스타일은 JS 가 주입(도구마다 `:root` 토큰이 있으니 `var(--surface)`·`var(--accent)` 만 쓴다). **번호 절차**: ① common.js ② `StaticFilesTest` — `/tools/common.js` 200, `content-type` js ③ verify. **축**: 5-10·toolbox 화면 스타일. **강제 지점**: ②. **건드리는 자리**: common.js 신설, 테스트 1. **닫힘**: ②. **실패 사다리**: 없음 | 0-4 | 완료 `dd5f6c6` |
| 0-7 | 백엔드 런처 | **결정**: `src/main/resources/tools/index.html` — 카드 데이터 `TOOLS=[{file, icon, name, desc, group, status}]` 11장(3.5 「런처 카드」): 텍스트·코드 = dev_tools·jsp_formatter·sql_snippets·special_chars·table_builder·code_check(M5) / DB·산출물 = db_browser(M1)·deliverable_sql·logical_name·program_analysis(M6)·crud_generator(M7). `status` 가 `ready` 아니면 카드 비활성 + 「M? 예정」. 설명 첫 문장에 무엇을·무엇으로·어떻게(toolbox CLAUDE 문구 규칙). `GET /` → 302 `/tools/index.html`. **번호 절차**: ① index.html ② App 에 `/` 리다이렉트 ③ `LauncherTest` — `/` 302, index 200, 본문 `data-file` 11개 ④ verify. **축**: 화면 스타일·문구 규칙. **강제 지점**: ③(카드 수). **건드리는 자리**: index.html 신설, App 한 줄, 테스트 1. **닫힘**: ③. **실패 사다리**: 없음 | 0-6 | 완료 `6823c0c` — 카드는 정적 마크업 + `data-file` |
| 0-8 | 기존 7 도구 백엔드본 | **사실**: `pure/tools/` 7 파일, 셋이 한글명. 기능 변경 없음(2.x 패치는 M2 이후). **결정**: 복사 + 영문명 — `논리명_변환기`→`logical_name.html`, `산출물_sql`→`deliverable_sql.html`, `특수문자_모음`→`special_chars.html`, 나머지 넷은 그대로. `</head>` 앞에 `<script src="/tools/common.js" defer></script>` 한 줄. **번호 절차**: ① 복사·개명 ② 태그 삽입 ③ `BackendToolsTest` — 7 경로 200, 본문에 `common.js` 태그 ④ verify. **축**: 5-10·1장 HTML 규약(나). **강제 지점**: ③. **건드리는 자리**: `resources/tools/*.html` 7 신설, 테스트 1(파일 수 상한 밖이지만 복사라 한 청크). **닫힘**: ③. **실패 사다리**: `</head>` 가 없는 파일 → `<body` 앞에 | 0-6 | 완료 `d2d8707` |
| 0-9 | 작업 큐 + SSE | **사실**: Javalin 6 `app.sse("/api/jobs/{id}/events", client -> …)`, `client.sendEvent(name, data)`, `client.keepAlive()`, `client.onClose(...)`. **결정**: `core.job` — `JobManager`(스레드풀 4), `Job{id 8자, name, status QUEUED·RUNNING·DONE·FAILED·CANCELLED, progress 0~100, message, result, events 큐}`, `submit(name, body)` 의 body 가 `JobContext{progress(p, msg), emit(name, data), isCancelled()}` 를 받는다, `cancel(id)` = 플래그 + interrupt. SSE 는 접속 시 지난 이벤트 재생 뒤 라이브, 끝나면 `done`(또는 `failed`·`cancelled`) 보내고 닫는다. `GET /api/jobs/{id}` 상태, `DELETE /api/jobs/{id}` 취소. **번호 절차**: ① Job·JobContext·JobManager ② `web/JobRoutes` ③ `JobManagerTest` — 10단계 더미(50ms 씩) 진행률 10회·DONE; 중간 cancel → CANCELLED ④ `JobSseTest` — `HttpClient` 로 events 스트림을 읽어 `progress` ≥1 과 `done` 수신 ⑤ verify. **축**: 5-4. **강제 지점**: ③④. **건드리는 자리**: `core/job/*` 3, `web/JobRoutes`, 테스트 2. **닫힘**: ③④. **실패 사다리**: SSE 테스트가 불안정하면 타임아웃 10초 + 1회 재시도를 테스트 안에 | 0-4 | 완료 — 커밋은 이력 |
| 0-10 | CLI | **결정**: picocli — `Main` `@Command(subcommands={Serve, Version})`; `serve --port --profile --data-dir --no-browser`; `version`. 0-1 의 Main 을 교체. **번호 절차**: ① `cli/Main`·`Serve`·`Version` ② `MainTest` — `version` 출력 검사; `serve --no-browser --port 0` 을 스레드에서 띄워 ping 뒤 stop ③ `java -jar target/app.jar version` ④ verify. **축**: 5-14. **강제 지점**: ②. **건드리는 자리**: cli 3, 테스트 1. **닫힘**: ②③. **실패 사다리**: shade 뒤 picocli 리소스 누락 → `ServicesResourceTransformer` 확인 | 0-4 | 완료 — 커밋은 이력. 명령 클래스는 `VersionCommand` |
| 0-11 | HtmlUnit 스모크 | **사실**: HtmlUnit 의 Rhino 가 최신 JS 문법 일부(옵셔널 체이닝 등)를 못 읽는다. **결정**: `SmokeHtmlUnitTest` — App port 0 → `/tools/index.html` + 7 도구를 `setThrowExceptionOnScriptError(true)` 로 열어 예외 0. 못 읽는 도구는 그 케이스만 JS 끄고 로드로 낮추고 파일명을 이력에. **번호 절차**: ① 테스트 ② 실행 → 실패 목록 → 사다리 ③ verify. **축**: 관례. **강제 지점**: ①. **건드리는 자리**: 테스트 1. **닫힘**: ① 초록. **실패 사다리**: Rhino 문법 오류 → 그 파일 `setJavaScriptEnabled(false)` · `common.js` 자체 오류 → 고친다 | 0-8 | 완료 — 커밋은 이력. JS 끔 2(dev_tools·logical_name) |
| 0-12 | 오프라인 빌드 | **사실**: `scripts/offline-build.sh` 는 부트스트랩에 있다(go-offline + resolve-plugins → `-o` 빌드). **결정**: 실행하고 결과를 기록하는 행. **번호 절차**: ① `bash scripts/offline-build.sh`(m2 채움, 수 분) ② `bash scripts/offline-build.sh --check` ③ `m2/` 크기를 이력에 ④ 스크립트를 고쳤으면 verify, 아니면 커밋 없음 — 이력만. **축**: 규칙 5. **강제 지점**: 없음 — 네트워크 차단은 자동화 못 한다. **건드리는 자리**: 없음(또는 스크립트). **닫힘**: ②. **실패 사다리**: `-o` 가 플러그인 좌표를 못 찾으면 그 좌표를 `pom` `pluginManagement` 에 버전 명시 | 0-1 | 완료 — 커밋은 이력. 채우기에 실제 빌드 한 번 추가 |
| 0-13 | H2 초기화 | **결정**: `core.db.Db.open(Path dataDir)` → `jdbc:h2:file:<dataDir>/toolbox`; `Migrator` — `schema_version(version, applied_at)` + `resources/db/migration/V001__init.sql` 부터 순차. V001 테이블: `dict_word(id, kind, word_ko, abbr, word_en, domain, source, created_at)` · `snapshot(id, profile, conn_id, taken_at, note)` · `snap_table(snapshot_id, schema_name, table_name, table_type, comment, row_count, created_at)` · `snap_column(snapshot_id, schema_name, table_name, name, ordinal, native_type, jdbc_type, length, precision, scale, nullable, default_value, comment)` · `snap_constraint(snapshot_id, schema_name, table_name, name, kind, columns, ref_table, ref_columns)` · `snap_index(snapshot_id, schema_name, table_name, name, is_unique, columns)` · `check_run(id, profile, started_at, path, changed_only, groups)` · `check_finding(run_id, file, line, grp, rule, severity)` — **본문 컬럼 없음**. App 기동 시 열고 두 번째 프로세스가 락에 걸리면 한글 사유 출력 후 종료. **번호 절차**: ① Db·Migrator ② V001 ③ `DbTest` — 임시 dir 열기 → version 1·테이블 존재; 다시 열기 → 재적용 없음 ④ App 배선 ⑤ verify. **축**: 규칙 3·5-1. **강제 지점**: ③ + `check_finding` 에 본문 컬럼이 없다. **건드리는 자리**: `core/db/*` 2, V001, App 한 줄, 테스트 1. **닫힘**: ③. **실패 사다리**: `SCHEMA`·`TYPE` 예약어 충돌 → 위 이름대로 접미 | 0-4 | 완료 — 커밋은 이력. `check_run.groups` → `rule_groups` |
| 1-1 | 메타모델 + Scope | **결정**: 7장 record 들 `core.meta`(`Schema`·`Table`·`Column`·`PrimaryKey`·`ForeignKey`·`Index`). `Scope.accepts(Table)` — `include.tables` 가 있으면 그 목록만(제외 무시), 없으면 exclude(접두·접미·정규식·목록) 적용; `skipEmpty` 는 `rowCount==0` 만 제외(`null` 은 유지). 프로필 `Scope`(0-3) 를 이 타입으로 통일. **번호 절차**: ① records ② Scope ③ `ScopeTest` 6케이스(include 우선·접두·접미·정규식·목록·skipEmpty null) ④ verify. **축**: 7장·2.2 scope. **강제 지점**: ③. **건드리는 자리**: `core/meta/*` 7, 테스트 1. **닫힘**: ③. **실패 사다리**: 없음 | 0-3 | 완료 — 커밋은 이력. `UniqueKey` 더함 |
| 1-2 | JdbcMetaSource + PG 골든 | **사실**: `postgres:17-alpine` 이 로컬에 있다(pull 없음). `DatabaseMetaData` — getTables·getColumns(REMARKS 에 코멘트, pgjdbc 가 채운다)·getPrimaryKeys·getImportedKeys·getIndexInfo(PK 인덱스가 섞여 온다). **결정**: `MetaSource` 인터페이스(7장) + `JdbcMetaSource(Connection)`. 샘플 DDL `src/test/resources/sample/postgres.sql` — 8 테이블(users·orders·order_items·products·categories·code_groups·codes·logs), PK·FK·UNIQUE·인덱스·코멘트 포함. 골든 `src/test/resources/golden/meta/postgres.json`(Jackson pretty, 키 정렬). `GoldenFiles.assertMatches(name, obj)` — `-Dgolden.update=true` 면 쓰고, 아니면 비교해 diff 출력. 테스트는 `@Tag("db")`. **번호 절차**: ① MetaSource·JdbcMetaSource ② sample sql ③ GoldenFiles ④ `JdbcMetaSourcePostgresTest`(Testcontainers) — 첫 실행 update 로 골든 생성 → 둘째 실행 비교 ⑤ `bash scripts/mvn.sh -q -B -Dgroups=db test` 초록 ⑥ verify. **축**: 5-2·5-13. **강제 지점**: ④ 골든. **건드리는 자리**: `core/meta/MetaSource`·`JdbcMetaSource`, 테스트 2 + 리소스 2. **닫힘**: ⑤⑥. **실패 사다리**: getIndexInfo 의 PK 인덱스 → PK 이름과 같은 인덱스 제외 · Docker 안 뜨면 `docker-up.sh` 3분 → 그래도면 행을 wip/ 로 | 1-1, 0-13 | 완료 — 커밋은 이력. Testcontainers 1.21.4 |
| 1-3 | 벤더 MetaSource 5종 | **사실**: 이미지 — `mariadb:11`, `mcr.microsoft.com/mssql/server:2022-latest`(`acceptLicense()` 필요, 메모리 2GB), `gvenzl/oracle-free:23-slim-faststart`(`org.testcontainers:oracle-free` 의 `OracleContainer`, 기동 3~5분). Tibero 이미지 없음. 벤더 SQL 원본은 `pure/tools/산출물_sql.html` 의 방언별 쿼리와 `db_docs/README.md` 「확인된 사실」(`LAST_DDL_TIME` 신뢰 불가 → `lastDdlAt` 은 null). **결정**: `OracleMetaSource`(ALL_TAB_COMMENTS·ALL_COL_COMMENTS·ALL_TABLES.NUM_ROWS·ALL_OBJECTS.CREATED) · `TiberoMetaSource extends OracleMetaSource`(차이 나는 것만 override, 테스트 없음) · `PostgresMetaSource`(pg_description·pg_class.reltuples) · `MariaMetaSource`(information_schema TABLE_COMMENT·COLUMN_COMMENT·TABLE_ROWS·CREATE_TIME) · `MssqlMetaSource`(sys.extended_properties MS_Description·sys.partitions·sys.tables.create_date). `MetaSources.forDialect(dialect, conn)`. 접속 시 `getDatabaseProductVersion()` 을 `Table` 이 아니라 `Schema.dbVersion` 에 담는다(7장 버전 방침). **번호 절차**: ① 5 클래스 + 팩토리 ② sample DDL 3벌(mariadb·mssql·oracle; PG 는 1-2 것) ③ 테스트 4개(db) → 골든 4벌 ④ `-Dgroups=db test` 초록 ⑤ verify. **축**: 5-2·7장. **강제 지점**: ③ 골든 4. **건드리는 자리**: `core/dialect/*` 6, 테스트 4 + 리소스 7(복사·골든이라 한 청크). **닫힘**: ④. **실패 사다리**: pull 30분 초과 → 그 벤더 테스트 `@Disabled("이미지 pull 대기")` + 이력, 아침에 사람이 켠다 · mssql 메모리 부족 → 같은 처리 · oracle 기동 느림 → `withStartupTimeout(Duration.ofMinutes(10))` | 1-2 | 미착수 |
| 1-4 | 접속 관리 | **결정**: `core.conn` — `DriverLoader`(`drivers/` 의 jar 를 `URLClassLoader` 로 열고 `DriverShim` 으로 `DriverManager` 에 등록; classpath 드라이버도 그대로) · `ConnectionRegistry`(프로필 접속 목록 + `Map<String,char[]>` 비밀번호 메모리, `setPassword`·`open(id)`·`test(id)` → `{ok, productName, productVersion}`; 테스트 SQL 은 방언별 `SELECT 1`/`SELECT 1 FROM DUAL`). 라우트 `POST /api/conn/{id}/password {password}` · `POST /api/conn/{id}/test`. 비밀번호는 로그에 안 찍는다 — 예외 메시지도 마스킹. **번호 절차**: ① DriverLoader·DriverShim ② ConnectionRegistry ③ `web/ConnRoutes` ④ `ConnectionRegistryTest` — H2 in-memory 로 open·test(`h2` 는 테스트 전용 dialect 분기), 틀린 비밀번호 → `ok=false` 이고 캡처한 로그·응답 본문에 비밀번호 문자열 없음 ⑤ PG 컨테이너로 `test` 1케이스(db) ⑥ verify. **축**: 규칙 2·11장 drivers. **강제 지점**: ④ 로그 검사. **건드리는 자리**: `core/conn/*` 3, 라우트 1, 테스트 1. **닫힘**: ④. **실패 사다리**: `DriverShim.getParentLogger` 미구현 예외 → 구현 · `drivers/` 없으면 조용히 건너뜀 | 0-3 | 미착수 |
| 1-5 | 스냅샷 저장·조회 | **결정**: `core.meta.SnapshotStore(Db)` — `save(profile, connId, List<Schema>)` → id · `list()` · `get(id)` → schemas · `getTable(id, schema, name)`. `SnapshotService` = 작업(0-9): 접속(1-4) → `MetaSources.forDialect`(1-3) → `Scope`(1-1) → save. 라우트 `POST /api/meta/snapshot {connId}`(job) · `GET /api/meta/snapshots` · `GET /api/meta/snapshots/{id}/tables` · `GET /api/meta/snapshots/{id}/tables/{name}`. **번호 절차**: ① SnapshotStore ② SnapshotService ③ `web/MetaRoutes` ④ `SnapshotStoreTest` — 메타모델 픽스처 save → get 이 `equals`(임시 H2) ⑤ verify. **축**: 5-3. **강제 지점**: ④. **건드리는 자리**: `core/meta/SnapshotStore`·`SnapshotService`, 라우트 1, 테스트 1. **닫힘**: ④. **실패 사다리**: `columns` 목록을 한 컬럼에 담을 때 구분자 충돌 → JSON 문자열로 | 1-3, 1-4, 0-13 | 미착수 |
| 1-6 | 스냅샷 diff | **결정**: `SnapshotDiff.compare(a, b, ignoreSchema=true)` → `{addedTables, removedTables, changedTables[{name, addedColumns, removedColumns, changedColumns[{name, field, before, after}], addedConstraints, removedConstraints}]}`. 키는 테이블명(스키마 무시가 기본 — 개발 `DEV`·운영 `PRD` 처럼 접속마다 스키마명이 다르다). `GET /api/meta/diff?a=&b=&ignoreSchema=`. **번호 절차**: ① SnapshotDiff ② 라우트 ③ `SnapshotDiffTest` 4케이스 골든 — 컬럼 추가·삭제·타입 변경·스키마명만 다른 두 스냅샷(접속 다름) ④ verify. **축**: 5-3·3.5 DB 간 비교. **강제 지점**: ③. **건드리는 자리**: `SnapshotDiff`, 라우트 한 줄, 테스트 1 + 골든 4. **닫힘**: ③. **실패 사다리**: 없음 | 1-5 | 미착수 |
| 1-7 | SQL 실행 + xlsx | **결정**: `core.sqlrun.SqlRunner.run(conn, sql, binds, maxRows=1000, timeoutSec=60)` → `ResultTable{columns[{name, type}], rows, truncated, elapsedMs}`. 값은 날짜 ISO 문자열, BLOB 은 `(BLOB n bytes)`. 문장은 그대로 던진다(분리 안 함). 로그에 SQL·결과 안 남김(규칙 3). `core.report.XlsxWriter.write(ResultTable, Path)`(POI). 라우트 `POST /api/sql/run {connId, sql, binds, maxRows}` · `POST /api/sql/export {…, format: xlsx|csv}` → `out/<프로필>/<yyyyMMdd-HHmmss>/result.<ext>` 경로 응답. **번호 절차**: ① SqlRunner ② XlsxWriter ③ `web/SqlRoutes` ④ `SqlRunnerTest`(H2 in-memory — select·바인드·maxRows 초과 truncated·타임아웃 예외) ⑤ `XlsxWriterTest`(쓰고 POI 로 다시 읽어 셀 비교) ⑥ verify. **축**: 2.1 부산물·2.5·5-7·12장 출력 폴더. **강제 지점**: ④⑤. **건드리는 자리**: `core/sqlrun/*`·`core/report/XlsxWriter`, 라우트 1, 테스트 2. **닫힘**: ④⑤. **실패 사다리**: `setQueryTimeout` 미지원 드라이버 → 무시 · POI 가 shade 에서 `META-INF/versions` 충돌 → `multiRelease` 켬 | 1-4 | 미착수 |
| 1-8 | DB 브라우저 화면 | **결정**: `resources/tools/db_browser.html` — 왼쪽: 프로필 선택(`GET /api/profiles`)·접속 목록·비밀번호 입력·「접속 시험」; 가운데: 스냅샷 목록·「스냅샷 찍기」(job → `TB.sse` 진행률)·테이블 목록(검색)·테이블 상세(컬럼·제약·인덱스); 아래: diff(스냅샷 둘 고르기 → 결과 표). 프로필 라우트가 없으면 여기서 `web/ProfileRoutes`(`GET /api/profiles`·`GET /api/profiles/{name}`·`POST /api/profiles/active`). 런처 카드 `db_browser` 를 `ready` 로. 설명 첫 문장 무엇을·무엇으로·어떻게. **번호 절차**: ① html ② ProfileRoutes ③ 런처 카드 상태 ④ `SmokeHtmlUnitTest` 목록에 추가 ⑤ verify. **축**: 화면 스타일·문구 규칙. **강제 지점**: ④. **건드리는 자리**: html 신설, 라우트 1, index.html 한 줄, 테스트 한 줄. **닫힘**: ④. **실패 사다리**: Puppeteer 스크린샷은 아침에 사람이(밤엔 생략) · HtmlUnit 이 `EventSource` 를 모르면 `TB.sse` 를 `typeof EventSource` 가드 | 1-6, 1-7, 0-7 | 미착수 |
| 0-14 | run.bat java 버전 사유 | **사실**(0-5 에서 드러남): `JAVA_HOME` 이 11 이면 `run.bat` 이 그 java 를 집고 영어 `UnsupportedClassVersionError` 로 끝난다 — 현장 사용자가 원인을 못 읽는다. **결정**: 기동 전 `"%JAVA%" -version 2>&1` 의 첫 줄에서 주 버전을 뽑아 17 미만이면 한글 사유(「java 17 이상이 필요하다. jre\ 를 넣거나 JAVA_HOME 을 17 로」) + `pause` + `exit /b 1`. **번호 절차**: ① run.bat ② JAVA_HOME=11 로 실행 → 한글 사유 ③ JAVA_HOME=17 → 버전 출력 ④ 커밋(레인 밖). **축**: 12장. **강제 지점**: 없음 — bat. **건드리는 자리**: run.bat. **닫힘**: ②③. **실패 사다리**: `1.8.0_x` 형식은 둘째 토큰 `8` 로 | 0-5 | 미착수 |
| 0-15 | 반입 묶음에 Maven 배포본 | **사실**(0-12 에서 드러남): `mvnw` 는 첫 실행에 `distributionUrl`(Maven 3.9.9 zip)을 `~/.m2/wrapper/dists` 로 받는다. 11장 묶음엔 `m2/` 만 있어 현장에서 `mvnw -o` 가 배포본을 못 받아 멈춘다. **결정**(설계 확인 필요): 묶음에 `maven/apache-maven-3.9.9-bin.zip` 을 넣고 `scripts/offline-build.sh --check` 가 `MAVEN_USER_HOME` 을 묶음 안 폴더로 돌려 zip 을 먼저 풀어 두게 한다. 11장 목록에 한 줄. **번호 절차**: ① 스크립트 ② `MAVEN_USER_HOME` 을 빈 임시 폴더로 두고 네트워크 없이 `--check` 초록 ③ 11장 ④ verify. **축**: 규칙 5·11장. **강제 지점**: ②. **건드리는 자리**: `scripts/offline-build.sh`, PLAN 11장. **닫힘**: ②. **실패 사다리**: wrapper 가 로컬 zip 을 못 쓰면 `distributionUrl` 을 `file:` 로 바꾼 사본 properties | 0-12 | 미착수 |
| 0-16 | PR AI 리뷰 | **사실**: ProjectShop `claude-review.yml` 이 2g~2g-7 에서 실측으로 다듬어져 있다(`--setting-sources user` 로 훅 끔, 서브에이전트 금지, `gh` 셋만 허용, 이번 회차 코멘트 확인). 구독 토큰(`CLAUDE_CODE_OAUTH_TOKEN`)으로 돌아 별도 과금이 없다. CodeQL 은 private 저장소에서 유료라 안 옮긴다. **결정**(2026-09-26 사용자 — 「클로드 코드 토큰 쓰는 거면 해도 된다」): `.github/workflows/claude-review.yml` — 설정은 ProjectShop 판 그대로, 프롬프트는 이 저장소 문서(CLAUDE.md·PLAN 분할표·PROGRESS 이력)와 강제 지점 순서(타입·코드 상수 > 스키마 > 테스트 > 훅 > 문서)로. 리뷰는 조언, 잡이 빨개지는 것은 「이번 회차 코멘트 없음」뿐. **번호 절차**: ① yml ② verify(tools 레인) ③ 사람이 시크릿 등록 ④ 다음 PR(번들 2)에서 코멘트 확인. **축**: 관례(ProjectShop 이관). **강제 지점**: 확인 step. **건드리는 자리**: yml 신설. **닫힘**: ④. **실패 사다리**: 코멘트 0 이면 `show_full_output: true` 로 한 회차만 켜서 거부 도구를 읽고 끈다 | 0-2 | 진행 — yml 은 이력, 시크릿 대기 |
| 0-17 | CodeQL | **사실**: CodeQL 은 public 저장소에서 무료다. 2026-09-26 사용자 결정으로 저장소를 public 으로 돌렸다(전환 전 이력 전체를 훑어 비밀·사설 IP·메일·실사업 파일 없음 확인). **결정**: ProjectShop `codeql.yml` 이관 — push 마다 + 주 1회 + 수동, 언어 actions·java-kotlin·javascript-typescript(전부 `build-mode: none`), `paths-ignore: pure·m2·target`, 질의 묶음 기본값. ProjectShop 의 `JdbcClient` 싱크 확장은 안 옮긴다(Spring 용). **번호 절차**: ① yml ② verify(tools 레인) ③ push 뒤 CodeQL 잡 셋 초록 ④ Security 탭 경고 수를 이력에. **축**: 관례(ProjectShop 이관). **강제 지점**: 없음 — CodeQL 경고는 잡을 안 빨갛게 한다. 처분은 사람·이력. **건드리는 자리**: yml 신설. **닫힘**: ③④. **실패 사다리**: java `build-mode: none` 이 추출을 못 하면 `autobuild` + setup-java 17 · 1-7 SQL 실행의 SQL 주입 경고는 기능이라 dismiss | 0-2 | 완료 `ec85da9` — 첫 실행 잡 셋 초록, 열린 경고 0 |
| 0-18 | gitleaks + CI 이벤트 | **사실**: ProjectShop `ci.yml` 의 `secrets` 잡(gitleaks-action@v3, 이력 전체)이 가지 보호 필수 검사다. 이 저장소가 2026-09-26 public 이 됐다. `verify.yml` 이 `[push, pull_request]` 라 같은 커밋이 두 번 돈다. **결정**(2026-09-26 사용자 — ProjectShop 도구 추천 순서대로): `verify.yml` 에 `secrets` 잡, 트리거는 ProjectShop 처럼 `push` + `workflow_dispatch`. 저장소 설정으로 secret scanning·push protection·Dependabot 경보를 켜고(자동 수정 PR 은 끔 — 갱신마다 `m2/` 재충전), main 가지 보호(필수 `verify`·`secrets`, strict, 관리자 포함, PR 필수·승인 0, 강제 push·삭제 금지). **번호 절차**: ① 설정 ② yml ③ push 뒤 `secrets` 초록 ④ 필수 검사에 `secrets` 추가. **축**: 관례(ProjectShop 이관). **강제 지점**: 가지 보호. **건드리는 자리**: `verify.yml`. **닫힘**: ③④. **실패 사다리**: gitleaks 오탐 → `.gitleaks.toml` 에 값 하나씩 허용(경로 허용 금지) | 0-2 | 진행 — 커밋은 이력 |
| 0-19 | ArchUnit | **사실**: 5-11 「core 는 web·cli·Javalin 을 import 하지 않는다」와 절대 규칙 1 「외부 통신 0」이 글로만 있다. ProjectShop 은 `ArchitectureTest` 로 막는다. **결정**: test 의존성 `com.tngtech.archunit:archunit` 1.4.1(JUnit 5.11 이라 junit 엔진 모듈 없이 일반 `@Test`). 규칙 셋 — core ↛ web·cli·`io.javalin` / web ↛ cli / 운영 코드 ↛ `java.net.http`·`javax.net.ssl`·`URLConnection`·`HttpURLConnection`·`Socket`·`DatagramSocket`·`SocketChannel`(서버 쪽 `ServerSocket`·`InetSocketAddress`, 리소스 `URI`·`URL` 은 허용). **번호 절차**: ① 의존성 ② `ArchitectureTest` ③ 초록 ④ 위반 클래스를 임시로 넣어 빨강 확인 후 지움 ⑤ verify. **축**: 5-11·규칙 1. **강제 지점**: ②. **건드리는 자리**: pom, 테스트 1. **닫힘**: ③④. **실패 사다리**: 1.4.1 좌표를 못 풀면 1.3.0 | 0-1 | 완료 — 커밋은 이력 |
| 0-20 | 마이그레이션 불변 | **사실**: 0-13 에서 H2 `V001` 이 생겼다. 반입 뒤 V 파일을 고치면 현장 `schema_version` 과 어긋난다. ProjectShop `migration-immutable.sh`(Q51)가 기준점 파일 방식으로 막는다. **결정**: 이 도구의 「배포」는 반입 — 기준점 `src/main/resources/db/released-baseline`(주석 허용, 첫 줄 커밋), 없으면 통과. `verify.sh` java 레인과 CI `verify` 잡에서 돈다(CI checkout `fetch-depth: 0`). 반입 전날 체크리스트에 한 줄. **번호 절차**: ① 스크립트 ② verify.sh·verify.yml ③ 상태 넷(기준점 없음·수정 없음·수정·없는 커밋) 확인 ④ 체크리스트 ⑤ verify. **축**: 0-13·규칙 없음(운영 안정). **강제 지점**: 스크립트(verify·CI). **건드리는 자리**: 스크립트 신설, verify.sh 두 줄, verify.yml. **닫힘**: ③. **실패 사다리**: 없음 | 0-13 | 완료 — 커밋은 이력 |

반입 전날:
- [ ] 네트워크 끊고 `mvn -o` 빌드 성공
- [ ] 새 PC(또는 VM)에서 zip 풀고 `run.bat` 기동 → 런처 열림
- [ ] Tibero 드라이버·라이선스 파일 확인
- [ ] 외부 호출 0 확인 — 기동 후 netstat 에 `127.0.0.1:<port>` 외 없음
- [ ] 테스트 코퍼스·`.git` 이 zip 에 안 들어갔는지
- [ ] `src/main/resources/db/released-baseline` 에 반입 커밋을 적는다(첫 반입 때 만든다) — 이 뒤로 `migration-immutable.sh` 가 V 파일 수정·삭제를 막는다(0-20)
- [ ] 라이선스 — SVNKit(TMate) 사내 배포 조건 확인. 걸리면 `svn` 명령줄 호출로 대체. JSqlParser 는 Apache 2.0 선택
- [ ] 현장 첫날 — jar 실행 가능 여부(AppLocker). 동봉 JRE 가 막히고 현장 JDK 가 8 이면 방법 없음. 가장 먼저 확인

`[미정]` 반입 시점.
