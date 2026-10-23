#!/usr/bin/env bash
# 실행 전용 반입 zip 만들기(8-10·9-4a, PLAN 11장·설계 31). 네트워크 없이 — 재료는 bundle-fetch.sh 가 미리 받아 둔다.
#   bash scripts/package.sh [--with <상대경로>]…
#     --with  실제 사업 파일(프로필·양식 등, .gitignore 대상)을 같은 상대 경로로 넣는다. 프로필의 dialect 로 드라이버를 고른다
# 결과: toolbox-server-<yyyymmdd>.zip + .zip.sha256(저장소 루트) — app.jar·jlink 런타임·드라이버·예시 설정·순수본·심의 자료·
# MANIFEST.txt(버전·커밋·동봉 jar 수·파일마다 sha256)·LICENSE·bundle/(동봉물 출처·라이선스). 소스·m2·빌드 도구·javadoc·픽스처는 안 넣는다.
# 성공하면 무대 target/bundle/ 은 지운다.
# 첫 반입이면 src/main/resources/db/released-baseline 에 커밋을 적는다(16장 끝) — 이 스크립트는 안 쓴다(리허설 zip 은 반입이 아니다).
set -uo pipefail
# 반입 런타임 모듈 — 이 한 줄이 원본(docs/import-review.md 8절·ImportReviewDocTest·hooks-test 가 읽는다). jdeps 합집합 + MS949·로캘·TLS·Unsafe(설계 31 §3)
JLINK_MODULES=java.base,java.compiler,java.desktop,java.instrument,java.management,java.naming,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.xml,java.xml.crypto,jdk.charsets,jdk.crypto.cryptoki,jdk.crypto.ec,jdk.localedata,jdk.net,jdk.security.jgss,jdk.unsupported,jdk.zipfs
cd "$(dirname "$0")/.."
R=$(pwd)
winpath() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi; } # wrapper 는 자바라 Windows 경로를 준다(offline-build.sh 와 같다)
with=()
while [ $# -gt 0 ]; do
  case "$1" in
    --with)
      [ $# -ge 2 ] || { echo "[빨강] --with <상대경로>"; exit 2; }
      w=$2
      w=${w#./}; w=${w%/}
      # 저장소 안 상대 경로만 — 절대 경로·드라이브·`..` 는 cp --parents 가 무대 밖 꼴로 놓거나 package-check 의 허용 목록과 어긋난다.
      # `.`(저장소 통째)·중간 `/./`·`//`(PACKAGED-WITH 가 문자열 비교라 어긋난다)·역슬래시(`[ -e ]` 는 받는데 cp --parents 가 깨진다, 마무리 리뷰)도 거부
      case "$w" in
        ""|.|/*|[A-Za-z]:*|..|../*|*/..|*/../*|*/.|*/./*|./*|*//*|*\\*) echo "[빨강] --with 는 저장소 안 상대 경로만(슬래시 구분, . · .. 성분 없이): $2"; exit 2 ;;
      esac
      with+=("$w"); shift 2 ;;
    *) echo "모르는 인자: $1"; exit 2 ;;
  esac
done

# ① 앞 검사
[ -z "$(git status --porcelain)" ] || { echo "[빨강] 작업 트리가 깨끗하지 않다 — 커밋한 것만 묶는다"; exit 1; }
for w in "${with[@]+"${with[@]}"}"; do [ -e "$w" ] || { echo "[빨강] --with 경로가 없다: $w"; exit 1; }; done
echo "== 재료 지문"; bash scripts/bundle-fetch.sh --check || { echo "[빨강] 반입 재료 — bash scripts/bundle-fetch.sh(네트워크)"; exit 1; }
echo "== 오프라인 빌드"; bash scripts/offline-build.sh --check || { echo "[빨강] 오프라인 빌드 — bash scripts/offline-build.sh(네트워크)"; exit 1; }
# 설계 21 — 반입 zip 은 전부 검증 뒤. push 는 바뀐 레인만 요구하고 스니펫 글·pure·골든·산출물 양식은 레인에서 뺐으니,
# 「반입 전 한 번 전부」 를 여기서 강제한다 — verify.sh --full 이 HEAD 트리에서 돌면 `release <트리> full` 을 적는다
st="$(git rev-parse --git-dir)/verify-stamp"
grep -qx "release $(git rev-parse 'HEAD^{tree}') full" "$st" 2>/dev/null   || { echo "[빨강] 반입 zip 은 전부 검증 뒤 — 이 커밋에서 bash scripts/verify.sh --full(약 20분) 이 돈 적이 없다"; exit 1; }

[ -d "$R/jre/jmods" ] && [ -x "$R/jre/bin/jlink.exe" ] || { echo "[빨강] jre/ 에 jmods·jlink 가 없다(JDK 가 아니다) — bash scripts/bundle-fetch.sh"; exit 1; }

# ② app.jar — 저장소 m2/·배포본(.mvn-home)·오프라인(-o)으로. ~/.m2 로 만들면 m2/ 의 지문과 다른 좌표를 쓸 수 있다
echo "== app.jar (m2/ 오프라인)"
MAVEN_USER_HOME="$(winpath "$R/m2/.mvn-home")" bash scripts/mvn.sh -q -B -o -Dmaven.repo.local="$R/m2" -DskipTests package || { echo "[빨강] 빌드"; exit 1; }

# ③ 무대
day=$(date +%Y%m%d)
name="toolbox-server-$day"
base="$R/target/bundle"
stage="$base/$name"
rm -rf "$base" && mkdir -p "$stage" || { echo "[빨강] 무대 만들기: $stage"; exit 1; }
echo "== 무대 $stage"
# 9-4a — 실행 전용. 소스·pom·mvnw·.mvn·config·build.bat·PLAN.md·m2·javadoc·픽스처는 안 넣는다(설계 31)
git ls-files -z -- pure profiles mappings rules templates run.bat toolbox.bat README.md docs LICENSE bundle \
  | xargs -0 cp --parents -t "$stage" || { echo "[빨강] 추적 파일 복사"; exit 1; }
cp target/app.jar "$stage/app.jar" || { echo "[빨강] app.jar 복사"; exit 1; }
echo "== jlink 런타임"
"$R/jre/bin/jlink.exe" --module-path "$(winpath "$R/jre/jmods")" --add-modules "$JLINK_MODULES" --strip-debug --no-man-pages --no-header-files \
  --compress=2 --output "$(winpath "$stage/jre")" || { echo "[빨강] jlink"; exit 1; }
# 드라이버 — --with 프로필의 dialect 것만, 프로필이 없으면 drivers/ 바로 아래 전부(alt 제외)
pick_drivers() {
  local w d dialects=() pats=() j b p hit profs=0
  for w in "${with[@]+"${with[@]}"}"; do
    profs=$((profs + $(find "$w" -type f \( -path '*profiles/*.yaml' -o -path '*profiles/*.yml' \) | wc -l)))
    # 따옴표·공백·CR 을 벗긴다(dialect: "oracle" 도 읽게 — 마무리 리뷰)
    while IFS= read -r d; do [ -n "$d" ] && dialects+=("$d"); done < <(find "$w" -type f \( -path '*profiles/*.yaml' -o -path '*profiles/*.yml' \) \
      -exec sed -n 's/^[[:space:]]*dialect:[[:space:]]*\([^#]*\).*/\1/p' {} + 2>/dev/null | tr -d "\"' \r\t" | tr 'A-Z' 'a-z')
  done
  if [ ${#dialects[@]} -eq 0 ]; then
    # 프로필을 줬는데 dialect 를 못 읽으면 넷 다 넣지 않고 멈춘다(라이선스가 붙은 jar 가 조용히 끼지 않게)
    [ "$profs" -eq 0 ] || { echo "[빨강] --with 프로필에서 dialect 를 못 읽었다" >&2; return 1; }
    ls "$R"/drivers/*.jar; return
  fi
  for d in "${dialects[@]}"; do
    case "$d" in
      oracle|tibero) pats+=(ojdbc tibero) ;; postgresql|postgres|pg) pats+=(postgresql) ;; mariadb|mysql) pats+=(mariadb mysql-connector) ;;
      mssql|sqlserver) pats+=(mssql-jdbc sqljdbc) ;; h2) ;; *) echo "모르는 dialect: $d" >&2 ;;
    esac
  done
  for j in "$R"/drivers/*.jar; do
    b=$(basename "$j" | tr 'A-Z' 'a-z'); hit=0
    for p in "${pats[@]+"${pats[@]}"}"; do case "$b" in "$p"*) hit=1 ;; esac; done
    [ $hit = 1 ] && echo "$j"
  done
}
mkdir -p "$stage/drivers" || { echo "[빨강] drivers/"; exit 1; }
mapfile -t drv < <(pick_drivers)
[ ${#drv[@]} -gt 0 ] || { echo "[빨강] 넣을 드라이버가 없다 — --with 프로필의 dialect 에 맞는 jar 가 drivers/ 에 없다"; exit 1; }
cp "${drv[@]}" "$stage/drivers/" || { echo "[빨강] 드라이버 복사"; exit 1; }
echo "  드라이버 ${#drv[@]}: $(for j in "${drv[@]}"; do basename "$j"; done | tr '\n' ' ')"
MAVEN_USER_HOME="$(winpath "$R/m2/.mvn-home")" bash scripts/mvn.sh -q -B -o -Dmaven.repo.local="$R/m2" dependency:list -DincludeScope=runtime -DoutputFile="$stage/docs/dependencies.txt" -DappendOutput=false \
  || { echo "[빨강] 의존성 목록"; exit 1; }
njar=$(grep -cE ':(compile|runtime)' "$stage/docs/dependencies.txt")
printf '\r\n동봉 jar: app.jar 안 %s · drivers %s\r\n' "$njar" "${#drv[@]}" >> "$stage/docs/dependencies.txt"
mkdir -p "$stage/data" "$stage/logs" "$stage/out" "$stage/rules" || { echo "[빨강] 빈 폴더"; exit 1; }
# --with 로 넣은 파일 목록 — 묶음 검사(package-check.sh)가 허용 목록으로 읽는다. 폴더를 주면 안쪽 파일 하나하나(리허설도 같은 목록을 본다)
for w in "${with[@]+"${with[@]}"}"; do
  cp -r --parents "$w" "$stage/" || { echo "[빨강] --with 복사: $w"; exit 1; }
  find "$w" -type f | sed 's#^\./##' >> "$stage/PACKAGED-WITH.txt"
done

# ④ MANIFEST.txt
echo "== MANIFEST.txt"
(
  cd "$stage" || exit 1
  files=$(find . -type f | wc -l)
  bytes=$(find . -type f -printf '%s\n' | awk '{s+=$1} END{print s}')
  {
    echo "toolbox-server 반입 묶음"
    echo "버전 $(sed -n 's/^version=//p' "$R/target/classes/version.properties" 2>/dev/null | head -1) · 커밋 $(git -C "$R" rev-parse HEAD) · 만든 시각 $(date '+%F %T')"
    echo "파일 $files · 바이트 $bytes"
    echo "동봉 jar: app.jar 안 $njar · drivers ${#drv[@]} · 런타임 jlink 모듈 $(echo "$JLINK_MODULES" | tr ',' '\n' | wc -l)"
    echo "sha256  크기  경로"
    # 한 번에 묶어 잰다 — 파일마다 프로세스를 띄우면 수천 파일(m2·jre)에서 10분 넘게 걸린다
    list=$(mktemp)
    find . -type f ! -name MANIFEST.txt -print0 | LC_ALL=C sort -z > "$list"
    paste <(xargs -0 sha256sum < "$list" | sed -E 's#^\\?([0-9a-f]+) [ *]\./#\1\t#') <(xargs -0 stat -c %s < "$list") \
      | awk -F'\t' '{printf "%s  %s  %s\n", $1, $3, $2}'
    rm -f "$list"
  } > MANIFEST.txt
) || { echo "[빨강] MANIFEST"; exit 1; }

# ⑤ 금지·필수 검사
echo "== 묶음 검사"
bash scripts/package-check.sh "$stage" || exit 1

# ⑥ zip — 이 PC 에 zip·7z 가 없어 JDK 의 jar 로(빈 폴더도 들어간다)
echo "== zip"
JAR="$R/jre/bin/jar.exe"
[ -x "$JAR" ] || JAR="${JAVA_HOME:-C:/Program Files/Java/jdk-17.0.19}/bin/jar"
rm -f "$R/$name.zip" "$R/$name.zip.sha256" # 옛 해시가 없는 zip 을 가리키지 않게
# 실패하면 부분 zip 을 지운다 — 실제 프로필이 저장소 루트에 반쪽으로 남지 않게(무대는 디버깅용으로 남긴다)
"$JAR" --create --no-manifest --file "$R/$name.zip" -C "$base" "$name" || { rm -f "$R/$name.zip"; echo "[빨강] zip"; exit 1; }
nfiles=$(find "$stage" -type f | wc -l)
sha=$(sha256sum < "$R/$name.zip" | cut -d' ' -f1)
printf '%s  %s\n' "$sha" "$name.zip" > "$R/$name.zip.sha256" || { echo "[빨강] .zip.sha256"; exit 1; } # sha256sum -c 꼴 — 반입 심사 서류용
rm -rf "$base" # 무대는 zip 에 다 들어갔다 — --with 로 넣은 실제 프로필을 target/ 에 안 남긴다. 실패하면 위 exit 들이 무대를 남긴다
size=$(du -h "$R/$name.zip" | cut -f1)
echo "반입 zip: $name.zip — $size · 파일 $nfiles · sha256 $sha · 옆에 $name.zip.sha256"
others=$(ls "$R"/toolbox-server-*.zip 2>/dev/null | grep -v "/$name\.zip\$" | xargs -r -n1 basename)
if [ -n "$others" ]; then echo "옛 zip 이 남아 있다 — 실제 프로필을 넣었던 것이면 지운다: $(echo $others)"; fi
echo "첫 반입이면 src/main/resources/db/released-baseline 에 $(git rev-parse HEAD) 를 적는다(PLAN 16장 끝)"
