"""1415's memosprite skill 20 「献予「诡计」之诗」 -- the damage half (2026-10-02).

Verbatim (1141520, params [0.18, 0.1, 0.06]): 「整场生效，对赛飞儿施放时，<b>使赛飞儿造成的伤害提高 #1%</b>，并使【老主顾】的防御力降低 #2%，【老主顾】以外的敌方目标的防御力降低 #3%。」

The first clause is the one today's vocabulary carries whole, and its shape is the one 1414's ode of romance already ships for 阿格莱雅: a `MODIFY_ATTR` on
`ALL_DAMAGE_TYPE_BOOST`, permanent, aimed at the rule's owner, with the share read from the CAST skill (the number runs 0.18 -> 0.504).

Measured: 赛飞儿 is cid 1406 in our own data ("Cipher"); #1, #2 and #3 all run with the level.

⛔ Registered: both defence-lowering clauses -- 【老主顾】 is a state our content does not model, and 「【老主顾】**以外**的敌方目标」 needs a NEGATIVE filter that the
`target_when` vocabulary does not have.
"""
import io
import json
import os
import sys

CIPHER = "src/main/resources/characters/1406.json"
SE = "src/main/resources/data/skill_effects.json"
SKILLS = "src/main/resources/data/skills.json"
SLOT = 20

table = json.load(io.open(SKILLS, encoding="utf-8"))
rows = table["11415"][str(SLOT)].get("param_list") or []
if not rows or rows[0][0] == rows[-1][0]:
    sys.exit("REFUSING: #1 does not run with level")
print("ok   #1 runs %s -> %s over %d levels" % (rows[0][0], rows[-1][0], len(rows)))

existed = os.path.exists(CIPHER)
if existed:
    doc = json.load(io.open(CIPHER, encoding="utf-8"))
    rules = doc if isinstance(doc, list) else doc.get("rules", [])
    print("ok   1406.json already exists with %d rules -- appending, not replacing" % len(rules))
else:
    doc, rules = {"rules": []}, []
    print("ok   1406.json does not exist -- creating it")

RULE_ID = "memosprite_ode_of_trickery_raises_her_damage"
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
    "source": ("1415 昔涟 忆灵技能 20 「献予「诡计」之诗」（数据槽位 20，SkillID 1141520）："
               "「整场生效，对赛飞儿施放时，**使赛飞儿造成的伤害提高 #1%**。」"),
    "note": ("⭐ 形状照 1414 的浪漫之诗（同一属性 `ALL_DAMAGE_TYPE_BOOST`，`target: \"self\"`，永久）。"
             "⭐ `#1` 随等级变（**实测**：0.18 → 0.504）→ `percent_from_cast_param: 0`。"
             "⛔ 同句的两句降防**已登记**：【老主顾】我们没建模，"
             "而「【老主顾】**以外**的敌方目标」需要一个**否定过滤**，`target_when` 没有。"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(CIPHER, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1406 now carries %s (%d rules)" % (RULE_ID, len(rules)))

effects = json.load(io.open(SE, encoding="utf-8"))
if str(SLOT) not in effects.get("11415", {}):
    effects.setdefault("11415", {})[str(SLOT)] = {
        "effect": "Rules",
        "source": "1415 昔涟 忆灵技能 20 「献予「诡计」之诗」（数据槽位 20）：工作在规则侧。",
        "note": "⭐ 没有条目就不可交付。",
    }
    io.open(SE, "w", encoding="utf-8", newline="\n").write(json.dumps(effects, ensure_ascii=False, indent=2) + "\n")
    print("ok   skill_effects.json: 11415/%d = Rules" % SLOT)

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
 * 1415's memosprite skill 20 「献予「诡计」之诗」: 「使赛飞儿造成的伤害提高 #1%」 (2026-10-02).
 *
 * <p>⭐ Two scenes that differ by exactly one thing: whether the ode was cast at her. ⚠ Nothing is ever replaced -- the table trap has already deleted a rule under
 * test once in this project.
 */
public class TrickeryOdeDamageTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int CIPHER = 1406;
    private static final int MONSTER = 1002011;

    @Test
    public void theOdeRaisesHerDamage() {
        double withoutOde = damageBoost(false);
        Boost withOde = damageBoost(true);
        System.out.println("[trickery] without the ode = " + withoutOde + " ; with it = " + withOde.gain
                + " (the cast row says " + withOde.expected + ")");

        Assertions.assertEquals(0.0, withoutOde, 1e-9, "precondition: nothing boosts her before the ode");
        Assertions.assertEquals(withOde.expected, withOde.gain, Math.abs(withOde.expected) * 1e-6,
                "\\u300c\\u4f7f\\u8d5b\\u98de\\u513f\\u9020\\u6210\\u7684\\u4f24\\u5bb3\\u63d0\\u9ad8 #1%\\u300d-- and #1 runs with level");
    }

    private record Boost(double gain, double expected) {
    }

    private static Boost damageBoost(boolean castTheOde) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character cipher = CharacterFactory.create(CIPHER, LEVEL);
        Battle battle = new Battle(List.of(cyrene, cipher),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        cipher = battle.characters.get(1);

        double expected = 0.0;
        if (castTheOde) {
            var demiurge = battle.summonServant(cyrene);
            var ode = demiurge.skillAt(20);
            Assertions.assertNotNull(ode, "precondition: the memosprite carries slot 20");
            expected = ode.getData().getSkills().get(demiurge.skillLevel(ode) - 1).get(0);
            SkillExecutor.execute(battle, ode, demiurge, List.of(cipher));
            battle.processRequests();
        }
        return new Boost(cipher.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), expected);
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/TrickeryOdeDamageTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   judge written")
