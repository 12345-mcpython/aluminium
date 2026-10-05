"""1415's memosprite skill 22 「献予「海洋」之诗」 -- the damage-boost sentence (2026-10-02, round 69).

Verbatim (params [0.6, 0.3, 0.4, 60], #1 runs 0.6 -> 0.72): 「单次生效，对海瑟音施放时，使海瑟音获得【暖流】。海瑟音施放攻击后消耗【暖流】为自身恢复 #4 点能量。<b>本场战斗中，海瑟音造成的伤害提高 #1%</b>，
施放普攻/战技攻击敌方目标后，使受到攻击的敌方目标当前承受的所有持续伤害立即产生相当于原伤害 #2%/#3% 的伤害。」

The marked sentence is the one that lands here, and it is the SAME shape already shipped twice for this family (1414's ode of romance and 1406's ode of trickery): a permanent
`ALL_DAMAGE_TYPE_BOOST` on the named character, with the share read out of the CAST skill's row (`#1` varies with level, so a literal would be an approximation).

The other two halves were already shipped in an earlier round (「获得【暖流】」 and 「消耗【暖流】恢复 #4 点能量」); the last sentence -- making the target's damage-over-time resolve
immediately -- is registered, with the shipped reader to study named in the note.
"""
import io
import json
import sys

HY = "src/main/resources/characters/1410.json"
SE = "src/main/resources/data/skill_effects.json"
SLOT = 22
MARK = "\u6696\u6d41"

doc = json.load(io.open(HY, encoding="utf-8"))
# ⚠ measured: 1410.json is a LIST, not an object like 1406/1414 -- handle both shapes
rules = doc if isinstance(doc, list) else doc.get("rules", [])
RULE_ID = "memosprite_ode_of_ocean_raises_her_damage"
if any(r.get("id") == RULE_ID for r in rules):
    sys.exit("REFUSING: %s is already there" % RULE_ID)

rules.append({
    "id": RULE_ID,
    "on": "CAST_SETUP",
    "when": ["target == self", "actor is_summon", "from_skill_id == " + str(SLOT)],
    "do": [{
        "op": "MODIFY_ATTR",
        "attribute": "ALL_DAMAGE_TYPE_BOOST",
        "percent_from_cast_param": 0,
        "permanent": True,
        "target": "self",
    }],
    "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 22 \u300c\u732e\u4e88\u300c\u6d77\u6d0b\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 22\uff0cSkillID 1141522\uff09\uff1a"
               "\u300c\u5355\u6b21\u751f\u6548\uff0c\u5bf9\u6d77\u745f\u97f3\u65bd\u653e\u65f6\uff0c\u4f7f\u6d77\u745f\u97f3\u83b7\u5f97\u3010\u6696\u6d41\u3011\u3002\u2026"
               "**\u672c\u573a\u6218\u6597\u4e2d\uff0c\u6d77\u745f\u97f3\u9020\u6210\u7684\u4f24\u5bb3\u63d0\u9ad8 #1%**\u2026\u300d"),
    "note": ("\u2b50 \u5f62\u72b6\u7167 1414 \u7684\u6d6a\u6f2b\u4e4b\u8bd7\u4e0e 1406 \u7684\u8be1\u8ba1\u4e4b\u8bd7\uff08**\u540c\u4e00\u5c5e\u6027\u3001\u540c\u4e00\u76ee\u6807\u3001\u6c38\u4e45**\uff09\u3002"
             "\u2b50 **\u5b9e\u6d4b**\uff1a`#1` \u968f\u7b49\u7ea7\u53d8\uff080.6 \u2192 0.72\uff09\u21d2 `percent_from_cast_param: 0`\uff08\u65bd\u653e\u6280\u80fd\u5c31\u662f\u672c\u6761\u8bd7\uff09\u3002"
             "\u26d4 \u540c\u53e5\u6700\u540e\u4e00\u53e5\uff08\u4f7f\u76ee\u6807\u8eab\u4e0a\u7684**\u6301\u7eed\u4f24\u5bb3\u7acb\u5373\u4ea7\u751f**\u76f8\u5f53\u4e8e\u539f\u4f24\u5bb3 `#2%`/`#3%` \u7684\u4f24\u5bb3\uff09**\u4ecd\u767b\u8bb0**\uff0c"
             "\u2605 \u5f62\u72b6\u5df2\u6709\u5148\u4f8b\uff1a`characters/1111.json` \u7684 \u300c\u4f7f\u5176\u5f53\u524d\u627f\u53d7\u7684\u88c2\u4f24\u72b6\u6001**\u7acb\u5373\u4ea7\u751f 1 \u6b21**\u76f8\u5f53\u4e8e\u539f\u4f24\u5bb3 85% \u7684\u4f24\u5bb3\u300d\u3002"),
})
if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(HY, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1410 now carries %s (%d rules)" % (RULE_ID, len(rules)))

effects = json.load(io.open(SE, encoding="utf-8"))
if str(SLOT) in effects.get("11415", {}):
    effects["11415"][str(SLOT)]["note"] = ("\u2b50 2026-10-02\uff1a\u672c\u6761\u6280\u80fd\u5df2\u6709**\u4e09\u534a**\u6210\u53e5"
                                           "\uff08\u3010\u6696\u6d41\u3011\u3001\u6d88\u8017\u56de\u80fd\u3001**\u672c\u573a\u6218\u6597\u4f24\u5bb3\u63d0\u9ad8**\uff09\uff1b"
                                           "\u5269\u4e0b\u201c\u6301\u7eed\u4f24\u5bb3\u7acb\u5373\u4ea7\u751f\u201d\u4e00\u53e5\u4ecd\u767b\u8bb0\u3002")
    io.open(SE, "w", encoding="utf-8", newline="\n").write(json.dumps(effects, ensure_ascii=False, indent=2) + "\n")
    print("ok   skill_effects.json 11415/%d note updated" % SLOT)

JUDGE = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 22 \u300c\u732e\u4e88\u300c\u6d77\u6d0b\u300d\u4e4b\u8bd7\u300d: \u300c\u672c\u573a\u6218\u6597\u4e2d\uff0c\u6d77\u745f\u97f3\u9020\u6210\u7684\u4f24\u5bb3\u63d0\u9ad8 #1%\u300d (2026-10-02).
 *
 * <p>\u2b50 TWO-SIDED without a magic number: the ode takes her boost to exactly the CAST row's own #1 (which runs with level), and a scene without the ode reads a different value.
 * \u26a0 Nothing is replaced -- her table holds the rule under test.
 */
public class OceanOdeDamageTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int HYSILENS = 1410;
    private static final int MONSTER = 1002011;

    @Test
    public void theOdeRaisesHerDamageToTheRowsOwnShare() {
        double without = boost(false);
        double[] with = boostWithExpected();
        System.out.println("[ocean_damage] without the ode = " + without + " ; with it = " + with[0]
                + " (the cast row says " + with[1] + ")");

        Assertions.assertEquals(with[1], with[0], Math.abs(with[1]) * 1e-6,
                "\\u300c\\u672c\\u573a\\u6218\\u6597\\u4e2d\\uff0c\\u6d77\\u745f\\u97f3\\u9020\\u6210\\u7684\\u4f24\\u5bb3\\u63d0\\u9ad8 #1%\\u300d-- and #1 runs with level");
        Assertions.assertNotEquals(without, with[0], 1e-9, "precondition + reading: the ode changes her boost");
    }

    private static double[] boostWithExpected() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character hysilens = CharacterFactory.create(HYSILENS, LEVEL);
        Battle battle = new Battle(List.of(cyrene, hysilens),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        hysilens = battle.characters.get(1);

        var demiurge = battle.summonServant(cyrene);
        battle.processRequests();
        var ode = demiurge.skillAt(22);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries slot 22");
        double expected = ode.getData().getSkills().get(demiurge.skillLevel(ode) - 1).get(0);
        SkillExecutor.execute(battle, ode, demiurge, List.of(hysilens));
        battle.processRequests();
        return new double[]{hysilens.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), expected};
    }

    /** Her boost with the ode never cast. */
    private static double boost(boolean ignored) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character hysilens = CharacterFactory.create(HYSILENS, LEVEL);
        Battle battle = new Battle(List.of(cyrene, hysilens),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        battle.summonServant(battle.characters.get(0));
        battle.processRequests();
        return battle.characters.get(1).getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/OceanOdeDamageTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   judge written")
