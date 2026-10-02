"""Rappa's break-damage trait (1317:440, 2026-10-02) + a judge that proves `damage_type` is really read.

Sentence (行迹 忍法帖\u2022枯叶): 「敌方目标的弱点被击破时，受到的击破伤害提高2%，若乱破当前攻击力高于2400点，每超过100点攻击力
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
    "source": ("1317 \u4e71\u7834 \u884c\u8ff9 \u5fcd\u6cd5\u5e16\u2022\u67af\u53f6: "
               "\u300c\u654c\u65b9\u76ee\u6807\u7684\u5f31\u70b9\u88ab\u51fb\u7834\u65f6\uff0c\u53d7\u5230\u7684**\u51fb\u7834\u4f24\u5bb3\u63d0\u9ad8 2%**\u2026"
               "\u6548\u679c\u6301\u7eed **2** \u56de\u5408\u300d"),
    "note": ("\u2b50 `damage_type: \"break\"` = `DamageType.BREAK` \u7684**\u6570\u636e\u503c** \u2713\uff08\u8bfb\u679a\u4e3e\u5f97\u5230 \u2713\uff09\u3002"
             "\u26a0 \u88c5\u8f7d\u671f**\u4e0d\u68c0\u67e5** `damage_type` \u2717 \u21d2 \u5224\u636e\u9760**\u9009\u62e9\u6027**\u8bc1\u660e\u5b83\u771f\u7684\u88ab\u8bfb \u2713\u3002"
             "\u26a0 `turns` \u662f\u8be5 op \u7684**\u5f3a\u5236**\u8981\u6c42 \u2713\uff08`requireDuration` \u2713\uff09\u3002"),
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
    "source": ("1317 \u4e71\u7834 \u884c\u8ff9 \u5fcd\u6cd5\u5e16\u2022\u67af\u53f6\uff08\u540e\u534a\uff09: "
               "\u300c\u82e5\u4e71\u7834\u5f53\u524d\u653b\u51fb\u529b\u9ad8\u4e8e **2400** \u70b9\uff0c**\u6bcf\u8d85\u8fc7 100 \u70b9\u653b\u51fb\u529b**"
               "\u53ef\u4f7f\u8be5\u6570\u503c\u989d\u5916\u63d0\u9ad8 **1%**\uff0c**\u6700\u591a\u989d\u5916\u63d0\u9ad8 8%**\u300d"),
    "note": ("\u2b50 \u62c6\u6210**\u7b2c\u4e8c\u6761\u89c4\u5219** \u2713\uff0c\u56e0\u4e3a\u300c\u82e5\u653b\u51fb\u529b\u9ad8\u4e8e 2400\u300d**\u53ea\u7ea6\u675f\u8fd9\u4e00\u534a** \u2713\u3002"
             "\u2b50 \u7528**\u4eca\u65e5\u51fa\u8d27**\u7684 `scale: \"self_attr_above:ATTACK:2400\"` \u2713"
             "\uff08**\u6bcf 100 \u70b9 1% = \u6bcf\u70b9 0.01%** \u21d2 `percent: 0.0001` \u2713\uff09\u3002"
             "\u2b50 `cap_amount: 0.08` \u2713\uff08\u300c\u6700\u591a\u989d\u5916\u63d0\u9ad8 8%\u300d\u21d2 \u5bf9**\u6700\u7ec8\u503c**\u7684\u5e38\u6570\u4e0a\u9650 \u2713\uff09\u3002"
             "\u26a0 \u672c\u6761\u7684**\u4e0a\u9650\u65ad\u8a00\u5c1a\u672a\u5199** \u2717\uff08\u9700\u8981\u5728\u5224\u636e\u91cc\u6539\u653b\u51fb\u529b \u2717 \u6216\u7528\u9608\u503c\u53c2\u6570\u95f4\u63a5\u9a8c \u2713\uff09\u21d2 \u767b\u8bb0 \u2713\u3002"),
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
 * \u300c\u654c\u65b9\u76ee\u6807\u7684\u5f31\u70b9\u88ab\u51fb\u7834\u65f6\uff0c\u53d7\u5230\u7684\u75db\u51fb\u7834\u4f24\u5bb3\u63d0\u9ad8 2%\u300d (1317:440, 2026-10-02).
 *
 * <p>\u2b50 SELECTIVITY is the point: `damage_type` is not validated at load, so the judge deals the SAME base damage twice --
 * once as BREAK and once as NORMAL -- and the rule must amplify only the first. A silently ignored field would move both.
 */
public class BreakDamageTakenTest {
    private static final int OWNER = 1317;
    private static final int MONSTER = 1002011;

    /** \u2b50 Break damage is raised, normal damage is not. */
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
