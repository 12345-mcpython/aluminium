package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1308 Acheron, from her own file: the [残梦] resource the document caps at 9, beside a stack it never caps at all.
 */
public class AcheronTest {
    private static final int ACHERON = 1308;
    private static final int LEVEL = 80;
        /** Note: One Slashed Dream per Skill, stopped at the document's nine. */
    @Test
    public void theSkillFeedsSlashedDreamUpToNine() {
        Character acheron = CharacterFactory.create(ACHERON, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(acheron), List.of(enemy), fixed());
        battle.startBattle();

        Assertions.assertEquals(0, dreamOf(acheron), "the document states no initial value, so it starts at 0");
        battle.fireTriggers(TriggerEvent.SKILL_CAST, acheron, enemy, 0, 0);
        Assertions.assertEquals(1, dreamOf(acheron), "「获得1点【残梦】」 (gains 1 point of [残梦])");

        for (int i = 0; i < 11; i++) {
            battle.fireTriggers(TriggerEvent.SKILL_CAST, acheron, enemy, 0, 0);
        }
        Assertions.assertEquals(9, dreamOf(acheron),
                "「【残梦】达到9点时可激活终结技」 (at 9 points of [残梦] the Ultimate can be activated) -- twelve casts must still read nine");
    }


    /** The declared resource's value, read through the combatant's own manager. */
    private static int dreamOf(Character unit) {
        return unit.getResources().get("残梦").getValue();
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }
}
