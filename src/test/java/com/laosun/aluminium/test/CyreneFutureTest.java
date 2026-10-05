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
 * \u6218\u6597\u5f00\u59cb\u65f6\u2026\u5176\u4ed6\u89d2\u8272\u83b7\u5f97\u3010\u672a\u6765\u3011 (2026-10-02). Two-sided: the OTHER ally gets it, 昔涟 herself does not (the text says \u5176\u4ed6).
 */
public class CyreneFutureTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int ANAXA = 1405;
    private static final int MONSTER = 1002011;
    private static final String FUTURE = "\u672a\u6765";

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
        Assertions.assertTrue(ally, "another ally gains 【\u672a\u6765\u3011");
        Assertions.assertFalse(her, "and 昔涟 does not, because the text says \u5176\u4ed6");
    }
}
