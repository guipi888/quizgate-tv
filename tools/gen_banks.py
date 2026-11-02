# -*- coding: utf-8 -*-
"""生成内置题库文件（assets/banks/*.json）。

用法：python tools/gen_banks.py
产出：
  app/src/main/assets/banks/pinyin_grade5.json   汉字拼音题库
  app/src/main/assets/banks/word_sample.json     英语单词示例题库

校验规则：汉字不重复、拼音格式合法、干扰项不等于答案、
选项去重。随机种子固定，重复执行结果一致。
"""
import json
import os
import random
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
BANKS_DIR = os.path.join(ROOT, "app/src/main/assets/banks")

# (汉字, 拼音) 候选池，约 160 个小学高段常见字，脚本去重校验
CHARS = [
    ("貌", "mào"), ("慕", "mù"), ("暮", "mù"), ("幕", "mù"), ("墓", "mù"), ("摹", "mó"),
    ("咨", "zī"), ("询", "xún"), ("悼", "dào"), ("煞", "shà"), ("慨", "kǎi"), ("忱", "chén"),
    ("阐", "chǎn"), ("慑", "shè"), ("斡", "wò"), ("悖", "bèi"), ("谛", "dì"), ("滞", "zhì"),
    ("衷", "zhōng"), ("衰", "shuāi"), ("奈", "nài"), ("沮", "jǔ"), ("丧", "sàng"), ("虐", "nüè"),
    ("窟", "kū"), ("窿", "lóng"), ("灶", "zào"), ("铝", "lǚ"), ("辄", "zhé"), ("炽", "chì"),
    ("梗", "gěng"), ("矗", "chù"), ("愁", "chóu"), ("忧", "yōu"), ("悦", "yuè"), ("愧", "kuì"),
    ("憾", "hàn"), ("惧", "jù"), ("慎", "shèn"), ("谨", "jǐn"), ("恭", "gōng"), ("敬", "jìng"),
    ("谦", "qiān"), ("虚", "xū"), ("骄", "jiāo"), ("懒", "lǎn"), ("怠", "dài"), ("恳", "kěn"),
    ("慈", "cí"), ("霞", "xiá"), ("虹", "hóng"), ("瀑", "pù"), ("渊", "yuān"), ("涯", "yá"),
    ("峭", "qiào"), ("峨", "é"), ("岱", "dài"), ("峦", "luán"), ("嶂", "zhàng"), ("峡", "xiá"),
    ("湾", "wān"), ("渚", "zhǔ"), ("汀", "tīng"), ("汐", "xī"), ("潮", "cháo"), ("澜", "lán"),
    ("瀚", "hàn"), ("拨", "bō"), ("揽", "lǎn"), ("攥", "zuàn"), ("拽", "zhuài"), ("抛", "pāo"),
    ("掷", "zhì"), ("捧", "pěng"), ("撕", "sī"), ("捻", "niǎn"), ("搓", "cuō"), ("掂", "diān"),
    ("掖", "yè"), ("揣", "chuāi"), ("剜", "wān"), ("窖", "jiào"), ("窑", "yáo"), ("鼎", "dǐng"),
    ("盔", "kuī"), ("铠", "kǎi"), ("盾", "dùn"), ("戟", "jǐ"), ("弩", "nǔ"), ("矢", "shǐ"),
    ("簇", "cù"), ("舆", "yú"), ("辕", "yuán"), ("辙", "zhé"), ("轨", "guǐ"), ("陌", "mò"),
    ("阡", "qiān"), ("衢", "qú"), ("闾", "lǘ"), ("塾", "shú"), ("豹", "bào"), ("猿", "yuán"),
    ("鹤", "hè"), ("鹭", "lù"), ("莺", "yīng"), ("鸦", "yā"), ("鹊", "què"), ("鹉", "wǔ"),
    ("蝶", "dié"), ("蛾", "é"), ("蚁", "yǐ"), ("蚌", "bàng"), ("蟹", "xiè"), ("蛙", "wā"),
    ("鲸", "jīng"), ("鲨", "shā"), ("鳄", "è"), ("獐", "zhāng"), ("麋", "mí"), ("馐", "xiū"),
    ("肴", "yáo"), ("馔", "zhuàn"), ("馏", "liù"), ("馒", "mán"), ("饺", "jiǎo"), ("饵", "ěr"),
    ("粥", "zhōu"), ("羹", "gēng"), ("酱", "jiàng"), ("醋", "cù"), ("酿", "niàng"), ("酵", "jiào"),
    ("酪", "lào"), ("酥", "sū"), ("糕", "gāo"), ("糖", "táng"), ("蜜", "mì"), ("饯", "jiàn"),
    ("谅", "liàng"), ("诺", "nuò"), ("诫", "jiè"), ("训", "xùn"), ("谤", "bàng"), ("诬", "wū"),
    ("诈", "zhà"), ("诡", "guǐ"), ("狡", "jiǎo"), ("谚", "yàn"), ("谣", "yáo"), ("讴", "ōu"),
    ("颂", "sòng"), ("谏", "jiàn"), ("诤", "zhèng"), ("谕", "yù"), ("诏", "zhào"), ("敕", "chì"),
    ("辰", "chén"), ("宿", "xiù"), ("昃", "zè"), ("晷", "guǐ"), ("霄", "xiāo"), ("昊", "hào"),
    ("罡", "gāng"), ("霁", "jì"), ("霾", "mái"), ("霖", "lín"), ("雹", "báo"), ("霜", "shuāng"),
    ("露", "lù"), ("穹", "qióng"), ("冥", "míng"), ("晦", "huì"), ("昭", "zhāo"), ("昧", "mèi"),
    ("玄", "xuán"), ("枢", "shū"), ("纽", "niǔ"), ("秉", "bǐng"), ("耿", "gěng"), ("恪", "kè"),
    ("悯", "mǐn"), ("恻", "cè"), ("恤", "xù"),
]

WORDS = [
    ("苹果", "apple", ["banana", "orange", "grape"]),
    ("铅笔", "pencil", ["ruler", "eraser", "crayon"]),
    ("教室", "classroom", ["library", "playground", "office"]),
    ("医生", "doctor", ["nurse", "teacher", "farmer"]),
    ("冬天", "winter", ["spring", "summer", "autumn"]),
    ("自行车", "bicycle", ["motorcycle", "scooter", "subway"]),
    ("雨伞", "umbrella", ["raincoat", "gloves", "hat"]),
    ("图书馆", "library", ["museum", "cinema", "hospital"]),
    ("星期一", "monday", ["tuesday", "wednesday", "friday"]),
    ("紫色", "purple", ["yellow", "pink", "brown"]),
    ("厨房", "kitchen", ["bedroom", "bathroom", "balcony"]),
    ("长城", "the great wall", ["the forbidden city", "the summer palace", "west lake"]),
    ("卷笔刀", "sharpener", ["schoolbag", "textbook", "notebook"]),
    ("洗衣机", "washing machine", ["refrigerator", "microwave", "air conditioner"]),
    ("长颈鹿", "giraffe", ["elephant", "monkey", "kangaroo"]),
    ("护士", "nurse", ["surgeon", "patient", "dentist"]),
    ("公园", "park", ["zoo", "farm", "square"]),
    ("蔬菜", "vegetable", ["fruit", "meat", "dessert"]),
    ("红色的", "red", ["blue", "green", "black"]),
    ("计算机", "computer", ["calculator", "telephone", "television"]),
    ("明天", "tomorrow", ["yesterday", "today", "tonight"]),
    ("晴朗的", "sunny", ["cloudy", "windy", "snowy"]),
    ("楼梯", "stairs", ["elevator", "corridor", "escalator"]),
    ("餐馆", "restaurant", ["hotel", "bakery", "supermarket"]),
]

PINYIN_RE = re.compile(r"^[a-züǖǘǚǜáàǎāēéěèīíǐìōóǒòūúǔù]+$")


def pick_distractors(answer, pool, n, rng):
    cands = [p for p in pool if p != answer]
    rng.shuffle(cands)
    return cands[:n]


def gen_pinyin_bank():
    rng = random.Random(42)
    seen = {}
    items = []
    for ch, py in CHARS:
        if ch in seen:
            print("SKIP duplicate char:", ch)
            continue
        if not PINYIN_RE.match(py):
            print("BAD pinyin:", ch, py)
            sys.exit(1)
        seen[ch] = py
        items.append((ch, py))

    pinyin_pool = sorted({py for _, py in items})
    pinyin_pool += ["māo", "miáo", "móu", "qiāo", "zhāo", "lín", "tíng"]

    questions = []
    for ch, py in items:
        ds = pick_distractors(py, pinyin_pool, 3, rng)
        assert len(ds) == 3, (ch, ds)
        questions.append({"q": ch, "a": py, "d": ds})

    # 同音字报告（同题感警告，不阻断）
    by_pinyin = {}
    for ch, py in items:
        by_pinyin.setdefault(py, []).append(ch)
    dup_sound = {k: v for k, v in by_pinyin.items() if len(v) > 1}

    return {
        "name": "小学高段汉字拼音",
        "type": "pinyin_choice",
        "questions": questions,
    }, len(items), dup_sound


def gen_word_bank():
    questions = []
    for zh, en, ds in WORDS:
        assert len(ds) == 3
        questions.append({"q": zh, "a": en, "d": ds})
    return {
        "name": "小学英语单词示例",
        "type": "word_choice",
        "questions": questions,
    }


def write_bank(path, bank):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(bank, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print("written:", path, "questions:", len(bank["questions"]))


def main():
    pinyin_bank, n, dup_sound = gen_pinyin_bank()
    write_bank(os.path.join(BANKS_DIR, "pinyin_grade5.json"), pinyin_bank)
    if dup_sound:
        print("SAME-PRONUNCIATION groups (expected for 多音/同音字, review):")
        for k, v in sorted(dup_sound.items()):
            print("  ", k, "".join(v))
    print("pinyin total:", n)
    if n < 140:
        print("WARNING: below 140 target")
        sys.exit(2)

    write_bank(os.path.join(BANKS_DIR, "word_sample.json"), gen_word_bank())


if __name__ == "__main__":
    main()
