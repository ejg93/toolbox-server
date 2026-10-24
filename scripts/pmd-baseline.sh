#!/usr/bin/env bash
# PMD 기준선(0-68, 설계 38) — pmd:pmd 를 돌려 target/pmd.xml 의 (패키지.클래스 → 규칙) 짝을
# config/pmd/exclude-from-failure.properties 로 쓴다. 래칫: 새 클래스·새 규칙 짝만 pmd:check 가 빨강.
# 다시 뜨는 때는 지적을 고쳐 줄였을 때뿐 — 늘리려고 뜨지 않는다(diff 를 이력에).
# verify 의 pmd:check 와 같은 조건(컴파일된 클래스로 형 해석)이 되게 test-compile 을 먼저 돈다.
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/mvn.sh -q -B -DskipTests -Djacoco.skip=true test-compile pmd:pmd
out=config/pmd/exclude-from-failure.properties
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
