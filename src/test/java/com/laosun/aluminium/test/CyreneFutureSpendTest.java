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

/** \u300c\u6301\u6709\u3010\u672a\u6765\u3011\u7684\u6211\u65b9\u76ee\u6807\u884c\u52a8\u65f6\u6d88\u8017\u3010\u672a\u6765\u3011\u4f7f\u6614\u6d9f\u83b7\u5f97 1 \u70b9\u3010\u8ffd\u5fc6\u3011\u300d (2026-10-02). */
public class CyreneFutureSpendTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int ANAXA = 1405;
    private static final int MONSTER = 1002011;
    private static final String FUTURE = "\u672a\u6765";
    private static final String MEMORY = "\u8ffd\u5fc6";

    @Test
    public void theAllyPaysALayerAndSheIsCredited() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character him = CharacterFactory.create(ANAXA, LEVEL);
        Battle battle = new Battle(List.of(cyrene, him),
                List.of(EnemyFactory.create(MONSTER, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character ally = battle.characters.get(1);
        Character her = battle.characters.get(0);
        Assertions.assertTrue(ally.getBuffManager().hasState(FUTURE), "precondition: the ally holds it");
        double before = her.getResources().has(MEMORY) ? her.getResources().value(MEMORY) : 0;
        battle.currentMove = new Signal(ally);
        battle.beforeMove();
        battle.processRequests();
        boolean still = battle.characters.get(1).getBuffManager().hasState(FUTURE);
        double after = battle.characters.get(0).getResources().has(MEMORY)
                ? battle.characters.get(0).getResources().value(MEMORY) : 0;
        System.out.println("[future_spend] after the ally acts: 【\u672a\u6765\u3011 still on it = " + still
                + " ; 昔涟's 【\u8ffd\u5fc6\u3011 " + before + " -> " + after);
        Assertions.assertFalse(still, "acting consumes it");
        Assertions.assertEquals(before + 1, after, 1e-9, "and she is credited one point");
    }
}
