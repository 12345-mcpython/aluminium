package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.buff.StackableStateBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * "开不败": half of a teammate's ended [好活当赏] (2026-10-02).
 *
 * <p>Four instances end, so half of them is the whole number 2 and no rounding can hide behind the arithmetic. The state is
 * applied through the manager (a hand-built TABLE never receives STATE_ENDED -- measured), and the rule under test lives in
 * 1505's own content file.
 */
public class KaiBuBaiTest {
    private static final int EVANESCIA = 1505;
    private static final int TEAMMATE = 1513;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String GIFT = "好活当赏";

    /** Four instances end, and she converts half of them. */
    @Test
    public void halfOfTheEndedGiftBecomesHers() {
        Character evanescia = CharacterFactory.create(EVANESCIA, LEVEL, false, null, null, 0);
        Character teammate = CharacterFactory.create(TEAMMATE, LEVEL, false, null, null, 0);
        Enemy victim = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(evanescia, teammate), List.of(victim), new Random(0));
        battle.startBattle();
        battle.processRequests();

        int before = evanescia.getResources().value(GIFT);
        for (int i = 0; i < 4; i++) {
            teammate.getBuffManager().addBuff(new StackableStateBuff(GIFT, 9, true, 99));
        }
        Assertions.assertEquals(4, teammate.getBuffManager().stacksOf(GIFT), "precondition: four instances");

        int removed = teammate.getBuffManager().removeState(GIFT);
        battle.processRequests();
        int after = evanescia.getResources().value(GIFT);
        System.out.println("[kai-bu-bai] removed=" + removed + " hers before=" + before + " after=" + after);
        Assertions.assertEquals(4, removed, "the sweep takes all four");
        Assertions.assertEquals(before + 2, after, "half of four -- the whole number 2 -- becomes hers");
    }
}
