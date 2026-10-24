#!/usr/bin/env bash
# PMD 기준선(0-68, 설계 38) — pmd:pmd 를 돌려 target/pmd.xml 의 (패키지.클래스 → 규칙) 짝을
# config/pmd/exclude-from-failure.properties 로 쓴다. 래칫: 새 클래스·새 규칙 짝만 pmd:check 가 빨강.
# 다시 뜨는 때는 지적을 고쳐 줄였을 때뿐 — 늘리려고 뜨지 않는다(diff 를 이력에).
# verify 의 pmd:check 와 같은 조건(컴파일된 클래스로 형 해석)이 되게 test-compile 을 먼저 돈다.
# pmd:pmd 보고서도 기준선에 든 지적을 뺀다 — 기준선을 머리줄만 남게 잠시 비워 전체 지적을 받는다.
# 도중에 끊기면 원래 기준선으로 되돌린다(마무리 리뷰).
set -euo pipefail
cd "$(dirname "$0")/.."
out=config/pmd/exclude-from-failure.properties
bak=$(mktemp)
cp "$out" "$bak"
trap 'cp "$bak" "$out"; rm -f "$bak"' ERR INT TERM
echo "# 기준선 다시 뜨는 중" > "$out"
bash scripts/mvn.sh -q -B -DskipTests -Djacoco.skip=true test-compile pmd:pmd
grep -q '<violation ' target/pmd.xml || { echo "target/pmd.xml 지적 0 — 기준선을 안 바꾸고 멈춘다(PMD 가 파일을 못 읽었다)"; false; }
{
  echo "# scripts/pmd-baseline.sh 가 뜬다. 손으로 늘리지 않는다 — 줄어들 때만 다시 뜬다(0-68)"
  awk '
    /<file name="/ { match($0, /name="[^"]*"/); f = substr($0, RSTART+6, RLENGTH-7) }
    /<violation / {
      r = p = c = ""
      if (match($0, / rule="[^"]*"/))    r = substr($0, RSTART+7,  RLENGTH-8)
      if (match($0, / package="[^"]*"/)) p = substr($0, RSTART+10, RLENGTH-11)
      if (match($0, / class="[^"]*"/))   c = substr($0, RSTART+8,  RLENGTH-9)
      if (p == "" || c == "") { k = f; gsub(/\\/, "/", k); sub(/.*\/java\//, "", k); sub(/\.java$/, "", k); gsub(/\//, ".", k) } else k = p "." c
      if (!((k, r) in seen)) { seen[k, r] = 1; rules[k] = (k in rules) ? rules[k] "," r : r }
    }
    END { for (k in rules) print k "=" rules[k] }
  ' target/pmd.xml | LC_ALL=C sort
} > "$out"
echo "기준선 $(grep -vc '^#' "$out") 클래스 → $out"
rm -f "$bak"
