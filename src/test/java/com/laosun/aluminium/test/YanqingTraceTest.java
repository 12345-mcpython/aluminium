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

/** 1209's trace 凌霜: 「处于【智剑连心】效果时，效果抵抗提高 20%」. */
public class YanqingTraceTest {
    private static final int WEARER = 1209;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theTraceRaisesEffectResistanceForTheTurn() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.currentMove = new Signal(unit);
        battle.beforeMove();
        double before = unit.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        battle.fireTriggers(TriggerEvent.SKILL_CAST, unit, enemy, 0, 0);
        double gain = unit.getAttribute(AttributeType.EFFECT_RESISTANCE).get() - before;
        System.out.println("[1209] effectResGain=" + gain + " hasState="
                + unit.getBuffManager().hasState("\u667a\u5251\u8fde\u5fc3"));
        Assertions.assertEquals(0.2, gain, 1e-6, "the trace row's own param");
        Assertions.assertTrue(unit.getBuffManager().hasState("\u667a\u5251\u8fde\u5fc3"),
                "the skill's own rule put the state on her");
    }
}
