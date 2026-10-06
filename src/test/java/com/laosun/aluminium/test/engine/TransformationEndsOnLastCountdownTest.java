package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Countdown;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1408："最后 1 个倒计时回合…结束变身".
 *
 * <p>TWO-WAY, and the countdown's turns are driven by the SHIPPED pattern (`CountdownTest`): point `currentMove` at the clock's
 * `Signal` on the action bar and call `beforeMove()`. `stepForward()` does not execute a turn at all -- the tree says so in
 * `ContentWeaknessClausesTest` and this judge does not rely on it.
 */
public class TransformationEndsOnLastCountdownTest {
    private static final int OWNER = 1408;
    private static final int MONSTER = 1002011;
    private static final String STATE = "变身";
    private static final int TURNS = 8;

    /** After the eighth countdown turn the transformation is gone. */
    @Test
    public void theEighthCountdownTurnEndsIt() {
        Assertions.assertFalse(transformedAfterCountdownTurns(TURNS),
                "「最后 1 个倒计时回合…结束变身」");
    }

    /** Note: One turn earlier the transformation must still be on. */
    @Test
    public void oneTurnEarlierItIsStillOn() {
        Assertions.assertTrue(transformedAfterCountdownTurns(TURNS - 1),
                "「最后 1 个」-- the seventh turn is not the last one");
    }

    // ==================================================================

    private static boolean transformedAfterCountdownTurns(int turns) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Skill ult = owner.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: she has an ultimate");
        SkillExecutor.execute(battle, ult, owner, List.of(owner));
        battle.processRequests();
        Assertions.assertTrue(owner.getBuffManager().hasState(STATE), "precondition: the transformation is on");
        Assertions.assertEquals(1, battle.countdownUnits().size(), "precondition: her clock exists");

        for (int turn = 0; turn < turns; turn++) {
            Countdown clock = battle.countdownUnits().isEmpty() ? null : battle.countdownUnits().getFirst();
            if (clock == null) {
                break;
            }
            battle.queue.snapshot().stream()
                    .filter(signal -> signal.getCanHit() == clock)
                    .findFirst()
                    .ifPresent(signal -> battle.currentMove = signal);
            battle.beforeMove();
            battle.processRequests();
        }
        return owner.getBuffManager().hasState(STATE);
    }
}
