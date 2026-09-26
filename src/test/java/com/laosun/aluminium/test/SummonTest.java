package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.SpeedBoostBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.enemy.SummonFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * P9-4: a monster's summon roster becomes a combatant that can actually be put on the field.
 *
 * <p><b>What was missing before this.</b> Two halves of "summons exist" had each been paid for and neither
 * was usable alone: {@code Battle.enemies} was widened to {@code List<CanHit>} (L-8), so the camp
 * <em>could</em> hold one; and {@code models/Summon} was declared, so the type existed. But production code
 * never called {@code new Summon(...)} anywhere, and {@code monster_config.json}'s {@code summon_id} column
 * — non-empty for <b>692 of 2649 monsters</b> — was not parsed at all. This is the piece that connects them:
 * the roster travels from the data onto the master, and a factory plus one {@code Battle} call turn an id
 * into a unit standing in the fight.
 *
 * <p><b>What these cases are really guarding.</b> Not "does summon() add to a list" — that would be hard to
 * get wrong. They pin the four things that could each be quietly wrong while the feature still looked
 * finished:
 * <ol>
 *   <li>{@code [0]} in the roster is <b>not</b> monster number zero (it is the data's way of writing "none",
 *       and it really occurs), so how that column is read is asserted rather than assumed;</li>
 *   <li>the summon's numbers come from <b>its own</b> data, not its master's — a copy of the master's stat
 *       sheet would still "work" and be completely wrong;</li>
 *   <li>a summon admitted <b>mid-battle</b> joins the action bar and can be reached by our attacks: the
 *       opening roster is wired in the constructor, and a later arrival is not;</li>
 *   <li>both ends of the lifecycle — it dies like anything else, and it goes when its master does — and
 *       <b>that going is not a kill</b>, so no on-kill reward may be paid for it.</li>
 * </ol>
 *
 * <p>The last one is the subtle one, and {@code CanHit.perish()} is where it lives: a minion vanishing with
 * its boss must not hand out 「每消灭 1 敌 +5 能量」. ⚠ <b>Mutation testing corrected my own account of why</b>
 * — I first wrote that {@code takeDamage} would fire the events, and the mutant that swaps {@code perish()}
 * for {@code takeDamage(maxHp)} stayed <b>green</b>. It is not {@code CanHit.takeDamage} that pays rewards
 * (it fires nothing); it is {@code Battle.applyDamage}, and this sweep deliberately runs outside it. So the
 * two are told apart by the one thing they really differ on — <b>HP</b> — and that is what the lifecycle case
 * asserts.
 */
public class SummonTest {
    private static final double EPS = 1e-6;

    /** 银鬃尉官, whose data roster is 银鬃近卫 ×2. */
    private static final int MASTER = 1003010;
    /** One 银鬃近卫 — the id the master's roster names. */
    private static final int MINION = 1002040;
    /** 冰锋, whose roster is empty (the common case: 1957 of 2649). */
    private static final int NO_ROSTER = 1002011;
    /** The one monster whose roster is spelled {@code [0]}. */
    private static final int ZERO_ROSTER = 405301004;
    private static final int LEVEL = 90;
    private static final int GROUP = 1;
    /** What the stand-in "on kill" rule below hands out. */
    private static final double KILL_REWARD = 5;

    // ==================================================================
    // The roster is data, and reading it is not obvious
    // ==================================================================

    /**
     * The roster's order and its duplicates both survive: it is a roster, not a set.
     *
     * <p>银鬃尉官 really does summon the same minion twice, so "1002040 ×2" has to stay two entries. A
     * {@code Set} would silently turn it into one, and nothing about the resulting battle would say so.
     */
    @Test
    public void theRosterTravelsFromTheDataOntoTheMaster() {
        Enemy master = EnemyFactory.create(MASTER, LEVEL, GROUP);

        Assertions.assertEquals(List.of(MINION, MINION), master.getSummonIds(),
                "the data's summon_id column, in order, duplicates kept");
    }

    /** A monster with no summons has an empty roster, which is the ordinary case. */
    @Test
    public void aMonsterWithNoSummonsHasAnEmptyRoster() {
        Assertions.assertEquals(List.of(), EnemyFactory.create(NO_ROSTER, LEVEL, GROUP).getSummonIds(),
                "1957 of 2649 monsters are like this, and a column that was never parsed looks exactly the same");
    }

    /**
     * {@code [0]} means "no summon" — and it is genuinely in the data, so this is not a hypothetical.
     *
     * <p>Handing 0 to the factory would look up monster 0 and fail. The tempting fix ("skip ids that do not
     * resolve") is worse than the failure: it would make a real data error indistinguishable from "this
     * monster summons nothing", which is the silent-answer shape this project keeps refusing. So the
     * convention is applied once, at load time, and this case records which convention was chosen.
     */
    @Test
    public void theZeroEntryMeansNoSummonAndIsDroppedAtLoad() {
        Assertions.assertEquals(List.of(), Constant.MONSTER_CONFIGS.get(ZERO_ROSTER).summonIds(),
                "the raw data spells this monster's roster as [0]; 0 is not a monster id, it is \"none\"");
    }

    // ==================================================================
    // A summon is built from its own data
    // ==================================================================

    /**
     * The stat sheet is the summon's own, computed by the same rules a monster's is.
     *
     * <p>Compared against {@code EnemyFactory.create} of the <b>same id</b>: if the two paths ever disagree,
     * one of them has grown a private copy of the scaling rules — and the summon would be the copy nobody
     * looks at.
     */
    @Test
    public void theSummonScalesByTheSameRulesAsAMonsterWithThatId() {
        Summon summon = SummonFactory.create(MINION, LEVEL, GROUP, Camp.ENEMY);
        Enemy asMonster = EnemyFactory.create(MINION, LEVEL, GROUP);

        for (AttributeType attribute : List.of(AttributeType.ATTACK, AttributeType.DEFENCE, AttributeType.SPEED)) {
            Assertions.assertEquals(asMonster.getAttribute(attribute).get(),
                    summon.getAttribute(attribute).get(), EPS, attribute.name());
        }
        Assertions.assertEquals(asMonster.getMaxHp(), summon.getMaxHp(), EPS, "HEALTH");
        Assertions.assertEquals(LEVEL, summon.getLevel(), "the level enters the defence zone, like any unit's");
    }

    /** It carries its own identity and its own skill — not its master's. */
    @Test
    public void theSummonCarriesItsOwnNameAndSkill() {
        Summon summon = SummonFactory.create(MINION, LEVEL, GROUP, Camp.ENEMY);

        Assertions.assertEquals(Constant.MONSTER_CONFIGS.get(MINION).name().chinese(), summon.getName(),
                "the summon's name is the summon's, so a roster entry naming the wrong monster is visible");
        Assertions.assertNotNull(summon.getSkills().get(SkillType.COMMON),
                "without a skill it would stand there doing nothing on its turn, which is how 'summoned but "
                        + "inert' would go unnoticed");
        Assertions.assertEquals(Camp.ENEMY, summon.getCamp(), "the camp is passed in, not assumed");
    }

    /** Two calls give two independent units — a roster entry is not a shared singleton. */
    @Test
    public void eachSummonIsItsOwnInstance() {
        Battle battle = new Battle(List.of(hero()), List.of(master()), new Random(0));

        Summon first = battle.summon(battle.enemies.getFirst(), MINION, GROUP);
        Summon second = battle.summon(battle.enemies.getFirst(), MINION, GROUP);

        Assertions.assertNotSame(first, second);
        battle.applyDamage(first, overkill(battle.characters.getFirst(), first));
        Assertions.assertTrue(first.isDeath());
        Assertions.assertFalse(second.isDeath(), "one minion dying must not take the other one with it");
    }

    // ==================================================================
    // Getting onto the field mid-battle
    // ==================================================================

    /**
     * A summon admitted after the battle started still joins the camp and the action bar.
     *
     * <p>The constructor wires the speed listener and the opening queue for the units it was handed; a unit
     * that arrives later goes through {@code addRequestItems}, like a wave. Both halves matter, so both are
     * asserted — and so does the fact that entering does not move the clock.
     */
    @Test
    public void aSummonAdmittedMidBattleJoinsTheCampAndTheActionBar() {
        Battle battle = startedBattle();
        Enemy master = battle.enemyUnits().getFirst();
        int queueBefore = battle.queue.size();
        int roundBefore = battle.getRound();

        Summon summon = battle.summon(master, MINION, GROUP);
        Assertions.assertEquals(queueBefore, battle.queue.size(), "not on the action bar until it is processed");

        battle.processRequests();

        Assertions.assertEquals(queueBefore + 1, battle.queue.size());
        Assertions.assertTrue(battle.enemies.contains(summon), "the camp holds it");
        Assertions.assertEquals(1, battle.enemyUnits().size(),
                "enemyUnits() is still the monsters only -- that split is the whole point of L-8");
        Assertions.assertEquals(roundBefore, battle.getRound(),
                "entering costs no action value: it starts from the current clock, it does not restart a round");
        Assertions.assertTrue(battle.queue.snapshot().stream().anyMatch(s -> s.getCanHit() == summon),
                "it has a signal of its own, so it will be handed turns");
    }

    /** Our side can reach it: the engine's own target list includes it, without the caller naming it. */
    @Test
    public void theEngineTargetListIncludesAFreshlySummonedUnit() {
        Battle battle = startedBattle();
        battle.summon(battle.enemyUnits().getFirst(), MINION, GROUP);
        battle.processRequests();

        Assertions.assertEquals(2, battle.targetableEnemies().size(),
                "master + summon; an AOE iterates this list, so leaving the summon out would make it "
                        + "untouchable while it kept attacking us");
    }

    /**
     * A unit admitted mid-battle can still be re-sorted by a speed change.
     *
     * <p>{@code Battle}'s constructor wires the "speed changed → reschedule" listener for the units it was
     * handed; a later arrival gets it only if the admission path remembers to, and forgetting looks like
     * nothing at all — the summon would simply keep its old action time for the rest of the battle while
     * every unit created normally re-sorted. So the wiring is asserted rather than assumed.
     */
    @Test
    public void aUnitAdmittedMidBattleIsStillRescheduledByASpeedChange() {
        Battle battle = startedBattle();
        Summon summon = battle.summon(battle.enemyUnits().getFirst(), MINION, GROUP);
        battle.processRequests();
        Signal signal = battle.queue.snapshot().stream()
                .filter(s -> s.getCanHit() == summon)
                .findFirst()
                .orElseThrow();
        double before = signal.getNextActionTime();

        summon.getBuffManager().addBuff(new SpeedBoostBuff(2, 1.0));

        Assertions.assertTrue(signal.getNextActionTime() < before,
                "a doubled speed must pull its next action earlier: " + before + " -> "
                        + signal.getNextActionTime());
    }

    // ==================================================================
    // Lifecycle
    // ==================================================================

    /** Killing it is an ordinary kill: it stops being a target and leaves the action bar. */
    @Test
    public void killingASummonTakesItOutOfTheFight() {
        Battle battle = startedBattle();
        Summon summon = battle.summon(battle.enemyUnits().getFirst(), MINION, GROUP);
        battle.processRequests();
        long aliveBefore = aliveCount(battle);

        battle.applyDamage(summon, overkill(battle.characters.getFirst(), summon));
        battle.processRequests();

        Assertions.assertTrue(summon.isDeath());
        Assertions.assertEquals(aliveBefore - 1, aliveCount(battle), "the live count came back down");
        Assertions.assertFalse(battle.queue.snapshot().stream().anyMatch(s -> s.getCanHit() == summon),
                "and it is off the action bar");
    }

    /**
     * The master falling takes its summon with it.
     *
     * <p>The failure this guards against is a minion outliving its boss: still hitting us in a battle that
     * should have been won, or keeping the enemy side "not wiped out" forever.
     */
    @Test
    public void theMasterFallingTakesItsSummonWithIt() {
        Battle battle = startedBattle();
        Enemy master = battle.enemyUnits().getFirst();
        Summon summon = battle.summon(master, MINION, GROUP);
        battle.processRequests();
        double summonHpBefore = summon.getCurrentHp();

        battle.applyDamage(master, overkill(battle.characters.getFirst(), master));
        battle.processRequests();

        Assertions.assertTrue(master.isDeath(), "precondition: the master is down");
        Assertions.assertTrue(summon.isDeath(), "and the summon went with it");
        Assertions.assertEquals(summonHpBefore, summon.getCurrentHp(), EPS,
                "it *left*, it was not beaten down: 'still at full HP' and 'killed' are different facts, and "
                        + "content that asks how hurt it is must not be told the second one");
        Assertions.assertFalse(battle.queue.snapshot().stream().anyMatch(s -> s.getCanHit() == summon));
        Assertions.assertEquals(Battle.Status.WIN, battle.getStatus(),
                "nothing is left standing on that side, so this is a win rather than a stalemate");
    }

    /** A summon that has not entered yet goes with its master just the same. */
    @Test
    public void aSummonStillWaitingToEnterDiesWithItsMasterToo() {
        Battle battle = startedBattle();
        Enemy master = battle.enemyUnits().getFirst();
        Summon summon = battle.summon(master, MINION, GROUP);      // deliberately NOT processed

        battle.applyDamage(master, overkill(battle.characters.getFirst(), master));
        battle.processRequests();

        Assertions.assertTrue(master.isDeath());
        Assertions.assertTrue(summon.isDeath(), "it never got a turn, but it is still the master's summon");
        Assertions.assertFalse(battle.queue.snapshot().stream().anyMatch(s -> s.getCanHit() == summon));
    }

    /**
     * <b>Vanishing with its master is not a kill</b>, so it must not pay out an on-kill reward.
     *
     * <p>This is why {@code CanHit.perish()} exists rather than a {@code takeDamage} call: a kill would fire
     * {@code HpLoss}/{@code Kill} and hand every on-kill talent in the game its reward for a death nobody
     * caused. The rule below is the smallest stand-in for that whole family (姬子's 「每消灭 1 敌 +5 能量」),
     * and the assertion is that <b>one death pays once</b>.
     *
     * <p>The precondition asserts the reward really was paid for the master — including the conventional
     * kill energy — so the second assertion cannot pass by the rule simply never firing.
     */
    @Test
    public void aSummonLeavingWithItsMasterIsNotAKill() {
        Character hero = heroWithKillReward();
        Battle battle = new Battle(List.of(hero), List.of(master()), new Random(0));
        battle.startBattle();
        Summon summon = battle.summon(battle.enemyUnits().getFirst(), MINION, GROUP);
        battle.processRequests();

        double before = hero.getCurrentEnergy();
        battle.applyDamage(battle.enemyUnits().getFirst(), overkill(hero, battle.enemyUnits().getFirst()));
        double afterMaster = hero.getCurrentEnergy();
        Assertions.assertEquals(KILL_REWARD + Constant.ENERGY_GAIN_KILL, afterMaster - before, EPS,
                "precondition: killing the master paid the rule plus the conventional kill energy");

        battle.processRequests();                                   // the summon perishes here
        Assertions.assertEquals(afterMaster, hero.getCurrentEnergy(), EPS,
                "and the summon vanishing with it paid nothing: one death, one reward");
        Assertions.assertTrue(summon.isDeath());
    }

    /** The same rule <em>does</em> fire for a summon that is genuinely killed, so the case above is not vacuous. */
    @Test
    public void aSummonThatIsActuallyKilledDoesPayTheReward() {
        Character hero = heroWithKillReward();
        Battle battle = new Battle(List.of(hero), List.of(master()), new Random(0));
        battle.startBattle();
        Summon summon = battle.summon(battle.enemyUnits().getFirst(), MINION, GROUP);
        battle.processRequests();

        double before = hero.getCurrentEnergy();
        battle.applyDamage(summon, overkill(hero, summon));

        Assertions.assertTrue(summon.isDeath());
        Assertions.assertEquals(KILL_REWARD + Constant.ENERGY_GAIN_KILL, hero.getCurrentEnergy() - before, EPS,
                "a real kill is a real kill -- the contrast is what makes the previous case mean something");
    }

    // ==================================================================
    // Refusals: the wrong answer must not be available
    // ==================================================================

    /**
     * Our side cannot summon yet, and the refusal says why.
     *
     * <p>Our roster is {@code List<Character>}, so a player-side {@link Summon} has nowhere to be placed —
     * the friendly half of L-8 was never done. Filing it under {@code enemies} instead would make our own
     * summon attackable by us and count it as an enemy for the victory check: a wrong answer that nothing
     * would report. A loud refusal is the project's rule for this shape (the P8-8 {@code PARTY} scope is
     * refused the same way).
     */
    @Test
    public void aPlayerSideMasterIsRefusedWithAReason() {
        Battle battle = new Battle(List.of(hero()), List.of(master()), new Random(0));

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> battle.summon(battle.characters.getFirst(), MINION, GROUP));

        Assertions.assertTrue(refused.getMessage().contains("enemy-camp"), refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains("List<Character>"), refused.getMessage());
        Assertions.assertEquals(1, battle.enemies.size(), "and nothing was quietly filed under enemies");
    }

    /** A dead master cannot summon: the call could never do anything, so it says so instead. */
    @Test
    public void aDeadMasterIsRefused() {
        Battle battle = startedBattle();
        Enemy master = battle.enemyUnits().getFirst();
        master.takeDamage(9_999_999);

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> battle.summon(master, MINION, GROUP));

        Assertions.assertTrue(refused.getMessage().contains("dead master"), refused.getMessage());
        Assertions.assertEquals(1, battle.enemies.size(), "no half-born summon was left in the roster");
    }

    /** An id that names no monster fails loudly — a roster's job is to be right, not to be skippable. */
    @Test
    public void anUnknownSummonIdFailsLoudly() {
        Battle battle = startedBattle();

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> battle.summon(battle.enemyUnits().getFirst(), 999_999_999, GROUP));

        Assertions.assertTrue(refused.getMessage().contains("Unknown monster"), refused.getMessage());
        Assertions.assertEquals(1, battle.enemies.size());
    }

    /** A null master is a caller bug, not a unit with no owner. */
    @Test
    public void aNullMasterIsRefused() {
        Battle battle = startedBattle();

        Assertions.assertThrows(IllegalArgumentException.class, () -> battle.summon(null, MINION, GROUP));
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /** One hero that out-damages everything, and one real master. */
    private static Battle startedBattle() {
        Battle battle = new Battle(List.of(hero()), List.of(master()), new Random(0));
        battle.startBattle();
        return battle;
    }

    private static Character hero() {
        return Character.fromAttributes("hero", 100_000, 100, 100, 100);
    }

    /** 姬子's id, but no shipped rule file, so the table built here is the only one in play. */
    private static Character heroWithKillReward() {
        Character hero = CharacterFactory.create(1003, 80);
        EffectSpec reward = TriggerSpecs.gainEnergy(KILL_REWARD);
        TriggerSpec rule = TriggerSpecs.rule("KILL", List.of("actor == self"), reward);
        hero.setTriggerTable(new TriggerTable(1003, List.of(rule)));
        return hero;
    }

    private static Enemy master() {
        return EnemyFactory.create(MASTER, LEVEL, GROUP);
    }

    /**
     * How many units of the enemy camp are still standing.
     *
     * <p>Counted here rather than read off {@code enemies.size()}, because that roster deliberately keeps
     * the dead: it records who was in the fight, and "is it alive" is asked through {@code isDeath()}
     * everywhere in the engine ({@code targetableEnemies}, {@code WaveManager.aliveEnemies},
     * {@code checkResult}). A test that asserted on the roster's size would be asserting the opposite
     * convention from the one the engine uses.
     */
    private static long aliveCount(Battle battle) {
        return battle.enemies.stream().filter(unit -> !unit.isDeath()).count();
    }

    /**
     * A hit big enough to kill anything, whatever the zones do to it.
     *
     * <p>The scale is deliberate: the base value goes through the defence and resistance zones, so "just
     * above max HP" would leave the outcome depending on the zone rounding rather than on the mechanic under
     * test.
     */
    private static Damage overkill(CanHit attacker, CanHit target) {
        return new Damage(attacker, target, DamageElement.PHYSICAL, DamageType.NORMAL,
                target.getMaxHp() * 1_000 + 1_000_000);
    }
}
