package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1412 Cerydra (刻律德菈)'s Skill, second half: "when charge reaches 6 points, automatically upgrade the character's [军功] to [爵位]".
 *
 * <p>Nothing new was needed for this: {@code RESOURCE_CHANGED} + {@code self_resource:充能 >= 6} + {@code holder_of:军功}
 * all ship already. Her skill grants 1 charge per cast, so six casts cross the threshold - and five must not, which is
 * the whole point of the case.
 *
 * <p>Note: "a character holding [爵位] is considered to also hold [军功]" is why the rule ADDS Peerage (爵位) and does not remove Merit (军功): the sentence's
 * "counts as holding both" then holds by construction. Note: The same sentence's clause "and removes its control-class negative states" is registered,
 * not written: the engine has no "clear a class of debuffs" capability.
 */
public class PeerageUpgradeTest {
    private static final int CERYDRA = 1412;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String MERIT = "军功";
    private static final String PEERAGE = "爵位";

    /** Five charges is not six: the state appears exactly on the cast that crosses the threshold. */
    @Test
    public void theUpgradeHappensAtSixChargeAndNotBefore() {
        Character cerydra = CharacterFactory.create(CERYDRA, LEVEL, false, null, null, 0);
        Character ally = CharacterFactory.create(ALLY, LEVEL, false, null, null, 0);
        ally.setTriggerTable(new TriggerTable(ALLY, List.of()));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(cerydra, ally), List.of(enemy), new Random(0));
        battle.startBattle();

        for (int cast = 1; cast <= 5; cast++) {
            cerydra.getSkills().get(SkillType.SKILL).execute(battle, cerydra, List.of(ally));
            battle.processRequests();
        }
        Assertions.assertTrue(ally.getBuffManager().hasState(MERIT), "precondition: the ally carries [军功]");
        Assertions.assertFalse(ally.getBuffManager().hasState(PEERAGE),
                "five charges is below the threshold, so no [爵位] yet");

        cerydra.getSkills().get(SkillType.SKILL).execute(battle, cerydra, List.of(ally));
        battle.processRequests();

        Assertions.assertTrue(ally.getBuffManager().hasState(PEERAGE),
                "the sixth charge upgrades [军功] to [爵位]");
        Assertions.assertTrue(ally.getBuffManager().hasState(MERIT),
                "and [爵位] COUNTS AS [军功], so both are on (the rule adds, it does not replace)");
    }
}
