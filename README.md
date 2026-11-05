# 1. 答题门禁 QuizGate TV

一款开源 Android TV 应用：电视看到设定时长后自动全屏锁屏，孩子必须**连续答对 N 道题**才能继续观看。答对累计、答错重新计数，把「看电视」变成一次动脑练习。

- 平台：Android TV / 电视盒子（小米、海信、TCL、当贝等，Android 5.0+）
- 交互：纯遥控器 D-pad 操作，无需触屏
- 依赖：零第三方库，`org.json` 用系统内置，最大兼容老旧电视 ROM
- 构建：GitHub Actions 云构建，本地无需 Android 开发环境
- 测试：GitHub Actions 跑**真实 Android TV 系统镜像**（API 28/30/34）+ Espresso 仪器测试，无需真机

[![Build APK](https://github.com/guipi888/quizgate-tv/actions/workflows/build.yml/badge.svg)](https://github.com/guipi888/quizgate-tv/actions/workflows/build.yml)
[![TV Test](https://github.com/guipi888/quizgate-tv/actions/workflows/tv-test.yml/badge.svg)](https://github.com/guipi888/quizgate-tv/actions/workflows/tv-test.yml)

## 1.1 功能特性

| 功能 | 说明 |
|---|---|
| 定时锁屏 | 15/30/45/60/90/120 分钟六档，到时自动全屏遮挡 |
| 答题解锁 | 连续答对 3/5/10 题解锁；答对 +1，答错计数归零 |
| 题型引擎 | 汉字拼音（题库）、英语单词（题库）、数学口算（程序生成，零维护） |
| **题库自定义导入** | 家长在电脑上编辑 JSON 放 U 盘，电视端一键扫描加载 |
| 防绕过 | 锁屏状态持久化，断电重启照锁；拦截返回键；HOME 逃逸 300ms 自愈拉回 |
| 开机自启 | 一次配置长期生效，重启后计时/锁屏状态恢复 |
| 家长 PIN | 4 位 PIN 随时解锁（默认 1234，首次使用务必修改） |

## 1.2 与 KidsLock 的关系

本项目架构参考了开源项目 [KidsLock](https://github.com/wplleo/kidslock)（小米电视儿童锁屏，汉字认字解锁）已验证的产品思路：定时锁屏 + 答题解锁 + 防绕过分层。

**本仓库为独立实现**：所有代码（计时服务、锁屏内核、题型引擎、题库系统）均为重新编写，未使用其任何源码——KidsLock 仓库未附 LICENSE 文件，直接 fork 衍生存在法律风险，故采用「思路参考、代码独立」路线。

核心差异：KidsLock 题库硬编码在源码中（改题需重新编译），本项目把题库做成 JSON 文件 + U 盘导入，家长可随时换题库（背单词、古诗词、口算），题型可扩展。

## 1.3 安装

### 1.3.1 U 盘安装（推荐）

1. 下载 `quizgate-tv-debug.apk`（Actions 构建产物或 Release 页）
2. 复制到 U 盘，插入电视 USB 口
3. 电视端打开「高清播放器」或「文件管理」，找到 APK 点击安装
4. 安装前需在「设置 → 账号与安全」中开启「允许安装未知来源应用」

### 1.3.2 adb 安装

```bash
adb connect <电视IP>:5555
adb install quizgate-tv-debug.apk
```

## 1.4 使用流程

1. **首次打开**：电视桌面找到「答题门禁」图标
2. **配置参数**：观看时长、解锁题数、题型开关、题库扫描
3. **修改 PIN**：默认 1234，设置页 → 修改 PIN（验旧 → 输新 → 确认）
4. **开启悬浮窗权限**：设置页 → 开启悬浮窗权限（锁屏防绕过必需）
5. **开始计时**：大按钮「开始计时」，到点自动锁屏答题
6. **（可选）设为默认桌面**：进一步增强防 HOME 绕过

## 1.5 题库格式（家长自定义）

题库就是一个 JSON 文本文件，用电脑上的任何文本编辑器即可修改。

```json
{
  "name": "题库名称",
  "type": "pinyin_choice",
  "questions": [
    { "q": "貌", "a": "mào", "d": ["miáo", "māo", "móu"] },
    { "q": "苹果", "a": "apple", "d": ["banana", "orange", "grape"] }
  ]
}
```

字段说明：

| 字段 | 说明 |
|---|---|
| `name` | 题库显示名 |
| `type` | 题型 id：`pinyin_choice`（汉字拼音）/ `word_choice`（中英互选） |
| `q` | 题干 |
| `a` | 正确答案 |
| `d` | 3 个干扰项 |

选项顺序由程序自动打乱，JSON 里无需关心顺序。

### 1.5.1 U 盘导入步骤

1. 电脑上建目录 `QuizGate/banks/`
2. 把题库 JSON 文件放入（如 `pinyin_grade5.json`、`my_words.json`）
3. U 盘插入电视
4. 打开答题门禁 → 题库 → 「重新扫描题库」
5. 设置页会显示「已加载 N 个题库 / M 道题」

内置题库位于 `app/src/main/assets/banks/`：`pinyin_grade5.json`（183 个小学高段汉字）、`word_sample.json`（24 个小学英语单词示例）。数学口算为程序生成，无需题库。

### 1.5.2 扩展新题型

实现 `QuestionType` 接口（`parse` / `generate`），在 `QuestionTypeRegistry` 注册一行即可，详见源码注释。

## 1.6 防绕过机制与已知限制

已实现的防绕过（按 KidsLock 验证过的分层思路）：

1. 锁屏状态与截止时间写入 SharedPreferences，**断电重启后锁屏自动恢复**
2. 锁屏页全屏 + 拦截返回键
3. 锁屏期间被切走（HOME/多任务），300ms 后自愈拉回
4. 开机自启（BootReceiver），重启后计时恢复

**已知限制**（诚实声明）：

- Android 系统不允许第三方应用真正拦截 HOME 键，最强方案是「设为默认桌面」（设置页有引导）
- 修改系统时间可能影响计时精度（计时用墙钟时间戳，改时间会提前或延后触发）
- 防卸载需 Device Owner 权限（adb 一次性配置），未内置，见 Roadmap
- **U 盘题库导入仅支持到 Android 9（API 28）**：2026-11-05 TV CI 在 API 30/34 真实镜像上实测确认，targetSdk 33 的应用在 API 29+ 设备上一律受分区存储约束（`requestLegacyExternalStorage` 只对 targetSdk 29 有效），无法用 File 路径直接读 U 盘 `QuizGate/banks/` 目录；此前「Android 13+ 才受限」的记录有误。Android 10+ 电视需要改用 SAF 目录选择或申请「所有文件访问权限」，见 Roadmap。CI 中该场景按「可读则正向断言 / 不可读则优雅降级」分配断，重复扫描回归防线由 JVM 单元测试守护
- 本产品定位是「儿童抵抗」而非「安全防护」：能防住 10 岁以下孩子的大部分操作，防不住有心人

## 1.7 构建

### 1.7.1 GitHub Actions（推荐，零本地环境）

代码推送到 GitHub 后自动构建，debug APK 在 Actions → Artifacts 下载；打 `v*` tag 自动发 Release。

### 1.7.2 自动化测试（真实 Android TV 镜像，无需真机）

`tv-test.yml` 工作流在 GitHub Actions 上启动**真实 Android TV 系统镜像**（matrix：API 28 × API 30 × API 34），跑三层验证：

| 层 | 内容 |
|---|---|
| 单元测试（JVM） | 题型引擎、题库解析、连对/解锁规则（`app/src/test/`） |
| 仪器测试（Espresso） | 设置页、锁屏页、PIN 键盘、自定义时长、倒计时到期、恢复广播（`app/src/androidTest/`） |
| 端到端（adb） | TV 桌面可见性、U 盘题库导入（API 28 走可读正向断言，API 29+ 走分区存储降级断言）、真实 `adb reboot` 后的开机恢复 |

API 28 是产品声称支持 U 盘导入的最低系统，CI 必须覆盖它——否则「支持到 Android 9」只是无测试背书的声明。对本地无电视硬件的开发者，这是验证 P0 行为（前台服务、通知、悬浮窗拉起、重启恢复）最接近真机的免费方案。仪器测试通过与 App 同进程的反射读取答题引擎，确定性点击「正确选项」，不依赖 OCR 或穷举。

### 1.7.3 本地构建

```bash
# 需要 JDK 17 + Android SDK
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

Release 签名：在 `gradle.properties` 或环境变量提供 `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`，构建时自动启用签名配置。**密钥严禁提交到仓库**。

## 1.8 合规声明

本项目是**家长监护工具**，仅供家长用于管理自己未成年子女的电视观看时长。请勿用于未经同意的监控场景。使用本软件产生的任何后果由使用者自行承担。

## 1.9 Roadmap

- [ ] 古诗文默写题型（复用 JSON 引擎）
- [ ] 答题记录与正确率统计
- [ ] Device Owner 防卸载引导（adb 一次性配置）
- [ ] 局域网手机端管理页（推题 / 看记录 / 临时放行）
- [ ] 更多电视 ROM 适配实测（海信 / TCL / 当贝）
- [ ] Android 10+（API 29+）U 盘题库导入（SAF 目录选择或「所有文件访问」授权）

## 📝 版本迭代记录

| 版本 | 日期 | 更新内容摘要 | 操作人 |
|------|------|------------|--------|
| v1.0 | 2026-11-02 | 创建项目：锁屏内核 + 三题型引擎 + U 盘题库导入 + 云构建流水线 | 桂皮 |
| v1.1 | 2026-11-05 | TV 仿真测试流水线（API 30/34 × android-tv × Espresso）；修复 PIN 按钮不显示、自定义时长对话框、确认新 PIN 标题错显、题库重复扫描；新增 Android 13+ 前台服务/通知权限适配 | 桂皮 |
| v1.2 | 2026-11-05 | 修复 CI 首跑暴露的 4 类问题：锁屏测试未进锁屏态即启动（Activity 自毁 NPE）、Espresso scrollTo 在 TV 模拟器不可用（初版改真实滑动手势）、UI 读取线程竞态、U 盘导入存储边界纠正（实为 Android 10+/API 29+ 分区存储，非 13+）；新增 canonical 去重 JVM 回归测试 | 桂皮 |
| v1.3 | 2026-11-05 | 修复 CI 二跑暴露的 3 类问题：① TV 镜像为 no-touch 设备（`hw.screen=no-touch`），注入的 MOVE 事件滚不动 ScrollView，clickAfterScroll 改**焦点滚动**（`requestChildFocus` → `ScrollView.scrollToChild`，与真实用户 D-pad 导航同路径），滑动仅作兜底；② 锁屏页布局在 1080p（960×540dp）上溢出约 13dp，「家长解锁」按钮被裁——真实用户同样点不到，属产品缺陷，已包 ScrollView + 收紧题干/选项尺寸；③ `tv-e2e.sh` 的 run-as 单引号被本地 shell 剥掉，重定向落到设备端 CWD=/，**P0 ⑧ 重启恢复两轮 CI 都从未真正验证过**，已修引号并先 force-stop 再注入 prefs；另新增 API 28 job 覆盖 U 盘导入正向分支，`judge()` 越界「忽略」语义补文档（锁屏路径抛异常＝门禁被绕过，比忽略危险） | 桂皮 |
| v1.4 | 2026-11-05 | 修复 CI 三跑暴露的根因：`requestChildFocus` 自 Android 9 起被 `getRevealOnFocusHint()` 把门，布局不设 `android:revealOnFocus` 时**什么都不滚**（AOSP 源码 + CI 实测：调用后按钮 y 坐标仍 1709，scrollY 未变）——v1.3 的锁屏页「修复」实为尺寸收紧后按钮落回一屏内，滚动机制从未生效。clickAfterScroll 改 `requestRectangleOnScreen(rect, true)`（immediate=true 时 ScrollView 走 `scrollToChildRect` → 同步 `scrollBy`，与真实 D-pad 焦点滚动同路径；immediate=false 的 smoothScrollBy 动画是同帧复检失败的败因），保留 ScrollView 手算 `scrollBy` 兜底，删除 no-touch 设备上已知无效的 swipe 兜底。P0 ⑧ 重启恢复经 run-as 修复后**首次真正验证通过**（BootRestoreTest 2/2） | 桂皮 |
| v1.5 | 2026-11-05 | 修复 CI 四跑/五跑暴露的两个叠加 bug：① `ViewGroup.requestChildRectangleOnScreen` 默认实现直接 `return false`、**不向父链冒泡**（AOSP 源码实证），view 的直接父是普通 LinearLayout 时请求断在中间层——`requestRectangleOnScreen(true)` 是死路；② `isDisplayed()` 的 `catch (Exception)` 捕不住 Espresso 的 `AssertionFailedWithCauseError`（继承 `AssertionFailedError` → **`java.lang.Error`**），复检第一次调用就炸穿整个 clickAfterScroll，滚动分支后的一切从未执行。scrollIntoView 改为**直接定位最近 ScrollView 祖先、按 `getLocationOnScreen` 实测坐标手算 `scrollTo`**（maxScroll 用 ScrollView.onLayout 同款公式 clamp），滚动失败时抛带全部中间值的 RuntimeException 便于定位。五跑 API 28/30 **15 个仪器测试全绿**。tv-e2e.sh 的 P0 ⑧ 段重构为「诊断 + 三级递进注入」（run-as → adb root 直写 → 报错），修掉 set -e 下 run-as 一失败整个 job 被杀的链路；run-as 在 TV 模拟器上报 `unknown package` 的现象留待六跑诊断数据定位 | 桂皮 |
| v1.6 | 2026-11-05 | 六跑诊断闭环，确诊 P0 ⑧ 五轮 CI 从未真正验证的终极根因：**AGP 8.1+ 的 connectedCheck 在 `SimpleTestCallable` 的 finally 块里连 app + test app 一起卸载**（Google issue tracker 295039976，AGP 8.5.2 实证）——测试全绿后 app 真的不在设备上，前三轮报的 `run-as: unknown package` 不是 run-as 坏，是包被卸了；二跑的 `can't create shared_prefs` 则是单引号 bug（重定向落到设备端 CWD=/，run-as 从未执行）。修复双保险：`gradle.properties` 设 `android.injected.androidTest.leaveApksInstalledAfterRun=true`（AGP 8.3+ 支持）+ tv-e2e.sh 注入前 `pm path` 确认、缺失则 `installDebug` 兜底重装 | 桂皮 |
| v1.7 | 2026-11-05 | 七跑 API 28/30 全绿（含 P0 ⑧ 真实 reboot 端到端），API 34 仍挂：八跑确诊该镜像（`system-images;android-34;android-tv;x86`，32 位）的 ddmlib 持久 shell 不可用——boot completed 后 adb 仍持续 `device offline` 抖动近 20 秒，`installDebug` 的 pm install 连续 6 轮无响应（二跑至八跑，每次 4-5 分钟超时，adb server 重启×3 重试无效，重试机制本身按设计生效）。tv-e2e.sh 改为**按 SDK 分流**：API 34 走 CLI 通道（`adb install` + `am instrument` 短连接，绕开 ddmlib；失败判据经查证为输出中的 `FAILURES!!!`/`Process crashed`/缺 `OK (n tests)`，不用退出码——adb shell 关闭时会返回伪 255），API 28/30 保持已验证全绿的 ddmlib 通道不动；另加 adb 稳定性闸门（连续探测 + `adb reconnect offline`）。API 28/30 七跑起保持全绿未回归 | 桂皮 |
| v1.8 | 2026-11-05 | 九跑暴露无界等待挂死：API 34 走 CLI 通道越过安装关后，在测试/重启段挂死 2 小时以上（人工取消，未耗到 GitHub 6 小时 job 上限）——`am instrument -w` 与 P0 ⑧ 的 `adb wait-for-device` 都是无界等待，adb 通道抖动时永久挂起。修复：① 所有 adb 调用包 `timeout`（默认 60s，长任务用 `ADB_TIMEOUT=xxx` 覆盖：install 180s / am instrument 1200s；`timeout` 不能跑 shell 内建，用 `command -v adb` 解析真实路径——本地 mock 冒烟测试坐实，否则每次调用 127 秒挂会带崩 28/30）；② reboot 后弃用 `adb wait-for-device`，改有界轮询 `sys.boot_completed`（6 分钟上限），未启动则跳过 LockService 检查直接记失败；③ 全章节改带 UTC 时间戳的 `log()`，挂死位置一眼定位。九跑 API 28/30 双绿未回归（脚本改动前最后一次全绿复现） | 桂皮 |
| v1.9 | 2026-11-05 | 十跑 API 34 首次完整跑进测试段（15 测试执行完 1 失败 + P0 ⑧ reboot 端到端首次通过）；十一跑败因转移到安装段：`adb install` 三次均整 3 分钟被本地 timeout 杀掉且无 `INSTALL_FAILED_*` 输出——本地客户端被杀后设备端 `pm install` 仍在跑，重试大概率等同一个包的会话（会话锁），三次全挂。修复：CLI 通道安装改 `adb push`（纯传输，超时 300s）+ `adb shell pm install`（纯安装，超时 600s）分离；两个 APK 各自独立三次重试；每次重试前 `adb reconnect` + 双包 `pm uninstall` 清会话 + sleep 15 等 installd 收尾；成功判据为输出含 `Success`。本地 mock adb 三场景（一次成功 / install 全败 / push 全败）冒烟通过。测试侧：`startTimerStartsForegroundService` 通知等待 8s→20s（对齐已通过的 BootRestoreTest 窗口），`waitUntil` 超时失败转储诊断（SDK/计时状态/LockService 运行态/通知开关/活跃通知清单）——十跑起 API 28/30 保持双绿 | 桂皮 |
| v1.10 | 2026-11-05 | 十二跑暴露两个独立根因：① **API 30 测试 15/15 全过但 job 红**——`adb reboot` 后设备从 offline 变 `device not found` 干等 6 分钟（emulator 重启后 adb server transport 坏死，七跑能过是那次没坏死，环境抖动差异）；② **API 34 push 0.05s 秒过但 `pm install` 每次整 600s 被本地杀**——传输没问题、设备端 PackageManager 死等（32 位 TV 镜像 + Android 14 odsign dexopt 在软件模拟器上时快时死，十跑同形态 1-2 分钟成功过，非「慢」是「死」）。修复：① reboot 轮询分级刷新——每 30s `adb reconnect`（轻）、每 2 分钟 `adb kill-server && start-server`（重，八跑证明有效），不再干等；② 安装双形态——每次尝试先 kill-server 刷新 transport，再依次试 `adb install`（十跑成功形态，900s）→ `push + pm install`（900s），双挂才重试，sleep 30 给 installd 收尾；单次尝试最坏 40 分钟、三次 2 小时，仍在 GitHub 6h job 上限内。本地 mock adb 五场景冒烟（含真 timeout 分支：1s 杀挂起的 install → pm install 兜住）全过 | 桂皮 |
