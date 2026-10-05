package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * A DOT's <b>layer ceiling</b> (2026-09-28): "风化状态最多叠加 5 层" (1108 Sampo's talent).
 *
 * <p><b>What is implemented is exactly what the sentence asserts</b> - at most N layers of a state <i>deal damage</i>;
 * applications past the ceiling are inert (they are not removed: `DotBuff.isSameKind` answers `false`, so a DOT is never
 * evicted, and the extra layers simply sit there).
 *
 * <p>Note: <b>What is NOT implemented</b>: 130 Black Swan's extra sentence "after the layer count reaches its cap it can keep stacking, and <b>the layers beyond the cap are removed after the damage is dealt</b>" - the excess layers are removed after the damage. It is stated for ONE state, so applying it to every capped DOT
 * would be an inference rather than a reading; it stays registered. Likewise no refresh policy is invented: the corpus has
 * <b>zero</b> sentences describing what a re-application does to a duration.
 *
 * <p>The observation is {@code Battle.tickDots}' own return value (the damage that pass dealt), which is what makes the
 * ceiling measurable without touching HP.
 */
public class DotCapTest {
    /** Himeko: no shipped rule file, so the table under test is the only one. */
    private static final int OWNER = 1003;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: Without a ceiling every application pays, which is what the engine has always done. */
    @Test
    public void withoutACeilingEveryLayerPays() {
        double one = tickAfter(1, null);
        double three = tickAfter(3, null);

        Assertions.assertTrue(one > 0, "precondition: the DOT deals damage at all");
        Assertions.assertEquals(3 * one, three, 1e-6,
                "three applications of the same state deal three times one layer (DOTs are never evicted)");
    }

    /** Note: With a ceiling, only the first N layers pay - the extra applications add nothing. */
    @Test
    public void pastTheCeilingExtraLayersPayNothing() {
        double one = tickAfter(1, 2);
        double two = tickAfter(2, 2);
        double five = tickAfter(5, 2);

        Assertions.assertEquals(2 * one, two, 1e-6, "two layers are within the ceiling of 2, so both pay");
        Assertions.assertEquals(2 * one, five, 1e-6,
                "⚠ 「最多叠加 N 层」: five applications still deal exactly two layers' damage (the rest are inert)");
    }

    /** Note: A ceiling has to be a real count; zero or negative is refused when the rule is compiled. */
    @Test
    public void aNonsenseCeilingIsRefused() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(rule(0))));
        Assertions.assertTrue(refused.getMessage().contains("max_stacks"), refused.getMessage());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static double tickAfter(int applications, Integer maxStacks) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(rule(maxStacks))));
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });
        battle.startBattle();
        for (int i = 0; i < applications; i++) {
            battle.fireTriggers(TriggerEvent.KILL, owner, enemy, 0, 0);
        }
        return battle.tickDots(enemy);
    }

    private static TriggerSpec rule(Integer maxStacks) {
        EffectSpec dot = new EffectSpec();
        TriggerSpecs.set(dot, "op", "APPLY_DOT");
        TriggerSpecs.set(dot, "element", "Thunder");
        TriggerSpecs.set(dot, "percent", 0.5);
        TriggerSpecs.set(dot, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(dot, "turns", 3);
        TriggerSpecs.set(dot, "target", "target");
        if (maxStacks != null) {
            TriggerSpecs.set(dot, "maxStacks", maxStacks);
        }
        return TriggerSpecs.rule("KILL", List.of("actor == self"), dot);
    }
}
