package com.laosun.aluminium.test.content.characters.cerydra;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1412 刻律德菈's technique:「使用秘技后获得【军功】」- the technique state at battle start hands her the merit.
 *
 * <p>The mapping-side halves (transfer on switching, the free Skill cast) are registered as gaps, not tested here.
 */
public class CerydraTechniqueTest {

    @Test
    public void theTechniqueGrantsMeritOnEntry() {
        Assertions.assertTrue(merit(true), "「使用秘技后获得【军功】」");
        Assertions.assertFalse(merit(false), "the control: without the technique there is no merit");
    }

    private static boolean merit(boolean technique) {
        Character her = CharacterFactory.create(1412, 80);
        if (technique) {
            her.getBuffManager().addBuff(new StateBuff("秘技", 1));
        }
        Battle battle = new Battle(new ArrayList<>(List.of(her)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        System.out.println("[1412] technique=" + technique + " merit=" + her.getBuffManager().hasState("军功"));
        return her.getBuffManager().hasState("军功");
    }
}