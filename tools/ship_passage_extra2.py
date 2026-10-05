"""1415's ode of passage, second sentence -- with the magnitude stated as "the same instance" (2026-10-02).

Verbatim: 「…**缇宝施放追加攻击触发缇宝的结界的附加伤害时，会额外造成 #1 次附加伤害。**」 #1 is 1 at every level of 11415/15.

⚠ The first attempt stated the magnitude as `owner_max_hp` + `percent_from_skill_param: "ULTRA:2"` and MEASURED 1.2479250335691177 where the zone's own instance is
~68 -- the sentence says one more instance OF THAT DAMAGE, so that was wrong and it was rolled back. `original_damage` is the engine's own spelling for it, and its
comment records the whole dimension lesson (divide by the triggering instance's own factor, or the zones get multiplied twice; skip the division for TRUE damage).

The event stays `DAMAGE_SETTLED`: that is where `ctx.damage()` exists (on `FOLLOW_UP` it is null and the loader refuses an instance question), and it is the event the
zone rider's own instance settles on. ⭐ The clause cannot re-trigger itself, because the instance IT deals states no `cast_category`.
"""
import io
import json
import sys

TRIBBIE = "src/main/resources/characters/1403.json"
SKILLS = "src/main/resources/data/skills.json"

doc = json.load(io.open(TRIBBIE, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
RULE_ID = "ode_of_passage_extra_instance"
if any(r.get("id") == RULE_ID for r in rules):
    sys.exit("REFUSING: %s is already there" % RULE_ID)

follow = [r for r in rules if r.get("id") == "talent_followup_on_other_ult"]
if len(follow) != 1 or not any(e.get("cast_category") == "FOLLOW_UP" for e in follow[0].get("do", [])):
    sys.exit("REFUSING: his follow-up does not declare cast_category FOLLOW_UP")

odes = [r for r in rules if r.get("id") == "memosprite_ode_of_passage_makes_his_damage_ignore_defence"]
marks = [e.get("buff") for e in odes[0].get("do", []) if e.get("op") == "APPLY_BUFF" and e.get("buff")]
if len(marks) != 1:
    sys.exit("REFUSING: the first clause applies no single mark")
ODE = marks[0]
print("ok   the ode's mark is present (%d chars), his follow-up declares FOLLOW_UP" % len(ODE))

table = json.load(io.open(SKILLS, encoding="utf-8"))
rows = table["11415"]["15"].get("param_list") or []
if not rows or any(r[0] != 1 for r in rows):
    sys.exit("REFUSING: #1 is not 1 at every level")
print("ok   #1 is 1 at all %d levels" % len(rows))

rules.append({
    "id": RULE_ID,
    "on": "DAMAGE_SETTLED",
    "when": ["actor == self", "damage_is_follow_up", "self has_state " + ODE],
    "do": [{
        "op": "DAMAGE",
        "times": 1,                                       # "额外造成 #1 次"-- #1 is 1 at EVERY level
        "scale": "original_damage",                       # ONE MORE INSTANCE OF THAT SAME DAMAGE (the engine's own spelling for it)
        "percent": 1.0,
        "element": "Quantum",
        "target": "target",
    }],
    "source": ("1415 昔涟 忆灵技能 14 「献予「门径」之诗」（数据槽位 15，SkillID 1141515）："
               "「**缇宝施放追加攻击触发缇宝的结界的附加伤害时，会额外造成 #1 次附加伤害**。」"),
    "note": ("⭐ 三件是本轮／近几轮出货的：`SkillCategory.FOLLOW_UP`、`cast_category`、`damage_is_follow_up`。"
             "⭐ 量的拼法是 `original_damage`（**同一笔**）—— ⚠ 第一版写成 `owner_max_hp` + `percent_from_skill_param: \"ULTRA:2\"`，"
             "实测只有 `1.2479250335691177`（而结界自己那笔 ≈ 68），与原句不符，已回滚。"
             "⭐ 事件必须是 `DAMAGE_SETTLED`（只有它带 `ctx.damage()`；挂 `FOLLOW_UP` 会被加载器拒绝）。"
             "⭐ 而它**不会**递归：它造的那笔实例不声明类别，`damage_is_follow_up` 对它为假。"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(TRIBBIE, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1403 now carries %s (%d rules)" % (RULE_ID, len(rules)))

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
 * 1415's ode of passage, second sentence (2026-10-02): 「缇宝施放追加攻击触发缇宝的结界的附加伤害时，会额外造成 #1 次附加伤害」.
 *
 * <p>⭐ Two scenes that differ by exactly one thing: whether the ode has been cast at him. Nothing is ever replaced -- his own table stays loaded (the `level_convention`
 * trap is recorded in `literalBase`'s comment, sprung three times by judges).
 *
 * <p>⭐ The reading is the SIZE of the extra instance, not merely that something happened: 「额外造成 #1 次附加伤害" is one more instance of THAT damage, so the delta must
 * be of the zone rider's own order (~#3 x Max HP), not a token amount. ⚠ The first version of this clause measured 1.2479250335691177 and was rolled back for it.
 */
public class PassageExtraInstanceTest {
    private static final int LEVEL = 80;
    private static final int TRIBBIE = 1403;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;

    @Test
    public void theOdeAddsOneMoreInstanceOfTheSameDamage() {
        double withOde = damageFromFollowUp(true);
        double withoutOde = damageFromFollowUp(false);
        double delta = withOde - withoutOde;
        System.out.println("[passage_extra] his follow-up with the ode on him = " + withOde
                + " ; without it = " + withoutOde + " ; the extra instance = " + delta);

        Assertions.assertTrue(withoutOde > 0, "precondition: the follow-up lands");
        Assertions.assertTrue(delta > 0, "\\u300c\\u4f1a**\\u989d\\u5916\\u9020\\u6210 #1 \\u6b21**\\u9644\\u52a0\\u4f24\\u5bb3\\u300d");
        Assertions.assertTrue(delta > 10,
                "and it is ONE MORE INSTANCE OF THAT DAMAGE, not a token: " + delta + " must be of the zone rider's own order");
    }

    private static double damageFromFollowUp(boolean castTheOde) {
        Character tribbie = CharacterFactory.create(TRIBBIE, LEVEL);
        Character cyrene = CharacterFactory.create(1415, LEVEL);
        Battle battle = new Battle(List.of(tribbie, cyrene),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        tribbie = battle.characters.getFirst();
        cyrene = battle.characters.get(1);

        tribbie.setCurrentEnergy(tribbie.getMaxEnergy());
        Assertions.assertTrue(battle.castUltra(tribbie, List.of(battle.enemies.getFirst())),
                "precondition: his ultimate opens the zone");
        battle.processRequests();

        if (castTheOde) {
            var demiurge = battle.summonServant(cyrene);
            var ode = demiurge.skillAt(15);
            Assertions.assertNotNull(ode, "precondition: the memosprite carries the ode");
            SkillExecutor.execute(battle, ode, demiurge, List.of(tribbie));
            battle.processRequests();
            Assertions.assertEquals(1, tribbie.getBuffManager().countStates(), "precondition: the ode's mark is on him");
        }

        var enemy = battle.enemies.getFirst();
        double before = enemy.getCurrentHp();
        battle.fireTriggers(TriggerEvent.ULT_CAST, cyrene, enemy, 1, 0);   // his follow-up hangs on another character's ultimate
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/PassageExtraInstanceTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   judge written")
