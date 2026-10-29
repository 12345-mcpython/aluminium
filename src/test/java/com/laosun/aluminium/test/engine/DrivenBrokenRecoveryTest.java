package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.battle.BattleDriver;
import com.laosun.aluminium.battle.BattleListener;
import com.laosun.aluminium.battle.SkillPolicy;
import com.laosun.aluminium.buff.ControlBuff;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * A broken enemy must recover, and must act again once it has.
 *
 * <h2>The defect this pins</h2>
 * The driver decided whether a unit could act <b>before</b> asking whether it was broken:
 *
 * <pre>
 *   boolean mayAct = !actor.isDeath() &amp;&amp; actor.getBuffManager().canAct();
 *   if (mayAct &amp;&amp; actor instanceof Enemy broken &amp;&amp; broken.isBroken()) { ... }
 * </pre>
 *
 * <p>A broken unit reports {@code canAct() == false} — that is what being broken <i>means</i> — so for a unit that was
 * already broken when its turn began, {@code mayAct} was false, the broken branch never ran, and
 * {@code handleBrokenTurn} was never called. Its countdown therefore never ticked and it stayed broken <b>forever</b>:
 * a monster that stopped acting for the rest of the battle. Measured, three separate gaps in one twelve-turn trace.
 *
 * <p>It survived the engine's whole suite because nothing else covered "a broken enemy recovers" — the earlier case
 * only checked the skip. A rule with no test is a rule that can be deleted, or in this case broken, without anyone
 * noticing.
 *
 * <p>Note the split of responsibilities: a broken unit misses exactly the turns its countdown announces, and the
 * turn on which the countdown reaches zero is the one it recovers on and acts. It used to be skipped as well, which
 * made a break cost one action more than it said.
 */
public class DrivenBrokenRecoveryTest {

    private static final int LEVEL = 90;
    private static final int MONSTER = 1002011;
    private static final int HERO = 1003;

    /**
     * A broken enemy recovers, and acts on its first turn afterwards.
     *
     * <p>The assertion is the whole life cycle rather than just the skip: without the counting, the skip alone is
     * exactly what the softlock looked like from outside.
     */
    @Test
    public void aBrokenEnemyRecoversAndThenActsAgain() {
        Character hero = toughHero();
        Enemy foe = bigEnemy();

        BattleDriver driver = BattleDriver.of(List.of(hero), List.of(foe), 1L);
        Recorder recorder = new Recorder(foe);
        driver.withListener(recorder);
        driver.battle().startBattle();

        foe.breakEnemy(DamageElement.FIRE);
        foe.setBrokenRemainTurns(Constant.BROKEN_REMAIN_TURNS);

        // ⚠ `driver.step(turn)`, NOT `driver.step()`. The no-argument overload reports no turn number, and the
        // per-turn callbacks are gated on having one -- so a listener attached to a battle stepped without a number
        // observes NOTHING. That is what made this case report zero recoveries while the engine was recovering the
        // enemy over and over.
        for (int turn = 1; turn <= 40 && !driver.battle().isOver(); turn++) {
            driver.step(turn);
        }

        // ⚠ NOT "it is unbroken at the end". That was the first version of this assertion and it was wrong: the hero
        // keeps attacking, so the countdown cycles -- 2, 1, 0, recovered, broken again -- and by turn forty the enemy is
        // legitimately broken once more. A point-in-time check on a cycling state tests when the case stopped, not
        // whether it works.
        //
        // The softlock looked completely different, which is why the assertions are on the life cycle instead: the
        // countdown sat at its initial value forever and the enemy never recovered even once.
        Assertions.assertTrue(recorder.recoveries >= 1,
                "the enemy must recover at least once. It never did, so its countdown never ticked -- the softlock this "
                        + "case exists for. Broken at the end: " + foe.isBroken()
                        + ", remain " + foe.getBrokenRemainTurns());
        Assertions.assertTrue(recorder.turnsWhileBroken >= 1,
                "and it must have had turns while broken, or the skip was never exercised");
        Assertions.assertTrue(recorder.actionsAfterRecovery >= 1,
                "and it must act again once recovered -- the half the softlock destroyed");
        // ⚠ The precise claim, and the reason this case exists in this shape: the action lands on the RECOVERY TURN
        // itself, not on the next one. "It acted at some point after recovering" was already true under the old
        // contract, one turn later, so it could not tell the two apart -- a control proved that by reverting the
        // contract and leaving this case green.
        Assertions.assertEquals(recorder.recoveryTurn, recorder.firstActionTurnAfterRecovery,
                "the unit must act on the turn it recovers (turn " + recorder.recoveryTurn + "), not on a later one: "
                        + "its first action after recovering was on turn " + recorder.firstActionTurnAfterRecovery);
    }

    /**
     * A unit that is already broken at the start of its turn still gets its countdown ticked.
     *
     * <p>This is the precise shape of the ordering defect, stated as the observable it broke: the enemy is broken and
     * unable to act, and those two facts together must not stop the engine from counting its turns down.
     */
    @Test
    public void aBrokenUnitThatCannotActStillCountsDown() {
        Character hero = CharacterFactory.create(HERO, LEVEL);
        Enemy foe = bigEnemy();

        BattleDriver driver = BattleDriver.of(List.of(hero), List.of(foe), 1L);
        driver.battle().startBattle();
        foe.breakEnemy(DamageElement.FIRE);
        foe.setBrokenRemainTurns(5);            // more turns than the battle needs, so it must tick several times

        int start = foe.getBrokenRemainTurns();
        int seen = 0;
        for (int turn = 1; turn <= 30 && !driver.battle().isOver() && foe.isBroken(); turn++) {
            driver.step(turn);
            seen++;
        }

        Assertions.assertTrue(foe.getBrokenRemainTurns() < start,
                "the countdown must move: it started at " + start + " and is " + foe.getBrokenRemainTurns()
                        + " after " + seen + " turns, so the broken branch never ran");
    }

    /**
     * ⭐ A broken unit that is ALSO blocked still gets its countdown ticked.
     *
     * <p><b>This is the case that reproduces the defect</b>, and the trigger is narrower than "broken" alone. A broken
     * unit in this engine still reports {@code canAct() == true}, so the original ordering did not freeze it -- which is
     * why the two cases above stayed green when the fix was reverted, and why a negative control caught them being
     * decoration.
     *
     * <p>The freeze needs a unit that is broken <b>and</b> blocked by a control. Then {@code canAct()} is false, and the
     * original order asked that first:
     *
     * <pre>
     *   boolean mayAct = ... &amp;&amp; actor.getBuffManager().canAct();   // false, because blocked
     *   if (mayAct &amp;&amp; actor instanceof Enemy broken &amp;&amp; broken.isBroken()) { ... }   // never runs
     * </pre>
     *
     * <p>so {@code handleBrokenTurn} was never called, the countdown never ticked, and the unit stayed broken and silent
     * for the rest of the battle.
     *
     * <p>⚠ <b>The control must outlast the loop, and the first version of this case got that wrong.</b> It froze the
     * unit for nine turns and observed thirty, so the freeze expired, the countdown resumed, and the assertion passed
     * even with the defect restored -- a case that could not fail. The window now sits entirely inside the control.
     *
     * <p>Measured in the battle that exposed it: {@code 基层员工 mayAct=false broken=true remain=2}, frozen at 2 for the
     * rest of the fight under the original order and counted down under the fix.
     */
    @Test
    public void aBrokenUnitThatIsAlsoFrozenStillCountsDown() {
        Character hero = toughHero();
        Enemy foe = bigEnemy();

        BattleDriver driver = BattleDriver.of(List.of(hero), List.of(foe), 1L);
        driver.battle().startBattle();

        // Broken AND blocked, with the block long enough to cover every turn observed below.
        foe.breakEnemy(DamageElement.FIRE);
        foe.setBrokenRemainTurns(20);
        foe.getBuffManager().addBuff(new ControlBuff(Constant.CONTROL_EFFECTS.get("FROZEN"), 60));

        Assertions.assertFalse(foe.getBuffManager().canAct(),
                "precondition: the freeze must actually block it, or this case cannot reproduce the defect");

        int start = foe.getBrokenRemainTurns();
        int attacksByUs = 0;
        for (int turn = 1; turn <= 15 && !driver.battle().isOver(); turn++) {
            driver.step(turn);
        }

        Assertions.assertFalse(foe.getBuffManager().canAct(),
                "precondition: it must still be blocked at the end, or the window escaped the control and the case "
                        + "cannot distinguish the two orderings");
        Assertions.assertTrue(foe.getBrokenRemainTurns() < start,
                "the countdown must move while the unit is broken AND blocked: it started at " + start + " and is "
                        + foe.getBrokenRemainTurns() + " after the window. Unchanged means the broken branch never ran, "
                        + "which is the softlock this case exists for");
    }

    // ================================================================== helpers

    /**
     * Counts the enemy's turns while broken and its actions once recovered.
     */
    private static final class Recorder implements BattleListener {

        private final Enemy foe;
        private boolean recovered;
        int turnsWhileBroken;
        int actionsAfterRecovery;
        int recoveries;
        int recoveryTurn = -1;
        int firstActionTurnAfterRecovery = -1;
        private int currentTurn = -1;

        private Recorder(Enemy foe) {
            this.foe = foe;
        }

        @Override
        public void onTurnStart(CanHit actor, int turn) {
            currentTurn = turn;
            if (actor != foe) {
                return;
            }
            // Ask the ACTOR, not the enemy object we happen to hold: they are the same object here, but reading the live
            // flag is what made the first version of this count report zero.
            if (actor instanceof Enemy e && e.isBroken()) {
                turnsWhileBroken++;
            } else {
                if (!recovered) {
                    recoveries++;               // the first turn it came back
                    recoveryTurn = turn;
                }
                recovered = true;
            }
        }

        @Override
        public void onAction(CanHit actor, SkillPolicy.Decision decision) {
            if (actor != foe || !recovered) {
                return;
            }
            actionsAfterRecovery++;
            if (firstActionTurnAfterRecovery < 0) {
                firstActionTurnAfterRecovery = currentTurn;
            }
        }
    }

    /**
     * A hero that cannot die inside the case.
     *
     * <p>⚠ Without this the battle can end before the enemy's next turn, which makes "it acted after recovering"
     * untestable -- and a case that cannot reach its own assertion is worse than no case, because it looks like one.
     *
     * @return the hero
     */
    private static Character toughHero() {
        Character hero = CharacterFactory.create(HERO, LEVEL);
        hero.setAttribute(AttributeType.HEALTH, new DoubleValue(500_000));
        hero.heal(500_000);
        return hero;
    }

    /**
     * An enemy that cannot die inside the case, so it keeps getting turns.
     *
     * @return the enemy
     */
    private static Enemy bigEnemy() {
        Enemy e = EnemyFactory.create(MONSTER, 90, 1);
        e.setAttribute(AttributeType.HEALTH, new DoubleValue(500_000));
        e.heal(500_000);
        e.setMaxEnergy(0);
        return e;
    }

}
