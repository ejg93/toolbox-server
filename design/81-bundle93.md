# 설계 81 — 번들 93: 번들 92 마무리 리뷰 행 여섯 + G4-C1 (Fable 설계 · Opus 실행, 2026-11-25)

## 맥락

번들 92(#137 `467a965`)의 마무리 리뷰가 남긴 대기 행 여섯(`0-132`·`6-103`·`6-104`·`6-105`·`5-159`·`1-139`)과 설계 78 4절의 후보 `G4-C1`(링크가 가리키는 UNC 를 닿은 뒤 거절 — 규칙 1)을 한 PR 로 닫는다. 실행은 무인이다. 점검 5차는 이 번들이 머지된 뒤 92~93 diff 로 따로 한다.

**사용자 결정(고정 — 다시 묻지 않는다. 메모리 `bundle93-decisions` + 2026-11-25 답 둘)**
1. 범위 — 위 일곱만. 뺌: 점검 5차 · 설계 78 4절 나머지 후보 · 순수본 재주입(`5-100`·`5-31`·`5-134`) · 반입 zip · 조건부 `5-14`·`5-15`·`6-9`
2. Fable 설계 → Opus 「번들 해」 · **머지 위임** · 시간 컷 없음 · RAM 조율 불요(컴퓨터 계속 켬) · 무거운 판은 `--corpus` → `--db` 하나씩
3. **G4-C1 은 같은 꼴 자리 전부**(답 (가)) — 폴더·파일 칸 입력(`LocalFiles.check`) + 결과 쓰기 + `.git`·`.svn` 표식·가드가 읽는 파일·변경분 파일 크기 + 명령줄 폴더. 「이 컴퓨터 안만」 을 지키는 **거절** 검사다(로컬 밖을 쓰게 하는 기능이 아니다)
4. **저장소 설정 값의 매핑된 네트워크 드라이브(`Z:/…`)도 이번에 거절**(답 (가)) — `0-132a` 에 싣는다

**보정 A — 일곱 행의 사실**(2026-11-25, 트리 `7d4a964` = 코드 `69f9182`. 탐침은 jshell 표준입력 + `target/app.jar` 01:55 빌드 = HEAD 코드 — 파일을 쓰지 않았다. 번들 92 이력의 「정한 것(계획 밖)」 열둘과 리뷰 고침 `69f9182` 는 이 일곱의 사실을 안 바꿨다)

| 행 | 판정 | 재현(지금 출력) |
|---|---|---|
| `0-132` 하위 `.git`·alternates 사슬·bare 꼴 | 확인(코드) | `GitConfigGuard.check:45-48` 은 첫 단 alternates 줄만 잰다 · `network("\"\\057\\057h/s\"")` → 거짓(탐침) · `WorkingCopy.dotGit:158-166` 은 꼭대기 `.git` 하나 · bare 꼴은 `Cli` 의 `safe.bareRepository=explicit` 뿐(옛 git 은 모른다) |
| `G4-C1` | 확인(코드 — 네트워크라 추적만) | `LocalFiles.refuseForbiddenReal:178·185` 의 `Files.exists`·`toRealPath` 가 링크를 먼저 따라간다. 대상이 없는 링크는 `exists` 거짓 → 조상으로 올라가 **통과**. 이 PC 는 L2R 링크 평가 켜짐 · 심볼릭 링크 만들 권한 없음 |
| `6-103` 색인 밖 인터페이스 | 확인(표본) | shopizer `ResetCustomerPasswordApi:45`(완전 이름 필드)·`ShoppingCartApi:40·68`(명시 import) → `…facade.v1.CustomerFacade` 는 표본에 없고 `store/facade/customer/CustomerFacadeImpl:33·41` 이 그 import 로 `implements`. `store/facade/**` 구현 스물의 인터페이스가 전부 표본 밖(sm-shop-model) |
| `6-104` ① 감싸개 둘째 호출 | 확인(탐침) | `dao.selectList("user." + t, null); dao.selectList("dept." + t, null);` → 문장 `[user.]` 하나 · `selectList(a); selectList(b);` → `statement` 미해결 한 줄 |
| `6-104` ② QueryDSL import | 확인(탐침) | 엔티티 `domain.Member` + 저장소 `import app.dto.Member` + `selectFrom(QMember.member)` → 문장 0 · `querydsl` 미해결(import 를 빼면 `…list#qdsl`) |
| `6-105` ① FROM 목록 안 분기 | 확인(탐침) | `… ON ␁ a.X = b.X ␁ a.Y = b.Y` → 표 `X`·`Y` · `… ON a.ID = b.ID ␁ , T3 c` → `T3` 빠짐 + `table` 미해결. 더 넓다: `FROM T1 a ␁ , T3 c ␁ , T4 d` 도 `T3`·`T4` 빠짐 · `␁ a.X <> b.X`·`␁ a.Y IS NULL`·`␁ X = 1`·`␁ a.K LIKE …` 도 표를 지어낸다 |
| `6-105` ② 분기 사이 별칭 | 확인(탐침) | `␁ FROM EMP JOIN DEPT d ON EMP.D_ID = d.ID ␁ FROM EMP_HIST EMP JOIN DEPT d ON EMP.D_ID = d.ID` → 조인 `DEPT.ID—EMP_HIST.D_ID` 하나(기대 둘) |
| `5-159` ① script 안 태그 꼴 | 확인(탐침) | `<script>⏎for (var i = 0; i<max; i++) { } // can't⏎</script>⏎<textarea>⏎console.log(1);⏎</textarea>⏎<p title='x'>y</p>` → `rawLines` 전부 거짓(jsp·html. 기대 3~5줄 참). 따옴표가 짝이 맞으면 정상 |
| `5-159` ② 가린 이름 | 확인(탐침) | 필드 초기화식 람다 매개변수 · 메서드 안 람다 매개변수 · catch 매개변수 · 익명 클래스 필드 · record 구성요소 · 같은 파일 무관한 클래스의 상수(조상이 파일 밖 / 조상 없음) — 여섯 다 `java.equalsLiteralFirst` 1 건(기대 0) |
| `1-139` 반영 DDL 의 자동증가 부모 | 확인(탐침) | oracle → mariadb·postgresql·mssql: 새 표·ADD COLUMN 의 FK 자식이 `DECIMAL(38,0)`·`NUMERIC(38,0)`(부모는 자동증가 정수) |

**보정 B — 행의 글과 처방이 달라지는 자리**(근거는 3절)
- `6-103` — 행의 「…등록하거나, 미해결 한 줄」 가운데 **구현 등록**. 미해결 줄은 맞는 종류가 없고(`Unresolved.KINDS` 는 안 늘린다) 등록 뒤 남는 것은 진짜 프로젝트 밖 형식이라 소음이다. 등록은 **패키지 앞 두 마디가 같은 인터페이스만**(`Serializable`·`Runnable` 같은 JDK·프레임워크 인터페이스에 구현 수백을 걸지 않는다)
- `6-104` ① — 열쇠에 인자 식 자리만 더한다. 행의 「낱말 리터럴 가지는 sink 이거나 `.` 든 글일 때만」 은 **안 한다** — 점 없는 문장 id(iBATIS 맨 id)를 가운데 층이 넘기는 꼴의 둘째 문장이 다시 빠진다. 설계 79 의 사다리(표본에서 `depth` 가 늘 때만) 그대로 둔다
- `6-105` ② — 행의 「자리 앞 가장 가까운 정의가 자기 정의면 그것을」 은 `UPDATE a SET a.X = … FROM T1 a`(6-99 가 고친 꼴)를 되돌린다. **UPDATE 대상 자리의 자기 정의만 별칭 정의에 지게** 한다(FROM·JOIN 자리의 자기 정의는 표다)
- `5-159` ① — `quietMarkup` 과 `blankScripts` 의 차례를 바꾼다(script 몸을 먼저 비운다). 따옴표 짝을 줄 안으로 묶는 쪽은 여러 줄 속성 값을 놓쳐 버렸다
- `5-159` ② — 「다른 파일 조상 필드」 는 못 본다(파일 밖). 고치는 것은 「같은 파일 아무 클래스의 상수로 떨어지는」 쪽 — 같은 파일에 선언된 조상의 필드만 받는다

**드러난 것(이 번들에 실음)** — `GitConfigGuard.network` 가 `//`·`\\` 머리만 본다 → 매핑된 네트워크 드라이브 값이 지난다(사용자 결정 4 → `0-132a`) · `WorkingCopy.detect`·`dotGit` 의 `Files.exists(….git)` 와 가드의 `Files.isRegularFile` 이 링크를 따라간다(사용자 결정 3 → `0-132a`)

## 1. 목표와 범위

목표 — 번들 92 마무리 리뷰의 남은 여섯과 G4-C1 을 코드 행 12 개 · PR 하나로 닫는다. 사용자가 겪는 변화:
- **절대 규칙 1** — 네트워크를 가리키는 심볼릭 링크를 따라가기 **전에** 거절한다(지금은 접속이 한 번 나간 뒤 거절, 대상이 없으면 통과) · 받은 소스의 `.git` 이 2단 alternates·따옴표 친 8진 탈출·매핑 드라이브 값·하위 폴더 `.git` 파일로 네트워크를 가리키면 git 조회를 안 한다 · 검사 폴더가 git 저장소 폴더 꼴이면 조회를 안 한다
- **프로그램 분석** — 분석 폴더 밖 모듈의 인터페이스를 구현한 클래스로 호출이 이어진다(shopizer 의 `…Facade` 스물 — 지금은 그 아래 문장이 흔적 없이 빠진다) · 한 메서드가 공통 DAO 를 접두 id 로 두 번 부르면 둘 다 나온다 · 엔티티와 이름이 같은 DTO 를 import 한 저장소의 QueryDSL 문장이 나온다 · `ON <when>…` 조건의 컬럼을 표로 지어내지 않고 `<when> , T3 c` 의 표를 읽는다 · 분기마다 같은 이름이 표·별칭으로 갈린 조인이 분기대로 나온다
- **코드 정리** — script 의 `i<max … '` 뒤에 있는 `<textarea>`·`<pre>` 가 다시 날글 구역이 된다(그 안 console 을 안 지운다) · 람다·catch 매개변수·익명 클래스 필드·record 구성요소로 가려진 이름을 상수로 보고 `equals` 를 뒤집지 않는다
- **반영 DDL** — 새 표·ADD COLUMN 의 FK 자식이 자동증가 부모와 같은 정수 타입으로 나간다(FK 추가 실패가 없어진다)

In scope — 코드 행 12(`0-133a`·`0-133b`·`0-132a`·`0-132b`·`5-159a`·`5-159b`·`6-105a`·`6-105b`·`6-104a`·`6-104b`·`6-103`·`1-139`) + `S0`·`V-72a`·`R-39`·`V-72`

Out of scope
- 사용자 결정 1 의 뺀 것 · 행마다 「한계」 줄
- 새 라우트·화면·규칙·마이그레이션·의존성 0. 새 파일은 `core/check/NameScope.java` 하나. 응답 JSON·미해결 종류·기존 사유 글자 그대로(새 사유 글은 `BARE_SHAPE` 하나)
- 순수본(`pure/`)·`rules.yaml`·`pure-rules.json` — 안 고친다

## 2. 현재 구조 요약

행마다 「사실」 줄이 지금 자리(줄 번호 `69f9182`)다. 여러 행에 걸치는 것만 둔다.

- **fs 경로 검사** — 사용자 경로는 전부 `LocalFiles.check(raw)` 를 지난다(글자로 UNC·장치·매핑 드라이브 거절 → 금지 폴더 → `refuseForbiddenReal`). 목록 걷기(`list`)는 링크·정션 폴더를 `PathRules.linked` 로, 링크 파일을 `!a.isRegularFile()` 로 이미 건너뛴다(속성만 본다 — 안 따라간다). `core.vcs`·`cli` 는 공개 `LocalFiles.network(String)` 을 쓴다. `core.vcs` → `core.fs` import 는 지금 없다(더해도 고리 아님 — `core.fs` 는 `core.vcs` 를 안 쓴다)
- **git 가드** — `WorkingCopy.require`(→ `changed`·`diff`)·`recent` 가 `GitConfigGuard.check(dotGit(root))` 를 부른 뒤 git 을 띄운다. `detect` 는 `git --version` 만(저장소를 안 읽는다). 라우트는 `files.check(path)` 를 먼저 지난 `root` 를 넘긴다(`CheckRoutes:224·264·403` · `CleanupRoutes:83`)
- **JavaGraph 구현 색인** — `run:224-230` 이 `interfacesOf`(색인 안 인터페이스 FQCN)마다 `implementors` 에 구현을 건다. `resolve:648-673` 이 필드 타입 후보(명시 import 가 있으면 그 완전 이름과 같은 것만 — 6-93a)를 구현으로 바꾼다. 후보가 없으면 빈 목록 → 그 호출은 조용히 안 따라간다
- **SqlTables 분기** — `MapperIndex` 가 `<when>`·`<otherwise>` 머리에 `BRANCH`(`\u0001`) 낱말을 넣는다(끝 표시는 없다). 표 자리를 분기가 다시 여는 길은 셋 — `alias` 의 `ALIAS_OK`·`AFTER_ALIAS`·닫힌 상태의 `reopen`(셋 다 `sc.byBranch = true`)
- **self-check 기준선**(`golden/check/self-check.txt`) — 이 번들이 건드리는 파일의 줄: `JavaGraph` 메서드 34·`run(2)` 31·`querydsl(3)` 22·`walk(5)` 42·`index(4)` 21·`entityManager(4)` 19·`repoCall(8)` · `JpaIndex` 메서드 38 · `SqlTables.extract(2)` 17 · `AlterGen.Run` 메서드 40·`modifyColumn(7)` 38 · `DdlGen.table(1)` 16·`type(2)` 16 · `LocalFiles.list(4)` 21·`resultRoot(5)` 22. **줄 없음**(상한 — 복잡도 15 · 길이 100 · 중첩 4 · 인자 7 · 클래스 메서드 30): `PathRules` · `GitConfigGuard` · `WorkingCopy` · `Batch` · `MakeTemplates` · `TypeBind`(메서드 22) · `StmtArgs` · `JpqlPaths` · `SqlJoins` · `Zones` · `ValueAst`(메서드 27)

**함정**
- 기준선 줄 있는 메서드에 분기·줄을 더하면 빨강 — 식을 도우미 호출로 바꾼다. `JavaGraph`·`JpaIndex`·`AlterGen.Run` 에 메서드를 못 더한다(→ `TypeBind`·`JpqlPaths`·바깥 `AlterGen` static)
- **이 PC 는 심볼릭 링크를 못 만든다**(개발자 모드 꺼짐) — 링크 시험은 가짜 `LinkReader` 로 RED 를 보고, 실물 링크 시험은 리눅스(CI)에서만 돈다. Windows 에서 `Files.createSymbolicLink(…, \\서버\…)` 는 JDK 가 대상 속성을 먼저 물어 **네트워크에 닿는다 — 쓰지 않는다**
- 리눅스에서 `Path.of("//h/s")` 는 `/h/s` 로 접힌다 — 실물 링크의 네트워크 꼴 대상은 역슬래시 글 `\\h\s\x`(리눅스에선 그 이름의 상대 경로 — 끊긴 링크)로 만든다. `LocalFiles.network` 는 글자 판정이라 리눅스에서도 참
- 시험·끝에서 끝에 쓰는 네트워크 꼴 글은 `//127.0.0.1/tb-none/…` — 가드가 새도 루프백만 닿는다. **가드 시험은 git 을 띄우지 않고 가드 메서드를 직접 부른다**(RED 때 git 이 그 경로를 열지 않게)
- `SqlTables.BRANCH` 는 낱말 하나 — 시험의 `B` 는 `String.valueOf(SqlTables.BRANCH)` · 골든 JSON(`sql-tables.json`·`sql-joins.json`)은 `-Dgolden.update=true` 로 새 열쇠만
- SpotBugs REDOS(묶음 반복 금지) · PMD `AvoidReassigningLoopVariables`(`for` 변수를 몸에서 안 바꾼다 — `while`) · ErrorProne raw 형식 · Bash heredoc 은 백슬래시를 벗긴다(역슬래시 든 편집은 Edit·Write) · Java 소스에 BOM 글자를 그대로 안 넣는다(`\uFEFF` 탈출)
- CI 는 ubuntu — 드라이브 글자를 쓰는 단언은 `assumeTrue` 로 OS 를 가린다
- `core/fs`·`core/vcs` 는 corpus 레인에도 든다(`verify-fingerprint.sh:69-70`) — 빠른 `verify.sh` 끝 줄이 `--corpus` 를 알린다. 무거운 판은 `V-72a` 에서 한 번

## 3. 설계 결정 — 전부 [고정]

1. **링크는 따라가기 전에 대상 글로 판정한다**(`0-133a`) — 뿌리부터 조각마다 `Files.isSymbolicLink`(따라가지 않는다) → 링크면 `readSymbolicLink` 글이 네트워크 꼴이면 거절, 로컬이면 그 대상으로 갈아타 이어 걷는다(사슬 64). 버린 대안 — 링크가 든 경로를 통째 거절: 로컬 링크를 쓰는 정상 폴더가 막힌다 / `toRealPath` 뒤 판정(지금): 닿은 뒤다
2. **정션은 한계로 둔다** — JDK 가 정션 대상을 못 읽고(`NotLinkException`), NTFS 정션은 로컬 볼륨만 가리킨다. 정션 너머 길에 다시 UNC 링크가 있는 2단 꼴은 지금처럼 닿은 뒤 거절. 버린 대안 — 정션이 든 경로 거절: 작업 폴더를 정션으로 옮겨 둔 PC 가 통째로 막힌다 / 내부 API 로 재분석 버퍼 읽기: 새 Add-Opens·Unsafe
3. **기억은 두지 않는다** — 경로 하나에 속성 읽기 조각 수만큼(약 0.2ms). `LocalFilesTest.checkIsCheapAfterTheFirst`(2,000 회 10초)가 지킨다. 빨개지면 사다리(부모 폴더 기억 10초)
4. **git 가드의 네트워크 판정은 fs 와 같은 식**(`0-132a`) — `//`·`\\` 머리 + `LocalFiles.network`(장치 경로·매핑 드라이브). alternates 는 git 과 같은 깊이 5 까지 따라가고 따옴표 줄은 C 꼴로 푼다. 못 푸는 따옴표 줄은 `UNREADABLE`(닫힌 실패)
5. **하위 `.git` 은 gitdir·commondir 줄만 잰다**(`0-132b`) — 설정 글(include 등)은 안 읽는다(git 도 안 든 저장소의 설정은 안 읽는다). 걷기는 `changed`(status) 앞에서만 — `log`·`diff A B` 는 작업 트리를 안 훑는다. 링크·정션 폴더와 `.git` 폴더 안은 안 들어간다
6. **bare 꼴 폴더는 자바도 거절한다**(`0-132b`) — `safe.bareRepository=explicit` 를 모르는 옛 git 대비. 새 사유 글 하나
7. **색인 밖 인터페이스는 완전 이름으로 구현을 건다**(`6-103`) — 구현 클래스(와 색인 안 조상)의 `implements` 가 완전 이름이거나 명시 import 이고 그 이름이 색인에 없으며 **패키지 앞 두 마디가 구현 클래스와 같을 때만**. 필드 쪽은 후보가 하나도 없을 때만 같은 완전 이름으로 찾는다. 별 import·같은 패키지는 못 정한다(한계). [고정] 9(설계 79 — 명시 import 가 답)는 그대로
8. **감싸개의 못 닿은 인자 열쇠 = 출처 자리 + 식 자리(줄:칸)**(`6-104a`) — 리터럴 가지는 그대로(보정 B)
9. **Q 타입 길은 엔티티가 아닌 import 를 안 본다**(`6-104b`) — `QMember` 는 엔티티 `Member` 다. `JpaIndex.entitiesNamed` 는 안 고친다(메서드 38) — 부르는 자리에서 참조 자리(`From`)를 바꿔 넘긴다. 저장소·EntityManager 길은 6-96 그대로
10. **분기가 연 표 자리**(`6-105a`) — 쉼표는 건너뛰고 표 자리를 둔다 · 이름 사슬 뒤가 연산자 글자·조건 낱말이면 조건이라 닫는다 · 컬럼 목록 괄호(6-101)는 그대로
11. **별칭 정의에 지는 자기 정의는 UPDATE 대상 자리 것만**(`6-105b`) — 설계 79 [고정] 11 의 「자기 정의보다 별칭 정의가 이긴다」 를 이 범위로 좁힌다. 분기 표시는 여전히 범위를 안 가른다
12. **날글 구역은 script 몸을 먼저 비운 글에서 찾는다**(`5-159a`)
13. **이름 가림은 `NameScope` 한 자리**(`5-159b`) — 지역: 감싼 callable·초기화 블록·필드 선언 안의 매개변수(메서드·람다·catch)·지역·패턴 변수. 필드: 바깥으로 나가며 record 구성요소(가림)·형식과 익명 클래스 몸의 필드·같은 파일에 선언된 조상의 필드. 「같은 파일 아무 클래스」 떨어지기는 없앤다. 파일 밖 조상의 필드는 못 본다(한계)
14. **DdlGen 의 FK 부모 찾기는 미리 받은 표도 본다**(`1-139`) — 반영 DDL 이 뒤 스냅샷 전체를 넘긴다. 이름 열쇠(대문자)는 DdlGen 과 같다(스키마는 안 가른다 — 한계)
15. 행 차례 — 규칙 1 넷(도우미 `0-133a` 먼저) → corpus 레인(같은 파일끼리, 표본을 가장 크게 움직이는 `6-103` 을 끝에) → db 레인 → 무거운 판

## 4. 실행 스텝

행마다 끝은 CLAUDE.md 「Task 끝」 순서(분할표 행·이력 → `bash scripts/verify.sh` → `git commit` 따로 → `task-done`). `src/main/java` 를 고치는 행은 시험을 먼저 쓰고 RED(컴파일 실패 포함)를 본 뒤 고친다. 커밋 제목 `fix: <번호> <무엇>`. 줄 번호는 `69f9182` 기준 — 앞 행이 밀면 메서드 이름으로 찾는다. 코드 조각은 꼴이다 — import·`final`·주석 글은 그 파일 관례에 맞춘다.

공통 사다리
- `SelfCheckTest.mainMatchesBaseline` 빨강 — **오른 값·새 줄**: 분기를 도우미로 뺀다(2절 함정). 그래도 오르면 그 조각을 빼고 이력 「정한 것(계획 밖)」 + 새 행. **내린 값·사라진 줄**: 그 행이 고친 메서드면 `-Dtest=SelfCheckTest -Dgolden.update=true` 로 갱신하고 diff 가 그 줄뿐인지 본다
- `golden/` diff — 행의 「의도된 골든 변화」 만 갱신. 그 밖은 고침이 넓다 → 고친다
- RED 가 「전」 과 다를 때 — 이미 기대대로 나오는 사례는 지킴이로 두고 이력에 한 줄. 기대도 「전」 도 아니면 7절
- 기존 단언이 빨개질 때 — 이 행이 고치는 결함을 못 박은 단언이면 기대를 바꾸고 이력에. 아니면 고침이 넓다
- 시험 글에 실제 사업 이름·SQL·값을 안 쓴다(`T1`~`T5`·`com.acme.*`). 인라인 `Source`·임시 폴더로(픽스처 폴더에 안 더한다)
- PMD·SpotBugs·ErrorProne 은 빠른 `verify.sh` 가 안 본다 — `V-72a` 가 본다. 새 짝은 코드로 고친다
- 행의 「대상」 밖 파일은 둘까지(이력에), 셋부터 7절 · 조각이 여럿이면 조각마다 RED → GREEN, 한 조각이 사다리로 빠져도 나머지는 친다

### S0 — 설계 문서 · 행 · 번들 표 (실행 세션이 했다)
1. 가지 `work/2026-11-25` 그대로(HEAD = main `467a965` + 번들 92 닫기 `7d4a964` — 같은 PR 에 실린다). 새 가지 안 딴다
2. 이 플랜 전체를 `design/81-bundle93.md` 로(글자 그대로 — 이 S0 머리만 「실행 세션이 했다」 로, 「설계 세션이 못 친 것」·「Opus 번들 해 시작 지시문」 절은 뺀다)
3. `PROGRESS.md` — 「번들 92 닫기」 줄의 `(이 커밋)` → `7d4a964`
4. `PLAN.md` 15장 — 「번들 93~」 행을 둘로:
   - **「번들 93」** — 순서 칸은 아래 「차례」 · 설계 칸 「2026-11-25(Fable, 설계 81 — `design/81-bundle93.md`. 실행 Opus high · 마무리 독립 리뷰 Fable xhigh). **번들 92 리뷰 행 여섯 + G4-C1 — 코드 행 12.** 사용자 결정(2026-11-25): 일곱만 · G4-C1 은 같은 꼴 자리 전부 · 매핑 드라이브 값 거절 · **머지 위임** · 시간 컷 없음」 · 설치 칸 「표본 폴더 · node · Docker · 여유 RAM 15GB」 · 상태 「대기」
   - **「번들 94~」** — 순서 칸 「(설계 전)」 · 요지 「가로 점검 5차(번들 92~93 diff `fccf261..<93 머지>`) · 설계 78 4절 남은 후보(C1 후보-1~5 · C2-H2·H3 · A 후보 셋 · `NEXT VALUE FOR`) · `5-100`·`5-31`·`5-134`(순수본 재주입) · 반입 zip 갱신 · 조건부 `5-14`·`5-15`·`6-9`」 · 상태 「설계 전」
5. `PLAN.md` 16장 —
   - 대기 둘(`6-103`·`1-139`)의 사실 칸 끝에 ` · **처방**: 설계 81 §4`
   - `0-132`·`6-104`·`6-105`·`5-159` — 사실 칸 끝에 ` · **처방**: 설계 81 §4 — `<번호>a`·`<번호>b` 로 나눴다`, 상태 「나눔」
   - 표 끝에 새 행 — 아래 S0 표에서 `6-103`·`1-139` 를 뺀 행마다(꼴 `| <번호> | <청크> | **설계 81 §4 가 원본**. <근거>. **축**: <축> **강제 지점**: <강제 지점> | <선행> | 대기 |`)
6. `PROGRESS.md` 이력 한 줄(S0 설계 81 — 코드 행 12 + `V-72a`·`R-39`·`V-72`, 차례) + 「현재 상태」(진행중 = 번들 93 대기)
7. `bash scripts/verify.sh`(docs 레인) → `git commit`(따로) `docs: 설계 81 — 번들 93 행·번들 표`
- 확인: `git log --oneline -1` · `git status --short` 깨끗 · 8절 「첫 스텝 전에」

| # | 청크 | 근거 | 축(레인) | 강제 지점 | 선행 |
|---|---|---|---|---|---|
| 0-133a | fs — 네트워크를 가리키는 링크를 따라가기 전에 거절 | 설계 78 G4-C1 | 규칙 1(java) | `LocalFilesTest.linksToNetworkAreRefusedBeforeFollowing` | S0 |
| 0-133b | cli — 명령줄 폴더의 링크 | G4-C1(사용자 결정 3) | 규칙 1(java) | `BatchCliTest` · `MakeTemplatesTest` | 0-133a |
| 0-132a | git 가드 — alternates 사슬 · 따옴표 줄 · 매핑 드라이브 값 · 링크 | 대기 0-132 ② + 사용자 결정 3·4 | 규칙 1(java) | `WorkingCopyTest.gitGuardFollowsAlternatesAndRefusesMappedDrives` | 0-133a |
| 0-132b | git 가드 — 하위 `.git` · 안에 든 bare 꼴 | 대기 0-132 ①·③ | 규칙 1(java) | `WorkingCopyTest.gitGuardSeesNestedGitAndBareShape` | 0-132a |
| 5-159a | 날글 구역 — script 몸을 먼저 비운다 | 대기 5-159 ①(리뷰 M5) | 정리(corpus) | `ZonesTest.rawLinesSkipsScriptBodies` | S0 |
| 5-159b | 값 고침 — 가린 이름 · 보이는 필드 | 대기 5-159 ②(리뷰 M9) | 정리(corpus) | `CleanupTest.valueFixRespectsLambdaCatchAnonymousAndRecordNames` | 5-159a |
| 6-105a | SqlTables — 분기가 연 표 자리의 쉼표 · 조건 | 대기 6-105 ①(리뷰 M2) | 분석(corpus) | `SqlTablesTest.CASES` | S0 |
| 6-105b | SqlJoins — UPDATE 대상 자리의 자기 정의만 진다 | 대기 6-105 ②(리뷰 M3) | 분석(corpus) | `SqlJoinsTest.shapes` | 6-105a |
| 6-104a | 감싸개 방문 열쇠 — 인자 식 자리 | 대기 6-104 ①(리뷰 M1) | 분석(corpus) | `JavaGraphTest.wrapperCallsWithUnreachedIdsAreWalkedEach` | S0 |
| 6-104b | QueryDSL — 엔티티가 아닌 같은 이름 import | 대기 6-104 ②(리뷰 M4) | 분석(corpus) | `JavaGraphTest.qTypeIgnoresUnrelatedImportOfTheEntityName` | 6-104a |
| 6-103 | 색인 밖 인터페이스 — 완전 이름으로 구현 등록 | 대기 행(리뷰 I4) | 분석(corpus) | `JavaGraphTest.outsideInterfaceResolvesByFqn` | 6-104b |
| 1-139 | 반영 DDL — 새 표·ADD COLUMN 의 자동증가 부모 타입 | 대기 행(리뷰 M8) | 생성기(db) | `AlterGenTest.fkChildOfAutoParentInNewTableAndAddColumn` | S0 |
| V-72a | 정적 분석 · corpus 골든 | — | 4장 | `verify.sh --corpus` | 코드 행 전부 |
| R-39 | 손 판정 · 처분 표 | — | 4장 | compare ㊷ 절 | V-72a |
| V-72 | `--db` | — | 4장 | `verify.sh --db` | R-39 |

차례 — `S0 → 0-133a → 0-133b → 0-132a → 0-132b → 5-159a → 5-159b → 6-105a → 6-105b → 6-104a → 6-104b → 6-103 → 1-139 → V-72a → R-39 → V-72 → 마무리`

#### 0-133a — fs: 네트워크를 가리키는 링크를 따라가기 전에 거절 (설계 78 G4-C1)
- 대상: `core/fs/PathRules.java`(`LinkReader`·`LINKS`·`linkToNetwork`·`networkText`) · `core/fs/LocalFiles.java`(`check:146-168` · `writeResult:506-524` · 새 `networkLink`) · `LocalFilesTest`
- 사실: `refuseForbiddenReal:178` `Files.exists(near)` 와 `:185` `near.toRealPath()` 가 링크를 따라간 뒤 `:186` 에서 `network(real)` 을 잰다 — 대상이 `\\서버\공유` 인 심볼릭 링크는 접속이 나간 뒤 거절, 대상이 없으면 `exists` 거짓이라 조상으로 올라가 통과한다. `writeResult:518-519` 도 `mkdirs(parent)`·`parent.toRealPath()` 가 먼저다
- 변경:
  1. `PathRules` 에 더한다
     ```java
     /** 0-133a(G4-C1) — 링크 하나 읽기: 심볼릭 링크면 대상 글, 아니면(없는 조각·파일·폴더·정션) null. 링크 자신만 연다 */
     @FunctionalInterface
     interface LinkReader {
         String target(Path p) throws IOException;
     }

     static final LinkReader LINKS = p -> Files.isSymbolicLink(p) ? Files.readSymbolicLink(p).toString() : null;
     /** OS 가 푸는 링크 사슬(Windows 63 · 리눅스 40)보다 크다 — 넘으면 OS 도 안 따라간다 */
     static final int MAX_LINKS = 64;

     /**
      * 0-133a(G4-C1, 절대 규칙 1) — p 로 가는 길에 네트워크를 가리키는 심볼릭 링크가 있나. 뿌리부터 조각마다 링크인지 보고(따라가지 않는다),
      * 링크면 대상 글만 읽어 네트워크 꼴이면 참, 로컬이면 그 대상 + 남은 조각으로 갈아타 이어 걷는다. 링크를 못 읽으면 참(닫힌 실패)
      */
     static boolean linkToNetwork(Path p, LinkReader links) {
         Path abs = p.toAbsolutePath().normalize();
         java.util.ArrayDeque<Path> rest = new java.util.ArrayDeque<>();
         abs.forEach(rest::add);
         Path cur = abs.getRoot();
         int hops = 0;
         while (cur != null && !rest.isEmpty() && hops <= MAX_LINKS) {
             cur = cur.resolve(rest.poll());
             String target;
             try {
                 target = links.target(cur);
             } catch (IOException e) {
                 return true;
             }
             if (target == null) {
                 continue;
             }
             Path to = networkText(target) ? null : cur.resolveSibling(target).normalize(); // 상대 대상은 링크의 부모 기준
             if (to == null || LocalFiles.network(to.toString())) {
                 return true;
             }
             hops++;
             java.util.List<Path> head = new java.util.ArrayList<>();
             to.forEach(head::add);
             for (int i = head.size() - 1; i >= 0; i--) {
                 rest.addFirst(head.get(i));
             }
             cur = to.getRoot();
         }
         return false;
     }

     /** 링크 대상 글이 네트워크 꼴인가 — UNC·장치 경로·매핑 드라이브(LocalFiles.network)와 머리가 벗겨진 UNC\서버\공유 · \??\ */
     static boolean networkText(String target) {
         String up = target.trim().replace('/', '\\').toUpperCase(Locale.ROOT);
         return LocalFiles.network(target) || up.startsWith("UNC\\") || up.startsWith("\\??\\");
     }
     ```
     `cur.resolveSibling(target)` 이 `InvalidPathException` 을 던지는 대상 글(금지 글자)도 참으로 닫는다 — 그 한 줄을 try 로 감싸거나 `to` 구하기를 도우미로 뺀다(복잡도 15 안)
  2. `LocalFiles.check` — `refuseForbidden(p);` 와 `refuseForbiddenReal(p);` 사이에
     ```java
     if (PathRules.linkToNetwork(p, PathRules.LINKS)) { // 0-133a(G4-C1) — exists·toRealPath 가 링크 대상을 열기 전에
         throw new Refused(400, "네트워크·장치 경로는 받지 않는다 — 로컬 드라이브만");
     }
     ```
     (같은 글이 세 자리가 되면 상수로 뽑아도 된다 — 글자는 그대로)
  3. `LocalFiles.writeResult` — `PathRules.mkdirs(parent);` 앞에 `if (PathRules.linkToNetwork(p, PathRules.LINKS)) { throw new Refused(400, "결과 폴더 밖에는 안 쓴다(링크)"); }`
  4. `LocalFiles` 공개 문 하나 — `core.vcs`·`cli` 가 쓴다
     ```java
     /** 0-133a(G4-C1) — p 로 가는 길에 네트워크를 가리키는 심볼릭 링크가 있나(링크는 안 따라간다). core.vcs·cli 가 같이 쓴다 */
     public static boolean networkLink(Path p) {
         return PathRules.linkToNetwork(p, PathRules.LINKS);
     }
     ```
- 시험(먼저) `LocalFilesTest.linksToNetworkAreRefusedBeforeFollowing(@TempDir)` — 가짜 읽개(`Map<Path, String>` — 없는 열쇠는 null. 부른 경로를 목록에 적는다). 경로는 `tmp` 아래 절대 경로로(리눅스·Windows 같이 돈다):
  - `a/lnk` → `\\h\s\x` · `//h/s/x` · `UNC\h\s\x` 각각 참 · 가운데 조각이 링크(`a/lnk/sub/f.txt`)여도 참
  - 사슬: `a/l1` → `tmp/b/l2`(절대 로컬), `b/l2` → `\\h\s` → 참 · 상대 대상 `../b/l2` 도 참
  - 로컬 링크(`a/l3` → `tmp/c`) · 링크 없음 → 거짓 · 고리(`a/x` → `tmp/a/y`, `a/y` → `tmp/a/x`) → 거짓이고 `assertTimeout` 2초 안에 끝난다
  - 읽개가 `IOException` → 참 · **부른 경로 목록에 네트워크 꼴 글이 없다**(대상 쪽은 묻지 않는다)
  - 실물 링크(리눅스만 — `assumeTrue(!windows)` + 링크를 만들 수 있을 때): `Files.createSymbolicLink(tmp.resolve("lnk"), Path.of("\\\\h\\s\\x"))` → `fs.check(lnk)` 가 `Refused` 400 · 글에 「네트워크」(전 — 통과) · `fs.read(lnk/a.txt)` 400 · 로컬 폴더를 가리키는 실물 링크는 통과(지킴이)
  - 지킴이(그대로 초록): `checkIsCheapAfterTheFirst` · `networkPathsRefusedBeforeTouching` · `listSkipsForbiddenAndJunctions` · 정션으로 금지 폴더에 드는 길의 거절(G1-K07)
- 한계(이력·compare 에): 정션 너머 2단 꼴([고정] 2) · 확인과 열기 사이 링크 바꿔치기
- 사다리: `checkIsCheapAfterTheFirst` 빨강 → `LocalFiles` 에 「길에 네트워크 링크가 없다고 확인한 부모 폴더」 집합을 두고 `clean` 과 같이 10초 뒤 버린다(부모가 집합에 있고 끝 조각이 링크가 아니면 건너뜀) — 계획 밖 결정 하나
- 검증: `bash scripts/mvn.sh -q test -Dtest='LocalFilesTest,ArchitectureTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

#### 0-133b — cli: 명령줄 폴더의 링크 (G4-C1 · 사용자 결정 3)
- 대상: `cli/Batch.java`(`remoteDir:149-151`) · `cli/MakeTemplates.java`(`inside:52-59`) · `BatchCliTest` · `MakeTemplatesTest`
- 사실: `Batch.remoteDir` 는 글자 판정(`LocalFiles::network`)뿐 — `--data-dir`·`--profiles-dir` 가 네트워크를 가리키는 링크면 뒤에서 폴더를 만들며 따라간다. `MakeTemplates.inside:55` 는 `x.toRealPath()` 로 따라간 뒤 잰다
- 변경:
  1. `Batch` — 두 인자 `remoteDir` 가 넘기는 판정을 `s -> remotePath(s, LocalFiles::networkLink)` 로, 그리고
     ```java
     /** 0-133b(G4-C1) — 글자로 네트워크·장치 경로거나, 그 길에 네트워크를 가리키는 링크가 있다(링크는 안 따라간다) */
     static boolean remotePath(String s, java.util.function.Predicate<Path> linked) {
         return kr.ejg.toolbox.core.fs.LocalFiles.network(s) || linked.test(Path.of(s));
     }
     ```
     세 인자 `remoteDir` 는 안 고친다
  2. `MakeTemplates.inside` — 조건 머리를 `LocalFiles.network(p.toString()) || LocalFiles.networkLink(abs) || …` 로(`under(…toRealPath())` 보다 앞)
- 시험(먼저): `BatchCliTest` — `Batch.remotePath(tmp.toString(), p -> true)` 참 · `p -> false` 거짓 · 글자 UNC 는 `p -> false` 여도 참 · 실물 링크(리눅스만, `0-133a` 와 같은 꼴) `Batch.remoteDir(lnk, null)` 이 `--data-dir` 로 끝난다 · `MakeTemplatesTest` — 실물 링크(리눅스만) 출력 폴더가 `ParameterException`. 지킴이: `linkOutsideRunFolderIsRefused`·`insideRunFolderWrites` 그대로
- 검증: `bash scripts/mvn.sh -q test -Dtest='BatchCliTest,MakeTemplatesTest,MainTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 0-133a

#### 0-132a — git 가드: alternates 사슬 · 따옴표 줄 · 매핑 드라이브 값 · 링크 (대기 0-132 ② + 사용자 결정 3·4)
- 대상: `core/vcs/GitConfigGuard.java`(`check:30-49` · `network:114-117` · `optional:120-122` · 새 `alternates`·`unquote`·`refuseLink`) · `core/vcs/WorkingCopy.java`(`detect:129-144` · `dotGit:158-166` · `sizeInside:106-121`) · `WorkingCopyTest`
- 사실: ① `check:45-48` 은 `objects/info/alternates` 의 줄만 잰다 — 그 줄이 가리키는 로컬 폴더의 `info/alternates` 가 UNC 면 지난다(git 은 깊이 5 까지 따라간다). 따옴표 줄 `"\057\057h/s"`(git 이 C 꼴로 푼다)는 `network` 가 거짓 ② `network` 는 `\\` 머리만 — `Z:/hooks`(매핑 드라이브)는 지난다 ③ `detect:132`·`dotGit:161` 의 `Files.exists(d.resolve(".git"))`, `optional:121` 의 `Files.isRegularFile(f)`, `sizeInside:114` 가 링크를 따라간다
- 변경:
  1. `network` — fs 판정을 더한다(시험이 드라이브 판정을 넣을 수 있게 두 벌)
     ```java
     static boolean network(String value) {
         return network(value, kr.ejg.toolbox.core.fs.LocalFiles::network);
     }

     /** 큰따옴표를 전부 지우고 // · \\ 머리거나, fs 판정(장치 경로 · 매핑된 네트워크 드라이브 — 0-132a)이 참 */
     static boolean network(String value, java.util.function.Predicate<String> fs) {
         String v = value.replace("\"", "").strip();
         return v.replace('/', '\\').startsWith("\\\\") || fs.test(v);
     }
     ```
  2. alternates 사슬 — `check` 의 `:45-48` 을 `alternates(common.resolve("objects"), 0, new java.util.HashSet<>());` 로
     ```java
     private static final int MAX_ALT = 5; // git 의 alternates 깊이 상한

     /** objects/info/alternates — 줄마다 다른 객체 폴더(상대면 이 objects 기준). 그 폴더의 info/alternates 도 따라간다(0-132a) */
     private static void alternates(Path objects, int depth, Set<Path> seen) {
         String alt = depth > MAX_ALT || !seen.add(objects) ? null : optional(objects.resolve("info/alternates"));
         if (alt == null) {
             return;
         }
         for (String raw : alt.split("\r?\n|\r", -1)) {
             String s = raw.strip();
             if (s.isEmpty() || s.charAt(0) == '#') {
                 continue;
             }
             String line = unquote(s);
             refuseNetwork(line);
             alternates(resolved(objects, line), depth + 1, seen);
         }
     }
     ```
     `resolved` = `objects.resolve(line).normalize()`, `InvalidPathException` 이면 `IllegalStateException(UNREADABLE)`
  3. `unquote(String s)` — `"` 로 안 시작하면 그대로. 시작하면 닫는 `"` 까지 git 의 C 꼴로 푼다: `\\` `\"` `\a` `\b` `\f` `\n` `\r` `\t` `\v` 와 8진 `\NNN`(첫 자리 0~3, 세 자리). 푼 바이트를 UTF-8 로 읽는다(`ByteArrayOutputStream`). 모르는 탈출·닫는 따옴표 없음은 `IllegalStateException(UNREADABLE)`. 복잡도 15 를 넘으면 탈출 한 글자 풀기를 도우미로 나눈다
  4. 링크 — `check` 첫 줄에 `refuseLink(dotGit);`, `optional` 을 `refuseLink(f); return Files.isRegularFile(f) ? read(f) : null;` 로
     ```java
     /** 0-132a(G4-C1) — 가드가 읽는 자리가 네트워크를 가리키는 링크면 읽지 않고 거절 */
     private static void refuseLink(Path f) {
         if (kr.ejg.toolbox.core.fs.LocalFiles.networkLink(f)) {
             throw new IllegalStateException(REMOTE_PATH);
         }
     }
     ```
  5. `WorkingCopy` — `detect` 의 `.git` 판정과 `dotGit` 의 판정을 `Files.exists(g, LinkOption.NOFOLLOW_LINKS)` 로(링크 자신이 있으면 GIT — 가드가 바로 뒤에서 대상을 잰다) · `.svn` 은 `!LocalFiles.networkLink(svn) && Files.isDirectory(svn)` · `sizeInside` 는 `Files.isRegularFile(f)` 앞에 `if (LocalFiles.networkLink(f)) { return null; }`
- 시험(먼저) `WorkingCopyTest.gitGuardFollowsAlternatesAndRefusesMappedDrives(@TempDir)` — **git 을 안 띄운다**(`GitConfigGuard.check` 직접. 네트워크 꼴 글은 `//127.0.0.1/tb-none/…`):
  - 2단: `a/.git/objects/info/alternates` = `far/objects` 의 절대 경로, `far/objects/info/alternates` = `//127.0.0.1/tb-none/o` → `REMOTE_PATH`(전 — 통과)
  - 따옴표 줄: `"\057\057127.0.0.1/tb-none/o"`(자바 글에서는 `\\057`) → `REMOTE_PATH`(전 — 통과) · 닫는 따옴표 없는 줄 → `UNREADABLE`
  - 지킴이: 로컬 2단 통과 · `# //127.0.0.1/x` 주석 줄 통과 · 서로 가리키는 alternates 둘이 끝난다 · 기존 `gitConfigGuardRefusesIncludesAndRemotePaths` 그대로 초록(`C:/x/.gitignore` 통과 포함)
  - 매핑 드라이브: `GitConfigGuard.network("Z:/hooks", s -> s.startsWith("Z:"))` 참 · `network("C:/x", s -> false)` 거짓 · `network("\"//h\"/s", s -> false)` 참
  - 실물 링크(리눅스만): `.git/config` 가 대상 글 `\\h\s\c` 인 링크 → `REMOTE_PATH` · `.git` 자체가 그런 링크인 폴더 → `GitConfigGuard.check` `REMOTE_PATH`(전 — `exists` 거짓이라 「형상 관리 폴더가 아니다」)
- 한계: 전역·시스템 git 설정과 `GIT_DIR`·`GIT_CONFIG_*` 환경 변수는 안 본다(쓰는 사람의 환경 — `0-128` 한계 그대로) · 매핑 드라이브 값의 실측은 사람 ⑫
- 검증: `bash scripts/mvn.sh -q test -Dtest='WorkingCopyTest,ArchitectureTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 0-133a

#### 0-132b — git 가드: 하위 `.git` · 안에 든 bare 꼴 (대기 0-132 ①·③)
- 대상: `core/vcs/GitConfigGuard.java`(새 `nested`·`one`·`bareShape`·`BARE_SHAPE`) · `core/vcs/WorkingCopy.java`(`require:146-155` · `recent:408` · `gitChanged:234`) · `WorkingCopyTest`
- 사실: `git status --untracked-files=all` 은 작업 트리의 하위 폴더마다 `.git` 을 보고 안 든 저장소인지 잰다 — `.git` 파일의 `gitdir:` 경로와 그 폴더의 `commondir` 가 가리키는 곳을 연다. 가드는 꼭대기 `.git` 만 본다 · 검사 폴더에서 `.git` 부모까지 사이에 `HEAD`·`objects`·`refs` 가 같이 있는 폴더가 있으면 git 은 그 폴더를 저장소로 먼저 집는다(가드가 읽은 `.git` 과 다르다 — 지금 방어는 `safe.bareRepository=explicit` 하나)
- 변경:
  1. `GitConfigGuard`
     ```java
     static final String BARE_SHAPE = "검사 폴더가 git 저장소 폴더 꼴이다(HEAD·objects·refs) — git 조회를 하지 않는다";
     private static final String DOT_GIT = ".git";
     private static final int MAX_WALK = 64;

     /** 0-132b(리뷰 I2) — root 에서 .git 부모까지 HEAD·objects·refs 가 같이 있는 폴더면 거절(옛 git 은 safe.bareRepository 를 모른다) */
     static void bareShape(Path root, Path dotGit) {
         Path top = dotGit.toAbsolutePath().normalize().getParent();
         Path d = root.toAbsolutePath().normalize();
         while (d != null) {
             if (Files.isRegularFile(d.resolve("HEAD")) && Files.isDirectory(d.resolve("objects")) && Files.isDirectory(d.resolve("refs"))) {
                 throw new IllegalStateException(BARE_SHAPE);
             }
             d = d.equals(top) ? null : d.getParent();
         }
     }

     /** 0-132b(리뷰 I2) — root 아래 하위 폴더의 .git(파일·폴더·링크)마다 gitdir·commondir 줄을 잰다. 링크·정션 폴더와 .git 폴더 안은 안 들어간다 */
     static void nested(Path root) {
         Path top = root.toAbsolutePath().normalize();
         try {
             Files.walkFileTree(top, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class), MAX_WALK, new java.nio.file.SimpleFileVisitor<>() {
                 @Override
                 public java.nio.file.FileVisitResult preVisitDirectory(Path d, java.nio.file.attribute.BasicFileAttributes a) {
                     if (a.isSymbolicLink() || a.isOther()) {
                         return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                     }
                     if (!DOT_GIT.equals(String.valueOf(d.getFileName()))) {
                         return java.nio.file.FileVisitResult.CONTINUE;
                     }
                     one(top, d);
                     return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                 }

                 @Override
                 public java.nio.file.FileVisitResult visitFile(Path f, java.nio.file.attribute.BasicFileAttributes a) {
                     if (DOT_GIT.equals(String.valueOf(f.getFileName()))) {
                         one(top, f);
                     }
                     return java.nio.file.FileVisitResult.CONTINUE;
                 }

                 @Override
                 public java.nio.file.FileVisitResult visitFileFailed(Path f, IOException e) {
                     return java.nio.file.FileVisitResult.CONTINUE;
                 }
             });
         } catch (IOException e) {
             throw new IllegalStateException(UNREADABLE, e);
         }
     }

     /** 하위 .git 하나 — root 바로 아래 것은 꼭대기(이미 check 가 봤다). 링크면 대상, 파일이면 gitdir 줄, 그리고 그 폴더의 commondir 줄 */
     private static void one(Path top, Path dotGit) {
         if (top.equals(dotGit.getParent())) {
             return;
         }
         refuseLink(dotGit);
         Path dir = Files.isRegularFile(dotGit) ? linked(dotGit) : dotGit;
         String c = optional(dir.resolve("commondir"));
         if (c != null) {
             refuseNetwork(c.lines().findFirst().orElse("").strip());
         }
     }
     ```
  2. `WorkingCopy` — 도우미 하나를 두고 `require` 의 `GitConfigGuard.check(dotGit(root));` 와 `recent:408` 을 `guard(root);` 로, `gitChanged` 첫 줄에 `GitConfigGuard.nested(root);`
     ```java
     /** 0-128·0-132b(규칙 1) — git 을 부르기 전: 저장소 설정 · 안에 든 bare 꼴 */
     private static void guard(Path root) {
         Path g = dotGit(root);
         GitConfigGuard.check(g);
         GitConfigGuard.bareShape(root, g);
     }
     ```
- 시험(먼저) `WorkingCopyTest.gitGuardSeesNestedGitAndBareShape(@TempDir)` — **git 을 안 띄운다**:
  - `repo/.git/config`(깨끗) + `repo/sub/.git` 파일 `gitdir: //127.0.0.1/tb-none/x` → `GitConfigGuard.nested(repo)` 가 `REMOTE_PATH`
  - `repo2/mod/.git` 파일 `gitdir: ../.git/modules/mod` + `repo2/.git/modules/mod/commondir` = `//127.0.0.1/tb-none/c` → `REMOTE_PATH` · 하위 폴더 꼴 `repo3/inner/.git/commondir` = 같은 글 → `REMOTE_PATH`
  - 지킴이: 로컬 서브모듈 꼴(`gitdir: ../.git/modules/a`, commondir 없음) 통과 · `.git` 폴더 안의 `.git` 파일(`repo/.git/x/.git` — 네트워크 꼴)은 안 본다 · root 바로 아래 `.git` 은 다시 안 잰다
  - bare 꼴: `proj/.git/config` + `proj/src/HEAD`(파일)·`proj/src/objects/`·`proj/src/refs/` → `GitConfigGuard.bareShape(proj/src, proj/.git)` 와 `bareShape(proj/src/deep, proj/.git)` 가 `BARE_SHAPE` · 지킴이: 보통 저장소 통과 · `HEAD` 만 있고 `objects` 없는 폴더 통과
  - 기존 `gitChangedDiffRecent`·`gitRefusesImplicitBareRepository` 그대로 초록(실물 git 지킴이)
- 한계: 작업 사본 안 정션 너머의 `.git` 은 안 본다(git for Windows 는 정션을 폴더로 걷는다 — 사람 ⑫) · `.gitignore` 로 빠진 폴더의 `.git` 도 잰다(엄격한 쪽) · 큰 트리(파일 10만)는 조회마다 걷기가 1초쯤 더 든다
- 검증: `bash scripts/mvn.sh -q test -Dtest='WorkingCopyTest,CheckRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 0-132a

#### 5-159a — 날글 구역: script 몸을 먼저 비운다 (대기 5-159 ① · 리뷰 M5)
- 대상: `core/check/Zones.java`(`rawLines:228`) · `ZonesTest`
- 사실(탐침): 보정 A 의 입력 → `rawLines` 전부 거짓(기대 — `<textarea>` 세 줄 참). `:228` 이 `blankScripts(quietMarkup(…))` 차례라 `quietMarkup` 이 JS 의 `i<max` 를 태그로 읽고(`quietTag:194-212`) 짝 없는 `'` 부터 파일 뒤쪽 `'` 까지 비운다 — `</script>`·`<textarea>` 가 지워진다
- 변경: `:228` 을 `String s = quietMarkup(blankScripts(RegexRule.stripComments(text, lang)));` 로(한 줄). `quietMarkup`·`quietTag`·`blankScripts` 몸은 안 고친다
- 시험(먼저): `ZonesTest.rawLinesSkipsScriptBodies` 에 — 보정 A 입력의 `rawLines(…, "jsp")`·`"html"` 이 `[거짓, 거짓, 거짓, 참, 참, 참, 거짓, 거짓]`(전 — 전부 거짓) · 지킴이: 5-150 의 세 입력(스크립틀릿 문자열 속 `<textarea>` · 속성 값 속 `<pre>`)은 여전히 전부 거짓 · 따옴표 짝이 맞는 script 뒤 `<textarea>` 는 참
- 의도된 골든 변화: corpus `cleanup-*` — 날글 구역이 되살아난 파일의 console 삭제가 **줄고** 건너뜀이 는다(표본 0 기대). 그 밖 0
- 한계: script 밖 본문 글의 `<글자 … '`(짝 없는 따옴표)는 여전히 뒤쪽 따옴표까지 비운다 · 속성 값 안 `<script>` 글자가 몸을 여는 꼴(`Zones.scriptLines` 와 같은 판정)
- 검증: `bash scripts/mvn.sh -q test -Dtest='ZonesTest,CleanupTest,CheckRulesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

#### 5-159b — 값 고침: 가린 이름 · 보이는 필드 (대기 5-159 ② · 리뷰 M9)
- 대상: 새 `core/check/NameScope.java` · `core/check/ValueAst.java`(`shadowed:208-219` · `staticFinal:222-231` · `nearestField:235-245` 삭제) · `CleanupTest`
- 사실(탐침 — `ValueAst.apply(cu, {EQUALS_FIRST})` 건수, 전부 `static final String KEY = "k";` 가 같은 파일에 있다): `(s, KEY) -> s.equals(KEY)` 필드 초기화식 1 · `l.stream().anyMatch(KEY -> s.equals(KEY))` 1 · `catch (RuntimeException KEY) { … s.equals(KEY) }` 1 · 익명 클래스 필드 `String KEY = …` 를 쓰는 그 안 메서드 1 · `record R(String KEY)` 의 메서드 1 · `class H6 extends Other`(상수는 무관한 `Base2`) 1 · 조상 없는 `H9`(상수는 무관한 `Base4`) 1 — 기대 전부 0. `shadowed` 는 callable 의 매개변수·지역·패턴 변수만 보고, `nearestField` 는 `TypeDeclaration` 사다리만 오르다 못 찾으면 `staticFinal:228-230` 이 같은 파일 아무 클래스의 `static final` 로 떨어진다
- 변경:
  1. 새 `NameScope`(패키지 전용 `final class`, 정적 메서드만)
     ```java
     /** 5-159b(리뷰 M9) — 값 고침·판정이 묻는 「이 이름이 여기서 무엇을 가리키나」 — 지역 가림과 보이는 필드. ValueAst 가 메서드 상한에 가까워 따로 둔다 */
     final class NameScope {
         private static final int MAX_UP = 8;

         /** from 을 감싼 callable(없으면 초기화 블록, 없으면 필드 선언)에 같은 이름 매개변수(메서드·생성자·람다·catch)·지역 변수·패턴 변수가 있나 */
         static boolean local(Node from, String x) {
             Node m = scope(from);
             if (m == null) {
                 return false;
             }
             return m.findAll(Parameter.class).stream().anyMatch(p -> p.getNameAsString().equals(x))
                     || m.findAll(VariableDeclarator.class).stream().anyMatch(v -> v.getNameAsString().equals(x)
                             && v.getParentNode().filter(p -> p instanceof FieldDeclaration).isEmpty())
                     || m.findAll(TypePatternExpr.class).stream().anyMatch(p -> p.getNameAsString().equals(x));
         }

         /**
          * from 에서 보이는 필드 x 의 선언 — 바깥으로 나가며 층마다: record 구성요소면 가림(빈 값) · 그 형식(익명 클래스 몸 포함)의 필드 ·
          * 같은 파일에 선언된 조상(extends·implements)의 필드. 어디에도 없으면 빈 값(파일 밖 조상의 필드는 못 본다)
          */
         static Optional<FieldDeclaration> field(Node from, String x) {
             Node p = from.getParentNode().orElse(null);
             while (p != null) {
                 if (p instanceof RecordDeclaration r && r.getParameters().stream().anyMatch(c -> c.getNameAsString().equals(x))) {
                     return Optional.empty();
                 }
                 Optional<FieldDeclaration> hit = inType(p, x, MAX_UP);
                 if (hit.isPresent()) {
                     return hit;
                 }
                 p = p.getParentNode().orElse(null);
             }
             return Optional.empty();
         }
     }
     ```
     - `scope(from)` — `CallableDeclaration` 조상, 없으면 `InitializerDeclaration`, 없으면 `FieldDeclaration`(지금 `shadowed:209-211` 의 조상 찾기 꼴 그대로 + 필드 선언)
     - `inType(Node type, String x, int left)` — `members(type)` 가운데 변수 이름이 x 인 `FieldDeclaration`. 없고 `left > 0` 이면 `supers(type)` 의 이름마다 같은 파일의 `ClassOrInterfaceDeclaration`(자기 자신 빼고)에서 `inType(…, left - 1)`
     - `members(Node)` — `TypeDeclaration` 이면 `getMembers()`, 익명 몸이 있는 `ObjectCreationExpr` 이면 그 몸, 아니면 빈 목록 · `supers(Node)` — `ClassOrInterfaceDeclaration` 의 extends·implements 단순 이름, 익명 `ObjectCreationExpr` 의 형식 이름, 아니면 빈 목록
  2. `ValueAst.shadowed` 몸 → `return NameScope.local(from, x);`
  3. `ValueAst.staticFinal` 몸 →
     ```java
     return NameScope.field(from, x).filter(d -> d.isStatic() && d.isFinal())
             .flatMap(d -> d.getVariables().stream().filter(v -> v.getNameAsString().equals(x)).findFirst());
     ```
     `nearestField` 는 지운다
- 시험(먼저) `CleanupTest.valueFixRespectsLambdaCatchAnonymousAndRecordNames` — `valueFixRespectsFieldAndInitializerShadowing:1141` 의 꼴로: 「사실」 의 일곱 입력마다 정리 결과가 입력과 같고(`equals` 를 안 뒤집는다) `ValueAst.apply(…, {EQUALS_FIRST})` 가 빈 목록(전 — 1 건씩) · 지킴이: 같은 클래스의 상수(조상이 파일 밖이어도) 1 건 · 같은 파일 조상(`class H8 extends Base3`)의 상수 1 건 · 기존 `G1`·`G2`·`G3` 그대로
- 의도된 골든 변화: corpus `check-*`·`cleanup-*` 의 `java.equalsLiteralFirst` 지적·고침이 **준다**(표본 0 기대 — 주는 것은 이 꼴만). `self-check.txt` 0
- 한계: 파일 밖 조상이 같은 이름 필드를 가지면 못 본다 · 감싼 callable 어디든 같은 이름이 있으면 가린 것으로 본다(안전한 쪽 — 5-139 와 같다) · 인터페이스 상수(`static final` 을 안 적은 필드)는 전과 같이 상수로 안 본다
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,ValueAstTest,JavaDefectsTest,JavaRulesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 5-159a

#### 6-105a — SqlTables: 분기가 연 표 자리의 쉼표 · 조건 (대기 6-105 ① · 리뷰 M2)
- 대상: `core/analyze/SqlTables.java`(`Scan.columnParen:152-165` 옆 새 `branchHead` · `table:385-390`) · `SqlTablesTest.CASES` · `golden/analyze/sql-tables.json`
- 사실(탐침 — `B` 는 분기 표시): ⓐ `SELECT 1 FROM T1 a JOIN T2 b ON B a.X = b.X B a.Y = b.Y` → `[T1, T2, X, Y]` ⓑ `… ON a.ID = b.ID B , T3 c` → `[T1, T2]` + 미해결 `table` ⓒ `SELECT 1 FROM T1 a B , T3 c B , T4 d WHERE 1=1` → `[T1]` + `table` ⓓ `… ON a.ID = b.ID B a.X <> b.X B a.Y IS NULL B X = 1 B a.K LIKE 'x'` → `[K, T1, T2, X, Y]`. 분기가 표 자리를 열면(`reopen:364-372` · `alias:497-499` · `Scan.afterAlias:145-149`) 다음 토큰을 무조건 표로 읽는다
- 변경:
  1. `SqlTables` 에 `private static final Set<String> COND = Set.of("=", "<", ">", "!", "|", "+", "-", "*", "/", "%", "^", "IS", "IN", "LIKE", "BETWEEN", "NOT", "ILIKE", "RLIKE", "REGEXP", "SIMILAR");`(표 이름 뒤에 오면 그 이름이 조건의 왼쪽인 토큰)
  2. `Scan` 에
     ```java
     /**
      * 6-105a(리뷰 M2) — 분기 표시가 연 표 자리의 첫 토큰. 참이면 표로 안 읽는다: 쉼표(분기 안이 「, T3 c」 로 시작)는 표 자리를 둔 채 건너뛰고,
      * 조건(이름 사슬 뒤가 연산자 글자·조건 낱말)과 컬럼 목록 괄호(6-101)는 닫는다
      */
     static boolean branchHead(List<String> t, int i, Scan sc) {
         String x = t.get(i);
         if (",".equals(x)) {
             sc.byBranch = true;
             return true;
         }
         int next = tableName(x) ? chainEnd(t, i) + 1 : t.size();
         if (next < t.size() && COND.contains(t.get(next)) || columnParen(t, i)) {
             sc.st = State.CLOSED;
             return true;
         }
         return false;
     }
     ```
  3. `table:387-390` 의 `if (branch && Scan.columnParen(t, i)) { sc.st = State.CLOSED; return i; }` 를 `if (branch && Scan.branchHead(t, i, sc)) { return i; }` 로(분기 수 그대로)
- 시험(먼저): `SqlTablesTest.CASES` 에 넷(`branchOnConditions` ⓐ → `T1`·`T2` · `branchLeadingComma` ⓑ → `T1`·`T2`·`T3`, 미해결 없음 · `branchLeadingCommaAfterAlias` ⓒ → `T1`·`T3`·`T4`, 미해결 없음 · `branchConditionOps` ⓓ → `T1`·`T2`) → `-Dtest=SqlTablesTest -Dgolden.update=true` 로 새 열쇠 넷만. 지킴이: 옛 열쇠 전부 그대로(6-80·6-101 의 분기 사례 포함)
- 의도된 골든 변화: `sql-tables.json` 새 열쇠 넷 · corpus `analyze-*` — 조건의 컬럼 이름이던 표가 **빠지고** 쉼표로 시작한 분기의 표가 **는다** · `table` 미해결이 **준다**(늘면 7절)
- 한계: 분기 머리가 `a.COL` 로 끝나고 연산자가 다음 분기·분기 밖에 있는 꼴 · `COND` 밖 연산자(`@>`·`&&` 등 방언 연산자)
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlTablesTest,MapperIndexTest,SqlJoinsTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

#### 6-105b — SqlJoins: UPDATE 대상 자리의 자기 정의만 진다 (대기 6-105 ② · 리뷰 M3)
- 대상: `core/analyze/SqlJoins.java`(`Scopes.aliasFirst:358-361` · `entry:407-422` · `table:292`) · `SqlJoinsTest.shapes` · `golden/analyze/sql-joins.json`
- 사실(탐침): `SELECT 1 B FROM EMP JOIN DEPT d ON EMP.D_ID = d.ID B FROM EMP_HIST EMP JOIN DEPT d ON EMP.D_ID = d.ID` → `[DEPT.ID—EMP_HIST.D_ID]`(기대 — `DEPT.ID—EMP.D_ID` 도). `aliasFirst` 가 같은 범위에 별칭 정의가 하나라도 있으면 자기 정의(표 = 이름)를 전부 뺀다 — 첫 분기의 `FROM EMP` 가 빠진다. 지킴이(지금 맞다): `UPDATE a SET a.X = 1 FROM T1 a JOIN T2 b ON a.ID = b.ID` → `T1—T2` · `UPDATE a … B FROM T1 a … B FROM T3 a …` → `T1—T2`·`T2—T3`
- 변경:
  1. `Scopes` 에 `private final Set<Integer> updateTargets = new java.util.HashSet<>();`(UPDATE 대상 자리에서 적힌 자기 정의의 자리)
  2. `entry` — `m.computeIfAbsent(name, …).add(new Def(k, real));` 앞에 `if (afterUpdate(t, i)) { updateTargets.add(k); }`
     ```java
     /** i 의 표 낱말 앞(건너뛰는 수식어를 지나)이 문장의 UPDATE 인가(잠금·대입의 UPDATE 는 아니다) */
     private static boolean afterUpdate(List<String> t, int i) {
         int p = i - 1;
         while (p >= 0 && PASS.contains(t.get(p))) {
             p--;
         }
         return p >= 0 && "UPDATE".equals(t.get(p)) && !lockUpdate(t, p);
     }
     ```
  3. `aliasFirst` 를 인스턴스 메서드로
     ```java
     /**
      * 6-99(C1-02)·6-105b(리뷰 M3) — 같은 범위에 별칭 정의(표 ≠ 이름)나 빈 글 정의가 있으면 UPDATE 대상 자리의 자기 정의(UPDATE a 의 a)만
      * 후보에서 뺀다. FROM·JOIN 자리의 자기 정의는 표다(다른 분기가 같은 이름을 별칭으로 써도)
      */
     private List<Def> aliasFirst(List<Def> defs, String name) {
         if (defs.stream().allMatch(d -> d.table().equals(name))) {
             return defs;
         }
         return defs.stream().filter(d -> !(d.table().equals(name) && updateTargets.contains(d.at()))).toList();
     }
     ```
- 시험(먼저): `SqlJoinsTest.shapes` 에 `branchTableThenAlias`(「사실」 입력 → 조인 둘) → `-Dtest=SqlJoinsTest -Dgolden.update=true` 로 새 열쇠 하나만. 지킴이: 옛 열쇠 72 전부 그대로(`updateAliasFrom`·`updateAliasDerived`·`updateAliasJoinKept`·`updateTableFromKept`·`branchSameAlias`·`mergeNoInto` 포함)
- 의도된 골든 변화: `sql-joins.json` 새 열쇠 하나 · corpus `analyze-*-joins`·`deliverable-egov-relations.json` — 분기 사이 같은 이름이 표·별칭으로 갈린 문장의 조인만(표본 0 기대)
- 사다리: 옛 열쇠가 바뀌면 그 SQL 이 「FROM 자리 자기 정의가 별칭에 져야 맞는」 꼴인지 본다 — 그렇다면 7절(설계가 놓친 꼴)
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlJoinsTest,SqlTablesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-105a

#### 6-104a — 감싸개 방문 열쇠: 인자 식 자리 (대기 6-104 ① · 리뷰 M1)
- 대상: `core/analyze/StmtArgs.java`(`idText:92-103`) · `JavaGraphTest`
- 사실(탐침 — 공통 DAO `selectList(String queryId, Object p) { return sqlSession.selectList(queryId, p); }`): 처리기 `two(t)` 가 `dao.selectList("user." + t, null); dao.selectList("dept." + t, null);` → 문장 `[user.(prefix)]` 하나 · `un(a, b)` 가 `dao.selectList(a, null);`·`dao.selectList(b, null);` → `statement` 미해결 한 줄(첫 호출 줄만). 못 닿은 인자의 열쇠 꼬리가 `|@클래스#메서드` 뿐이라 같은 메서드의 둘째 호출이 방문 집합에 걸린다
- 변경: `idText:102` 를
  ```java
  return sink ? "|@" + at.c().fqcn + "#" + at.m().getNameAsString() + at.arg().getBegin().map(p -> ":" + p.line + ":" + p.column).orElse("") : "";
  ```
  로. 리터럴 가지(`:96-101`)는 **안 고친다**(보정 B · [고정] 8)
- 시험(먼저) `JavaGraphTest.wrapperCallsWithUnreachedIdsAreWalkedEach` — 「사실」 의 두 처리기: `/two.do` 문장이 `dept.`·`user.` 둘(전 — 하나)이고 `prefix` 미해결 둘 · `/un.do` 의 `statement` 미해결이 두 줄(전 — 한 줄) · 지킴이: `statementIdsThroughLayersAndWrappers:559` 전부 그대로(위임 260 회에 `depth` 없음 포함)
- 의도된 골든 변화: corpus `analyze-*` — 같은 메서드가 감싸개를 접두·변수 id 로 여러 번 부르는 자리의 `prefix` 문장·`prefix`·`statement` 미해결 줄이 **는다**. `depth` 가 **늘면** R-39 에서 그 처리기를 본다(감싸개 호출 자리 수만큼 는 것이면 허용, 아니면 7절)
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaGraphTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

#### 6-104b — QueryDSL: 엔티티가 아닌 같은 이름 import (대기 6-104 ② · 리뷰 M4)
- 대상: `core/analyze/JpqlPaths.java`(새 `qFrom`) · `core/analyze/JavaGraph.java`(`querydsl:860` — 식 하나) · `JavaGraphTest`
- 사실(탐침): 엔티티 `domain.Member`(`@Table(name = "TB_MEMBER")`) · `app.dto.Member` · 저장소가 `import app.dto.Member; import domain.QMember;` 하고 `queryFactory.selectFrom(QMember.member).fetch()` → 문장 0 · 미해결 `querydsl …list:QMember`(`import app.dto.Member` 를 빼면 `repo.MemberQueryRepo.list#qdsl`). `JpaIndex.entitiesNamed:164-167` 안의 `importedOnly`(6-96)가 `Q` 를 뗀 이름 `Member` 의 import 를 보고 후보를 비운다
- 변경:
  1. `JpqlPaths` 에
     ```java
     /** 6-104b(리뷰 M4) — Q 타입 길의 참조 자리. Q 를 뗀 이름의 명시 import 가 엔티티가 아니면(같은 이름 DTO) 그 import 를 뺀 자리 — QMember 는 엔티티 Member 다 */
     static JpaIndex.From qFrom(JpaIndex.From from, String entity, List<JpaIndex.Entity> entities) {
         String imp = from.imports().get(entity);
         if (imp == null || entities.stream().anyMatch(e -> e.fqcn().equals(imp))) {
             return from;
         }
         Map<String, String> rest = new java.util.HashMap<>(from.imports());
         rest.remove(entity);
         return new JpaIndex.From(from.pkg(), rest, from.module());
     }
     ```
  2. `JavaGraph.querydsl:860` 의 `jpa.entitiesNamed(type.substring(1), from(c))` 를 `jpa.entitiesNamed(type.substring(1), JpqlPaths.qFrom(from(c), type.substring(1), jpa.entities()))` 로. 그 메서드에 분기·줄을 더하지 않는다(기준선 22). `JpaIndex`·`entityManager:771`·`JpaIndex:378` 은 안 고친다
- 시험(먼저) `JavaGraphTest.qTypeIgnoresUnrelatedImportOfTheEntityName` — 「사실」 의 소스(인라인 `Source` 넷 + `JpaIndex.scan`): `/m.do` 문장이 `repo.MemberQueryRepo.list#qdsl` 하나이고 `querydsl` 미해결 없음(전 — 문장 0 + 미해결) · 지킴이: 저장소가 엔티티 `domain.Member` 를 import 한 꼴 그대로 · EntityManager 길(`em.persist(new Member())` + `import app.dto.Member`)은 여전히 `jpaType`(6-96) · `qdsl-graph.json`·`jpa-graph.json` 골든 그대로
- 의도된 골든 변화: corpus `analyze-*` — `querydsl` 미해결이 **줄고** `#qdsl` 문장이 그만큼 는다(표본 0 기대)
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaGraphTest,JpaIndexTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-104a

#### 6-103 — 색인 밖 인터페이스: 완전 이름으로 구현 등록 (대기 행 · 리뷰 I4)
- 대상: `core/analyze/TypeBind.java`(새 `implemented`·`outsideName`·`outsideImpls`·`sameRoot`) · `core/analyze/JavaGraph.java`(`run:226` 식 하나 · `resolve:648-673` 에 `if` 하나) · `JavaGraphTest`
- 사실(표본): 보정 A. 필드 타입의 완전 이름(완전 이름으로 적었거나 명시 import)이 색인에 없으면 `resolve` 의 후보가 비어 그 호출을 안 따라간다 — 구현 클래스는 색인에 있는데(`implements` 의 import 가 같은 완전 이름) 닿을 길이 없다. 6-93a 전에는 어림으로 우연히 닿던 30 문장이 흔적 없이 빠졌고, 같은 꼴의 다른 `…Facade` 는 처음부터 빠져 있었다
- 변경:
  1. `TypeBind` 에
     ```java
     /** 6-103 — 구현 등록 열쇠: 색인 안 인터페이스(ifaces — FQCN)에 더해, c 와 색인 안 조상(MAX_UP)이 implements 한 색인 밖 인터페이스의 완전 이름 */
     static Set<String> implemented(Set<String> ifaces, JavaGraph.Cls c, Map<String, List<JavaGraph.Cls>> byName,
             BiFunction<JavaGraph.Cls, String, List<JavaGraph.Cls>> lookup) {
         Set<String> out = new java.util.LinkedHashSet<>(ifaces);
         Set<String> seen = new HashSet<>();
         JavaGraph.Cls cur = c;
         for (int up = 0; up <= MAX_UP && cur != null && seen.add(cur.fqcn); up++) {
             for (ClassOrInterfaceType t : cur.decl.getImplementedTypes()) {
                 String fqn = outsideName(cur, t.getScope().map(Object::toString).orElse(""), t.getNameAsString(), byName);
                 if (fqn != null && sameRoot(cur.pkg, fqn)) {
                     out.add(fqn);
                 }
             }
             cur = parent(cur, lookup);
         }
         return out;
     }

     /** 6-103 — from 자리에서 적은 타입의 완전 이름(완전 이름으로 적었으면 scope.type, 아니면 명시 import)이 색인에 없을 때만 그 이름. 못 정하거나(별 import·같은 패키지) 색인에 있으면 null */
     static String outsideName(JavaGraph.Cls from, String scope, String type, Map<String, List<JavaGraph.Cls>> byName) {
         String fqn = scope.isEmpty() ? from.imports.get(type) : scope + "." + type;
         boolean indexed = fqn != null && byName.getOrDefault(type, List.of()).stream().anyMatch(x -> fqn.equals(x.fqcn) || fqn.equals(x.fqn));
         return indexed ? null : fqn;
     }

     /** 6-103 — 필드 타입이 색인 밖 인터페이스일 때 그 완전 이름으로 등록된 구현 */
     static List<JavaGraph.Cls> outsideImpls(Map<String, List<JavaGraph.Cls>> implementors, Map<String, List<JavaGraph.Cls>> byName,
             JavaGraph.Cls owner, JavaGraph.Field f) {
         String fqn = outsideName(owner, f.scope(), f.type(), byName);
         return fqn == null ? List.of() : implementors.getOrDefault(fqn, List.of());
     }

     /** 같은 사업의 다른 모듈인가 — 패키지 앞 두 마디가 같다. JDK·프레임워크 인터페이스(Serializable·Runnable …)에 구현 수백을 걸지 않는다 */
     private static boolean sameRoot(String pkg, String fqn) {
         int a = pkg.indexOf('.');
         int b = a < 0 ? -1 : pkg.indexOf('.', a + 1);
         String root = a < 0 ? null : b < 0 ? pkg : pkg.substring(0, b);
         return root != null && fqn.startsWith(root + ".");
     }
     ```
  2. `JavaGraph.run:226` — `for (String i : interfacesOf(c, new HashSet<>()))` 를 `for (String i : TypeBind.implemented(interfacesOf(c, new HashSet<>()), c, byName, this::lookup))` 로(식만 — `run(2)` 31 그대로)
  3. `JavaGraph.resolve` — 후보 `for` 문 바로 뒤(빈 이름 고르기 `if (out.size() > 1 && f.bean() != null)` 앞)에
     ```java
     if (out.isEmpty()) { // 6-103 — 후보가 없다: 색인 밖 인터페이스면 그 완전 이름으로 등록된 구현
         out.addAll(TypeBind.outsideImpls(implementors, byName, owner, f));
     }
     ```
     뒤의 빈 이름 고르기·`ambiguous` 적기는 새 후보에도 그대로 선다. `interfacesOf`·`lookup`·`TypeBind.mappings` 로 가는 `ifaces` 는 안 고친다
- 시험(먼저) `JavaGraphTest.outsideInterfaceResolvesByFqn` — 인라인 `Source`(패키지는 `com.acme.…` — 두 마디 뿌리):
  - 색인 밖 `com.acme.api.facade.v1.CustomerFacade`(소스 없음) · 색인 안 `com.acme.shop.facade.CustomerFacade`(인터페이스) + 같은 패키지 `CustomerFacadeImpl`(문장 `old.one`) · `com.acme.shop.store.CustomerFacadeImpl`(`import com.acme.api.facade.v1.CustomerFacade;` 하고 `implements CustomerFacade`, `@Service("customerFacadev1")`, 문장 `v1.two`) · DAO(`@Repository`, `sqlSession.selectOne("old.one")`·`("v1.two")`)
  - 처리기 셋: 완전 이름 필드 `private com.acme.api.facade.v1.CustomerFacade f;` → `[v1.two]`(전 — `[]`) · 명시 import 필드 → `[v1.two]`(전 — `[]`) · 색인 안 인터페이스를 import 한 필드 → `[old.one]` 만(지킴이)
  - 구현 둘(`ProductFacadeImpl`·`ProductFacadeV2Impl` 이 같은 색인 밖 인터페이스)이면 둘 다 걷고 `ambiguous` 한 줄 · `@Resource(name = …)` 이면 그 빈 하나
  - 지킴이: `implements java.io.Serializable`(명시 import) 클래스 여럿 + `Serializable` 필드의 `toString()` 호출 → 대상 0 · 미해결 변화 없음(뿌리가 다르다) · 패키지 한 마디(`web`) 클래스는 등록 안 됨 · `explicitImportDecidesTheType`(6-93a) · 픽스처 골든(`java-graph.json`·`generic-graph.json`·`jpa-graph.json`·`qdsl-graph.json`·`jdbc-graph.json`) 그대로
- 의도된 골든 변화: corpus `analyze-shopizer*` — 문장·CRUD 프로그램이 **는다**(`…Facade` 스물의 구현 아래) · `jpql` 미해결 2 → 4 쪽으로 · `ambiguous` 가 늘 수 있다(같은 색인 밖 인터페이스의 구현 둘) · 그 밖 표본의 `analyze-*` 는 이 꼴만. `table` 미해결(등급 A)이 새로 나오면 7절. `depth` 가 새로 나오면 R-39 에서 본다(새로 닿은 구현 아래면 허용 — 이력에 수)
- 한계: 별 import·같은 패키지로 적은 색인 밖 인터페이스 · 색인 안 인터페이스가 색인 밖 인터페이스를 `extends` 한 꼴 · 패키지가 한 마디인 클래스 · 뿌리가 다른 공용 모듈(`com.a.*` 구현이 `org.b.*` 인터페이스를)
- 사다리: 픽스처 골든이 바뀌면 그 픽스처에 「뿌리 같은 색인 밖 인터페이스 + 그 필드」 가 정말 있는지 본다 — 있으면 의도된 변화로 갱신(이력에), 없으면 고침이 넓다 · `resolve` 가 복잡도 15 를 넘으면 그 `if` 를 `TypeBind.outsideImpls` 가 `out` 을 받아 채우는 꼴로
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaGraphTest,JpaIndexTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-104b

#### 1-139 — 반영 DDL: 새 표·ADD COLUMN 의 자동증가 부모 타입 (대기 행 · 리뷰 M8)
- 대상: `core/gen/DdlGen.java`(`generate:248-257` 인자 하나 · `Gen.run:346-348`) · `core/gen/AlterGen.java`(바깥 새 `byName` · `Run.gen:202-210` · `Run.newTables:323-324`) · `AlterGenTest`
- 사실(탐침 — 부모 `CUSTOMERS.CUSTOMER_ID NUMBER(38,0)` 자동증가, 자식 `ORDERS.CUSTOMER_ID NUMBER(38,0)` + FK, 원본 oracle): 새 표 `ORDERS` 의 `CUSTOMER_ID` 가 mariadb·mssql `DECIMAL(38,0)` · postgresql `NUMERIC(38,0)` · 있는 표에 그 컬럼을 더하는 `ADD COLUMN` 도 같다(부모는 자동증가 정수 — FK 추가가 실패한다). `DdlGen.refColumn:136` 이 `g.byTable` 에서 부모를 찾는데 `Run.gen` 이 만든 `Gen` 은 `byTable` 이 빈 맵이고, `newTables` 의 `DdlGen.generate` 는 새 표만 넘긴다
- 변경:
  1. `DdlGen` — 패키지 전용 `generate(List<Table>, Options, TypeMapping, boolean exactSchema)` 에 다섯째 인자 `Map<String, Table> known`(미리 아는 표 — 이름 대문자 열쇠)을 더하고 `g.exactSchema = exactSchema;` 뒤에 `g.byTable = known;`. 공개 세 인자 `generate` 는 `Map.of()` 를 넘긴다
  2. `Gen.run:348` 의 `byTable = byName;` 을
     ```java
     Map<String, Table> all = new LinkedHashMap<>(byTable); // 1-139 — 미리 받은 표(반영 DDL 의 뒤 스냅샷) 위에 이 실행의 표
     all.putAll(byName);
     byTable = all;
     ```
     로. `fk(fks, t, fk, i, byName)` 는 그대로(이 실행의 표만)
  3. `AlterGen` 바깥(`Run` 아님 — 메서드 40)에
     ```java
     /** 1-139 — 뒤 스냅샷의 표를 이름(대문자)으로 — DdlGen 이 FK 부모 컬럼을 찾는다(자동증가 부모 · 부모 길이) */
     private static Map<String, Table> byName(Map<String, Table> b) {
         Map<String, Table> m = new LinkedHashMap<>();
         b.values().forEach(t -> m.putIfAbsent(t.name().toUpperCase(Locale.ROOT), t));
         return m;
     }
     ```
  4. `Run.gen` 의 람다 안 `g.exactSchema = …;` 뒤에 `g.byTable = byName(b);` · `Run.newTables` 의 `DdlGen.generate(…, DdlGen.blank(o.schema()))` 에 다섯째 인자 `byName(b)`
- 시험(먼저) `AlterGenTest.fkChildOfAutoParentInNewTableAndAddColumn` — `DdlGenTest.fkChildFollowsTheParentKey:90` 의 표 꼴로, 대상 `postgresql`·`mssql`·`mariadb` 마다: ⓐ 앞 `[CUSTOMERS]` → 뒤 `[CUSTOMERS, ORDERS]` 의 `CREATE TABLE … ORDERS` 안 `CUSTOMER_ID` 줄에 `BIGINT`(전 — `DECIMAL`·`NUMERIC`) ⓑ 앞 `[CUSTOMERS, ORDERS(자식 컬럼 없음)]` → 뒤 `[CUSTOMERS, ORDERS]` 의 `ADD` 줄에 `BIGINT` ⓒ 둘 다 경고에 「FK 부모가 자동증가」 · 지킴이: 부모가 자동증가가 아니면 지금 타입 · 원본 = 대상(같은 방언)은 지금 글자 그대로 · 기존 `AlterGenTest`·`DdlGenTest` 전부 초록
- 의도된 골든 변화: `golden/` 의 `alter-*`·`ddl-*` — 자동증가 부모를 가리키는 FK 자식 컬럼이 반영 DDL 에 있으면 그 컬럼 타입 줄과 경고 한 줄만(없으면 0). `--db` 는 `V-72`
- 한계: 부모 찾기가 표 이름뿐이다(스키마가 다른 같은 이름 표는 먼저 든 것) · 앞 스냅샷에만 있는 부모(뒤에서 지워지는 표)는 안 본다
- 검증: `bash scripts/mvn.sh -q test -Dtest='AlterGenTest,DdlGenTest,SnapshotDiffTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

### V-72a — 정적 분석 · corpus 골든 확인
1. `bash scripts/mvn.sh -q -DskipTests -Djacoco.skip=true verify`(PMD·SpotBugs·ErrorProne) — 새 짝은 코드로 고친다(기준선에 안 더한다). 고치기 둘
2. `bash scripts/verify.sh --corpus`(약 15분). 다른 JVM·컨테이너를 같이 띄우지 않는다. 허용 변화 —
   - `analyze-shopizer*`(`.json`·`-unresolved`) — `6-103` 의 「의도된 골든 변화」 그대로(문장·CRUD 프로그램 는다 · `jpql` 는다 · `ambiguous` 늘 수 있다)
   - 그 밖 `analyze-*` — `6-103`(뿌리 같은 색인 밖 인터페이스 구현) · `6-104a`(`prefix`·`statement` 는다) · `6-104b`(`querydsl` 준다) · `6-105a`(지어낸 표 빠짐 · 쉼표 분기의 표 는다 · `table` 준다) · `6-105b`(조인) 꼴만. `table` 미해결이 **늘면** 7절
   - `deliverable-egov-relations.json` — 조인·표 변화(`6-105a`·`6-105b`)를 따라가는 것만
   - `check-*`·`cleanup-*` — `java.equalsLiteralFirst` 지적·고침이 준다(`5-159b`) · console 삭제가 줄고 건너뜀이 는다(`5-159a`) — 둘 다 표본 0 기대. `CleanupCompileCorpusTest` 의 컴파일 실패가 **늘면** 등급 A — 7절
   - `security-*`·Juliet·Benchmark — 재현율이 내려가면 7절
   - 그 밖(`gen-*`·`hr-insert-*`·`masking-*`·`logsql-*`·`csv-*`·`strip-*`·`sqlfixture`·`metrics`)은 diff 0 — 바뀌면 원인 행과 댄다
   - 등급 B baseline 이 상한(표본의 2%)을 넘으면 7절. 허용 밖 변화 — 고치기 둘, 넘으면 원인 행을 `wip/<행>` 로 빼고 다시 돈다
3. diff 줄 수·종류별 건수(전 → 후)·벽시계를 이력에
- 의존: 코드 행 전부

### R-39 — 손 판정 · 처분 표
- `design/compare-2026-10-28.md` 끝에 `## ㊷ 번들 93 — 번들 92 리뷰 행 여섯 + G4-C1(2026-11-25)`:
  1. 처분 표 — 행마다(행 · 근거 · 강제 지점 시험 · 닫음/한계/wip). 한계는 행의 「한계」 줄
  2. `V-72a` 골든 변화 표(파일 종류별 건수 전 → 후). 0 이면 「없음」
  3. 손 판정(SQL 본문·코드·값은 안 적는다 — 파일·줄·문장 id·이름만) —
     - **`6-103` 으로 새로 닿은 문장 스물**: 그 처리기 필드의 완전 이름·명시 import 가 구현 클래스의 `implements` import 와 같은 완전 이름인지(다르면 7절) · `ResetCustomerPasswordApi.passwordResetVerify` → `CustomerRepository.findByResetPasswordToken` 길이 돌아왔는지(번들 92 리뷰 I4 의 증거 길 — 안 돌아왔으면 7절)
     - **새 `ambiguous` 전부(스물까지)**: 같은 색인 밖 인터페이스의 구현이 정말 둘 이상인지 · **새 `depth` 전부**: 새로 닿은 구현 아래인지
     - **`6-105a` 로 빠진 표 전부(스물까지)**: 그 이름이 분기 안 조건의 컬럼인지 · **새 표 전부**: 분기 안 `, 표` 꼴인지
     - **`6-104a` 의 새 `prefix`·`statement` 줄 스물** · **`6-105b` 로 바뀐 조인 전부** · **`5-159a`·`5-159b` 로 바뀐 고침 전부**: 각 행의 꼴인지(아니면 7절)
  4. 끝에서 끝 — `run.bat --port 18873 --no-browser`(빈 `--data-dir`·`--profiles-dir`) → ① 하위 폴더에 `.git` 파일(`gitdir: //127.0.0.1/tb-none/x`)을 둔 임시 git 저장소 → `curl -s "http://127.0.0.1:18873/api/check/vcs?path=<그 폴더>"` 의 사유에 「네트워크 경로」, 변경분 수 없음(`0-132b`) ② `objects/info/alternates` 가 로컬 폴더를 가리키고 그 폴더의 `info/alternates` 가 `//127.0.0.1/tb-none/o` 인 임시 저장소 → 같은 사유(`0-132a`) → 서버를 끄고 임시 폴더를 지운다
  5. `PROGRESS.md` 「사람이 할 것」 ⑫ 에 셋 — 개발자 모드 PC 에서 `mklink /D lnk \\서버\공유` 를 폴더 칸에 넣어 접속 시도 없이 거절되는지(`0-133a`) · 받은 소스의 `.git/config` 값이 매핑 드라이브(`Z:/…`)일 때 조회 전에 거절되는지(`0-132a`) · 작업 사본 안 정션 너머의 `.git` 파일(`0-132b` 한계)
- 코드·레인 지문이 안 바뀐다(docs 레인만)
- 의존: V-72a

### V-72 — `--db`
1. 여유 RAM — PowerShell `(Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB`. 15 미만이면 `docker ps` 로 다른 컨테이너를 본다 — 사용자 결정 2: **묻지 않고 돈다**. 그래도 15 미만이면 30분 뒤 한 번 더 재고, 여전하면 7절. `--corpus` 가 끝난 뒤에만
2. `bash scripts/docker-up.sh` → `bash scripts/verify.sh --db`(컨테이너 아홉, 17~35분). 다른 JVM 을 같이 띄우지 않는다
3. 허용 변화 — 골든 diff 0 기대. 움직일 수 있는 것: `alterRoundTrip`·`alterTwoReleases` 의 FK 추가 실패가 **준다** · 반영 DDL 의 FK 자식 컬럼 타입(`1-139`). 기존 등급 A(`alterRoundTrip`·`alterTwoReleases`·`ddlRoundTrip`·`insertRuns`·`readOnlyBypasses`·`qualitySqlRuns` …)는 0 — 새로 빨개지면 `1-139` 를 고치기 둘, 넘으면 `wip/1-139` 로 되돌리고 다시 돈다. 그 밖 줄이 바뀌면 7절
4. `verify.sh --db` 와 `git push` 는 따로 낸다
- 의존: R-39

### 마무리
CLAUDE.md 「마무리」 1~8 그대로 — 독립 리뷰 하나(**`model: fable`, effort `xhigh`** — 서브에이전트 호출에 둘 다 명시. Review Focus: ① 절대 규칙 1 — `PathRules.linkToNetwork` 가 놓치는 링크 꼴(대상 글 변종 · 상대 대상 · 사슬 · 가운데 조각)과 `LocalFiles`·`WorkingCopy`·`GitConfigGuard`·`Batch`·`MakeTemplates` 에 남은 「따라간 뒤 판정」 자리 · `GitConfigGuard.unquote` 가 git 의 C 꼴 풀기와 어긋나는 꼴 · `alternates` 사슬이 git 이 여는 경로를 빠뜨리는 길 · `nested` 걷기가 git status 가 재는 `.git` 을 빠뜨리는 길 · `bareShape` 가 정상 저장소를 막는 꼴 ② 시험이 네트워크에 닿는 자리(`//127.0.0.1` 밖 글 + 실물 git · Windows 에서 UNC 대상 링크 만들기) ③ `TypeBind.implemented`·`sameRoot` — 맞던 구현 대상을 바꾸거나 엉뚱한 구현을 거는 길 · 프레임 예산(`MAX_FRAMES`)을 넘기는 꼴 ④ `StmtArgs.idText` 의 자리 열쇠가 같은 문장을 중복으로 내거나 `depth` 를 내는 길 ⑤ `JpqlPaths.qFrom` 이 맞던 Q 타입 풀기를 바꾸는 꼴 ⑥ `SqlTables.branchHead` 가 진짜 표를 조건으로 닫는 꼴 · `SqlJoins.aliasFirst` 가 6-99 의 관계를 되돌리는 꼴 ⑦ `NameScope.field`·`local` 이 맞던 상수 판정을 빼는 꼴(지적이 조용히 준다) · `Zones.rawLines` 차례 바꿈이 5-150 의 사례를 되돌리는 길 ⑧ `DdlGen` 의 `byTable` 합치기가 같은 방언·DdlGen 한 번 실행의 출력을 바꾸는 길 ⑨ 기준선 — 오른 값·새 줄이 없는가 ⑩ 골든 diff 가 행의 「의도된 골든 변화」 와 다른 자리 ⑪ 새 판정에 양성·음성 단언이 다 있나) → `bash scripts/gate-probe.sh`(「패치 갱신 필요」 면 다시 뜬다) → 「순수본 밀린 목록」: 해당 없음 기대(이 번들은 `rules.yaml` 을 안 고치고 `java.equalsLiteralFirst`·날글 구역 판정은 순수본 code_check 에 없다 — `grep -l equalsLiteralFirst -r pure` 0 으로 확인하고 없으면 안 적는다) → `verify.sh`(리뷰 고침이 건드린 무거운 레인만 다시 — corpus 먼저, 그다음 db) → push → PR(본문에 행마다 시작 세 줄 표 — `V-72a`·`R-39`·`V-72` 포함) → CI 폴링 → claude[bot] 「아니오」 처분 → **머지 위임** `gh pr merge N --merge --delete-branch` → 닫기 커밋은 새 로컬 가지에 → 끝 보고(다음 손 = 가로 점검 5차, 대상 `fccf261..<93 머지>`).

## 5. 금지 사항

- `pure/` · `ext-fixes.yaml` · `config/pmd/exclude-from-failure.properties` · `config/spotbugs/exclude.xml` · `pom.xml` · 마이그레이션 · `rules.yaml` · `pure-rules.json` 을 고치지 않는다. 새 마이그레이션·규칙·의존성·라우트·화면 0. `Unresolved.KINDS` 를 늘리지 않는다
- **self-check 기준선** — 값을 올리거나 새 줄을 만들지 않는다. `JavaGraph`(34)·`JpaIndex`(38)·`AlterGen.Run`(40)에 메서드를 더하지 않는다 · `run(2)`·`querydsl(3)`·`walk(5)`·`index(4)`·`extract(2)`·`list(4)`·`resultRoot(5)`·`modifyColumn(7)` 에 분기·줄을 더하지 않는다(식을 도우미 호출로)
- 기존 경고·사유·미해결·`[손]` 글자를 바꾸지 않는다(새 사유는 `BARE_SHAPE` 하나) · 기존 시험 단언을 지우거나 느슨하게 바꿔 통과시키지 않는다 · 골든의 옛 열쇠·사례는 그대로
- 행이 「안 고친다」 고 적은 자리 — `Batch` 의 세 인자 `remoteDir` · `StmtArgs.idText` 의 리터럴 가지 · `JpaIndex.entitiesNamed`·`importedOnly` · `Zones.quietMarkup`·`quietTag`·`blankScripts` 몸 · `JavaGraph.interfacesOf`·`lookup` · `DdlGen` 의 `fk(…, byName)`
- **시험·탐침·끝에서 끝에서 네트워크에 닿지 않는다** — 네트워크 꼴 글은 `//127.0.0.1/tb-none/…`(가짜 읽개에 넣는 글은 무엇이든 된다 — 열지 않는다). 가드 시험은 git 을 띄우지 않는다. Windows 에서 UNC 대상 심볼릭 링크를 만들지 않는다. 실물 링크 시험은 `assumeTrue(!windows)`
- 화면(HTML·JS)·`common.js`·`common.css` 를 고치지 않는다 · 픽스처 폴더에 파일을 더하지 않는다 · 시험·compare 절·로그에 실제 사업 코드·SQL 본문·값을 쓰지 않는다(절대 규칙 2·3)
- 무거운 판을 겹쳐 띄우지 않는다(정적 분석 → `--corpus` → 손 판정 → RAM 확인 → Docker → `--db`)
- 다른 저장소(portfolio·satchel)를 고치지 않는다 · worktree·`finishing-a-development-branch` 를 쓰지 않는다 · 새 가지를 안 딴다(닫기 커밋 빼고)
- 뺀 것을 하지 않는다 — 점검 5차 · 설계 78 4절 나머지 후보 · 순수본 재주입 · 반입 zip · 조건부 `5-14`·`5-15`·`6-9`

## 6. 최종 검증

- `bash scripts/verify.sh` 초록 · `bash scripts/mvn.sh -q -DskipTests -Djacoco.skip=true verify` 초록 · `bash scripts/verify.sh --corpus` 초록(등급 A 0) · `bash scripts/verify.sh --db` 초록(컨테이너 아홉, 등급 A 0) · `bash scripts/gate-probe.sh` 열 전부 산다
- 강제 지점 시험이 전부 있다(S0 표, `wip/` 로 뺀 행 빼고) — 코드 행 이력에 RED → GREEN 한 줄
- 손 확인(R-39): compare ㊷ 처분 표 · 손 판정 · `curl` 둘
- 회귀: `golden/` diff 는 행의 「의도된 골든 변화」 와 `V-72a`·`V-72` 의 허용 변화뿐 · `self-check.txt` diff 0 · `pure-rules.json`·`rules.yaml` diff 0 · 규칙 수·마이그레이션 수·`Unresolved.KINDS` 그대로 · 커버리지 하한 유지
- 분할표 — 대기 여섯과 새 행이 다 닫혔다(또는 `wip/` 사유) · 15장 번들 93 완료 · `PROGRESS.md` ⑫ 에 셋
- CI 초록 · claude[bot] 처분 · PR 머지 · 메모리 `bundle93-done` + `MEMORY.md` 한 줄

## 7. 중단 조건

다음이면 임의로 풀지 말고 멈추고 보고한다(어느 스텝 · 무엇이 예상과 달랐나 · 가능한 선택지).
- 행의 「사실」 이 실제 코드와 다르다(줄 번호 어긋남이 아니라 메서드·흐름이 다를 때) · RED 가 행의 「전」 과 다르다(이미 맞게 나오는 사례는 지킴이로 두고 이력에 적은 뒤 계속, 행 전체가 그러면 그 행을 닫고 이력에 「재현 안 됨」)
- 검증이 2회 연속 실패하고 원인이 이 플랜 범위 밖
- 5절을 어기지 않고는 진행할 수 없다(특히 기준선 값을 올리거나 메서드 수 기준선 클래스에 메서드를 더해야만 되는 고침 · 네트워크에 닿아야만 되는 시험)
- 스텝에 없는 파일을 3개 이상 고쳐야 한다(골든·`self-check.txt` 는 스텝에 있는 것이다)
- 골든의 옛 열쇠·사례가 바뀐다(`sql-joins.json`·`sql-tables.json`·픽스처 그래프 골든·`alter-*`·`ddl-*`) — 행의 사다리가 처분을 적었으면 그대로 하고 계속
- `V-72a` — 등급 A(`table` 미해결·컴파일 실패·파싱 실패)가 새로 나온다 · 보안 재현율이 내려간다 · 등급 B baseline 이 2% 를 넘는다
- `R-39` — 손 판정 가운데 「아니면 7절」 에 걸린다
- `V-72` — 기존 등급 A 가 새로 빨강(`1-139` 를 `wip/` 로 되돌린 뒤에도) · 여유 RAM 15GB 미만이 30분 뒤에도 그대로

번들 모드의 「갈려도 안 멈춘다」 는 위 밖에서만 선다. 행당 계획 밖 결정 둘까지, 넘으면 `wip/<청크>` 로 빼고 다음 행. **무인이다 — 위 조건에 걸려도 그 행(또는 그 무거운 판)만 `wip/` 로 빼고 다음으로 갈 수 있으면 그렇게 한다**(이력·끝 보고에 적는다). 번들 전체를 세우는 것은: 절대 규칙을 어기지 않고는 못 가는 때 · `work/*` 트리가 빨간 채 남는 때 · 등급 A 가 원인 행을 못 찾은 채 남는 때뿐이다. 빠졌을 때 뒤 행 — `0-133a` 가 빠지면 `0-133b` 와 `0-132a` 의 링크 조각(변경 4·5)을 같이 빼고 `0-132a` 의 나머지·`0-132b`(`one` 의 `refuseLink` 줄만 빼고)는 친다 · `0-132a` 가 빠지면 `0-132b` 는 그대로 친다 · `5-159a`·`6-105a`·`6-104a`·`6-104b` 가 빠져도 그 뒤 행은 그대로 친다(같은 파일이라 이은 것뿐이다).

## 8. 불확실 항목

사용자만 정할 수 있는 것 — 없음(범위 · G4-C1 넓이 · 매핑 드라이브 값 — 2026-11-25 답 반영. 메모리 `bundle93-decisions`).

실행해야만 알 수 있는 것 — 실행자가 해당 스텝에서 확인한다.
- **첫 스텝 전에**: `git log --oneline -3`(꼭대기 `7d4a964`) · `git status --short` 깨끗 · `docker ps`
- **행마다 RED 가 「전」 과 같은가**(시험 실행) — 탐침은 `69f9182` 의 `target/app.jar` 로 돌린 것이다. `0-133a`·`0-133b`·`0-132a`·`0-132b` 의 「전」 은 코드 추적이다(네트워크·링크 권한 — 이 PC 에서 못 돌린다). 다르면 7절 첫 줄
- **행마다 self-check 값**(`-Dtest=SelfCheckTest`) — 줄 있는 메서드는 식만 바꿨다. `JavaGraph.resolve`·`SqlTables.table`·`GitConfigGuard.unquote` 가 15 를 넘는지는 실행이 잰다(넘으면 행의 사다리)
- **실물 심볼릭 링크 시험** — 이 PC 에서는 건너뛴다. CI(ubuntu)가 처음 돌린다 — CI 빨강이면 「마무리」 6 의 고치기 둘
- **`6-103` 이 shopizer 골든을 얼마나 움직이나 · `depth` 가 나오나** — `V-72a` 허용 변화 · `R-39` 손 판정
- **`6-105a`·`6-105b`·`6-104a` 가 다른 표본의 표·조인·미해결을 움직이나** — `V-72a`
- **`1-139` 가 `--db` 왕복과 `alter-*` 골든을 움직이나** — 행의 골든 줄 · `V-72` 3
- **Windows 의 심볼릭 링크 → UNC · 매핑 드라이브 값 · 정션 너머 `.git`** — 설계·실행 어디서도 못 돌린다(네트워크). 사람이 할 것 ⑫
