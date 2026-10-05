package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Countdown;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 21, the refresh clause (2026-10-02): 「在卡厄斯兰那的额外回合耗尽后不会结束变身、刷新全部额外回合并获得 #9 点【毁伤】」.
 *
 * ⭐ The countdown's turn is driven THE SHIPPED WAY (`battle.currentMove = signal; battle.beforeMove();`, as CountdownTest does) -- a hand-fired COUNTDOWN_TURN never reaches his
 * table, because the engine announces it with the ally broadcaster inside beforeMove.
 */
public class WorldOdeExtraTurnsRefreshTest {
    private static final int LEVEL = 80;
    private static final int PHAINON = 1408;
    private static final int MONSTER = 1002011;
    private static final String STATE = "永续的燃烧";
    private static final String COUNTER = "额外回合计数";
    private static final String MARK = "毁伤";

    @Test
    public void theLastExtraTurnRefreshesWhileEverBurning() {
        double[] with = run(true);
        double[] without = run(false);
        System.out.println("[refresh] with the state: 变身 " + with[0] + ", counter " + with[1] + ", 【毁伤】 " + with[2]
                + " ; without it: 变身 " + without[0] + ", counter " + without[1] + ", 【毁伤】 " + without[2]);
        Assertions.assertTrue(with[0] > 0, "the transformation survives the exhausted turn while ever-burning");
        Assertions.assertEquals(0.0, with[1], 1e-9, "and every extra turn is refreshed, so the counter is empty again");
        Assertions.assertEquals(4.0, with[2], 1e-9, "and 【毁伤】 gains #9 = 4");
        Assertions.assertEquals(0.0, without[0], 1e-9, "while without the state the same turn ends the transformation");
        Assertions.assertEquals(0.0, without[2], 1e-9, "and nothing is gained");
    }

    /** [变身 stacks, the counter, 【毁伤】] after the exhausted extra turn, with or without the ever-burning state. */
    private static double[] run(boolean everBurning) {
        Character him = CharacterFactory.create(PHAINON, LEVEL);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        him = battle.characters.getFirst();
        // ⭐ The ode grants 【永续的燃烧】 (2026-10-02), so it must land before his transformation.
        var odeSprite = battle.summonServant(CharacterFactory.create(1415, LEVEL));
        battle.processRequests();
        var ode = odeSprite.skillAt(21);
        Assertions.assertNotNull(ode, "precondition: slot 21");
        SkillExecutor.execute(battle, ode, odeSprite, List.of(battle.characters.getFirst()));
        battle.processRequests();
        var ult = him.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: he has an ultimate");
        SkillExecutor.execute(battle, ult, him, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        him = battle.characters.getFirst();
        Assertions.assertTrue(him.getBuffManager().stacksOf("变身") > 0, "precondition: he transformed");
        if (!everBurning) {
            him.getBuffManager().removeState(STATE);
        }
        Assertions.assertEquals(everBurning ? 1 : 0, Math.min(1, him.getBuffManager().stacksOf(STATE)), "precondition: the state");

        // ⭐ WITH AN OWNER (2026-10-02): the engine announces COUNTDOWN_TURN to the countdown’s OWN side, and an owner-less countdown has none -- measured, the turn
        // reached nobody until the owner was passed, exactly as CountdownTest does it.
        Countdown countdown = battle.startCountdown(him, "额外回合", 90);
        // ⭐ The counter's declared cap is 8, so it starts at 7 and the counting rule's own +1 makes this the eighth turn -- the exhausted one.
        him.getResources().gain(COUNTER, 7);
        Signal signal = battle.queue.snapshot().stream()
                .filter(s -> s.getCanHit() == countdown)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the countdown has no signal on the action order"));
        battle.currentMove = signal;
        battle.beforeMove();
        battle.processRequests();
        him = battle.characters.getFirst();
        return new double[]{him.getBuffManager().stacksOf("变身"),
                him.getResources().value(COUNTER), him.getResources().value(MARK)};
    }
}
