package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 121 Huohuo (藿藿): "after casting the Skill, Huohuo gains [禳命], lasting 2 turns, and the remaining turns decrease by 1 at the start of each of Huohuo's turns".
 *
 * <p>The point is WHOSE clock spends it: the sentence names Huohuo (藿藿), not the party. Note: The drive is the one a green sibling uses
 * (`ArlanEidolonFourTest`): a timed buff ticks in two halves per turn, and expiry is announced on the late one.
 */
public class HuohuoTalismanDurationTest {
    private static final int HUOHUO = 1217;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String STATE = "禳命";

    /** A teammate's turn does not spend it; hers does, and the count is the one the sentence states. */
    @Test
    public void theTalismanRunsOnHerOwnClock() {
        Character her = CharacterFactory.create(HUOHUO, 80, false, null, null, 0);
        Character ally = CharacterFactory.create(ALLY, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(her, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        SkillExecutor.execute(battle, her.getSkills().get(SkillType.SKILL), her, List.of(her));
        battle.processRequests();
        Assertions.assertTrue(her.getBuffManager().hasState(STATE), "precondition: her skill grants 【禳命】 (Divine Provision)");

        spendTurnOf(battle, ally);
        boolean afterAllyTurn = her.getBuffManager().hasState(STATE);
        spendTurnOf(battle, her);
        boolean afterHerFirstTurn = her.getBuffManager().hasState(STATE);
        spendTurnOf(battle, her);
        boolean afterHerSecondTurn = her.getBuffManager().hasState(STATE);
        System.out.println("[huohuo-clock] after the ally's turn=" + afterAllyTurn
                + " ; after her 1st=" + afterHerFirstTurn + " ; after her 2nd=" + afterHerSecondTurn);

        Assertions.assertTrue(afterAllyTurn,
                "「藿藿每回合开始时」 (at the start of each of Huohuo's turns)-- the clock is HERS, so a teammate's turn costs it nothing");
        Assertions.assertTrue(afterHerFirstTurn, "「持续 2 回合」 (lasts 2 turns)-- one of her turns is not two");
        Assertions.assertFalse(afterHerSecondTurn, "「持续回合数减 1」 (the remaining turns are reduced by 1)-- two of her turns spend it");
    }

    /** Note: Half a turn is `beforeMove()` alone; a full one is both halves, and expiry lands on the late half. */
    private static void spendTurnOf(Battle battle, Character unit) {
        Signal signal = battle.queue.snapshot().stream()
                .filter(candidate -> candidate.getCanHit() == unit).findFirst()
                .orElseThrow(() -> new AssertionError("precondition: the unit is in the queue"));
        battle.currentMove = signal;
        battle.beforeMove();
        battle.afterMove();
        battle.processRequests();
    }
}
