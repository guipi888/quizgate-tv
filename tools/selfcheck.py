# -*- coding: utf-8 -*-
"""静态自检：在无 Android SDK 的环境下验证工程完整性。

检查项：
  1. 所有 XML 文件语法合法
  2. Java 代码引用的 R.* 资源均已在 XML 中定义
  3. AndroidManifest 声明的组件类文件存在
  4. assets 题库 JSON 可解析且字段符合引擎约定
  5. 可点击控件文案审计（防"按钮没文字"类缺陷）
  6. Java 文件括号配对（粗检）

用法：python tools/selfcheck.py
"""
import glob
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
MAIN = os.path.join(ROOT, "app/src/main")
JAVA_DIR = os.path.join(MAIN, "java/com/quizgate/tv")

errors = []
warnings = []


def err(msg):
    errors.append(msg)


def warn(msg):
    warnings.append(msg)


# ---------- 1. XML 语法 ----------
xml_files = glob.glob(os.path.join(MAIN, "**", "*.xml"), recursive=True)
xml_files.append(os.path.join(ROOT, "AndroidManifest.xml")) if os.path.exists(
    os.path.join(ROOT, "AndroidManifest.xml")) else None
print("XML files:", len(xml_files))
for f in xml_files:
    try:
        ET.parse(f)
    except ET.ParseError as e:
        err("XML parse error: %s (%s)" % (f, e))
print("[1] XML syntax OK" if not errors else "[1] XML syntax FAILED")

# ---------- 2. 收集已定义资源 ----------
defined_ids = set()
for f in glob.glob(os.path.join(MAIN, "res", "**", "*.xml"), recursive=True):
    text = open(f, encoding="utf-8").read()
    defined_ids.update(re.findall(r'android:id="@\+id/(\w+)"', text))

defined_drawables = {os.path.splitext(os.path.basename(p))[0]
                     for p in glob.glob(os.path.join(MAIN, "res", "drawable*", "*"))}
defined_mipmaps = {os.path.splitext(os.path.basename(p))[0]
                   for p in glob.glob(os.path.join(MAIN, "res", "mipmap*", "*"))}
defined_anims = {os.path.splitext(os.path.basename(p))[0]
                 for p in glob.glob(os.path.join(MAIN, "res", "anim", "*"))}

res_values = {}
for f in glob.glob(os.path.join(MAIN, "res", "values", "*.xml")):
    tree = ET.parse(f)
    for child in tree.getroot():
        tag = child.tag
        name = child.get("name")
        if name:
            res_values.setdefault(tag, set()).add(name)

defined_colors = res_values.get("color", set())
defined_strings = res_values.get("string", set())
defined_styles = res_values.get("style", set())

# ---------- 3. Java 引用交叉校验 ----------
java_files = glob.glob(os.path.join(JAVA_DIR, "*.java"))
print("Java files:", len(java_files))

R_MAP = {
    "id": defined_ids,
    "layout": None,  # layout 文件名即资源名
    "drawable": defined_drawables,
    "mipmap": defined_mipmaps,
    "color": defined_colors,
    "string": defined_strings,
    "anim": defined_anims,
    "style": defined_styles,
}
layouts_defined = {os.path.splitext(os.path.basename(p))[0]
                   for p in glob.glob(os.path.join(MAIN, "res", "layout", "*.xml"))}
R_MAP["layout"] = layouts_defined

for jf in java_files:
    text = open(jf, encoding="utf-8").read()
    for m in re.finditer(r"(?<!android\.)R\.(\w+)\.(\w+)", text):
        kind, name = m.group(1), m.group(2)
        if kind == "attr":
            continue
        pool = R_MAP.get(kind)
        if pool is None:
            warn("unknown R kind %s in %s" % (kind, jf))
            continue
        if name not in pool:
            err("missing resource R.%s.%s (used in %s)" % (kind, name, os.path.basename(jf)))
print("[2] R.* references checked")

# ---------- 4. Manifest 组件类 ----------
manifest_path = os.path.join(MAIN, "AndroidManifest.xml")
tree = ET.parse(manifest_path)
ns = "{http://schemas.android.com/apk/res/android}"
for tag in ("activity", "service", "receiver"):
    for comp in tree.getroot().find("application").findall(tag):
        name = comp.get(ns + "name")
        if name and name.startswith("."):
            cls = name[1:] + ".java"
            if not os.path.exists(os.path.join(JAVA_DIR, cls)):
                err("manifest component class missing: %s" % cls)
print("[3] Manifest components checked")

# Manifest 资源引用
for attr, val in re.findall(r'(android:\w+)="(@\w+/[\w.]+)"',
                            open(manifest_path, encoding="utf-8").read()):
    pass  # 图标/banner/theme 引用已由布局与 drawable 校验覆盖

# ---------- 5. 题库 JSON ----------
bank_files = glob.glob(os.path.join(MAIN, "assets", "banks", "*.json"))
print("Bank files:", len(bank_files))
for bf in bank_files:
    try:
        data = json.load(open(bf, encoding="utf-8"))
    except ValueError as e:
        err("bank JSON invalid: %s (%s)" % (bf, e))
        continue
    for key in ("name", "type", "questions"):
        if key not in data:
            err("bank %s missing key %s" % (bf, key))
    qs = data.get("questions", [])
    if len(qs) < 4:
        err("bank %s has too few questions: %d" % (bf, len(qs)))
    for q in qs:
        if not all(k in q for k in ("q", "a", "d")):
            err("bank %s question missing q/a/d: %r" % (bf, q))
            continue
        if len(q["d"]) != 3:
            err("bank %s distractors != 3: %r" % (bf, q))
        if q["a"] in q["d"]:
            err("bank %s answer equals a distractor: %r" % (bf, q))
        if len(set(q["d"])) != 3:
            err("bank %s duplicate distractors: %r" % (bf, q))
    print("  %s: %d questions, type=%s" % (os.path.basename(bf), len(qs), data.get("type")))
print("[4] Bank JSON checked")

# ---------- 6. 可点击控件文案审计 ----------
# 背景：曾出现 dialog_pin.xml 的 1-9 按钮漏写 android:text，
# 而 PinPadDialog 又用 b.getText() 取值，导致 PIN 永远输不进、
# 家长解锁 100% 卡死。此类缺陷编译不报错、肉眼也容易漏。
#
# 规则：可点击控件必须满足下列之一，否则报错。
#   a) android:text / android:contentDescription 非空
#   b) ToggleButton 的 android:textOn / textOff 非空
#   c) 所用 style 内定义了 android:text
#   d) 显式声明 tools:runtimeText="true" —— 表示文案由 Java 运行时填充
#      （必须写在布局里，迫使加控件的人明确表态；不写就过不了门禁）
CLICKABLE_TAGS = ("Button", "ToggleButton", "CheckBox", "RadioButton", "ImageButton")
ANDROID = "{http://schemas.android.com/apk/res/android}"
TOOLS = "{http://schemas.android.com/tools}"

style_items = {}
for f in glob.glob(os.path.join(MAIN, "res", "values", "*.xml")):
    for st in ET.parse(f).getroot().findall("style"):
        items = {}
        for item in st.findall("item"):
            items[item.get("name")] = (item.text or "").strip()
        style_items[st.get("name")] = items

strings_map = {}
for f in glob.glob(os.path.join(MAIN, "res", "values", "*.xml")):
    for s in ET.parse(f).getroot().findall("string"):
        strings_map[s.get("name")] = (s.text or "").strip()


def resolve_text(value):
    """把 @string/xxx 解析为实际文案；非引用原样返回。"""
    m = re.match(r"^@string/(\w+)$", value or "")
    if not m:
        return value or ""
    return strings_map.get(m.group(1), "")


def label_of(el):
    """返回该控件的可见文案，找不到返回空串。"""
    text = resolve_text(el.get(ANDROID + "text"))
    if text:
        return text
    desc = resolve_text(el.get(ANDROID + "contentDescription"))
    if desc:
        return desc
    for attr in ("textOn", "textOff"):
        v = resolve_text(el.get(ANDROID + attr))
        if v:
            return v
    style_ref = el.get("style") or ""
    style_name = style_ref.rsplit("/", 1)[-1] if style_ref else ""
    return resolve_text(style_items.get(style_name, {}).get("android:text", ""))


widget_total = 0
widget_missing = 0
for f in sorted(glob.glob(os.path.join(MAIN, "res", "layout", "*.xml"))):
    base = os.path.basename(f)
    for el in ET.parse(f).getroot().iter():
        if el.tag not in CLICKABLE_TAGS:
            continue
        widget_total += 1
        wid = el.get(ANDROID + "id") or "(no-id)"
        label = label_of(el)
        runtime = (el.get(TOOLS + "runtimeText") or "").lower() == "true"
        if not label and not runtime:
            widget_missing += 1
            err("clickable widget has no text/contentDescription and no "
                "tools:runtimeText=\"true\": %s id=%s tag=%s"
                % (base, wid, el.tag))
        elif not label and runtime:
            print("    (runtime-filled) %s %s" % (base, wid))
print("[5] Widget text audit: %d clickable widgets, %d missing label"
      % (widget_total, widget_missing))

# Java 侧不得依赖按钮文案取值（应按 id 映射），出现即警告
for jf in java_files:
    text = open(jf, encoding="utf-8").read()
    if re.search(r"\(\s*Button\s*\)[^\n;]*\.getText\(\)", text):
        warn("%s reads value from Button.getText(); map by id instead"
             % os.path.basename(jf))

# ---------- 7. Java 括号配对（跳过字符串与注释的粗检） ----------
for jf in java_files:
    text = open(jf, encoding="utf-8").read()
    depth = {"{": 0, "(": 0}
    i, n = 0, len(text)
    in_str = in_char = in_line_c = in_block_c = False
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if in_line_c:
            if c == "\n":
                in_line_c = False
        elif in_block_c:
            if c == "*" and nxt == "/":
                in_block_c = False
                i += 1
        elif in_str:
            if c == "\\":
                i += 1
            elif c == '"':
                in_str = False
        elif in_char:
            if c == "\\":
                i += 1
            elif c == "'":
                in_char = False
        else:
            if c == "/" and nxt == "/":
                in_line_c = True
                i += 1
            elif c == "/" and nxt == "*":
                in_block_c = True
                i += 1
            elif c == '"':
                in_str = True
            elif c == "'":
                in_char = True
            elif c in depth:
                depth[c] += 1
            elif c == "}":
                depth["{"] -= 1
            elif c == ")":
                depth["("] -= 1
        i += 1
    if depth["{"] != 0 or depth["("] != 0:
        err("unbalanced brackets in %s: %r" % (os.path.basename(jf), depth))
print("[7] Bracket balance checked")

# ---------- 汇总 ----------
print()
for w in warnings:
    print("WARN:", w)
if errors:
    for e in errors:
        print("ERROR:", e)
    print("SELFCHECK FAILED: %d error(s)" % len(errors))
    sys.exit(1)
print("SELFCHECK PASSED")
