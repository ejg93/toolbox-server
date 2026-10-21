# 설계 28 — 번들 44: 반입 zip 손질 — `scripts/package.sh` (Fable, 2026-10-20)

이 플랜은 실행 담당 모델(Opus)이 그대로 따라 실행한다. 실행자는 탐색 과정을 모른다. 판단이 필요한 지점을 남기지 않았다. 번들 모드 규칙(CLAUDE.md 「번들 모드」·「마무리」)을 따른다. 차례는 `S0` → `8-16a` → `8-16b` → 마무리.

사용자 결정(2026-10-20, 뒤집지 않는다):
- 명세(8-10) 안 버그 둘(복사 실패를 안 잡음 · `--with` 경로 검증 없음)은 고친다.
- 명세 밖 넷을 같이 고친다 — **라이선스 고지 동봉** · **app.jar 를 m2/ 로 오프라인 빌드** · **zip 옆 `.sha256` 파일** · **성공 시 무대 삭제**.
- 저장소 루트의 옛 zip(`toolbox-server-20261003.zip`·`20261004.zip`)은 **끝 줄에 목록만 알린다**. 스크립트가 지우지 않는다.
- **마무리에서 `verify.sh --full`(약 20분)을 돌리고 package.sh → rehearse.sh 를 실측한다**(2026-10-20). 미루지 않는다.

## 1. 목표와 범위

한 문장: `scripts/package.sh` 가 반입 zip 을 만들 때 **빠진 재료를 조용히 넘기지 않고, `--with` 가 잘못된 경로를 받지 않고, 동봉 jar 가 현장 재빌드와 같은 저장소(m2/)에서 나오고, 라이선스 고지와 zip 해시가 같이 나오며, 실제 프로필이 `target/` 에 남지 않게** 한다.

완료 시 사용자가 체감하는 변화:
- `bash scripts/package.sh --with profiles/<사업>.yaml` 끝 줄에 `toolbox-server-<날짜>.zip` + `toolbox-server-<날짜>.zip.sha256` 둘이 나오고, zip 안에 `LICENSE`·`bundle/SOURCES.md`·`bundle/MANIFEST` 가 있다.
- `--with C:/…`·`--with ../x` 는 첫 줄에서 `[빨강]` 으로 멈춘다.
- zip 뒤 `target/bundle/` 이 없다. 옛 zip 이 있으면 끝 줄에 이름이 나온다.

In scope: `scripts/package.sh` · `scripts/package-check.sh` · `scripts/hooks-test.sh` · `.gitignore` · `README.md` · `PLAN.md`(11장 두 줄 + 번들 표 + 분할표 새 행) · `PROGRESS.md` · `design/28-bundle44.md`(이 파일 사본).
Out of scope: `rehearse.sh`·`verify.sh`·`verify-fingerprint.sh`·`bundle-fetch.sh`·`offline-build.sh`·bat 셋·자바 코드. release 게이트 시험(PROGRESS 가 「손 확인」으로 두 번 닫음). 완료 행(8-10·0-53b)·설계 21 본문의 낡은 글.

## 2. 현재 구조 요약

### 파일과 역할
| 파일 | 역할 |
|---|---|
| `scripts/package.sh` (90줄) | ① 앞 검사(깨끗한 트리 · `bundle-fetch.sh --check` · `offline-build.sh --check` · `.git/verify-stamp` 의 `release <HEAD 트리> full` 줄) → ② `mvn.sh -q -B -DskipTests package` → ③ 무대 `target/bundle/toolbox-server-<yyyymmdd>/` 에 `git ls-files -- src pom.xml mvnw mvnw.cmd .mvn config pure profiles mappings rules templates run.bat toolbox.bat build.bat README.md PLAN.md docs`(golden/corpus 제외) + app.jar·jre·drivers·m2·docs/javadoc·docs/dependencies.txt·fixtures/·빈 data/logs/out/rules·`--with` 파일(목록 `PACKAGED-WITH.txt`) → ④ `MANIFEST.txt`(sha256 크기 경로) → ⑤ `package-check.sh "$stage"` → ⑥ `jre/bin/jar.exe --create --no-manifest` 로 zip |
| `scripts/package-check.sh` (64줄) | 무대(또는 푼 폴더)의 금지·필수 검사. `PACKAGED-WITH.txt` 를 허용 목록으로 읽는다. 필수: `app.jar run.bat toolbox.bat build.bat MANIFEST.txt jre/bin/javac.exe jre/bin/java.exe mvnw.cmd pom.xml` + `m2/.mvn-home/…/apache-maven-*` + `drivers/*.jar` + 빈 `data logs out` |
| `scripts/rehearse.sh` | zip 을 저장소 밖에 풀어 package-check → version → selftest → build.bat(-o) → run.bat → H2 위치, 일곱 단계. 안 고친다 |
| `scripts/hooks-test.sh` :270-307 | package-check 의 금지·필수 케이스. :273-274 가 최소 무대를 만든다(`for f in app.jar … profiles/example.yaml; do echo x > "$PK/$f"`). :319-322 가 `git check-ignore app.jar` 를 잰다 |
| `scripts/offline-build.sh` :18, :36-38 | `winpath()` 와 `MAVEN_USER_HOME="$(winpath "$mvnhome")" bash scripts/mvn.sh -q -B -o -Dmaven.repo.local="$repo" -DskipTests verify` — **②가 따라야 할 꼴** |
| `scripts/verify.sh` | 레인 지문. `scripts/` 는 tools 레인(`verify-fingerprint.sh` `listed` 배열) → 문법 검사 + `hooks-test.sh`. `--full` 끝에 `release <트리> full` 을 적는다(:119) |
| `.gitignore` :24-26 | `/app.jar` · `toolbox-server-*.zip` — package.sh ①의 깨끗한 트리 검사를 막지 않으려고. **`.zip.sha256` 은 없다** |
| `README.md` :5-10 「현장에서 켜기」 · :61-69 「만드는 쪽」 | 현장 안내 유일한 문서. `docs/` 에 설치 문서 없음 |
| `PLAN.md` :398-421 11장 체크리스트 · :524 16장 분할표(마지막 행 5-31 이 :891, 그 뒤 「반입 전날」) · :521 번들 43 행 | 분할표는 3열(번호·무엇·상태). 번들 표도 3열 |

### 탐색에서 확인한 사실(실행자가 다시 확인할 필요 없음)
- `jre/` 는 Temurin 17.0.20.1 **JDK**(`bin/jar.exe`·`javac.exe` 있음). `m2/` 4,241 파일 341MB, `.mvn-home` 안에 Maven 3.9.9 배포본. `drivers/` 4 jar + `drivers/alt/` 3 jar. `docs/javadoc/` 6 jar. 모두 git 무시, 지문은 `bundle/MANIFEST`(m2 는 지문 없음 — `offline-build.sh --check` 가 기능으로 잰다).
- `LICENSE`(Apache-2.0)·`bundle/SOURCES.md`·`bundle/MANIFEST`·`bundle/drivers/pom.xml`·`bundle/drivers-alt/pom.xml` 는 추적 파일이지만 **zip 에 안 들어간다**. PLAN 16장 끝 「반입 전날」 :898 이 라이선스 확인을 `bundle/SOURCES.md` 로 하라고 적는다 → 심사자가 받을 zip 에 없다.
- ②의 빌드는 `~/.m2` 로 간다(`-o`·`-Dmaven.repo.local` 없음). 바로 앞 `offline-build.sh --check` 는 m2/ 로 `verify` 를 돌리므로 동봉 jar 와 현장 `build.bat` 결과가 다른 저장소에서 나온다. `mvn.sh` 는 `export JAVA_HOME` 뒤 `exec ./mvnw "$@"`(:12) — `mvnw` 는 셸에서 `MAVEN_USER_HOME` 을 안 읽고 `.mvn/wrapper/maven-wrapper.jar`(추적, `MavenWrapperMain`)를 띄우며 **그 자바가 환경변수 `MAVEN_USER_HOME` 을 읽는다**. 그래서 환경변수로 넘기면 된다(offline-build.sh :37 이 그 꼴로 돌아 통과해 왔다). **확인(2026-10-20)**: `m2/.mvn-home/wrapper/dists/apache-maven-3.9.9-bin/33b4b2b4/apache-maven-3.9.9` 있음. m2/ 는 pom 의 마지막 변경(`c2eb1bc` 2026-10-07, test 의존성 `validation-api 2.0.1.Final`·`jakarta.validation-api 3.0.2` 추가)을 담고 있다(`m2/javax/validation/validation-api/2.0.1.Final`·`m2/jakarta/validation/jakarta.validation-api/3.0.2` 있음) — 낡지 않았다.
- `set -uo pipefail` 만 있고 `-e` 없음. :37 `rm -rf "$base" && mkdir -p "$stage"` · :42 `cp target/app.jar` · :43 `cp -r jre drivers m2` · :44 `cp -r docs/javadoc` · :48 `cp -r …fixtures…` · :50 `mkdir -p data logs out rules` 가 실패해도 계속 간다. package-check 가 app.jar·jre·m2·drivers·data/logs/out 은 잡지만 **docs/javadoc·dependencies.txt·fixtures·pure·README·LICENSE 는 아무도 안 잰다**.
- `--with` 는 `[ -e ]` 만 본다(:20). `cp -r --parents` 라 절대 경로(`C:/…`·`/c/…`)는 무대 밖 꼴(`stage/c/workspace/…`)로 떨어지고, `../x` 는 무대 밖으로 나가며, `./profiles/x.yaml` 은 `PACKAGED-WITH.txt` 에 `./` 없이(sed) 적혀 그건 맞지만 `profiles/x.yaml/`(끝 슬래시)은 `find` 출력과 package-check 의 `profiles/<이름>` 비교가 어긋날 수 있다.
- zip 뒤 `target/bundle/<이름>/` 이 남는다(지금도 `target/bundle/toolbox-server-20261004/` 있음). `--with` 로 넣은 실제 프로필(`password:` 평문)이 거기 남는다. 루트에 `toolbox-server-20261003.zip`·`20261004.zip` 도 남아 있다.
- zip 자체 sha256 은 끝 줄 콘솔에만 찍힌다(:88).
- 추적 파일에 한글 이름 xlsx 11개(`templates/deliverable/example/01_데이터베이스정의서.xlsx` 등). **실측(2026-10-20)**: `jre/bin/jar.exe --create` 는 항목마다 UTF-8 플래그(일반 목적 비트 11, `0x0800`)를 켠다. 한글 이름 파일 + 빈 `data/` 를 jar 로 묶어 .NET `Expand-Archive` 와 **Explorer 의 zip 핸들러(`Shell.Application` CopyHere)** 로 풀었더니 둘 다 한글 이름 그대로·빈 폴더 그대로. 20261004 zip 에도 `templates/deliverable/example/` 항목 12개가 들어 있고 리허설(Info-ZIP `unzip`)이 통과했다 — 문제 아님.
- 무대 안 가장 긴 상대 경로 130자(m2 의 infinispan pom.sha1) + 맨 위 폴더 24자. 사용자 폴더에 풀면 260자 안. 깊은 폴더면 걸릴 수 있다 → README 한 줄로만.
- 지금 `.git/verify-stamp` 에 `release` 줄이 없다(java·tools·docs 가 fast). package.sh 는 `--full` 뒤에만 돈다 — 그래서 **package.sh 자체의 끝까지 실행은 마무리(6장)에서 `--full` 뒤 한 번**.
- 데모 서버가 이 PC 에서 떠 있을 수 있다(`127.0.0.1:41790`, 데모 Oracle 컨테이너 `toolbox-demo-ora`). `--full` 앞에 서버는 끄고 컨테이너는 `docker stop`(**`rm` 금지** — 볼륨 없음).

### 드러난 것 — 이 번들에서 안 함(새 행도 안 세움, 이력에만)
- release 게이트·`verify.sh` release 기록에 시험이 없다 — PROGRESS :483-484 가 「손 확인」으로 두 번 닫았다.
- PLAN 8-10 행 「rules/ 추적 파일 0」(지금은 `rules/example.yaml` 추적) · 11장 javadoc 다섯(실제 여섯, poi-ooxml) · 0-53b·설계 21 :176-181 의 레인별 도장 글(코드는 `release` 한 줄) — 완료 행·완료 설계는 안 고친다.
- `package-check.sh` :37 `-maxdepth 1` — `profiles/` 하위 폴더의 yaml 은 안 본다. `ProfileStore` 도 한 단계만 읽으니 명세 안.
- 픽스처 `fixtures/check/common/pos.properties`(`db.password=secret01`)·`security/pos.java`(`"p@ssw0rd!"`) 가 zip 에 든다 — 규칙 양성 예. 반입 심사의 비밀 문자열 검사에 걸릴 수 있다 → 사람 몫(끝 보고에).

## 3. 설계 결정

- [고정] **②는 `offline-build.sh --check` 와 같은 꼴로 m2/ 오프라인 빌드** — `MAVEN_USER_HOME="$(winpath "$R/m2/.mvn-home")" bash scripts/mvn.sh -q -B -o -Dmaven.repo.local="$R/m2" -DskipTests package`. 버린 대안: ② 를 지우고 `--check` 가 만든 `target/app.jar` 를 쓰기 — `--check` 가 `verify` 를 돌려 jar 가 생기는 건 부작용이라 거기 기대지 않는다. 더 짧아 보여도 바꾸지 않는다.
- [고정] **라이선스 고지는 `git ls-files` 목록에 `LICENSE bundle` 을 더해서** 같은 상대 경로로 넣는다(`bundle/SOURCES.md`·`bundle/MANIFEST`·레시피 pom 둘). 버린 대안: `docs/THIRD-PARTY.md` 로 모아 쓰기 — 글을 새로 쓰면 SOURCES.md 와 중복(글 작성 규칙 「중복 금지」).
- [고정] **복사 실패는 `|| { echo "[빨강] …"; exit 1; }` 로 그 자리에서 멈춘다.** `set -e` 로 바꾸지 않는다 — 기존 `[ … ] || …`·`grep -q` 패턴이 많아 `-e` 는 뜻밖의 종료를 만든다.
- [고정] **`--with` 는 저장소 안 상대 경로만.** 앞 `./` 와 끝 `/` 는 벗기고(정규화), 빈 값·`/`·`[A-Za-z]:` 시작·`..` 성분은 exit 2. 역슬래시 경로는 따로 안 막는다 — `[ -e ]` 에서 「경로가 없다」로 이미 멈춘다.
- [고정] **`.sha256` 은 `sha256sum -c` 꼴** 한 줄(`<해시>  <zip 이름>`), zip 과 같은 폴더. `.gitignore` 에 `toolbox-server-*.zip.sha256` 을 더한다(안 더하면 두 번째 실행에서 ①의 깨끗한 트리 검사가 막힌다 — `/app.jar` 와 같은 교훈, PR #38 리뷰 6차).
- [고정] **무대는 zip 성공 뒤에만 `rm -rf "$base"`.** 실패 경로의 `exit 1` 들은 무대를 남긴다(디버깅용). `rm -rf "$base"` 가 이미 ③ 앞에 있으니 다음 실행이 치운다.
- [고정] **옛 zip 은 알림만.** `[ -n … ] && echo` 가 마지막 명령이 되면 종료 코드가 1 이 되니 `if` 로 쓴다.
- package-check 필수에 더하는 것은 **런타임·심사에 쓰이는 것만**: `LICENSE bundle/SOURCES.md README.md pure/toolbox.html profiles/example.yaml docs/dependencies.txt fixtures/README.txt` + `docs/javadoc/*.jar`. `PLAN.md`·`bundle/MANIFEST`·레시피 pom 은 안 더한다(없어도 현장이 안 깨진다). 버린 대안: 추적 파일 전부를 MANIFEST 와 맞대기 — 리허설이 푼 폴더에서 다시 재는 데 비싸다.
- 청크 둘로 나눈다 — 8-16a(package.sh + .gitignore) · 8-16b(package-check.sh + hooks-test.sh + README.md). 각각 파일 1~3개, 서로 독립 검증.

## 4. 실행 스텝

### S0 — 설계 반영(문서만, 코드 없음)
- 대상: `design/28-bundle44.md`(새) · `PLAN.md` · `PROGRESS.md`
- 변경:
  1. 이 플랜 파일(`C:\Users\EJG\.claude\plans\plan-fable-velvety-ripple.md`)을 `design/28-bundle44.md` 로 **그대로 복사**한다. 첫 줄은 그대로 둔다.
  2. `PLAN.md` 번들 표 — `| 번들 43 | …` 행(:521) 바로 아래에 한 행:
     ```
     | 번들 44 | `S0`→`8-16a`→`8-16b` | 2026-10-20(Fable, 설계 28 — `design/28-bundle44.md`). **반입 zip 손질** — `package.sh` 복사 가드·`--with` 검증·m2 오프라인 빌드·라이선스 고지 동봉(`LICENSE`·`bundle/`)·`.zip.sha256`·성공 시 무대 삭제·옛 zip 알림, `package-check.sh` 필수 추가. 마무리에 `--full` → package → rehearse 실측 |
     ```
  3. `PLAN.md` 분할표 — `| 5-31 | …` 행(:891) 바로 아래에 두 행:
     ```
     | 8-16a | `package.sh` 손질 — 복사 가드 · `--with` 상대 경로 검증 · ② m2 오프라인 빌드 · `LICENSE`·`bundle/` 동봉 · `.zip.sha256` · 성공 시 무대 삭제 · 옛 zip 알림 | 설계 28 §4 8-16a. 대기 |
     | 8-16b | `package-check.sh` 필수 추가(LICENSE·SOURCES.md·README·pure·example 프로필·dependencies.txt·fixtures README·javadoc jar) + hooks-test 케이스 + README 「만드는 쪽」·푸는 경로 | 설계 28 §4 8-16b. 대기 |
     ```
  4. `PLAN.md` 11장(:398-421 코드 블록) — `  PLAN.md  README.md` 줄 아래에 한 줄, `MANIFEST.txt` 줄 끝에 덧말:
     ```
       LICENSE  bundle/       이 도구의 라이선스(Apache-2.0) · 동봉 JDK·드라이버·javadoc 의 출처·판·라이선스(SOURCES.md)·지문(MANIFEST)·레시피 pom — 반입 심사용(8-16a)
       MANIFEST.txt             버전·커밋·파일마다 sha256 — 반입 검토 제출용. zip 자체의 sha256 은 zip 옆 `toolbox-server-<날짜>.zip.sha256`(8-16a)
     ```
  5. `PROGRESS.md` 이력 끝에 한 줄(4열: 날짜 · 제목 · 서사 · 해시). 서사에 「사용자 결정」 셋(위 머리)과 「드러난 것 — 안 함」 넷을 적는다. 해시 칸은 커밋 뒤 다음 줄에서 채운다(기존 관행).
- 검증: `bash scripts/verify.sh` 초록(docs 레인 — `doc-lint.sh` 존댓말). `grep -c '^| 8-16' PLAN.md` → `2`. `test -f design/28-bundle44.md`.
- 커밋: `docs: S0 번들 44 설계 28 반영`
- 의존: 없음

### 8-16a — `scripts/package.sh` + `.gitignore`
- 대상: `scripts/package.sh` · `.gitignore`
- 변경(위에서 아래 순서로. 줄 번호는 지금 파일 기준):
  1. 머리 주석 :5 를 `# 결과: toolbox-server-<yyyymmdd>.zip + .zip.sha256(저장소 루트) — MANIFEST.txt(버전·커밋·파일마다 sha256)·LICENSE·bundle/(동봉물 출처·라이선스) 동봉. 성공하면 무대 target/bundle/ 은 지운다.` 로.
  2. :9 `R=$(pwd)` 아래에:
     ```bash
     winpath() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi; } # wrapper 는 자바라 Windows 경로를 준다(offline-build.sh 와 같다)
     ```
  3. 인자 파싱 :11-16 을 통째로:
     ```bash
     while [ $# -gt 0 ]; do
       case "$1" in
         --with)
           w=${2:?--with <상대경로>}
           w=${w#./}; w=${w%/}
           # 저장소 안 상대 경로만 — 절대 경로·드라이브·`..` 는 cp --parents 가 무대 밖 꼴로 놓거나 package-check 의 허용 목록과 어긋난다
           case "$w" in
             ""|/*|[A-Za-z]:*|..|../*|*/..|*/../*) echo "[빨강] --with 는 저장소 안 상대 경로만: $2"; exit 2 ;;
           esac
           with+=("$w"); shift 2 ;;
         *) echo "모르는 인자: $1"; exit 2 ;;
       esac
     done
     ```
  4. ② :28-30 을:
     ```bash
     # ② app.jar — 현장 build.bat 과 같은 저장소(m2/)·배포본(.mvn-home)·오프라인(-o). ~/.m2 로 만들면 동봉 jar 와 현장 재빌드가 다른 좌표를 쓸 수 있다
     echo "== app.jar (m2/ 오프라인)"
     MAVEN_USER_HOME="$(winpath "$R/m2/.mvn-home")" bash scripts/mvn.sh -q -B -o -Dmaven.repo.local="$R/m2" -DskipTests package || { echo "[빨강] 빌드"; exit 1; }
     ```
  5. ③ :37 을 `rm -rf "$base" && mkdir -p "$stage" || { echo "[빨강] 무대 만들기: $stage"; exit 1; }`.
  6. ③ :39 `git ls-files -z -- src pom.xml … PLAN.md docs \` 의 목록 끝(`docs` 뒤)에 ` LICENSE bundle` 을 더한다.
  7. ③ :42-44 를:
     ```bash
     cp target/app.jar "$stage/app.jar" || { echo "[빨강] app.jar 복사"; exit 1; }
     for d in jre drivers m2; do cp -r "$d" "$stage/" || { echo "[빨강] $d/ 복사 — bash scripts/bundle-fetch.sh(네트워크)"; exit 1; }; done
     mkdir -p "$stage/docs" && cp -r docs/javadoc "$stage/docs/" || { echo "[빨강] docs/javadoc 복사"; exit 1; }
     ```
  8. ③ :47-48 을:
     ```bash
     mkdir -p "$stage/fixtures" && cp -r src/test/resources/fixtures/check src/test/resources/fixtures/analyze "$stage/fixtures/" || { echo "[빨강] fixtures 복사"; exit 1; }
     ```
  9. ③ :50 을 `mkdir -p "$stage/data" "$stage/logs" "$stage/out" "$stage/rules" || { echo "[빨강] 빈 폴더"; exit 1; }`.
  10. ⑥ :86-89 를:
      ```bash
      "$JAR" --create --no-manifest --file "$R/$name.zip" -C "$base" "$name" || { echo "[빨강] zip"; exit 1; }
      nfiles=$(find "$stage" -type f | wc -l)
      sha=$(sha256sum < "$R/$name.zip" | cut -d' ' -f1)
      printf '%s  %s\n' "$sha" "$name.zip" > "$R/$name.zip.sha256" # sha256sum -c 꼴 — 반입 심사 서류용
      rm -rf "$base" # 무대는 zip 에 다 들어갔다 — --with 로 넣은 실제 프로필을 target/ 에 안 남긴다. 실패하면 위 exit 들이 무대를 남긴다
      size=$(du -h "$R/$name.zip" | cut -f1)
      echo "반입 zip: $name.zip — $size · 파일 $nfiles · sha256 $sha · 옆에 $name.zip.sha256"
      others=$(ls "$R"/toolbox-server-*.zip 2>/dev/null | grep -v "/$name\.zip\$" | xargs -r -n1 basename)
      if [ -n "$others" ]; then echo "옛 zip 이 남아 있다 — 실제 프로필을 넣었던 것이면 지운다: $(echo $others)"; fi
      echo "첫 반입이면 src/main/resources/db/released-baseline 에 $(git rev-parse HEAD) 를 적는다(PLAN 16장 끝)"
      ```
      (`$(echo $others)` 는 줄바꿈을 공백으로 — 따옴표 없이 둔다.)
  11. `.gitignore` :26 `toolbox-server-*.zip` 아래에 `toolbox-server-*.zip.sha256`, :24 주석 끝에 ` · 8-16a 의 .zip.sha256 도 같다`.
  - 역슬래시가 든 글은 없다. 그래도 편집은 Edit 도구로(heredoc 이 `\` 를 벗긴다 — 메모리).
- 검증:
  - `bash -n scripts/package.sh` → 출력 없음.
  - `bash scripts/package.sh --with C:/x; echo rc=$?` → `[빨강] --with 는 저장소 안 상대 경로만: C:/x` · `rc=2`. `--with ../x` · `--with /c/x` · `--with ""` 도 각각 `rc=2`.
  - `bash scripts/package.sh --with ./profiles/example.yaml/; echo rc=$?` → 파싱은 지나고 ①에서 멈춘다(`[빨강] 작업 트리가 깨끗하지 않다` 또는 `[빨강] 반입 zip 은 전부 검증 뒤`) · `rc=1`. `--with 는 …` 글이 **없어야** 한다.
  - `git check-ignore -q toolbox-server-20261020.zip.sha256 && echo ok` → `ok`.
  - `bash scripts/verify.sh` 초록(tools 레인 — 문법 + hooks-test. hooks-test 는 아직 그대로).
  - 끝까지 실행(②~⑥)은 6장에서 `--full` 뒤 한 번.
- 커밋: `fix: 8-16a package.sh 복사 가드·--with 검증·m2 오프라인 빌드·라이선스 동봉·.sha256`
- 의존: S0(행이 있어야 분할표를 「완료」로 고친다). 코드는 S0 와 무관.

### 8-16b — `scripts/package-check.sh` + `scripts/hooks-test.sh` + `README.md`
- 대상: `scripts/package-check.sh` :41 필수 목록 · `scripts/hooks-test.sh` :273-274 최소 무대, :307 뒤 새 케이스, :319-322 뒤 무시 케이스 · `README.md` :7, :66, :71
- 변경:
  1. `package-check.sh` :41 `for f in app.jar run.bat toolbox.bat build.bat MANIFEST.txt jre/bin/javac.exe jre/bin/java.exe mvnw.cmd pom.xml; do` 의 목록 끝에 ` LICENSE bundle/SOURCES.md README.md pure/toolbox.html profiles/example.yaml docs/dependencies.txt fixtures/README.txt` 를 더한다. :45 `ls drivers/*.jar …` 줄 아래에:
     ```bash
     ls docs/javadoc/*.jar >/dev/null 2>&1 || { echo "  [없음] docs/javadoc/*.jar"; fail=1; }
     ```
     :40 주석 `# 있어야 하는 것` 을 `# 있어야 하는 것 — 기동·현장 빌드에 드는 것 + 반입 심사가 보는 것(LICENSE·bundle/SOURCES.md, 8-16b)` 로.
  2. `hooks-test.sh` :273 `mkdir -p …` 에 `"$PK/bundle" "$PK/pure" "$PK/docs/javadoc" "$PK/fixtures"` 를 더하고, :274 `for f in … profiles/example.yaml; do` 목록 끝에 ` LICENSE bundle/SOURCES.md README.md pure/toolbox.html docs/dependencies.txt fixtures/README.txt docs/javadoc/x-javadoc.jar` 를 더한다(`echo x >` 로 같이 만들어진다).
  3. `hooks-test.sh` :307(`pk_case "package.sh 가 남긴 --with 목록의 프로필은 초록" …` 줄) 아래에:
     ```bash
     # 8-16b — 심사가 보는 것·런타임이 읽는 것이 빠지면 빨강(package.sh 의 cp 가드가 놓친 것을 푼 폴더에서 다시 잰다)
     mv "$PK/bundle/SOURCES.md" "$PK/bundle/SOURCES.off"; pk_case "bundle/SOURCES.md 없으면 빨강" 1; mv "$PK/bundle/SOURCES.off" "$PK/bundle/SOURCES.md"
     mv "$PK/docs/javadoc" "$PK/docs/javadoc.off"; pk_case "docs/javadoc jar 없으면 빨강" 1; mv "$PK/docs/javadoc.off" "$PK/docs/javadoc"
     pk_case "되돌리면 초록 (8-16b)" 0
     ```
  4. `hooks-test.sh` :322 뒤(`else echo "  [실패] $n 루트 app.jar 가 …"; fail=1; fi` 다음)에:
     ```bash
     # 8-16a — package.sh 가 zip 옆에 쓰는 .zip.sha256 도 무시된다(두 번째 실행의 깨끗한 트리 검사)
     n=$((n + 1))
     if (cd "$R" && git check-ignore -q toolbox-server-20991231.zip.sha256); then echo "  [통과] $n 루트 .zip.sha256 은 git 무시"
     else echo "  [실패] $n 루트 .zip.sha256 이 무시되지 않는다 — 두 번째 package.sh 가 막힌다"; fail=1; fi
     ```
  5. `README.md`:
     - :7(「현장에서 켜기」 1) 문장 끝에 ` 경로는 짧게 — 깊은 폴더(전체 200자 넘게)에 풀면 \`m2\\\` 안의 긴 경로가 Windows 260자 한도에 걸린다` 를 더한다(백틱 안 `m2\` — Edit 도구로).
     - :66 `bash scripts/package.sh [--with 경로]   toolbox-server-<날짜>.zip — 실제 사업 프로필은 --with` 를 `bash scripts/package.sh [--with 경로]   toolbox-server-<날짜>.zip + .zip.sha256 — 실제 사업 프로필은 --with(저장소 안 상대 경로)` 로.
     - :71 문장 앞에 한 문장: `라이선스 고지는 \`LICENSE\`(이 도구, Apache-2.0)와 \`bundle/SOURCES.md\`(동봉 JDK·드라이버·javadoc 의 출처·판·라이선스) — 둘 다 zip 에 든다.`
- 검증:
  - `bash scripts/hooks-test.sh` → 끝 줄 초록, `[실패]` 0. 새 케이스 셋 + 무시 케이스 하나가 `[통과]` 로 보인다.
  - 수동: `bash scripts/package-check.sh target/bundle/toolbox-server-20261004` → `[없음] LICENSE` · `[없음] bundle/SOURCES.md` 등이 찍히고 `묶음 검사 빨강`(옛 무대라 당연히 빨강 — 새 필수가 작동한다는 확인). 이 무대는 6장의 package.sh 가 `rm -rf "$base"` 로 치운다.
  - `bash scripts/verify.sh` 초록(tools + docs 레인).
  - `ReadmeCommandsTest` 가 README 의 `scripts/` 줄은 건너뛰니 README 변경이 자바 시험을 안 깨운다(`bash scripts/mvn.sh -q -B -Dtest=ReadmeCommandsTest test` 로 확인해도 된다, 선택).
- 커밋: `test: 8-16b package-check 필수 추가 + hooks-test + README`
- 의존: 8-16a(`.gitignore` 줄이 있어야 무시 케이스가 초록).

### 분할표·이력
각 청크 커밋 뒤 `PLAN.md` 해당 행의 상태를 `대기` → **사실**(한 줄 요약) 으로, `PROGRESS.md` 에 한 줄(해시까지). PLAN·PROGRESS 는 청크 파일 수에 안 넣는다(CLAUDE.md).

## 5. 금지 사항

- `pure/` · `design/27-bundle43.md` 이하 완료 설계 · 분할표 완료 행(8-10·0-53b 등) · 설계 21 본문 — 안 고친다.
- `scripts/verify.sh` · `scripts/verify-fingerprint.sh` · `scripts/rehearse.sh` · `scripts/bundle-fetch.sh` · `scripts/offline-build.sh` · bat 셋 · 자바 코드 — 안 건드린다.
- `.git/verify-stamp` 에 손으로 `release` 줄을 쓰지 않는다. package.sh 끝까지 돌리려면 `verify.sh --full` 을 진짜로 돈다.
- 옛 zip(`toolbox-server-20261003.zip`·`20261004.zip`)을 지우지 않는다. `target/bundle/toolbox-server-20261004/` 도 손으로 지우지 않는다(새 package.sh 의 `rm -rf "$base"` 가 치운다).
- `docker rm toolbox-demo-ora` 금지(볼륨 없음 — 데이터가 사라진다). `docker stop` 만.
- 새 의존성 없음. `set -e` 로 바꾸지 않는다. hooks-test 기존 케이스의 기대값을 바꾸지 않는다.
- 맨몸 `./mvnw` 금지 — `bash scripts/mvn.sh`. `verify.sh` 와 `git commit`, `--full`·`--db`·`--corpus` 와 `git push` 는 **따로 낸다**(훅).
- 외부 통신 — 런타임은 0. 만드는 쪽 스크립트 중 네트워크를 쓰는 것은 `bundle-fetch.sh`(인자 없음)·`offline-build.sh`(인자 없음)만, 그것도 ①이 빨강일 때 재료를 다시 채우는 용도로만.

## 6. 최종 검증

순서대로. 1~3 은 무거운 준비, 4 부터가 이 번들의 판정.

1. 데모 서버·컨테이너 끄기 — `netstat -ano | grep ':41790 '` 에 LISTENING 이 있으면 그 PID 를 `taskkill //F //T //PID <pid>`. `docker ps --filter name=toolbox-demo-ora -q` 가 비어 있지 않으면 `docker stop toolbox-demo-ora`. Docker Desktop 은 켜 둔다(Testcontainers). 메모리 여유를 본다(메모리 「full 검증과 메모리 겹침」) — 남은 Maven·테스트 JVM 이 있으면 `bash scripts/reap-maven.sh`.
2. `bash scripts/verify.sh --full`(약 20분 · Docker · `C:/workspace/toolbox-corpus` · node 필요). 끝 줄 초록, `.git/verify-stamp` 에 `release <트리> full` 한 줄. 빨강이면 고치기 둘까지(번들 규칙), 원인이 이 번들 밖(DB·표본)이면 7장으로.
3. (조건부) 4 의 ①에서 `offline-build.sh --check` 가 빨강이면 `bash scripts/offline-build.sh`(네트워크) 한 번 뒤 다시. 2026-10-20 확인으로는 m2/ 가 낡지 않아 안 일어날 것으로 본다.
4. `bash scripts/package.sh --with profiles/demo.yaml` → 기대:
   - `== app.jar (m2/ 오프라인)` 줄 뒤 빨강 없음.
   - 끝 줄 `반입 zip: toolbox-server-<날짜>.zip — … · 옆에 toolbox-server-<날짜>.zip.sha256`.
   - `옛 zip 이 남아 있다 … : toolbox-server-20261003.zip toolbox-server-20261004.zip`.
   - `test ! -d target/bundle && echo gone` → `gone`.
   - `sha256sum -c toolbox-server-<날짜>.zip.sha256` → `…: OK`.
   - `jre/bin/jar.exe tf toolbox-server-<날짜>.zip | grep -cE '^toolbox-server-[0-9]+/(LICENSE|bundle/SOURCES\.md|bundle/MANIFEST|PACKAGED-WITH\.txt|profiles/demo\.yaml)$'` → `5`. `… | grep -c '/\.git/'` → `0`.
   - `git status --porcelain` → 비어 있다(zip·.sha256 둘 다 무시).
5. `bash scripts/rehearse.sh toolbox-server-<날짜>.zip` → `리허설 통과 — 일곱 단계`. ①의 package-check 가 새 필수(LICENSE 등)를 푼 폴더에서 다시 잰다.
6. `bash scripts/rehearse.sh toolbox-server-<날짜>.zip --break jre` → `깨 보기 통과`(강제 지점 회귀).
7. 회귀 — `bash scripts/gate-probe.sh`(마무리 2) 아홉 초록. 「패치 갱신 필요」면 다시 떠서 커밋.
8. 끝 보고에 적는 것: 만든 zip 이름·크기·sha256 · zip 에 `profiles/demo.yaml`(데모 컨테이너 비밀번호)이 들었으니 **리허설용이고 반입용이 아니다** — 반입 zip 은 사람이 실제 프로필로 다시 만든다 · 옛 zip 둘은 사람이 지운다 · 픽스처의 비밀 문자열 양성 예가 심사에 걸릴 수 있다.

그 뒤 CLAUDE.md 「마무리」 1~8(독립 리뷰 → gate-probe → verify → push → PR → CI 폴링 → 머지 위임 여부 → 끝 보고).

## 7. 중단 조건

다음이면 임의로 해결하지 말고 멈추고 보고:
- 플랜의 줄 번호·인용 글이 실제 `package.sh`·`package-check.sh`·`hooks-test.sh`·`README.md`·`PLAN.md` 와 다르다(이 플랜은 2026-10-20 `da0cd0e` 기준).
- 6장 2(`--full`)가 두 번 빨강이고 원인이 이 번들 파일 밖(DB 컨테이너·표본·메모리)이다 → 멈추고 보고. 그 경우 4~6 은 못 돌린 것으로 적는다(「통과」라고 쓰지 않는다).
- 6장 4 의 ② 빌드가 m2/ 오프라인에서 빨강인데 3 을 거쳐도 그렇다.
- 금지 사항을 어기지 않고는 진행 불가(예: `verify.sh` 를 고쳐야 한다).
- 스텝에 없는 파일을 3개 이상 고쳐야 한다.
보고 형식: 어느 스텝, 무엇이 예상과 달랐는지(명령·출력 그대로), 가능한 선택지 둘.

## 8. 불확실 항목

없음 — 설계 세션이 2026-10-20 에 다 확인했다(아래는 확인 결과. 실행자는 첫 줄만 다시 본다).
- `git rev-parse --short HEAD` = `da0cd0e`, `git status --porcelain` 비어 있음. **실행자는 첫 스텝 전에 이것만 다시 확인한다** — 다르면 2장의 줄 번호를 글로 다시 찾는다.
- `MAVEN_USER_HOME` 전달 — `mvnw` 가 `.mvn/wrapper/maven-wrapper.jar` 를 띄우고 그 자바가 환경변수를 읽는다(2장). ②가 `Downloading` 을 찍으면 7장.
- m2/ 낡지 않음 — pom 마지막 변경(10/07)의 의존성 둘이 m2/ 에 있다(2장).
- jar 가 만든 zip 의 한글 이름·빈 폴더 — Explorer zip 핸들러(`Shell.Application`)·.NET·Info-ZIP 셋으로 실측 통과(2장).
- `--full` 전제 — Docker 데몬 응답(컨테이너 떠 있음), `C:/workspace/toolbox-corpus` 있음, node v22.23.2. 20분은 **이 번들에서 돌린다**(사용자 결정).
- 「새 PC·네트워크 차단·AppLocker·Tibero」는 여전히 사람 몫(PLAN 16장 끝) — 이 플랜의 불확실이 아니라 반입 체크리스트다.
