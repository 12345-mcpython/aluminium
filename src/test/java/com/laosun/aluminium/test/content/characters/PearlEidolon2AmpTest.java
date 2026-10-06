package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/** Character 1503 (Pearl): Eidolon 2 raises the whole team's 增笑 by 15%. */
public class PearlEidolon2AmpTest {
    private static final int PEARL = 1503;
    private static final int ALLY = 1204;

    @Test
    public void theAmpArrivesAtEidolonTwoAndNowhereElse() {
        double none = ampAt(0);
        double one = ampAt(1);
        double two = ampAt(2);
        System.out.println("[pearl_e2] team ELATION_DAMAGE_AMP at eidolon 0/1/2 = " + none + " / " + one + " / " + two);

        Assertions.assertEquals(0.0, none, 1e-9, "no amp without the eidolon");
        Assertions.assertEquals(0.0, one, 1e-9, "and Eidolon 1 does not grant it either");
        Assertions.assertEquals(0.15, two, 1e-9, "Eidolon 2 grants 15%");
    }

    /** Returns a NON-Pearl ally's 增笑 at the given Eidolon rank -- "我方全体目标" includes the others. */
    private static double ampAt(int rank) {
        Battle battle = new Battle(List.of(CharacterFactory.create(PEARL, 80, true, null, null, rank),
                        CharacterFactory.create(ALLY, 80)),
                List.of(EnemyFactory.create(1002011, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character ally = battle.characters.get(1);
        return ally.getAttribute(AttributeType.ELATION_DAMAGE_AMP).get();
    }
}
