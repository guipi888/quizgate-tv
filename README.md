# 1. 答题门禁 QuizGate TV

一款开源 Android TV 应用：电视看到设定时长后自动全屏锁屏，孩子必须**连续答对 N 道题**才能继续观看。答对累计、答错重新计数，把「看电视」变成一次动脑练习。

- 平台：Android TV / 电视盒子（小米、海信、TCL、当贝等，Android 5.0+）
- 交互：纯遥控器 D-pad 操作，无需触屏
- 依赖：零第三方库，`org.json` 用系统内置，最大兼容老旧电视 ROM
- 构建：GitHub Actions 云构建，本地无需 Android 开发环境
- 测试：GitHub Actions 跑**真实 Android TV 系统镜像**（API 30/34）+ Espresso 仪器测试，无需真机

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
- **U 盘题库导入仅支持到 Android 12（API 32）**：`READ_EXTERNAL_STORAGE` 声明了 `maxSdkVersion="32"`，Android 13+ 不再申请存储权限，且系统已禁止应用直接读共享存储里的非媒体文件。Android 13+ 电视需要改用 SAF 目录选择或申请「所有文件访问权限」，见 Roadmap
- 本产品定位是「儿童抵抗」而非「安全防护」：能防住 10 岁以下孩子的大部分操作，防不住有心人

## 1.7 构建

### 1.7.1 GitHub Actions（推荐，零本地环境）

代码推送到 GitHub 后自动构建，debug APK 在 Actions → Artifacts 下载；打 `v*` tag 自动发 Release。

### 1.7.2 自动化测试（真实 Android TV 镜像，无需真机）

`tv-test.yml` 工作流在 GitHub Actions 上启动**真实 Android TV 系统镜像**（matrix：API 30 × API 34），跑三层验证：

| 层 | 内容 |
|---|---|
| 单元测试（JVM） | 题型引擎、题库解析、连对/解锁规则（`app/src/test/`） |
| 仪器测试（Espresso） | 设置页、锁屏页、PIN 键盘、自定义时长、倒计时到期、恢复广播（`app/src/androidTest/`） |
| 端到端（adb） | TV 桌面可见性、U 盘题库导入、真实 `adb reboot` 后的开机恢复 |

对本地无电视硬件的开发者，这是验证 P0 行为（前台服务、通知、悬浮窗拉起、重启恢复）最接近真机的免费方案。仪器测试通过与 App 同进程的反射读取答题引擎，确定性点击「正确选项」，不依赖 OCR 或穷举。

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
- [ ] Android 13+ U 盘题库导入（SAF 目录选择或「所有文件访问」授权）

## 📝 版本迭代记录

| 版本 | 日期 | 更新内容摘要 | 操作人 |
|------|------|------------|--------|
| v1.0 | 2026-11-02 | 创建项目：锁屏内核 + 三题型引擎 + U 盘题库导入 + 云构建流水线 | 桂皮 |
| v1.1 | 2026-11-05 | TV 仿真测试流水线（API 30/34 × android-tv × Espresso）；修复 PIN 按钮不显示、自定义时长对话框、确认新 PIN 标题错显、题库重复扫描；新增 Android 13+ 前台服务/通知权限适配 | 桂皮 |
