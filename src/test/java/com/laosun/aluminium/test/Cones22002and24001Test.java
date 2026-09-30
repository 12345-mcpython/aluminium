package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Cones 22002 (attack, then post-ultimate damage) and 24001 (crit, an INSTANCE crit against a low-HP target, post-kill attack).
 *
 * <p>24001 is also a composition test: `instance: true` writes the extra crit into the damage being settled, so the constant crit clause and the conditional one never
 * replace each other.
 */
public class Cones22002and24001Test {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void cone22002RaisesAttackAndThenDamageAfterTheUlt() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(22002, LEVEL, false, 5));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        double boostBefore = unit.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        battle.fireTriggers(TriggerEvent.ULT_CAST, unit, enemy, 0, 0);
        double boost = unit.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - boostBefore;
        System.out.println("[22002] damageBoostAfterUlt=" + boost);
        Assertions.assertEquals(0.3, boost, 1e-6, "rank 5 states 30% after the ultimate");
    }

    @Test
    public void cone24001PutsTheLowHpCritOnTheDamageItself() {
        double full = instanceCrit(0.0);
        double low = instanceCrit(0.5);
        System.out.println("[24001] instanceCritAtFullHp=" + full + " instanceCritAtLowHp=" + low);
        Assertions.assertEquals(0.0, full, 1e-9, "a full-HP target gets nothing extra");
        Assertions.assertEquals(0.16, low, 1e-6, "rank 5 states 16% against a target at or below 50% HP");
    }

    /** The instance-scoped crit chance written into the damage, with the enemy optionally brought low. */
    private static double instanceCrit(double fraction) {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(24001, LEVEL, false, 5));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        if (fraction > 0) {
            int guard = 0;
            while (enemy.getCurrentHp() / enemy.getMaxHp() > fraction && guard++ < 80) {
                battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.QUANTUM, DamageType.NORMAL,
                        enemy.getMaxHp() * 0.15));
            }
        }
        Damage configured = new Damage(unit, enemy, DamageElement.QUANTUM, DamageType.NORMAL, 1000);
        battle.applyDamage(enemy, configured);
        System.out.println("    enemyHp=" + (enemy.getCurrentHp() / enemy.getMaxHp()) + " fraction=" + fraction);
        return configured.getExtraCritChance();
    }
}
