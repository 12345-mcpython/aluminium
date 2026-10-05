package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * ⭐ CHARACTERISATION of the {@code target_weakness_count} variable (2026-09-30, step two of the cone-22004 diagnosis): a rule
 * guarded by "the target is weak to at least one element" must fire for a monster that has weaknesses and NOT for one that has
 * none.
 *
 * <p>★ The event is fired by a real damage instance, so the target in the context is the unit that was hit -- which is the
 * question this step exists to answer, one question at a time.
 */
public class TargetWeaknessCountTest {
    private static final int UNIT = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int TWO_WEAKNESSES = 1002011;
    private static final int NO_WEAKNESS = 2024020;

    private boolean fires(int monster) {
        Character unit = CharacterFactory.create(UNIT, LEVEL);
        Enemy enemy = EnemyFactory.create(monster, 90, 1);
        EffectSpec pay = new EffectSpec();
        TriggerSpecs.set(pay, "op", "GAIN_ENERGY");
        TriggerSpecs.set(pay, "amount", 7.0);
        TriggerSpecs.set(pay, "target", "self");
        unit.setTriggerTable(new TriggerTable(UNIT, List.of(TriggerSpecs.rule("DEALING_DAMAGE",
                List.of("actor == self", "target_weakness_count >= 1"), pay))));
        Battle battle = new Battle(List.of(unit, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy), new Random(0));
        battle.startBattle();
        double before = unit.getCurrentEnergy();
        battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.FIRE, DamageType.NORMAL, 10));
        boolean fired = unit.getCurrentEnergy() > before;
        System.out.println("[weakness-var] monster " + monster + " (weaknesses=" + enemy.weaknessCount()
                + ") -> rule fired=" + fired);
        return fired;
    }

    @Test
    public void theVariableSeesTheTargetsWeaknesses() {
        Assertions.assertTrue(fires(TWO_WEAKNESSES), "a monster with weaknesses satisfies >= 1");
        Assertions.assertFalse(fires(NO_WEAKNESS), "and one with none does not (false case)");
    }
}
