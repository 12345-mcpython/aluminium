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
    """        // ⭐ A third shape (2026-10-02): 「奇袭」 commands a copy of 1412's SKILL, which is a BUFF -- its work is done by the
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
            // ⭐ The cast happens (its events fire, so the rule table does its work) and delivers nothing by itself.
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
    "source": "1412 刻律德菈 战技 升变，士皆可帅 (141202)：增益型技能——"
              "「使指定我方单体角色获得【军功】并使刻律德菈获得 1 点充能」。"
              "它的效果**由规则表表达** ✓（`skill_grants_military_merit` 在 `SKILL_CAST` 上 ✓）"
              "，本身没有治疗量或护盾量可写 ✗ ⇒ `Rules` ✓。",
    "note": "⚠ `CAST_SKILL` 拒绝过它 ✗（报错原文自己点名：*\"1303/1412's are buffs the table has no entry for\"* ✓）。",
}
with io.open(PATH, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=4)
    handle.write("\n")
print("ok   skill_effects.json: 1412's skill is delivered by rules")
