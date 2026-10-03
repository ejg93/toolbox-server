#!/usr/bin/env bash
# 이 저장소의 Maven JVM 과 그 자손(surefire 테스트 JVM 등) 중 주어진 시각(epoch 초) 뒤에 뜬 것을 끈다 — 고아일 때만 부른다(0-42).
# Maven JVM: java.exe 이고 명령줄에 이 저장소 경로(슬래시·역슬래시 둘 다)와 MavenWrapperMain 또는 plexus-classworlds 가 있다.
# 자손: 윈도 부모 PID 로 내려간다(surefire 는 임시 폴더에서 떠 명령줄에 저장소 경로가 없다). 시각 거름으로 재사용 PID 를 피한다.
# 앱 서버(run.bat 의 java -jar)는 Maven 이 아니라 안 걸린다. Testcontainers 컨테이너는 JVM 이 죽으면 ryuk 가 지운다.
#   bash scripts/reap-maven.sh <시작 epoch 초> [누가]
# 끈 PID 를 한 줄에 하나 내고, 끈 것이 있으면 .git/mvn-reap.log 에 한 줄 적는다. 윈도가 아니면 아무것도 안 한다.
cd "$(dirname "$0")/.." || exit 0
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) ;; *) exit 0 ;; esac
killed=$(REAP_REPO=$(pwd -W 2>/dev/null) REAP_SINCE=${1:-0} powershell -NoProfile -ExecutionPolicy Bypass -Command '
  $r1 = $env:REAP_REPO; $r2 = $r1.Replace("/", "\")
  $since = [DateTimeOffset]::FromUnixTimeSeconds([int64]$env:REAP_SINCE).LocalDateTime.AddSeconds(-5)
  $all = @(Get-CimInstance Win32_Process | Where-Object { $_.CreationDate -ge $since })
  $ids = @($all | Where-Object { $_.Name -eq "java.exe" -and $_.CommandLine -and ($_.CommandLine.Contains($r1) -or $_.CommandLine.Contains($r2)) -and ($_.CommandLine -match "MavenWrapperMain|plexus-classworlds") } | ForEach-Object { [int]$_.ProcessId })
  if ($ids.Count -eq 0) { exit 0 }
  do {
    $more = @($all | Where-Object { $ids -notcontains [int]$_.ProcessId -and $ids -contains [int]$_.ParentProcessId } | ForEach-Object { [int]$_.ProcessId })
    $ids += $more
  } while ($more.Count -gt 0)
  [array]::Reverse($ids)
  foreach ($id in $ids) { try { Stop-Process -Id $id -Force -ErrorAction Stop; $id } catch {} }
' < /dev/null 2>/dev/null | tr -d "\r")
[ -n "$killed" ] || exit 0
echo "$killed"
echo "$(date "+%F %T") ${2:-reap} 끔: $(echo $killed)" >> .git/mvn-reap.log
exit 0
