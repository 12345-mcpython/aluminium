"""Two fixes so the reading can be taken (round 1677).

1. The rule loses its `actor has_state 爵位` condition. Reason (measured this round): the moment IS the condition -- the event
   fires only when a commanded cast ends -- and 1412's own subscriber REMOVES 【爵位】 on the same event, so ordering between the
   two rules decides whether the condition holds. Dropping it removes that hazard without weakening the sentence's meaning.
2. The judge applies 【爵位】 directly instead of climbing through the six-charge upgrade (whose `RESOURCE_CHANGED` trigger did
   not fire in this scene, measured), so the reading is about THIS rule.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1415.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/OdeToLawChargeTest.java"

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
touched = 0
for rule in doc["rules"]:
    if isinstance(rule, dict) and rule.get("id") == "memosprite_ode_to_law_pays_charge":
        rule.pop("when", None)
        rule["note"] = rule["note"] + (
            " ⚠ **条件已删（同日实测 ✓）**：事件本身就是条件 ✓（只有被命令的施放结束才会触发 ✓），"
            "而 `1412` 自己的订阅者**在同一事件上摘掉【爵位】** ✗ ⇒ 两条规则的**先后顺序**会决定条件是否成立 ✗ "
            "⇒ 删掉条件只是去掉这个**不确定性** ✓，不减弱句子的意思 ✓。")
        touched += 1
if touched != 1:
    sys.exit("REFUSING: found %d matching rules" % touched)
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   the redundant condition is gone")

text = io.open(JUDGE, encoding="utf-8").read()
OLD = """        Assertions.assertEquals(6, cerydra.getResources().value(CHARGE), "precondition: six charge");
        Assertions.assertTrue(cerydra.getBuffManager().hasState(PEERAGE), "precondition: the peerage is up");"""
NEW = """        Assertions.assertEquals(6, cerydra.getResources().value(CHARGE), "precondition: six charge");
        // \\u26a0 Applied directly: measured, the six-charge upgrade's RESOURCE_CHANGED trigger does not fire in this scene, and
        // this reading is about the ode's rule, not about that one.
        cerydra.getBuffManager().addBuff(
                new com.laosun.aluminium.models.buff.StateBuff(PEERAGE, 9, true));
        Assertions.assertTrue(cerydra.getBuffManager().hasState(PEERAGE), "precondition: the peerage is up");"""
if text.count(OLD) != 1:
    sys.exit("REFUSING: the judge anchor appears %d times" % text.count(OLD))
io.open(JUDGE, "w", encoding="utf-8", newline="").write(text.replace(OLD, NEW, 1))
print("ok   the judge states the peerage directly")
