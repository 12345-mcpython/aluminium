package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
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
 * Light cone 21064: while the wearer casts an ELATION skill, every enemy takes more elation damage.
 *
 * <p>\u2b50 What makes it expressible: the cast's CATEGORY reaches the rules on {@code CAST_SETUP}, and `from_category
 * ELATION_DAMAGE` is the honest spelling -- `from_skill` reads a SKILL SLOT, and elation is a category the slot
 * vocabulary does not have. The reading is a ratio of the same elation instance before and after, so nothing else can
 * account for the difference.
 */
public class Cone21064Test {
    private static final int CONE = 21064;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        // \u26a0 Crit chance pinned to 0: two hits otherwise differ by the crit roll (measured: 476.19 -> 714.29, exactly
        // the 1.5 crit multiplier), and a judge that compares two hits must compare the same arithmetic.
        wearer.getAttribute(com.laosun.aluminium.enums.AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(-1.0, DoubleValue.Modifier.ModifierSource.BUFF, 210640));
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private double elationHit(Battle battle) {
        return battle.applyDamage(enemy, new Damage(wearer, enemy, DamageElement.FIRE, DamageType.ELATION, 1000));
    }

    private void elationCast(Battle battle, SkillCategory category) {
        var skill = wearer.getSkills().values().iterator().next();
        var token = battle.beginCast(skill, wearer);
        battle.fireTriggers(TriggerEvent.CAST_SETUP, wearer, enemy, 0, 0, category);
        battle.endCastOutcome();
        battle.endCast(token);
    }

    @Test
    public void anElationCastRaisesElationDamageTaken() {
        Battle battle = battle(true);
        double before = elationHit(battle);
        elationCast(battle, SkillCategory.ELATION_DAMAGE);
        double after = elationHit(battle);
        System.out.println("[21064] elation instance before=" + before + " after=" + after
                + " ratio=" + (after / before));
        Assertions.assertEquals(1.06, after / before, 1e-6, "rank 1 states 6% more elation damage taken");
    }

    @Test
    public void aNonElationCastDoesNot() {
        Battle battle = battle(true);
        double before = elationHit(battle);
        elationCast(battle, SkillCategory.ULTRA);
        double after = elationHit(battle);
        System.out.println("[21064] after an ULTRA cast: before=" + before + " after=" + after);
        Assertions.assertEquals(before, after, 1e-9, "the clause names an ELATION skill (false case)");
    }


    @Test
    public void withoutTheConeNothingMoves() {
        Battle battle = battle(false);
        double before = elationHit(battle);
        elationCast(battle, SkillCategory.ELATION_DAMAGE);
        double after = elationHit(battle);
        System.out.println("[21064] without the cone: before=" + before + " after=" + after);
        Assertions.assertEquals(before, after, 1e-9, "no cone, no change (false case)");
    }
}
