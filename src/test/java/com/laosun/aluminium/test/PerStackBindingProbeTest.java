package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.RelicTriggerTables;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** Reads the COMPILED rule with a real context (the earlier attempt had no target, so nothing matched). */
public class PerStackBindingProbeTest {
    @Test
    public void printTheCompiledSpec() {
        Character unit = CharacterFactory.create(1001, 80);
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        enemy.getBuffManager().addBuff(new DotBuff(unit, DamageElement.FIRE, 100, 3));
        enemy.getBuffManager().addBuff(new DotBuff(unit, DamageElement.ICE, 100, 3));
        TriggerTable table = RelicTriggerTables.of(116).at(4);
        var rules = table.matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(unit, unit, enemy, 0, 0));
        System.out.println("[probe] enemyDots=" + enemy.getBuffManager().countBuffs(DotBuff.class)
                + " rules=" + rules.size());
        for (var rule : rules) {
            for (var effect : rule.effects()) {
                System.out.println("[probe] id=" + rule.id() + " op=" + effect.getOp()
                        + " attribute=" + effect.getAttribute() + " percent=" + effect.getPercent()
                        + " perStack=" + effect.getPerStack() + " instance=" + effect.getInstance());
            }
        }
    }
}
