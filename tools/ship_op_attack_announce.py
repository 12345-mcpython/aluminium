"""An op attack can announce itself, and say what kind it was (2026-10-02). Then 1415's second sentence lands.

Measured before writing:
  * `fireAfterAttack` has exactly two callers (`SkillExecutor:556`, `EnemySkill:162`), so an op-driven attack never announces itself at all;
  * and the `ATTACK_FINISHED` fire passes `null, null` for the instance and the cast, so a rule on it cannot ask where the attack came from.

So: an OVERLOAD of `fireAfterAttack` that carries the category (the existing one delegates, so no caller changes), and the DAMAGE op announcing its attack when the rule
STATES a `cast_category`. \u26a0 Deliberately not passed by `SkillExecutor`: a real cast's category is already available, and touching that path would change the behaviour of
every shipped skill.

Then the ode's clause can hang on the SAME event as the zone rider -- `ATTACK_FINISHED`, which never fires for an attack that hit nothing -- and ask `from_category:
FOLLOW_UP`. \u2b50 Its own extra instance states no category, so it announces no attack and cannot re-trigger anything.
"""
import io
import json
import sys

BAT = "src/main/java/com/laosun/aluminium/Battle.java"
INT = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
TRIBBIE = "src/main/resources/characters/1403.json"
ZONE = "\u7ed3\u754c"

bat = io.open(BAT, encoding="utf-8").read()
interp = io.open(INT, encoding="utf-8").read()

BAT_ANCHOR = """    public void fireAfterAttack(CanHit attacker, CanHit mainTarget,
                                Collection<? extends CanHit> hitTargets, double totalDamage) {
        if (attacker == null || hitTargets == null || hitTargets.isEmpty()) {
            return;                                  // not a single hit landed → it does not count as an attack
        }"""
BAT_NEW = """    public void fireAfterAttack(CanHit attacker, CanHit mainTarget,
                                Collection<? extends CanHit> hitTargets, double totalDamage) {
        fireAfterAttack(attacker, mainTarget, hitTargets, totalDamage, null);
    }

    /**
     * The same, with the attacking instance's CAST CATEGORY (2026-10-02; reader: 1415's ode of passage, whose clause is about a \u300c\u8ffd\u52a0\u653b\u51fb\u300d).
     *
     * <p>\u26a0 An op-driven attack had no way to announce itself at all -- this method had exactly two callers, both of them casts -- and the event it fires passed no
     * cast, so a rule listening at the attack level could not ask what kind of attack it was. \u2b50 The four-argument form delegates here, so no existing caller changes.
     */
    public void fireAfterAttack(CanHit attacker, CanHit mainTarget,
                                Collection<? extends CanHit> hitTargets, double totalDamage,
                                com.laosun.aluminium.enums.SkillCategory castCategory) {
        if (attacker == null || hitTargets == null || hitTargets.isEmpty()) {
            return;                                  // not a single hit landed → it does not count as an attack
        }"""

BAT_FIRE_ANCHOR = """        fireTriggers(TriggerEvent.ATTACK_FINISHED, attacker, mainTarget, targets.size(), totalDamage,
                null, null, 0, 0, targets);"""
BAT_FIRE_NEW = """        fireTriggers(TriggerEvent.ATTACK_FINISHED, attacker, mainTarget, targets.size(), totalDamage,
                null, castCategory, 0, 0, targets);"""

INT_ANCHOR = """                    effect.getCastCategory() == null || effect.getCastCategory().isBlank()
                            ? null
                            : com.laosun.aluminium.enums.SkillCategory.fromString(effect.getCastCategory().trim()));
    }"""
INT_NEW = """                    effect.getCastCategory() == null || effect.getCastCategory().isBlank()
                            ? null
                            : com.laosun.aluminium.enums.SkillCategory.fromString(effect.getCastCategory().trim()));
            // \u2b50 A rule that STATES its category is also saying "this was an attack of that kind" (2026-10-02), which is the only way an op-driven attack can
            // announce itself -- `fireAfterAttack` had two callers, both casts. The hit set is the instance's own snapshot (or the attack's, when there is one).
            com.laosun.aluminium.enums.SkillCategory stated =
                    effect.getCastCategory() == null || effect.getCastCategory().isBlank()
                            ? null
                            : com.laosun.aluminium.enums.SkillCategory.fromString(effect.getCastCategory().trim());
            if (stated != null) {
                java.util.Set<CanHit> hits = !ctx.attackHitTargets().isEmpty()
                        ? new java.util.LinkedHashSet<>(ctx.attackHitTargets())
                        : (ctx.damage() == null ? java.util.Set.of() : ctx.damage().hitTargets());
                battle.fireAfterAttack(attacker, victim,
                        hits.isEmpty() ? java.util.List.of(victim) : java.util.List.copyOf(hits),
                        settledBase, stated);
            }
        }
    }"""

for body, old, label in ((bat, BAT_ANCHOR, "fireAfterAttack"), (bat, BAT_FIRE_ANCHOR, "the event fire"),
                         (interp, INT_ANCHOR, "the worker tail")):
    n = body.count(old)
    print("anchor %-18s : %d" % (label, n))
    if n != 1:
        sys.exit("REFUSING: %s occurs %d times -- nothing written" % (label, n))

io.open(BAT, "w", encoding="utf-8", newline="\n").write(bat.replace(BAT_ANCHOR, BAT_NEW).replace(BAT_FIRE_ANCHOR, BAT_FIRE_NEW))
io.open(INT, "w", encoding="utf-8", newline="\n").write(interp.replace(INT_ANCHOR, INT_NEW))
print("ok   an op attack can announce itself, with its category")

# ---- the content: the ode's clause, on the same event as the rider ----
doc = json.load(io.open(TRIBBIE, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
RULE_ID = "ode_of_passage_extra_instance"
if any(r.get("id") == RULE_ID for r in rules):
    sys.exit("REFUSING: %s is already there" % RULE_ID)
follow = [r for r in rules if r.get("id") == "talent_followup_on_other_ult"]
if len(follow) != 1 or not any(e.get("cast_category") == "FOLLOW_UP" for e in follow[0].get("do", [])):
    sys.exit("REFUSING: his follow-up does not declare FOLLOW_UP")
odes = [r for r in rules if r.get("id") == "memosprite_ode_of_passage_makes_his_damage_ignore_defence"]
marks = [e.get("buff") for e in odes[0].get("do", []) if e.get("op") == "APPLY_BUFF" and e.get("buff")]
if len(marks) != 1:
    sys.exit("REFUSING: the first clause applies no single mark")
ODE = marks[0]

rules.append({
    "id": RULE_ID,
    "on": "ATTACK_FINISHED",
    "when": ["from_category FOLLOW_UP", "self has_state " + ODE, "self has_state " + ZONE],
    "do": [{
        "op": "DAMAGE",
        "times": 1,
        "scale": "owner_max_hp",
        "percent_from_skill_param": "ULTRA:2",
        "element": "Quantum",
        "target": "highest_hp_attack_hit",
    }],
    "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 14 \u300c\u732e\u4e88\u300c\u95e8\u5f84\u300d\u4e4b\u8bd7\u300d\uff1a"
               "\u300c**\u7f07\u5b9d\u65bd\u653e\u8ffd\u52a0\u653b\u51fb\u89e6\u53d1\u7f07\u5b9d\u7684\u7ed3\u754c\u7684\u9644\u52a0\u4f24\u5bb3\u65f6\uff0c\u4f1a\u989d\u5916\u9020\u6210 #1 \u6b21\u9644\u52a0\u4f24\u5bb3**\u3002\u300d"),
    "note": ("\u2b50 \u4e0e\u7ed3\u754c rider **\u540c\u4e00\u4e2a\u4e8b\u4ef6**\uff08`ATTACK_FINISHED`\uff09\uff1a\u5b83\u5929\u7136\u4e0d\u4f1a\u4e3a\u201c\u4e00\u4e2a\u4eba\u4e5f\u6ca1\u6253\u5230\u201d\u7684\u653b\u51fb\u53d1\u51fa\u3002"
             "\u2b50 \u95e8\u662f `from_category FOLLOW_UP`\uff08**\u672c\u8f6e\u65b0\u901a\u7684**\uff1a\u4ed6\u7684\u8ffd\u52a0\u653b\u51fb\u58f0\u660e\u4e86 `cast_category`\uff0cDAMAGE op \u4ece\u800c\u80fd\u201c\u5ba3\u544a\u4e00\u6b21\u653b\u51fb\u201d\uff09"
             "**\u52a0\u4e0a**\u90a3\u53e5\u8bd7\u7684\u5370\u8bb0\u4e0e\u7ed3\u754c\u72b6\u6001\u3002\u2b50 \u800c\u5b83\u81ea\u5df1\u90a3\u7b14**\u4e0d\u58f0\u660e\u7c7b\u522b**\uff0c\u6240\u4ee5\u5b83\u53d1\u4e0d\u51fa ATTACK_FINISHED\uff0c**\u4e0d\u4f1a\u9012\u5f52**\u3002"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(TRIBBIE, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1403 now carries %s (%d rules)" % (RULE_ID, len(rules)))
