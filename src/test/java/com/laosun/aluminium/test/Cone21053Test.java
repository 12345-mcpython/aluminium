package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 21053: 「使装备者提供的护盾量提高 12%，我方目标持有护盾时，造成的伤害提高 12%」.
 *
 * <p>Unlike 22003/23009/23015 this cone has NO ability_property row, so the constant half is ours too. The conditional half is
 * an aura that needs no new vocabulary: `actor is_ally` plus the existing `actor has_shield`.
 */
public class Cone21053Test {
    private static final int CONE = 21053;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character ally(boolean withCone) {
        return withCone
                ? CharacterFactory.create(ALLY, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(ALLY, LEVEL);
    }

    private double hit(Character unit, boolean shielded) {
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        if (shielded) {
            battle.grantShield(unit, unit, 500);
        }
        return battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.QUANTUM, DamageType.NORMAL, 1000));
    }

    @Test
    public void bothHalvesAreWrittenAndTheConstantIsOurs() {
        Character unit = ally(true);
        int constants = 0;
        for (var rule : unit.getTriggerTable().matching(TriggerEvent.BATTLE_START,
                new TriggerTable.TriggerContext(unit, unit, unit, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("SHIELD_BOOST".equals(effect.getAttribute())) {
                    constants++;
                    System.out.println("[21053] shield boost rule percent=" + effect.getPercent());
                    Assertions.assertEquals(0.12, effect.getPercent(), 1e-9, "rank 1 states 12%");
                }
            }
        }
        Assertions.assertEquals(1, constants, "this cone has no ability_property row, so we write the constant");
        // \u26a0 BATTLE_START must actually FIRE before the attribute moves: reading the trigger table alone proves nothing
        // (measured: boostedShield returned exactly 1000 until a battle was started).
        Character bare = ally(false);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit, bare), List.of(enemy), new Random(0));
        battle.startBattle();
        double boostWith = Battle.boostedShield(unit, 1000);
        double boostWithout = Battle.boostedShield(bare, 1000);
        System.out.println("[21053] boostedShield with cone=" + boostWith + " without=" + boostWithout);
        Assertions.assertEquals(1000 * 0.12, boostWith - 1000, 1e-6, "the shield really is 12% bigger");
        Assertions.assertEquals(1000, boostWithout, 1e-9, "and nothing without the cone");
    }

    @Test
    public void theShieldGatedHalfNeedsBothTheConeAndAShield() {
        double shielded = hit(ally(true), true);
        double bare = hit(ally(true), false);
        double control = hit(ally(false), true);
        System.out.println("[21053] shielded=" + shielded + " bare=" + bare + " control(no cone)=" + control);
        Assertions.assertTrue(shielded > bare, "with the cone, holding a shield raises the damage dealt");
        Assertions.assertEquals(bare, control, 1e-6, "without the cone a shield changes nothing");
    }
}
