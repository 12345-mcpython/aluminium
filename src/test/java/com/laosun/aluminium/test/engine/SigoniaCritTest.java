package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Relic set 313 (planar, 2 pieces): crit rate from battle start, and crit DMG per kill capped at the document's own stack count.
 *
 * <p>The cap is measured by firing cap+2 times: the gain must be exactly cap x the per-stack value, which is the only assertion an ignored `max_stacks` fails.
 */
public class SigoniaCritTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int RELIC_LEVEL = 15;

    @Test
    public void theSetGivesCritRateAtBattleStart() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, null, RelicFactory.suit(313, 2, RELIC_LEVEL));
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        // 2026-10-19: the crit rate is the two-piece properties value, so it is already in the panel before the battle
        // starts (the shipped rule that added it again was a duplicate and is gone).
        battle.startBattle();
        double plain = CharacterFactory.create(WEARER, LEVEL, true).getAttribute(AttributeType.CRIT_CHANCE).get();
        System.out.println("[313] crit=" + unit.getAttribute(AttributeType.CRIT_CHANCE).get() + " plain=" + plain);
        Assertions.assertEquals(0.04 + plain, unit.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-6,
                "crit rate comes from the two-piece properties");
    }

    @Test
    public void critDamageStacksOnlyUpToTheDocumentedCap() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, null, RelicFactory.suit(313, 2, RELIC_LEVEL));
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
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
