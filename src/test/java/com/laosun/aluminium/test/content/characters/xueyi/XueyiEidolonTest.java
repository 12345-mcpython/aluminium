package com.laosun.aluminium.test.content.characters.xueyi;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1214 雪衣's Eidolons 1 and 6: the follow-up deals 40% more, and the cap drops from eight stacks to six.
 */
public class XueyiEidolonTest {

    private static final int XUEYI = 1214;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;
    private static final String KARMA = "恶报";

    @Test
    public void eidolonOneRaisesTheFollowupByFortyPercent() {
        double e0 = followUpDamage(0);
        double e1 = followUpDamage(1);
        System.out.println("[1214] follow-up " + e0 + " -> " + e1 + " ratio=" + (e1 / e0));
        Assertions.assertTrue(e0 > 0, "precondition: the follow-up landed");
        Assertions.assertEquals(1.4, e1 / e0, 0.02, "「天赋的追加攻击造成的伤害提高 40%」");
    }

    @Test
    public void eidolonSixFiresAtSixInsteadOfEight() {
        Assertions.assertEquals(5, stacksAfter(6, 4), "E6 at five stacks: still building up");
        Assertions.assertEquals(0, stacksAfter(6, 5), "E6 at six stacks: the follow-up fires and consumes all");
        Assertions.assertEquals(7, stacksAfter(0, 6), "without E6, seven stacks stay on");
    }

    /** Builds {@code reached} stacks, then fires ONE more stack-producing event; returns the stacks left. */
    private static int stacksAfter(int eidolon, int reached) {
        Battle battle = battle(eidolon);
        Character xueyi = battle.characters().getFirst();
        Enemy enemy = (Enemy) battle.enemies().getFirst();
        for (int i = 0; i < reached; i++) {
            reduce(battle, enemy, 1);
        }
        Assertions.assertEquals(reached, xueyi.getBuffManager().stacksOf(KARMA), "precondition");
        reduce(battle, enemy, 1);
        return xueyi.getBuffManager().stacksOf(KARMA);
    }

    /** One short of the cap, then the hit that pushes it over: the damage that hit produces. */
    private static double followUpDamage(int eidolon) {
        int cap = 8;
        Battle battle = battle(eidolon);
        Enemy enemy = (Enemy) battle.enemies().getFirst();
        for (int i = 0; i < cap - 1; i++) {
            reduce(battle, enemy, 1);
        }
        double before = enemy.getCurrentHp();
        reduce(battle, enemy, 1);
        return before - enemy.getCurrentHp();
    }

    /** A real toughness reduction, keeping the bar from emptying so the loop can repeat. */
    private static void reduce(Battle battle, Enemy enemy, double points) {
        battle.reduceToughness(battle.characters().get(1), enemy, DamageElement.FIRE, points, null);
        enemy.setStance(1000);
    }

    private static Battle battle(int eidolon) {
        Character xueyi = CharacterFactory.create(XUEYI, LEVEL, true, null, null, eidolon);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(xueyi, ally)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle;
    }
}