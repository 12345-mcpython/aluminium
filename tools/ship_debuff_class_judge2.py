"""The judge, with the keys the validator names (round 9 of the goal).

`APPLY_CONTROL` says: requires "control" (known: 冻结 / 禁锢 / 纠缠). The dot half is asked the same way -- and if `APPLY_DOT` wants a
different key, the load refuses loudly rather than silently, which is the point of the guard.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/DebuffClassConditionTest.java"
io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.ResourceSpec;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * \u300c\u654c\u65b9\u5bf9\u6211\u65b9\u65bd\u52a0\u4e86**\u63a7\u5236\u7c7b**\u8d1f\u9762\u72b6\u6001\u300d\u53ef\u4ee5\u88ab\u95ee\u4e86 (2026-10-02).
 *
 * <p>Two-way on purpose: the same rule must fire for a CONTROL debuff and stay silent for a DOT one, which is the only way this
 * reading tells "the filter works" apart from "the event fired at all".
 */
public class DebuffClassConditionTest {
    private static final int OWNER = 1002;
    private static final int MONSTER = 1002011;
    private static final String PROBE = "probeClass";

    /** A control debuff fires the control rule only; a dot fires the dot rule only. */
    @Test
    public void theFilterTellsTheTwoFamiliesApart() {
        Assertions.assertEquals(1, hitsFor("APPLY_CONTROL", "control"),
                "\u300c\u63a7\u5236\u7c7b\u300d-- a landed control fires the rule that asked for it");
        Assertions.assertEquals(0, hitsFor("APPLY_CONTROL", "dot"),
                "and the same control does NOT fire a rule that asked for \u300c\u6301\u7eed\u4f24\u5bb3\u7c7b\u300d");
        Assertions.assertEquals(1, hitsFor("APPLY_DOT", "dot"),
                "\u300c\u6301\u7eed\u4f24\u5bb3\u7c7b\u300d-- a landed dot fires the dot rule");
        Assertions.assertEquals(0, hitsFor("APPLY_DOT", "control"),
                "and it does NOT fire the control one");
    }

    /** How many times a rule asking for {@code wanted} fires when {@code op} lands a debuff. */
    private static int hitsFor(String op, String wanted) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        EffectSpec land = new EffectSpec();
        TriggerSpecs.set(land, "op", op);
        if ("APPLY_CONTROL".equals(op)) {
            // \u26a0 The key the validator names: `APPLY_CONTROL requires "control" (known: \u51bb\u7ed3 / \u7981\u9522 / \u7f20\u7ed5)`.
            TriggerSpecs.set(land, "control", "\\u51bb\\u7ed3");
        } else {
            TriggerSpecs.set(land, "dot", "probeDot");
            TriggerSpecs.set(land, "percent", 0.1);
            TriggerSpecs.set(land, "turns", 2);
        }
        TriggerSpecs.set(land, "baseChance", 1.0);
        TriggerSpecs.set(land, "target", "all_enemies");

        EffectSpec watch = new EffectSpec();
        TriggerSpecs.set(watch, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(watch, "resource", PROBE);
        TriggerSpecs.set(watch, "amount", 1.0);
        TriggerSpecs.set(watch, "target", "self");

        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), land),
                TriggerSpecs.rule("DEBUFF_APPLIED", List.of("debuff_class:" + wanted), watch)),
                List.of(new ResourceSpec(PROBE, 99, 0, null, null, "probe", null))));
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return owner.getResources().has(PROBE) ? owner.getResources().value(PROBE) : 0;
    }
}
''')
print("ok   the judge uses the keys the validator names")
