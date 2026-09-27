#!/usr/bin/env bash
# 반입용 오프라인 저장소 `m2/` 를 채우고, 그것만으로 빌드되는지 본다(CLAUDE.md 절대 규칙 5, PLAN 13장 0-12).
#
#   bash scripts/offline-build.sh          m2/ 채우기 → -o 빌드
#   bash scripts/offline-build.sh --check  m2/ 를 안 채우고 -o 빌드만(네트워크 끊고 돌리는 리허설)
#
# `m2/` 는 의존성만이 아니라 **Maven 배포본**도 든다(0-15). mvnw 는 첫 실행에 distributionUrl 의 zip 을
# `$MAVEN_USER_HOME/wrapper/dists/` 에 받는데 현장엔 네트워크가 없다. 채우기가 배포본을 `m2/.mvn-home/` 에 복사하고,
# 확인은 `MAVEN_USER_HOME=m2/.mvn-home` 으로 돌려 wrapper 가 무엇도 받으려 하지 않는지(`Downloading` 이 없는지) 잰다.
# 현장에서도 같은 환경변수로 `mvnw -o` 를 부른다(11장).
set -uo pipefail
cd "$(dirname "$0")/.."
repo="$(pwd)/m2"
mvnhome="$repo/.mvn-home"
# distributionUrl=…/apache-maven-3.9.9-bin.zip → apache-maven-3.9.9-bin
dist=$(sed -n 's#^distributionUrl=.*/\([^/]*\)\.zip[[:space:]]*$#\1#p' .mvn/wrapper/maven-wrapper.properties)
[ -n "$dist" ] || { echo "maven-wrapper.properties 에서 distributionUrl 을 못 읽었다"; exit 1; }
# wrapper 는 자바라 Windows 경로를 준다
winpath() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi; }

if [ "${1:-}" != "--check" ]; then
  echo "== m2/ 채우기"
  bash scripts/mvn.sh -q -B -Dmaven.repo.local="$repo" dependency:go-offline dependency:resolve-plugins || exit 1
  # go-offline 과 실제 빌드는 전이 의존성 버전을 다르게 풀 때가 있다(2026-09-26: kotlin-stdlib 의 annotations 13.0 을 go-offline 은 안 받았다).
  # 같은 m2/ 로 실제 빌드를 한 번 돌려 빌드가 쓰는 좌표를 그대로 받는다. verify 까지 — surefire 공급자·SpotBugs 가 실행 때 받는 것까지(컨테이너 테스트는 뺀다)
  bash scripts/mvn.sh -q -B -Dmaven.repo.local="$repo" -DexcludedGroups=db,corpus verify || exit 1
  echo "== Maven 배포본 → m2/.mvn-home ($dist)"
  src="${MAVEN_USER_HOME:-$HOME/.m2}/wrapper/dists/$dist"
  [ -d "$src" ] || { echo "배포본이 없다: $src — 위 빌드가 받았어야 한다"; exit 1; }
  rm -rf "$mvnhome/wrapper/dists/$dist"
  mkdir -p "$mvnhome/wrapper/dists"
  cp -r "$src" "$mvnhome/wrapper/dists/" || exit 1
fi

echo "== 오프라인 빌드 (MAVEN_USER_HOME=m2/.mvn-home)"
[ -d "$mvnhome/wrapper/dists/$dist" ] || { echo "m2/.mvn-home 에 배포본($dist)이 없다 — 채우기부터"; exit 1; }
out=$(MAVEN_USER_HOME="$(winpath "$mvnhome")" MVNW_VERBOSE=true \
  bash scripts/mvn.sh -q -B -o -Dmaven.repo.local="$repo" -DskipTests verify 2>&1); st=$?
if echo "$out" | grep -qiE "downloading|download from"; then  # wrapper 판마다 문구가 달라 넓게(번들 3 리뷰)
  echo "wrapper 가 배포본을 받으려 했다 — 현장에선 여기서 멈춘다"; echo "$out" | grep -i "download" | head -3; exit 1
fi
[ "$st" -eq 0 ] || { echo "$out" | tail -20; echo "오프라인 빌드 실패 — m2/ 에 빠진 것이 있다"; exit 1; }
echo "오프라인 빌드 초록. 배포본 받기 없음. m2/ 크기: $(du -sh "$repo" | cut -f1)"
