package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.battle.BattleDriver;
import com.laosun.aluminium.battle.BattleListener;
import com.laosun.aluminium.battle.BattleNarrator;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A whole real battle, driven by the engine: the same three characters and three enemies the hand-written demo
 * uses, but with {@link BattleDriver} owning the turn loop and {@code AutoPolicy} owning every choice.
 *
 * <h2>What this is for, given other driver tests exist</h2>
 * The other driver suites use pairs and single monsters, because they are checking one mechanism each. This one
 * runs the <b>full demo content</b> -- three real kits and three real monsters with their own weaknesses,
 * resistances and toughness bars -- and asserts the properties that only matter at that scale:
 *
 * <ul>
 *   <li>the battle <b>terminates</b>, and terminates as {@code FINISHED} rather than {@code TURN_LIMIT} or
 *       {@code STALLED}. A stall is the failure mode a long battle actually hits: a turn that neither kills,
 *       earns, nor advances, repeated until the ceiling. Running a real fight is the only way to catch it.</li>
 *   <li>the driver, not the caller, played <b>every</b> turn: the listener sees one {@code onTurnEnd} per actor-turn
 *       the result reports, so no turn was skipped or double-counted between the two.</li>
 *   <li>both camps are populated to the end and the numbers moved -- i.e. the fight was real rather than a
 *       degenerate no-op that trips over a decision boundary.</li>
 * </ul>
 *
 * <p>⚠ <b>This class used to say the outcome was deliberately NOT asserted</b>, on the reasoning that the auto policy
 * is a weak reference opponent that loses this content while the hand-tuned demo wins. <b>Both halves of that were
 * wrong</b>, and are recorded in ROADMAP §3.5-§3.8 and P11-4:
 *
 * <ul>
 *   <li>the policy was not losing to a strategy gap -- three engine rules simply were not applied on the driven path
 *       (a free ultimate, a doubled skill-point charge, and a broken enemy that acted anyway). With those fixed the
 *       same party wins this fight on every seed tried;</li>
 *   <li>the hand-tuned demo is not a stronger opponent. Its own decision order, replicated as a policy and run on the
 *       same 30000 HP content, also wins -- but takes more turns (66 against 47 with the demo's own party), because this policy is thriftier
 *       with skill points on protection and leaves more for the damage. Slower is not stronger.</li>
 * </ul>
 *
 * <p>So {@link #theDrivenDemoWinsAtItsOwnScale()} does pin the win. What is <b>still</b> not asserted is any claim
 * that the policy is strong: it is a simple reference implementation, and a loss elsewhere would not be an engine
 * defect.
 */
public class DrivenRealBattleTest {

    private static final int LEVEL = 90;
    private static final long SEED = 20261012L;

    /**
     * The demo's three characters, from their real data.
     */
    private static List<Character> party() {
        return List.of(
                CharacterFactory.create(1003, LEVEL, false, null, null, 0),   // 姬子 Himeko, fire
                CharacterFactory.create(1001, LEVEL, false, null, null, 0),   // 三月七 March 7th, ice
                CharacterFactory.create(1203, LEVEL, false, null, null, 0));  // 罗刹 Luocha, healer
    }

    /**
     * The demo's three enemies at the given HP each, with their energy bar removed.
     *
     * @param hp per-enemy HP
     * @return the enemies
     */
    private static List<Enemy> foes(int hp) {
        List<Enemy> enemies = new ArrayList<>(List.of(
                EnemyFactory.create(1002011, LEVEL, 1),   // 冰锋 Ice Edge
                EnemyFactory.create(8032010, LEVEL, 1),   // 基层员工·外勤
                EnemyFactory.create(8002040, LEVEL, 1))); // 次元扑满
        for (Enemy enemy : enemies) {
            enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(hp));
            enemy.heal(hp);
            enemy.setMaxEnergy(0);
        }
        return enemies;
    }

    /**
     * The party WINS the fight the driven demo runs -- at the scale that demo uses.
     *
     * <p>⚠ <b>This used to be measured at 15000, because 30000 appeared to be a wall</b> (0/8, every fight lost with
     * the enemies still standing). That appearance came from three defects, all of which made the driven party look
     * weaker than it is: ultimates cost no energy (§3.5), every Skill was charged twice (§3.6), and a <b>broken enemy
     * never skipped its turn</b> because only the hand-written demo ticked the broken countdown. With those fixed the
     * party wins 8 of 8 at the data's own 30000 scale, so the scale is no longer lowered for it.
     *
     * <p>⚠ <b>What a WIN here does and does not claim.</b> It claims the engine can play a real three-on-three
     * through to a victory with nothing scripting the choices -- which is what {@code gradlew run --args=driven}
     * shows. It does NOT claim the policy is strong: it is a simple reference opponent, and a loss elsewhere is not
     * an engine defect.
     */
    @Test
    public void theDrivenDemoWinsAtItsOwnScale() {
        BattleDriver driver = BattleDriver.of(party(), foes(30_000), SEED);
        BattleDriver.Result result = driver.run();

        Assertions.assertEquals(BattleDriver.Outcome.FINISHED, result.outcome(),
                "the battle must be decided, not merely stopped");
        Assertions.assertEquals(Battle.Status.WIN, result.status(),
                "at the demo's own scale the party must come out ahead: " + result);
        Assertions.assertTrue(driver.battle().enemyUnits().stream().allMatch(CanHit::isDeath),
                "a win means the enemy camp is gone, not that a turn ceiling was reached");
    }

    /**
     * A real battle runs to a decided end, and the driver plays every turn it reports.
     */
    @Test
    public void aWholeRealBattleRunsToADecidedEnd() {
        Counter counter = new Counter();
        BattleDriver driver = BattleDriver.of(party(), foes(30_000), SEED).withListener(counter);

        BattleDriver.Result result = driver.run();

        Assertions.assertEquals(BattleDriver.Outcome.FINISHED, result.outcome(),
                "a real battle must reach a decision -- TURN_LIMIT means the policy could not close it out and "
                        + "STALLED means no unit could act at all");
        Assertions.assertTrue(result.turns() > 10,
                "a three-on-three at 30000 HP is not over in a handful of turns; got " + result.turns());
        Assertions.assertEquals(result.turns(), counter.ended,
                "the driver reports " + result.turns() + " actor-turns but the listener saw " + counter.ended
                        + " turn ends, so the two disagree about which turns happened");
        Assertions.assertEquals(counter.started, counter.ended,
                "every turn that started must also have ended, or afterMove was skipped and the queue is corrupt");
    }

    /**
     * Both camps really acted: the party damaged somebody, and the enemies landed hits on our side.
     *
     * <p>Guards the degenerate case where a battle "finishes" because one side was empty to begin with, or where the
     * enemy camp never got a turn.
     *
     * <p>⚠ <b>Why the second half is not "our side ends wounded".</b> It used to assert exactly that (final
     * {@code maxHp - currentHp} > 0), and it went red the day 1001 March 7th (三月七)'s slots started reading their
     * documented level row - not because the enemies stopped attacking, but because the party now wins with Luocha (罗刹)
     * having healed everyone back to full. Measured at seed {@value #SEED}: <b>19</b> enemy→ally damage instances
     * settling <b>6546.86</b> (2979.67 of it absorbed by shields) while the final HP deficit was <b>0</b>. Healing makes
     * the final deficit a proxy that answers a different question, so the case now counts what the sentence means:
     * enemy attacks that landed on our side. It still fails on the regression it exists for - an enemy camp that never
     * acts delivers zero instances.
     */
    @Test
    public void bothCampsWereReallyEngaged() {
        Engagement engagement = new Engagement();
        BattleDriver driver = BattleDriver.of(party(), foes(30_000), SEED).withListener(engagement);
        BattleDriver.Result result = driver.run();
        Battle battle = driver.battle();

        Assertions.assertEquals(3, battle.characters().size(), "the party is still three characters");
        Assertions.assertEquals(3, battle.enemyUnits().size(), "the camp keeps its roster, dead or not");

        double enemiesHurt = battle.enemyUnits().stream()
                .mapToDouble(e -> e.getMaxHp() - e.getCurrentHp()).sum();

        Assertions.assertTrue(enemiesHurt > 0, "the party must have damaged somebody");
        Assertions.assertTrue(engagement.enemyHits > 0,
                "the enemies must have attacked our side; measured " + engagement.enemyHits + " landed hits");
        Assertions.assertTrue(engagement.enemyApplied > 0,
                "…and settled damage, not a parade of zeroes (applied " + engagement.enemyApplied + ")");
        Assertions.assertNotNull(result.status(), "a decided battle reports which side won");
        System.out.println("[driven] enemy hits on our side = " + engagement.enemyHits
                + " ; applied = " + engagement.enemyApplied
                + " ; absorbed = " + engagement.enemyAbsorbed);
    }

    /**
     * The narrator's output describes a battle rather than dumping an object graph.
     *
     * <p>It is capture-tested because it is a deliverable in its own right: {@code gradlew run --args=driven} is how
     * a person sees the engine play itself, and a narrator that printed nothing, or printed one line, would look
     * like a working command.
     *
     * <p>Captured through the narrator's own stream rather than by swapping {@code System.out}, so this case cannot
     * interfere with any other test sharing the JVM.
     */
    @Test
    public void theNarratorDescribesTheBattle() {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream sink = new PrintStream(captured, true, StandardCharsets.UTF_8);

        BattleDriver.of(party(), foes(30_000), SEED).withListener(new BattleNarrator(sink)).run();
        sink.flush();
        String text = captured.toString(StandardCharsets.UTF_8);

        Assertions.assertTrue(text.contains("Battle — driven headlessly by BattleDriver"),
                "the narrator announces what ran it");
        Assertions.assertTrue(text.contains("Party   ") && text.contains("Enemies "),
                "both camps are listed");
        Assertions.assertTrue(text.contains("casts "), "at least one action is narrated");
        Assertions.assertTrue(text.contains("action bar:"), "the action bar is shown");
        Assertions.assertTrue(text.contains("Result: "), "the outcome is reported");
        Assertions.assertTrue(text.contains("[Ally]") && text.contains("[Enemy]"),
                "the opening state lists both camps");
    }

    /**
     * Counts turns, to check the driver and the listener agree on how many there were.
     */
    private static final class Counter implements BattleListener {

        private int started;
        private int ended;

        @Override
        public void onTurnStart(CanHit actor, int turn) {
            started++;
        }

        @Override
        public void onTurnEnd(CanHit actor, int turn) {
            ended++;
        }
    }

    /**
     * Counts the hits each camp landed on the other, which is what "both camps were really engaged" means.
     *
     * <p>Note: Counted from {@link BattleListener#onDamage}, i.e. from settled instances rather than from final HP: a
     * heal after the hit leaves the HP deficit at zero while the battle was perfectly real (measured: 19 landed enemy
     * hits, final deficit 0).
     */
    private static final class Engagement implements BattleListener {

        private int enemyHits;
        private double enemyApplied;
        private double enemyAbsorbed;

        @Override
        public void onDamage(Damage damage, double applied, double hpLoss, double shieldAbsorbed, boolean died) {
            CanHit attacker = damage.getAttacker();
            CanHit defender = damage.getDefender();
            if (attacker == null || defender == null) {
                return;
            }
            if (attacker.getCamp() == Camp.ENEMY && defender.getCamp() == Camp.PLAYER) {
                enemyHits++;
                enemyApplied += applied;
                enemyAbsorbed += shieldAbsorbed;
            }
        }
    }
}
