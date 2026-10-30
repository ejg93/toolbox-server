#!/usr/bin/env bash
# 실물 표본 받기(V-1, PLAN 4장). 사람이 이전에 만든 코드·SQL·JSP 를 저장소 밖 폴더로 잘라 온다 — 표본은 커밋하지 않는다.
# 저장소에는 이 레시피와 corpus/SOURCES.md(출처·라이선스), corpus/MANIFEST(출처별 지문)만 둔다.
#
#   bash scripts/corpus-fetch.sh              전부
#   bash scripts/corpus-fetch.sh egov jspwiki 골라서
#   bash scripts/corpus-fetch.sh --manifest   받지 않고 지문만 다시(손으로 넣은 출처 — csv 등)
#
# 폴더: $TOOLBOX_CORPUS, 없으면 저장소 형제 toolbox-corpus. 받은 저장소 캐시: 그 옆 .corpus-src(다시 받지 않는다).
# 받기는 고정 태그·커밋만 — 원본이 바뀌어도 표본은 안 바뀐다. core.autocrlf=false 로 받아 바이트가 저장소 원본 그대로.
# 지문: 출처마다 「sha256  상대경로」 줄을 LC_ALL=C 로 정렬해 그 목록의 sha256 한 줄. 파일별 목록은 표본 폴더 .manifest/ 에.
set -uo pipefail
set -f   # pathspec 의 * 를 셸이 먼저 풀지 않게 — git ls-files 가 푼다
R=$(cd "$(dirname "$0")/.." && pwd)
CORPUS="${TOOLBOX_CORPUS:-$(dirname "$R")/toolbox-corpus}"
CACHE="$(dirname "$CORPUS")/.corpus-src"
MANIFEST="$R/corpus/MANIFEST"

# 이름|저장소|태그 또는 커밋|잘라 올 pathspec(공백 구분, git ls-files 문법)
SOURCES=(
  "egov|eGovFramework/egovframe-common-components|v5.0.6|src/main/java/**/*.java src/main/webapp/**/*.jsp src/main/webapp/**/*.js src/main/webapp/**/*.css src/main/resources/egovframework/mapper/**/*.xml src/main/resources/**/*.properties script/**/*.sql src/script/ddl/**/*.sql"
  "egov-prev|eGovFramework/egovframe-common-components|v5.0.5|src/main/java/**/*.java src/main/webapp/**/*.jsp src/main/resources/egovframework/mapper/**/*.xml script/**/*.sql"
  "egov-portal|eGovFramework/egovframe-portal-site-template|249e819941624027bbe8a13180a057da5cdadb59|**/*.jsp **/*.java"
  "egov-enterprise|eGovFramework/egovframe-enterprise-business-template|ab78fc9501f0604395eac0fb6431774f053bfa69|**/*.jsp **/*.java"
  "egov-homepage|eGovFramework/egovframe-simple-homepage-template|9dacccbce18f4d7636c955fd1ce74465d25de617|**/*.jsp **/*.java"
  "egov-react|eGovFramework/egovframe-template-simple-react|957d9b1b9fbb265a1ed38a1de6582054938594dd|src/**/*.jsx src/**/*.js"
  "jspwiki|apache/jspwiki|2.12.5|**/*.jsp **/src/main/java/**/*.java"
  "roller|apache/roller|a944dcb5ecc82bbad8f9509fea6bcfe3f13007c4|**/*.jsp **/src/main/java/**/*.java"
  "struts|apache/struts|3e428e43879548af39293dd74a92e2e5ccfbef56|**/*.jsp core/src/main/java/**/*.java"
  "commons-lang|apache/commons-lang|rel/commons-lang-3.20.0|src/main/java/**/*.java"
  "commons-io|apache/commons-io|rel/commons-io-2.22.0|src/main/java/**/*.java"
  "commons-collections|apache/commons-collections|rel/commons-collections-4.6.0|src/main/java/**/*.java"
  "db-samples|oracle-samples/db-sample-schemas|v23.3|human_resources/*.sql order_entry/*.sql sales_history/*.sql customer_orders/*.sql"
  "chinook|lerocha/chinook-database|v1.4.5|ChinookDatabase/DataSources/*.sql"
  "k8s-examples|kubernetes/examples|d6b8cd27eacb51e651a1aa6f7c190a28713eff6e|**/*.yaml **/*.yml **/*.sh"
  "kafka|apache/kafka|41a8c21f21eaaba267e7485be35ee400917a359f|**/*.sh **/*.bat **/*.cmd"
  "bootstrap5|twbs/bootstrap|v5.3.8|scss/**/*.scss"
  "bootstrap3|twbs/bootstrap|v3.4.1|less/**/*.less"
  "jsontest|nst/JSONTestSuite|1ef36fa01286573e846ac449e8683f8833c5b26a|test_parsing/*.json test_transform/*.json"
  "shopizer|shopizer-ecommerce/shopizer|6a4a0a65a3408ee8f62597b51d1b3aac24b77dee|sm-core-model/src/main/java/**/*.java sm-core/src/main/java/**/*.java sm-shop/src/main/java/**/*.java"
  "egov-msa|eGovFramework/egovframe-msa-common-components|4f5a895b3b1807da9c863884aba4650056e34237|**/src/main/java/**/*.java"
  "ruoyi|yangzongzhuan/RuoYi-Vue|13db1fcef36bee9ce45d2d636a1d4e8f5ed5bbc3|**/src/main/java/**/*.java **/src/main/resources/mapper/**/*.xml sql/*.sql"
  # 설계 41(V-35) — 레거시 Spring MVC + MyBatis + JSP·Thymeleaf 넷. 노드 스크립트는 corpus-common.js SKIP 으로 안 걷는다(R3 가 승격)
  "jeecg|zhangdaiscott/jeecg|2608008efc47594c8920d4de74f3b3d475c348c2|src/main/webapp/**/*.jsp src/main/java/**/*.java **/*.ftl src/main/resources/**/*.xml docs/*.sql"
  "jeesite|thinkgem/jeesite|v1.2.7|src/main/webapp/**/*.jsp src/main/java/**/*.java src/main/resources/mappings/**/*.xml db/**/*.sql"
  "zheng|shuzheng/zheng|7005c0a775e6d014d1dc8a8a809f7b1c13bf785a|**/src/main/webapp/**/*.jsp **/src/main/java/**/*.java **/src/main/**/*Mapper.xml project-datamodel/*.sql"
  "ruoyi-classic|yangzongzhuan/RuoYi|9cd770d2ac839999d6a425f70f3bd2cf3a0ee5cd|**/src/main/java/**/*.java **/src/main/resources/mapper/**/*.xml **/src/main/resources/templates/**/*.html **/src/main/resources/vm/**/*.vm sql/*.sql"
  # 설계 41(V-36) — 보안 채점 정답지. GPL-2.0 — 표본 폴더에만 두고 골든은 파일 이름·건수. helpers 는 설계 43(V-39a) — 요청을 감싼 래퍼·Thing 클래스(파일 사이 오염), benchmark.properties 는 설계 44(V-40a) — 설정 값 알고리즘
  "benchmark|OWASP-Benchmark/BenchmarkJava|f40c618c5a709afce350ef06ed2899c85cf586e9|src/main/java/org/owasp/benchmark/testcode/*.java src/main/java/org/owasp/benchmark/helpers/*.java src/main/resources/benchmark.properties expectedresults-1.2.csv"
  # 설계 41(V-37) — SQL 파스 트리 정답지(방언 여덟 + 규칙 시험 yml)
  "sqlfluff-dialects|sqlfluff/sqlfluff|4.4.0|test/fixtures/dialects/ansi/* test/fixtures/dialects/oracle/* test/fixtures/dialects/mysql/* test/fixtures/dialects/mariadb/* test/fixtures/dialects/postgres/* test/fixtures/dialects/tsql/* test/fixtures/dialects/sqlite/* test/fixtures/dialects/db2/* test/fixtures/rules/std_rule_cases/*.yml"
)
# 손으로 넣는 출처 — 받지 않고 지문만(폴더가 있으면)
MANUAL=(csv)
# 메이븐 레시피로 받는 선택 출처(V-17) — 이름|레시피 pom. MANIFEST 밖이다: 없는 PC 에서 다른 표본 테스트를 막지 않게.
# 좌표·버전이 레시피에 고정이라 지문 없이도 재현된다. 이름을 골라야만 받는다(전부 받기에 안 든다)
MAVEN=("egov35-lib|corpus/egov35-lib/pom.xml" "jspwiki-bin|corpus/jspwiki-bin/pom.xml")
# 빌드해서 받는 선택 출처(V-29) — 이름|SOURCES 이름. 캐시 클론을 저장소 Maven(JDK 17)으로 package 해 모듈의 target/classes 를 합치고
# 실행 jar 의 BOOT-INF/lib 를 lib/ 에 둔다(SpotBugs 입력 꼴, 설계 34). MANIFEST 밖 — 빌드 산출물은 바이트 재현을 약속하지 않는다.
# 이름을 골라야만 받고, 그 SOURCES 출처를 먼저 받아 둬야 한다
BUILD=("ruoyi-bin|ruoyi")
# zip 으로 받는 선택 출처(설계 41 V-36b) — 이름|URL|zip sha256|zip 안 접두(최상위 폴더 아래)|폴더 글롭(공백 구분). MANIFEST 밖 — 없는 PC 에서 다른 표본 시험을 막지 않게.
# 이름을 골라야만 받는다. zip 은 캐시에 두고 다시 받지 않는다
ZIP=("juliet|https://samate.nist.gov/SARD/downloads/test-suites/2017-10-01-juliet-test-suite-for-java-v1-3.zip|d985f4177c2bcd7b03455a05c1c8f2e755f55c9eb250accd052f05f877347e60|src/testcases|CWE78_* CWE89_* CWE23_* CWE36_* CWE327_* CWE328_* CWE338_* CWE601_* CWE113_* CWE259_* CWE321_* CWE209_* CWE80_* CWE81_* CWE83_*")

fetch_one() { # 이름 저장소 참조 pathspec…
  local name=$1 repo=$2 ref=$3; shift 3
  local src="$CACHE/$name@${ref//\//_}"
  if [ ! -d "$src/.git" ]; then
    echo "  받기 $repo @ $ref"
    rm -rf "$src"; mkdir -p "$src"
    git -C "$src" init -q
    git -C "$src" config core.autocrlf false
    git -C "$src" remote add origin "https://github.com/$repo.git"
    if ! git -C "$src" fetch -q --depth 1 origin "$ref" 2>/dev/null; then
      git -C "$src" fetch -q --depth 1 origin "refs/tags/$ref:refs/tags/$ref" || { echo "  [실패] $name — $ref 를 못 받았다" >&2; return 1; }
    fi
    git -C "$src" -c advice.detachedHead=false checkout -q FETCH_HEAD 2>/dev/null || git -C "$src" -c advice.detachedHead=false checkout -q "$ref"
  fi
  rm -rf "${CORPUS:?}/$name"; mkdir -p "$CORPUS/$name"
  # shellcheck disable=SC2086
  (cd "$src" && git ls-files -z -- $@) | (cd "$src" && xargs -0 -r cp --parents -t "$CORPUS/$name")
  echo "  $name: $(find "$CORPUS/$name" -type f | wc -l) 파일"
}

post_maven() { # 이름 — jspwiki-bin: 본 jar 를 classes/ 로 풀고 나머지는 lib/ 로(SpotBugs 입력 = 클래스 폴더 + aux jar 폴더)
  local dir="$CORPUS/$1" j
  [ "$1" = jspwiki-bin ] || return 0
  set +f  # 맨 위 set -f(pathspec 보호)가 jar 글롭을 막는다
  mkdir -p "$dir/classes" "$dir/lib"
  for j in "$dir"/*.jar; do
    case "$(basename "$j")" in
      jspwiki-main-*.jar) unzip -q -o "$j" -d "$dir/classes" && rm -f "$j" ;;
      *) mv "$j" "$dir/lib/" ;;
    esac
  done
  echo "  $1: 클래스 $(find "$dir/classes" -name '*.class' | wc -l) · lib $(find "$dir/lib" -name '*.jar' | wc -l) jar"
  set -f
}

manifest_one() { # 이름 → 「이름 파일수 sha256」
  local name=$1 list
  [ -d "$CORPUS/$name" ] || return 0
  mkdir -p "$CORPUS/.manifest"
  list=$(cd "$CORPUS/$name" && find . -type f -print0 | LC_ALL=C sort -z | xargs -0 -r sha256sum | sed -E 's#^([0-9a-f]+) [ *]\./#\1  #')  # Git Bash 는 「hash *./경로」(바이너리 표시)
  printf '%s\n' "$list" > "$CORPUS/.manifest/$name.txt"
  echo "$name $(printf '%s\n' "$list" | grep -c .) $(printf '%s\n' "$list" | sha256sum | cut -d' ' -f1)"
}

mkdir -p "$CORPUS" "$CACHE" "$R/corpus"
want=("$@")
if [ "${1:-}" != "--manifest" ]; then
  for s in "${SOURCES[@]}"; do
    IFS='|' read -r name repo ref paths <<< "$s"
    if [ ${#want[@]} -gt 0 ] && [[ " ${want[*]} " != *" $name "* ]]; then continue; fi
    # shellcheck disable=SC2086
    fetch_one "$name" "$repo" "$ref" $paths || exit 1
  done
fi
for m in "${MAVEN[@]}"; do
  IFS='|' read -r name pom <<< "$m"
  [[ " ${want[*]} " == *" $name "* ]] || continue
  echo "  받기 $name ← $pom"
  rm -rf "${CORPUS:?}/$name"; mkdir -p "$CORPUS/$name"
  bash "$R/scripts/mvn.sh" -q -B -f "$R/$pom" dependency:copy-dependencies -DoutputDirectory="$CORPUS/$name"       -Dmaven.repo.local="$CACHE/m2-$name" || { echo "  [실패] $name — 받지 못했다(네트워크·egovframe 저장소)" >&2; exit 1; }
  echo "  $name: $(find "$CORPUS/$name" -name '*.jar' | wc -l) jar"
  post_maven "$name"
done
for b in "${BUILD[@]}"; do
  IFS='|' read -r name from <<< "$b"
  [[ " ${want[*]} " == *" $name "* ]] || continue
  line=$(printf '%s\n' "${SOURCES[@]}" | grep "^$from|")
  IFS='|' read -r _ _ ref _ <<< "$line"
  src="$CACHE/$from@${ref//\//_}"
  [ -f "$src/pom.xml" ] || { echo "  [실패] $name — 먼저 $from 을 받는다(bash scripts/corpus-fetch.sh $from)" >&2; exit 1; }
  echo "  빌드 $name ← $from (수 분 — 의존성 받기)"
  bash "$R/scripts/mvn.sh" -q -B -f "$src/pom.xml" package -DskipTests || { echo "  [실패] $name — 빌드" >&2; exit 1; }
  rm -rf "${CORPUS:?}/$name"; mkdir -p "$CORPUS/$name/classes" "$CORPUS/$name/lib"
  set +f  # 글롭(맨 위 set -f)
  for c in "$src"/*/target/classes; do [ -d "$c" ] && cp -r "$c"/. "$CORPUS/$name/classes/"; done
  for j in "$src"/*/target/*.jar; do unzip -q -o -j "$j" 'BOOT-INF/lib/*.jar' -d "$CORPUS/$name/lib" 2>/dev/null || true; done
  echo "  $name: 클래스 $(find "$CORPUS/$name/classes" -name '*.class' | wc -l) · lib $(find "$CORPUS/$name/lib" -name '*.jar' | wc -l) jar"
  set -f
done
for z in "${ZIP[@]}"; do
  IFS='|' read -r name url sha prefix globs <<< "$z"
  [[ " ${want[*]} " == *" $name "* ]] || continue
  zip="$CACHE/$name.zip"
  [ -f "$zip" ] || { echo "  받기 $name ← $url"; curl -fsSL -o "$zip.part" "$url" && mv "$zip.part" "$zip" || { rm -f "$zip.part"; echo "  [실패] $name — zip 을 못 받았다" >&2; exit 1; }; }
  echo "$sha  $zip" | sha256sum -c --quiet - || { echo "  [실패] $name — zip 지문이 다르다(캐시 $zip 를 지우고 다시)" >&2; exit 1; }
  top=$(unzip -Z1 "$zip" | head -1 | cut -d/ -f1)   # zip 안 최상위 폴더(Juliet 은 Java/)
  rm -rf "$CACHE/$name-x" "${CORPUS:?}/$name"; mkdir -p "$CORPUS/$name"
  for g in $globs; do   # 「CWE89_*」 → 그 이름으로 시작하는 폴더 아래 모든 .java(하위 폴더 s01… 포함 — Git Bash unzip 의 * 는 / 를 안 넘는다)
    list=$(unzip -Z1 "$zip" | grep -E "^$top/$prefix/${g%\*}[^/]*/.*\.java$" | grep -v '/antbuild/')   # antbuild/ = ant 틀(「package ;」 — 자바가 아니다)
    [ -n "$list" ] || { echo "  [실패] $name — $g 가 zip 에 없다" >&2; exit 1; }
    printf '%s\n' "$list" | tr '\n' '\0' | xargs -0 unzip -q -o "$zip" -d "$CACHE/$name-x" || { echo "  [실패] $name — $g 풀기" >&2; exit 1; }
  done
  cp -r "$CACHE/$name-x/$top/$prefix"/. "$CORPUS/$name/" || { echo "  [실패] $name — 복사" >&2; exit 1; }
  rm -rf "$CACHE/$name-x"
  echo "  $name: $(find "$CORPUS/$name" -name '*.java' | wc -l) 파일"
done
{
  echo "# 실물 표본 지문(V-1) — scripts/corpus-fetch.sh 가 쓴다. 이름 파일수 sha256(「sha256  상대경로」 정렬 목록의)"
  for s in "${SOURCES[@]}"; do manifest_one "${s%%|*}"; done
  for m in "${MANUAL[@]}"; do manifest_one "$m"; done
} > "$MANIFEST"
echo "지문: $MANIFEST — $(grep -vc '^#' "$MANIFEST") 출처, $(awk '!/^#/{s+=$2} END{print s}' "$MANIFEST") 파일, $(du -sh "$CORPUS" | cut -f1)"
