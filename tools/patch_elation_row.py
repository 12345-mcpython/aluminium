"""Engine: an ElationDamage skill row reads its params as [hits, per-hit share, final split share] (2026-10-02, item 57).

Document, verbatim (`data/skills.json` -> 8009 slot 20, `skill_id` 800920, `attack_type` ElationDamage, L15 `[8, 0.25, 0.75]`):
  「造成 **#1** 次伤害，每次对敌方**随机单体**造成 **#2%** 的雷属性欢愉伤害。**最后**造成 **#3%** 的雷属性欢愉伤害，由**敌方全体均分**。」
Readers: 8009 and 8010 (both slot 20; the other seven ElationDamage rows in the corpus have the same shape).

⭐ WHY A NEW BRANCH AND NOT A FIX TO A NUMBER: the row's own `skill_effect` is `AoEAttack`, so today it takes the AOE path and reads
`params.getFirst()` = **8** as the MULTIPLIER -- i.e. 800% damage, where the document says eight hits of 25%. `TriggerInterpreter`'s
CAST_SKILL comment already names this as the reason the Elation auto-casts stay registered: "a skill the engine's own model
mis-reads (an Elation skill's row starts with a HIT COUNT) is mis-read here too".

✅ What already existed (so this is smaller than it looks): the damage TYPE follows the data and the 欢愉 boost is folded into the
base -- both landed 2026-09-30 ("slice 1b", see `hit(...)`'s comment). This patch is only the reading of the row.
"""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/models/skill/SkillExecutor.java"
ANCHOR = "        CanHit mainTarget = targets.getFirst();\n\n        double totalDamage = 0;\n"

BRANCH = '''        CanHit mainTarget = targets.getFirst();

        // ⭐ An ElationDamage row reads its params as [hits, per-hit share, final split share] (2026-10-02; readers 8009/8010
        // slot 20, whose text is 「造成 #1 次伤害，每次对敌方随机单体造成 #2%…。最后造成 #3%…由敌方全体均分").
        // ⚠ The row's own `skill_effect` is AoEAttack, so without this branch the leading 8 is read as a MULTIPLIER -- 800% damage
        // -- which is the mis-reading the CAST_SKILL comment names as the reason the Elation auto-casts stay registered.
        // ✅ The damage type and the Elation boost already follow the data (2026-09-30 "slice 1b"), so only the row reading is new.
        if (data.getSkillType() == com.laosun.aluminium.enums.SkillType.ELATION_SKILL) {
            int elationHits = (int) Math.round(params.getFirst());
            double perHit = params.size() > 1 ? params.get(1) : params.getFirst();
            double perHitBase = user.getAttribute(baseAttribute).get() * perHit;
            // H-3's rule, applied here too: the row's stance value is the WHOLE skill's toughness reduction, so it is spread
            // evenly over the instances this row settles (the hits plus the one final split instance).
            double elationStance = data.stanceFor(true) / Math.max(1, elationHits + 1);
            for (int i = 0; i < elationHits; i++) {
                // 「对敌方随机单体」: re-drawn every hit, so a target that dies mid-way is simply not drawn again.
                CanHit victim = battle.randomOpponent(user);
                if (victim == null) {
                    break;
                }
                totalDamage += hit(battle, data, user, element, perHitBase, victim, hitTargets, elationStance,
                        skill.getSkillSlot());
            }
            if (params.size() > 2) {
                List<CanHit> everyone = battle.targetableEnemies();
                if (!everyone.isEmpty()) {
                    double share = user.getAttribute(baseAttribute).get() * params.get(2) / everyone.size();
                    for (CanHit victim : everyone) {
                        totalDamage += hit(battle, data, user, element, share, victim, hitTargets, elationStance,
                                skill.getSkillSlot());
                    }
                }
            }
            return;
        }

        double totalDamage = 0;
'''

text = io.open(PATH, encoding="utf-8").read()
if text.count(ANCHOR) != 1:
    sys.stderr.write("REFUSING: the anchor appears %d times\n" % text.count(ANCHOR))
    raise SystemExit(1)
io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(ANCHOR, BRANCH, 1))
print("ok   SkillExecutor: the ElationDamage branch is in, before the effect switch")
