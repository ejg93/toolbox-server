#!/usr/bin/env bash
# SessionStart. `PROGRESS.md` 「현재 상태」를 컨텍스트에 넣는다. 세션이 열릴 때마다 어디까지 했는지 안다.
cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0
sed -n '/^## 현재 상태/,/^## 이력/p' PROGRESS.md | sed '$d'
exit 0
