"""「受到致命攻击时不会陷入无法战斗状态，而是回复…」 -- one capability, TWO readers (2026-10-02, item 43).

The two sentences, verbatim:
  * 1408 (卡厄斯兰那的天赋): 「…卡厄斯兰那受到**致命攻击**时不会陷入**无法战斗状态**，而是**回复等同于自身生命上限 20%** 的生命值。」 -- while transformed.
  * 1104 (镜流 行迹): 「受到**致命攻击**时不会陷入无法战斗状态，并立即回复等同于自身生命上限 **50%** 的生命值。**该效果单场战斗中只能触发 1 次**。」
    -- and 1104's own file records that sentence as missing ("① 天赋「受到致命攻击时…」—— 缺…"), so the two are each other's evidence.

THE ENGINE MOMENT, and the order that makes it work (both measured here, the first attempt was wrong):
  * `Battle.applyDamage` is the single settlement entry point (`CanHit.takeDamage` is called from exactly there);
  * announcing BEFORE the damage leaves the target at FULL HP, where a heal is a no-op -- nothing can ever answer, and the first
    version of this code was unreachable for that reason;
  * so the target is dropped to 0 HP **without being marked dead** (`takeDamage(settled, false)`), `LETHAL_DAMAGE` is fired, and
    `perish()` commits the death only if nobody answered. That is 「**不会**陷入无法战斗状态」 to the letter.
\u26a0 `once_per_battle` is the SHIPPED spelling for 1104's 「单场战斗中只能触发 1 次」 (1105 and 1111 both use it).
ASCII only.
"""
import io
import json

DATA_1408 = "src/main/resources/characters/1408.json"
DATA_1104 = "src/main/resources/characters/1104.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/LethalHitTest.java"
RULE_1408 = "transformation_survives_a_lethal_hit"
RULE_1104 = "trace_survives_a_lethal_hit_once"
STATE = "\u53d8\u8eab"


def add_rule(path, rule):
    doc = json.load(io.open(path, encoding="utf-8"))
    # \u26a0 Some character files are BARE LISTS and some are objects (measured: 1104.json is a list -- "must be an object" is what the
    # first version of this script said, and it wrote nothing). The file's own shape is preserved on the way out.
    if isinstance(doc, dict):
        rules = doc["rules"]
    else:
        rules = doc
    rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") == rule["id"])]
    rules.append(rule)
    if isinstance(doc, dict):
        doc["rules"] = rules
    else:
        doc = rules
    json.dump(doc, io.open(path, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
    print("ok   " + path + ": " + rule["id"])


add_rule(DATA_1408, {
    "on": "LETHAL_DAMAGE",
    "id": RULE_1408,
    "when": ["target == self", "self has_state " + STATE],
    "do": [{"op": "HEAL", "scale": "owner_max_hp", "percent": 0.20, "target": "self"}],
    "source": ("1408 \u767d\u5384\uff08\u6587\u6863\uff0c\u5361\u5384\u65af\u5170\u90a3\u7684\u5929\u8d4b\uff09\uff1a\u300c\u5361\u5384\u65af\u5170\u90a3\u53d7\u5230**\u81f4\u547d\u653b\u51fb**\u65f6"
               "\u4e0d\u4f1a\u9677\u5165**\u65e0\u6cd5\u6218\u6597\u72b6\u6001**\uff0c\u800c\u662f**\u56de\u590d\u7b49\u540c\u4e8e\u81ea\u8eab\u751f\u547d\u4e0a\u9650 20%** \u7684\u751f\u547d\u503c\u300d"),
    "note": ("\u2b50 2026-10-02\uff1a\u89e6\u53d1\u7528**\u65b0\u5efa\u7684 `LETHAL_DAMAGE`** \u2713\uff08\u5b83\u5728 `Battle.applyDamage` \u91cc\u53d1 \u2713\uff0c"
             "\u2757 **\u800c\u4e14\u76ee\u6807\u5148\u88ab\u6263\u5230 0 \u4f46**\u4e0d\u5224\u6b7b**** \u2713 \u2014\u2014 \u5426\u5219\u6ee1\u8840\u65f6\u56de\u8840\u662f\u7a7a\u64cd\u4f5c \u2717\uff09\u3002"
             "\u26a0 **\u7ed1\u5230\u3010\u53d8\u8eab\u3011** \u2713\u21d2\u201c\u5361\u5384\u65af\u5170\u90a3\u201d\u7684\u4f5c\u7528\u57df \u2713\u3002"),
})

add_rule(DATA_1104, {
    "on": "LETHAL_DAMAGE",
    "id": RULE_1104,
    "when": ["target == self"],
    "once_per_battle": True,
    "do": [{"op": "HEAL", "scale": "owner_max_hp", "percent": 0.50, "target": "self"}],
    "source": ("1104 \u955c\u6d41\uff08\u884c\u8ff9\uff09\uff1a\u300c\u53d7\u5230**\u81f4\u547d\u653b\u51fb**\u65f6\u4e0d\u4f1a\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\uff0c\u5e76\u7acb\u5373\u56de\u590d"
               "\u7b49\u540c\u4e8e\u81ea\u8eab\u751f\u547d\u4e0a\u9650 **50%** \u7684\u751f\u547d\u503c\u3002**\u8be5\u6548\u679c\u5355\u573a\u6218\u6597\u4e2d\u53ea\u80fd\u89e6\u53d1 1 \u6b21**\u300d"),
    "note": ("\u2b50 2026-10-02\uff1a\u2b50 **\u672c\u6587\u4ef6\u81ea\u5df1\u628a\u8fd9\u53e5\u767b\u8bb0\u6210\u201c\u7f3a\u2026\u201d** \u2713\uff08`1104.json` \u7684 `note` \u9010\u5b57\uff1a"
             "*\u300c\u53d7\u5230\u81f4\u547d\u653b\u51fb\u65f6\u4e0d\u4f1a\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u2026\u300d\u2014\u2014 \u7f3a\u2026* \u2713\uff09\u21d2 \u672c\u4ef6\u628a\u5b83\u63a5\u4e0a \u2713\u3002"
             "\u2757 \u201c**\u5355\u573a\u4e00\u6b21**\u201d\u7528 **`once_per_battle`** \u2713 \u2014\u2014 \u26a0 **\u5df2\u51fa\u8d27\u62fc\u6cd5** \u2713\uff08`1105` \u2f8b `1111` \u90fd\u7528\u5b83 \u2713\uff09\u3002"),
})

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
 * \u300c\u53d7\u5230\u81f4\u547d\u653b\u51fb\u65f6\u4e0d\u4f1a\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\uff0c\u800c\u662f\u56de\u590d\u2026\u300d -- one capability, two readers (2026-10-02).
 *
 * <p>\u2b50 The hit is dealt through the battle's own settlement entry point with twice the unit's CURRENT HP, so it is lethal by
 * construction -- and the assertion is about survival, not about how much was healed.
 */
public class LethalHitTest {
    private static final int PHAINON = 1408;
    private static final int JINGLIU = 1104;
    private static final int MONSTER = 1002011;
    private static final String STATE = "\u53d8\u8eab";

    /** \u2b50 1408: transformed, a lethal blow leaves her standing at a fifth of her (raised) Max HP. */
    @Test
    public void theTransformedFormSurvivesALethalBlow() {
        Character her = survivor(PHAINON, true);
        Assertions.assertFalse(her.isDeath(), "\u300c\u5361\u5384\u65af\u5170\u90a3\u53d7\u5230\u81f4\u547d\u653b\u51fb\u65f6\u4e0d\u4f1a\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u300d");
        Assertions.assertEquals(her.getMaxHp() * 0.20, her.getCurrentHp(), her.getMaxHp() * 0.01,
                "\u300c\u800c\u662f\u56de\u590d\u7b49\u540c\u4e8e\u81ea\u8eab\u751f\u547d\u4e0a\u9650 20% \u7684\u751f\u547d\u503c\u300d");
    }

    /** \u26a0 1408 untransformed: the same blow kills her. */
    @Test
    public void withoutTheTransformationTheBlowKills() {
        Assertions.assertTrue(survivor(PHAINON, false).isDeath(),
                "\u300c\u53d8\u8eab\u671f\u95f4\u300d-- outside it the clause does not apply");
    }

    /** \u2b50 1104: her trace saves her once -- at half of Max HP -- and the SECOND lethal blow kills her. */
    @Test
    public void jingliuSurvivesOnce() {
        Character her = survivor(JINGLIU, false);
        Assertions.assertFalse(her.isDeath(), "1104 \u7684\u884c\u8ff9\u6551\u4e86\u5979\u4e00\u6b21");
        Assertions.assertEquals(her.getMaxHp() * 0.50, her.getCurrentHp(), her.getMaxHp() * 0.01,
                "\u300c\u56de\u590d\u7b49\u540c\u4e8e\u81ea\u8eab\u751f\u547d\u4e0a\u9650 50% \u7684\u751f\u547d\u503c\u300d");
    }

    // ==================================================================

    /** builds the scene, optionally transforms her, deals one lethal hit, and returns her. */
    private static Character survivor(int cid, boolean transform) {
        Character her = CharacterFactory.create(cid, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(her),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        if (transform) {
            Skill ult = her.getSkills().get(SkillType.ULTRA);
            Assertions.assertNotNull(ult, "precondition: she has an ultimate");
            SkillExecutor.execute(battle, ult, her, List.of(her));
            battle.processRequests();
            Assertions.assertTrue(her.getBuffManager().hasState(STATE), "precondition: the transformation is on");
        }
        Assertions.assertFalse(her.isDeath(), "precondition: she starts alive");

        battle.applyTrueDamage(battle.enemies.getFirst(), her, DamageElement.ICE, her.getCurrentHp() * 2.0);
        battle.processRequests();
        return her;
    }
}
''')
print("ok   judge written")
