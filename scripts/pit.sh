#!/usr/bin/env bash
# PIT 뮤테이션 시험(0-65, 설계 38) — core.check 만, 손으로만. 리포트 out/pit/index.html. 게이트 아님.
# 네트워크가 필요하다 — offline-build 채움(resolve-plugins)이 플러그인 의존성 pitest-junit5-plugin 을 m2/ 에 안 넣는다. 반입 PC 에선 안 돈다(집 전용).
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/mvn.sh -B -DexcludedGroups=db,corpus -Djacoco.skip=true test-compile org.pitest:pitest-maven:mutationCoverage
echo "리포트: out/pit/index.html"
