#!/usr/bin/env bash
# 옛 판 DB 이미지 다섯을 받는다(V-21, 네트워크·수 GB). `--full` 의 옛 판 표본(Oracle11·Mysql57·Mysql80·Mssql2017·Postgres12CorpusTest)이 쓴다.
# --check 는 받지 않고 로컬에 있는지만 본다 — 없는 것이 있으면 1.
set -u
images="gvenzl/oracle-xe:11-slim mysql:5.7 mysql:8.0 mcr.microsoft.com/mssql/server:2017-latest postgres:12-alpine"
check=0
[ "${1:-}" = "--check" ] && check=1
docker info >/dev/null 2>&1 || { echo "Docker 가 꺼져 있다 — bash scripts/docker-up.sh"; exit 1; }
miss=0
for i in $images; do
  if docker image inspect "$i" >/dev/null 2>&1; then
    echo "있음 $i"
  elif [ $check = 1 ]; then
    echo "없음 $i"; miss=1
  else
    echo "받기 $i"
    docker pull "$i" || { echo "실패 $i"; miss=1; }
  fi
done
[ $miss = 0 ] || { [ $check = 1 ] && echo "bash scripts/docker-pull-old.sh 로 받는다(네트워크)"; exit 1; }
