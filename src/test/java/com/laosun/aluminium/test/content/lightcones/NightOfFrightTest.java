package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 2301: when any ally casts an ultimate, the wearer heals the ally with the LOWEST HP percentage for 10% of
 * that ally's own maximum HP.
 */
public class NightOfFrightTest {
    private static final int CONE = 23017;
    private static final int WEARER = 1205;
    private static final int HURT_ALLY = 1002;
    private static final int HEALTHY_ALLY = 1204;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** The lowest-HP ally's gain when an ally casts an ultimate, with or without the cone in the party. */
    private double healGain(boolean withCone) {
        Character wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        Character hurt = CharacterFactory.create(HURT_ALLY, LEVEL);
        Character healthy = CharacterFactory.create(HEALTHY_ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, hurt, healthy), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.applyDamage(hurt, new com.laosun.aluminium.models.Damage(enemy, hurt,
                com.laosun.aluminium.enums.DamageElement.PHYSICAL,
                com.laosun.aluminium.enums.DamageType.NORMAL, hurt.getMaxHp() * 0.5));
        double before = hurt.getCurrentHp();
        battle.fireTriggers(TriggerEvent.ULT_CAST, wearer, enemy, 0, 0);
        return hurt.getCurrentHp() - before;
    }

    @Test
    public void theLowestAllyIsHealedForTenPercentOfItsOwnMaximum() {
        double withCone = healGain(true);
        double withoutCone = healGain(false);
        Character reference = CharacterFactory.create(HURT_ALLY, LEVEL);
        System.out.println("[23017] heal with cone=" + withCone + " without=" + withoutCone
                + " ; expected=" + (reference.getMaxHp() * 0.10));
        Assertions.assertEquals(reference.getMaxHp() * 0.10, withCone, reference.getMaxHp() * 0.01,
                "10% of the RECIPIENT's own maximum HP (scale: target_max_hp)");
        Assertions.assertEquals(0.0, withoutCone, 1e-9, "without the cone nothing is healed (false case)");
    }
}
