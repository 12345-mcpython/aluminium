"""1211: 「当白露的队友受到致命攻击时，不会陷入无法战斗状态，白露会立即为其提供治疗…」 (2026-10-02, item 46).

Document, verbatim (1211 白露 天赋): 「当**白露的队友**受到致命攻击时，不会陷入**无法战斗状态**，**白露会立即为其提供治疗**，回复等同于**白露** 18.00%
生命上限 + 480 的生命值。」

\u2b50 WHY THIS IS A NEW SHAPE, not a repeat of items 43: 1104 and 1408 heal THEMSELVES; here the healer is a DIFFERENT unit, and the amount
is a share of the HEALER's Max HP -- which is exactly what `scale: "owner_max_hp"` means (the rule's owner is 白露, while `target` is the
unit that would have died, because `LETHAL_DAMAGE` is fired with the victim as both actor and target).

\u26a0 「队友」 EXCLUDES HER: the shipped `1101.json` already writes `"target != self"`, so `!=` is in the DSL. Without it she would also
heal herself on her own lethal hit, which the sentence does not say.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1211.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/LethalHealOnTeammateTest.java"
RULE = "talent_heals_a_lethal_hit_on_a_teammate"

doc = json.load(io.open(DATA, encoding="utf-8"))
# \u26a0 1211.json is a BARE LIST (like 1104.json and 1209.json); the file's own shape is preserved on the way out.
isObject = isinstance(doc, dict)
rules = doc["rules"] if isObject else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") == RULE)]

if len(rules) != 3:
    raise SystemExit("expected the three shipped 1211 rules, found " + str(len(rules)))

rules.append({
    "on": "LETHAL_DAMAGE",
    "id": RULE,
    "when": ["actor is_ally", "actor != self"],
    "do": [{"op": "HEAL", "scale": "owner_max_hp", "percent": 0.18, "amount": 480, "target": "target"}],
    "source": ("1211 \u767d\u9732 \u5929\u8d4b\uff1a\u300c\u5f53**\u767d\u9732\u7684\u961f\u53cb**\u53d7\u5230\u81f4\u547d\u653b\u51fb\u65f6\uff0c\u4e0d\u4f1a\u9677\u5165**\u65e0\u6cd5\u6218\u6597\u72b6\u6001**\uff0c"
               "**\u767d\u9732\u4f1a\u7acb\u5373\u4e3a\u5176\u63d0\u4f9b\u6cbb\u7597**\uff0c\u56de\u590d\u7b49\u540c\u4e8e**\u767d\u9732** 18.00% \u751f\u547d\u4e0a\u9650 + 480 \u7684\u751f\u547d\u503c\u3002\u300d"),
    "note": ("\u2b50 2026-10-02\uff1a\u2b50 **\u672c\u6761\u662f `LETHAL_DAMAGE` \u7684**\u7b2c\u4e09\u4f4d\u8bfb\u8005**** \u2713\uff0c\u800c\u4e14\u662f**\u65b0\u5f62\u6001** \u2713\uff1a"
             "\u524d\u4e24\u4f4d\uff081104\u2f8b1408\uff09\u90fd\u662f**\u81ea\u5df1\u6551\u81ea\u5df1** \u2713\uff0c\u800c\u8fd9\u91cc**\u6cbb\u7597\u8005\u4e0e\u88ab\u5bb3\u8005\u662f\u4e24\u4e2a\u5355\u4f4d** \u2713\u3002"
             "\u2757 `scale: \"owner_max_hp\"` \u6b63\u662f\u539f\u6587\u7684\u201c\u7b49\u540c\u4e8e**\u767d\u9732**\u2026\u201d \u2713\uff08owner \u662f\u767d\u9732 \u2713\uff0c`target` \u662f\u90a3\u4e2a\u5373\u5c06\u5012\u4e0b\u7684\u5355\u4f4d \u2713\uff09\u3002"
             "\u26a0 **\u201c\u961f\u53cb\u201d\u6392\u9664\u5979\u81ea\u5df1** \u2713\uff1a\u5df2\u51fa\u8d27\u7684 `1101.json` \u5c31\u5199\u7740 `\"target != self\"` \u2713 \u21d2 `!=` \u5728 DSL \u91cc \u2713\u3002"),
})

if isObject:
    doc["rules"] = rules
else:
    doc = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1211.json: a teammate's lethal hit is healed")

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
 * 1211\uff1a\u300c\u5f53\u767d\u9732\u7684\u961f\u53cb\u53d7\u5230\u81f4\u547d\u653b\u51fb\u65f6\u2026\u767d\u9732\u4f1a\u7acb\u5373\u4e3a\u5176\u63d0\u4f9b\u6cbb\u7597\uff0c\u56de\u590d\u7b49\u540c\u4e8e\u767d\u9732 18.00% \u751f\u547d\u4e0a\u9650 + 480 \u7684\u751f\u547d\u503c\u300d
 * (2026-10-02).
 *
 * <p>\u2b50 ONE VARIABLE: whether the blow would have killed the teammate. Nothing else differs -- same party, same heal amount, same scene.
 */
public class LethalHealOnTeammateTest {
    private static final int BAILU = 1211;
    private static final int TEAMMATE = 1002;
    private static final int MONSTER = 1002011;

    /** \u2b50 A lethal blow: the teammate is saved, at 白露\u2019s 18% Max HP plus 480. */
    @Test
    public void aLethalBlowIsHealed() {
        double[] result = afterBlow(true);
        Assertions.assertFalse(result[0] < 0, "the teammate is alive (hp " + result[0] + ")");
        Assertions.assertTrue(result[0] > 0, "\u300c\u4e0d\u4f1a\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u300d-- a teammate at 0 HP would have fallen");
        Assertions.assertEquals(result[1], result[0], 1.0,
                "\u300c\u56de\u590d\u7b49\u540c\u4e8e\u767d\u9732 18.00% \u751f\u547d\u4e0a\u9650 + 480\u300d");
    }

    /** \u26a0 A survivable blow: the clause has not started, so the damage simply lands. */
    @Test
    public void aSurvivableBlowIsNotHealed() {
        double[] result = afterBlow(false);
        Assertions.assertTrue(result[0] < result[2], "\u300c\u53d7\u5230\u81f4\u547d\u653b\u51fb\u65f6\u300d-- this one was not lethal");
    }

    // ==================================================================

    /** { hp after, the amount the sentence promises, hp before }. */
    private static double[] afterBlow(boolean lethal) {
        Character healer = CharacterFactory.create(BAILU, 80, false, null, null, 0);
        Character teammate = CharacterFactory.create(TEAMMATE, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(healer, teammate),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        double before = teammate.getCurrentHp();
        double damage = lethal ? before * 2.0 : before * 0.1;
        battle.applyTrueDamage(battle.enemies.getFirst(), teammate, DamageElement.ICE, damage);
        battle.processRequests();
        return new double[]{teammate.getCurrentHp(), healer.getMaxHp() * 0.18 + 480, before};
    }
}
''')
print("ok   judge written")
