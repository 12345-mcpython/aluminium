package com.laosun.aluminium.test.content.characters;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Character 1503 (Pearl): Eidolon 1 raises the team's Elation damage at 2/3/4 Elation allies. */
public class PearlEidolon1ElationTest {
    private static final int PEARL = 1503;
    private static final int ELATION = 1502;   // Yao Guang, Elation
    private static final int HUNTER = 1204;

    @Test
    public void theElationBoostNeedsBothTheEidolonAndTheSecondElationAlly() {
        double ranked = boost(1, 2);
        double alone = boost(1, 1);
        double unranked = boost(0, 2);
        System.out.println("[pearl_e1] Elation boost -- eidolon 1 with 2 Elation allies = " + ranked
                + " ; eidolon 1 with 1 = " + alone + " ; eidolon 0 with 2 = " + unranked);

        Assertions.assertEquals(0.10, ranked, 1e-9, "at 2 Elation allies the team gains 10%");
        Assertions.assertEquals(0.0, alone, 1e-9, "the first tier needs a second Elation ally");
        Assertions.assertEquals(0.0, unranked, 1e-9, "and the rule carries min_eidolon: 1");
    }

    /** Builds a team with the given Eidolon rank and number of Elation characters. */
    private static double boost(int eidolonRank, int elationAllies) {
        List<Character> team = new ArrayList<>();
        team.add(CharacterFactory.create(PEARL, 80, true, null, null, eidolonRank));
        for (int i = 1; i < elationAllies; i++) {
            team.add(CharacterFactory.create(ELATION, 80));
        }
        team.add(CharacterFactory.create(HUNTER, 80));
        Battle battle = new Battle(team, List.of(EnemyFactory.create(1002011, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character mine = battle.characters.stream().filter(c -> c.getCid() == PEARL).findFirst().orElseThrow();
        return mine.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
    }
}
