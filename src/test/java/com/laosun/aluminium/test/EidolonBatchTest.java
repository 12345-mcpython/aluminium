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

/** Eidolon clauses 「施放X后，<attr>提高N%，持续M回合」, values verified against `eidolons.json`, expectations split by attribute kind. */
public class EidolonBatchTest {
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void character1214Eidolon4GivesBREAKING_EFFECT() {
        Assertions.assertEquals(0.4, gain(1214, TriggerEvent.ULT_CAST, AttributeType.BREAKING_EFFECT, false), 1e-6, "the data's own value");
    }

    @Test
    public void character1405Eidolon4GivesATTACK() {
        Assertions.assertEquals(0.3, gain(1405, TriggerEvent.SKILL_CAST, AttributeType.ATTACK, true), 1e-6, "the data's own value");
    }

    @Test
    public void character1013Eidolon6GivesATTACK() {
        Assertions.assertEquals(0.25, gain(1013, TriggerEvent.ULT_CAST, AttributeType.ATTACK, true), 1e-6, "the data's own value");
    }

    private static double gain(int cid, TriggerEvent event, AttributeType attribute, boolean flat) {
        Character unit = CharacterFactory.create(cid, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.currentMove = new Signal(unit);
        battle.beforeMove();
        double before = unit.getAttribute(attribute).get();
        battle.fireTriggers(event, unit, enemy, 0, 0);
        double gain = unit.getAttribute(attribute).get() - before;
        return flat ? gain / unit.getAttribute(attribute).baseValue() : gain;
    }
}
