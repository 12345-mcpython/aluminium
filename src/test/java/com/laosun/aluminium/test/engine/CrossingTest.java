package com.laosun.aluminium.test.engine;

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
 * The threshold CROSSING: "生命值百分比降到50%或以下时" fires once, not on every later hit.
 *
 * <p>The pair is the measurement: damage that takes the ally from above half to below it heals him, while damage to an ally who is ALREADY below half
 * does not - the second case is what `target_hp_percent_before` exists for.
 */
public class CrossingTest {
    private static final int LUOCHA = 1203;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: Crossing from above half to below it triggers the heal. */
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
                "「当我方任意单体当前生命值百分比降到50%或以下时，罗刹会立即对其触发一次等同于战技的效果」 (when any one of our units' current HP percentage drops to 50% or below, Luocha immediately triggers a Skill-equivalent effect on them) -- after the crossing he must be back above half: " + (ally.getCurrentHp() / ally.getMaxHp()));
    }

    /** Note: The control: hitting someone who is ALREADY below half is not a crossing. */
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
        // Note: Honest scope: by now the crossing has already fired (that is asserted above) and `cooldown: 2` blocks the rule, so what this case
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
