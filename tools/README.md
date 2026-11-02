# tools 目录定位

工程辅助脚本，不参与 APK 运行时，仅开发维护用。

| 文件 | 作用 |
|---|---|
| `gen_assets.py` | 生成 TV 图标、桌面横幅、通知栏小图标（PNG 位图，Leanback 桌面要求） |
| `gen_banks.py` | 生成内置题库 JSON（183 个汉字拼音 + 24 个英语单词），含重复字/同音字/干扰项合法性校验 |
| `selfcheck.py` | 无 Android SDK 环境下的静态自检：XML 语法、R.\* 资源交叉引用、Manifest 组件、题库 JSON、Java 括号配对 |

修改题库或图标后重跑对应脚本；提交代码前跑 `python tools/selfcheck.py`。
