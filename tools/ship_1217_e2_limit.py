"""1217: 「该效果单场战斗中可以触发 **2** 次」 -- the trigger limit the shipped rule was missing (2026-10-02, item 52).

Why this is a fix and not a new capability: item 47 shipped the save, and the SAME sentence carries the limit --
  「当藐藐拥有【禳命】时，若我方目标受到致命攻击，不会陷入无法战斗状态，并立即回复等同于其自身生命上限 **50%** 的生命值。
    **该效果单场战斗中可以触发 2 次**。」
`param` read from the data: `eidolons.json -> 1217/2/param = [0.5, 2]` -> #1 = 0.5 (the heal) and #2 = **2** (the count).

\u2b50\u2b50 THE SPELLING ALREADY EXISTS IN THE TREE, and it was named for exactly this sentence family -- `TriggerInterpreter:762`:
  *"「每当我方目标…施放 2 次…后」／「**触发 2 次后自动解除**」: a **counter**, which is a named stack buff (see `StackBuff`)"*
so nothing new is built here: a counter is `ADD_STACK` plus a `self_stacks:<name>` condition, copied from shipped `1111.json`
(`ADD_STACK` at :10, `"self_stacks:斗志 >= 2"` at :69). The counter lives on the rule's OWNER (her), which is what
`self_stacks:` reads.
"""
import io
import json

DATA = "src/main/resources/characters/1217.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/TalismanSavesAnAllyTest.java"
RULE = "e2_saves_an_ally_with_the_talisman"
COUNTER = "\u6551\u4e3b\u8ba1\u6570"          # the battle-long counter: how many times the save has fired
LIMIT = 2

doc = json.load(io.open(DATA, encoding="utf-8"))
if not isinstance(doc, list):
    raise SystemExit("1217.json is expected to be a bare list")
rules = [r for r in doc if isinstance(r, dict) and r.get("id") == RULE]
if len(rules) != 1:
    raise SystemExit("expected exactly one shipped %s rule, found %d" % (RULE, len(rules)))
rule = rules[0]

condition = "self_stacks:%s < %d" % (COUNTER, LIMIT)
if condition in rule["when"]:
    raise SystemExit("the limit is already stated")
rule["when"].append(condition)
rule["do"].append({
    "op": "ADD_STACK",
    "buff": COUNTER,
    "amount": 1,
    "max_stacks": LIMIT,
    "target": "self",
    "permanent": True,
})
rule["source"] = ("1217 \u85ff\u85ff \u661f\u9b42 2 \u300c\u5224\u5b98\u4e66\u7b26\uff0c\u9547\u5c3e\u9501\u7075\u300d\uff1a\u300c\u5f53**\u85ff\u85ff\u62e5\u6709\u3010"
                  "\u79b3\u547d\u3011\u65f6**\uff0c\u82e5**\u6211\u65b9\u76ee\u6807**\u53d7\u5230\u81f4\u547d\u653b\u51fb\uff0c\u4e0d\u4f1a\u9677\u5165**\u65e0\u6cd5\u6218\u6597"
                  "\u72b6\u6001**\uff0c\u5e76\u7acb\u5373\u56de\u590d\u7b49\u540c\u4e8e**\u5176\u81ea\u8eab**\u751f\u547d\u4e0a\u9650 **50%** \u7684\u751f\u547d\u503c\u3002"
                  "**\u8be5\u6548\u679c\u5355\u573a\u6218\u6597\u4e2d\u53ef\u4ee5\u89e6\u53d1 2 \u6b21**\u300d\uff08`param = [0.5, 2]` \u21d2 #1 = 50% \u2713\uff0c#2 = 2 \u6b21 \u2713\uff09")
rule["note"] = ("\u2b50 2026-10-02\uff08\u7b2c 52 \u4ef6\uff09\uff1a\u2b50 **\u8865\u4e0a\u540c\u4e00\u53e5\u91cc\u7684\u4e0a\u9650** \u2713\u3002"
                "\u26a0 \u4e0a\u9650\u5c31\u5728\u539f\u53e5\u91cc\uff08\u300c\u8be5\u6548\u679c\u5355\u573a\u6218\u6597\u4e2d\u53ef\u4ee5\u89e6\u53d1 **2** \u6b21\u300d\uff09\uff0c"
                "\u800c `param` \u7684 #2 = **2** \u2713\u3002\u2b50 **\u62fc\u6cd5\u662f\u73b0\u6210\u7684** \u2713\uff1a\u8ba1\u6570\u5668 = `ADD_STACK` + "
                "`self_stacks:<\u540d>` \u6761\u4ef6\uff08\u6837\u677f `1111.json` \u7684\u3010\u6597\u5fd7\u3011\u2713\uff1b\u800c `TriggerInterpreter:762` \u7684\u6ce8\u91ca"
                "**\u70b9\u540d\u4e86\u8fd9\u4e00\u53e5**\uff1a\u300c\u89e6\u53d1 2 \u6b21\u540e\u81ea\u52a8\u89e3\u9664\u300d\u21d2 \u8ba1\u6570\u5668 \u2713\uff09\u3002"
                "\u26a0 \u8ba1\u6570\u5668\u5199\u5728**\u89c4\u5219\u4e3b\u4eba\u81ea\u5df1**\u8eab\u4e0a \u2713\uff08`self_stacks:` \u8bfb\u7684\u5c31\u662f\u89c4\u5219\u4e3b\u4eba \u2713\uff09\u3002"
                "\u26a0 **\u65e0\u9700\u5f15\u64ce\u6539\u52a8** \u2713\uff08\u672c\u6765\u4ee5\u4e3a\u8981\u52a0 `per_battle` \u5b57\u6bb5 \u2717\uff0c\u5df2\u56de\u6eda \u2713\uff09\u3002"
                "\u26a0 **\u4ecd\u767b\u8bb0**\uff1a\u300c\u4f7f\u3010\u79b3\u547d\u3011\u7684\u6301\u7eed\u56de\u5408\u6570**\u51cf 1**\u300d\u2717\uff08`EXTEND_BUFF` \u53ea\u6536\u6b63\u6570 \u2717\uff09\u3002")

json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1217.json: the e2 save now fires at most twice per battle")

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
 * 1217\uff1a\u300c\u5f53\u85ff\u85ff\u62e5\u6709\u3010\u79b3\u547d\u3011\u65f6\uff0c\u82e5\u6211\u65b9\u76ee\u6807\u53d7\u5230\u81f4\u547d\u653b\u51fb\u2026\u7acb\u5373\u56de\u590d\u7b49\u540c\u4e8e\u5176\u81ea\u8eab\u751f\u547d\u4e0a\u9650 50% \u7684\u751f\u547d\u503c\u3002
 * \u8be5\u6548\u679c\u5355\u573a\u6218\u6597\u4e2d\u53ef\u4ee5\u89e6\u53d1 2 \u6b21\u300d (2026-10-02).
 *
 * <p>\u2b50 ONE VARIABLE per test: the eidolon rank, or the NUMBER OF LETHAL BLOWS. Same party, same skill (which is what puts
 * \u3010\u79b3\u547d\u3011 on her), same blow.
 */
public class TalismanSavesAnAllyTest {
    private static final int HUOHUO = 1217;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String STATE = "\u79b3\u547d";

    /** \u2b50 At E2 the ally survives, at HALF OF ITS OWN Max HP. */
    @Test
    public void atEidolonTwoTheAllySurvives() {
        double[] result = afterLethalBlows(2, 1);
        Assertions.assertTrue(result[0] > 0, "\u300c\u4e0d\u4f1a\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u300d (hp " + result[0] + ")");
        Assertions.assertEquals(result[1] * 0.50, result[0], result[1] * 0.01,
                "\u300c\u56de\u590d\u7b49\u540c\u4e8e**\u5176\u81ea\u8eab**\u751f\u547d\u4e0a\u9650 50%\u300d-- the VICTIM\u2019s own, not the healer\u2019s");
    }

    /** \u26a0 Below E2 the ally falls. */
    @Test
    public void belowEidolonTwoTheAllyFalls() {
        Assertions.assertTrue(afterLethalBlows(0, 1)[0] <= 0, "\u661f\u9b42 2 \u624d\u6709\u8fd9\u4e00\u6761");
    }

    /**
     * \u2b50\u2b50 \u300c\u8be5\u6548\u679c\u5355\u573a\u6218\u6597\u4e2d\u53ef\u4ee5\u89e6\u53d1 **2** \u6b21\u300d: the first two blows are answered, the third is not.
     *
     * <p>\u2b50 The count is a shipped spelling, not a new capability: a counter is `ADD_STACK` plus a `self_stacks:` condition
     * (sample: 1111's \u3010\u6597\u5fd7\u3011). \u26a0 The two answered blows each leave her ALLY at half of ITS OWN Max HP, so the third
     * blow is lethal again -- the assertion is about the count, not about a first-blow-only effect.
     */
    @Test
    public void theLimitIsTwoBlowsInOneBattle() {
        double[] one = afterLethalBlows(2, 1);
        double[] two = afterLethalBlows(2, 2);
        double[] three = afterLethalBlows(2, 3);
        Assertions.assertTrue(one[0] > 0, "\u7b2c\u4e00\u6b21\u88ab\u6551");
        Assertions.assertTrue(two[0] > 0, "\u7b2c\u4e8c\u6b21\u4ecd\u88ab\u6551\uff08\u300c\u53ef\u4ee5\u89e6\u53d1 2 \u6b21\u300d\uff09");
        Assertions.assertTrue(three[0] <= 0,
                "\u7b2c\u4e09\u6b21**\u4e0d\u518d**\u88ab\u6551\uff08\u300c\u53ef\u4ee5\u89e6\u53d1 2 \u6b21\u300d\u7684\u4e0a\u9650\uff09");
    }

    // ==================================================================

    /** { the ally's HP after the blows, the ally's Max HP }. */
    private static double[] afterLethalBlows(int eidolon, int blows) {
        Character her = CharacterFactory.create(HUOHUO, 80, false, null, null, eidolon);
        Character ally = CharacterFactory.create(ALLY, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(her, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        // Her skill is what puts \u3010\u79b3\u547d\u3011 on her (2 turns, ticking on her own turns).
        Skill skill = her.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: she has a skill");
        SkillExecutor.execute(battle, skill, her, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        Assertions.assertTrue(her.getBuffManager().hasState(STATE), "precondition: \u3010\u79b3\u547d\u3011 is on her");

        for (int blow = 0; blow < blows; blow++) {
            if (ally.isDeath()) {
                break;
            }
            battle.applyTrueDamage(battle.enemies.getFirst(), ally, DamageElement.ICE, ally.getCurrentHp() * 2.0);
            battle.processRequests();
        }
        return new double[]{ally.isDeath() ? 0 : ally.getCurrentHp(), ally.getMaxHp()};
    }
}
''')
print("ok   judge rewritten: the original two assertions, plus the third blow")
