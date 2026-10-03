"""1217: 「当藿藿拥有【禳命】时，若我方目标受到致命攻击，不会陷入无法战斗状态，并立即回复等同于其自身生命上限 50% 的生命值」 -- item 47.

Document, verbatim (1217 藿藿 星魂 2 「判官书符，镇尾锁灵」): 「当**藿藿拥有【禳命】时**，若**我方目标**受到致命攻击，不会陷入**无法战斗状态**，并立即回复等同于
**其自身**生命上限 **50%** 的生命值，**使【禳命】的持续回合数减 1**。」

\u2b50 FOURTH reader of `LETHAL_DAMAGE`, and the FIRST one that heals by the VICTIM's Max HP rather than the healer's: 「其自身」 is the
unit that would have fallen, which is `scale: "target_max_hp"` (a shipped spelling -- `1001.json` uses it). 1211 (item 46) was the
mirror image: 「等同于**白露**…」, i.e. the healer's.

\u26a0 「我方目标」 includes 藿藿 herself, so unlike 1211 there is no `!= self` here.
\u26a0 REGISTERED, not shipped, from the same sentence: 「使【禳命】的持续回合数**减 1**」. `EXTEND_BUFF` requires POSITIVE turns
(`requirePositiveTurns`), so "take a turn off" has no spelling today -- and 【禳命】's own rule says its clock is 「藿藿每回合开始时…减 1」
(ticks_on self, 2 turns), so the reduction would have to go through that same duration bookkeeping.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1217.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/TalismanSavesAnAllyTest.java"
RULE = "e2_saves_an_ally_with_the_talisman"
STATE = "\u79b3\u547d"

doc = json.load(io.open(DATA, encoding="utf-8"))
# \u26a0 1217.json is a BARE LIST (like 1104 / 1209 / 1211); the file's own shape is preserved on the way out.
isObject = isinstance(doc, dict)
rules = doc["rules"] if isObject else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") == RULE)]

if len(rules) != 6:
    raise SystemExit("expected the six shipped 1217 rules, found " + str(len(rules)))

rules.append({
    "on": "LETHAL_DAMAGE",
    "id": RULE,
    "min_eidolon": 2,
    "when": ["actor is_ally", "self has_state " + STATE],
    "do": [{"op": "HEAL", "scale": "target_max_hp", "percent": 0.50, "target": "target"}],
    "source": ("1217 \u85ff\u85ff \u661f\u9b42 2 \u300c\u5224\u5b98\u4e66\u7b26\uff0c\u9547\u5c3e\u9501\u7075\u300d\uff1a\u300c\u5f53**\u85ff\u85ff\u62e5\u6709\u3010\u79b3\u547d\u3011\u65f6**\uff0c"
               "\u82e5**\u6211\u65b9\u76ee\u6807**\u53d7\u5230\u81f4\u547d\u653b\u51fb\uff0c\u4e0d\u4f1a\u9677\u5165**\u65e0\u6cd5\u6218\u6597\u72b6\u6001**\uff0c\u5e76\u7acb\u5373\u56de\u590d\u7b49\u540c\u4e8e"
               "**\u5176\u81ea\u8eab**\u751f\u547d\u4e0a\u9650 **50%** \u7684\u751f\u547d\u503c\u300d"),
    "note": ("\u2b50 2026-10-02\uff1a\u2b50 **`LETHAL_DAMAGE` \u7684\u7b2c\u56db\u4f4d\u8bfb\u8005** \u2713\uff0c\u800c\u4e14\u662f**\u7b2c\u4e00\u4f4d\u6309\u88ab\u5bb3\u8005\u751f\u547d\u4e0a\u9650\u6cbb\u7597\u7684** \u2713"
             "\uff08\u300c**\u5176\u81ea\u8eab**\u300d\u21d2 `scale: \"target_max_hp\"` \u2713 \u2014\u2014 \u5df2\u51fa\u8d27\u62fc\u6cd5\uff0c`1001.json` \u7528\u5b83 \u2713\uff09\uff1b"
             "\u2f8b\u7b2c 46 \u4ef6\uff081211\uff09**\u6b63\u597d\u76f8\u53cd** \u2713\uff08\u90a3\u91cc\u662f\u300c\u7b49\u540c\u4e8e**\u767d\u9732**\u300d\u21d2 \u6cbb\u7597\u8005\u7684\u4e0a\u9650 \u2713\uff09\u3002"
             "\u26a0 **\u201c\u6211\u65b9\u76ee\u6807\u201d\u5305\u62ec\u85ff\u85ff\u81ea\u5df1** \u2713 \u21d2 **\u6ca1\u6709** `!= self` \u2713\uff08\u4e0e 1211 \u4e0d\u540c \u2713\uff09\u3002"
             "\u26a0 **\u4ecd\u767b\u8bb0**\uff1a\u201c\u4f7f\u3010\u79b3\u547d\u3011\u7684\u6301\u7eed\u56de\u5408\u6570**\u51cf 1**\u201d \u2717\uff08`EXTEND_BUFF` **\u53ea\u6536\u6b63\u6570** \u2717\uff09\u3002"),
})

if isObject:
    doc["rules"] = rules
else:
    doc = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1217.json: the talisman saves an ally who takes a lethal blow")

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
 * 1217\uff1a\u300c\u5f53\u85ff\u85ff\u62e5\u6709\u3010\u79b3\u547d\u3011\u65f6\uff0c\u82e5\u6211\u65b9\u76ee\u6807\u53d7\u5230\u81f4\u547d\u653b\u51fb\u2026\u7acb\u5373\u56de\u590d\u7b49\u540c\u4e8e\u5176\u81ea\u8eab\u751f\u547d\u4e0a\u9650 50% \u7684\u751f\u547d\u503c\u300d (2026-10-02).
 *
 * <p>\u2b50 ONE VARIABLE: the eidolon rank. Same party, same skill (which is what puts \u3010\u79b3\u547d\u3011 on her), same lethal blow.
 */
public class TalismanSavesAnAllyTest {
    private static final int HUOHUO = 1217;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String STATE = "\u79b3\u547d";

    /** \u2b50 At E2 the ally survives, at HALF OF ITS OWN Max HP. */
    @Test
    public void atEidolonTwoTheAllySurvives() {
        double[] result = afterLethalBlow(2);
        Assertions.assertTrue(result[0] > 0, "\u300c\u4e0d\u4f1a\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u300d (hp " + result[0] + ")");
        Assertions.assertEquals(result[1] * 0.50, result[0], result[1] * 0.01,
                "\u300c\u56de\u590d\u7b49\u540c\u4e8e**\u5176\u81ea\u8eab**\u751f\u547d\u4e0a\u9650 50%\u300d-- the VICTIM\u2019s own, not the healer\u2019s");
    }

    /** \u26a0 Below E2 the ally falls. */
    @Test
    public void belowEidolonTwoTheAllyFalls() {
        Assertions.assertTrue(afterLethalBlow(0)[0] <= 0, "\u661f\u9b42 2 \u624d\u6709\u8fd9\u4e00\u6761");
    }

    // ==================================================================

    /** { the ally's HP after the blow, the ally's Max HP }. */
    private static double[] afterLethalBlow(int eidolon) {
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

        Battle dummy = battle;
        dummy.applyTrueDamage(battle.enemies.getFirst(), ally, DamageElement.ICE, ally.getCurrentHp() * 2.0);
        battle.processRequests();
        return new double[]{ally.isDeath() ? 0 : ally.getCurrentHp(), ally.getMaxHp()};
    }
}
''')
print("ok   judge written")
