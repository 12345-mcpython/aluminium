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
                // \u2b50 A cast parameter is a third spelling (2026-10-02; reader: 1415's ode of sky, \u300c\u4e3a\u98ce\u5807\u6062\u590d #2 \u70b9\u80fd\u91cf\u300d, whose
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
        // \u2b50 \u300c\u4e3a\u98ce\u5807\u6062\u590d #2 \u70b9\u80fd\u91cf\u300d (2026-10-02): an ABSOLUTE amount out of the skill that produced the event. The reader is the same one
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
    "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 19 \u300c\u732e\u4e88\u300c\u5929\u7a7a\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 19\uff0cSkillID 1141519\uff09\uff1a"
               "\u300c**\u5bf9\u98ce\u5807\u65bd\u653e\u65f6\uff0c\u4e3a\u98ce\u5807\u6062\u590d #2 \u70b9\u80fd\u91cf**\u3002\u300d"),
    "note": ("\u2b50 `#2` **\u968f\u7b49\u7ea7\u53d8**\uff08**\u5b9e\u6d4b**\uff1a12 \u2192 33.6\uff09\u2192 \u8d70 `cast_skill_param:1`\u3002"
             "\u2b50 \u800c `GAIN_ENERGY` \u672c\u6765**\u53ea\u6536** `amount` \u6216 `target_max_energy` \u7684\u4efd\u989d\uff08**\u5b9e\u6d4b**\uff1a"
             "`ENERGY_SCALES = Set.of(\"target_max_energy\")`\uff09\u2014\u2014 **\u672c\u8f6e\u7ed9\u5b83\u52a0\u4e86\u7b2c\u4e09\u79cd\u62fc\u6cd5**\u3002"),
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
        "source": "1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 19 \u300c\u732e\u4e88\u300c\u5929\u7a7a\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 19\uff09\uff1a\u5de5\u4f5c\u5728\u89c4\u5219\u4fa7\u3002",
        "note": "\u2b50 \u6ca1\u6709\u6761\u76ee\u5c31\u4e0d\u53ef\u4ea4\u4ed8\u3002",
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
 * 1415's memosprite skill 19 \u300c\u732e\u4e88\u300c\u5929\u7a7a\u300d\u4e4b\u8bd7\u300d: \u300c\u5bf9\u98ce\u5807\u65bd\u653e\u65f6\uff0c\u4e3a\u98ce\u5807\u6062\u590d #2 \u70b9\u80fd\u91cf\u300d (2026-10-02).
 *
 * <p>\u2b50 Two scenes that differ by exactly one thing: whether the ode was cast at her. #2 runs with the level (12 -> 33.6), so the expected number comes out of the CAST
 * skill's row. \u26a0 Her energy is zeroed BEFORE the cast -- reading an increment needs its baseline set before the action.
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
