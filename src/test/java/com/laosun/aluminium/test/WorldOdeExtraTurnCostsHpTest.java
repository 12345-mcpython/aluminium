package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Countdown;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 21, the last clause (2026-10-02): 「额外回合开始时，卡厄斯兰那消耗等同于当前生命值 #2% 的生命值」.
 *
 * \u2b50 Two-sided: with the state the turn costs him about 15% of the HP he had; without it the same turn costs him nothing. The cost is a share of his CURRENT HP, so the judge
 * computes the expectation from the HP it reads immediately before the turn -- the engine's own number.
 */
public class WorldOdeExtraTurnCostsHpTest {
    private static final int LEVEL = 80;
    private static final int PHAINON = 1408;
    private static final int MONSTER = 1002011;
    private static final String STATE = "\u6c38\u7eed\u7684\u71c3\u70e7";

    @Test
    public void theExtraTurnCostsHpWhileEverBurning() {
        double[] with = run(true);
        double[] without = run(false);
        System.out.println("[hp_cost] with the state he lost " + with[0] + " of " + with[1]
                + " (15% would be " + (with[1] * 0.15) + ") ; without it he lost " + without[0] + " of " + without[1]);
        Assertions.assertEquals(with[1] * 0.15, with[0], with[1] * 1e-6,
                "the extra turn costs 15% of the HP he had at that moment");
        Assertions.assertEquals(0.0, without[0], 1e-9, "and without the state the same turn costs nothing");
    }

    /** [the HP the extra turn cost, the HP he had just before it] */
    private static double[] run(boolean everBurning) {
        Character him = CharacterFactory.create(PHAINON, LEVEL);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        him = battle.characters.getFirst();

        // \u2b50 The ode lands first: it is the sentence that grants 【永续的燃烧】
        var odeSprite = battle.summonServant(CharacterFactory.create(1415, LEVEL));
        battle.processRequests();
        var ode = odeSprite.skillAt(21);
        Assertions.assertNotNull(ode, "precondition: slot 21");
        SkillExecutor.execute(battle, ode, odeSprite, List.of(him));
        battle.processRequests();
        him = battle.characters.getFirst();
        if (!everBurning) {
            him.getBuffManager().removeState(STATE);
        }
        Assertions.assertEquals(everBurning ? 1 : 0, Math.min(1, him.getBuffManager().stacksOf(STATE)), "precondition: the state");

        Countdown countdown = battle.startCountdown(him, "\u989d\u5916\u56de\u5408", 90);
        Signal signal = battle.queue.snapshot().stream()
                .filter(s -> s.getCanHit() == countdown)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the countdown has no signal on the action order"));
        double before = him.getCurrentHp();
        battle.currentMove = signal;
        battle.beforeMove();
        battle.processRequests();
        return new double[]{before - battle.characters.getFirst().getCurrentHp(), before};
    }
}
