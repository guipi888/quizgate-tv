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
TEST_PKG="$PKG.test"

# 所有 adb 调用默认 60s 超时；长任务用 ADB_TIMEOUT=xxx 覆盖。
# 背景：九跑 API 34 在测试/重启段挂死 2 小时以上才被人工取消——
# am instrument -w 或 P0 ⑧ 的 adb wait-for-device 是无界等待，
# adb 通道抖动时永久挂起，只能等 GitHub 6 小时 job 上限才失败。
# 原则：宁可快速失败留证（am-instrument.log + 截图），不无限挂起。
# 注意：timeout 是外部命令，不能跑 shell 内建 command，先用 command -v
# 解析出 adb 真实路径（本地冒烟测试坐实过这一点：直接写 command adb
# 会让每次调用都以 127 失败）。
adb() {
  timeout "${ADB_TIMEOUT:-60}" "$(command -v adb)" "$@"
}
log() {
  echo "[$(date -u +%H:%M:%SZ)] $*"
}

log "== 等待 adb 连接稳定 =="
# API 34 TV 镜像实测（2026-11-05 八跑日志）：emulator 报 boot completed
# 之后，adb 仍持续 "device offline" 抖动近 20 秒（settings put 连续
# 失败 3 次才恢复）。不稳定的 adb 通道会让 ddmlib 的持久 shell 安装
# 无响应（ShellCommandUnresponsiveException）。先过稳定性闸门再干活。
STABLE=0
for round in $(seq 1 15); do
  OK=1
  for probe in 1 2 3; do
    if ! ADB_TIMEOUT=10 adb shell echo ok >/dev/null 2>&1; then OK=0; break; fi
    sleep 1
  done
  if [ "$OK" = "1" ]; then STABLE=1; break; fi
  log "::warning::adb 第 $round 轮探测仍不稳定，尝试 reconnect"
  adb reconnect offline 2>/dev/null || adb reconnect 2>/dev/null || true
  sleep 3
done
if [ "$STABLE" != "1" ]; then
  log "::error::adb 连接 15 轮探测后仍不稳定，放弃"
  adb exec-out screencap -p > tv-failure.png || true
  exit 1
fi
log "adb 连接稳定"

# 按设备 SDK 选择安装/测试通道：
#   API 28/30 → gradle（ddmlib）通道：连续多轮全绿，保持不动；
#   API 34   → CLI 通道（adb install + am instrument）。
# 为什么 API 34 换通道：该镜像（system-images;android-34;android-tv;x86，
# 32 位）上 gradle installDebug 的 ddmlib pm install 连续 6 轮无响应
# （二跑至八跑，每次 4-5 分钟超时），adb server 重启×3 重试也无效——
# 不是偶发抖动，是 ddmlib 持久 shell 在该镜像上不可用。
# adb install / am instrument 走短连接 CLI 通道，绕开 ddmlib。
SDK=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
case "$SDK" in ''|*[!0-9]*) SDK=0 ;; esac
CLI_MODE=0
if [ "$SDK" -ge 34 ]; then CLI_MODE=1; fi
if [ "$CLI_MODE" = "1" ]; then
  echo "设备 API level: $SDK，安装/测试通道：CLI（adb install + am instrument）"
else
  echo "设备 API level: $SDK，安装/测试通道：gradle/ddmlib"
fi

# 安装单个 APK：push（纯传输）+ pm install（纯安装）分离，超时/重试各自独立。
# 为什么不用 adb install：十一跑 API 34 三次 install 均整 3 分钟被本地
# timeout 杀掉且无 INSTALL_FAILED_* 输出——本地客户端被杀后设备端
# pm install 仍在跑，后续重试大概率等同一个包的会话（会话锁），
# 三次全挂。分离后传输失败只重推、安装失败可清理会话后再试。
# pm install /data/local/tmp 为官方文档用法。
install_one_apk() {
  local apk="$1" name="$2"
  local remote="/data/local/tmp/$(basename "$apk")"
  for i in 1 2 3; do
    log "[$name] 安装尝试 $i/3：push"
    if ADB_TIMEOUT=300 adb push "$apk" "$remote" >/dev/null 2>&1; then
      log "[$name] push 完成，pm install（超时 600s）"
      local out
      out=$(ADB_TIMEOUT=600 adb shell pm install -r -t "$remote" 2>&1) || true
      echo "$out" | grep -a -v "^$" | head -5 || true
      if echo "$out" | grep -aq "Success"; then
        log "[$name] 安装成功"
        return 0
      fi
      log "::warning::[$name] pm install 未报 Success（尝试 $i）"
    else
      log "::warning::[$name] push 失败（尝试 $i）"
    fi
    # 清理：本地 timeout 杀掉 adb 后设备端 pm install 可能仍在跑，
    # 先 reconnect 断僵死通道，再卸载清会话，最后等 installd 收尾。
    adb reconnect >/dev/null 2>&1 || true
    ADB_TIMEOUT=120 adb shell pm uninstall "$PKG" >/dev/null 2>&1 || true
    ADB_TIMEOUT=120 adb shell pm uninstall "$TEST_PKG" >/dev/null 2>&1 || true
    sleep 15
  done
  log "::error::[$name] 三次安装均失败"
  return 1
}

log "== 安装 App 与仪器测试 APK =="
if [ "$CLI_MODE" = "1" ]; then
  ./gradlew assembleDebug assembleDebugAndroidTest --no-daemon
  install_one_apk app/build/outputs/apk/debug/app-debug.apk app || {
    adb exec-out screencap -p > tv-failure.png || true; exit 1; }
  install_one_apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk androidTest || {
    adb exec-out screencap -p > tv-failure.png || true; exit 1; }
else
  # API 34 之外的镜像 ddmlib 通道正常；保留 3 次重试作为偶发抖动对策
  INSTALL_OK=0
  for i in 1 2 3; do
    if ./gradlew installDebug installDebugAndroidTest --no-daemon; then
      INSTALL_OK=1
      break
    fi
    log "::warning::安装失败（第 $i 次），重启 adb server 后重试"
    adb kill-server || true
    sleep 5
    adb start-server || true
    adb wait-for-device || true
    sleep 10
  done
  if [ "$INSTALL_OK" != "1" ]; then
    log "::error::安装 App 与仪器测试 APK 三次均失败"
    adb exec-out screencap -p > tv-failure.png || true
    exit 1
  fi
fi

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


log "== 单元测试 + 仪器测试 =="
if [ "$CLI_MODE" = "1" ]; then
  # 单元测试：本地 JVM，照走 gradle
  if ! ./gradlew testDebugUnitTest --no-daemon; then
    echo "::error::单元测试失败"
    FAIL=1
  fi
  # 仪器测试：am instrument 直连（不经过 ddmlib）。
  # 输出格式已查证（AOSP 官方文档 + 第三方设备农场 CI 实践）：
  #   AndroidJUnitRunner 全绿 → 打印 OK（标准格式 "OK (n tests)"）；
  #   有失败 → 打印 FAILURES!!!；进程被杀 → "Process crashed"。
  # 不用退出码判定：adb shell 连接关闭时会返回伪 255（protocol fault），
  # 会把全绿误判成失败。
  # ADB_TIMEOUT=1200：正常约 3-5 分钟（swiftshader 软渲染）；九跑疑似
  # 在此无限挂起，必须有界——超时即失败并留 am-instrument.log 取证。
  log "仪器测试开始（am instrument，上限 20 分钟）"
  INSTR_LOG=$(mktemp)
  ADB_TIMEOUT=1200 adb shell am instrument -w "$TEST_PKG/androidx.test.runner.AndroidJUnitRunner" \
    > "$INSTR_LOG" 2>&1 || true
  log "仪器测试结束，判定结果"
  grep -a -E "OK \(|OK$|FAILURES|Tests run:|INSTRUMENTATION_RESULT" "$INSTR_LOG" || true
  if grep -aq "FAILURES" "$INSTR_LOG" \
    || grep -aq "Process crashed" "$INSTR_LOG"; then
    echo "::error::仪器测试存在失败（详见 tv-test-report-api34 中的 am-instrument.log）"
    FAIL=1
  elif ! grep -aqE "OK \([0-9]+ tests\)|OK[[:space:]]*$" "$INSTR_LOG"; then
    echo "::error::仪器测试输出无成功标记（既无 OK 也无 FAILURES，疑似中断/超时，取证见 am-instrument.log）"
    FAIL=1
  fi
  # 原始输出随 artifact 上传（CI 失败时取证）
  mkdir -p app/build/outputs/androidTest-results
  cp "$INSTR_LOG" app/build/outputs/androidTest-results/am-instrument.log
  rm -f "$INSTR_LOG"
else
  if ./gradlew testDebugUnitTest connectedCheck --no-daemon; then
    echo "测试全部通过"
  else
    echo "::error::测试存在失败"
    FAIL=1
  fi
fi

log "== P0 ⑧ 重启恢复（真实 adb reboot + BOOT_COMPLETED）=="
# 向 App 数据目录注入「计时中」状态，绕开 UI 操作。
#
# 历史教训（2026-11-05，P0 ⑧ 因此五轮 CI 从未真正验证，四个连续错误）：
# ① 单引号被本地 shell 剥掉：`adb shell run-as "$PKG" sh -c 'cat > f'` 的
#    重定向落到设备端外层 shell 的 CWD=/，报 can't create shared_prefs/...。
# ② 修好引号后仍报 `run-as: unknown package`——**真相：AGP 8.1+ 的
#    connectedCheck 跑完会连 app 一起卸载**（SimpleTestCallable 的
#    finally 块；AGP 8.5.2 实证，与测试成败无关）。不是 run-as 坏，
#    是包真的被卸了。已在 gradle.properties 设
#    android.injected.androidTest.leaveApksInstalledAfterRun=true。
# ③ 命令无 `|| true` 且脚本 set -e：失败即杀整个 job，reboot 从未执行。
#
# 现为「保安装 + 兜底重装 + 三级递进注入」：先确认 app 还在（不在则
# 重装），再依次尝试 run-as → adb root，每级有明确输出。
adb shell am force-stop "$PKG" || true
if ! adb shell pm path "$PKG" >/dev/null 2>&1; then
  echo "::warning::设备上没有 app（AGP 测试后卸载？），重装后继续"
  if [ "$CLI_MODE" = "1" ]; then
    install_one_apk app/build/outputs/apk/debug/app-debug.apk app || true
  else
    ./gradlew installDebug --no-daemon || true
  fi
fi
if ! adb shell pm path "$PKG" >/dev/null 2>&1; then
  echo "::error::app 不在设备上且重装失败，P0 ⑧ 无法继续"
  FAIL=1
else
  END=$(( $(date +%s) * 1000 + 1800000 ))
  PREFS_XML=$(mktemp)
  sed "s/__LOCK_END_AT__/$END/" app/src/androidTest/assets/prefs_timer_running.xml > "$PREFS_XML"
  INJECT_METHOD=""
  if adb shell "run-as $PKG sh -c 'mkdir -p shared_prefs'" 2>/dev/null; then
    # stdin 重定向本地文件（不用 /sdcard 中转：run-as 内进程是 app UID，
    # 无存储权限，读不了 /sdcard；adb shell 带命令参数时会转发 stdin）
    if adb shell "run-as $PKG sh -c 'cat > shared_prefs/quizgate_prefs.xml'" \
        < "$PREFS_XML" 2>/dev/null; then
      INJECT_METHOD="run-as"
    fi
  fi
  if [ -z "$INJECT_METHOD" ]; then
    # 兜底：模拟器镜像多为 userdebug，adb root 后直写 /data/data
    if adb root >/dev/null 2>&1 && adb wait-for-device 2>/dev/null; then
      if adb shell "mkdir -p /data/data/$PKG/shared_prefs" 2>/dev/null; then
        if adb shell "cat > /data/data/$PKG/shared_prefs/quizgate_prefs.xml" \
            < "$PREFS_XML" 2>/dev/null; then
          INJECT_METHOD="adb-root"
        fi
      fi
    fi
  fi
  rm -f "$PREFS_XML"
  if [ -z "$INJECT_METHOD" ]; then
    echo "::error::prefs 注入失败：run-as 与 adb root 均不可用"
    FAIL=1
  else
    echo "prefs 注入方式：$INJECT_METHOD"
  fi
fi
# 启动一次让进程读取注入的状态（并清掉 stopped 标志，确保开机广播能送达）
adb shell am start -n "$PKG/.MainActivity" || true
sleep 5
log "== 真实 adb reboot，等待重新启动（上限 6 分钟）=="
adb reboot
# 不用 adb wait-for-device：设备不回时它是无限等（九跑 2 小时挂死嫌疑之一）。
# 改用有界轮询 sys.boot_completed。
BOOTED=0
for i in $(seq 1 180); do
  if [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; then
    BOOTED=1
    break
  fi
  sleep 2
done
if [ "$BOOTED" != "1" ]; then
  echo "::error::reboot 后 6 分钟内未完成启动"
  FAIL=1
else
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
fi

if [ "$FAIL" != "0" ]; then
  adb exec-out screencap -p > tv-failure.png || true
  exit 1
fi

log "== 全部通过 =="
