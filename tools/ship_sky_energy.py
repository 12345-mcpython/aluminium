"""1415's memosprite skill 19 「献予「天空」之诗」 -- the energy sentence (2026-10-02).

Verbatim (1141519, params [0.36, 12]): 「德谬歌施放忆灵技时，使风堇获得2层【献予「天空」之诗】。<b>对风堇施放时，为风堇恢复 #2 点能量。</b>…」

Measured: #2 is NOT constant -- it runs 12 at level 1 to 33.6 at level 10 -- so it must be read out of the cast skill's row rather than written as a literal. 风堇 is cid 1409
in our own data ("Hyacine"), and her file already exists, so this appends.

\u26d4 Registered from the same skill: 「德谬歌施放忆灵技时，使风堇获得2层【献予「天空」之诗】」 (a stack on HER, triggered by the memosprite's cast -- a cross-table target our
selectors do not have), 「计入小伊卡忆灵技的治疗数值额外提高…」 and 「消耗1层」.
"""
import io
import json
import os
import sys

HYACINE = "src/main/resources/characters/1409.json"
SE = "src/main/resources/data/skill_effects.json"
SKILLS = "src/main/resources/data/skills.json"
SLOT = 19
MARK = "\u732e\u4e88\u300c\u5929\u7a7a\u300d\u4e4b\u8bd7"

table = json.load(io.open(SKILLS, encoding="utf-8"))
rows = table["11415"][str(SLOT)].get("param_list") or []
if not rows or rows[0][1] == rows[-1][1]:
    sys.exit("REFUSING: #2 does not run with level")
print("ok   #2 runs %s -> %s over %d levels" % (rows[0][1], rows[-1][1], len(rows)))

existed = os.path.exists(HYACINE)
if not existed:
    sys.exit("REFUSING: 1409.json does not exist -- read before creating")
doc = json.load(io.open(HYACINE, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
print("ok   1409.json has %d rules -- appending" % len(rules))

RULE_ID = "memosprite_ode_of_sky_gives_her_energy"
if any(r.get("id") == RULE_ID for r in rules):
    sys.exit("REFUSING: %s is already there" % RULE_ID)

rules.append({
    "id": RULE_ID,
    "on": "CAST_SETUP",
    "when": ["target == self", "actor is_summon", "from_skill_id == " + str(SLOT)],
    "do": [{
        "op": "GAIN_ENERGY",
        "scale": "cast_skill_param:1",     # #2 comes out of the CAST skill's row -- measured 12 -> 33.6
        "percent": 1.0,
        "target": "self",
    }],
    "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 19 \u300c\u732e\u4e88\u300c\u5929\u7a7a\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 19\uff0cSkillID 1141519\uff09\uff1a"
               "\u300c**\u5bf9\u98ce\u5807\u65bd\u653e\u65f6\uff0c\u4e3a\u98ce\u5807\u6062\u590d #2 \u70b9\u80fd\u91cf**\u3002\u300d"),
    "note": ("\u2b50 `#2` **\u968f\u7b49\u7ea7\u53d8**\uff08**\u5b9e\u6d4b**\uff1a12 \u2192 33.6\uff09\u2192 \u8d70 `cast_skill_param:1`\uff08\u65bd\u653e\u6280\u80fd\u7684\u7b2c 1 \u53c2\u6570\uff09\u3002"
             "\u2b50 \u5f62\u72b6\u7167 1410 \u7684 `memosprite_ode_of_ocean_spends_itself_for_energy`\uff08\u540c\u4e00\u53e5\u8bdd\u7684\u53e6\u4e00\u4e2a\u5b9e\u4f8b\uff09\u3002"),
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
 * <p>\u2b50 Two scenes that differ by exactly one thing: whether the ode was cast at her. #2 runs with the level (12 -> 33.6), so the expected number is read out of the
 * CAST skill's own row rather than written down. Nothing is replaced.
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
        Character hyacine = CharacterFactory.create(HYACINE, LEVEL);
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
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

    /** Her energy gain from an empty starting point, with or without the ode. */
    private static double energyAfter(boolean castTheOde) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character hyacine = CharacterFactory.create(HYACINE, LEVEL);
        Battle battle = new Battle(List.of(cyrene, hyacine),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        hyacine = battle.characters.get(1);

        if (castTheOde) {
            var demiurge = battle.summonServant(cyrene);
            var ode = demiurge.skillAt(19);
            Assertions.assertNotNull(ode, "precondition: slot 19");
            SkillExecutor.execute(battle, ode, demiurge, List.of(hyacine));
            battle.processRequests();
        }
        hyacine.setCurrentEnergy(0);
        return hyacine.getCurrentEnergy();
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/SkyOdeEnergyTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   judge written")
