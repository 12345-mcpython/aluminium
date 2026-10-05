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
 * Light cone 23058: while the wearer casts an ELATION skill, every enemy takes 15% more damage for 2 turns (its crit
 * damage / energy / energy-cap clauses are the row's and are registered).
 *
 * <p>The op is {@code MODIFY_DAMAGE_TAKEN} WITHOUT a damage type: the sentence says "受到的伤害", not elation damage --
 * unlike 21064, which names elation explicitly. The reading compares an ORDINARY instance, so a wrong damage_type
 * scoping would show up.
 */
public class Cone23058Test {
    private static final int CONE = 23058;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double SHARE = 0.15;

    private Character wearer;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        wearer.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(-1.0, DoubleValue.Modifier.ModifierSource.BUFF, 230580));
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private double ordinaryHit(Battle battle) {
        return battle.applyDamage(enemy, new Damage(wearer, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000));
    }

    private void elationCast(Battle battle, SkillCategory category) {
        var skill = wearer.getSkills().values().iterator().next();
        var token = battle.beginCast(skill, wearer);
        battle.fireTriggers(TriggerEvent.CAST_SETUP, wearer, enemy, 0, 0, category);
        battle.endCastOutcome();
        battle.endCast(token);
    }

    @Test
    public void anElationCastRaisesOrdinaryDamageTakenToo() {
        Battle battle = battle(true);
        double before = ordinaryHit(battle);
        elationCast(battle, SkillCategory.ELATION_DAMAGE);
        double after = ordinaryHit(battle);
        System.out.println("[23058] ordinary instance before=" + before + " after=" + after
                + " ratio=" + (after / before));
        Assertions.assertEquals(1 + SHARE, after / before, 1e-6,
                "the sentence says 受到的伤害 -- 15% on EVERY damage type, not elation only");
    }

    @Test
    public void aNonElationCastDoesNot() {
        Battle battle = battle(true);
        double before = ordinaryHit(battle);
        elationCast(battle, SkillCategory.ULTRA);
        System.out.println("[23058] after an ULTRA cast: before=" + before);
        Assertions.assertEquals(before, ordinaryHit(battle), 1e-9, "the clause names an ELATION skill (false case)");
    }

    /** The spec half: op, share, duration and the target set pinned, so a wrong duration has something to break. */
    @Test
    public void theSpecPinsTheNumbers() {
        Battle battle = battle(true);
        int pinned = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.CAST_SETUP,
                new com.laosun.aluminium.models.TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null, battle,
                        SkillCategory.ELATION_DAMAGE))) {
            for (var effect : rule.effects()) {
                if (!"MODIFY_DAMAGE_TAKEN".equals(effect.getOp())) {
                    continue;
                }
                pinned++;
                System.out.println("[23058] spec percent=" + effect.getPercent() + " damageType=" + effect.getDamageType()
                        + " turns=" + effect.getTurns() + " target=" + effect.getTarget());
                Assertions.assertEquals(0.15, effect.getPercent(), 1e-9, "15% at rank 1");
                Assertions.assertNull(effect.getDamageType(), "no damage type -- the sentence says 受到的伤害");
                Assertions.assertEquals(2, effect.getTurns(), "for 2 turns");
                Assertions.assertEquals("all_enemies", effect.getTarget(), "on every enemy");
            }
        }
        Assertions.assertEquals(1, pinned, "exactly one such rule from this cone");
    }

    @Test
    public void withoutTheConeNothingMoves() {
        Battle battle = battle(false);
        double before = ordinaryHit(battle);
        elationCast(battle, SkillCategory.ELATION_DAMAGE);
        System.out.println("[23058] without the cone: before=" + before);
        Assertions.assertEquals(before, ordinaryHit(battle), 1e-9, "no cone, no change (false case)");
    }
}
