package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Character 1503 (Pearl): her Elation skill arms the team for their next attack.
 *
 * <p>Two-sided: casting it with the Elation category arms every ally, and casting with an ordinary category
 * arms nobody.
 *
 * <p>Note on a false alarm this judge caused: earlier runs of a similar probe read zero carriers and I
 * registered a suspected engine problem. Those readings were taken while the content file was still patched
 * by an experiment of mine; a same-run probe on the restored file read `delta=1.0 carriers=2`. The rules were
 * always fine. Restore the file before believing a zero.
 */
public class PearlElationSkillCarrierTest {
    private static final int PEARL = 1503;
    private static final int ELATION = 1502;
    private static final int HUNTER = 1204;
    private static final String CARRIER = "\u6e32\u67d3\u7406\u6027\uff0c\u89e3\u6784\u6b22\u6986";

    @Test
    public void theElationSkillArmsTheWholeTeamAndAnotherCategoryDoesNot() {
        List<String> armed = armedWith(SkillCategory.ELATION_DAMAGE);
        List<String> elsewhere = armedWith(SkillCategory.BPSKILL);
        System.out.println("[pearl_carrier] armed by the Elation skill: " + armed
                + " ; armed by BPSKILL: " + elsewhere);

        Assertions.assertEquals(3, armed.size(), "every ally carries the carrier for their next attack");
        Assertions.assertTrue(elsewhere.isEmpty(), "and another category arms nobody (got " + elsewhere + ")");
    }

    /** Fires CAST_SETUP with the given category and lists the allies that carry the carrier. */
    private static List<String> armedWith(SkillCategory category) {
        List<Character> team = new ArrayList<>();
        team.add(CharacterFactory.create(PEARL, 80));
        team.add(CharacterFactory.create(ELATION, 80));
        team.add(CharacterFactory.create(HUNTER, 80));
        Battle battle = new Battle(team, List.of(EnemyFactory.create(1002011, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character pearl = battle.characters.get(0);

        battle.fireTriggers(TriggerEvent.CAST_SETUP, pearl, pearl, 0, 0, category);

        List<String> carriers = new ArrayList<>();
        for (Character c : battle.characters) {
            if (c.getBuffManager().hasState(CARRIER)) {
                carriers.add(String.valueOf(c.getCid()));
            }
        }
        return carriers;
    }
}
