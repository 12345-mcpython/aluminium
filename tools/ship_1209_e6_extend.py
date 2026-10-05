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

⭐ The event is the shipped `KILL` (fired from `Battle.applyDamage`'s settlement), and the eidolon gate is the rule-level `min_eidolon`
(the engine's own note says a rule-level gate turns the whole rule off, which is exactly right here).
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1209.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/EidolonSixExtendsSoulsteelTest.java"
RULE = "e6_extends_soulsteel_on_kill"
STATE = "智剑连心"

doc = json.load(io.open(DATA, encoding="utf-8"))
# ⚠ Some character files are BARE LISTS (measured: 1104.json and 1209.json) and some are objects (1408.json). The file's own shape
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
    "source": ("1209 彦卿 星魂 6 「自在 / Swift Swoop」：「**消灭敌方目标时**，如果当前持有"
               "【智剑连心】**或**终结技的增益效果，则使**这些**增益效果的持续时间**全部延长 1 回合**」"),
    "note": ("⭐ 2026-10-02：事件用**已出货的 `KILL`** ✓；阀用**规则级 `min_eidolon`** ✓（星魂 6 ✓）。"
             "❗ **本条只接了「或」的**第一支**** ✓（【智剑连心】是**有名**状态 ✓ ⇒ `EXTEND_BUFF{buff: …}` **名实相符** ✓）；"
             "⚠ **第二支（终结技的增益）已登记** ✗：它们是**无名**的 `MODIFY_ATTR` ✗"
             "，按**属性**延长会**连战技的同名属性一并延长** ✗（那是句子没提的 buff ✗）。"),
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
 * 1209：「消灭敌方目标时，如果当前持有【智剑连心】…则使这些增益效果的持续时间全部延长 1 回合」 (2026-10-02).
 *
 * <p>⭐ ONE VARIABLE: the eidolon rank. Everything else -- her skill (which applies 【智剑连心】 for one turn), the kill, the tick --
 * is identical, so the survival of the state after one tick is exactly what eidolon six buys.
 */
public class EidolonSixExtendsSoulsteelTest {
    private static final int YANQING = 1209;
    private static final int MONSTER = 1002011;
    private static final String STATE = "智剑连心";

    /** ⭐ At E6 the kill lengthens it by a turn, so it outlives the tick that would have ended it. */
    @Test
    public void atEidolonSixTheKillLengthensIt() {
        Assertions.assertTrue(survivesTheTick(6), "「使这些增益效果的持续时间全部延长 1 回合」");
    }

    /** ⚠ Below E6 the rule is off, so one tick ends it. */
    @Test
    public void belowEidolonSixItEnds() {
        Assertions.assertFalse(survivesTheTick(0), "星魂 6 才有这一条");
    }

    // ==================================================================

    private static boolean survivesTheTick(int eidolon) {
        Character her = CharacterFactory.create(YANQING, 80, false, null, null, eidolon);
        Battle battle = new Battle(List.of(her),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        // Her skill applies 【智剑连心】 for one turn.
        Skill skill = her.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: she has a skill");
        SkillExecutor.execute(battle, skill, her, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        Assertions.assertTrue(her.getBuffManager().hasState(STATE), "precondition: the skill applied it");

        // ⭐ She lands the kill, so the EVENT's actor is her -- which the rule requires ("actor == self").
        battle.applyTrueDamage(her, battle.enemies.getFirst(), DamageElement.ICE,
                battle.enemies.getFirst().getCurrentHp());
        battle.processRequests();
        Assertions.assertTrue(battle.enemies.getFirst().isDeath(), "precondition: the enemy died");

        // ⚠ The decrement site measured in round 1619: `BuffManager.beforeMove()` -> `processBuffTick`.
        her.getBuffManager().beforeMove();
        her.getBuffManager().afterMove();
        return her.getBuffManager().hasState(STATE);
    }
}
''')
print("ok   judge written")
