# tools 目录定位

工程辅助脚本，不参与 APK 运行时，仅开发维护用。

| 文件 | 作用 |
|---|---|
| `gen_assets.py` | 生成 TV 图标、桌面横幅、通知栏小图标（PNG 位图，Leanback 桌面要求） |
| `gen_banks.py` | 生成内置题库 JSON（183 个汉字拼音 + 24 个英语单词），含重复字/同音字/干扰项合法性校验 |
| `selfcheck.py` | 无 Android SDK 环境下的静态自检：XML 语法、R.\* 资源交叉引用、Manifest 组件、题库 JSON、**可点击控件文案审计**、Java 括号配对 |

## 可点击控件文案审计（检查项 5）

曾出现 `dialog_pin.xml` 的 1–9 按钮漏写 `android:text`，而 `PinPadDialog` 又用
`b.getText()` 取值，导致 PIN 永远输不进、家长解锁 100% 卡死。这类缺陷编译不报错、
肉眼也容易漏，因此设为硬门禁。

一个可点击控件必须满足下列**之一**，否则 `selfcheck.py` 报错退出：

| 条件 | 说明 |
|---|---|
| `android:text` 非空 | 常规按钮 |
| `android:contentDescription` 非空 | ImageButton 等图标按钮 |
| `android:textOn` / `textOff` 非空 | ToggleButton |
| 所用 `style` 内定义 `android:text` | 文案由样式统一提供 |
| `tools:runtimeText="true"` | **文案由 Java 运行时填充**，需在布局里显式声明 |

最后一条是故意设计的：新增"运行时填值"的控件时必须显式表态，不写就过不了门禁，
从而逼使加控件的人确认「这个控件到底有没有文案」。`tools:` 命名空间的属性在编译期
会被 AAPT 剥离，不影响 APK。

修改题库或图标后重跑对应脚本；提交代码前跑 `python tools/selfcheck.py`。
