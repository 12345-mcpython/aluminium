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
    public void thePayoutScalesWithHowManyTiersTheTeamQualifiesFor() {
        double two = payoutWith(2);
        double four = payoutWith(4);
        System.out.println("[pearl_payout] payout with 2 Elation allies = " + two + " ; with 4 = " + four
                + " ; ratio = " + (four / two));
        Assertions.assertTrue(two > 0, "the payout happens at all");
        Assertions.assertEquals(0.85 / 0.25, four / two, 1e-6,
                "four allies qualify for 0.25+0.20+0.40 = 0.85 of the base, two for 0.25, and the damage zones"
                        + " cancel in the ratio");
    }

    /** The armour the payout adds, measured as (armed - unarmed) for a team with that many Elation characters. */
    private static double payoutWith(int elationAllies) {
        double unarmed = basicAttackDamage(elationAllies, false);
        double armed = basicAttackDamage(elationAllies, true);
        return armed - unarmed;
    }

    private static double basicAttackDamage(int elationAllies, boolean armTheTeam) {
        List<Character> team = new java.util.ArrayList<>();
        team.add(CharacterFactory.create(PEARL, 80));
        for (int i = 1; i < elationAllies; i++) {
            team.add(CharacterFactory.create(ELATION, 80));
        }
        team.add(CharacterFactory.create(1204, 80));
        Battle battle = new Battle(team, List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character pearl = battle.characters.get(0);
        Character attacker = battle.characters.get(battle.characters.size() - 1);
        if (armTheTeam) {
            battle.fireTriggers(TriggerEvent.CAST_SETUP, pearl, pearl, 0, 0, SkillCategory.ELATION_DAMAGE);
        }
        double before = battle.enemies.getFirst().getCurrentHp();
        battle.castImmediate(attacker.getSkills().get(SkillType.COMMON), attacker,
                List.of(battle.enemies.getFirst()));
        return before - battle.enemies.getFirst().getCurrentHp();
    }
}
