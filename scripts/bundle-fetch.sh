#!/usr/bin/env bash
# 반입 재료 받기(8-9) — 네트워크가 있는 빌드 PC 에서. 런타임이 아니다(절대 규칙 1 은 도구 실행 중 통신).
#   jre/          Temurin 17 JDK(x64 Windows) — Adoptium API 의 zip, sha256 대조. 현장 빌드용이라 JRE 가 아니라 JDK(사용자 2026-10-03)
#   drivers/      한 벤더 한 jar(bundle/drivers/pom.xml)        drivers/alt/  다른 판(bundle/drivers-alt/pom.xml)
#   docs/javadoc/ JavaParser·POI·Javalin·Freemarker·picocli 의 javadoc jar(판은 루트 pom 이 실제로 쓰는 것)
#
#   bash scripts/bundle-fetch.sh           받기 + bundle/MANIFEST 갱신
#   bash scripts/bundle-fetch.sh --check   네트워크 없이 — 셋이 있고 지문이 bundle/MANIFEST 와 같은지(package.sh 가 부른다)
#
# 지문은 이 스크립트만 쓴다. Tibero 드라이버는 Maven Central 에 없어 사람이 drivers/alt/ 에 넣는다(없으면 경고만).
set -uo pipefail
cd "$(dirname "$0")/.."
R=$(pwd)
MANIFEST=bundle/MANIFEST
CACHE="${TOOLBOX_BUNDLE_CACHE:-${TEMP:-/tmp}/toolbox-bundle}"
command -v cygpath >/dev/null 2>&1 && CACHE=$(cygpath -u "$CACHE")
sha() { sha256sum < "$1" | cut -d" " -f1; } # 파일 이름 없이 — 이름에 역슬래시가 있으면 sha256sum 이 해시 앞에 「」 를 붙인다
API='https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
# 우리가 받는 드라이버 jar 이름 머리 — 다시 받을 때 이것만 지운다(사람이 넣은 tibero 등은 둔다)
MAIN_JARS='ojdbc11-* postgresql-* mariadb-java-client-* mssql-jdbc-*'
ALT_JARS='ojdbc8-* ojdbc6-* mysql-connector-j-*'
JAVADOC=(io.javalin:javalin com.github.javaparser:javaparser-core org.apache.poi:poi org.apache.poi:poi-ooxml org.freemarker:freemarker info.picocli:picocli)

fingerprint() { # 이름 폴더 [최대 깊이] → 「이름 파일수 sha256」(「sha256  상대경로」 정렬 목록의 sha256 — 실물 표본 MANIFEST 와 같은 꼴)
  local name=$1 dir=$2 depth=${3:-64} list
  if [ ! -d "$dir" ]; then echo "$name 0 -"; return; fi
  list=$(cd "$dir" && find . -maxdepth "$depth" -type f -print0 | LC_ALL=C sort -z | xargs -0 -r sha256sum | sed -E 's#^([0-9a-f]+) [ *]\./#\1  #')
  echo "$name $(printf '%s\n' "$list" | grep -c .) $(printf '%s\n' "$list" | sha256sum | cut -d' ' -f1)"
}

fingerprint_jars() { # 이름 폴더 머리들… → 우리가 받는 jar 만(사람이 넣는 tibero 등은 지문 밖 — PR #38 리뷰)
  local name=$1 dir=$2 list
  shift 2
  if [ ! -d "$dir" ]; then echo "$name 0 -"; return; fi
  list=$(cd "$dir" && for p in "$@"; do for f in $p; do [ -f "$f" ] && printf '%s\0' "$f"; done; done | LC_ALL=C sort -z | xargs -0 -r sha256sum | sed -E 's#^\\?([0-9a-f]+) [ *]#\1  #')
  echo "$name $(printf '%s\n' "$list" | grep -c .) $(printf '%s\n' "$list" | sha256sum | cut -d' ' -f1)"
}

fingerprints() {
  fingerprint jre jre
  # shellcheck disable=SC2086
  fingerprint_jars drivers drivers $MAIN_JARS
  # shellcheck disable=SC2086
  fingerprint_jars drivers-alt drivers/alt $ALT_JARS
  fingerprint javadoc docs/javadoc
}

tibero_note() {
  if ! ls drivers/tibero*.jar >/dev/null 2>&1; then
    echo "  [경고] Tibero 드라이버가 없다 — Maven Central 에 없어 사람이 drivers/ 에 넣는다(벤더가 하나라 겹치지 않는다, bundle/SOURCES.md)"
  fi
}

if [ "${1:-}" = "--check" ]; then
  fail=0
  [ -f jre/bin/javac.exe ] || { echo "  [실패] jre/bin/javac.exe 가 없다 — JDK 가 아니거나 안 받았다"; fail=1; }
  ls drivers/*.jar >/dev/null 2>&1 || { echo "  [실패] drivers/ 에 jar 가 없다"; fail=1; }
  [ -f "$MANIFEST" ] || { echo "  [실패] $MANIFEST 가 없다 — bash scripts/bundle-fetch.sh 먼저(네트워크)"; exit 1; }
  while read -r line; do
    name=${line%% *}
    want=$(grep -E "^$name " "$MANIFEST" || true)
    if [ "$line" != "$want" ]; then
      echo "  [실패] $name 지문이 다르다 — 지금 「$line」 / MANIFEST 「$want」"; fail=1
    fi
  done < <(fingerprints)
  tibero_note
  [ "$fail" -eq 0 ] && echo "반입 재료 초록 — $(grep -vc '^#' "$MANIFEST") 묶음, 지문 일치" || echo "반입 재료 빨강"
  exit "$fail"
fi

mkdir -p "$CACHE" drivers/alt docs/javadoc

# ① JDK
echo "== JDK(Temurin 17)"
meta=$(curl -fsSL --max-time 60 "$API") || { echo "  [실패] Adoptium API 를 못 열었다 — bundle/SOURCES.md 의 GitHub 릴리스 주소로 받는다"; exit 1; }
read -r JDK_NAME JDK_LINK JDK_SUM JDK_REL < <(printf '%s' "$meta" | node -e '
  let d = ""; process.stdin.on("data", x => d += x).on("end", () => {
    const a = JSON.parse(d).find(x => x.binary.package.name.endsWith(".zip"));
    if (!a) { process.exit(1); }
    const p = a.binary.package;
    console.log(p.name, p.link, p.checksum, a.release_name);
  })') || { echo "  [실패] API 응답에 zip 이 없다"; exit 1; }
zip="$CACHE/$JDK_NAME"
if [ ! -f "$zip" ] || [ "$(sha "$zip")" != "$JDK_SUM" ]; then
  echo "  받기 $JDK_NAME"
  curl -fsSL --max-time 1800 -o "$zip" "$JDK_LINK" || { echo "  [실패] JDK 를 못 받았다"; exit 1; }
fi
got=$(sha "$zip")
[ "$got" = "$JDK_SUM" ] || { echo "  [실패] sha256 이 다르다 — 받은 $got / API $JDK_SUM"; rm -f "$zip"; exit 1; }
rm -rf "$CACHE/x" && mkdir -p "$CACHE/x"
unzip -q "$zip" -d "$CACHE/x" || { echo "  [실패] 풀지 못했다"; exit 1; }
top=$(find "$CACHE/x" -mindepth 1 -maxdepth 1 -type d | head -1)
[ -f "$top/bin/javac.exe" ] || { echo "  [실패] 받은 것에 javac.exe 가 없다(JDK 아님)"; exit 1; }
rm -rf jre && mv "$top" jre || { echo "  [실패] jre/ 로 옮기지 못했다"; exit 1; }
echo "  jre/ ← $JDK_REL ($("jre/bin/java.exe" -version 2>&1 | head -1))"

# ② 드라이버
echo "== 드라이버"
(cd drivers && for p in $MAIN_JARS; do rm -f $p; done)
(cd drivers/alt && for p in $ALT_JARS; do rm -f $p; done)
bash scripts/mvn.sh -q -B -f bundle/drivers/pom.xml dependency:copy-dependencies -DexcludeTransitive=true -DoutputDirectory="$R/drivers" \
  || { echo "  [실패] drivers 를 못 받았다"; exit 1; }
bash scripts/mvn.sh -q -B -f bundle/drivers-alt/pom.xml dependency:copy-dependencies -DexcludeTransitive=true -DoutputDirectory="$R/drivers/alt" \
  || { echo "  [실패] drivers/alt 를 못 받았다"; exit 1; }
echo "  drivers/ $(ls drivers/*.jar | wc -l) · drivers/alt/ $(ls drivers/alt/*.jar | wc -l)"
tibero_note

# ③ javadoc — 판은 루트 pom 이 실제로 푸는 것
echo "== javadoc"
deps="$CACHE/deps.txt"
bash scripts/mvn.sh -q -B dependency:list -DincludeScope=runtime -DoutputFile="$deps" -DappendOutput=false || { echo "  [실패] 의존성 목록"; exit 1; }
rm -f docs/javadoc/*.jar
jd=0
for ga in "${JAVADOC[@]}"; do
  v=$(grep -oE "${ga//./\\.}:jar:[^:]+" "$deps" | head -1 | sed -E 's/.*:jar://')
  if [ -z "$v" ]; then echo "  [경고] $ga 판을 못 찾았다 — 건너뜀"; continue; fi
  if bash scripts/mvn.sh -q -B dependency:copy -Dartifact="$ga:$v:jar:javadoc" -DoutputDirectory="$R/docs/javadoc" >/dev/null 2>&1; then
    jd=$((jd + 1))
  else
    echo "  [경고] $ga:$v javadoc 이 없다 — 건너뜀"
  fi
done
echo "  docs/javadoc/ $jd"

# ④ 지문
{
  echo "# 반입 재료 지문(8-9) — scripts/bundle-fetch.sh 가 쓴다. 손으로 고치지 않는다. 이름 파일수 sha256(「sha256  상대경로」 정렬 목록의)"
  echo "# 받은 날 $(date +%F) · JDK $JDK_REL ($JDK_NAME sha256 $JDK_SUM)"
  fingerprints
} > "$MANIFEST"
echo "지문: $MANIFEST — $(grep -vc '^#' "$MANIFEST") 묶음 · jre $(du -sh jre | cut -f1) · drivers $(du -sh drivers | cut -f1) · javadoc $(du -sh docs/javadoc | cut -f1)"
