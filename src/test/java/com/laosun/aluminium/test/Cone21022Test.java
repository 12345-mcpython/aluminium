package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
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
 * Light cone 21022: "对处于触电或风化状态的敌方目标造成的伤害提高#2%" at rank 5.
 *
 * <p>The disjunction is two rules, the second requiring the ABSENCE of the first state, so a target carrying both is boosted once.
 */
public class Cone21022Test {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theConeBoostsAgainstEitherStateButNotTwice() {
        double clean = settled(0);
        double shocked = settled(1);
        double winded = settled(2);
        double both = settled(3);
        System.out.println("[21022] clean=" + clean + " shocked=" + shocked + " winded=" + winded + " both=" + both);
        Assertions.assertEquals(1.32, shocked / clean, 2e-2, "触电 (rank 5 says #2 = 0.32)");
        Assertions.assertEquals(1.32, winded / clean, 2e-2, "风化");
        Assertions.assertEquals(1.32, both / clean, 2e-2, "both states must not double the boost");
    }

    /** 0 = clean, 1 = 触电, 2 = 风化, 3 = both. */
    private static double settled(int which) {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21022, LEVEL, false, 5));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        if (which == 1 || which == 3) {
            enemy.getBuffManager().addBuff(new DotBuff(unit, DamageElement.THUNDER, 100, 3));
        }
        if (which == 2 || which == 3) {
            enemy.getBuffManager().addBuff(new DotBuff(unit, DamageElement.WIND, 100, 3));
        }
        return battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.QUANTUM, DamageType.NORMAL, 1000));
    }
}
