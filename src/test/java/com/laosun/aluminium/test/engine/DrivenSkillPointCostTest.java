package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.entity.CanHit;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.battle.BattleDriver;
import com.laosun.aluminium.models.battle.BattleListener;
import com.laosun.aluminium.models.battle.SkillPolicy;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * A Skill costs the driver exactly ONE point, the same as it costs anywhere else.
 *
 * <h2>The bug this pins</h2>
 * {@code Battle.useSkill} charges through {@code skillPointPolicy.onSkillCast} for <b>every</b> skill -- the charge is
 * about deciding to act, not about which pipeline settles the effect. {@code BattleDriver.cast} charged non-damaging
 * skills <b>again</b>, on the stated belief that a healing or shielding skill "is executed by SkillExecutor rather
 * than through the damage pipeline, so nothing charges it on the way in". That belief holds for the hand-written
 * demo (which calls {@code skill.execute} directly) but not for the driver (which goes through {@code useSkill}), so
 * every heal and shield on the driven path cost <b>double</b>: a Skill took 3 → 2 through {@code useSkill} alone and
 * 3 → 1 through the driver.
 *
 * <p>Same shape as the free-ultimate bug next door -- a cost the caller is trusted to pay. The rule is now that
 * <b>the engine charges and a caller must not</b>.
 *
 * <h2>⚠ Two traps this case had to be written around, both found by the negative control</h2>
 * <ol>
 *   <li><b>The pool at turn end is not the cost.</b> A turn ends with a skill point restored, so measuring
 *       "before − at-turn-end" reads 2 when the cost was 1 <i>and</i> reads 2 when nothing was charged at all,
 *       because the manual charge is then <b>refused</b> by {@code useSkill} for lack of a point. The measurement
 *       has to bracket <b>the cast itself</b>: the pool right after the action was announced, minus the pool at the
 *       end of that same turn.</li>
 *   <li><b>Comparing the driver against {@code useSkill} proves nothing.</b> An earlier version of this case did
 *       exactly that, and passed while the bug was present -- both paths charged through the same method, so both
 *       were double. The comparison has to be against a path that <b>cannot</b> double-charge: the hand-written
 *       demo's shape, {@code skill.execute} with the single manual charge.</li>
 * </ol>
 */
public class DrivenSkillPointCostTest {

    /** 罗刹 1203: his Skill is a RESTORE, the non-damaging kind the driver used to charge twice. */
    private static final int LUOCHA = 1203;
    private static final int LEVEL = 90;
    private static final int MONSTER = 1002011;

    /**
     * Casting his Skill through the driver spends exactly one point.
     *
     * <p>Measured across <b>the cast</b>, not across the turn -- see trap 1 in the class javadoc.
     */
    @Test
    public void aSkillCostsTheDriverOnePoint() {
        Character luocha = CharacterFactory.create(LUOCHA, LEVEL);
        Pool pool = new Pool();
        Watcher watcher = new Watcher(luocha, pool);

        BattleDriver driver = BattleDriver.of(List.of(luocha), List.of(enemy()), 1L).withListener(watcher);
        pool.battle = driver.battle();
        driver.run();

        Assertions.assertTrue(watcher.sawSkill, "precondition: the policy chose a Skill at least once");
        Assertions.assertNotNull(watcher.poolBeforeCast, "the pool was read at the moment of the cast");
        Assertions.assertEquals(1, watcher.costOfTheCast(),
                "a Skill costs one point: pool was " + watcher.poolBeforeCast
                        + " when the cast was announced and " + watcher.poolAtTurnEnd + " at that turn's end");
    }

    /**
     * And it costs what the hand-written demo's shape costs for the same cast.
     *
     * <p>The reference is deliberately <b>not</b> {@code useSkill}: that is the very method the driver charged
     * through a second time, so comparing against it is tautological and stays green under the bug. The demo's shape
     * -- {@code skill.execute} plus one manual charge -- cannot double-charge, because {@code DefaultSkill.execute}
     * contains no skill-point code at all.
     */
    @Test
    public void itCostsWhatTheHandWrittenShapeCosts() {
        Character asTheDemoDoesIt = CharacterFactory.create(LUOCHA, LEVEL);
        Battle battle = new Battle(List.of(asTheDemoDoesIt), List.of(enemy()));
        battle.startBattle();
        battle.processRequests();
        asTheDemoDoesIt = battle.characters().getFirst();

        int before = battle.getSkillPoints();
        Assertions.assertTrue(battle.applySkillPointCost(asTheDemoDoesIt.getSkills().get(SkillType.SKILL),
                asTheDemoDoesIt), "precondition: the point is spendable");
        asTheDemoDoesIt.getSkills().get(SkillType.SKILL).execute(battle, asTheDemoDoesIt,
                List.of(asTheDemoDoesIt));
        int demoCost = before - battle.getSkillPoints();

        Character throughDriver = CharacterFactory.create(LUOCHA, LEVEL);
        Pool pool = new Pool();
        Watcher watcher = new Watcher(throughDriver, pool);
        BattleDriver driver = BattleDriver.of(List.of(throughDriver), List.of(enemy()), 1L).withListener(watcher);
        pool.battle = driver.battle();
        driver.run();

        Assertions.assertEquals(demoCost, watcher.costOfTheCast(),
                "the driven path and the demo's own shape must charge the same for one Skill");
    }

    /**
     * An enemy that cannot die, so the fight lasts long enough to observe a Skill.
     *
     * @return the enemy
     */
    private static Enemy enemy() {
        Enemy e = EnemyFactory.create(MONSTER, LEVEL, 1);
        e.setAttribute(AttributeType.HEALTH, new DoubleValue(400_000));
        e.heal(400_000);
        e.setMaxEnergy(0);
        return e;
    }

    /**
     * Lets the listener read the pool without holding the driver.
     */
    private static final class Pool {
        private Battle battle;

        int points() {
            return battle == null ? -1 : battle.getSkillPoints();
        }
    }

    /**
     * Reads the pool on both sides of one Skill cast.
     *
     * <p>⚠ The driver notifies the action BEFORE the cast settles, so {@code onAction} still sees the pool as it was
     * when the decision was taken; the end-of-turn notification sees whatever the turn did afterwards. Bracketing the
     * cast that way is what separates "charged once" from "charged twice, second refused".
     */
    private static final class Watcher implements BattleListener {

        private final Character hero;
        private final Pool pool;
        private boolean sawSkill;
        private Integer poolBeforeCast;
        private int poolAtTurnEnd;

        private Watcher(Character hero, Pool pool) {
            this.hero = hero;
            this.pool = pool;
        }

        private int costOfTheCast() {
            return poolBeforeCast - poolAtTurnEnd;
        }

        @Override
        public void onAction(CanHit actor, SkillPolicy.Decision decision) {
            if (!sawSkill && actor == hero && decision.skill() == hero.getSkills().get(SkillType.SKILL)) {
                sawSkill = true;
                poolBeforeCast = pool.points();
            }
        }

        @Override
        public void onTurnEnd(CanHit actor, int turn) {
            if (sawSkill && poolAtTurnEnd == 0 && poolBeforeCast != null) {
                poolAtTurnEnd = pool.points();
            }
        }
    }
}
