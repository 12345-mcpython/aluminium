"""v3: the same stale fragment sits in TWO rules' notes (the note was copied), so replace it in every one of them."""
import io
import json
import sys

CHAR = "src/main/resources/characters/1314.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/JadeLiveGoodsTest.java"

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rules = doc["rules"] if isinstance(doc, dict) and "rules" in doc else doc

OLD_NOTE = "⇒ 精确登记 ✓（这是第 20/270 轮边界结论的新实例 ✓）；"
NEW_NOTE = (
    "⇒ ⭐ **订正（2026-10-02 实测 ✓）**：这条现在**已经出货** ✓ —— "
    "`MODIFY_ATTR ATTACK percent: 0.005 per_stack: 当品 **per_stack_live: true**` ✓（第 63 件的拼写 ✓）；"
    "判据 `JadeLiveGoodsTest` ✓：再加 **4 层**（不重挂光环 ✓）⇒ 攻击力份额 **+0.02 = 0.005 × 4** ✓✓"
    "（共享读数 0.185 ⇒ 0.205 ✓）。"
    "⚠ **同一条规则里的 `CRIT_ATTACK` 那句仍是快照** ✗：它的拼法是 "
    "`scale: self_stacks:当品` ✗（**绝对值派生** ✓）⇒ 后加的层数**不跟随** ✗（实测差值 **+0.0** ✗）⇒ 登记 ✓；")

updated = 0
for rule in rules:
    note = rule.get("note")
    if note and OLD_NOTE in note:
        rule["note"] = note.replace(OLD_NOTE, NEW_NOTE)
        updated += 1
if updated < 1:
    sys.exit("REFUSING: the stale fragment was not found")
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   corrected the note in %d rule(s)" % updated)

text = io.open(JUDGE, encoding="utf-8").read()
for fragment in (
    "        // CRIT DMG's base is 0 (the 50% everyone starts with is itself a modifier), so its reading is absolute\n"
    "        double critShare = jade.getAttribute(AttributeType.CRIT_ATTACK).get();\n",
    "        double critAfter = jade.getAttribute(AttributeType.CRIT_ATTACK).get();\n",
    '        Assertions.assertEquals(0.024 * 4, critAfter - critShare, 1e-9,\n'
    '                "and so does the crit-damage one (absolute: its base is zero)");\n',
):
    if text.count(fragment) != 1:
        sys.exit("REFUSING: a judge fragment appears %d times" % text.count(fragment))
    text = text.replace(fragment, "", 1)
text = text.replace(' + " critShare=" + critShare', "", 1)
text = text.replace(' + " critShare=" + critAfter', "", 1)
io.open(JUDGE, "w", encoding="utf-8", newline="").write(text)
print("ok   the judge reads the clause that works")
