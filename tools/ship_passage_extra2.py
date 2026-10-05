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
        "times": 1,                                       # \u300c\u989d\u5916\u9020\u6210 #1 \u6b21\u300d-- #1 is 1 at EVERY level
        "scale": "original_damage",                       # \u2b50 ONE MORE INSTANCE OF THAT SAME DAMAGE (the engine's own spelling for it)
        "percent": 1.0,
        "element": "Quantum",
        "target": "target",
    }],
    "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 14 \u300c\u732e\u4e88\u300c\u95e8\u5f84\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 15\uff0cSkillID 1141515\uff09\uff1a"
               "\u300c**\u7f07\u5b9d\u65bd\u653e\u8ffd\u52a0\u653b\u51fb\u89e6\u53d1\u7f07\u5b9d\u7684\u7ed3\u754c\u7684\u9644\u52a0\u4f24\u5bb3\u65f6\uff0c\u4f1a\u989d\u5916\u9020\u6210 #1 \u6b21\u9644\u52a0\u4f24\u5bb3**\u3002\u300d"),
    "note": ("\u2b50 \u4e09\u4ef6\u662f\u672c\u8f6e\uff0f\u8fd1\u51e0\u8f6e\u51fa\u8d27\u7684\uff1a`SkillCategory.FOLLOW_UP`\u3001`cast_category`\u3001`damage_is_follow_up`\u3002"
             "\u2b50 \u91cf\u7684\u62fc\u6cd5\u662f `original_damage`\uff08**\u540c\u4e00\u7b14**\uff09\u2014\u2014 \u26a0 \u7b2c\u4e00\u7248\u5199\u6210 `owner_max_hp` + `percent_from_skill_param: \"ULTRA:2\"`\uff0c"
             "\u5b9e\u6d4b\u53ea\u6709 `1.2479250335691177`\uff08\u800c\u7ed3\u754c\u81ea\u5df1\u90a3\u7b14 \u2248 68\uff09\uff0c\u4e0e\u539f\u53e5\u4e0d\u7b26\uff0c\u5df2\u56de\u6eda\u3002"
             "\u2b50 \u4e8b\u4ef6\u5fc5\u987b\u662f `DAMAGE_SETTLED`\uff08\u53ea\u6709\u5b83\u5e26 `ctx.damage()`\uff1b\u6302 `FOLLOW_UP` \u4f1a\u88ab\u52a0\u8f7d\u5668\u62d2\u7edd\uff09\u3002"
             "\u2b50 \u800c\u5b83**\u4e0d\u4f1a**\u9012\u5f52\uff1a\u5b83\u9020\u7684\u90a3\u7b14\u5b9e\u4f8b\u4e0d\u58f0\u660e\u7c7b\u522b\uff0c`damage_is_follow_up` \u5bf9\u5b83\u4e3a\u5047\u3002"),
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
 * <p>⭐ The reading is the SIZE of the extra instance, not merely that something happened: 「额外造成 #1 次附加伤害」 is one more instance of THAT damage, so the delta must
 * be of the zone rider's own order (~#3 x Max HP), not a token amount. \u26a0 The first version of this clause measured 1.2479250335691177 and was rolled back for it.
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
