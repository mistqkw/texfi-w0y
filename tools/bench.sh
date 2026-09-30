#!/usr/bin/env bash
# Замеры w0y на подключённом телефоне (сборка bench: ./gradlew assembleBench,
# adb install -r app/build/outputs/apk/bench/app-bench.apk).
#
#   tools/bench.sh            холодный старт x10, джанк на скролле главной, память
#
# Перед первым запуском один раз пройди приветствие вручную или через adb
# (pm grant ... POST_NOTIFICATIONS, затем «SKIP»). Экран должен быть включён.
set -u
ADB=${ADB:-/home/mista/toolchain/android-sdk/platform-tools/adb}
PKG=${PKG:-com.texfi.w0y.bench}
ACT=$PKG/com.texfi.w0y.MainActivity

$ADB shell input keyevent KEYCODE_WAKEUP
$ADB shell wm dismiss-keyguard

echo "== холодный старт (am start -W, мс: TotalTime = до первого кадра)"
totals=()
for i in $(seq 1 10); do
  $ADB shell am force-stop $PKG
  sleep 1
  t=$($ADB shell am start -W -n $ACT | awk '/TotalTime/{print $2}')
  totals+=("$t")
  sleep 2
done
printf '%s\n' "${totals[@]}" | sort -n | awk '{a[NR]=$1; s+=$1} END {printf "runs=%d min=%d median=%d max=%d mean=%.0f\n", NR, a[1], a[int((NR+1)/2)], a[NR], s/NR}'

echo "== скролл главной (gfxinfo: кадры и джанк)"
$ADB shell am force-stop $PKG
$ADB shell am start -W -n $ACT >/dev/null
sleep 5   # заставка и первая отрисовка
$ADB shell dumpsys gfxinfo $PKG reset >/dev/null
for i in 1 2 3 4 5 6; do
  $ADB shell input swipe 540 1800 540 600 350
  sleep 0.4
  $ADB shell input swipe 540 600 540 1800 350
  sleep 0.4
done
sleep 1
$ADB shell dumpsys gfxinfo $PKG | grep -E "Total frames rendered|Janky frames|50th|90th|95th|99th|Number Missed Vsync|Number High input latency|Number Slow UI|Number Slow bitmap|Number Frame deadline"

echo "== память (PSS, КБ)"
$ADB shell dumpsys meminfo $PKG | grep -E "TOTAL PSS|TOTAL:|Native Heap|Java Heap" | head -4

echo "== размер APK"
ls -l "$(dirname "$0")/../app/build/outputs/apk/bench/app-bench.apk" 2>/dev/null | awk '{print $5 " байт"}'
