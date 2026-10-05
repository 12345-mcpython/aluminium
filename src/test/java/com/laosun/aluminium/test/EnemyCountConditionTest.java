package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The `enemy_count` condition variable: "the number of enemy targets on the field", whose reader is 1413 长夜月's talent.
 *
 * <p>Note: The event matters: `enemyCount` returns NaN when there is no battlefield in context (the rule this whole vocabulary
 * follows -- "cannot read it" must fail, never read as zero), and BATTLE_START can be exactly that. Measured: on BATTLE_START the
 * four-enemy case did NOT fire, and the one-enemy case "passed" for the same reason -- a condition that never evaluates also
 * passes a negative case. So the rule hangs on TURN_START and the fixture drives a turn.
 *
 * <p>Both directions are therefore real: four enemies must grant the skill point, one must not.
 */
public class EnemyCountConditionTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    /** Note: Measured: a battle opens with 3 skill points. */
    private static final int OPENING = 3;

    @Test
    public void fourEnemiesSatisfyTheThreshold() {
        Assertions.assertEquals(OPENING, points(4, false), "precondition: no rule, ordinary opening");
        Assertions.assertEquals(OPENING + 1, points(4, true), "with four enemies the rule fires");
    }

    @Test
    public void oneEnemyDoesNot() {
        Assertions.assertEquals(OPENING, points(1, false));
        Assertions.assertEquals(OPENING, points(1, true), "with one enemy it must not fire");
    }

    /** Skill points after the wearer's turn starts, with four or one enemy, with or without the rule. */
    private static int points(int enemies, boolean withRule) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL);
        if (withRule) {
            EffectSpec grant = new EffectSpec();
            TriggerSpecs.set(grant, "op", "GAIN_SKILL_POINT");
            TriggerSpecs.set(grant, "amount", 1.0);
            TriggerSpecs.set(grant, "target", "self");
            wearer.setTriggerTable(new TriggerTable(9501, List.of(TriggerSpecs.rule(
                    TriggerEvent.TURN_START.name(), List.of("enemy_count >= 4"), grant))));
        }
        List<Enemy> foes = new ArrayList<>();
        for (int i = 0; i < enemies; i++) {
            foes.add(EnemyFactory.create(MONSTER, 90, 1));
        }
        Battle battle = new Battle(List.of(wearer), foes, new Random(0));
        battle.startBattle();
        battle.currentMove = new Signal(wearer);
        battle.beforeMove();
        battle.afterMove();
        return battle.getSkillPoints();
    }
}
