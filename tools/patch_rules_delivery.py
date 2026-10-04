"""A commanded cast can deliver a skill whose work is done by RULES (2026-10-02, item 67).

Found last round by driving 1412's own content: `CAST_SKILL` refused her skill outright --

  "CAST_SKILL effect points at SKILL, whose effect is SUPPORT and which the engine cannot deliver: a non-damaging skill needs
   an entry in skill_effects.json saying what it restores or shields, and this one has none"

-- and the code's own comment names her: "1414's own skill is a shield; 1303/1412's are buffs the table has no entry for".

Measured: that table's two shapes are `Restore` and `Defence` (heal / shield), and `dispatchNonDamaging` treats anything that
is not `Restore` as a shield. A BUFF has no healing or shielding to state, and 1412's skill already does its work through the
rule table (her `skill_grants_military_merit` on SKILL_CAST). So the honest third shape is a spec that says exactly that:
`"effect": "Rules"` -- deliverable, and delivering nothing by itself.

Readers (all named in the source comment): 1412's 奇袭, 1303, 1414's shield-skill case. Plus 1415's ode clause, which needs
the coup to run at all.
"""
import io
import json
import sys

EXEC = None
for base, _dirs, files in __import__("os").walk("src/main/java"):
    if "SkillExecutor.java" in files:
        EXEC = base + "/SkillExecutor.java"
if EXEC is None:
    sys.exit("REFUSING: SkillExecutor.java not found")


def patch(path, old, new, label, count=1):
    text = io.open(path, encoding="utf-8").read()
    found = text.count(old)
    if found != count:
        sys.stderr.write("REFUSING %s: the anchor appears %d times\n" % (label, found))
        raise SystemExit(1)
    io.open(path, "w", encoding="utf-8", newline="\n").write(text.replace(old, new, count))
    print("ok   %s" % label)


patch(
    EXEC,
    """        boolean supported = spec != null
                && ("Restore".equals(spec.getEffect()) || "Defence".equals(spec.getEffect()))
                && !isAmbiguous(spec);""",
    """        // \u2b50 A third shape (2026-10-02): \u300c\u5947\u88ad\u300d commands a copy of 1412's SKILL, which is a BUFF -- its work is done by the
        // rule table (`skill_grants_military_merit` on SKILL_CAST), so there is no amount to restore or shield. `Rules` says
        // exactly that, and it has no parameter row to be ambiguous about.
        boolean supported = spec != null
                && ("Restore".equals(spec.getEffect()) || "Defence".equals(spec.getEffect())
                || "Rules".equals(spec.getEffect()))
                && ("Rules".equals(spec.getEffect()) || !isAmbiguous(spec));""",
    "deliverableSpec accepts the Rules shape",
)

patch(
    EXEC,
    """        for (CanHit target : targets) {
            double amount = effectAmount(skill, spec, user, target);
            if ("Restore".equals(spec.getEffect())) {""",
    """        if ("Rules".equals(spec.getEffect())) {
            // \u2b50 The cast happens (its events fire, so the rule table does its work) and delivers nothing by itself.
            return;
        }
        for (CanHit target : targets) {
            double amount = effectAmount(skill, spec, user, target);
            if ("Restore".equals(spec.getEffect())) {""",
    "dispatchNonDamaging delivers nothing for Rules",
)

PATH = "src/main/resources/data/skill_effects.json"
doc = json.load(io.open(PATH, encoding="utf-8"))
if "1412" in doc and "2" in doc["1412"]:
    sys.exit("REFUSING: 1412 slot 2 already has an entry")
doc.setdefault("1412", {})["2"] = {
    "effect": "Rules",
    "source": "1412 \u523b\u5f8b\u5fb7\u83c8 \u6218\u6280 \u5347\u53d8\uff0c\u58eb\u7686\u53ef\u5e05 (141202)\uff1a\u589e\u76ca\u578b\u6280\u80fd\u2014\u2014"
              "\u300c\u4f7f\u6307\u5b9a\u6211\u65b9\u5355\u4f53\u89d2\u8272\u83b7\u5f97\u3010\u519b\u529f\u3011\u5e76\u4f7f\u523b\u5f8b\u5fb7\u83c8\u83b7\u5f97 1 \u70b9\u5145\u80fd\u300d\u3002"
              "\u5b83\u7684\u6548\u679c**\u7531\u89c4\u5219\u8868\u8868\u8fbe** \u2713\uff08`skill_grants_military_merit` \u5728 `SKILL_CAST` \u4e0a \u2713\uff09"
              "\uff0c\u672c\u8eab\u6ca1\u6709\u6cbb\u7597\u91cf\u6216\u62a4\u76fe\u91cf\u53ef\u5199 \u2717 \u21d2 `Rules` \u2713\u3002",
    "note": "\u26a0 `CAST_SKILL` \u62d2\u7edd\u8fc7\u5b83 \u2717\uff08\u62a5\u9519\u539f\u6587\u81ea\u5df1\u70b9\u540d\uff1a*\"1303/1412's are buffs the table has no entry for\"* \u2713\uff09\u3002",
}
with io.open(PATH, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=4)
    handle.write("\n")
print("ok   skill_effects.json: 1412's skill is delivered by rules")
