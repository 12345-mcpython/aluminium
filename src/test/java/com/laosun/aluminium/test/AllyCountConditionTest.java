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
 * The `ally_count` condition variable: "我方目标数量", the mirror of `enemy_count` and the blocker set 321 names.
 *
 * <p>Both directions through a synthetic rule, on TURN_START: round 6measured that BATTLE_START carries no battlefield, where a
 * count reads NaN and a negative case would pass without the condition ever being evaluated.
 */
public class AllyCountConditionTest {
    private static final int WEARER = 1001;
    private static final int TEAMMATE = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    /** Note: Measured: a battle opens with 3 skill points. */
    private static final int OPENING = 3;

    @Test
    public void fourAlliesSatisfyTheThreshold() {
        Assertions.assertEquals(OPENING, points(4, false), "precondition: no rule, ordinary opening");
        Assertions.assertEquals(OPENING + 1, points(4, true), "with four allies on the field the rule fires");
    }

    @Test
    public void twoAlliesDoNot() {
        Assertions.assertEquals(OPENING, points(2, false));
        Assertions.assertEquals(OPENING, points(2, true), "with two the same rule must not fire");
    }

    /** Skill points after the wearer's turn starts, with the given number of allies, with or without the rule. */
    private static int points(int allies, boolean withRule) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL);
        if (withRule) {
            EffectSpec grant = new EffectSpec();
            TriggerSpecs.set(grant, "op", "GAIN_SKILL_POINT");
            TriggerSpecs.set(grant, "amount", 1.0);
            TriggerSpecs.set(grant, "target", "self");
            wearer.setTriggerTable(new TriggerTable(9801, List.of(TriggerSpecs.rule(
                    TriggerEvent.TURN_START.name(), List.of("ally_count >= 4"), grant))));
        }
        List<Character> team = new ArrayList<>();
        team.add(wearer);
        for (int i = 1; i < allies; i++) {
            team.add(CharacterFactory.create(TEAMMATE, LEVEL));
        }
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(team, List.of(enemy), new Random(0));
        battle.startBattle();
        battle.currentMove = new Signal(wearer);
        battle.beforeMove();
        battle.afterMove();
        return battle.getSkillPoints();
    }
}
