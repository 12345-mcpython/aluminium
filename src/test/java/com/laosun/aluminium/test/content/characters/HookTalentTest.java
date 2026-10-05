package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1109 Hook's talent: the extra damage instance, and the guard that stops it from re-triggering itself.
 *
 * <p>The clause reacts to a hit on a burning target by dealing one more Fire instance and granting 5 energy. That extra
 * instance is a damage instance too, so without a guard the rule would react to itself until the engine's trigger depth
 * limit threw. The guard is the positive condition `damage_is_attack`, which is TRUE for an ordinary attack and FALSE
 * for additional damage.
 *
 * <p>Note: <b>Why this file was rewritten (round 4 of the current goal).</b> It used to assert
 * {@code energy >= before + 5} after one basic attack -- but a basic attack grants energy on its own, so the assertion was
 * satisfied whether or not the talent fired. Measured: with {@code damage_is_attack} inverted (its implementation until
 * 2026-09-30) the talent did NOT fire on ordinary attacks, and this test stayed green. The reading is now the
 * <b>difference</b> between attacking a burning target and attacking a plain one: only the talent can produce it.
 *
 * <p>Note: <b>That this test finishes is itself the assertion about recursion.</b> If the guard were missing or
 * ineffective, the engine would raise "Trigger recursion exceeded" and the test would fail with that, not with a number.
 */
public class HookTalentTest {
    private static final int HOOK = 1109;
    private static final int LEVEL = 80;
    private static final double TALENT_ENERGY = 5;

    /** The energy one basic attack gains, with and without the burning target the talent needs. */
    private double energyFromOneBasicAttack(boolean burning) {
        Character hook = CharacterFactory.create(HOOK, LEVEL);
        Enemy enemy = Enemy.fromAttributes("Test Dummy", 20000, 100, 100, 90);
        Battle battle = new Battle(List.of(hook), List.of(enemy), new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });
        battle.startBattle();
        if (burning) {
            battle.castImmediate(hook.getSkills().get(SkillType.SKILL), hook, List.of(enemy));
            Assertions.assertTrue(enemy.getBuffManager().hasState("灼烧"), "precondition: the target burns");
        }
        double before = hook.getCurrentEnergy();
        battle.castImmediate(hook.getSkills().get(SkillType.COMMON), hook, List.of(enemy));
        double gained = hook.getCurrentEnergy() - before;
        Assertions.assertTrue(hook.getMaxEnergy() > gained, "precondition: the bar is not clipped by the cap");
        return gained;
    }

    @Test
    public void theTalentPaysFiveEnergyThatTheAttackAloneDoesNot() {
        double plain = energyFromOneBasicAttack(false);
        double burning = energyFromOneBasicAttack(true);
        System.out.println("[1109] energy from one basic attack: burning=" + burning + " plain=" + plain
                + " difference=" + (burning - plain));
        Assertions.assertEquals(TALENT_ENERGY, burning - plain, 1e-6,
                "the talent's 5 energy is the DIFFERENCE -- the attack's own gain cancels out, so this reading is "
                        + "attributable to the talent and to nothing else");
    }
}
