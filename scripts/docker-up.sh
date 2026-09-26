#!/usr/bin/env bash
# Docker 가 꺼져 있으면 Docker Desktop 을 켜고 3분까지 기다린다. 컨테이너 테스트(`db` 태그) 앞에 부른다.
set -u
if docker info >/dev/null 2>&1; then echo "Docker 켜져 있음"; exit 0; fi
exe="C:/Program Files/Docker/Docker/Docker Desktop.exe"
[ -f "$exe" ] || { echo "Docker Desktop 이 없다: $exe"; exit 1; }
echo "Docker Desktop 기동 중"
(cd / && "$exe" >/dev/null 2>&1 &)
for i in $(seq 1 36); do
  sleep 5
  docker info >/dev/null 2>&1 && { echo "Docker 켜짐 ($((i*5))초)"; exit 0; }
done
echo "3분 안에 안 떴다"; exit 1
