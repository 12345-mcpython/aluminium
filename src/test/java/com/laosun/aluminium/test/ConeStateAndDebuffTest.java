package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** Cones 21015 (a state and the defence drop it implies) and 23020 (debuff-fed crit damage, debate state). */
public class ConeStateAndDebuffTest {
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer(int cone) {
        return cone == 0 ? CharacterFactory.create(WEARER, LEVEL)
                : CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(cone, LEVEL, false, 1));
    }

    private Battle battleWith(Character unit, Enemy enemy) {
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    @Test
    public void theStateIsAppliedAndThenLowersDefence() {
        Character unit = wearer(21015);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = battleWith(unit, enemy);
        unit.getAttribute(AttributeType.EFFECT_HIT_RATE)
                .addModifier(DoubleValue.Modifier.pure(2.0, DoubleValue.Modifier.ModifierSource.BUFF, 210150));
        double before = enemy.getAttribute(AttributeType.DEFENCE).get();
        battle.fireTriggers(TriggerEvent.DEALING_DAMAGE, unit, enemy, 0, 0);
        System.out.println("[21015] state=" + enemy.getBuffManager().hasState("攻陷")
                + " defence " + before + " -> " + enemy.getAttribute(AttributeType.DEFENCE).get());
        Assertions.assertTrue(enemy.getBuffManager().hasState("攻陷"), "the state landed");
        // The state rule fires on the same event, so the defence drop is visible immediately.
        Assertions.assertTrue(enemy.getAttribute(AttributeType.DEFENCE).get() <= before,
                "the defence drop follows the state");
        int pinned = 0;
        for (var rule : unit.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(unit, unit, enemy, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("DEFENCE".equals(effect.getAttribute())) {
                    pinned++;
                    System.out.println("[21015] spec defence percent=" + effect.getPercent()
                            + " turns=" + effect.getTurns() + " target=" + effect.getTarget());
                    Assertions.assertEquals(-0.12, effect.getPercent(), 1e-9, "rank 1 states 12% less");
                }
            }
        }
        Assertions.assertEquals(1, pinned, "the defence-drop effect exists");
        // Note: The guard itself is only observable with a CONTRAST: a clean enemy must expose the "apply the state"
        // rule and NOT the defence-drop one; an enemy that carries the state must expose exactly the opposite
        // (measured before this: dropping the guard changed no reading at all).
        int cleanApply = 0;
        int cleanDrop = 0;
        for (var rule : unit.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(unit, unit,
                        EnemyFactory.create(MONSTER, 90, 1), 0, 0))) {
            for (var effect : rule.effects()) {
                if ("APPLY_BUFF".equals(effect.getOp())) {
                    cleanApply++;
                }
                if ("DEFENCE".equals(effect.getAttribute())) {
                    cleanDrop++;
                }
            }
        }
        int statedApply = 0;
        int statedDrop = 0;
        for (var rule : unit.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(unit, unit, enemy, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("APPLY_BUFF".equals(effect.getOp())) {
                    statedApply++;
                }
                if ("DEFENCE".equals(effect.getAttribute())) {
                    statedDrop++;
                }
            }
        }
        System.out.println("[21015] clean enemy: apply=" + cleanApply + " drop=" + cleanDrop
                + " ; stated enemy: apply=" + statedApply + " drop=" + statedDrop);
        Assertions.assertEquals(1, cleanApply, "a clean enemy is offered the state");
        Assertions.assertEquals(0, cleanDrop, "but not the defence drop");
        Assertions.assertEquals(1, statedDrop, "a stated enemy is offered the defence drop");
        Assertions.assertEquals(0, statedApply, "and no longer the state");
    }

    /** A real debuff, attached through the engine's own roll path, so `target_debuff_count` is genuinely >= 1. */
    private void giveTheEnemyADebuff(Character unit, Enemy enemy, Battle battle) {
        // Note: A 1.0 base chance STILL rolls against the target's effect RES (discipline 145): without this the
        // helper silently attached nothing and the spec query saw 0 debuffs.
        unit.getAttribute(AttributeType.EFFECT_HIT_RATE)
                .addModifier(DoubleValue.Modifier.pure(5.0, DoubleValue.Modifier.ModifierSource.BUFF, 230208));
        battle.tryApplyDebuff(unit, enemy,
                new com.laosun.aluminium.models.buff.DotBuff(unit, DamageElement.FIRE, 100, 2), 1.0, null);
    }

    /** One guaranteed-crit hit, so the instance-scoped crit-damage bonus is what moves. */
    private double critHit(Character unit, Enemy enemy, Battle battle) {
        unit.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(1.0, DoubleValue.Modifier.ModifierSource.BUFF, 230209));
        return battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000));
    }

    @Test
    public void theDebuffCountRaisesCritDamageAndOnlyFollowUpsIgnoreDefence() {
        Character unit = wearer(23020);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = battleWith(unit, enemy);
        // Note: The spec query EVALUATES conditions, so the enemy must really carry a debuff before asking
        // (discipline from round 254: a count on a false condition is 0 by construction).
        giveTheEnemyADebuff(unit, enemy, battle);
        System.out.println("[23020] enemy debuffs=" + enemy.getBuffManager().countBuffs(com.laosun.aluminium.models.buff.DotBuff.class));
        int critPerDebuff = 0;
        for (var rule : unit.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(unit, unit, enemy, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("CRIT_ATTACK".equals(effect.getAttribute()) && Boolean.TRUE.equals(effect.getInstance())) {
                    critPerDebuff++;
                    System.out.println("[23020] spec crit percent=" + effect.getPercent()
                            + " per_stack=" + effect.getPerStack() + " damage_type=" + effect.getDamageType());
                    Assertions.assertEquals(0.08, effect.getPercent(), 1e-9, "rank 1 states 8 points per debuff");
                    Assertions.assertEquals("target_debuff_count", effect.getPerStack(), "one layer per debuff");
                }
            }
        }
        Assertions.assertEquals(1, critPerDebuff, "one instance-scoped crit modifier from this cone");

        // Behaviour: a guaranteed crit against a debuffed enemy versus a clean one.
        double withDebuff = critHit(unit, enemy, battle);
        Character plain = wearer(23020);
        Enemy clean = EnemyFactory.create(MONSTER, 90, 1);
        Battle plainBattle = battleWith(plain, clean);
        double withoutDebuff = critHit(plain, clean, plainBattle);
        // Note: Derive the expectation from the wearer's OWN crit-damage attribute, not from a hard-coded 1.5: this cone
        // also grants a 0.2 constant, so the base multiplier here is 1.(measured 1.040588235 = 1.8/1.).
        // Note: Two corrections in one place: (a) the expectation must come from the wearer's OWN attribute (this cone
        // grants a 0.2 constant, so 0.5 alone would be wrong), and (b) the ATTRIBUTE and the damage ZONE differ by one --
        // the zone is 1 + CRIT_ATTACK, which is why 0.reads as a 1.multiplier.
        double critAttribute = unit.getAttribute(AttributeType.CRIT_ATTACK).get();
        double zone = 1.0 + critAttribute;
        System.out.println("[23020] crit " + withDebuff + " vs clean " + withoutDebuff
                + " ratio=" + (withDebuff / withoutDebuff) + " expected=" + ((zone + 0.08) / zone)
                + " (crit attribute " + critAttribute + ", zone " + zone + ")");
        Assertions.assertEquals((zone + 0.08) / zone, withDebuff / withoutDebuff, 1e-6,
                "8 points of instance crit damage inside the wearer's own crit zone");

        // Note: The two readings must differ ONLY in the debate state: same enemy, same debuffs, same crit setup.
        // Comparing two different enemies conflated the per-debuff crit rule (measured 1.424 instead of 1.0).
        double additionalBefore = battle.applyAdditionalDamage(unit, enemy, DamageElement.FIRE, 1000, 0.0, 1.5);
        double ordinaryBefore = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000));
        battle.fireTriggers(TriggerEvent.ULT_CAST, unit, enemy, 0, 0);
        System.out.println("[23020] debate state=" + unit.getBuffManager().hasState("论辩")
                + " damage boost=" + unit.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get());
        Assertions.assertTrue(unit.getBuffManager().hasState("论辩"), "the debate state is on the wearer");
        Assertions.assertEquals(0.36, unit.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "36 points of damage while it lasts");
        double additionalAfter = battle.applyAdditionalDamage(unit, enemy, DamageElement.FIRE, 1000, 0.0, 1.5);
        double ordinaryAfter = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000));
        System.out.println("[23020] additional " + additionalBefore + " -> " + additionalAfter
                + " ratio=" + (additionalAfter / additionalBefore)
                + " ; ordinary " + ordinaryBefore + " -> " + ordinaryAfter
                + " ratio=" + (ordinaryAfter / ordinaryBefore));
        // Note: The state itself boosts EVERY hit by the authored 36%, so the ordinary ratio is 1.36, not 1.0 (my first
        // wording was simply wrong). What proves the SCOPE is that the follow-up lands strictly higher: 1.5556 against
        // 1.36 -- the extra factor is the defence ignore, and it exists only on ADDITIONAL instances.
        Assertions.assertEquals(1.0 + 0.36, ordinaryAfter / ordinaryBefore, 1e-6,
                "the state's own damage bonus applies to every instance");
        Assertions.assertEquals(1.5555555507, additionalAfter / additionalBefore, 1e-6,
                "the follow-up adds the defence ignore on top (measured, then pinned)");
        Assertions.assertTrue(additionalAfter / additionalBefore > ordinaryAfter / ordinaryBefore,
                "the ignore is scoped to ADDITIONAL: it only shows up on the follow-up");
    }
}
