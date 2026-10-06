package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * Character 1503 (Pearl): the Elation skill's payout -- an armed ally's attack carries an extra Elation amount.
 *
 * <p>Two-sided: the SAME ally's ordinary basic attack, measured with and without the team first being armed by
 * the Elation skill, so the difference is the payout and nothing else.
 */
public class PearlElationPayoutTest {
    private static final int PEARL = 1503;
    private static final int ELATION = 1502;
    private static final int MONSTER = 1002011;

    @Test
    public void anArmedAllyDealsMoreThanAnUnarmedOne() {
        double unarmed = basicAttackDamage(false);
        double armed = basicAttackDamage(true);
        System.out.println("[pearl_payout] the same ally's basic attack: unarmed = " + unarmed
                + " ; armed by the Elation skill = " + armed);
        Assertions.assertTrue(unarmed > 0, "the basic attack lands either way");
        Assertions.assertTrue(armed > unarmed,
                "and the armed one deals MORE (got " + armed + " vs " + unarmed + ")");
    }

    private static double basicAttackDamage(boolean armTheTeam) {
        Battle battle = new Battle(List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ELATION, 80),
                CharacterFactory.create(1204, 80)), List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character pearl = battle.characters.get(0);
        Character attacker = battle.characters.get(2);
        if (armTheTeam) {
            battle.fireTriggers(TriggerEvent.CAST_SETUP, pearl, pearl, 0, 0, SkillCategory.ELATION_DAMAGE);
        }
        double before = battle.enemies.getFirst().getCurrentHp();
        battle.castImmediate(attacker.getSkills().get(SkillType.COMMON), attacker,
                List.of(battle.enemies.getFirst()));
        return before - battle.enemies.getFirst().getCurrentHp();
    }
}
