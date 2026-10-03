"""1209: 「消灭敌方目标时，如果当前持有【智剑连心】或终结技的增益效果，则使这些增益效果的持续时间全部延长 1 回合」 -- the first arm
(2026-10-02, item 45).

Document, verbatim (1209 彦卿 星魂 6 「自在 / Swift Swoop」): 「**消灭敌方目标时**，如果当前持有【智剑连心】**或**终结技的增益效果，则使**这些**增益
效果的持续时间**全部延长 1 回合**。」

WHY THE `或` HAS TWO ARMS AND ONLY ONE SHIPS HERE:
  * 【智剑连心】 is a NAMED state (1209's own skill applies it: `APPLY_BUFF{智剑连心, turns: 1}`), so `EXTEND_BUFF{buff: "智剑连心"}` names
    exactly what the sentence names -- no over-reach, nothing invented;
  * 「终结技的增益效果」 are the ultimate's two `MODIFY_ATTR` modifiers (`CRIT_CHANCE` 0.6 and `CRIT_ATTACK` 0.5), and they carry NO name.
    Extending "by attribute" would also lengthen the SKILL's modifiers on those same attributes (0.2 / 0.3) -- a buff the sentence
    never mentions -- and giving them a name is a content decision this span does not take on its own. So that arm is REGISTERED,
    with its prerequisite written down: the ultimate's modifiers need a name before they can be addressed individually.

\u2b50 The event is the shipped `KILL` (fired from `Battle.applyDamage`'s settlement), and the eidolon gate is the rule-level `min_eidolon`
(the engine's own note says a rule-level gate turns the whole rule off, which is exactly right here).
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1209.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/EidolonSixExtendsSoulsteelTest.java"
RULE = "e6_extends_soulsteel_on_kill"
STATE = "\u667a\u5251\u8fde\u5fc3"

doc = json.load(io.open(DATA, encoding="utf-8"))
# \u26a0 Some character files are BARE LISTS (measured: 1104.json and 1209.json) and some are objects (1408.json). The file's own shape
# is preserved on the way out -- the first version of the 1104 script said "must be an object" and wrote nothing.
isObject = isinstance(doc, dict)
rules = doc["rules"] if isObject else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") == RULE)]

if len(rules) != 6:
    raise SystemExit("expected the six shipped 1209 rules, found " + str(len(rules)))

rules.append({
    "on": "KILL",
    "id": RULE,
    "min_eidolon": 6,
    "when": ["actor == self", "self has_state " + STATE],
    "do": [{"op": "EXTEND_BUFF", "buff": STATE, "turns": 1, "target": "self"}],
    "source": ("1209 \u5f66\u537f \u661f\u9b42 6 \u300c\u81ea\u5728 / Swift Swoop\u300d\uff1a\u300c**\u6d88\u706d\u654c\u65b9\u76ee\u6807\u65f6**\uff0c\u5982\u679c\u5f53\u524d\u6301\u6709"
               "\u3010\u667a\u5251\u8fde\u5fc3\u3011**\u6216**\u7ec8\u7ed3\u6280\u7684\u589e\u76ca\u6548\u679c\uff0c\u5219\u4f7f**\u8fd9\u4e9b**\u589e\u76ca\u6548\u679c\u7684\u6301\u7eed\u65f6\u95f4**\u5168\u90e8\u5ef6\u957f 1 \u56de\u5408**\u300d"),
    "note": ("\u2b50 2026-10-02\uff1a\u4e8b\u4ef6\u7528**\u5df2\u51fa\u8d27\u7684 `KILL`** \u2713\uff1b\u9600\u7528**\u89c4\u5219\u7ea7 `min_eidolon`** \u2713\uff08\u661f\u9b42 6 \u2713\uff09\u3002"
             "\u2757 **\u672c\u6761\u53ea\u63a5\u4e86\u300c\u6216\u300d\u7684**\u7b2c\u4e00\u652f**** \u2713\uff08\u3010\u667a\u5251\u8fde\u5fc3\u3011\u662f**\u6709\u540d**\u72b6\u6001 \u2713 \u21d2 `EXTEND_BUFF{buff: \u2026}` **\u540d\u5b9e\u76f8\u7b26** \u2713\uff09\uff1b"
             "\u26a0 **\u7b2c\u4e8c\u652f\uff08\u7ec8\u7ed3\u6280\u7684\u589e\u76ca\uff09\u5df2\u767b\u8bb0** \u2717\uff1a\u5b83\u4eec\u662f**\u65e0\u540d**\u7684 `MODIFY_ATTR` \u2717"
             "\uff0c\u6309**\u5c5e\u6027**\u5ef6\u957f\u4f1a**\u8fde\u6218\u6280\u7684\u540c\u540d\u5c5e\u6027\u4e00\u5e76\u5ef6\u957f** \u2717\uff08\u90a3\u662f\u53e5\u5b50\u6ca1\u63d0\u7684 buff \u2717\uff09\u3002"),
})

if isObject:
    doc["rules"] = rules
else:
    doc = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1209.json: eidolon six extends the soulsteel on a kill")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1209\uff1a\u300c\u6d88\u706d\u654c\u65b9\u76ee\u6807\u65f6\uff0c\u5982\u679c\u5f53\u524d\u6301\u6709\u3010\u667a\u5251\u8fde\u5fc3\u3011\u2026\u5219\u4f7f\u8fd9\u4e9b\u589e\u76ca\u6548\u679c\u7684\u6301\u7eed\u65f6\u95f4\u5168\u90e8\u5ef6\u957f 1 \u56de\u5408\u300d (2026-10-02).
 *
 * <p>\u2b50 ONE VARIABLE: the eidolon rank. Everything else -- her skill (which applies \u3010\u667a\u5251\u8fde\u5fc3\u3011 for one turn), the kill, the tick --
 * is identical, so the survival of the state after one tick is exactly what eidolon six buys.
 */
public class EidolonSixExtendsSoulsteelTest {
    private static final int YANQING = 1209;
    private static final int MONSTER = 1002011;
    private static final String STATE = "\u667a\u5251\u8fde\u5fc3";

    /** \u2b50 At E6 the kill lengthens it by a turn, so it outlives the tick that would have ended it. */
    @Test
    public void atEidolonSixTheKillLengthensIt() {
        Assertions.assertTrue(survivesTheTick(6), "\u300c\u4f7f\u8fd9\u4e9b\u589e\u76ca\u6548\u679c\u7684\u6301\u7eed\u65f6\u95f4\u5168\u90e8\u5ef6\u957f 1 \u56de\u5408\u300d");
    }

    /** \u26a0 Below E6 the rule is off, so one tick ends it. */
    @Test
    public void belowEidolonSixItEnds() {
        Assertions.assertFalse(survivesTheTick(0), "\u661f\u9b42 6 \u624d\u6709\u8fd9\u4e00\u6761");
    }

    // ==================================================================

    private static boolean survivesTheTick(int eidolon) {
        Character her = CharacterFactory.create(YANQING, 80, false, null, null, eidolon);
        Battle battle = new Battle(List.of(her),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        // Her skill applies \u3010\u667a\u5251\u8fde\u5fc3\u3011 for one turn.
        Skill skill = her.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: she has a skill");
        SkillExecutor.execute(battle, skill, her, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        Assertions.assertTrue(her.getBuffManager().hasState(STATE), "precondition: the skill applied it");

        // \u2b50 She lands the kill, so the EVENT's actor is her -- which the rule requires ("actor == self").
        battle.applyTrueDamage(her, battle.enemies.getFirst(), DamageElement.ICE,
                battle.enemies.getFirst().getCurrentHp());
        battle.processRequests();
        Assertions.assertTrue(battle.enemies.getFirst().isDeath(), "precondition: the enemy died");

        // \u26a0 The decrement site measured in round 1619: `BuffManager.beforeMove()` -> `processBuffTick`.
        her.getBuffManager().beforeMove();
        her.getBuffManager().afterMove();
        return her.getBuffManager().hasState(STATE);
    }
}
''')
print("ok   judge written")
