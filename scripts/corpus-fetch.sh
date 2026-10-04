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
)
# 손으로 넣는 출처 — 받지 않고 지문만(폴더가 있으면)
MANUAL=(csv)
# 메이븐 레시피로 받는 선택 출처(V-17) — 이름|레시피 pom. MANIFEST 밖이다: 없는 PC 에서 다른 표본 테스트를 막지 않게.
# 좌표·버전이 레시피에 고정이라 지문 없이도 재현된다. 이름을 골라야만 받는다(전부 받기에 안 든다)
MAVEN=("egov35-lib|corpus/egov35-lib/pom.xml")

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
done
{
  echo "# 실물 표본 지문(V-1) — scripts/corpus-fetch.sh 가 쓴다. 이름 파일수 sha256(「sha256  상대경로」 정렬 목록의)"
  for s in "${SOURCES[@]}"; do manifest_one "${s%%|*}"; done
  for m in "${MANUAL[@]}"; do manifest_one "$m"; done
} > "$MANIFEST"
echo "지문: $MANIFEST — $(grep -vc '^#' "$MANIFEST") 출처, $(awk '!/^#/{s+=$2} END{print s}' "$MANIFEST") 파일, $(du -sh "$CORPUS" | cut -f1)"
