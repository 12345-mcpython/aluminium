package com.laosun.aluminium.test.content.characters.archer;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.AbstractBuff;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1015 Archer (Archer)'s 【回路连接】: 「【回路连接】状态下施放战技后，<b>本回合不会结束</b>」.
 *
 * <p><b>What the engine could say.</b> There is no "the turn does not end" switch - the turn is closed by {@code afterMove}. What the
 * vocabulary does have is {@code EXTRA_TURN}: a <b>free</b> turn (no duration is spent, see
 * {@code FreeTurnDoesNotSpendDurationsTest}), which is the engine's reading of "he acts again without a turn passing". Every
 * reading below is about that one fact: he acts again at once, his own counters do not reset in between, and the loop can run
 * its five casts inside one turn.
 *
 * <p>The two registered differences (a new {@code TURN_START} is fired for him; DoTs still tick on an inserted turn) are what
 * the two {@code !self has_state 回路连接} gates in his own file are for - the first of them is asserted here, because without it
 * his Eidolon 6 would pay one skill point per loop cast instead of one per turn.
 */
public class ArcherLoopTurnTest {

    private static final int ARCHER = 1015;
    private static final int LEVEL = 80;
    private static final String LOOP = "char:1015/loop_link";

    /**
     * Inside the loop a Skill cast hands him the next action immediately, and the loop's own counters survive it.
     */
    @Test
    public void aLoopCastDoesNotEndTheTurn() {
        Run run = new Run(0);
        Assertions.assertFalse(run.archer.getBuffManager().hasState(LOOP), "precondition: not in the loop yet");

        run.castSkill();
        Assertions.assertTrue(run.archer.getBuffManager().hasState(LOOP), "precondition: the cast entered the loop");
        Assertions.assertTrue(run.battle.queue().hasPendingExtraTurn(),
                "the cast scheduled a FREE turn for her (the clause is EXTRA_TURN, not a queue accident)");

        // The turn ends; the next step must be his again (a free turn), not the enemy's.
        run.battle.afterMove();
        run.battle.stepForward();
        Assertions.assertSame(run.archer, run.battle.queue().getCurrentActor().getCanHit(),
                "「本回合不会结束」 — he acts again at once");
        Assertions.assertEquals(1, run.layers(), "and the loop counted this cast");
    }

    /**
     * The free turn spends no duration: a three-turn state on him is still on three turns after it.
     */
    @Test
    public void theExtraActionSpendsNoDuration() {
        Run run = new Run(0);
        run.castSkill();
        run.archer.getBuffManager().addBuff(new StateBuff("测试", 3));

        run.battle.afterMove();
        run.battle.stepForward();
        run.battle.beforeMove();

        Assertions.assertEquals(3, run.throwawayTurns(),
                "「不消耗回合数」 — the free turn spends nothing");
    }

    /**
     * {@code TURN_START} fires on the free turn too, so his own per-turn clauses must be gated by the loop state.
     *
     * <p>Eidolon 6 pays one skill point at the start of his turn; inside the loop, one turn is five casts.
     */
    @Test
    public void theEidolonSixPayoutIsNotPaidOncePerLoopCast() {
        Run run = new Run(6);
        double before = run.skillPoints();
        run.castSkill();
        run.battle.afterMove();
        run.battle.stepForward();
        run.battle.beforeMove();
        run.battle.processRequests();
        double gainedInTheLoop = run.skillPoints() - before;
        // The Skill itself costs a point; the Eidolon's turn-start point must not be paid again inside the loop.
        Assertions.assertTrue(gainedInTheLoop <= 0,
                "the loop's inserted turn must not pay the 回合开始 point again (gained " + gainedInTheLoop + ")");
    }

    // ==================================================================

    private static final class Run {
        private final Character archer;
        private final Enemy enemy;
        private final Battle battle;

        private Run(int eidolon) {
            archer = CharacterFactory.create(ARCHER, LEVEL, false, null, null, eidolon);
            enemy = BattleFixture.monster();
            battle = new Battle(List.of(archer), List.of(enemy), BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            battle.processRequests();
        }

        private void castSkill() {
            battle.requestSkill(archer.getSkills().get(SkillType.SKILL), archer, List.of(enemy));
            battle.processRequests();
        }

        private int throwawayTurns() {
            return archer.getBuffManager().allBuffsOf(StateBuff.class).stream()
                    .filter(buff -> "测试".equals(buff.getState()))
                    .mapToInt(AbstractBuff::remainingTurns)
                    .findFirst().orElse(-1);
        }

        private int layers() {
            return archer.getBuffManager().stacksOf("internal:1015/loop_layers");
        }

        private double skillPoints() {
            return battle.getSkillPointPolicy().getValue();
        }
    }
}
