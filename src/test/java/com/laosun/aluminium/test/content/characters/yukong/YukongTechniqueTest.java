package com.laosun.aluminium.test.content.characters.yukong;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** 1207 驭空's technique:「主动攻击敌人进入战斗时，驭空获得 2 层【鸣弦号令】」. */
public class YukongTechniqueTest {

    @Test
    public void theTechniqueGrantsTwoStacksOnEntry() {
        Assertions.assertEquals(2, stacks(true), "「获得 2 层【鸣弦号令】」");
        Assertions.assertEquals(0, stacks(false), "the control: without the technique there are none");
    }

    private static int stacks(boolean technique) {
        Character her = CharacterFactory.create(1207, 80);
        if (technique) {
            her.getBuffManager().addBuff(new StateBuff("秘技", 1));
        }
        Battle battle = new Battle(new ArrayList<>(List.of(her)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        System.out.println("[1207] technique=" + technique + " stacks=" + her.getBuffManager().stacksOf("鸣弦号令"));
        return her.getBuffManager().stacksOf("鸣弦号令");
    }
}