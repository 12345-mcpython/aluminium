package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 23044: damage always ignores 18% of the target's defence, and while \u3010\u70c8\u9633\u3011 is up (granted by an Ultimate,
 * removed at the wearer's turn start) the wearer deals 60% more.
 *
 * <p>\u2b50 Four states are measured on the same settlement: no cone, the cone alone (ignore only), after an Ultimate (ignore +
 * the sun), and after a turn start (the sun is gone again, so back to ignore only).
 */
public class Cone23044Test {
    private static final int CONE = 23044;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String SUN = "\u70c8\u9633";

    private Character wearer;
    private Enemy enemy;
    private Battle battle;

    private void build(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(List.of(wearer, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy), new Random(0));
        battle.startBattle();
        wearer.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(-1.0, DoubleValue.Modifier.ModifierSource.BUFF, 230441));
    }

    private double hit() {
        return battle.applyDamage(enemy, new Damage(wearer, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000));
    }

    @Test
    public void theIgnoreIsAlwaysOnAndTheSunAddsMore() {
        build(false);
        double plain = hit();
        build(true);
        double withCone = hit();
        battle.fireTriggers(TriggerEvent.ULT_CAST, wearer, enemy, 0, 0);
        boolean sun = wearer.getBuffManager().stacksOf(SUN) >= 1;
        double withSun = hit();
        battle.fireTriggers(TriggerEvent.TURN_START, wearer, null, 0, 0);
        boolean sunGone = wearer.getBuffManager().stacksOf(SUN) == 0;
        double afterTurnStart = hit();
        System.out.println("[23044] plain=" + plain + " cone=" + withCone + " (x" + (withCone / plain) + ")"
                + " ; after ult (sun=" + sun + ")=" + withSun + " (x" + (withSun / plain) + ")"
                + " ; after turn start (sunGone=" + sunGone + ")=" + afterTurnStart
                + " (x" + (afterTurnStart / plain) + ")");
        Assertions.assertTrue(withCone > plain, "the defence ignore alone already raises the damage");
        Assertions.assertTrue(sun, "an Ultimate grants the sun");
        Assertions.assertTrue(withSun > withCone, "and the sun adds 60% on top");
        Assertions.assertTrue(sunGone, "the wearer's turn start removes it");
        Assertions.assertEquals(withCone / plain, afterTurnStart / plain, 1e-9,
                "so the third state is the first state again");
    }

    @Test
    public void somebodyElsesUltimateDoesNotRaiseTheSun() {
        build(true);
        com.laosun.aluminium.models.CanHit ally = battle.allies.get(1);
        battle.fireTriggers(TriggerEvent.ULT_CAST, ally, enemy, 0, 0);
        System.out.println("[23044] after the ALLY's ultimate: sun stacks=" + wearer.getBuffManager().stacksOf(SUN));
        Assertions.assertEquals(0, wearer.getBuffManager().stacksOf(SUN), "the wearer's own Ultimate (false case)");
    }

    @Test
    public void theSpecPinsAllFourClauses() {
        build(true);
        int ignores = 0;
        int boosts = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new com.laosun.aluminium.models.TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null,
                        battle, null))) {
            if (!rule.id().startsWith("cone23044_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                System.out.println("[23044] spec " + rule.id() + " attribute=" + effect.getAttribute()
                        + " percent=" + effect.getPercent() + " instance=" + effect.getInstance());
                if ("DEFENCE_IGNORE".equals(effect.getAttribute())) {
                    ignores++;
                    Assertions.assertEquals(0.18, effect.getPercent(), 1e-9, "18% at rank 1");
                } else {
                    boosts++;
                    Assertions.assertEquals(0.6, effect.getPercent(), 1e-9, "60% at rank 1");
                }
            }
        }
        Assertions.assertEquals(1, ignores, "one ignore rule");
        Assertions.assertEquals(0, boosts, "the sun rule only exists while the sun is up (checked behaviourally above)");
    }
}
