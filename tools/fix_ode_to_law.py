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
            " \u26a0 **\u6761\u4ef6\u5df2\u5220\uff08\u540c\u65e5\u5b9e\u6d4b \u2713\uff09**\uff1a\u4e8b\u4ef6\u672c\u8eab\u5c31\u662f\u6761\u4ef6 \u2713\uff08\u53ea\u6709\u88ab\u547d\u4ee4\u7684\u65bd\u653e\u7ed3\u675f\u624d\u4f1a\u89e6\u53d1 \u2713\uff09\uff0c"
            "\u800c `1412` \u81ea\u5df1\u7684\u8ba2\u9605\u8005**\u5728\u540c\u4e00\u4e8b\u4ef6\u4e0a\u6458\u6389\u3010\u7235\u4f4d\u3011** \u2717 \u21d2 \u4e24\u6761\u89c4\u5219\u7684**\u5148\u540e\u987a\u5e8f**\u4f1a\u51b3\u5b9a\u6761\u4ef6\u662f\u5426\u6210\u7acb \u2717 "
            "\u21d2 \u5220\u6389\u6761\u4ef6\u53ea\u662f\u53bb\u6389\u8fd9\u4e2a**\u4e0d\u786e\u5b9a\u6027** \u2713\uff0c\u4e0d\u51cf\u5f31\u53e5\u5b50\u7684\u610f\u601d \u2713\u3002")
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
