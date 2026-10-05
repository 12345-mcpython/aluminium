package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * At battle start... other characters gain [未来] (2026-10-02). Two-sided: the OTHER ally gets it, Cyrene (昔涟) herself does not (the text says 其他, "other").
 */
public class CyreneFutureTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int ANAXA = 1405;
    private static final int MONSTER = 1002011;
    private static final String FUTURE = "未来";

    @Test
    public void onlyTheOthersGetFuture() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character him = CharacterFactory.create(ANAXA, LEVEL);
        Battle battle = new Battle(List.of(cyrene, him),
                List.of(EnemyFactory.create(MONSTER, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        boolean ally = battle.characters.get(1).getBuffManager().hasState(FUTURE);
        boolean her = battle.characters.get(0).getBuffManager().hasState(FUTURE);
        System.out.println("[future] at battle start: the ally has it = " + ally + " ; 昔涟 herself = " + her);
        Assertions.assertTrue(ally, "another ally gains 【未来】");
        Assertions.assertFalse(her, "and 昔涟 does not, because the text says 其他");
    }
}
