package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 110's 星魂 2: "施放终结技后攻击力提高#1%，持续2回合".
 *
 * <p>ATTACK is a FLAT attribute, so the share scales `baseValue()`, read after the battle starts (rounds 125 and 133).
 */
public class NumbyEidolonTest {
    private static final int WEARER = 1107;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theSecondEidolonRaisesAttackAfterTheUltimate() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.currentMove = new Signal(unit);
        battle.beforeMove();
        double before = unit.getAttribute(AttributeType.ATTACK).get();
        unit.getAttribute(AttributeType.ATTACK).baseValue();
        battle.fireTriggers(TriggerEvent.ULT_CAST, unit, enemy, 0, 0);
        double gain = unit.getAttribute(AttributeType.ATTACK).get() - before;
        double base = unit.getAttribute(AttributeType.ATTACK).baseValue();
        Assertions.assertEquals(0.3, gain / base, 1e-6,
                "share of the post-trigger base: base=" + base + " gain=" + gain);
    }
}
