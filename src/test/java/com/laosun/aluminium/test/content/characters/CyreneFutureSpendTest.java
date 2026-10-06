package com.laosun.aluminium.test.content.characters;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/** "持有[未来]的我方目标行动时消耗[未来]使昔涟获得 1 点[追忆]". */
public class CyreneFutureSpendTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int ANAXA = 1405;
    private static final int MONSTER = 1002011;
    private static final String FUTURE = "未来";
    private static final String MEMORY = "追忆";

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
        System.out.println("[future_spend] after the ally acts: 【未来】 still on it = " + still
                + " ; 昔涟's 【追忆】 " + before + " -> " + after);
        Assertions.assertFalse(still, "acting consumes it");
        Assertions.assertEquals(before + 1, after, 1e-9, "and she is credited one point");
    }
}
