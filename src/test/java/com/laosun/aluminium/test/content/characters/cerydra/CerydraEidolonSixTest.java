package com.laosun.aluminium.test.content.characters.cerydra;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1412 刻律德菈's Eidolon 6, the two penetration halves:「持有【军功】的角色全属性抗性穿透提高 20%」 and 「场上存在持有【军功】
 * 的队友时，刻律德菈全属性抗性穿透提高 20%」.
 */
public class CerydraEidolonSixTest {

    @Test
    public void theMeritHolderPiercesMoreAndTheBonusMoves() {
        Character her = CharacterFactory.create(1412, 80, true, null, null, 6);
        Character first = CharacterFactory.create(1101, 80);
        Character second = CharacterFactory.create(1102, 80);
        Battle battle = new Battle(new ArrayList<>(List.of(her, first, second)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.castImmediate(her.getSkills().get(SkillType.SKILL), her, List.of(first));
        battle.processRequests();
        System.out.println("[1412] E6 pen: first " + pen(first) + " ; second " + pen(second));
        Assertions.assertEquals(0.2, pen(first), 1e-9, "「持有【军功】的角色全属性抗性穿透提高 20%」");
        battle.castImmediate(her.getSkills().get(SkillType.SKILL), her, List.of(second));
        battle.processRequests();
        Assertions.assertEquals(0.0, pen(first), 1e-9, "the penetration moves with the merit");
        Assertions.assertEquals(0.2, pen(second), 1e-9, "and the new holder carries it");
    }

    @Test
    public void shePiercesMoreWhileAnAllyHoldsMerit() {
        Character her = CharacterFactory.create(1412, 80, true, null, null, 6);
        Character ally = CharacterFactory.create(1101, 80);
        Battle battle = new Battle(new ArrayList<>(List.of(her, ally)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.castImmediate(her.getSkills().get(SkillType.SKILL), her, List.of(ally));
        battle.processRequests();
        double before = pen(her);
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, her, her, 0, 0));
        double after = pen(her);
        System.out.println("[1412] E6 self pen " + before + " -> " + after);
        Assertions.assertEquals(0.2, after - before, 1e-9, "「场上存在持有【军功】的队友时…提高 20%」");
    }

    private static double pen(Character unit) {
        return unit.getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
    }
}