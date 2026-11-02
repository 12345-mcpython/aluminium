package com.laosun.aluminium.test.content.characters.yaoguang;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1502 爻光's zone:「结界持续期间，我方全体目标欢愉度提高，提高数值等同于爻光欢愉度的 20%」.
 *
 * <p>The share is HER OWN elation ({@code self_attr:ELATION_DAMAGE_BOOST}), so the judge gives her a known amount first and
 * then checks the party reads a fifth of it.
 */
public class YaoguangZoneElationTest {

    @Test
    public void theZoneRaisesThePartysElationByAFifthOfHers() {
        Character her = CharacterFactory.create(1502, 80);
        Character ally = CharacterFactory.create(1101, 80);
        Battle battle = new Battle(new ArrayList<>(List.of(her, ally)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        // A Character attribute is a DoubleValue with modifiers; a fixture writes the BASE and recomputes.
        her.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).base(0.5);
        her.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        double allyBefore = ally.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        double herElation = her.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        battle.castImmediate(her.getSkills().get(SkillType.SKILL), her, List.of(ally));
        battle.processRequests();
        double gained = ally.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get() - allyBefore;
        System.out.println("[1502] her elation=" + herElation + " ; ally gained=" + gained + " (expect " + (herElation * 0.2) + ")");
        Assertions.assertEquals(herElation * 0.2, gained, 1e-9, "「等同于爻光欢愉度的 20%」");
    }
}