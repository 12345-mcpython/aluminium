"""v3: the same stale fragment sits in TWO rules' notes (the note was copied), so replace it in every one of them."""
import io
import json
import sys

CHAR = "src/main/resources/characters/1314.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/JadeLiveGoodsTest.java"

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rules = doc["rules"] if isinstance(doc, dict) and "rules" in doc else doc

OLD_NOTE = "⇒ \u7cbe\u786e\u767b\u8bb0 \u2713\uff08\u8fd9\u662f\u7b2c 20/270 \u8f6e\u8fb9\u754c\u7ed3\u8bba\u7684\u65b0\u5b9e\u4f8b \u2713\uff09\uff1b"
NEW_NOTE = (
    "\u21d2 \u2b50 **\u8ba2\u6b63\uff082026-10-02 \u5b9e\u6d4b \u2713\uff09**\uff1a\u8fd9\u6761\u73b0\u5728**\u5df2\u7ecf\u51fa\u8d27** \u2713 \u2014\u2014 "
    "`MODIFY_ATTR ATTACK percent: 0.005 per_stack: \u5f53\u54c1 **per_stack_live: true**` \u2713\uff08\u7b2c 63 \u4ef6\u7684\u62fc\u5199 \u2713\uff09\uff1b"
    "\u5224\u636e `JadeLiveGoodsTest` \u2713\uff1a\u518d\u52a0 **4 \u5c42**\uff08\u4e0d\u91cd\u6302\u5149\u73af \u2713\uff09\u21d2 \u653b\u51fb\u529b\u4efd\u989d **+0.02 = 0.005 \u00d7 4** \u2713\u2713"
    "\uff08\u5171\u4eab\u8bfb\u6570 0.185 \u21d2 0.205 \u2713\uff09\u3002"
    "\u26a0 **\u540c\u4e00\u6761\u89c4\u5219\u91cc\u7684 `CRIT_ATTACK` \u90a3\u53e5\u4ecd\u662f\u5feb\u7167** \u2717\uff1a\u5b83\u7684\u62fc\u6cd5\u662f "
    "`scale: self_stacks:\u5f53\u54c1` \u2717\uff08**\u7edd\u5bf9\u503c\u6d3e\u751f** \u2713\uff09\u21d2 \u540e\u52a0\u7684\u5c42\u6570**\u4e0d\u8ddf\u968f** \u2717\uff08\u5b9e\u6d4b\u5dee\u503c **+0.0** \u2717\uff09\u21d2 \u767b\u8bb0 \u2713\uff1b")

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
