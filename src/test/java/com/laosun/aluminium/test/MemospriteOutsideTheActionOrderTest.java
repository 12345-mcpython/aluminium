package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * A memosprite whose speed the game keeps at zero is in the battle but not in the action order (2026-10-02).
 *
 * <p>⭐ The game's own words, from 小伊卡's own skill row (1140903, whose SkillEffect is `Restore`, which is why this was
 * mis-registered as a heal): 「小伊卡的速度<b>保持为0</b>，免疫负面效果，并且<b>不会出现在行动序列上</b>」. 德谬歌 (1415's memosprite) carries the
 * same panel row -- `{"attribute": "SPEED", "flat": 0, "by_ability": true}` -- so the data was right and the queue was wrong: `Signal`'s
 * guard refused any speed at or below zero, which turned the whole battle into an exception the moment such a memosprite was summoned,
 * and made every clause needing one on the field unjudgeable.
 *
 * <p>⭐ The rule is arithmetic as much as it is the game's: an action value is `10000 / speed`, so a unit at zero has none and there is
 * nothing to schedule. It is skipped rather than refused.
 *
 * <p>The third assertion is a control: a normal ally IS in the order, so "not in the order" cannot pass vacuously.
 */
public class MemospriteOutsideTheActionOrderTest {
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** 德谬歌: summoned, in the battle, out of the order. */
    @Test
    public void theZeroSpeedMemospriteIsInTheBattleButNotInTheOrder() {
        assertOutsideTheOrder(1415, 1404);
    }

    /** 小伊卡: the other memosprite whose panel says the same thing. */
    @Test
    public void littleIcaIsInTheBattleButNotInTheOrder() {
        assertOutsideTheOrder(1409, 1217);
    }

    private static void assertOutsideTheOrder(int summonerCid, int allyCid) {
        Character summoner = CharacterFactory.create(summonerCid, LEVEL);
        Character ally = CharacterFactory.create(allyCid, LEVEL);
        Battle battle = new Battle(List.of(summoner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Summon servant = battle.summonServant(summoner);
        // \u26a0 This is the line that used to throw "Speed must be greater than 0."
        battle.processRequests();

        boolean inOrder = battle.getQueueSnapshot().stream().anyMatch(s -> s.getCanHit() == servant);
        boolean inBattle = battle.allies.contains(servant);
        boolean allyInOrder = battle.getQueueSnapshot().stream().anyMatch(s -> s.getCanHit() == ally);
        System.out.println("[order] memosprite in the action order = " + inOrder + " ; in the battle = " + inBattle
                + " ; a normal ally in the order = " + allyInOrder);

        Assertions.assertFalse(inOrder, "the memosprite does not appear in the action order");
        Assertions.assertTrue(inBattle, "but it IS in the battle, so it can be aimed at and commanded");
        Assertions.assertTrue(allyInOrder, "control: an ordinary ally is in the order, so the reading is not vacuous");
    }
}
