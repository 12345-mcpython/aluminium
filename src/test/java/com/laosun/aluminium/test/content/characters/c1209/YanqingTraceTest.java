package com.laosun.aluminium.test.content.characters.c1209;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1209's trace Frost Favors the Brave (凌霜): "while in the [智剑连心] effect, effect resistance is increased by 20%".
 */
public class YanqingTraceTest {
    private static final int WEARER = 1209;
    private static final int LEVEL = 80;

    @Test
    public void theTraceRaisesEffectResistanceForTheTurn() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.currentMove = new Signal(unit);
        battle.beforeMove();
        double before = unit.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        battle.fireTriggers(TriggerEvent.SKILL_CAST, unit, enemy, 0, 0);
        double gain = unit.getAttribute(AttributeType.EFFECT_RESISTANCE).get() - before;
        System.out.println("[1209] effectResGain=" + gain + " hasState="
                + unit.getBuffManager().hasState("智剑连心"));
        Assertions.assertEquals(0.2, gain, 1e-6, "the trace row's own param");
        Assertions.assertTrue(unit.getBuffManager().hasState("智剑连心"),
                "the skill's own rule put the state on her");
    }
}
