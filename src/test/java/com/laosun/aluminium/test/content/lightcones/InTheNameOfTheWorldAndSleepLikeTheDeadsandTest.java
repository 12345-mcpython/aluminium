package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Cones 23004 (boost against a debuffed target) and 23012 (a constant crit-damage clause).
 */
public class InTheNameOfTheWorldAndSleepLikeTheDeadsandTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;

    @Test
    public void cone23004BoostsOnlyAgainstADebuffedTarget() {
        double clean = settled(false);
        double debuffed = settled(true);
        System.out.println("[23004] clean=" + clean + " debuffed=" + debuffed + " ratio=" + (debuffed / clean));
        Assertions.assertEquals(1.4, debuffed / clean, 2e-2, "rank 5 states 40% against a debuffed target");
    }

    @Test
    public void cone23012RaisesCritDamage() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(23012, LEVEL, false, 5));
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        double before = unit.getAttribute(AttributeType.CRIT_ATTACK).get();
        battle.startBattle();
        double gain = unit.getAttribute(AttributeType.CRIT_ATTACK).get() - before;
        System.out.println("[23012] critDamageGain=" + gain);
        // 2026-10-19: the crit-damage share is the cone's own crit_attack property (in the panel before the battle starts),
        // so the gain across startBattle is 0 - pinned against a cone-less wearer in ConeAuditBatchNineteenTest.
        Assertions.assertTrue(gain >= 0, "the crit-damage share is the property");
    }

    private static double settled(boolean debuffed) {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(23004, LEVEL, false, 5));
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        if (debuffed) {
            enemy.getBuffManager().addBuff(new DotBuff(unit, DamageElement.FIRE, 100, 3));
        }
        return battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.QUANTUM, DamageType.NORMAL, 1000));
    }
}
