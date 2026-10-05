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
⚠ `once_per_battle` is the SHIPPED spelling for 1104's 「单场战斗中只能触发 1 次」 (1105 and 1111 both use it).
ASCII only.
"""
import io
import json

DATA_1408 = "src/main/resources/characters/1408.json"
DATA_1104 = "src/main/resources/characters/1104.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/LethalHitTest.java"
RULE_1408 = "transformation_survives_a_lethal_hit"
RULE_1104 = "trace_survives_a_lethal_hit_once"
STATE = "变身"


def add_rule(path, rule):
    doc = json.load(io.open(path, encoding="utf-8"))
    # Note: Some character files are BARE LISTS and some are objects (measured: 1104.json is a list -- "must be an object" is what the
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
    "source": ("1408 白厄（文档，卡厄斯兰那的天赋）：「卡厄斯兰那受到**致命攻击**时"
               "不会陷入**无法战斗状态**，而是**回复等同于自身生命上限 20%** 的生命值」"),
    "note": ("⭐ 2026-10-02：触发用**新建的 `LETHAL_DAMAGE`** ✓（它在 `Battle.applyDamage` 里发 ✓，"
             "❗ **而且目标先被扣到 0 但**不判死**** ✓ —— 否则满血时回血是空操作 ✗）。"
             "⚠ **绑到【变身】** ✓⇒“卡厄斯兰那”的作用域 ✓。"),
})

add_rule(DATA_1104, {
    "on": "LETHAL_DAMAGE",
    "id": RULE_1104,
    "when": ["target == self"],
    "once_per_battle": True,
    "do": [{"op": "HEAL", "scale": "owner_max_hp", "percent": 0.50, "target": "self"}],
    "source": ("1104 镜流（行迹）：「受到**致命攻击**时不会陷入无法战斗状态，并立即回复"
               "等同于自身生命上限 **50%** 的生命值。**该效果单场战斗中只能触发 1 次**」"),
    "note": ("⭐ 2026-10-02：⭐ **本文件自己把这句登记成“缺…”** ✓（`1104.json` 的 `note` 逐字："
             "*「受到致命攻击时不会陷入无法战斗状态…」—— 缺…* ✓）⇒ 本件把它接上 ✓。"
             "❗ “**单场一次**”用 **`once_per_battle`** ✓ —— ⚠ **已出货拼法** ✓（`1105` ⾋ `1111` 都用它 ✓）。"),
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
 * 「受到致命攻击时不会陷入无法战斗状态，而是回复…」 -- one capability, two readers (2026-10-02).
 *
 * <p>⭐ The hit is dealt through the battle's own settlement entry point with twice the unit's CURRENT HP, so it is lethal by
 * construction -- and the assertion is about survival, not about how much was healed.
 */
public class LethalHitTest {
    private static final int PHAINON = 1408;
    private static final int JINGLIU = 1104;
    private static final int MONSTER = 1002011;
    private static final String STATE = "变身";

    /** ⭐ 1408: transformed, a lethal blow leaves her standing at a fifth of her (raised) Max HP. */
    @Test
    public void theTransformedFormSurvivesALethalBlow() {
        Character her = survivor(PHAINON, true);
        Assertions.assertFalse(her.isDeath(), "「卡厄斯兰那受到致命攻击时不会陷入无法战斗状态」");
        Assertions.assertEquals(her.getMaxHp() * 0.20, her.getCurrentHp(), her.getMaxHp() * 0.01,
                "「而是回复等同于自身生命上限 20% 的生命值」");
    }

    /** ⚠ 1408 untransformed: the same blow kills her. */
    @Test
    public void withoutTheTransformationTheBlowKills() {
        Assertions.assertTrue(survivor(PHAINON, false).isDeath(),
                "「变身期间」-- outside it the clause does not apply");
    }

    /** ⭐ 1104: her trace saves her once -- at half of Max HP -- and the SECOND lethal blow kills her. */
    @Test
    public void jingliuSurvivesOnce() {
        Character her = survivor(JINGLIU, false);
        Assertions.assertFalse(her.isDeath(), "1104 的行迹救了她一次");
        Assertions.assertEquals(her.getMaxHp() * 0.50, her.getCurrentHp(), her.getMaxHp() * 0.01,
                "「回复等同于自身生命上限 50% 的生命值」");
    }

    /** ⚠ 「该效果**单场战斗中只能触发 1 次**」: the second lethal blow kills her. */
    @Test
    public void jingliuDiesToASecondLethalBlow() {
        Character her = CharacterFactory.create(JINGLIU, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(her),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        battle.applyTrueDamage(battle.enemies.getFirst(), her, DamageElement.ICE, her.getCurrentHp() * 2.0);
        battle.processRequests();
        Assertions.assertFalse(her.isDeath(), "precondition: the first blow is survived");

        battle.applyTrueDamage(battle.enemies.getFirst(), her, DamageElement.ICE, her.getCurrentHp() * 2.0);
        battle.processRequests();
        Assertions.assertTrue(her.isDeath(),
                "「该效果单场战斗中只能触发 1 次」-- the second one is not saved");
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
