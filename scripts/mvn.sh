#!/usr/bin/env bash
# `./mvnw` 를 JDK 17 로 부른다. 이 기계의 JAVA_HOME 은 JDK 11 이라 맨몸 mvnw 는 17 코드를 못 컴파일한다(java-home-guard 가 막는다).
# 리눅스 러너(CI)는 setup-java 가 JAVA_HOME 을 주니 그대로 둔다.
cd "$(dirname "$0")/.."
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) export JAVA_HOME="C:/Program Files/Java/jdk-17.0.19" ;; esac
exec ./mvnw "$@"
