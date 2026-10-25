# JDK 17 고르기(0-72) — mvn.sh·make-example-templates.sh·package.sh 가 윈도에서 source 해 부르고, hooks-test.sh 가 가짜 java 로 잰다(고르기 한 자리).
# jdk17_pick — 고른 폴더를 표준 출력에 찍고 0. 못 고르면 사유 한 줄을 표준 오류에 찍고 1.
#   ① TOOLBOX_JDK17_HOME — 있는데 17 이 아니면 아래로 안 내려가고 실패(명시한 값을 조용히 덮지 않는다)
#   ② JAVA_HOME 이 17 이면 그대로 ③ C:/Program Files/Java/jdk-17.0.19 ④ 셋 다 아니면 실패
#   17 판정은 `java -version` 글 — Oracle 「java version "17…"」·OpenJDK·Temurin 「openjdk version "17…"」 둘 다.
jdk17_is17() { "$1/bin/java" -version 2>&1 | grep -qE 'version "17[".]'; }
jdk17_pick() {
  if [ -n "${TOOLBOX_JDK17_HOME:-}" ]; then
    jdk17_is17 "$TOOLBOX_JDK17_HOME" && { echo "$TOOLBOX_JDK17_HOME"; return 0; }
    echo "TOOLBOX_JDK17_HOME=$TOOLBOX_JDK17_HOME 에 JDK 17 이 없다" >&2; return 1
  fi
  if [ -n "${JAVA_HOME:-}" ] && jdk17_is17 "$JAVA_HOME"; then echo "$JAVA_HOME"; return 0; fi
  local d="C:/Program Files/Java/jdk-17.0.19"
  jdk17_is17 "$d" && { echo "$d"; return 0; }
  echo "JDK 17 을 못 찾았다 — TOOLBOX_JDK17_HOME 에 JDK 17 폴더를 넣는다(JAVA_HOME=${JAVA_HOME:-없음})" >&2; return 1
}
