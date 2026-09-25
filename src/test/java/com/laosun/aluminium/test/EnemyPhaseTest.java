package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemySkill;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * P9-5: an enemy swaps its skill at an HP threshold.
 *
 * <p>The design point worth pinning is that <b>nothing has to be scheduled</b>: {@code activeSkill()} reads
 * the current HP each time it is asked, so there is no transition flag and no "hold the bar at 1 HP"
 * trick — which is the approach the roadmap forbids, because {@code takeDamage} marks the enemy dead the
 * moment HP reaches 0, so a locked bar either skips the phase or lets a corpse be hit.
 *
 * <p>⚠ This covers <b>threshold switching only</b>. A multi-HP-bar boss additionally needs its bar locked
 * and explicitly reset ({@code setInvulnerable(true)} then set HP), which is a separate item.
 */
public class EnemyPhaseTest {

    private static final double MAX_HP = 10_000;
    private static final double EPS = 1e-9;

    /** Ice Edge's real template, but with a small HP pool so a percentage is easy to set up. */
    private static Enemy enemy() {
        Enemy enemy = com.laosun.aluminium.models.EnemyFactory.create(1002011, 90, 1);
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(MAX_HP));
        enemy.heal(MAX_HP);
        return enemy;
    }

    private static EnemySkill skill(String name) {
        // The element/segments are irrelevant to this test; identity is what is asserted.
        return new EnemySkill(DamageElement.FIRE, 1.0, 1, DamageType.NORMAL);
    }

    /** With no phase registered, the enemy has no phase behaviour and the caller falls back. */
    @Test
    public void withoutAPhaseThereIsNothingToSwitchTo() {
        Enemy enemy = enemy();

        Assertions.assertEquals(0, enemy.phaseCount());
        Assertions.assertNull(enemy.activeSkill(),
                "null means 'no phase applies'; the caller uses the ordinary skill");
    }

    /** The phase takes over at its threshold and not before. */
    @Test
    public void aPhaseTakesOverAtItsThreshold() {
        Enemy enemy = enemy();
        EnemySkill enraged = skill("enraged");
        enemy.setPhaseSkill(0.5, enraged);

        Assertions.assertNull(enemy.activeSkill(), "at full HP the phase has not started");

        enemy.takeDamage(MAX_HP * 0.3);                       // down to 70%
        Assertions.assertNull(enemy.activeSkill(), "at 70% HP the phase has still not started");

        enemy.takeDamage(MAX_HP * 0.3);                       // down to 40%
        Assertions.assertSame(enraged, enemy.activeSkill(), "at 40% HP the phase is active");

        enemy.takeDamage(MAX_HP * 0.3);                       // down to 10%
        Assertions.assertSame(enraged, enemy.activeSkill(), "and it stays active below the threshold");
    }

    /**
     * With several phases, the <b>tightest</b> one wins — a boss at 20% must use its 20% skill, not the one
     * it unlocked at 80%.
     *
     * <p>This is the assertion that pins the ascending sort and the first-match loop; getting the direction
     * wrong is invisible until a boss is nearly dead and behaves like its healthiest self.
     */
    @Test
    public void theTightestPhaseWins() {
        Enemy enemy = enemy();
        EnemySkill early = skill("early");
        EnemySkill late = skill("late");
        // Registered out of order on purpose, to show the sort is what makes the choice correct.
        enemy.setPhaseSkill(0.3, late);
        enemy.setPhaseSkill(0.8, early);

        enemy.takeDamage(MAX_HP * 0.5);                       // 50%: only the 0.8 phase qualifies
        Assertions.assertSame(early, enemy.activeSkill(), "at 50% the 80% phase is the tightest one met");

        enemy.takeDamage(MAX_HP * 0.3);                       // 20%: both qualify, the 0.3 one wins
        Assertions.assertSame(late, enemy.activeSkill(),
                "at 20% the tighter phase must win, not the one unlocked earlier");
    }

    /** Exactly at the threshold counts as "at or below" — an off-by-one here would delay a phase. */
    @Test
    public void theThresholdItselfCounts() {
        Enemy enemy = enemy();
        EnemySkill enraged = skill("enraged");
        enemy.setPhaseSkill(0.5, enraged);

        enemy.takeDamage(MAX_HP * 0.5);                        // exactly 50%
        Assertions.assertEquals(0.5, enemy.getCurrentHp() / enemy.getMaxHp(), EPS,
                "the premise: the enemy is exactly at the threshold");
        Assertions.assertSame(enraged, enemy.activeSkill(), "50% is 'at or below 50%'");
    }

    /** A null skill is ignored rather than stored as a phase that would select nothing. */
    @Test
    public void aNullSkillIsNotRegistered() {
        Enemy enemy = enemy();
        enemy.setPhaseSkill(0.5, null);

        Assertions.assertEquals(0, enemy.phaseCount());
    }
}
