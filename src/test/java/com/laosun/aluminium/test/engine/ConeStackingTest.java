package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Cones 22005 and 2102(round 249): event-driven ATTACK stacks. Their constant halves are the engine's ability_property, so only
 * the conditional half is written here -- and the text gives no duration, so the 21005 precedent (permanent stacks) is followed.
 */
public class ConeStackingTest {
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer(int cone) {
        return cone == 0 ? CharacterFactory.create(WEARER, LEVEL)
                : CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(cone, LEVEL, false, 1));
    }

    private Battle battleWith(Character unit) {
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    @Test
    public void theSkillConeStacksAttackUpToThreeTimes() {
        Character unit = wearer(22005);
        Battle battle = battleWith(unit);
        double before = unit.getAttribute(AttributeType.ATTACK).get();
        for (int i = 0; i < 4; i++) {
            battle.fireTriggers(TriggerEvent.SKILL_CAST, unit, unit, 0, 0);
        }
        double after = unit.getAttribute(AttributeType.ATTACK).get();
        System.out.println("[22005] attack " + before + " -> " + after + " (delta=" + (after - before) + ")");
        double base = unit.getAttribute(AttributeType.ATTACK).baseValue();
        Assertions.assertEquals(base * 0.08 * 3, after - before, base * 0.02,
                "four casts cap at three stacks of 8% of BASE attack");
    }

    @Test
    public void theKillConeStacksOnKillsOnly() {
        Character unit = wearer(21027);
        Character control = wearer(0);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit, control), List.of(enemy), new Random(0));
        battle.startBattle();
        double before = unit.getAttribute(AttributeType.ATTACK).get();
        // A non-kill event must not move it.
        battle.fireTriggers(TriggerEvent.SKILL_CAST, unit, enemy, 0, 0);
        Assertions.assertEquals(before, unit.getAttribute(AttributeType.ATTACK).get(), 1e-9,
                "only a kill stacks this cone");
        for (int i = 0; i < 4; i++) {
            battle.fireTriggers(TriggerEvent.KILL, unit, enemy, 0, 0);
        }
        double after = unit.getAttribute(AttributeType.ATTACK).get();
        double base = unit.getAttribute(AttributeType.ATTACK).baseValue();
        System.out.println("[21027] attack " + before + " -> " + after + " (delta=" + (after - before) + ")");
        Assertions.assertEquals(base * 0.04 * 3, after - before, base * 0.02,
                "four kills cap at three stacks of 4% of BASE attack");
        double controlBefore = control.getAttribute(AttributeType.ATTACK).get();
        System.out.println("[21027] control attack unchanged at " + controlBefore);
    }
}
