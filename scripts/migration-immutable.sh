#!/usr/bin/env bash
# 반입된 H2 마이그레이션을 고치거나 지웠나. ProjectShop `scripts/migration-immutable.sh`(Q51)를 옮겼다(2026-09-26, 0-20).
#
# 이 도구에서 「배포」는 **반입**이다. 현장 PC 의 `data/toolbox.mv.db` 에 `schema_version` 이 박히면
# 그 뒤로 V 파일을 고치면 현장 DB 와 어긋난다 — 새 V 만 더한다.
#
# **첫 반입 전에는 안 잰다.** 나간 적이 없으니 가변이 맞다. 반입 전날 체크리스트(PLAN 16장 끝)가
# 기준점 파일에 그 커밋을 적는다. 기준점을 태그가 아니라 파일에 두는 이유는 ProjectShop 과 같다 —
# 「반입 전」과 「CI 가 얕게 받아 태그가 안 왔다」가 같은 모양이 되지 않게. 파일은 리뷰 diff 에도 뜬다.
set -euo pipefail
cd "$(dirname "$0")/.."

baseline_file=src/main/resources/db/released-baseline
watched=src/main/resources/db/migration

if [ ! -f "$baseline_file" ]; then
    echo "반입 전이다 — 잴 기준점이 없다. 첫 반입 때 $baseline_file 에 그 커밋을 적는다."
    exit 0
fi

# 주석(#)·빈 줄을 걷고 첫 줄. `|| true` 가 없으면 주석뿐인 파일에서 set -e 가 이유 없이 끊는다(ProjectShop 실측)
baseline=$(grep -vE '^[[:space:]]*(#|$)' "$baseline_file" | head -1 | tr -d '[:space:]' || true)
if [ -z "$baseline" ]; then
    echo "기준점 파일에 커밋이 없다: $baseline_file" >&2
    exit 1
fi

# 못 찾으면 빨강 — 기준점이 있는데 못 읽는 것은 「반입 전」이 아니라 고장이다
if ! git cat-file -e "${baseline}^{commit}" 2>/dev/null; then
    echo "기준점 커밋을 못 찾는다: $baseline" >&2
    echo "  이력을 얕게 받았으면 그 탓이다 — CI 잡은 fetch-depth: 0 이어야 한다." >&2
    exit 1
fi

# M(고침)·D(지움)·R(이름 바꿈)만. A(새 파일)는 허용. 작업 트리와 견준다 — verify.sh 가 커밋 앞에 돈다
changed=$(git diff --name-status --diff-filter=MDR "$baseline" -- $watched)
if [ -n "$changed" ]; then
    echo "반입된 마이그레이션을 고쳤다. 기준점 $baseline 뒤로는 새 V 만 더한다." >&2
    echo "$changed" >&2
    exit 1
fi

echo "마이그레이션 불변 — 기준점 ${baseline} 뒤로 고치거나 지운 것이 없다"
