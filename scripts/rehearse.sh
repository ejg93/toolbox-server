#!/usr/bin/env bash
# 반입 리허설(8-11) — zip 을 저장소 밖에 풀고, PATH·JAVA_HOME 을 비워 동봉 JDK(jre\)만으로:
#   ① 묶음 검사 ② java 가 PATH 에 없는지 ③ toolbox.bat version ④ toolbox.bat selftest(java.home = 푼 폴더\jre)
#   ⑤ build.bat — 네트워크 없이 다시 빌드(Downloading 없음, app.jar 갱신) ⑥ run.bat 기동 → ping → 127.0.0.1 만 LISTENING → 끔
#   ⑦ 푼 폴더에 H2 가 생기고 저장소 data/ 는 그대로
# 띄운 서버와 임시 폴더는 어떤 끝에서도 치운다(0-42 교훈).
#   bash scripts/rehearse.sh <zip> [--port N] [--break jre]
#     --break jre  푼 폴더의 jre 를 치우고 ③ 이 한글 사유로 빨강인지만 본다(강제 지점이 사는지)
# 진짜 새 PC·네트워크 차단·AppLocker·Tibero 는 사람이 한다(PLAN 16장 끝).
set -uo pipefail
cd "$(dirname "$0")/.."
R=$(pwd)
zip=${1:?zip 경로}
shift
port=41799
brk=""
while [ $# -gt 0 ]; do
  case "$1" in
    --port) port=${2:?}; shift 2 ;;
    --break) brk=${2:?}; shift 2 ;;
    *) echo "모르는 인자: $1"; exit 2 ;;
  esac
done
[ -f "$zip" ] || { echo "zip 이 없다: $zip"; exit 2; }
zip=$(cd "$(dirname "$zip")" && pwd)/$(basename "$zip")

base="${TEMP:-/tmp}"
command -v cygpath >/dev/null 2>&1 && base=$(cygpath -u "$base")
TMPD="$base/toolbox-rehearse-$$"
server_pid=""
launcher=""
cleanup() {
  if [ -n "$server_pid" ]; then taskkill //F //T //PID "$server_pid" >/dev/null 2>&1; fi
  # 띄운 cmd 트리(cmd → toolbox.bat → java) — ping 전에 죽어도 남기지 않는다
  if [ -n "$launcher" ]; then taskkill //F //T //PID "$launcher" >/dev/null 2>&1; fi
  rm -rf "$TMPD" 2>/dev/null || { sleep 2; rm -rf "$TMPD" 2>/dev/null; }
}
trap cleanup EXIT
mkdir -p "$TMPD"
step=0
notes=0
ok() { step=$((step + 1)); echo "  [통과] $step $1"; }
repo_data() { find "$R/data" -type f -printf '%p %s %T@\n' 2>/dev/null | LC_ALL=C sort | sha256sum | cut -d' ' -f1; }
data_before=$(repo_data)
die() { echo "  [실패] $1"; echo "리허설 빨강"; exit 1; }

echo "== 풀기 $TMPD"
unzip -q "$zip" -d "$TMPD" || die "풀지 못했다"
top=$(find "$TMPD" -mindepth 1 -maxdepth 1 -type d | head -1)
WD=$(cygpath -w "$top")

# 비운 환경으로 cmd 한 줄을 돌린다 — 끝 코드를 그대로 돌려준다
run_cmd() {
  printf '%s\r\n' '@echo off' 'set "JAVA_HOME="' 'set "PATH=C:\Windows\System32;C:\Windows;C:\Windows\System32\WindowsPowerShell\v1.0"' \
    'set "TOOLBOX_NO_PAUSE=1"' "cd /d \"$WD\"" "$1" 'exit /b %ERRORLEVEL%' > "$TMPD/run.cmd"
  cmd //c "$(cygpath -w "$TMPD/run.cmd")" < /dev/null
}

if [ "$brk" = "jre" ]; then
  mv "$top/jre" "$top/jre.off" || die "jre 를 못 치웠다"
  out=$(run_cmd 'call .\toolbox.bat version' 2>&1); rc=$?
  if [ $rc -ne 0 ] && printf '%s' "$out" | grep -q "java"; then
    echo "  [통과] jre 가 없으면 한글 사유로 멈춘다 — $(printf '%s' "$out" | head -1)"
    echo "깨 보기 통과 — 강제 지점이 산다"; exit 0
  fi
  die "jre 를 치웠는데 version 이 돌았다(rc=$rc): $out"
fi

# ① 묶음 검사
bash "$R/scripts/package-check.sh" "$top" >/dev/null || die "푼 폴더 묶음 검사"
ok "푼 폴더 묶음 검사"

# ② PATH 에 java 가 없다
if run_cmd 'where java' >/dev/null 2>&1; then
  step=$((step + 1)); notes=$((notes + 1)) # 단계는 센다 — 판정을 ④ 로 넘긴 것이지 건너뛴 것이 아니다(끝의 일곱 단계 검사, PR #38 리뷰 8차)
  echo "  [알림] $step 비운 PATH 에서도 java 가 보인다 — ④ 의 java.home 줄로만 판정한다(행 8-11 사다리)"
else
  ok "비운 PATH 에 java 없음"
fi

# ③ version
want=$(sed -n 's/^버전 \([^ ]*\) .*/\1/p' "$top/MANIFEST.txt" | head -1)
out=$(run_cmd 'call .\toolbox.bat version' 2>&1); rc=$?
[ $rc -eq 0 ] && printf '%s' "$out" | grep -q "toolbox-server $want" || die "version(rc=$rc): $out"
ok "version — toolbox-server $want"

# ④ selftest
out=$(run_cmd 'call .\toolbox.bat selftest' 2>/dev/null); rc=$?
[ $rc -eq 0 ] || die "selftest(rc=$rc): $(printf '%s' "$out" | grep -E '실패|통과 ' | head -5)"
jline=$(printf '%s\n' "$out" | grep "^\[통과\] 자바" | head -1)
[[ "${jline,,}" == *"${WD,,}\\jre"* ]] || die "java.home 이 푼 폴더의 jre 가 아니다: $jline" # grep -iF 는 역슬래시 패턴에서 죽는다(Git Bash)
ok "selftest — $(printf '%s\n' "$out" | tail -1 | tr -d '\r')"

# ⑤ build.bat
before=$(stat -c %Y "$top/app.jar")
sleep 1
out=$(run_cmd 'call .\build.bat' 2>&1); rc=$?
[ $rc -eq 0 ] || die "build.bat(rc=$rc): $(printf '%s' "$out" | tail -5)"
printf '%s' "$out" | grep -qi "downloading" && die "build.bat 이 무엇을 받으려 했다"
after=$(stat -c %Y "$top/app.jar")
[ "$after" -gt "$before" ] || die "app.jar 가 안 바뀌었다"
ok "build.bat — 네트워크 없이 다시 빌드, app.jar 갱신"

# ⑥ run.bat 기동
run_cmd "call .\run.bat --no-browser --port $port" > "$TMPD/server.log" 2>&1 &
launcher=$(cat "/proc/$!/winpid" 2>/dev/null || true)
c=000
for _ in $(seq 1 60); do
  c=$(curl -s -o /dev/null -w "%{http_code}" --max-time 2 "http://127.0.0.1:$port/api/ping" || true)
  [ "$c" = 200 ] && break
  sleep 1
done
[ "$c" = 200 ] || die "기동·ping($c): $(tail -5 "$TMPD/server.log")"
listen=$(netstat -ano | tr -d '\r' | awk -v p=":$port" '$1=="TCP" && $4=="LISTENING" && $2 ~ p"$"')
server_pid=$(printf '%s\n' "$listen" | awk '{print $5}' | head -1)
[ -n "$listen" ] || die "LISTENING 줄이 없다"
if printf '%s\n' "$listen" | awk '{print $2}' | grep -qv "^127\.0\.0\.1:$port$"; then die "127.0.0.1 밖에서 듣는다: $listen"; fi
# 그 PID 의 모든 소켓(TCP·UDP, IPv4·IPv6) — 루프백 밖으로 듣거나 연결하면 빨강(규칙 1)
loop='^(127\.0\.0\.1|\[::1\]):'
mine=$(netstat -ano | tr -d '\r' | awk -v pid="$server_pid" '($1=="TCP" || $1=="UDP") && $NF==pid')
printf '%s\n' "$mine" | awk '$1=="TCP" && $4=="LISTENING" {print $2}' | grep -Ev "$loop" | grep -q . && die "서버가 루프백 밖 주소로 듣는다: $mine"
printf '%s\n' "$mine" | awk '$1=="TCP" && $4!="LISTENING" {print $3}' | grep -Ev "$loop|^0\.0\.0\.0:0$|^\[::\]:0$" | grep -q . && die "서버가 밖으로 연결했다: $mine"
printf '%s\n' "$mine" | awk '$1=="UDP" {print $2}' | grep -Ev "$loop" | grep -q . && die "서버가 루프백 밖 UDP 소켓을 열었다: $mine"
taskkill //F //T //PID "$server_pid" >/dev/null 2>&1
server_pid=""
sleep 2
netstat -ano | tr -d '\r' | awk -v p=":$port" '$1=="TCP" && $4=="LISTENING" && $2 ~ p"$"' | grep -q . && die "끈 뒤에도 포트가 열려 있다"
ok "run.bat — ping 200, 127.0.0.1:$port 만 LISTENING, 끈 뒤 닫힘"

# ⑦ H2 는 푼 폴더에, 저장소 data/ 는 그대로
[ -f "$top/data/toolbox.mv.db" ] || die "푼 폴더 data/ 에 H2 가 없다"
[ "$(repo_data)" = "$data_before" ] || die "저장소 data/ 가 바뀌었다"
ok "H2 는 푼 폴더 data/ 에, 저장소 data/ 그대로"

[ "$step" -eq 7 ] || die "단계 수가 PLAN 8-11(일곱)과 다르다: $step"
echo "리허설 통과 — 일곱 단계$([ "$notes" -gt 0 ] && echo " · 알림 $notes") · 사람 몫: 네트워크를 실제로 끊고 한 번 · 새 PC 또는 VM · AppLocker · Tibero(PLAN 16장 끝)"
