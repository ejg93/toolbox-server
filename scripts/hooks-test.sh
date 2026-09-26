#!/usr/bin/env bash
# 훅·도구 회귀 시험. 훅 아홉과 `doc-lint` 를 stdin JSON·임시 저장소로 잰다 — 실제 저장소는 안 건드린다.
# ProjectShop `scripts/hooks-test.sh` 에서 레인(java·tools·docs)만 바꿔 옮겼다(2026-09-26). `verify.sh` 의 tools 레인이 돈다.
set -u
R=$(cd "$(dirname "$0")/.." && pwd)
H=$R/scripts/hooks
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
fail=0; n=0

json_cmd() { node -e 'process.stdout.write(JSON.stringify({tool_input:{command:process.argv[1]}}))' "$1"; }
json_file() { node -e 'process.stdout.write(JSON.stringify({tool_input:{file_path:process.argv[1]}}))' "$1"; }

case_() { # 이름, 훅 파일, 기대 exit, stdin, [기대 stderr 앞머리], [작업 디렉터리]
  local name=$1 hook=$2 want=$3 input=$4 head=${5:-} dir=${6:-$T/repo}
  n=$((n + 1))
  local err got
  err=$(cd "$dir" && printf '%s' "$input" | bash "$H/$hook" 2>&1 >/dev/null); got=$?
  if [ "$got" != "$want" ]; then
    echo "  [실패] $n $hook — $name: 기대 exit $want, 실제 $got. ${err:0:80}"; fail=1
  elif [ -n "$head" ] && [ "${err#"$head"}" = "$err" ]; then
    echo "  [실패] $n $hook — $name: stderr 가 「$head」로 안 시작한다: ${err:0:80}"; fail=1
  else
    echo "  [통과] $n $hook — $name"
  fi
}

mkrepo() { # origin/main 하나, work/x 가지에 src 한 줄을 더한 커밋
  rm -rf "$T/repo"; mkdir -p "$T/repo/scripts" "$T/repo/src" "$T/repo/.claude"
  cp "$R/scripts/verify-fingerprint.sh" "$T/repo/scripts/"
  (
    cd "$T/repo" || exit 1
    git init -q -b main
    git config user.email t@t; git config user.name t; git config commit.gpgsign false; git config core.autocrlf false
    echo base > src/A.java; echo '{}' > .claude/settings.json; echo '<project/>' > pom.xml
    git add -A; git commit -qm base
    git update-ref refs/remotes/origin/main HEAD
    git checkout -qb work/x
    echo change >> src/A.java; git commit -qam change
  )
}
stamp_head() { (cd "$T/repo" && bash scripts/verify-fingerprint.sh HEAD | sed "s/\$/ $1/" > .git/verify-stamp); }
stamp_worktree() {
  (cd "$T/repo" && tmp=$(mktemp) && GIT_INDEX_FILE="$tmp" git read-tree HEAD && GIT_INDEX_FILE="$tmp" git add -A . \
    && tree=$(GIT_INDEX_FILE="$tmp" git write-tree) && rm -f "$tmp" \
    && bash scripts/verify-fingerprint.sh "$tree" | sed 's/$/ fast/' > .git/verify-stamp)
}

mkrepo
export CLAUDE_PROJECT_DIR=$T/repo

echo "pr-guard(가짜 gh):"
mkdir -p "$T/bin"
cat > "$T/bin/gh" <<'GH'
#!/usr/bin/env bash
case "$1 $2" in
  "pr list") echo "${FAKE_OPEN_PRS:-0}" ;;
  "pr checks") exit "${FAKE_CHECKS_RC:-0}" ;;
esac
GH
chmod +x "$T/bin/gh"
PATH="$T/bin:$PATH"
case_ "base develop" pr-guard.sh 2 "$(json_cmd 'gh pr create --base develop --title x')" "PR 의 base 는 언제나 main"
FAKE_OPEN_PRS=0 case_ "base main, 열린 작업 PR 0" pr-guard.sh 0 "$(json_cmd 'gh pr create --base main --title x')"
FAKE_OPEN_PRS=1 case_ "열린 작업 PR 1" pr-guard.sh 2 "$(json_cmd 'gh pr create --base main --title x')" "열린 작업 PR 이 1 개"
FAKE_CHECKS_RC=8 case_ "merge — 체크가 돈다" pr-guard.sh 2 "$(json_cmd 'gh pr merge 7 --merge')" "체크가 아직 돈다"
FAKE_CHECKS_RC=1 case_ "merge — 빨간 체크" pr-guard.sh 2 "$(json_cmd 'gh pr merge 7 --merge')" "빨간 체크가 있다"
FAKE_CHECKS_RC=0 case_ "merge — 초록" pr-guard.sh 0 "$(json_cmd 'gh pr merge 7 --merge')"
case_ "ls" pr-guard.sh 0 "$(json_cmd 'ls')"

echo "push-guard:"
case_ "push origin main" push-guard.sh 2 "$(json_cmd 'git push origin main')" "main 에 직접 안 민다"
case_ "push HEAD:refs/heads/main" push-guard.sh 2 "$(json_cmd 'git push origin HEAD:refs/heads/main')" "main 에 직접 안 민다"
stamp_head fast
case_ "work 가지, fast 도장뿐" push-guard.sh 2 "$(json_cmd 'git push')" "full 도장이 없다"
stamp_head full
case_ "work 가지, full 도장" push-guard.sh 0 "$(json_cmd 'git push')"
(cd "$T/repo" && git checkout -q main)
case_ "현재 가지가 main" push-guard.sh 2 "$(json_cmd 'git push')" "현재 가지가 main 이다"
(cd "$T/repo" && git checkout -q work/x)
case_ "ls" push-guard.sh 0 "$(json_cmd 'ls')"

echo "commit-guard:"
: > "$T/repo/.git/verify-stamp"
echo more >> "$T/repo/src/A.java"
case_ "work 가지, 도장 없음" commit-guard.sh 2 "$(json_cmd 'git commit -m x')" "도장 없는 커밋은 work/* 에 안 올린다"
case_ "verify && commit 한 체인" commit-guard.sh 2 "$(json_cmd 'bash scripts/verify.sh && git commit -m x')" "도장 없는 커밋은"
case_ "git status" commit-guard.sh 0 "$(json_cmd 'git status')"
stamp_worktree
case_ "작업 트리 지문이 도장에 있다" commit-guard.sh 0 "$(json_cmd 'git commit -m x')"
(cd "$T/repo" && git stash -q && git checkout -q main)
case_ "main 가지" commit-guard.sh 0 "$(json_cmd 'git commit -m x')"
(cd "$T/repo" && git checkout -q work/x && git stash pop -q)

echo "java-home-guard:"
case_ "mvnw 맨몸" java-home-guard.sh 2 "$(json_cmd './mvnw -q test')" "mvnw 는 bash scripts/mvn.sh"
case_ "mvnw.cmd 맨몸" java-home-guard.sh 2 "$(json_cmd 'mvnw.cmd package')" "mvnw 는 bash scripts/mvn.sh"
case_ "JAVA_HOME= 붙임" java-home-guard.sh 0 "$(json_cmd 'JAVA_HOME="C:/x" ./mvnw test')"
case_ "scripts/mvn.sh" java-home-guard.sh 0 "$(json_cmd 'bash scripts/mvn.sh -q test')"
case_ "한글 줄의 mvnw" java-home-guard.sh 0 "$(json_cmd 'echo "설명 ./mvnw 는 느리다"')"

echo "편집 훅 둘(가짜 doc-lint):"
cat > "$T/repo/scripts/doc-lint.sh" <<'LINT'
#!/usr/bin/env bash
echo "$*" > "$(dirname "$0")/../.lint-args"
echo "[가짜 린트] rc=${FAKE_LINT_RC:-0}"
exit "${FAKE_LINT_RC:-0}"
LINT
FAKE_LINT_RC=1 case_ "CLAUDE.md 편집, 린트 빨강" doc-lint-edit.sh 2 "$(json_file "$T/repo/CLAUDE.md")" "[가짜 린트] rc=1"
n=$((n + 1))
if grep -q "CLAUDE.md" "$T/repo/.lint-args" 2>/dev/null; then echo "  [통과] $n doc-lint-edit.sh — 편집한 파일 하나를 넘긴다(범위 모드)"
else echo "  [실패] $n doc-lint-edit.sh — 받은 인자: $(cat "$T/repo/.lint-args" 2>/dev/null)"; fail=1; fi
FAKE_LINT_RC=0 case_ "CLAUDE.md 편집, 린트 초록" doc-lint-edit.sh 0 "$(json_file "$T/repo/CLAUDE.md")"
FAKE_LINT_RC=1 case_ "Java 편집은 안 부른다" doc-lint-edit.sh 0 "$(json_file "$T/repo/src/A.java")"
FAKE_LINT_RC=1 case_ "sed -i PLAN.md, 린트 빨강" doc-lint-bash.sh 2 "$(json_cmd "sed -i 's/a/b/' PLAN.md")" "[가짜 린트] rc=1"
FAKE_LINT_RC=1 case_ "ls 는 안 부른다" doc-lint-bash.sh 0 "$(json_cmd 'ls')"
rm -f "$T/repo/scripts/doc-lint.sh" "$T/repo/.lint-args"

echo "session-state:"
printf '## 현재 상태\n\n| 무엇 | 상태 |\n|---|---|\n| 가 | 나 |\n\n## 이력\n' > "$T/repo/PROGRESS.md"
n=$((n + 1))
out=$(printf '{}' | bash "$H/session-state.sh" 2>/dev/null); got=$?
if [ "$got" = 0 ] && printf '%s' "$out" | grep -q "| 가 | 나 |"; then echo "  [통과] $n session-state.sh — 「현재 상태」를 낸다"
else echo "  [실패] $n session-state.sh — exit $got, 출력 ${out:0:60}"; fail=1; fi
rm -f "$T/repo/PROGRESS.md"

echo "stop 훅 둘:"
case_ "uncommitted — stop_hook_active" stop-uncommitted.sh 0 '{"stop_hook_active":true}'
echo dirty >> "$T/repo/src/A.java"
case_ "uncommitted — 더러운 트리" stop-uncommitted.sh 2 '{"stop_hook_active":false}' "커밋 안 된 작업물이 있다"
(cd "$T/repo" && git checkout -q -- src/A.java)
case_ "uncommitted — 깨끗한 트리" stop-uncommitted.sh 0 '{"stop_hook_active":false}'
case_ "stamp — stop_hook_active" stop-stamp.sh 0 '{"stop_hook_active":true}'
stamp_head fast
case_ "stamp — HEAD 지문 도장 있음" stop-stamp.sh 0 '{"stop_hook_active":false}'
: > "$T/repo/.git/verify-stamp"
case_ "stamp — 도장 비움" stop-stamp.sh 2 '{"stop_hook_active":false}' "검증 도장이 없다"

echo "지문(윈도 경로 변환):"
n=$((n + 1))
(cd "$T/repo" && git update-ref refs/remotes/origin/main HEAD \
  && sed '/^  scripts \.claude/s| \.claude/settings\.json||' scripts/verify-fingerprint.sh > "$T/fp-fallback.sh")
if grep -q '^  scripts \.claude/settings\.json' "$T/fp-fallback.sh" || ! grep -q '^lane tools .*\.claude/settings\.json' "$T/fp-fallback.sh"; then
  echo "  [실패] $n verify-fingerprint.sh — 목록 밖 사본을 못 만들었다(목록 줄 꼴이 바뀌었다)"; fail=1
else
  h=$(cd "$T/repo" && git rev-parse HEAD)
  a=$(cd "$T/repo" && cp "$T/fp-fallback.sh" scripts/fp-fallback.sh && bash scripts/fp-fallback.sh "$h")
  b=$(cd "$T/repo" && bash scripts/fp-fallback.sh origin/main; rm -f scripts/fp-fallback.sh)
  if [ "$a" = "$b" ]; then echo "  [통과] $n verify-fingerprint.sh — 목록 밖 .claude 경로도 16진수 커밋·origin/main 이 같다"
  else echo "  [실패] $n verify-fingerprint.sh — 16진수 커밋과 origin/main 이 다르다(윈도 경로 변환)"; fail=1; fi
fi

echo "doc-lint(임시 사본):"
L=$T/lint; mkdir -p "$L/scripts"; cp "$R/scripts/doc-lint.sh" "$L/scripts/"
(cd "$L" && git init -q && git config user.email t@t && git config user.name t && git config commit.gpgsign false)
lint_case() { # 이름, 파일, 본문, 기대 라벨(빈 값이면 초록이어야)
  local name=$1 f=$2 body=$3 label=$4
  n=$((n + 1)); printf '%b' "$body" > "$L/$f"
  local out rc; out=$(cd "$L" && bash scripts/doc-lint.sh "$f" 2>&1); rc=$?
  if [ -z "$label" ]; then
    if [ "$rc" = 0 ]; then echo "  [통과] $n doc-lint — $name"; else echo "  [실패] $n doc-lint — $name: rc=$rc ${out:0:80}"; fail=1; fi
  elif [ "$rc" != 0 ] && printf '%s' "$out" | grep -qF "$label"; then echo "  [통과] $n doc-lint — $name"
  else echo "  [실패] $n doc-lint — $name: rc=$rc, 「$label」 없음. ${out:0:80}"; fail=1; fi
  rm -f "$L/$f"
}
lint_case "깨끗한 문서" ok.md '# 제목\n\n평서형으로 적었다.\n' ''
lint_case "존댓말 어미(합니다)" h.md '# 제목\n\n이렇게 합니다.\n' '[존댓말]'
lint_case "인용은 면제" q.md '# 제목\n\n남의 말 「이렇게 합니다」 를 옮겼다.\n' ''
lint_case "html 문구" t.html '<p>이렇게 하세요</p>\n' '[존댓말]'

# 0-33 — gate-probe 판정기 자체: 안 부순 패치를 「게이트가 죽었다」 로 잡는가. 셸만 돌아 빠르다(메이븐 프로브는 주 1회 CI)
n=$((n + 1))
if out=$(bash "$R/scripts/gate-probe.sh" --self-test 2>&1); then echo "  [통과] $n gate-probe — 판정기(죽은 게이트를 잡는다)"
else echo "  [실패] $n gate-probe — 판정기: ${out: -120}"; fail=1; fi
n=$((n + 1))
if out=$(bash "$R/scripts/gate-probe.sh" migration-immutable 2>&1); then echo "  [통과] $n gate-probe — migration-immutable 프로브가 산다"
else echo "  [실패] $n gate-probe — migration-immutable: ${out: -120}"; fail=1; fi
# 0-36 — m2/ 없는 폴더(CI 와 같다)에서 offline-download 를 건너뛰면 끝 줄이 「산다」 가 아니라 「건너뜀 1」
n=$((n + 1))
mkdir -p "$T/gp/scripts" && cp "$R/scripts/gate-probe.sh" "$T/gp/scripts/"
out=$(bash "$T/gp/scripts/gate-probe.sh" offline-download 2>&1); rc=$?
if [ $rc -eq 0 ] && [[ "$(echo "$out" | tail -1)" == "산다 0 · 건너뜀 1"* ]]; then echo "  [통과] $n gate-probe — 건너뜀을 따로 센다"
else echo "  [실패] $n gate-probe — 건너뜀 요약: rc=$rc ${out: -120}"; fail=1; fi

echo
if [ "$fail" -eq 0 ]; then echo "훅·도구 회귀 시험 통과 — ${n}경우"; else echo "훅·도구 회귀 시험 실패"; fi
exit "$fail"
