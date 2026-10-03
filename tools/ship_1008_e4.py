"""1008: 「受到致命攻击时阿兰不会陷入无法战斗状态，并立即回复至自身生命上限的 25%」 (2026-10-02, item 48).

Document, verbatim (1008 阿兰 星魂 4 「绝处反击 / Turn the Tables」): 「**进入战斗后**，受到致命攻击时阿兰不会陷入**无法战斗状态**，并立即**回复至**自身生命上限的
**25%**。」

\u2b50 FIFTH reader of `LETHAL_DAMAGE`, and the first that states a CEILING ("回复**至**") rather than an amount ("回复") -- and here the two
coincide EXACTLY, which is why this ships rather than being registered:
  * `LETHAL_DAMAGE` is announced with the target already at 0 HP (that is what makes the moment answerable at all -- see item 43), and a
    unit at 0 HP that is healed for 25% of its Max HP ends at 25% of its Max HP. "Set to 25%" and "add 25%" are the same instruction from
    that starting point, so no approximation is involved.
  * 「进入战斗后」 is the sentence's own framing for a battle-scoped trace; in a battle it is already true, so it adds no condition.

\u26a0 `1008.json` is a BARE LIST (like 1104 / 1209 / 1211 / 1217); the file's own shape is preserved on the way out.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1008.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/ArlanEidolonFourTest.java"
RULE = "e4_survives_a_lethal_blow_at_a_quarter"

doc = json.load(io.open(DATA, encoding="utf-8"))
isObject = isinstance(doc, dict)
rules = doc["rules"] if isObject else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") == RULE)]

if len(rules) != 3:
    raise SystemExit("expected the three shipped 1008 rules, found " + str(len(rules)))

rules.append({
    "on": "LETHAL_DAMAGE",
    "id": RULE,
    "min_eidolon": 4,
    "when": ["actor == self"],
    "do": [{"op": "HEAL", "scale": "owner_max_hp", "percent": 0.25, "target": "self"}],
    "source": ("1008 \u963f\u5170 \u661f\u9b42 4 \u300c\u7edd\u5904\u53cd\u51fb / Turn the Tables\u300d\uff1a\u300c**\u8fdb\u5165\u6218\u6597\u540e**\uff0c\u53d7\u5230\u81f4\u547d\u653b\u51fb\u65f6"
               "\u963f\u5170\u4e0d\u4f1a\u9677\u5165**\u65e0\u6cd5\u6218\u6597\u72b6\u6001**\uff0c\u5e76\u7acb\u5373**\u56de\u590d\u81f3**\u81ea\u8eab\u751f\u547d\u4e0a\u9650\u7684 **25%**\u300d"),
    "note": ("\u2b50 2026-10-02\uff1a\u2b50 **`LETHAL_DAMAGE` \u7684\u7b2c\u4e94\u4f4d\u8bfb\u8005** \u2713\uff0c\u800c\u4e14\u662f**\u7b2c\u4e00\u4f4d\u8bf4\u7684\u662f\u201c\u56de\u590d**\u81f3**\u201d**\uff08\u4e0a\u9650\uff09"
             "\u800c\u4e0d\u662f\u201c\u56de\u590d\u201d\uff08\u6570\u91cf\uff09\u7684 \u2713\u3002\u2757 **\u800c\u5728\u8fd9\u6761\u8def\u4e0a\u4e24\u8005**\u6070\u597d\u91cd\u5408**** \u2713\uff1a"
             "`LETHAL_DAMAGE` \u53d1\u51fa\u65f6\u76ee\u6807**\u5df2\u7ecf\u5728 0 \u8840** \u2713\uff08\u90a3\u6b63\u662f\u8fd9\u4e2a\u65f6\u523b\u80fd\u88ab\u56de\u7b54\u7684\u539f\u56e0 \u2713\uff09"
             "\uff0c\u800c\u4e00\u4e2a 0 \u8840\u7684\u5355\u4f4d\u88ab\u6cbb\u7597 25% \u4e0a\u9650\u540e\uff0c\u7ed3\u679c**\u5c31\u662f 25% \u4e0a\u9650** \u2713 \u21d2 **\u4e0d\u6d89\u53ca\u8fd1\u4f3c** \u2713\u3002"
             "\u201c\u8fdb\u5165\u6218\u6597\u540e\u201d\u662f\u53e5\u5b50\u81ea\u5df1\u7684\u8d77\u624b\u5f0f \u2713\uff08\u5728\u6218\u6597\u4e2d\u5df2\u7136\u6210\u7acb \u2713\uff09\u3002"),
})

if isObject:
    doc["rules"] = rules
else:
    doc = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1008.json: eidolon four survives a lethal blow at a quarter")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1008\uff1a\u300c\u53d7\u5230\u81f4\u547d\u653b\u51fb\u65f6\u963f\u5170\u4e0d\u4f1a\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\uff0c\u5e76\u7acb\u5373\u56de\u590d\u81f3\u81ea\u8eab\u751f\u547d\u4e0a\u9650\u7684 25%\u300d (2026-10-02).
 *
 * <p>\u2b50 ONE VARIABLE: the eidolon rank. Same scene, same lethal blow.
 */
public class ArlanEidolonFourTest {
    private static final int ARLAN = 1008;
    private static final int MONSTER = 1002011;

    /** \u2b50 At E4 he stands at a quarter of his Max HP. */
    @Test
    public void atEidolonFourHeStandsAtAQuarter() {
        double[] result = afterLethalBlow(4);
        Assertions.assertTrue(result[0] > 0, "\u300c\u4e0d\u4f1a\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u300d (hp " + result[0] + ")");
        Assertions.assertEquals(result[1] * 0.25, result[0], result[1] * 0.01,
                "\u300c\u56de\u590d\u81f3\u81ea\u8eab\u751f\u547d\u4e0a\u9650\u7684 25%\u300d");
    }

    /** \u26a0 Below E4 he falls. */
    @Test
    public void belowEidolonFourHeFalls() {
        Assertions.assertTrue(afterLethalBlow(0)[0] <= 0, "\u661f\u9b42 4 \u624d\u6709\u8fd9\u4e00\u6761");
    }

    // ==================================================================

    /** { his HP after the blow, his Max HP }. */
    private static double[] afterLethalBlow(int eidolon) {
        Character him = CharacterFactory.create(ARLAN, 80, false, null, null, eidolon);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        battle.applyTrueDamage(battle.enemies.getFirst(), him, DamageElement.ICE, him.getCurrentHp() * 2.0);
        battle.processRequests();
        return new double[]{him.isDeath() ? 0 : him.getCurrentHp(), him.getMaxHp()};
    }
}
''')
print("ok   judge written")
