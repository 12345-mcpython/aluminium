package com.laosun.aluminium.test.content.characters.natasha;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Natasha's Eidolon 4 (妙手回春): 「受到攻击后，额外恢复5点能量。」
 *
 * <p>Two failures are possible and both are measured rather than assumed:
 *
 * <ul>
 *   <li><b>The gate.</b> The clause belongs to Eidolon 4, so at rank 0 no bonus is paid. The same hit at rank 0 and
 *       rank 4 is the contrast -- and it is stated as a <b>difference of differences</b>, because taking a hit already
 *       pays {@link Constant#ENERGY_GAIN_HIT} energy on its own. Measuring the raw gain would attribute that 10 to
 *       the talent.</li>
 *   <li><b>Whose hit.</b> {@code TAKING_HIT} is fired with the <b>attacker</b> as actor and the <b>victim</b> as
 *       target (Battle.java:1730), so the rule reads {@code when: ["target == self"]}. Without it, a hit on a
 *       teammate would top Natasha up from across the field -- and since the baseline gain goes to the victim, that
 *       error would look like a plausible number rather than an absurd one.</li>
 * </ul>
 */
public class NatashaEidolon4Test {

    private static final int NATASHA = 1105;
    private static final int TEAMMATE = 1109;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /**
     * At Eidolon 4 she gains the hit's usual energy <b>plus 5</b>; at Eidolon 0, only the usual.
     */
    @Test
    public void theBonusEnergyIsFiveAndOnlyAtEidolonFour() {
        double rank4 = energyGainedFromHitOnHer(4);
        double rank0 = energyGainedFromHitOnHer(0);

        Assertions.assertEquals(Constant.ENERGY_GAIN_HIT, rank0, 1e-9,
                "a hit always pays the standard gain; at Eidolon 0 that is all it pays");
        Assertions.assertEquals(5.0, rank4 - rank0, 1e-9,
                "「受到攻击后，额外恢复5点能量」 -- the rule's own share, measured against the same hit at "
                        + "Eidolon 0: " + rank4 + " vs " + rank0);
    }

    /**
     * A hit on a <b>teammate</b> pays her nothing at all.
     *
     * <p>The baseline hit gain goes to whoever was hit, so a hit on an ally credits <b>that ally</b>, not Natasha.
     * Her own energy therefore must not move, and it must not move by the bonus either -- a rule missing
     * {@code target == self} would pay her from across the field, which is the failure this case exists for.
     *
     * <p>Contrasted with the same hit on herself at the same rank, so the 15 she gains there is visibly the
     * {@link Constant#ENERGY_GAIN_HIT} plus the rule's 5 rather than the rule alone.
     */
    @Test
    public void aHitOnATeammatePaysHerNothing() {
        double onMate = energyGainedFromHitOnTeammate(4);
        double onHer = energyGainedFromHitOnHer(4);

        Assertions.assertEquals(0.0, onMate, 1e-9,
                "an ally's hit credits that ally; the rule is about hits on HER, so she gains nothing");
        Assertions.assertEquals(Constant.ENERGY_GAIN_HIT + 5.0, onHer, 1e-9,
                "a hit on her pays the standard gain plus the rule's 5: " + onHer);
    }

    /**
     * Energy Natasha gains when one hit lands on her.
     *
     * @param eidolon her Eidolon rank
     * @return the energy credited
     */
    private static double energyGainedFromHitOnHer(int eidolon) {
        return energyGained(eidolon, true);
    }

    /**
     * Energy Natasha gains when one hit lands on a teammate.
     *
     * @param eidolon her Eidolon rank
     * @return the energy credited
     */
    private static double energyGainedFromHitOnTeammate(int eidolon) {
        return energyGained(eidolon, false);
    }

    /**
     * Builds the battle, lands one hit on the chosen victim and reports what Natasha gained.
     *
     * @param eidolon    her Eidolon rank
     * @param targetHer  {@code true} to hit Natasha, {@code false} to hit her teammate
     * @return the energy credited to Natasha
     */
    private static double energyGained(int eidolon, boolean targetHer) {
        Character natasha = CharacterFactory.create(NATASHA, LEVEL, false, null, null, eidolon);
        Character mate = CharacterFactory.create(TEAMMATE, LEVEL, false, null, null, 0);
        Battle battle = new Battle(List.of(natasha, mate),
                List.of(EnemyFactory.create(MONSTER, LEVEL, 1)), BattleFixture.alwaysRoll(1.0));
        battle.startBattle();

        Character victim = targetHer ? natasha : mate;
        double before = natasha.getCurrentEnergy();

        Enemy attacker = battle.enemyUnits().getFirst();
        battle.applyDamage(victim, new Damage(attacker, victim, DamageElement.PHYSICAL, DamageType.NORMAL, 100));

        return natasha.getCurrentEnergy() - before;
    }
}
