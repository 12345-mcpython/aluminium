"""1008: 「受到致命攻击时阿兰不会陷入无法战斗状态，并立即回复至自身生命上限的 25%」 (2026-10-02, item 48).

Document, verbatim (1008 阿兰 星魂 4 「绝处反击 / Turn the Tables」): 「**进入战斗后**，受到致命攻击时阿兰不会陷入**无法战斗状态**，并立即**回复至**自身生命上限的
**25%**。」

⭐ FIFTH reader of `LETHAL_DAMAGE`, and the first that states a CEILING ("回复**至**") rather than an amount ("回复") -- and here the two
coincide EXACTLY, which is why this ships rather than being registered:
  * `LETHAL_DAMAGE` is announced with the target already at 0 HP (that is what makes the moment answerable at all -- see item 43), and a
    unit at 0 HP that is healed for 25% of its Max HP ends at 25% of its Max HP. "Set to 25%" and "add 25%" are the same instruction from
    that starting point, so no approximation is involved.
  * 「进入战斗后」 is the sentence's own framing for a battle-scoped trace; in a battle it is already true, so it adds no condition.

⚠ `1008.json` is a BARE LIST (like 1104 / 1209 / 1211 / 1217); the file's own shape is preserved on the way out.
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
    "source": ("1008 阿兰 星魂 4 「绝处反击 / Turn the Tables」：「**进入战斗后**，受到致命攻击时"
               "阿兰不会陷入**无法战斗状态**，并立即**回复至**自身生命上限的 **25%**」"),
    "note": ("⭐ 2026-10-02：⭐ **`LETHAL_DAMAGE` 的第五位读者** ✓，而且是**第一位说的是“回复**至**”**（上限）"
             "而不是“回复”（数量）的 ✓。❗ **而在这条路上两者**恰好重合**** ✓："
             "`LETHAL_DAMAGE` 发出时目标**已经在 0 血** ✓（那正是这个时刻能被回答的原因 ✓）"
             "，而一个 0 血的单位被治疗 25% 上限后，结果**就是 25% 上限** ✓ ⇒ **不涉及近似** ✓。"
             "“进入战斗后”是句子自己的起手式 ✓（在战斗中已然成立 ✓）。"),
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
 * 1008：「受到致命攻击时阿兰不会陷入无法战斗状态，并立即回复至自身生命上限的 25%」 (2026-10-02).
 *
 * <p>⭐ ONE VARIABLE: the eidolon rank. Same scene, same lethal blow.
 */
public class ArlanEidolonFourTest {
    private static final int ARLAN = 1008;
    private static final int MONSTER = 1002011;

    /** ⭐ At E4 he stands at a quarter of his Max HP. */
    @Test
    public void atEidolonFourHeStandsAtAQuarter() {
        double[] result = afterLethalBlow(4);
        Assertions.assertTrue(result[0] > 0, "「不会陷入无法战斗状态」 (hp " + result[0] + ")");
        Assertions.assertEquals(result[1] * 0.25, result[0], result[1] * 0.01,
                "「回复至自身生命上限的 25%」");
    }

    /** ⚠ Below E4 he falls. */
    @Test
    public void belowEidolonFourHeFalls() {
        Assertions.assertTrue(afterLethalBlow(0)[0] <= 0, "星魂 4 才有这一条");
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
