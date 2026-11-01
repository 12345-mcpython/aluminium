package com.laosun.aluminium.test.content.characters.saber;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.List;

/**
 * 1014 Saber's technique 骑士王的登场: 「使用秘技后，下一次战斗开始时 Saber 攻击力提高 35%，持续 2 回合，并获得 2 点【炉心共鸣】」.
 *
 * <p>The technique is a BATTLE_START rule gated on {@code self has_state 秘技}, so the check compares a battle opened
 * normally with one opened by the technique.
 */
public class SaberTechniqueTest {

    private static final int SABER = 1014;
    private static final int LEVEL = 80;

    @Test
    public void theTechniqueRaisesAttackAndPaysTwoResonance() {
        double plainAttack = attack(false);
        double techniqueAttack = attack(true);
        System.out.println("[1014] technique: attack " + plainAttack + " -> " + techniqueAttack
                + " ; resonance " + resonance(false) + " -> " + resonance(true));
        Assertions.assertTrue(techniqueAttack > plainAttack, "「攻击力提高 35%」");
        Assertions.assertEquals(resonance(false) + 2, resonance(true), "「并获得 2 点【炉心共鸣】」");
    }

    private static Character saber(boolean technique) {
        Character saber = CharacterFactory.create(SABER, LEVEL);
        if (technique) {
            saber.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff("秘技", 1));
        }
        Battle battle = new Battle(new ArrayList<>(List.of(saber)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return saber;
    }

    private static double attack(boolean technique) {
        return saber(technique).getAttribute(AttributeType.ATTACK).get();
    }

    private static int resonance(boolean technique) {
        return saber(technique).getBuffManager().stacksOf("炉心共鸣");
    }
}