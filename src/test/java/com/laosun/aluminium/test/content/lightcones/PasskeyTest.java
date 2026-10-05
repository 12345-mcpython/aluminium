package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 20013: "使装备者施放战技后额外恢复#1]点能量，该效果单个回合内不可重复触发".
 *
 * <p>Two things the probe (round 129) established and this test therefore does: the event is fired INSIDE a driven turn (`currentMove` + `beforeMove`), because
 * a `per_turn` counter has no turn to count in otherwise, and it is fired WITH a target -- an effect that resolves a target throws
 * `Effect targets "target" but this event has no such party` when the event carries none.
 */
public class PasskeyTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void oneSkillPerTurnGrantsEnergy() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(20013, LEVEL, false, 1));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        double start = unit.getCurrentEnergy();
        battle.currentMove = new Signal(unit);
        battle.beforeMove();
        battle.fireTriggers(TriggerEvent.SKILL_CAST, unit, enemy, 0, 0);
        double afterFirst = unit.getCurrentEnergy();
        Assertions.assertEquals(8.0, afterFirst - start, 1e-6,
                "rank 1 grants eight energy: " + start + " -> " + afterFirst);
        battle.fireTriggers(TriggerEvent.SKILL_CAST, unit, enemy, 0, 0);
        Assertions.assertEquals(afterFirst, unit.getCurrentEnergy(), 1e-6,
                "per_turn: 1 -- a second skill in the same turn grants nothing");
        battle.afterMove();
        battle.currentMove = new Signal(unit);
        battle.beforeMove();
        battle.fireTriggers(TriggerEvent.SKILL_CAST, unit, enemy, 0, 0);
        Assertions.assertEquals(16.0, unit.getCurrentEnergy() - start, 1e-6,
                "the next turn grants again: " + unit.getCurrentEnergy());
    }
}
