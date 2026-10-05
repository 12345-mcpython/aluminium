"""Keep the half that works, register the half that does not (round 5 of the goal).

Measured with a temporary probe, same scene, same enemy:
  * the content's cast deals 767.585; casting SLOT 9 directly deals 383.793 -- so `REPLACE_SKILL` installs the row into the map
    (`getSkillSlot() == 9`) but a `CAST_SKILL` later in the SAME rule still executes the OLD row. That is why the file used to
    claim 【弑王成王】 while running the plain skill.
  * the cost half works exactly: 1831.7376 -> 1190.6294 = 65% of the CURRENT value, through the new `target_current_hp` share.

So: the swap effect comes out, the cost stays, and the note says which half is missing and why.
"""
import io
import json
import os
import sys

CHAR = "src/main/resources/characters/1404.json"
PROBE = "src/test/java/com/laosun/aluminium/test/SwapReachesCastProbeTest.java"

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rule = next(entry for entry in doc["rules"]
            if isinstance(entry, dict) and entry.get("id") == "turn_start_autocasts_skill")
effects = rule["do"]
if not any(effect.get("op") == "REPLACE_SKILL" for effect in effects):
    sys.exit("REFUSING: the swap is not there")
rule["do"] = [effect for effect in effects if effect.get("op") != "REPLACE_SKILL"]
rule["note"] = (
    "「【血仇】状态期间…自身回合开始时自动施放【弑王成王】」⇒ `TURN_START` ＋ `self has_state 血仇` ⇒ "
    "`CONSUME_HP{scale: target_current_hp, percent: 0.35}` ✓ ⇒ `CAST_SKILL{skill: SKILL}` ✓。"
    "⚠⚠ **2026-10-02 三处订正（均实测 ✓）**：① 原来**没有 `self has_state 血仇` 门** ✗（句子明写在【血仇】语境里 ✓）；"
    "② 原来**没有付代价** ✗ ⇒ 本轮写出 `CONSUME_HP` ✓（新增**当前生命值**份额 "
    "`target_current_hp` ✓ —— 旧词汇只有最大值/已损失两种 ✗，而 35% 的**最大值**会是一个看上去很对的错数 ✗）；"
    "③ **旧报的 `CAST_SKILL{SKILL}` 放的是普通战技** ✗（槽 2 ✓）。"
    "⚠⚠ **而“改放【弑王成王】”本轮试了并撞到一个硬事实 ✗**："
    "`REPLACE_SKILL{skill: SKILL, skill_id: **9**}` **确实装进了 map** ✓（`getSkills().get(SKILL).getSkillSlot()` 读到 **9** ✓、category `BPSKILL` ✓），"
    "⚠ 但**同一条规则里随后的 `CAST_SKILL` 仍执行旧行** ✗：同一场景里，内容那条造成 "
    "**767.585** ✓、而**直接**放槽 9 只有 **383.793** ✓（他攻击力 426.888、上限生命 1831.7376 ✓）。"
    "⇒ 所以换装那一半**本轮不进树** ✗，登记在 `EXPRESSION.md` §3 ✓。"
    "⚠ 仍登记 ✓：充能 150 那句（❗ 实测：「充能 ≥ 100」那条**没有【血仇】门** ✗ ⇒ 它会**反复**触发并把充能抽干 ✗"
    "，充能因此攒不到 150 ✗）。")
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   the cost stays, the swap comes out, and the note names the missing half")

if os.path.exists(PROBE):
    os.remove(PROBE)
    print("ok   the probe is gone (it answered)")

# the surviving reading still needs to read the whole rule, so the hand-built swap test keeps its own place in the judge
JUDGE = "src/test/java/com/laosun/aluminium/test/MydeiBloodfeudSkillsTest.java"
text = io.open(JUDGE, encoding="utf-8").read()
OLD = '        Assertions.assertTrue(enemyAfter < enemyBefore, "and the attack lands");'
NEW = ('        Assertions.assertTrue(enemyAfter < enemyBefore, "and the attack lands");\n'
       '        // ⚠ This reading covers the COST half. The other half -- swapping slot 9 in so the cast runs 【弑王成王】 -- does NOT\n'
       '        // work from inside a rule yet, measured: the swap lands in the map while a `CAST_SKILL` later in the same rule still runs the old\n'
       '        // row (767.585 against 383.793 for a direct cast of slot 9). Registered in EXPRESSION §3, so the note does not claim it.')
if text.count(OLD) != 1:
    sys.exit("REFUSING: the judge anchor appears %d times" % text.count(OLD))
io.open(JUDGE, "w", encoding="utf-8", newline="").write(text.replace(OLD, NEW, 1))
print("ok   the judge says which half it covers")
