"""Append slot 22's two rules to the EXISTING characters/1410.json, add its entry, and judge it (2026-10-02).

⚠ `characters/1410.json` already existed -- the first attempt refused before writing, which is exactly what the guard is for. So its own rules are kept and the two
new ones are appended after checking they are not already there.

The pair mirrors 1402's ode of romance: a named mark applied when the ode is cast at the character, and an `ALLY_ATTACK` rule that spends it for energy.
"""
import io
import json
import sys

HYSILENS = "src/main/resources/characters/1410.json"
SE = "src/main/resources/data/skill_effects.json"
SLOT = 22
MARK = "暖流"
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
    "source": ("1415 昔涟 忆灵技能 16 「献予「海洋」之诗」（数据槽位 22，SkillID 1141522）："
               "「单次生效，对海瑟音施放时，使海瑟音获得【" + MARK + "】。」"),
    "note": "⭐ 形状照 1402 的 `memosprite_ode_of_romance_marks_aglaea`（同一句话的两个实例）。",
})
rules.append({
    "id": SPENDS,
    "on": "ALLY_ATTACK",
    "when": ["self has_state " + MARK],
    "do": [
        {"op": "GAIN_ENERGY", "amount": GAIN, "target": "self"},
        {"op": "REMOVE_STATE", "buff": MARK},
    ],
    "source": ("1415 昔涟 忆灵技能 16 「献予「海洋」之诗」（数据槽位 22）："
               "「**海瑟音施放攻击后消耗【" + MARK + "】为自身恢复 #4 点能量**。」"),
    "note": ("⭐ 形状照 1402 的 `memosprite_ode_of_romance_spends_itself_for_energy`。"
             "⭐ `#4` 在 1141522 的**十行里全是 60**（**实测**）。"),
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
        "source": ("1415 昔涟 忆灵技能 16 「献予「海洋」之诗」（数据槽位 22）："
                   "工作在**规则侧**（印记与能量在 `characters/1410.json`），所以是 `Rules` 形状。"),
        "note": "⭐ 没有条目就不可交付。",
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
 * 1415's memosprite skill 22 「献予「海洋」之诗」: the mark, and the energy it pays (2026-10-02).
 *
 * <p>「对海瑟音施放时，使海瑟音获得【暖流】。海瑟音施放攻击后消耗【暖流】为自身恢复 #4 点能量。」
 *
 * <p>⭐ Two readings: the mark lands when the ode is cast at her, and ONE attack spends it for exactly #4 = 60 energy -- the mark must be gone afterwards, so a rule
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
