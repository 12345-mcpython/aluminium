"""Append slot 22's two rules to the EXISTING characters/1410.json, add its entry, and judge it (2026-10-02).

\u26a0 `characters/1410.json` already existed -- the first attempt refused before writing, which is exactly what the guard is for. So its own rules are kept and the two
new ones are appended after checking they are not already there.

The pair mirrors 1402's ode of romance: a named mark applied when the ode is cast at the character, and an `ALLY_ATTACK` rule that spends it for energy.
"""
import io
import json
import sys

HYSILENS = "src/main/resources/characters/1410.json"
SE = "src/main/resources/data/skill_effects.json"
SLOT = 22
MARK = "\u6696\u6d41"
GAIN = 60

doc = json.load(io.open(HYSILENS, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
print("1410.json already carries %d rules" % len(rules))

MARKS = "memosprite_ode_of_ocean_marks_hysilens"
SPENDS = "memosprite_ode_of_ocean_spends_itself_for_energy"
if any(r.get("id") in (MARKS, SPENDS) for r in rules):
    sys.exit("REFUSING: the ocean rules are already there")

rules.append({
    "id": MARKS,
    "on": "CAST_SETUP",
    "when": ["target == self", "actor is_summon", "from_skill_id == " + str(SLOT)],
    "do": [{"op": "APPLY_BUFF", "buff": MARK, "permanent": True, "target": "self"}],
    "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 16 \u300c\u732e\u4e88\u300c\u6d77\u6d0b\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 22\uff0cSkillID 1141522\uff09\uff1a"
               "\u300c\u5355\u6b21\u751f\u6548\uff0c\u5bf9\u6d77\u745f\u97f3\u65bd\u653e\u65f6\uff0c\u4f7f\u6d77\u745f\u97f3\u83b7\u5f97\u3010" + MARK + "\u3011\u3002\u300d"),
    "note": "\u2b50 \u5f62\u72b6\u7167 1402 \u7684 `memosprite_ode_of_romance_marks_aglaea`\uff08\u540c\u4e00\u53e5\u8bdd\u7684\u4e24\u4e2a\u5b9e\u4f8b\uff09\u3002",
})
rules.append({
    "id": SPENDS,
    "on": "ALLY_ATTACK",
    "when": ["self has_state " + MARK],
    "do": [
        {"op": "GAIN_ENERGY", "amount": GAIN, "target": "self"},
        {"op": "REMOVE_STATE", "buff": MARK},
    ],
    "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 16 \u300c\u732e\u4e88\u300c\u6d77\u6d0b\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 22\uff09\uff1a"
               "\u300c**\u6d77\u745f\u97f3\u65bd\u653e\u653b\u51fb\u540e\u6d88\u8017\u3010" + MARK + "\u3011\u4e3a\u81ea\u8eab\u6062\u590d #4 \u70b9\u80fd\u91cf**\u3002\u300d"),
    "note": ("\u2b50 \u5f62\u72b6\u7167 1402 \u7684 `memosprite_ode_of_romance_spends_itself_for_energy`\u3002"
             "\u2b50 `#4` \u5728 1141522 \u7684**\u5341\u884c\u91cc\u5168\u662f 60**\uff08**\u5b9e\u6d4b**\uff09\u3002"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(HYSILENS, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1410 now carries %d rules (the two ocean ones appended)" % len(rules))

effects = json.load(io.open(SE, encoding="utf-8"))
if str(SLOT) not in effects.get("11415", {}):
    effects.setdefault("11415", {})[str(SLOT)] = {
        "effect": "Rules",
        "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 16 \u300c\u732e\u4e88\u300c\u6d77\u6d0b\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 22\uff09\uff1a"
                   "\u5de5\u4f5c\u5728**\u89c4\u5219\u4fa7**\uff08\u5370\u8bb0\u4e0e\u80fd\u91cf\u5728 `characters/1410.json`\uff09\uff0c\u6240\u4ee5\u662f `Rules` \u5f62\u72b6\u3002"),
        "note": "\u2b50 \u6ca1\u6709\u6761\u76ee\u5c31\u4e0d\u53ef\u4ea4\u4ed8\u3002",
    }
    io.open(SE, "w", encoding="utf-8", newline="\n").write(json.dumps(effects, ensure_ascii=False, indent=2) + "\n")
    print("ok   skill_effects.json: 11415/%d = Rules" % SLOT)

JUDGE = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 22 \u300c\u732e\u4e88\u300c\u6d77\u6d0b\u300d\u4e4b\u8bd7\u300d: the mark, and the energy it pays (2026-10-02).
 *
 * <p>\u300c\u5bf9\u6d77\u745f\u97f3\u65bd\u653e\u65f6\uff0c\u4f7f\u6d77\u745f\u97f3\u83b7\u5f97\u3010\u6696\u6d41\u3011\u3002\u6d77\u745f\u97f3\u65bd\u653e\u653b\u51fb\u540e\u6d88\u8017\u3010\u6696\u6d41\u3011\u4e3a\u81ea\u8eab\u6062\u590d #4 \u70b9\u80fd\u91cf\u3002\u300d
 *
 * <p>\u2b50 Two readings: the mark lands when the ode is cast at her, and ONE attack spends it for exactly #4 = 60 energy -- the mark must be gone afterwards, so a rule
 * that paid without spending (or spent without paying) cannot pass.
 */
public class OceanOdeEnergyTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int HYSILENS = 1410;
    private static final int MONSTER = 1002011;
    private static final String MARK = "\\u6696\\u6d41";

    @Test
    public void theMarkLandsAndOneAttackSpendsItForSixty() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character hysilens = CharacterFactory.create(HYSILENS, LEVEL);
        Battle battle = new Battle(List.of(cyrene, hysilens),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        hysilens = battle.characters.get(1);

        var demiurge = battle.summonServant(cyrene);
        var ode = demiurge.skillAt(22);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries slot 22");
        SkillExecutor.execute(battle, ode, demiurge, List.of(hysilens));
        battle.processRequests();
        Assertions.assertTrue(hysilens.getBuffManager().hasState(MARK),
                "\\u300c\\u4f7f\\u6d77\\u745f\\u97f3\\u83b7\\u5f97\\u3010\\u6696\\u6d41\\u3011\\u300d-- the mark is on her");

        hysilens.setCurrentEnergy(20);
        double before = hysilens.getCurrentEnergy();
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, cyrene, battle.enemies.getFirst(), 1, 0);
        battle.processRequests();
        double after = hysilens.getCurrentEnergy();
        boolean stillMarked = hysilens.getBuffManager().hasState(MARK);
        System.out.println("[ocean] energy " + before + " -> " + after + " ; the mark is still on her = " + stillMarked);

        Assertions.assertEquals(before + 60, after, 1e-6,
                "\\u300c\\u6d88\\u8017\\u3010\\u6696\\u6d41\\u3011\\u4e3a\\u81ea\\u8eab\\u6062\\u590d #4 \\u70b9\\u80fd\\u91cf\\u300d-- #4 is 60 at every level");
        Assertions.assertFalse(stillMarked, "and the mark is consumed -- \\u6d88\\u8017 means spent, not merely read");
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/OceanOdeEnergyTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   judge written")
