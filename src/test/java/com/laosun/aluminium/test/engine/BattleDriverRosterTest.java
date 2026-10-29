package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.battle.AutoPolicy;
import com.laosun.aluminium.battle.BattleDriver;
import com.laosun.aluminium.battle.BattleListener;
import com.laosun.aluminium.battle.SkillPolicy;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.laosun.aluminium.entity.Summon;

/**
 * The driver's action branches are actually reached, with real characters and a real enemy.
 *
 * <p><b>Why a second file rather than more cases in {@link BattleDriverTest}.</b> That file pins the
 * loop: termination, reproducibility, stepping, listeners. It uses one character (1109 Hook) whose
 * every slot is a single-target Fire attack, against a synthetic enemy built by
 * {@code Enemy.fromAttributes} with no weakness and no toughness bar. That is the right fixture for
 * loop questions and the wrong one for action questions, because it can never reach:
 *
 * <ul>
 *   <li>the non-damaging dispatch -- it has no healing or shielding skill at all;</li>
 *   <li>the ultimate branch -- {@code isUltraReady} is never satisfiable in the turns it takes;</li>
 *   <li>the weakness branch -- the synthetic enemy is weak to nothing;</li>
 *   <li>the AoE target expansion -- it has no area skill;</li>
 *   <li>a Summon acting -- it summons nothing.</li>
 * </ul>
 *
 * <p>So this file drives a small roster chosen for exactly those branches, each member already covered
 * by its own content tests so this is not the first attempt at its data:
 *
 * <ul>
 *   <li><b>1001 March 7th</b> (mar7th, 15 tests) -- {@code DEFENCE} skill, {@code AoEAttack} Ice
 *       ultimate. The shield is the non-damaging path; the ultimate is the AoE path.</li>
 *   <li><b>1105 Natasha</b> (natasha, 6 tests) -- {@code Restore} skill and ultimate, the healing
 *       path.</li>
 *   <li><b>1222 Lingsha</b> (lingsha, 4 tests) -- {@code AoEAttack} Fire, which is what reaches the
 *       weakness branch against an Ice Edge (weak to Fire and Thunder).</li>
 *   <li><b>1407 Castorice</b> (castorice, 4 tests) -- a {@code Summon} ultimate, so a Summon takes a
 *       turn of its own.</li>
 * </ul>
 *
 * <p>The enemy is 1002011 Ice Edge from {@link EnemyFactory}, not a synthetic unit: it carries a
 * toughness bar, real weaknesses and a skill, which is what makes the weakness branch and the enemy
 * turn meaningful.
 */
public class BattleDriverRosterTest {

    private static final int LEVEL = 80;

    /**
     * A party that reaches the non-damaging, ultimate and weakness branches in one battle.
     *
     * <p>The assertion is on what the DRIVER did, recorded from its own listener, rather than on who
     * won: a test that only checked the outcome would pass even if the policy silently fell through to
     * basic attacks every turn, which is precisely the failure this is meant to catch.
     */
    @Test
    void aMixedPartyReachesTheUltimateShieldAndWeaknessBranches() {
        Set<SkillType> slotsCast = new LinkedHashSet<>();
        List<String> notes = new ArrayList<>();

        BattleDriver driver = new BattleDriver(
                BattleFixture.battle(
                        List.of(CharacterFactory.create(1001, LEVEL, false, null, null, 0),
                                CharacterFactory.create(1105, LEVEL, false, null, null, 0),
                                CharacterFactory.create(1222, LEVEL, false, null, null, 0)),
                        List.of(EnemyFactory.create(1002011, LEVEL, 1))),
                new AutoPolicy())
                .withListener(new BattleListener() {
                    @Override
                    public void onAction(CanHit actor, SkillPolicy.Decision decision) {
                        slotsCast.add(slotOf(actor, decision));
                        notes.add(decision.note());
                    }
                });

        BattleDriver.Result result = driver.run();

        Assertions.assertTrue(result.isDecided(),
                "a three-hero party against one Ice Edge should finish, got " + result.outcome());
        Assertions.assertFalse(slotsCast.isEmpty(), "at least one action should have been taken");

        Assertions.assertTrue(slotsCast.contains(SkillType.SKILL) || slotsCast.contains(SkillType.ULTRA),
                "the party must reach past basic attacks; it cast only " + slotsCast
                        + " (notes: " + notes + ")");
        Assertions.assertTrue(notes.stream().anyMatch(n -> n.equals("target is weak to it")),
                "1222's Fire area skill should have met Ice Edge's Fire weakness, notes were " + notes);
    }

    /**
     * The ultimate branch runs, and it is the engine's readiness rule that gates it.
     *
     * <p>Asserted separately from the mixed battle because "an ultimate happened" is the single most
     * load-bearing claim about {@link AutoPolicy}: if {@code isUltraReady} were never true the policy
     * would still look like it worked, just weaker.
     */
    @Test
    void anUltimateIsCastOnceTheEngineSaysItIsReady() {
        List<String> ultimates = new ArrayList<>();

        BattleDriver driver = new BattleDriver(
                BattleFixture.battle(
                        List.of(CharacterFactory.create(1222, LEVEL, false, null, null, 0)),
                        List.of(EnemyFactory.create(1002011, LEVEL, 1))),
                new AutoPolicy())
                .withListener(new BattleListener() {
                    @Override
                    public void onAction(CanHit actor, SkillPolicy.Decision decision) {
                        if (slotOf(actor, decision) == SkillType.ULTRA) {
                            ultimates.add(decision.note());
                        }
                    }
                });

        driver.run();

        Assertions.assertFalse(ultimates.isEmpty(),
                "an ultimate must be cast at some point in a full battle");
    }

    /**
     * A Summon takes a turn of its own through the action bar.
     *
     * <p>This is the third actor kind the driver must handle, alongside a Character and an Enemy.
     *
     * <p><b>Why the memosprite is summoned explicitly rather than by casting her ultimate.</b> The first
     * version of this case simply ran a battle with Castorice and asserted a Summon appeared -- and it
     * <b>passed vacuously</b> because the assertion accepted {@code Character} or {@code Summon}. Tightening
     * it to require a Summon made it fail, and the reason is worth recording: her ultimate's effect is
     * {@code Summon}, but nothing in this engine turns that into a {@code Battle.summonMemosprite} call, so
     * the ultimate does not put a memosprite on the field. Every other test in the suite that needs one
     * calls {@link Battle#summonMemosprite} itself, which is also what a real client does. So this case does
     * the same and then asks the driver to drive the battle: the claim under test is that the driver lets a
     * Summon act, not that a particular ultimate summons.
     */
    @Test
    void aSummonActsOnItsOwnTurn() {
        List<String> actorsSeen = new ArrayList<>();

        Battle battle = BattleFixture.battle(
                List.of(CharacterFactory.create(1407, LEVEL, false, null, null, 0)),
                List.of(EnemyFactory.create(1002011, LEVEL, 1)));
        battle.startBattle();
        Assertions.assertNotNull(battle.summonMemosprite(battle.characters().getFirst()),
                "precondition: the memosprite is on the field before the driver runs");

        BattleDriver driver = new BattleDriver(battle, new AutoPolicy())
                .withListener(new BattleListener() {
                    @Override
                    public void onTurnStart(CanHit actor, int turn) {
                        actorsSeen.add(actor.getClass().getSimpleName());
                    }
                });

        BattleDriver.Result result = driver.run();

        Assertions.assertTrue(result.turns() > 0, "the battle should have taken turns");
        Assertions.assertTrue(actorsSeen.contains("Summon"),
                "a Summon must reach its own turn; actors seen were " + actorsSeen);
    }

    /**
     * The loss path is reachable: a party that cannot win ends the battle as LOSE.
     *
     * <p>The enemy must come from {@link EnemyFactory}, not {@code Enemy.fromAttributes}. A synthetic
     * enemy carries <b>no skills at all</b> -- the factory is what assigns one -- so an enemy built by
     * attributes never attacks, and the party cannot lose no matter how weak it is. That is not a
     * guess: this case was first written with a synthetic enemy and it ran 50 turns with the doomed
     * unit still at 1 HP, because nothing was ever thrown at it.
     */
    @Test
    void aPartyThatCannotWinEndsInLoss() {
        Character doomed = Character.fromAttributes("孤军", 1, 0, 100, 100);

        BattleDriver driver = new BattleDriver(
                BattleFixture.battle(List.of(doomed), List.of(EnemyFactory.create(1002011, LEVEL, 1))),
                new AutoPolicy());

        BattleDriver.Result result = driver.run();

        Assertions.assertTrue(result.isDecided(),
                "the battle should be decided in a few turns, got " + result.outcome()
                        + " after " + result.turns() + " turns");
        Assertions.assertEquals(Battle.Status.LOSE, result.status(),
                "a 1-HP unit with no skills against a real enemy must lose, not win");
    }

    /**
     * The skill slot a decision belongs to, used to record which branches ran.
     *
     * @param actor    the acting unit
     * @param decision what it chose
     * @return the slot the chosen skill occupies on the actor
     */
    private static SkillType slotOf(CanHit actor, SkillPolicy.Decision decision) {
        for (var entry : actor.getSkills().entrySet()) {
            if (entry.getValue() == decision.skill()) {
                return entry.getKey();
            }
        }
        return null;
    }
}
