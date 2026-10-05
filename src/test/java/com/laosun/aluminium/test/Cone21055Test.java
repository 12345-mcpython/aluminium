package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 21055: while one of OUR units is at half health or more, the damage IT deals is 12% higher.
 *
 * <p>⭐ The subject is the ATTACKER (not the wearer and not the victim), which is why the variable is `actor_hp_percent`. The
 * judge moves the ATTACKER’s health -- the wearer stays untouched, so no other mechanic of the cone’s owner is disturbed.
 */
public class Cone21055Test {
    private static final int CONE = 21055;
    private static final int WEARER = 1205;
    private static final int ATTACKER = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer;
    private Character attacker;
    private Enemy enemy;
    private Battle battle;

    private void build(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        attacker = CharacterFactory.create(ATTACKER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(List.of(wearer, attacker), List.of(enemy), new Random(0));
        battle.startBattle();
    }

    private double dealtByAttacker() {
        return battle.applyDamage(enemy, new Damage(attacker, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000));
    }

    private void hurtTheAttacker() {
        // ★ A bounded loop, because the defence zone does not let a fixed figure land as written (measured: damage of
        // 0.6 x max HP left the attacker at 57.3%) -- and it stops as soon as the share is really below half, so the unit
        // cannot be killed by accident (which would make every later reading zero).
        for (int i = 0; i < 8 && attacker.getCurrentHp() / attacker.getMaxHp() >= 0.5; i++) {
            battle.applyDamage(attacker, new Damage(enemy, attacker, DamageElement.FIRE, DamageType.NORMAL,
                    attacker.getMaxHp() * 0.2));
        }
        System.out.println("[21055] the attacker is now at " + attacker.getCurrentHp() + " of " + attacker.getMaxHp()
                + " (" + (attacker.getCurrentHp() / attacker.getMaxHp()) + ")");
    }

    @Test
    public void aHealthyAttackerHitsHarderAndAHurtOneDoesNot() {
        build(false);
        double plain = dealtByAttacker();
        build(true);
        double healthy = dealtByAttacker();
        hurtTheAttacker();
        double hurt = dealtByAttacker();
        System.out.println("[21055] the attacker’s damage: plain=" + plain + " healthy=" + healthy
                + " (x" + (healthy / plain) + ") ; after being hurt=" + hurt + " (x" + (hurt / plain) + ")");
        Assertions.assertEquals(1.12, healthy / plain, 0.01, "above half health the hit is 12% stronger");
        Assertions.assertEquals(1.0, hurt / plain, 0.01, "below it the clause stops applying (false case)");
    }

    @Test
    public void theWearerIsNotTheSubject() {
        build(true);
        double before = dealtByAttacker();
        battle.applyDamage(wearer, new Damage(enemy, wearer, DamageElement.FIRE, DamageType.NORMAL,
                wearer.getMaxHp() * 0.9));
        double after = dealtByAttacker();
        System.out.println("[21055] hurting the WEARER: " + before + " -> " + after);
        Assertions.assertEquals(before, after, before * 0.02, "the sentence names our units, not the wearer’s own health");
    }

    @Test
    public void withoutTheConeNothingChanges() {
        build(false);
        double first = dealtByAttacker();
        double second = dealtByAttacker();
        System.out.println("[21055] without the cone: " + first + " then " + second);
        Assertions.assertEquals(first, second, 1e-9, "no cone, no boost (false case)");
    }
}
