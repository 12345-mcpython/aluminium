"""GAIN_ENERGY may read a cast parameter, and then slot 19's energy sentence lands (2026-10-02).

The gap, measured: `GAIN_ENERGY`'s validation is `requireAmountOrScale(effect, op, spec, ENERGY_SCALES, "max energy")` with `ENERGY_SCALES = Set.of("target_max_energy")`, so the
op accepted a flat `amount` or a share of the receiver's max energy -- and nothing else. Slot 19's 「为风堇恢复 #2 点能量」 has a #2 that RUNS WITH LEVEL (12 -> 33.6), so a
literal would be an approximation.

The reader already exists: `castParamValue(effect, ctx, spelled)` reads a parameter out of the skill that PRODUCED the event -- which for the memosprite's own cast is exactly
the memosprite skill that states the number. So this adds the branch, and slot 19's sentence.
"""
import io
import json
import os
import re
import sys

INT = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
HYACINE = "src/main/resources/characters/1409.json"
SE = "src/main/resources/data/skill_effects.json"
SLOT = 19

interp = io.open(INT, encoding="utf-8").read()

VAL_ANCHOR = """            case "GAIN_ENERGY" -> {
                requireAmountOrScale(effect, op, spec, ENERGY_SCALES, "max energy");
                requireNoStackArguments(effect, op, spec);
            }"""
VAL_NEW = """            case "GAIN_ENERGY" -> {
                // ⭐ A cast parameter is a third spelling (2026-10-02; reader: 1415's ode of sky, 「为风堇恢复 #2 点能量」, whose
                // #2 runs 12 -> 33.6). `ENERGY_SCALES` holds only a share of max energy, so the op could not read a number out of the skill that produced it.
                if (isCastParamScale(effect)) {
                    requireNoStackArguments(effect, op, spec);
                } else {
                    requireAmountOrScale(effect, op, spec, ENERGY_SCALES, "max energy");
                    requireNoStackArguments(effect, op, spec);
                }
            }"""

WORK_ANCHOR = """        if (effect.getScale() == null || effect.getScale().isBlank()) {
            battle.grantEnergy(target, scaledAmount(effect, ctx));
            return;
        }"""
WORK_NEW = """        if (effect.getScale() == null || effect.getScale().isBlank()) {
            battle.grantEnergy(target, scaledAmount(effect, ctx));
            return;
        }
        // ⭐ 「为风堇恢复 #2 点能量」 (2026-10-02): an ABSOLUTE amount out of the skill that produced the event. The reader is the same one
        // `MODIFY_ATTR` uses for `cast_skill_param:`, so the two cannot drift.
        if (isCastParamScale(effect)) {
            battle.grantEnergy(target, Math.round(castParamValue(effect, ctx,
                    effect.getScale().trim().substring(TriggerTable.CAST_SKILL_PARAM_PREFIX.length()))));
            return;
        }"""

HELPER_ANCHOR = "    private static void gainEnergy(Battle battle, EffectSpec effect, TriggerContext ctx) {"
HELPER_NEW = """    /** Whether a {@code GAIN_ENERGY} effect takes its amount out of the skill that produced the event. */
    private static boolean isCastParamScale(EffectSpec effect) {
        return effect.getScale() != null
                && effect.getScale().trim().startsWith(TriggerTable.CAST_SKILL_PARAM_PREFIX);
    }

    private static void gainEnergy(Battle battle, EffectSpec effect, TriggerContext ctx) {"""

for body, old, label in ((interp, VAL_ANCHOR, "the validation"), (interp, WORK_ANCHOR, "the worker branch"),
                         (interp, HELPER_ANCHOR, "the helper anchor")):
    n = body.count(old)
    print("anchor %-18s : %d" % (label, n))
    if n != 1:
        sys.exit("REFUSING: %s occurs %d times -- nothing written" % (label, n))

interp = interp.replace(VAL_ANCHOR, VAL_NEW).replace(WORK_ANCHOR, WORK_NEW).replace(HELPER_ANCHOR, HELPER_NEW)
io.open(INT, "w", encoding="utf-8", newline="\n").write(interp)
print("ok   GAIN_ENERGY now reads a cast parameter")

# ---- slot 19's energy sentence ----
if not os.path.exists(HYACINE):
    sys.exit("REFUSING: 1409.json does not exist")
doc = json.load(io.open(HYACINE, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
RULE_ID = "memosprite_ode_of_sky_gives_her_energy"
if any(r.get("id") == RULE_ID for r in rules):
    sys.exit("REFUSING: %s is already there" % RULE_ID)

rules.append({
    "id": RULE_ID,
    "on": "CAST_SETUP",
    "when": ["target == self", "actor is_summon", "from_skill_id == " + str(SLOT)],
    "do": [{"op": "GAIN_ENERGY", "scale": "cast_skill_param:1", "percent": 1.0, "target": "self"}],
    "source": ("1415 昔涟 忆灵技能 19 「献予「天空」之诗」（数据槽位 19，SkillID 1141519）："
               "「**对风堇施放时，为风堇恢复 #2 点能量**。」"),
    "note": ("⭐ `#2` **随等级变**（**实测**：12 → 33.6）→ 走 `cast_skill_param:1`。"
             "⭐ 而 `GAIN_ENERGY` 本来**只收** `amount` 或 `target_max_energy` 的份额（**实测**："
             "`ENERGY_SCALES = Set.of(\"target_max_energy\")`）—— **本轮给它加了第三种拼法**。"),
})
if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(HYACINE, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1409 now carries %s (%d rules)" % (RULE_ID, len(rules)))

effects = json.load(io.open(SE, encoding="utf-8"))
if str(SLOT) not in effects.get("11415", {}):
    effects.setdefault("11415", {})[str(SLOT)] = {
        "effect": "Rules",
        "source": "1415 昔涟 忆灵技能 19 「献予「天空」之诗」（数据槽位 19）：工作在规则侧。",
        "note": "⭐ 没有条目就不可交付。",
    }
    io.open(SE, "w", encoding="utf-8", newline="\n").write(json.dumps(effects, ensure_ascii=False, indent=2) + "\n")
    print("ok   skill_effects.json: 11415/%d = Rules" % SLOT)

JUDGE = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 19 「献予「天空」之诗」: 「对风堇施放时，为风堇恢复 #2 点能量」 (2026-10-02).
 *
 * <p>⭐ Two scenes that differ by exactly one thing: whether the ode was cast at her. #2 runs with the level (12 -> 33.6), so the expected number comes out of the CAST
 * skill's row. ⚠ Her energy is zeroed BEFORE the cast -- reading an increment needs its baseline set before the action.
 */
public class SkyOdeEnergyTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int HYACINE = 1409;
    private static final int MONSTER = 1002011;

    @Test
    public void theOdeRestoresHerEnergy() {
        double withoutOde = energyAfter(false);
        double withOde = energyAfter(true);

        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character hyacine = CharacterFactory.create(HYACINE, LEVEL);
        Battle probe = new Battle(List.of(cyrene, hyacine),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        probe.startBattle();
        probe.processRequests();
        var demiurge = probe.summonServant(probe.characters.get(0));
        var ode = demiurge.skillAt(19);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries slot 19");
        double expected = ode.getData().getSkills().get(demiurge.skillLevel(ode) - 1).get(1);

        System.out.println("[sky] her energy without the ode = " + withoutOde + " ; with it = " + withOde
                + " (the cast row says " + expected + ")");

        Assertions.assertEquals(0.0, withoutOde, 1e-9, "precondition: the ode is what moves it");
        Assertions.assertEquals(expected, withOde, Math.abs(expected) * 1e-6,
                "\\u300c\\u4e3a\\u98ce\\u5807\\u6062\\u590d #2 \\u70b9\\u80fd\\u91cf\\u300d-- and #2 runs with level");
    }

    private static double energyAfter(boolean castTheOde) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character hyacine = CharacterFactory.create(HYACINE, LEVEL);
        Battle battle = new Battle(List.of(cyrene, hyacine),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        hyacine = battle.characters.get(1);

        hyacine.setCurrentEnergy(0);
        if (castTheOde) {
            var demiurge = battle.summonServant(cyrene);
            var ode = demiurge.skillAt(19);
            Assertions.assertNotNull(ode, "precondition: slot 19");
            SkillExecutor.execute(battle, ode, demiurge, List.of(hyacine));
            battle.processRequests();
        }
        return hyacine.getCurrentEnergy();
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/SkyOdeEnergyTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   judge written")
