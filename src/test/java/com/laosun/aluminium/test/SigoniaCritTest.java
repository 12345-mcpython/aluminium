package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Relic set 313 (planar, 2 pieces): crit rate from battle start, and crit DMG per kill capped at the document's own stack count.
 *
 * <p>The cap is measured by firing cap+2 times: the gain must be exactly cap x the per-stack value, which is the only assertion an ignored `max_stacks` fails.
 */
public class SigoniaCritTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int RELIC_LEVEL = 15;

    @Test
    public void theSetGivesCritRateAtBattleStart() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, null, RelicFactory.suit(313, 2, RELIC_LEVEL));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        double before = unit.getAttribute(AttributeType.CRIT_CHANCE).get();
        battle.startBattle();
        Assertions.assertEquals(0.04, unit.getAttribute(AttributeType.CRIT_CHANCE).get() - before, 1e-6,
                "crit rate from battle start");
    }

    @Test
    public void critDamageStacksOnlyUpToTheDocumentedCap() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, null, RelicFactory.suit(313, 2, RELIC_LEVEL));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.currentMove = new Signal(unit);
        battle.beforeMove();
        double before = unit.getAttribute(AttributeType.CRIT_ATTACK).get();
        int firings = 10 + 2;
        for (int i = 0; i < firings; i++) {
            battle.fireTriggers(TriggerEvent.KILL, unit, enemy, 0, 0);
        }
        double gain = unit.getAttribute(AttributeType.CRIT_ATTACK).get() - before;
        Assertions.assertEquals(0.04 * 10, gain, 1e-6,
                "of " + firings + " kills only 10 may stack: " + gain);
    }
}
