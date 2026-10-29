package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.entity.CanHit;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.battle.BattleListener;
import com.laosun.aluminium.models.battle.SkillPolicy;
import com.laosun.aluminium.models.battle.TurnLoop;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The turn loop owns the four boundaries, in one order.
 *
 * <p>Before it existed, every caller wrote {@code stepForward -> beforeMove -> act -> afterMove}
 * itself. The driven path shipped three defects of exactly that shape (a free ultimate, a skill point
 * charged twice, a broken enemy acting anyway), so the order is pinned here rather than left to each
 * caller's memory.
 */
public class TurnLoopTest {

    /**
     * The loop calls the four boundaries in the documented order.
     */
    @Test
    public void theTurnBoundariesRunInOneOrder() {
        Character hero = Character.fromAttributes("hero", 10_000, 100, 100, 200);
        Enemy foe = BattleFixture.monster();
        RecordingBattle battle = new RecordingBattle(List.of(hero), List.of(foe));

        TurnLoop loop = new TurnLoop(battle, (b, actor, turn) -> battle.calls.add("act"));

        Assertions.assertTrue(loop.step(1), "the first actor exists");
        Assertions.assertEquals(List.of("stepForward", "beforeMove", "act", "afterMove"), battle.calls,
                "the loop must advance, start the turn, act, then end it");
        Assertions.assertNull(battle.currentMove(), "afterMove clears the current actor");
    }

    /**
     * A unit killed by damage over time still has its turn closed.
     *
     * <p>The action is called after {@code beforeMove}, so it sees the dead unit; the loop must still
     * call {@code afterMove}, or the corpse stays in the action bar and the battle never resolves.
     */
    @Test
    public void aDotKillStillClosesTheTurn() {
        Character hero = Character.fromAttributes("hero", 1_000, 100, 100, 200);
        Enemy foe = BattleFixture.monster();
        RecordingBattle battle = new RecordingBattle(List.of(hero), List.of(foe));
        hero.getBuffManager().addBuff(new DotBuff(foe, DamageElement.FIRE, 10_000, 1));

        List<String> seen = new ArrayList<>();
        TurnLoop loop = new TurnLoop(battle,
                (b, actor, turn) -> seen.add(actor.getName() + ":" + actor.isDeath()));

        Assertions.assertTrue(loop.step(1));
        Assertions.assertEquals(List.of("stepForward", "beforeMove", "afterMove"), battle.calls,
                "the turn is still closed after a DOT kill");
        Assertions.assertTrue(hero.isDeath(), "the DOT kills before the action");
        Assertions.assertEquals(List.of("hero:true"), seen,
                "the action sees the dead actor, so it must not cast");
    }

    /**
     * The canonical policy-driven loop asks the policy, casts the decision, and reports the turn.
     */
    @Test
    public void thePolicyDrivenLoopCastsTheDecision() {
        Character hero = CharacterFactory.create(1001, 80);
        hero.setAttribute(AttributeType.SPEED, new DoubleValue(200));
        Enemy foe = BattleFixture.monster();
        Battle battle = new Battle(List.of(hero), List.of(foe), new Random(0));
        battle.startBattle();

        List<String> events = new ArrayList<>();
        BattleListener listener = new BattleListener() {
            @Override
            public void onTurnStart(CanHit actor, int turn) {
                events.add("start:" + turn);
            }

            @Override
            public void onAction(CanHit actor, SkillPolicy.Decision decision) {
                events.add("action");
            }

            @Override
            public void onTurnEnd(CanHit actor, int turn) {
                events.add("end:" + turn);
            }
        };
        TurnLoop loop = TurnLoop.policyDriven(battle,
                (b, actor) -> SkillPolicy.Decision.of(
                        actor.getSkills().get(SkillType.COMMON), List.of(foe), "test"),
                listener);

        double hpBefore = foe.getCurrentHp();
        Assertions.assertTrue(loop.step(1));
        Assertions.assertTrue(foe.getCurrentHp() < hpBefore,
                "the chosen basic attack must actually settle");
        Assertions.assertEquals(List.of("start:1", "action", "end:1"), events,
                "start -> action -> end, once each");
    }

    /**
     * A battle that records the three boundaries the loop must own.
     */
    private static final class RecordingBattle extends Battle {

        private final List<String> calls = new ArrayList<>();

        private RecordingBattle(List<Character> party, List<? extends CanHit> enemies) {
            super(party, enemies, new Random(0));
        }

        @Override
        public void stepForward() {
            calls.add("stepForward");
            super.stepForward();
        }

        @Override
        public void beforeMove() {
            calls.add("beforeMove");
            super.beforeMove();
        }

        @Override
        public void afterMove() {
            calls.add("afterMove");
            super.afterMove();
        }
    }
}
