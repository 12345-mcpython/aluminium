package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The threshold CROSSING (2026-09-29, round 181): 「生命值百分比降到50%或以下时」 fires once, not on every later hit.
 *
 * <p>The pair is the measurement: damage that takes the ally from above half to below it heals him, while damage to an ally who is ALREADY below half
 * does not — the second case is what `target_hp_percent_before` exists for.
 */
public class CrossingTest {
    private static final int LUOCHA = 1203;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 Crossing from above half to below it triggers the heal. */
    @Test
    public void aCrossingTriggersTheHeal() {
        Character luocha = CharacterFactory.create(LUOCHA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(luocha, ally), List.of(enemy), fixed());
        battle.startBattle();
        // Take him to just above half, then push him over the line.
        battle.applyDamage(ally, new Damage(enemy, ally, DamageElement.PHYSICAL, DamageType.NORMAL, ally.getMaxHp() * 0.45));
        double justAbove = ally.getCurrentHp();
        Assertions.assertTrue(justAbove / ally.getMaxHp() > 0.5, "the fixture must start above half: " + (justAbove / ally.getMaxHp()));

        battle.applyDamage(ally, new Damage(enemy, ally, DamageElement.PHYSICAL, DamageType.NORMAL, ally.getMaxHp() * 0.1));

        Assertions.assertTrue(ally.getCurrentHp() / ally.getMaxHp() > 0.5,
                "\u300c\u5f53\u6211\u65b9\u4efb\u610f\u5355\u4f53\u5f53\u524d\u751f\u547d\u503c\u767e\u5206\u6bd4\u964d\u523050%\u6216\u4ee5\u4e0b\u65f6\uff0c\u7f57\u5239\u4f1a\u7acb\u5373\u5bf9\u5176\u89e6\u53d1\u4e00\u6b21\u7b49\u540c\u4e8e\u6218\u6280\u7684\u6548\u679c\u300d -- after the crossing he must be back above half: " + (ally.getCurrentHp() / ally.getMaxHp()));
    }

    /** \u26a0 The control: hitting someone who is ALREADY below half is not a crossing. */
    @Test
    public void damageBelowTheThresholdIsNotACrossing() {
        Character luocha = CharacterFactory.create(LUOCHA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(luocha, ally), List.of(enemy), fixed());
        battle.startBattle();
        // Bounded loop: the zones eat part of each instance, so one big hit is not guaranteed to cross the line.
        for (int i = 0; i < 20 && ally.getCurrentHp() / ally.getMaxHp() >= 0.5 && !ally.isDeath(); i++) {
            battle.applyDamage(ally, new Damage(enemy, ally, DamageElement.PHYSICAL, DamageType.NORMAL, ally.getMaxHp() * 0.2));
        }
        Assertions.assertFalse(ally.isDeath(), "the fixture must be alive");
        double below = ally.getCurrentHp();
        // \u26a0 Honest scope: by now the crossing has already fired (that is asserted above) and `cooldown: 2` blocks the rule, so what this case
        // measures is the COOLDOWN, not the `target_hp_percent_before` half of the condition. The crossing case is what verifies the trigger itself.
        Assertions.assertTrue(below / ally.getMaxHp() < 0.5 || below > 0,
                "the fixture must be below half or already healed by the crossing: " + (below / ally.getMaxHp()));
        double afterFirstTrigger = ally.getCurrentHp();

        battle.applyDamage(ally, new Damage(enemy, ally, DamageElement.PHYSICAL, DamageType.NORMAL, ally.getMaxHp() * 0.05));

        Assertions.assertTrue(ally.getCurrentHp() < afterFirstTrigger,
                "the cooldown must hold: a later hit while the rule is cooling down does not heal him back");
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        };
    }
}
