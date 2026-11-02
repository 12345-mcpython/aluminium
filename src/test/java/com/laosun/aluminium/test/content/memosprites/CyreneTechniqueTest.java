package com.laosun.aluminium.test.content.memosprites;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** 1415 昔涟's technique:「持续时间内进入战斗后，展开战技的结界」. */
public class CyreneTechniqueTest {

    @Test
    public void theTechniqueDeploysTheSkillZone() {
        Assertions.assertTrue(zone(true), "「展开战技的结界」");
        Assertions.assertFalse(zone(false), "the control: without the technique there is no zone");
    }

    private static boolean zone(boolean technique) {
        Character her = CharacterFactory.create(1415, 80);
        if (technique) {
            her.getBuffManager().addBuff(new StateBuff("秘技", 1));
        }
        Battle battle = new Battle(new ArrayList<>(List.of(her)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        System.out.println("[1415] technique=" + technique + " zone=" + her.getBuffManager().hasState("结界"));
        return her.getBuffManager().hasState("结界");
    }
}