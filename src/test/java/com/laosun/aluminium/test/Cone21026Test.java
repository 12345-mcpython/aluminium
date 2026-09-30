package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 21026: 「攻击力提高#1%，对处于灼烧或裂伤状态的敌方目标造成的伤害提高#2%」 at rank 5.
 *
 * <p>ATTACK is flat, so its share scales the post-start base; the disjunction is two rules, the second excluding the first state.
 */
public class Cone21026Test {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theConeRaisesAttackAndBoostsEitherStateOnce() {
        double clean = settled(0);
        double burning = settled(1);
        double wounded = settled(2);
        double both = settled(3);
        double attackGain = attackRatio();
        System.out.println("[21026] clean=" + clean + " burning=" + burning + " wounded=" + wounded + " both=" + both
                + " attackRatio=" + attackGain);
        Assertions.assertEquals(0.2, attackGain, 1e-6, "rank 5 raises attack by 20% of base");
        Assertions.assertEquals(1.32, burning / clean, 2e-2, "灼烧");
        Assertions.assertEquals(1.32, wounded / clean, 2e-2, "裂伤");
        Assertions.assertEquals(1.32, both / clean, 2e-2, "both states must not double the boost");
    }

    private static double attackRatio() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21026, LEVEL, false, 5));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        double before = unit.getAttribute(AttributeType.ATTACK).get();
        battle.startBattle();
        double gain = unit.getAttribute(AttributeType.ATTACK).get() - before;
        return gain / unit.getAttribute(AttributeType.ATTACK).baseValue();
    }

    /** 0 = clean, 1 = 灼烧, 2 = 裂伤, 3 = both. */
    private static double settled(int which) {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21026, LEVEL, false, 5));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        if (which == 1 || which == 3) {
            enemy.getBuffManager().addBuff(new DotBuff(unit, DamageElement.FIRE, 100, 3));
        }
        if (which == 2 || which == 3) {
            enemy.getBuffManager().addBuff(new DotBuff(unit, DamageElement.PHYSICAL, 100, 3));
        }
        return battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.QUANTUM, DamageType.NORMAL, 1000));
    }
}
