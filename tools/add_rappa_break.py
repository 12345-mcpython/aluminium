"""Rappa's break-damage trait (1317:440, 2026-10-02) + a judge that proves `damage_type` is really read.

Sentence (行迹 忍法帖•枯叶): 「敌方目标的弱点被击破时，受到的击破伤害提高2%，若乱破当前攻击力高于2400点，每超过100点攻击力
可使该数值额外提高1%，最多额外提高8%。效果持续2回合。」

Both rules hang on the existing `BREAK` event (51 uses in content). The extra one is SEPARATE, because 「若乱破当前攻击力高于
2400 点」 constrains only that half. `damage_type: "break"` is `DamageType.BREAK`'s data value; `cap_amount` is a constant
ceiling on the final value (measured last round); `turns` is mandatory for this op (measured too).

The judge's point: `damage_type` is NOT validated at load, so it must be shown to be READ -- by SELECTIVITY. The same base
damage dealt as BREAK is amplified, and as NORMAL is not. If the field were silently ignored, both would move.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1317.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/BreakDamageTakenTest.java"
BASE_ID = "trace_break_damage_taken_up"
EXTRA_ID = "trace_break_damage_taken_up_above_attack"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") in (BASE_ID, EXTRA_ID))]
rules.append({
    "on": "BREAK",
    "id": BASE_ID,
    "do": [{
        "op": "MODIFY_DAMAGE_TAKEN",
        "damage_type": "break",
        "percent": 0.02,
        "turns": 2,
        "target": "target",
    }],
    "source": ("1317 乱破 行迹 忍法帖•枯叶: "
               "「敌方目标的弱点被击破时，受到的**击破伤害提高 2%**…"
               "效果持续 **2** 回合」"),
    "note": ("⭐ `damage_type: \"break\"` = `DamageType.BREAK` 的**数据值** ✓（读枚举得到 ✓）。"
             "⚠ 装载期**不检查** `damage_type` ✗ ⇒ 判据靠**选择性**证明它真的被读 ✓。"
             "⚠ `turns` 是该 op 的**强制**要求 ✓（`requireDuration` ✓）。"),
})
rules.append({
    "on": "BREAK",
    "id": EXTRA_ID,
    "when": ["self_attr:ATTACK > 2400"],
    "do": [{
        "op": "MODIFY_DAMAGE_TAKEN",
        "damage_type": "break",
        "scale": "self_attr_above:ATTACK:2400",
        "percent": 0.0001,
        "cap_amount": 0.08,
        "turns": 2,
        "target": "target",
    }],
    "source": ("1317 乱破 行迹 忍法帖•枯叶（后半）: "
               "「若乱破当前攻击力高于 **2400** 点，**每超过 100 点攻击力**"
               "可使该数值额外提高 **1%**，**最多额外提高 8%**」"),
    "note": ("⭐ 拆成**第二条规则** ✓，因为「若攻击力高于 2400」**只约束这一半** ✓。"
             "⭐ 用**今日出货**的 `scale: \"self_attr_above:ATTACK:2400\"` ✓"
             "（**每 100 点 1% = 每点 0.01%** ⇒ `percent: 0.0001` ✓）。"
             "⭐ `cap_amount: 0.08` ✓（「最多额外提高 8%」⇒ 对**最终值**的常数上限 ✓）。"
             "⚠ 本条的**上限断言尚未写** ✗（需要在判据里改攻击力 ✗ 或用阈值参数间接验 ✓）⇒ 登记 ✓。"),
})
out = doc if isinstance(doc, dict) else {"rules": rules}
if isinstance(doc, dict):
    doc["rules"] = rules
    out = doc
else:
    out = rules
json.dump(out, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1317.json: the two break-damage-taken rules")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 「敌方目标的弱点被击破时，受到的痛击破伤害提高 2%」 (1317:440, 2026-10-02).
 *
 * <p>⭐ SELECTIVITY is the point: `damage_type` is not validated at load, so the judge deals the SAME base damage twice --
 * once as BREAK and once as NORMAL -- and the rule must amplify only the first. A silently ignored field would move both.
 */
public class BreakDamageTakenTest {
    private static final int OWNER = 1317;
    private static final int MONSTER = 1002011;

    /** ⭐ Break damage is raised, normal damage is not. */
    @Test
    public void onlyBreakDamageIsRaised() {
        double breakDealt = dealt(DamageType.BREAK);
        double normalDealt = dealt(DamageType.NORMAL);
        Assertions.assertTrue(normalDealt > 0, "precondition: the hit lands (" + normalDealt + ")");
        Assertions.assertTrue(breakDealt > normalDealt * 1.005,
                "break damage must be raised by the rule (" + breakDealt + " vs " + normalDealt + ")");
    }

    // ==================================================================

    private static double dealt(DamageType type) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.applyDamage(enemy, new Damage(owner, enemy, DamageElement.IMAGINARY, type, 200));
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }
}
''')
print("ok   judge written (break vs normal)")
