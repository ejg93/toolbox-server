# 리허설 소켓 판정(8-14) — rehearse.sh 가 source 해 부르고, hooks-test.sh 가 가짜 netstat 줄로 잰다(판정 한 자리).
# socket_verdict <port> <pid> — 표준 입력 = `netstat -ano` 줄. 빨강이면 사유 한 줄을 찍고 1, 초록이면 아무것도 안 찍고 0.
#   듣기(LISTENING)는 127.0.0.1 만(구역 표 web 「127.0.0.1 외 바인드 금지」·PLAN 8-11 ⑥) — [::1] 로 듣는 회귀도 빨강(PR #38 리뷰 11차).
#   연결 상대·UDP 는 IPv4·IPv6 루프백까지 루프백으로 본다(규칙 1 은 밖으로 나가는 것).
#   PID 는 늘 마지막 칸 — TCP 는 5칸, UDP 는 상태가 없어 4칸.
socket_verdict() {
  local port=$1 pid=$2 lines listen mine
  local bind4='^127\.0\.0\.1:' loop='^(127\.0\.0\.1|\[::1\]):'
  lines=$(tr -d '\r')
  listen=$(printf '%s\n' "$lines" | awk -v p=":$port" '$1=="TCP" && $4=="LISTENING" && $2 ~ p"$"')
  [ -n "$listen" ] || { echo "LISTENING 줄이 없다"; return 1; }
  if printf '%s\n' "$listen" | awk '{print $2}' | grep -Ev "$bind4" | grep -q .; then
    echo "127.0.0.1 밖에서 듣는다: $listen"; return 1
  fi
  # 그 PID 의 모든 소켓(TCP·UDP, IPv4·IPv6) — 루프백 밖으로 듣거나 연결하면 빨강(규칙 1)
  mine=$(printf '%s\n' "$lines" | awk -v pid="$pid" '($1=="TCP" || $1=="UDP") && $NF==pid')
  if printf '%s\n' "$mine" | awk '$1=="TCP" && $4=="LISTENING" {print $2}' | grep -Ev "$bind4" | grep -q .; then
    echo "서버가 127.0.0.1 밖 주소로 듣는다: $mine"; return 1
  fi
  if printf '%s\n' "$mine" | awk '$1=="TCP" && $4!="LISTENING" {print $3}' | grep -Ev "$loop|^0\.0\.0\.0:0$|^\[::\]:0$" | grep -q .; then
    echo "서버가 밖으로 연결했다: $mine"; return 1
  fi
  if printf '%s\n' "$mine" | awk '$1=="UDP" {print $2}' | grep -Ev "$loop" | grep -q .; then
    echo "서버가 루프백 밖 UDP 소켓을 열었다: $mine"; return 1
  fi
  return 0
}
