package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1309 Robin (知更鸟): "after entering battle while the field is expanded, Robin restores 5 energy at the start of each wave".
 *
 * <p>Two-way on purpose: with the technique's field up she gains five at each wave's start, and without it she gains nothing. The
 * gate is the repo's own convention for "technique" (`self has_state 秘技`, the same one 1408's technique rule uses), and it holds because
 * `applyTechniqueStates()` runs before any rule.
 */
public class RobinWaveEnergyTest {
    private static final int ROBIN = 1309;
    private static final int MONSTER = 1002011;
    private static final double EPS = 1e-6;

    /** Five with the field, nothing without it. */
    @Test
    public void theFieldPaysFiveAtEachWave() {
        double withField = gainAtWaveStart(true);
        double withoutField = gainAtWaveStart(false);
        System.out.println("[robin-wave] with field=" + withField + " without=" + withoutField);

        Assertions.assertEquals(5.0, withField, EPS,
                "「每个波次开始时知更鸟恢复 5 点能量」-- and the field is what makes it hers");
        Assertions.assertEquals(0.0, withoutField, EPS,
                "「领域展开期间进入战斗后」-- no field, no energy: this half is what makes the reading about the clause");
    }

    /** Her energy gain when a wave starts, with or without the technique's field. */
    private static double gainAtWaveStart(boolean technique) {
        Character robin = CharacterFactory.create(ROBIN, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(robin),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        if (technique) {
            battle.markTechniqueUsed(robin);
        }
        battle.startBattle();
        battle.processRequests();
        double before = robin.getCurrentEnergy();
        battle.fireTriggers(TriggerEvent.WAVE_START);
        battle.processRequests();
        return robin.getCurrentEnergy() - before;
    }
}
