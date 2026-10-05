package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 21021 Equivalent Exchange (光锥 21021 等价交换), skill 酣适: `当装备者的回合开始时，随机为 1 个当前能量百分比小于 50% 的我方其他目标
 * 恢复 8 点能量` (the original: `for a randomly chosen ally (excluding the wearer) whose current Energy is lower than 50%`).
 *
 * <p>Note: three controls settle three things at once: the wearer itself is not picked (`excluding the wearer`); a teammate at full energy is not picked (the threshold);
 * and only that 30% teammate is restored, by 8 points (superimposition 1).
 */
public class Cone21021EnergyTest {
    private static final int WEARER = 1003;
    private static final int ALLY_LOW = 1002;
    private static final int ALLY_FULL = 1004;
    private static final int MONSTER = 1002011;
    private static final int LEVEL = 80;
    private static final double AMOUNT = 8;

    /** Wears 21021 (superimposition 1), fires one TURN_START, and returns the energy gain of the {wearer, 30% teammate, full teammate}. */
    private static double[] gainsAtTurnStart() {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21021, LEVEL, false, 1));
        Character low = CharacterFactory.create(ALLY_LOW, LEVEL);
        Character full = CharacterFactory.create(ALLY_FULL, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, low, full), List.of(enemy), new Random(0));
        battle.startBattle();

        wearer.setCurrentEnergy(wearer.getMaxEnergy());          // Note: the wearer at full energy: if it were picked, that would be plainly visible
        low.setCurrentEnergy(low.getMaxEnergy() * 0.3);          // Note: below 50%
        full.setCurrentEnergy(full.getMaxEnergy());               // Note: not below 50%

        double w0 = wearer.getCurrentEnergy();
        double l0 = low.getCurrentEnergy();
        double f0 = full.getCurrentEnergy();
        battle.fireTriggers(TriggerEvent.TURN_START, wearer, enemy, 0, 0);
        return new double[]{wearer.getCurrentEnergy() - w0,
                low.getCurrentEnergy() - l0, full.getCurrentEnergy() - f0};
    }

    @Test
    public void onlyTheLowEnergyAllyIsPicked() {
        double[] gain = gainsAtTurnStart();
        System.out.println("[21021] wearer=" + gain[0] + " lowAlly=" + gain[1] + " fullAlly=" + gain[2]);
        Assertions.assertEquals(AMOUNT, gain[1], 1e-9,
                "the 30% ally is the only candidate, so it gains the tier-1 amount");
        Assertions.assertEquals(0.0, gain[2], 1e-9,
                "an ally at full energy is above the 50% threshold and must not be picked");
        Assertions.assertEquals(0.0, gain[0], 1e-9,
                "the wearer is excluded by the text itself: a randomly chosen ally (excluding the wearer)");
    }

    // There used to be another scene here, "only the wearer is below 50%" (plus a "no light cone" control). They were withdrawn, because that one
    // exposed a real engine gap, not a case written wrong:
    //
    //   require(...) throws when resolveTarget returns null
    //   "Effect targets "random_ally_below_half_energy" but this event has no such party"
    //
    // Note: while "pick one at random" may legitimately have no candidate (the wearer is the only one low on energy, so it is excluded, so the qualifying set is empty). The correct semantics is
    // "do nothing", and require treated that as an error. Note: that message is generic (one shared helper says the same sentence to every selector),
    // so it pointed the diagnosis at "the event type is wrong" -- measured, that was wrong (the 'no light cone' control got 0, which says those 8 points really do come from this clause).
    //
    // The gap has been closed (`8c2ba29`: `resolveTargets` returns an empty list for this one name, following the `lowest_hp_ally` precedent),
    // so that scene is back -- and it is the one that makes the mutation necessarily red, see the note below.
    @Test
    public void aLoneLowWearerIsStillExcluded() {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21021, LEVEL, false, 1));
        Character a = CharacterFactory.create(ALLY_LOW, LEVEL);
        Character b = CharacterFactory.create(ALLY_FULL, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, a, b), List.of(enemy), new Random(0));
        battle.startBattle();

        wearer.setCurrentEnergy(wearer.getMaxEnergy() * 0.3);   // Note: the only one below 50%
        a.setCurrentEnergy(a.getMaxEnergy());
        b.setCurrentEnergy(b.getMaxEnergy());

        double w0 = wearer.getCurrentEnergy();
        double a0 = a.getCurrentEnergy();
        double b0 = b.getCurrentEnergy();
        battle.fireTriggers(TriggerEvent.TURN_START, wearer, enemy, 0, 0);
        double w = wearer.getCurrentEnergy() - w0;
        double x = a.getCurrentEnergy() - a0;
        double y = b.getCurrentEnergy() - b0;
        System.out.println("[21021] lone-low wearer=" + w + " allyA=" + x + " allyB=" + y);
        // Note: this scene is for mutation probing: the qualifying set has the wearer as its only candidate (the teammates are all full), so whether "exclude the wearer" or
        // "below 50%" is torn out, the candidate becomes that one -- here the random degenerates into certainty, and the gain is no longer 0.
        Assertions.assertEquals(0.0, w, 1e-9, "the wearer is never a candidate, low or not");
        Assertions.assertEquals(0.0, x, 1e-9, "an ally at full energy is above the threshold");
        Assertions.assertEquals(0.0, y, 1e-9, "and so is the other one");
    }
}
