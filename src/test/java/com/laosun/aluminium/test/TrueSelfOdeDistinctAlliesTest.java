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

/** \u300c\u6bcf\u4ece 1 \u4e2a\u9664\u5fb7\u8c2c\u6b4c\u4ee5\u5916**\u4e0d\u540c\u7684\u961f\u53cb**\u5904\u83b7\u5f97\u3010\u8ffd\u5fc6\u3011\u540e\u300d (2026-10-02). */
public class TrueSelfOdeDistinctAlliesTest {
    private static final String COUNTER = "\u5fc6\u7075\u6280\u7684\u989d\u5916\u4e00\u51fb";

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
        // her ultimate hands 【\u672a\u6765\u3011 out afresh, so the FIRST ally can act again
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
