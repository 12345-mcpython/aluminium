package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Her skill's +20% and her ult's +60% CRIT CHANCE must both count (1209,).
 *
 * <p>FILE-DRIVEN, and the numbers are the claim: 0.2 after the skill, 0.8 after the ult as well. A replaced modifier
 * gives 0.6 at the second reading, which is what the mutation has to produce.
 */
public class YanqingCritChanceStackingTest {
    private static final int OWNER = 1209;
    private static final int MONSTER = 1002011;

    /** Two sources, two shares, and the total. */
    @Test
    public void bothCritChanceSourcesAreCounted() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        double base = owner.getAttribute(AttributeType.CRIT_CHANCE).get();

        battle.fireTriggers(TriggerEvent.SKILL_CAST, owner, owner, 0, 0);
        battle.processRequests();
        double afterSkill = owner.getAttribute(AttributeType.CRIT_CHANCE).get();

        battle.fireTriggers(TriggerEvent.ULT_CAST, owner, owner, 0, 0);
        battle.processRequests();
        double afterUlt = owner.getAttribute(AttributeType.CRIT_CHANCE).get();

        Assertions.assertEquals(base + 0.2, afterSkill, 1e-6, "the skill's 0.2 on top of her own value");
        Assertions.assertEquals(base + 0.8, afterUlt, 1e-6, "and the ult's own 0.6 must be counted, not replace it");
    }
}
