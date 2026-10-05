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
 * Light cone 21006: a flat follow-up boost (+24%) plus an extra +24% against a target at or below half HP.
 *
 * <p>Rank 1 states 0.24 / 0.5 / 0.24, so a follow-up against a healthy target is 1.24x a cone-less one, against a low
 * target 1.48x. Note: The extra rule MUST be scoped to ADDITIONAL damage: on DEALING_DAMAGE it would otherwise raise the
 * wearer's ORDINARY damage whenever the target is low, which the text never says. That is the third reading below.
 */
public class Cone21006Test {
    private static final int CONE = 21006;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** One deterministic additional-damage instance against a FRESH enemy per reading (discipline 149). */
    private double followUp(boolean withCone, double enemyHpShare) {
        Character unit = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        while (enemy.getCurrentHp() / enemy.getMaxHp() > enemyHpShare) {
            battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.PHYSICAL, DamageType.NORMAL,
                    enemy.getMaxHp() * 0.1));
        }
        return battle.applyAdditionalDamage(unit, enemy, DamageElement.FIRE, 1000, 0.0, 1.5);
    }

    /** The same low-HP enemy, but hit with an ORDINARY instance: the extra half must not touch it. */
    private double ordinaryHit(boolean withCone, double enemyHpShare) {
        Character unit = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        while (enemy.getCurrentHp() / enemy.getMaxHp() > enemyHpShare) {
            battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.PHYSICAL, DamageType.NORMAL,
                    enemy.getMaxHp() * 0.1));
        }
        return battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.PHYSICAL, DamageType.NORMAL, 1000));
    }

    @Test
    public void theSpecCarriesTheUnconditionalHalf() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1));
        int constant = 0;
        for (var rule : unit.getTriggerTable().matching(TriggerEvent.BATTLE_START,
                new TriggerTable.TriggerContext(unit, unit, unit, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("FOLLOW_UP_DAMAGE_BOOST".equals(effect.getAttribute())) {
                    constant++;
                    System.out.println("[21006] spec constant percent=" + effect.getPercent());
                    Assertions.assertEquals(0.24, effect.getPercent(), 1e-9, "rank 1 states 24%");
                }
            }
        }
        Assertions.assertEquals(1, constant, "this cone has no ability_property row, so we write the constant");
    }

    @Test
    public void theExtraHalfAppliesOnlyToFollowUpsAgainstALowTarget() {
        double healthy = followUp(true, 0.9);
        double healthyNoCone = followUp(false, 0.9);
        double low = followUp(true, 0.4);
        double lowNoCone = followUp(false, 0.4);
        double lowOrdinary = ordinaryHit(true, 0.4);
        double lowOrdinaryNoCone = ordinaryHit(false, 0.4);
        System.out.println("[21006] follow-up healthy " + healthy + " vs no cone " + healthyNoCone
                + " ratio=" + (healthy / healthyNoCone));
        System.out.println("[21006] follow-up low " + low + " vs no cone " + lowNoCone
                + " ratio=" + (low / lowNoCone));
        System.out.println("[21006] ORDINARY at low hp " + lowOrdinary + " vs no cone " + lowOrdinaryNoCone
                + " ratio=" + (lowOrdinary / lowOrdinaryNoCone));
        Assertions.assertEquals(1.24, healthy / healthyNoCone, 1e-6, "the unconditional 24%");
        Assertions.assertEquals(1.48, low / lowNoCone, 1e-6, "and the extra 24% below half HP");
        Assertions.assertEquals(1.0, lowOrdinary / lowOrdinaryNoCone, 1e-6,
                "the extra half is scoped to ADDITIONAL damage: an ordinary hit is untouched");
    }
}
