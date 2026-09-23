package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.EnemySkill;
import com.laosun.aluminium.models.ai.TargetSelector;
import com.laosun.aluminium.models.buffs.TauntBuff;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * P5-2 / P5-4 acceptance: target selection.
 *
 * <p>Two strategies: the taunt hard constraint takes priority, then aggro-weighted random.
 */
public class TargetSelectorTest {
    private static final double EPS = 1e-9;

    @Test
    public void aggroWeightingPicksInProportion() {
        Character tank = withAggro("tank", 150);
        Character other = withAggro("other", 100);
        Battle battle = new Battle(List.of(tank, other), List.of(dummy()), new Random(42));
        Random rng = new Random(42);
        List<CanHit> candidates = List.of(tank, other);

        int tankPicked = 0;
        int rounds = 4000;
        for (int i = 0; i < rounds; i++) {
            if (TargetSelector.select(battle, candidates, TargetSelector.Intent.SINGLE, rng) == tank) {
                tankPicked++;
            }
        }

        double frequency = (double) tankPicked / rounds;
        Assertions.assertEquals(0.6, frequency, 0.03, "150 / 250 ≈ 0.6, deviation < 3%");
    }

    @Test
    public void deadCandidateIsNeverPicked() {
        Character alive = withAggro("alive", 150);
        Character dead = withAggro("dead", 100);
        dead.takeDamage(999_999);
        Battle battle = new Battle(List.of(alive, dead), List.of(dummy()), new Random(0));

        // the caller is responsible for filtering out dead targets (candidate-set convention:
        // Battle.targetableEnemies only hands over the living)
        List<CanHit> aliveCandidates = new ArrayList<>(List.of(alive, dead));
        aliveCandidates.removeIf(CanHit::isDeath);

        Random rng = new Random(1);
        for (int i = 0; i < 200; i++) {
            Assertions.assertSame(alive, TargetSelector.select(battle, aliveCandidates,
                    TargetSelector.Intent.SINGLE, rng), "a dead unit must not be selected");
        }
    }

    @Test
    public void tauntForcesTheTargetForSingleAndBlast() {
        Character tank = withAggro("tank", 100);
        Character squishy = withAggro("squishy", 150);       // higher aggro, should normally be hit more often
        squishy.getBuffManager().addBuff(new TauntBuff(2));
        Battle battle = new Battle(List.of(tank, squishy), List.of(dummy()), new Random(7));
        List<CanHit> candidates = List.of(tank, squishy);

        Random rng = new Random(7);
        for (int i = 0; i < 200; i++) {
            Assertions.assertSame(squishy,
                    TargetSelector.select(battle, candidates, TargetSelector.Intent.SINGLE, rng),
                    "single-target attack is hard-assigned by the taunt");
            Assertions.assertSame(squishy,
                    TargetSelector.select(battle, candidates, TargetSelector.Intent.BLAST, rng),
                    "the centre of a blast attack is likewise hard-assigned");
        }
    }

    @Test
    public void tauntDoesNotAffectAoeOrRandomIntents() {
        Character tank = withAggro("tank", 100);
        Character taunter = withAggro("taunter", 150);
        taunter.getBuffManager().addBuff(new TauntBuff(2));
        Battle battle = new Battle(List.of(tank, taunter), List.of(dummy()), new Random(3));
        List<CanHit> candidates = List.of(tank, taunter);

        // AoE hits everyone, bounce is random per hit: neither should be "locked" onto the taunter by the taunt
        Random rng = new Random(3);
        boolean sawTank = false;
        for (int i = 0; i < 400; i++) {
            if (TargetSelector.select(battle, candidates, TargetSelector.Intent.RANDOM, rng) == tank) {
                sawTank = true;
                break;
            }
        }
        Assertions.assertTrue(sawTank, "bounce is not bound by the taunt (it still rolls someone else)");
    }

    @Test
    public void deadTaunterLosesTheConstraintAndFallsBackToAggro() {
        Character tank = withAggro("tank", 100);
        Character taunter = withAggro("taunter", 150);
        taunter.getBuffManager().addBuff(new TauntBuff(2));
        Battle battle = new Battle(List.of(tank, taunter), List.of(dummy()), new Random(5));

        // the taunter is dead and the caller's candidate set already filtered it out → fall back
        // to aggro weighting, selecting the only one left, the tank
        taunter.takeDamage(999_999);
        List<CanHit> aliveCandidates = new ArrayList<>(List.of(tank, taunter));
        aliveCandidates.removeIf(CanHit::isDeath);

        Assertions.assertSame(tank, TargetSelector.select(battle, aliveCandidates,
                TargetSelector.Intent.SINGLE, new Random(5)), "a corpse must not be force-selected");
    }

    @Test
    public void emptyCandidateSetReturnsNull() {
        Battle battle = new Battle(List.of(withAggro("a", 100)), List.of(dummy()), new Random(0));

        Assertions.assertNull(TargetSelector.select(battle, List.of(),
                TargetSelector.Intent.SINGLE, new Random(0)));
    }

    @Test
    public void enemyAttackActuallyRunsThroughPerformAction() {
        // P5-5's minimal verification: the enemy acts as the actor, uses TargetSelector to pick
        // a target, and acts through performAction
        Enemy iceEdge = EnemyFactory.create(1002011, 90, 1);
        Character victim = Character.fromAttributes("victim", 100_000, 1000, 100, 100);
        victim.setMaxEnergy(120);                             // with no energy bar, energy gain is a no-op (maxEnergy == 0)
        Battle battle = new Battle(List.of(victim), List.of(iceEdge), new Random(0));

        Assertions.assertTrue(iceEdge.getSkills().get(SkillType.COMMON) instanceof EnemySkill,
                "the EnemySkill is properly installed on the enemy");

        battle.stepForward();                                 // Ice Edge SPD 132 > 100, it acts first
        CanHit actor = battle.queue.getCurrentActor().getCanHit();
        Assertions.assertSame(iceEdge, actor);

        CanHit target = TargetSelector.select(battle, List.of(victim), TargetSelector.Intent.SINGLE, battle.getRng());
        double hpBefore = victim.getCurrentHp();
        Assertions.assertTrue(battle.performAction(iceEdge.getSkills().get(SkillType.COMMON), List.of(target)));
        battle.processRequests();

        // expected value derived from the attacker's stat sheet: ATK × multiplier 1.0 × defence zone (attacker Lv90, victim DEF 1000)
        double levelTerm = Constant.DEFENCE_CONST + Constant.DEFENCE_PER_LEVEL * iceEdge.getLevel();
        double expected = iceEdge.getAttribute(AttributeType.ATTACK).get() * levelTerm / (1000 + levelTerm);
        Assertions.assertEquals(expected, hpBefore - victim.getCurrentHp(), 0.1,
                "Ice Edge's basic attack: ATK × 1.0 through the defence zone");
        Assertions.assertEquals(10, victim.getCurrentEnergy(), EPS, "energy gain from taking a hit");
    }

    // ==================================================================

    private static Character withAggro(String name, int aggro) {
        Character c = Character.fromAttributes(name, 10_000, 1000, 100, 100);
        c.setAggro(aggro);
        return c;
    }

    private static Enemy dummy() {
        return EnemyFactory.create(1002011, 90, 1);
    }
}
