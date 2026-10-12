package com.laosun.aluminium.test.content.characters.pearl;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Character 1503 (Pearl): Eidolon 1 raises the team's Elation damage at 2/3/4 Elation allies.
 */
public class PearlEidolon1ElationTest {
    private static final int PEARL = 1503;
    private static final int ELATION = 1502;   // Yao Guang, Elation
    private static final int HUNTER = 1204;

    /**
     * Eidolon 1's tiers, read as the difference the EIDOLON makes rather than as her absolute panel.
     *
     * <p>Note: Pearl's panel is no longer zero-based - her ten stat traces (measured absent from
     * {@code skill_traces.json}) put 10% Elation on her from the start - so an absolute assertion here would be
     * asserting that other clause as much as this one. Each reading below is therefore the difference against the
     * same team with the Eidolon condition left unmet.
     */
    @Test
    public void theElationBoostNeedsBothTheEidolonAndTheSecondElationAlly() {
        double ranked = boost(1, 2);
        double alone = boost(1, 1);
        double unranked = boost(0, 2);
        System.out.println("[pearl_e1] panel Elation -- eidolon 1 with 2 Elation allies = " + ranked
                + " ; eidolon 1 with 1 = " + alone + " ; eidolon 0 with 2 = " + unranked);

        // Note: `alone` and `unranked` both sit at the stat-trace floor, and the tier adds 0.10 on top of it.
        Assertions.assertEquals(0.10, ranked - unranked, 1e-9, "at 2 Elation allies the team gains 10%");
        Assertions.assertEquals(0.0, alone - unranked, 1e-9,
                "the first tier needs a second Elation ally, not just the Eidolon");
        Assertions.assertTrue(unranked > 0, "precondition: her stat traces give the panel a floor (" + unranked + ")");
    }

    /**
     * Builds a team with the given Eidolon rank and number of Elation characters.
     */
    private static double boost(int eidolonRank, int elationAllies) {
        List<Character> team = new ArrayList<>();
        team.add(CharacterFactory.create(PEARL, 80, true, null, null, eidolonRank));
        for (int i = 1; i < elationAllies; i++) {
            team.add(CharacterFactory.create(ELATION, 80));
        }
        team.add(CharacterFactory.create(HUNTER, 80));
        Battle battle = new Battle(team, List.of(EnemyFactory.create(1002011, 100, 1)), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character mine = battle.characters.stream().filter(c -> c.getCid() == PEARL).findFirst().orElseThrow();
        return mine.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
    }
}
