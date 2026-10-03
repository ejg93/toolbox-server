#!/usr/bin/env bash
# 반입 zip 만들기(8-10, PLAN 11장). 네트워크 없이 — 재료는 bundle-fetch.sh 가 미리 받아 둔다.
#   bash scripts/package.sh [--with <상대경로>]…
#     --with  실제 사업 파일(프로필·양식 등, .gitignore 대상)을 같은 상대 경로로 넣는다
# 결과: toolbox-server-<yyyymmdd>.zip(저장소 루트) — MANIFEST.txt(버전·커밋·파일마다 sha256) 동봉.
# 첫 반입이면 src/main/resources/db/released-baseline 에 커밋을 적는다(16장 끝) — 이 스크립트는 안 쓴다(리허설 zip 은 반입이 아니다).
set -uo pipefail
cd "$(dirname "$0")/.."
R=$(pwd)
with=()
while [ $# -gt 0 ]; do
  case "$1" in
    --with) with+=("${2:?--with <상대경로>}"); shift 2 ;;
    *) echo "모르는 인자: $1"; exit 2 ;;
  esac
done

# ① 앞 검사
[ -z "$(git status --porcelain)" ] || { echo "[빨강] 작업 트리가 깨끗하지 않다 — 커밋한 것만 묶는다"; exit 1; }
for w in "${with[@]+"${with[@]}"}"; do [ -e "$w" ] || { echo "[빨강] --with 경로가 없다: $w"; exit 1; }; done
echo "== 재료 지문"; bash scripts/bundle-fetch.sh --check || { echo "[빨강] 반입 재료 — bash scripts/bundle-fetch.sh(네트워크)"; exit 1; }
echo "== 오프라인 빌드"; bash scripts/offline-build.sh --check || { echo "[빨강] 오프라인 빌드 — bash scripts/offline-build.sh(네트워크)"; exit 1; }

# ② app.jar
echo "== app.jar"
bash scripts/mvn.sh -q -B -DskipTests package || { echo "[빨강] 빌드"; exit 1; }

# ③ 무대
day=$(date +%Y%m%d)
name="toolbox-server-$day"
base="$R/target/bundle"
stage="$base/$name"
rm -rf "$base" && mkdir -p "$stage"
echo "== 무대 $stage"
git ls-files -z -- src pom.xml mvnw mvnw.cmd .mvn config pure profiles mappings rules templates run.bat toolbox.bat build.bat README.md PLAN.md docs \
  | grep -zv '^src/test/resources/golden/corpus/' \
  | xargs -0 cp --parents -t "$stage" || { echo "[빨강] 추적 파일 복사"; exit 1; }
cp target/app.jar "$stage/app.jar"
cp -r jre drivers m2 "$stage/"
mkdir -p "$stage/docs" && cp -r docs/javadoc "$stage/docs/"
bash scripts/mvn.sh -q -B dependency:list -DincludeScope=runtime -DoutputFile="$stage/docs/dependencies.txt" -DappendOutput=false \
  || { echo "[빨강] 의존성 목록"; exit 1; }
mkdir -p "$stage/fixtures"
cp -r src/test/resources/fixtures/check src/test/resources/fixtures/analyze "$stage/fixtures/"
printf '%s\r\n' "코드 검사·프로그램 분석의 픽스처 — check 의 pos 파일은 규칙이 잡아야 하는 나쁜 예를 일부러 넣은 것이다(neg 는 잡지 말아야 하는 예)." > "$stage/fixtures/README.txt"
mkdir -p "$stage/data" "$stage/logs" "$stage/out" "$stage/rules"
allow=()
for w in "${with[@]+"${with[@]}"}"; do
  cp -r --parents "$w" "$stage/" && allow+=(--allow "$w")
done

# ④ MANIFEST.txt
echo "== MANIFEST.txt"
(
  cd "$stage" || exit 1
  files=$(find . -type f | wc -l)
  bytes=$(find . -type f -printf '%s\n' | awk '{s+=$1} END{print s}')
  {
    echo "toolbox-server 반입 묶음"
    echo "버전 $(sed -n 's/^version=//p' "$R/src/main/resources/version.properties" 2>/dev/null | head -1) · 커밋 $(git -C "$R" rev-parse HEAD) · 만든 시각 $(date '+%F %T')"
    echo "파일 $files · 바이트 $bytes"
    echo "sha256  크기  경로"
    find . -type f ! -name MANIFEST.txt -print0 | LC_ALL=C sort -z | while IFS= read -r -d '' f; do
      printf '%s  %s  %s\n' "$(sha256sum < "$f" | cut -d' ' -f1)" "$(stat -c %s "$f")" "${f#./}"
    done
  } > MANIFEST.txt
) || { echo "[빨강] MANIFEST"; exit 1; }

# ⑤ 금지·필수 검사
echo "== 묶음 검사"
bash scripts/package-check.sh "$stage" "${allow[@]+"${allow[@]}"}" || exit 1

# ⑥ zip — 이 PC 에 zip·7z 가 없어 JDK 의 jar 로(빈 폴더도 들어간다)
echo "== zip"
JAR="$R/jre/bin/jar.exe"
[ -x "$JAR" ] || JAR="${JAVA_HOME:-C:/Program Files/Java/jdk-17.0.19}/bin/jar"
rm -f "$R/$name.zip"
"$JAR" --create --no-manifest --file "$R/$name.zip" -C "$base" "$name" || { echo "[빨강] zip"; exit 1; }
size=$(du -h "$R/$name.zip" | cut -f1)
echo "반입 zip: $name.zip — $size · 파일 $(find "$stage" -type f | wc -l) · sha256 $(sha256sum < "$R/$name.zip" | cut -d' ' -f1)"
echo "첫 반입이면 src/main/resources/db/released-baseline 에 $(git rev-parse HEAD) 를 적는다(PLAN 16장 끝)"
