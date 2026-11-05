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
# 通知：前台服务常驻倒计时通知（API 33+ 运行时权限；低版本系统不认识该权限，
# pm grant 会在设备端抛 IllegalArgumentException，2>/dev/null 只压噪音不掩失败）
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
# 存储：U 盘题库导入（仅 API<=32 有效，33+ 见 README 已知限制）
adb shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true

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
