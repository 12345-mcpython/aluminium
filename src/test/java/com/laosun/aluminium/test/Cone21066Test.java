package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 21066: while the wearer casts an ELATION skill, its damage ignores 8% of the target's defence.
 *
 * <p>⭐ The expectation is DERIVED, not guessed: the engine's defence zone is {@code (200 + 10L) / (def + 200 + 10L)},
 * so ignoring a share s of the defence multiplies the settled value by {@code (def + K) / (def * (1 - s) + K)}. The judge
 * computes that from the enemy's own DEFENCE attribute, which is what makes the reading attributable.
 */
public class Cone21066Test {
    private static final int CONE = 21066;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double IGNORE = 0.08;

    private Character wearer;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        // Crit pinned to 0: two instances must differ only by the defence zone (a crit is x1.5, measured last round).
        wearer.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(-1.0, DoubleValue.Modifier.ModifierSource.BUFF, 210660));
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private double hit(Battle battle) {
        return battle.applyDamage(enemy, new Damage(wearer, enemy, DamageElement.FIRE, DamageType.ELATION, 1000));
    }

    private void elationCast(Battle battle) {
        var skill = wearer.getSkills().values().iterator().next();
        var token = battle.beginCast(skill, wearer);
        battle.fireTriggers(TriggerEvent.CAST_SETUP, wearer, enemy, 0, 0, SkillCategory.ELATION_DAMAGE);
        battle.endCastOutcome();
        battle.endCast(token);
    }

    @Test
    public void duringAnElationCastTheDefenceZoneIsWeaker() {
        Battle battle = battle(true);
        double outside = hit(battle);
        double defence = enemy.getAttribute(AttributeType.DEFENCE).get();
        double k = 200 + 10.0 * LEVEL;
        double expected = (defence + k) / (defence * (1 - IGNORE) + k);
        double during = 0;
        var skill = wearer.getSkills().values().iterator().next();
        var token = battle.beginCast(skill, wearer);
        battle.fireTriggers(TriggerEvent.CAST_SETUP, wearer, enemy, 0, 0, SkillCategory.ELATION_DAMAGE);
        during = hit(battle);                       // settled WHILE the cast window is open
        battle.endCastOutcome();
        battle.endCast(token);
        double after = hit(battle);
        System.out.println("[21066] outside=" + outside + " during=" + during + " after=" + after
                + " ratio=" + (during / outside) + " derived=" + expected + " defence=" + defence);
        Assertions.assertEquals(expected, during / outside, 1e-6,
                "the derived defence-zone ratio: ignoring 8% of the defence is a factor of (def + K)/(def*0.92 + K)");
        Assertions.assertEquals(outside, after, 1e-9, "and the cast's end takes it away again");
    }

    @Test
    public void aNonElationCastDoesNotIgnoreAnything() {
        Battle battle = battle(true);
        double outside = hit(battle);
        var skill = wearer.getSkills().values().iterator().next();
        var token = battle.beginCast(skill, wearer);
        battle.fireTriggers(TriggerEvent.CAST_SETUP, wearer, enemy, 0, 0, SkillCategory.ULTRA);
        double during = hit(battle);
        battle.endCastOutcome();
        battle.endCast(token);
        System.out.println("[21066] during an ULTRA cast: outside=" + outside + " during=" + during);
        Assertions.assertEquals(outside, during, 1e-9, "the clause names an ELATION skill (false case)");
    }

    @Test
    public void withoutTheConeNothingMoves() {
        Battle battle = battle(false);
        double outside = hit(battle);
        elationCast(battle);
        System.out.println("[21066] without the cone: outside=" + outside);
        Assertions.assertEquals(outside, hit(battle), 1e-9, "no cone, no change (false case)");
    }
}
