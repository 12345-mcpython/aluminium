package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/** 「每从 1 个除德谬歌以外**不同的队友**处获得【追忆】后」 (2026-10-02). */
public class TrueSelfOdeDistinctAlliesTest {
    private static final String COUNTER = "忆灵技的额外一击";

    @Test
    public void onlyTheFirstTimeEachAllyCounts() {
        Character cyrene = CharacterFactory.create(1415, 80);
        Character one = CharacterFactory.create(1405, 80);
        Character two = CharacterFactory.create(1403, 80);
        Battle battle = new Battle(List.of(cyrene, one, two),
                List.of(EnemyFactory.create(1002011, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        var dragon = battle.summonMemosprite(battle.characters.get(0));
        battle.processRequests();
        act(battle, 1);
        int afterOne = counter(dragon);
        act(battle, 2);
        int afterTwo = counter(dragon);
        // her ultimate hands 【未来】 out afresh, so the FIRST ally can act again
        Character her = battle.characters.get(0);
        // "after she acts" is her TURN_END, which is what hands the future out again.
        battle.currentMove = new Signal(her);
        battle.afterMove();
        battle.processRequests();
        act(battle, 1);
        int afterRepeat = counter(dragon);
        System.out.println("[distinct_allies] after ally1: " + afterOne + " ; after ally2: " + afterTwo
                + " ; after ally1 acts again: " + afterRepeat);
        Assertions.assertEquals(1, afterOne, "the first ally counts once");
        Assertions.assertEquals(2, afterTwo, "a second, different ally counts again");
        Assertions.assertEquals(2, afterRepeat, "the same ally a second time adds nothing");
    }

    private static void act(Battle battle, int index) {
        battle.currentMove = new Signal(battle.characters.get(index));
        battle.beforeMove();
        battle.processRequests();
    }

    private static int counter(com.laosun.aluminium.models.Summon dragon) {
        return dragon.getResources().has(COUNTER) ? dragon.getResources().value(COUNTER) : 0;
    }
}
