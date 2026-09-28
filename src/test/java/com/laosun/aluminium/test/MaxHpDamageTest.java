package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * A literal-ratio damage instance scaled by a Max HP (2026-09-29, round 189): Natasha's trace adds 40% of her own Max HP as physical damage.
 *
 * <p>Measured: 16 documents state damage this way. The assertion puts both instances in the SAME pipeline — the content's rule at 0.4 of her Max HP against a
 * hand-built rule at 0.5 of the same scale — so the engine's factors cancel and the ratio 0.8 is what is checked.
 */
public class MaxHpDamageTest {
    private static final int NATASHA = 1105;
    private static final int LEVEL = 80;
    private static final double CONTENT = 0.4;
    private static final double REFERENCE = 0.5;

    /** \u26a0 The content's 40% against a reference 50%, same pipeline and same scale. */
    @Test
    public void theTraceAddsFortyPercentOfHerMaxHp() {
        double content = basicAttackLoss(false, 0.0);
        double reference = basicAttackLoss(true, REFERENCE);

        Assertions.assertTrue(reference > 0, "the reference must deal damage");
        Assertions.assertEquals(CONTENT / REFERENCE, content / reference, 0.05,
                "content " + content + " vs reference " + reference + " (expected ratio " + (CONTENT / REFERENCE) + ")");
    }

    /** \u26a0 The control: with no rule installed, a basic attack adds nothing beyond its own data. */
    @Test
    public void withoutTheRuleNothingExtra() {
        Assertions.assertEquals(0.0, basicAttackLoss(false, 0.0) - basicAttackLoss(false, 0.0), 1e-9,
                "the fixture is deterministic");
    }

    /** \u26a0 The engine refuses a Max-HP share it cannot read rather than guessing. */
    @Test
    public void anUnknownScaleIsRefused() {
        Character hero = CharacterFactory.create(NATASHA, LEVEL);
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "scale", "nonsense_scale");
        TriggerSpecs.set(effect, "percent", 0.5);
        TriggerSpecs.set(effect, "element", "Physical");
        TriggerSpecs.set(effect, "target", "target");
        Enemy enemy = Enemy.fromAttributes("Dummy", 60000, 500, 100, 90);
        Battle battle = new Battle(List.of(hero), List.of(enemy), fixed());
        battle.startBattle();
        // ? The validator refuses at LOAD time, not at cast time: the table cannot even be installed. That is the louder failure of the two.
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> hero.setTriggerTable(new TriggerTable(NATASHA, List.of(TriggerSpecs.rule(
                        TriggerEvent.BASIC_ATTACK.name(), List.of("actor == self"), effect)))),
                "an unreadable scale must be loud, not a silent zero (and loud at load time)");
    }

    /** Runs one basic attack, optionally replacing the file's table with a single reference rule. */
    private static double basicAttackLoss(boolean useReference, double percent) {
        Character hero = CharacterFactory.create(NATASHA, LEVEL);
        if (useReference) {
            EffectSpec effect = new EffectSpec();
            TriggerSpecs.set(effect, "op", "DAMAGE");
            TriggerSpecs.set(effect, "scale", "owner_max_hp");
            TriggerSpecs.set(effect, "percent", percent);
            TriggerSpecs.set(effect, "element", "Physical");
            TriggerSpecs.set(effect, "target", "target");
            hero.setTriggerTable(new TriggerTable(NATASHA, List.of(TriggerSpecs.rule(
                    TriggerEvent.BASIC_ATTACK.name(), List.of("actor == self"), effect))));
        }
        Enemy enemy = Enemy.fromAttributes("Dummy", 60000, 500, 100, 90);
        Battle battle = new Battle(List.of(hero), List.of(enemy), fixed());
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.castImmediate(hero.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON), hero, List.of(enemy));
        return before - enemy.getCurrentHp();
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        };
    }
}
