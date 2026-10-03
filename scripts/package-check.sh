#!/usr/bin/env bash
# 반입 zip 무대(또는 푼 폴더) 검사(8-10) — 들어가면 안 되는 것과 있어야 하는 것. package.sh·rehearse.sh 가 부른다.
#   bash scripts/package-check.sh <무대> [--allow <상대경로>]…
# --allow 는 package.sh --with 로 일부러 넣은 실제 사업 파일(프로필 등).
set -uo pipefail
stage=${1:?무대 폴더}
shift
allow=()
while [ $# -gt 0 ]; do
  case "$1" in
    --allow) allow+=("${2:?}"); shift 2 ;;
    *) echo "모르는 인자: $1"; exit 2 ;;
  esac
done
[ -d "$stage" ] || { echo "무대가 없다: $stage"; exit 2; }
cd "$stage" || exit 2
# package.sh --with 로 일부러 넣은 파일(무대에 남긴 목록) — 리허설이 푼 폴더를 다시 잴 때도 같은 목록
if [ -f PACKAGED-WITH.txt ]; then
  while IFS= read -r line; do
    line=${line%$'\r'} # $'\r' 는 큰따옴표 안에서 안 풀린다 — 따옴표 밖 대입으로
    [ -n "$line" ] && allow+=("$line")
  done < PACKAGED-WITH.txt
fi
fail=0
bad() { echo "  [금지] $1"; fail=1; }
allowed() { local x; for x in "${allow[@]+"${allow[@]}"}"; do [ "$x" = "$1" ] && return 0; done; return 1; }

# 들어가면 안 되는 것 — 저장소 이력·도구 설정·실물 표본·H2 파일·로컬 설정·빌드 찌꺼기
for d in .git .claude .github corpus target; do [ -e "$d" ] && bad "$d/"; done
while IFS= read -r p; do bad "$p"; done < <(find . -path '*/golden/corpus' -prune -print 2>/dev/null | sed 's#^\./##')
while IFS= read -r p; do bad "$p"; done < <(find . \( -name '*.mv.db' -o -name '*.trace.db' -o -name 'settings.local.json' -o -name 'active-profile' \) 2>/dev/null | sed 's#^\./##')
if [ -d profiles ]; then
  while IFS= read -r p; do
    rel="profiles/$p"
    [ "$p" = "example.yaml" ] && continue
    allowed "$rel" || bad "$rel(실제 사업 프로필 — 넣으려면 package.sh --with $rel)"
  done < <(cd profiles && find . -maxdepth 1 -name '*.yaml' | sed 's#^\./##')
fi

# 있어야 하는 것
for f in app.jar run.bat toolbox.bat build.bat MANIFEST.txt jre/bin/javac.exe jre/bin/java.exe mvnw.cmd pom.xml; do
  [ -f "$f" ] || { echo "  [없음] $f"; fail=1; }
done
ls -d m2/.mvn-home/wrapper/dists/apache-maven-*/*/apache-maven-* >/dev/null 2>&1 || { echo "  [없음] m2/.mvn-home 의 Maven 배포본"; fail=1; }
ls drivers/*.jar >/dev/null 2>&1 || { echo "  [없음] drivers/*.jar"; fail=1; }
# 한 벤더 한 jar — DriverLoader 는 drivers/ 바로 아래 jar 를 전부 등록해 같은 벤더 둘이면 이름 순 첫째가 이긴다(PLAN 11장)
if ls drivers/*.jar >/dev/null 2>&1; then
  dup=$(for j in drivers/*.jar; do
    b=$(basename "$j" | tr 'A-Z' 'a-z')
    case "$b" in
      ojdbc*) echo oracle ;; postgresql*) echo postgresql ;; mariadb*) echo mariadb ;; mysql-connector*) echo mysql ;;
      mssql-jdbc*|sqljdbc*) echo mssql ;; tibero*) echo tibero ;; *) echo "기타:$b" ;;
    esac
  done | sort | uniq -d)
  [ -n "$dup" ] && { echo "  [금지] drivers/ 에 같은 벤더 jar 가 둘 이상: $(echo $dup) — 하나만 두고 나머지는 drivers/alt/"; fail=1; }
fi
for d in data logs out; do [ -d "$d" ] || { echo "  [없음] $d/"; fail=1; }; done
for d in data logs out; do
  [ -d "$d" ] && [ -n "$(ls -A "$d" 2>/dev/null)" ] && { echo "  [금지] $d/ 가 비어 있지 않다"; fail=1; }
done

if [ "$fail" -eq 0 ]; then echo "묶음 검사 초록"; else echo "묶음 검사 빨강"; fi
exit "$fail"
