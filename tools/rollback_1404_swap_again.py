"""Roll the swap back again, and correct the record: the comparison that "proved" the blocker was invalid.

Found by re-reading my own probe: scene 2 REPLACED his trigger table, so `level_convention` never ran and its slot-9 skill sat at
level 1, while the content scene runs at level 15. Two different levels are not a comparison, so last round's claim ("the swap
does not reach the commanded cast, 767.585 against 383.793") does NOT hold as measured -- what holds is the narrower fact that
`REPLACE_SKILL` installs the row into the map.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1404.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/MydeiBloodfeudSkillsTest.java"

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rule = next(entry for entry in doc["rules"]
            if isinstance(entry, dict) and entry.get("id") == "turn_start_autocasts_skill")
if not any(effect.get("op") == "REPLACE_SKILL" for effect in rule["do"]):
    sys.exit("REFUSING: the swap is not there")
rule["do"] = [effect for effect in rule["do"] if effect.get("op") != "REPLACE_SKILL"]
rule["note"] = rule["note"].replace(
    "⭐ **2026-10-02 第二次尝试：换装的寿命改成 `turns: 1`** ✓（❗ 第一次用 `until: next_attack` ✗，"
    "而它**在攻击开始时就还原** ✗ ⇒ 伤害仍用旧行算 ✗；这正是 `1301` 注记里写着“**尚未钉住**”的那件事 ✓）。",
    "⚠⚠ **2026-10-02 两次尝试均回滚 ✓，而且第一次的“阻断”已被认定为无效测量 ✗**："
    "① 寿命试了 `until: next_attack` ✗ 与 `turns: 1` ✓，伤害都是 **767.585** ✓ 不变 ⇒ 不是寿命问题 ✗；"
    "② 而用来对比的那个 **383.793** ✓ 是在“**换掉他整张表**”的场景里量的 ✗ —— 那样 `level_convention` **没跑** ✗，"
    "槽 9 停在 **1 级** ✗，而内容场景是 **15 级** ✓ ⇒ 两个数**不可比** ✗。"
    "⇒ 如实的结论：⭐ **只知道换装装进了 map** ✓（`getSkillSlot()` 读到 9 ✓）；"
    "❗ **“被命令的放用哪一行”仍未测准** ✗（下一步：在**同一场景、同一等级**下对比 ✓）。")
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   the swap is out and the note records the invalid comparison")

text = io.open(JUDGE, encoding="utf-8").read()
MARKER = "    /**\n     * ⭐ And the swap must REACH the cast"
index = text.find(MARKER)
if index < 0:
    sys.exit("REFUSING: the invalid reading was not found")
# drop the invalid reading and re-close the class
text = text[:index].rstrip()
if not text.endswith("}"):
    sys.exit("REFUSING: unexpected judge tail")
io.open(JUDGE, "w", encoding="utf-8", newline="").write(text + "\n}\n")
print("ok   the invalid comparison reading is gone")

lines = io.open("EXPRESSION.md", encoding="utf-8").read().split("\n")
PREFIX = "| **「自动施放【强化战技】」**"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d §3 rows start with that prefix" % len(hits))
lines[hits[0]] = (
    "| **「自动施放【强化战技】」**（万敌的【弑王成王】槽 **9** ✓、【弑神登神】槽 **11** ✓；"
    "槽号由 `SkillData.init(1404, 槽).stanceFor(…)` 的破韧 60/30 与 90/60 对上语料 ✓） "
    "| ⭐ 一个**可比的读数** ✗ —— ❗ 已知：`REPLACE_SKILL` **装进了 map** ✓（`getSkillSlot()` 读到 9 ✓、category `BPSKILL` ✓）；"
    "❗ **未知**：被命令的 `CAST_SKILL` 到底用哪一行 ✗（⚠ 两次尝试后发现：我用来对比的那个 383.793 是在“**换掉他整张表**”的场景里量的 ✗，"
    "那样 `level_convention` 没跑 ✗ ⇒ 槽 9 停在 1 级，而内容场景是 15 级 ✓ ⇒ 不可比 ✗） "
    "| `1404`（两句）、**`1415` 的忆灵技能 8** ✓ **共 3 位** ✓ "
    "| ⭐ 一个**同场景、同等级**的对比读数 ✗（如：两场都让 `level_convention` 跑 ✓，只差“有无换装” ✓） |")
io.open("EXPRESSION.md", "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   the §3 row says exactly what is known and what is not")
