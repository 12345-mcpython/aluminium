"""1211: 「当白露的队友受到致命攻击时，不会陷入无法战斗状态，白露会立即为其提供治疗…」 (2026-10-02, item 46).

Document, verbatim (1211 白露 天赋): 「当**白露的队友**受到致命攻击时，不会陷入**无法战斗状态**，**白露会立即为其提供治疗**，回复等同于**白露** 18.00%
生命上限 + 480 的生命值。」

⭐ WHY THIS IS A NEW SHAPE, not a repeat of items 43: 1104 and 1408 heal THEMSELVES; here the healer is a DIFFERENT unit, and the amount
is a share of the HEALER's Max HP -- which is exactly what `scale: "owner_max_hp"` means (the rule's owner is 白露, while `target` is the
unit that would have died, because `LETHAL_DAMAGE` is fired with the victim as both actor and target).

⚠ 「队友」 EXCLUDES HER: the shipped `1101.json` already writes `"target != self"`, so `!=` is in the DSL. Without it she would also
heal herself on her own lethal hit, which the sentence does not say.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1211.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/LethalHealOnTeammateTest.java"
RULE = "talent_heals_a_lethal_hit_on_a_teammate"

doc = json.load(io.open(DATA, encoding="utf-8"))
# ⚠ 1211.json is a BARE LIST (like 1104.json and 1209.json); the file's own shape is preserved on the way out.
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
    "source": ("1211 白露 天赋：「当**白露的队友**受到致命攻击时，不会陷入**无法战斗状态**，"
               "**白露会立即为其提供治疗**，回复等同于**白露** 18.00% 生命上限 + 480 的生命值。」"),
    "note": ("⭐ 2026-10-02：⭐ **本条是 `LETHAL_DAMAGE` 的**第三位读者**** ✓，而且是**新形态** ✓："
             "前两位（1104⾋1408）都是**自己救自己** ✓，而这里**治疗者与被害者是两个单位** ✓。"
             "❗ `scale: \"owner_max_hp\"` 正是原文的“等同于**白露**…” ✓（owner 是白露 ✓，`target` 是那个即将倒下的单位 ✓）。"
             "⚠ **“队友”排除她自己** ✓：已出货的 `1101.json` 就写着 `\"target != self\"` ✓ ⇒ `!=` 在 DSL 里 ✓。"),
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
 * 1211：「当白露的队友受到致命攻击时…白露会立即为其提供治疗，回复等同于白露 18.00% 生命上限 + 480 的生命值」
 * (2026-10-02).
 *
 * <p>⭐ ONE VARIABLE: whether the blow would have killed the teammate. Nothing else differs -- same party, same heal amount, same scene.
 */
public class LethalHealOnTeammateTest {
    private static final int BAILU = 1211;
    private static final int TEAMMATE = 1002;
    private static final int MONSTER = 1002011;

    /** ⭐ A lethal blow: the teammate is saved, at 白露’s 18% Max HP plus 480. */
    @Test
    public void aLethalBlowIsHealed() {
        double[] result = afterBlow(true);
        Assertions.assertFalse(result[0] < 0, "the teammate is alive (hp " + result[0] + ")");
        Assertions.assertTrue(result[0] > 0, "「不会陷入无法战斗状态」-- a teammate at 0 HP would have fallen");
        Assertions.assertEquals(result[1], result[0], 1.0,
                "「回复等同于白露 18.00% 生命上限 + 480」");
    }

    /** ⚠ A survivable blow: the clause has not started, so the damage simply lands. */
    @Test
    public void aSurvivableBlowIsNotHealed() {
        double[] result = afterBlow(false);
        Assertions.assertTrue(result[0] < result[2], "「受到致命攻击时」-- this one was not lethal");
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
