"""The judge, simplified to what actually discriminates (round 9 of the goal).

Measured from the validators: `APPLY_CONTROL` needs `control` + a positive `turns` (base chance optional), while `APPLY_DOT` needs an
`element` and a magnitude. So the reading does not need to land a dot at all: land ONE control and watch it with two rules -- one
asking for `debuff_class: control` and one asking for `debuff_class: dot`. The filter is pinned by the pair, and the mutant (a filter
that ignores the class) turns the second half red.
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
 * 「敌方对我方施加了**控制类**负面状态」可以被问了 (2026-10-02).
 *
 * <p>The pair is the reading: one landed control, watched by a rule that asked for 「控制类」 (fires) and by one that asked for
 * 「持续伤害类」 (silent). A filter that ignored the class would fire both, so the second half is what pins it.
 */
public class DebuffClassConditionTest {
    private static final int OWNER = 1002;
    private static final int MONSTER = 1002011;

    /** A landed control fires the control rule and does not fire the dot rule. */
    @Test
    public void theFilterTellsTheTwoFamiliesApart() {
        Assertions.assertEquals(1, hitsFor("control"),
                "「控制类」-- the landed control fires the rule that asked for it");
        Assertions.assertEquals(0, hitsFor("dot"),
                "and it does NOT fire the rule that asked for 「持续伤害类」");
    }

    /** How many times a rule asking for {@code wanted} fires when ONE control (冻结) lands. */
    private static int hitsFor(String wanted) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        String probe = "probe" + wanted;

        EffectSpec land = new EffectSpec();
        TriggerSpecs.set(land, "op", "APPLY_CONTROL");
        // ⚠ The keys the validator names: `APPLY_CONTROL requires "control"` and `requires "turns"`.
        TriggerSpecs.set(land, "control", "\\u51bb\\u7ed3");
        TriggerSpecs.set(land, "turns", 2);
        TriggerSpecs.set(land, "baseChance", 1.0);
        TriggerSpecs.set(land, "target", "all_enemies");

        EffectSpec watch = new EffectSpec();
        TriggerSpecs.set(watch, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(watch, "resource", probe);
        TriggerSpecs.set(watch, "amount", 1.0);
        TriggerSpecs.set(watch, "target", "self");

        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), land),
                TriggerSpecs.rule("DEBUFF_APPLIED", List.of("debuff_class:" + wanted), watch)),
                List.of(new ResourceSpec(probe, 99, 0, null, null, "probe", null))));
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return owner.getResources().has(probe) ? owner.getResources().value(probe) : 0;
    }
}
''')
print("ok   the judge asks the pair")
