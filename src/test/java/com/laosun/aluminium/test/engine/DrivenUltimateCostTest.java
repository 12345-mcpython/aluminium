package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.battle.BattleDriver;
import com.laosun.aluminium.models.battle.BattleListener;
import com.laosun.aluminium.models.battle.SkillPolicy;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * An ultimate cast through {@link BattleDriver} must SPEND its energy.
 *
 * <h2>The bug this pins, and why it was invisible</h2>
 * {@code Battle.castUltra} is the only entry that pays for an ultimate -- it reads the bar, then zeroes it
 * <b>before</b> the ultimate body settles, deliberately, so the kill and break energy the ultimate itself earns is
 * not wiped by the zeroing. {@code useSkill} never touches energy at all.
 *
 * <p>{@code BattleDriver.cast} routed <b>every</b> action through {@code performAction} → {@code useSkill},
 * including ultimates. So on the driven path ultimates were <b>free</b>: measured, 姬子 fired her 120-cost ultimate
 * four times in one fight with her bar reading 120 before <b>and</b> 120 after every cast, while calling
 * {@code castUltra} directly in isolation drained it correctly to 15.
 *
 * <p>It survived because the hand-written demo calls {@code castUltra} itself, so the engine's own battle could not
 * show it. It appeared the moment the engine was given the choice -- which is exactly what the driver is for.
 */
public class DrivenUltimateCostTest {

    private static final int HIMEKO = 1003;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /**
     * Casting an ultimate through the driver leaves the caster's bar short of full.
     *
     * <p>Asserted as "the bar moved down" rather than against an exact figure: what the ultimate then earns back
     * during its own settlement is a separate rule, and pinning the net number would pin that rule here too.
     */
    @Test
    public void theDriverPaysForAnUltimate() {
        Character hero = CharacterFactory.create(HIMEKO, LEVEL);
        Capture capture = new Capture(hero);

        BattleDriver driver = BattleDriver.of(List.of(hero), List.of(enemy()), 1L).withListener(capture);
        driver.battle().startBattle();
        hero.gainEnergy(hero.getMaxEnergy());
        Assertions.assertTrue(driver.battle().isUltraReady(hero), "precondition: the ultimate is ready");

        // ⚠ `run()`, not a loop of `step()`: the bare `step()` delegates with turn = -1, and the driver only notifies
        // its listener when it has a turn number -- so a stepping caller observes nothing at all, which cost a round
        // of confusion here. `run()` is the entry that reports turns.
        driver.run();

        Assertions.assertTrue(capture.sawUltimate,
                "precondition: the policy chose the ultimate at some point in this fight");
        Assertions.assertNotNull(capture.energyBefore, "the bar was read before that cast");
        Assertions.assertTrue(capture.energyAfter < capture.energyBefore - 1,
                "an ultimate must SPEND its energy: before=" + capture.energyBefore
                        + " after=" + capture.energyAfter
                        + " -- routing it through useSkill made it free");
    }

    /**
     * A ready ultimate is not still ready immediately after being cast.
     *
     * <p>The observable consequence of the bug, stated the way a player would notice it.
     */
    @Test
    public void theUltimateIsNotStillReadyAfterBeingCast() {
        Character hero = CharacterFactory.create(HIMEKO, LEVEL);
        Enemy foe = enemy();
        Battle battle = new Battle(List.of(hero), List.of(foe), BattleFixture.seeded());
        battle.startBattle();
        hero.gainEnergy(hero.getMaxEnergy());

        Skill ultra = hero.getSkills().get(SkillType.ULTRA);
        Assertions.assertTrue(battle.isUltraReady(hero), "precondition: it is ready");

        boolean cast = battle.castUltra(hero, List.of(foe));

        Assertions.assertTrue(cast, "the ultimate was cast");
        Assertions.assertFalse(battle.isUltraReady(hero),
                "after paying for the ultimate the bar cannot still count as full");
    }

    /**
     * An ultimate that reaches the ORDINARY action path cannot be cast for free.
     *
     * <h2>Why this case is the important one</h2>
     * The bug was not "the driver forgot"; it was that <b>the cost lived in a sibling method</b>, so any caller
     * reaching {@code useSkill} directly got an ultimate for nothing -- and {@code BattleDriver} did exactly that,
     * for every action. Fixing the driver alone would have left the same trap for the next front end.
     *
     * <p>So the rule is now enforced where the cast happens: an ultimate arriving at {@code useSkill} must be ready,
     * and its bar is spent by the same path {@code castUltra} uses. This pins that <b>from the outside</b> -- through
     * {@code useSkill}, the path that used to be free -- rather than through the entry that was always correct.
     */
    @Test
    public void anUltimateCannotBeCastForFreeThroughTheOrdinaryPath() {
        Character hero = CharacterFactory.create(HIMEKO, LEVEL);
        Enemy foe = enemy();

        Battle battle = new Battle(List.of(hero), List.of(foe), BattleFixture.seeded());
        battle.startBattle();
        battle.currentMove = new Signal(hero);

        // (a) not ready: the ordinary path must REFUSE rather than hand out a free ultimate.
        Assertions.assertFalse(battle.isUltraReady(hero), "precondition: the bar starts empty");
        Assertions.assertFalse(battle.useSkill(hero.getSkills().get(SkillType.ULTRA), List.of(foe)),
                "an ultimate with an empty bar must be refused through useSkill, not cast for free");

        // (b) ready: casting it through the ordinary path must SPEND the bar.
        hero.gainEnergy(hero.getMaxEnergy());
        Assertions.assertTrue(battle.isUltraReady(hero), "precondition: the bar is full");
        double before = hero.getCurrentEnergy();

        Assertions.assertTrue(battle.useSkill(hero.getSkills().get(SkillType.ULTRA), List.of(foe)),
                "a ready ultimate is cast through the ordinary path");

        Assertions.assertTrue(hero.getCurrentEnergy() < before - 1,
                "and it PAYS: before=" + before + " after=" + hero.getCurrentEnergy());
    }

    /**
     * An enemy with its HP raised, so a case cannot end early.
     *
     * @return the enemy
     */
    private static Enemy enemy() {
        Enemy e = EnemyFactory.create(MONSTER, 90, 1);
        e.setAttribute(AttributeType.HEALTH, new DoubleValue(200_000));
        e.heal(200_000);
        e.setMaxEnergy(0);
        return e;
    }

    /**
     * Watches for the ultimate and reads the caster's bar around it.
     */
    private static final class Capture implements BattleListener {

        private final Character hero;
        private boolean sawUltimate;
        private Double energyBefore;
        private double energyAfter;

        private Capture(Character hero) {
            this.hero = hero;
        }

        @Override
        public void onAction(CanHit actor, SkillPolicy.Decision decision) {
            if (actor == hero && decision.skill() == hero.getSkills().get(SkillType.ULTRA)) {
                sawUltimate = true;
                // Read BEFORE the cast settles: the driver checks readiness, asks the policy, then casts.
                energyBefore = hero.getCurrentEnergy();
            }
        }

        @Override
        public void onTurnEnd(CanHit actor, int turn) {
            if (sawUltimate && energyBefore != null && energyAfter == 0) {
                energyAfter = hero.getCurrentEnergy();
            }
        }
    }
}
