package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.buffs.CounterMechanic;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * P9-5: counter-attack.
 *
 * <p>A unit wearing {@link CounterMechanic} hits whoever causes it to lose HP back — the enemy-side
 * mirror of Clara's counter, so bosses are no longer only something to be hit. Each test compares against
 * an identically-built battle without the buff, so the measurement is "what the counter added" rather
 * than an absolute damage figure that relic and trace bonuses would make meaningless.
 */
public class BossMechanicTest {

    private static final int HIMEKO = 1003;
    private static final int ICE_EDGE = 1002011;
    private static final int ENEMY_LEVEL = 90;
    private static final int CHARACTER_LEVEL = 80;
    private static final int DURATION = 5;

    /** Counter damage = the wearer's ATK × this. */
    private static final double RATIO = 0.5;

    // ==================================================================

    /**
     * The wearer counters the one who hit them.
     *
     * <p>The control battle is identical except that the enemy does not wear the buff, so the attacker's HP
     * loss in the tested battle <b>is</b> the counter.
     */
    @Test
    public void theWearerCountersWhoeverHitThem() {
        Battle control = newBattle(false);
        Assertions.assertEquals(heroMaxHp(control), heroHp(control), "the control hero is untouched");

        Battle countered = newBattle(true);
        Assertions.assertTrue(heroHp(countered) < heroMaxHp(countered),
                "attacking a unit that counters must cost the attacker HP");
    }

    /**
     * The counter is <b>additional</b> damage, so it does not count as an attack: the one it lands on gains
     * no energy from it.
     *
     * <p>This is the same contract Clara's counter has, and it is easy to get wrong by routing a counter
     * through a normal attack path.
     */
    @Test
    public void theCounterDoesNotFeedTheOneItHits() {
        // ⚠ Measured as a differential against the control, NOT as "the hero's energy did not move": the
        // hero's own attack credits hit energy, so the hero's energy always rises. Both battles perform the
        // same attack, so any difference between them can only come from the counter.
        Battle control = newBattle(false);
        Battle countered = newBattle(true);

        Assertions.assertTrue(heroHp(countered) < heroHp(control), "the counter really landed");
        Assertions.assertEquals(control.characters.getFirst().getCurrentEnergy(),
                countered.characters.getFirst().getCurrentEnergy(), 1e-9,
                "additional damage grants the victim no energy, so the counter must not have credited any");
    }

    /**
     * A mutual pair of counters resolves without blowing up, and the first counter still lands.
     *
     * <p>⚠ <b>This test does not measure the thing that is wrong.</b> It watches the <i>hero's</i> HP,
     * which is the same whether or not both sides wear a counter (237.23 in both cases). The defect shows
     * up on the <b>enemy's</b> side: with both sides wearing a counter the enemy loses ~5902 where the
     * hero's single attack explains ~260 — i.e. the reaction fires far too often. The measurement and the
     * open question are written up in {@code CounterMechanic}'s Javadoc; whoever picks this up should
     * assert on the enemy's HP loss, which is the observable that actually moves.
     */
    @Test
    public void aMutualPairOfCountersResolves() {
        Battle battle = newBattle(true);
        Character hero = battle.characters.getFirst();
        hero.getBuffManager().addBuff(new CounterMechanic(DURATION, DamageElement.PHYSICAL, RATIO));

        Assertions.assertDoesNotThrow(() -> attack(battle),
                "two counters facing each other must not blow the stack");
        Assertions.assertTrue(heroHp(battle) < heroMaxHp(battle), "and the exchange really happened");
    }

    /** A wearer that is already down does not counter — a corpse does not hit back. */
    @Test
    public void aDeadWearerDoesNotCounter() {
        Battle battle = newBattle(false);
        Enemy enemy = battle.enemies.getFirst();
        enemy.getBuffManager().addBuff(new CounterMechanic(DURATION, DamageElement.PHYSICAL, RATIO));
        enemy.takeDamage(enemy.getMaxHp() * 2);
        Assertions.assertTrue(enemy.isDeath(), "the premise: the wearer is dead");

        attack(battle);

        Assertions.assertEquals(heroMaxHp(battle), heroHp(battle), 1e-9,
                "the dead enemy must not have countered");
    }

    /** Losing HP for a reason with no author (a DOT whose applier is gone) must not throw. */
    @Test
    public void anAttributelessLossDoesNotThrow() {
        Battle battle = newBattle(true);
        Enemy enemy = battle.enemies.getFirst();

        Assertions.assertDoesNotThrow(() -> enemy.takeDamage(100),
                "direct HP loss has no source, so there is nobody to counter");
    }

    /**
     * The counter chain is bounded to <b>one exchange</b>: the answer to a counter is refused.
     *
     * <p>This is the assertion the earlier version of this class should have made. It watches the
     * <b>enemy's</b> HP, the observable that actually moves, rather than the hero's, which is identical
     * whether or not the chain is bounded — that mistake let the previous version pass while proving
     * nothing, and led to a defect being reported that did not exist.
     *
     * <p>Traced with temporary instrumentation (since removed), both sides wearing a counter and one basic
     * attack produce exactly four asks:
     *
     * <pre>
     *   ENEMY-counter  target=enemy  source=hero    -> reacts   (the hero's attack)
     *   HERO-counter   target=hero   source=enemy   -> refuses  (runCounter depth is already 1)
     *   ENEMY-counter  target=hero   source=enemy   -> skips    (owner check: not my loss)
     *   HERO-counter   target=enemy  source=hero    -> skips    (owner check)
     * </pre>
     *
     * So the enemy takes the attack and nothing comes back: its HP loss equals the case where only the
     * enemy counters. Remove {@code Battle.runCounter}'s nesting check and the hero's answer lands, which
     * is what turns this test red.
     */
    @Test
    public void aCounterAnsweringACounterIsRefused() {
        double enemyCountersOnly = enemyLost(newBattle(true, false));
        double bothCounter = enemyLost(newBattle(true, true));

        Assertions.assertTrue(enemyCountersOnly > 0, "the premise: the hero's attack really landed");
        Assertions.assertEquals(enemyCountersOnly, bothCounter, enemyCountersOnly * 1e-9,
                "a counter must not answer a counter, so the enemy takes the same damage either way");
    }

    // ==================================================================

    /** One real character against one real enemy; the flags decide who wears a counter. */
    private static Battle newBattle(boolean countering) {
        return newBattle(countering, false);
    }

    private static Battle newBattle(boolean enemyCounters, boolean heroCounters) {
        Character hero = CharacterFactory.create(HIMEKO, CHARACTER_LEVEL);
        Enemy enemy = EnemyFactory.create(ICE_EDGE, ENEMY_LEVEL, 1);
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(1_000_000));
        enemy.heal(1_000_000);
        Battle battle = new Battle(List.of(hero), List.of(enemy), new Random(0));
        battle.startBattle();
        // Deterministic damage: no crit rolls to compare around.
        hero.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0));
        enemy.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0));
        if (enemyCounters) {
            enemy.getBuffManager().addBuff(new CounterMechanic(DURATION, DamageElement.PHYSICAL, RATIO));
        }
        if (heroCounters) {
            hero.getBuffManager().addBuff(new CounterMechanic(DURATION, DamageElement.PHYSICAL, RATIO));
        }
        attack(battle);
        return battle;
    }

    private static double enemyLost(Battle battle) {
        return battle.enemies.getFirst().getMaxHp() - battle.enemies.getFirst().getCurrentHp();
    }

    /** The hero's basic attack on the enemy. */
    private static void attack(Battle battle) {
        battle.castImmediate(battle.characters.getFirst().getSkills().get(SkillType.COMMON),
                battle.characters.getFirst(), List.of(battle.enemies.getFirst()));
    }

    private static double heroHp(Battle battle) {
        return battle.characters.getFirst().getCurrentHp();
    }

    private static double heroMaxHp(Battle battle) {
        return battle.characters.getFirst().getMaxHp();
    }
}
