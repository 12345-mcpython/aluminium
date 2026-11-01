package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.buff.StackBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * A ZONE whose size follows a counter: "每层【X】使敌人受到的伤害提高 5%" via {@code per_stack_live}.
 */
public class LiveZoneShareTest {

    private static final String COUNTER = "标记";

    @Test
    public void theZoneGrowsWithTheCounter() {
        Character target = CharacterFactory.create(1001, 80);
        target.setTriggerTable(new TriggerTable(1001,
                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), perLayerZone(0.05)))));
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(target, CharacterFactory.create(1002, 80))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.99));
        battle.startBattle();

        double bare = hit(battle, enemy, target);
        target.getBuffManager().addBuff(new StackBuff(COUNTER, 1, true, 99));
        double one = hit(battle, enemy, target);
        target.getBuffManager().addBuff(new StackBuff(COUNTER, 1, true, 99));
        double two = hit(battle, enemy, target);

        System.out.println("[zone] bare=" + bare + " one=" + one + " two=" + two);
        Assertions.assertEquals(1.05, one / bare, 1e-6, "one layer: +5% taken");
        Assertions.assertEquals(1.10, two / bare, 1e-6, "two layers: +10% taken - the zone followed the counter");
    }

    private static EffectSpec perLayerZone(double percent) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_DAMAGE_TAKEN");
        TriggerSpecs.set(effect, "percent", percent);
        TriggerSpecs.set(effect, "perStack", COUNTER);
        TriggerSpecs.set(effect, "perStackLive", true);
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpecs.set(effect, "target", "self");
        return effect;
    }

    private static double hit(Battle battle, Enemy enemy, Character target) {
        return battle.applyDamage(target, new Damage(enemy, target, DamageElement.PHYSICAL, DamageType.NORMAL, 100));
    }
}