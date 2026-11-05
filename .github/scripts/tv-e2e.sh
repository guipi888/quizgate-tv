#!/usr/bin/env bash
# QuizGate TV 端到端验证脚本。
#
# 为什么需要这个文件：android-emulator-runner 的 script 输入会被
# parseScript() 按行拆开，每行单独用 `sh -c`（dash）执行——
# 变量不跨行保留，多行 if/for 直接语法错误。
# 因此 tv-test.yml 只写一行 `bash .github/scripts/tv-e2e.sh`，
# 全部逻辑放这里，用真正的 bash 执行。
#
# 覆盖的 P0 项：
#   ① TV 桌面可见性（LEANBACK_LAUNCHER）
#   ② 计时/锁屏（由仪器测试覆盖）
#   ⑥ U 盘题库导入（推送样本 + 仪器测试断言；API 29+ 分区存储下断言优雅降级）
#   ⑧ 重启恢复（真实 adb reboot + BOOT_COMPLETED）
set -euo pipefail

PKG=com.quizgate.tv

echo "== 安装 App 与仪器测试 APK =="
./gradlew installDebug installDebugAndroidTest --no-daemon

echo "== 授予仪器测试依赖的权限 =="
# 悬浮窗：Android 10+ 后台拉起锁屏页 / 开机恢复必需（appops 方式，重装保持）
adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow || true
# 通知：前台服务常驻倒计时通知（API 33+ 运行时权限）
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
# 存储：U 盘题库导入（仅 API<=32 有效，33+ 见 README 已知限制）
adb shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE || true

echo "== P0 ⑥ 准备 U 盘题库样本（模拟插入 U 盘）=="
adb shell mkdir -p /sdcard/QuizGate/banks
adb push app/src/androidTest/assets/usb_bank_sample.json /sdcard/QuizGate/banks/usb_bank_sample.json

echo "== P0 ① TV 桌面可见性（LEANBACK_LAUNCHER）=="
if ! adb shell dumpsys package "$PKG" | grep -q "android.intent.category.LEANBACK_LAUNCHER"; then
  echo "::error::App 未在 TV 桌面（LEANBACK_LAUNCHER）可见"
  exit 1
fi
echo "TV 桌面可见 OK"

FAIL=0

# ============ 一次性诊断：TV 滚动机制对照实验（结论落地后删除本段） ============
# 背景：clickAfterScroll 原用 UiDevice.swipe，CI 实测在 TV 镜像上滑 4 次
# 目标按钮仍 <90% 可见；Espresso scrollTo 同样无效。本段用 uiautomator 的
# bounds（屏幕像素坐标，1920x1080）对照三种机制谁能真正滚动 ScrollView：
#   input swipe / input motionevent / D-pad 焦点导航。
DIAG_XML=/sdcard/qg_diag.xml
diag_dump() {
  for t in 1 2 3 4 5; do
    if adb shell uiautomator dump "$DIAG_XML" >/dev/null 2>&1; then break; fi
    sleep 2
  done
  adb shell cat "$DIAG_XML" 2>/dev/null | tr '<' '\n' \
    | grep -E 'btn_start_timer|btn_change_pin|btn_lock_now|tg_custom|btn_rescan_bank|btn_parent_unlock|tv_lock_prompt' \
    | while IFS= read -r line; do
        id=$(printf '%s' "$line" | grep -oE 'resource-id="[^"]*"' | head -1)
        bd=$(printf '%s' "$line" | grep -oE 'bounds="[^"]*"' | head -1)
        tx=$(printf '%s' "$line" | grep -oE 'text="[^"]*"' | head -1)
        echo "    $id $bd $tx"
      done
  return 0
}
echo "== DIAG-0 屏幕与输入设备 =="
adb shell wm size || true
adb shell wm density || true
adb shell dumpsys input 2>/dev/null | grep -iE "Device [0-9]+:|TouchInputMapper" | head -10 || true
echo "== DIAG-A 设置页启动后（滚动前） =="
adb shell am force-stop "$PKG" || true
adb shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1 \
  || adb shell monkey -p "$PKG" -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1 \
  || true
sleep 3
diag_dump || true
echo "== DIAG-B input swipe x4 后 =="
for i in 1 2 3 4; do adb shell input swipe 960 810 960 270 || true; sleep 1; done
diag_dump || true
echo "== DIAG-C input motionevent 序列后 =="
adb shell input motionevent DOWN 960 810 || true
adb shell input motionevent MOVE 960 700 || true
adb shell input motionevent MOVE 960 500 || true
adb shell input motionevent MOVE 960 300 || true
adb shell input motionevent UP 960 270 || true
sleep 1
diag_dump || true
echo "== DIAG-D D-pad DOWN x15 后 =="
for i in $(seq 1 15); do adb shell input keyevent 20 || true; done
sleep 1
diag_dump || true
echo "== DIAG-E 锁屏页 bounds（注入 lock_active prefs 后启动） =="
adb shell am force-stop "$PKG" || true
END=$(( $(date +%s) * 1000 + 1800000 ))
# LockActivity.onCreate 见到 lock_active=false 会直接 finish，必须翻成 true
sed "s/__LOCK_END_AT__/$END/; s/name=\"lock_active\" value=\"false\"/name=\"lock_active\" value=\"true\"/" \
  app/src/androidTest/assets/prefs_timer_running.xml \
  | adb shell "run-as $PKG sh -c 'mkdir -p shared_prefs && cat > shared_prefs/quizgate_prefs.xml'" || true
adb shell am start -n "$PKG/.LockActivity" >/dev/null 2>&1 || true
sleep 3
diag_dump || true
adb shell am force-stop "$PKG" || true
# ============ 诊断结束 ============

echo "== 单元测试 + 仪器测试 =="
if ./gradlew testDebugUnitTest connectedCheck --no-daemon; then
  echo "测试全部通过"
else
  echo "::error::测试存在失败"
  FAIL=1
fi

echo "== P0 ⑧ 重启恢复（真实 adb reboot + BOOT_COMPLETED）=="
# 用 run-as（debug 包免 root）直接写入「计时中」状态，绕开 UI 操作。
# 引号教训（2026-11-05）：整条命令必须整体加双引号，让重定向由设备端
# run-as 内的 sh 在 App 数据目录里执行。早期写作
#   adb shell run-as "$PKG" sh -c 'cat > shared_prefs/...'
# 单引号被本地 shell 剥掉后，重定向落到设备端外层 shell 的 CWD=/，
# 报 "can't create shared_prefs/..."——P0 ⑧ 两轮 CI 都因此从未真正验证。
# 另先 force-stop：避免运行中的进程用内存态 prefs 覆盖刚注入的文件。
adb shell am force-stop "$PKG"
END=$(( $(date +%s) * 1000 + 1800000 ))
sed "s/__LOCK_END_AT__/$END/" app/src/androidTest/assets/prefs_timer_running.xml \
  | adb shell "run-as $PKG sh -c 'mkdir -p shared_prefs && cat > shared_prefs/quizgate_prefs.xml'"
# 启动一次让进程读取注入的状态（并清掉 stopped 标志，确保开机广播能送达）
adb shell am start -n "$PKG/.MainActivity"
sleep 5
adb reboot
adb wait-for-device
for i in $(seq 1 60); do
  if [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; then
    break
  fi
  sleep 2
done
# BootReceiver 收到 BOOT_COMPLETED 后应重新拉起 LockService
FOUND=0
for i in $(seq 1 30); do
  if adb shell dumpsys activity services "$PKG" | grep -q LockService; then
    FOUND=1
    break
  fi
  sleep 2
done
if [ "$FOUND" = "1" ]; then
  echo "重启后计时服务已恢复"
else
  echo "::error::重启后 LockService 未恢复"
  FAIL=1
fi

if [ "$FAIL" != "0" ]; then
  adb exec-out screencap -p > tv-failure.png || true
  exit 1
fi

echo "== 全部通过 =="
