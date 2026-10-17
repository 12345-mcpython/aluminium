package com.laosun.aluminium.test.content.characters.boothill;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * F-3: 「强化普攻无法恢复战技点」, from the data's bp_add column (2026-10-02).
 */
public class BoothillEnhancedBasicTest {
    private static final String STANDOFF = "绝命对峙";

    @Test
    public void theEnhancedBasicRestoresNothingWhileTheOrdinaryOneRestoresAPoint() {
        int ordinary = skillPointsAfter(false);
        int enhanced = skillPointsAfter(true);
        System.out.println("[boothill_sp] skill points after an ordinary basic = " + ordinary
                + " ; after the enhanced basic inside Standoff = " + enhanced);
        Assertions.assertEquals(4, ordinary, "an ordinary basic attack restores one point (start 3)");
        Assertions.assertEquals(2, enhanced, "his Skill spent one and the enhanced basic restored none");
    }

    /**
     * Casts his basic once, either straight away or after his Skill has put him in Standoff.
     */
    private static int skillPointsAfter(boolean enhanced) {
        Character boothill = CharacterFactory.create(1315, 80);
        Battle battle = new Battle(List.of(boothill), List.of(EnemyFactory.create(1002011, 100, 1)), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        boothill = battle.characters.getFirst();
        if (enhanced) {
            battle.currentMove = new Signal(boothill);          // useSkill takes the actor from the current move
            Assertions.assertTrue(battle.useSkill(boothill.getSkills().get(SkillType.SKILL),
                    List.of(battle.enemies.getFirst())), "precondition: his Skill is affordable");
            battle.processRequests();
            boothill = battle.characters.getFirst();
            Assertions.assertTrue(boothill.getBuffManager().hasState(STANDOFF), "precondition: Standoff is up");
        }
        battle.currentMove = new Signal(boothill);
        battle.useSkill(boothill.getSkills().get(SkillType.COMMON), List.of(battle.enemies.getFirst()));
        battle.processRequests();
        return battle.getSkillPoints();
    }
}
