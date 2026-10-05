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
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Cones 23035 (a broken enemy takes more BREAK damage) and 23038 (a crit-damage aura carried by a state).
 */
public class ConeBreakAndAuraTest {
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Battle battle(Character... units) {
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(units), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private Enemy enemyOf(Battle battle, Character unit) {
        return (Enemy) battle.getOpponents(unit).getFirst();
    }

    @Test
    public void aBrokenEnemyTakesMoreBreakDamageButNotOrdinaryDamage() {
        Character withCone = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(23035, LEVEL, false, 1));
        Character without = CharacterFactory.create(WEARER, LEVEL);
        Battle battle = battle(withCone, without);
        Enemy target = enemyOf(battle, withCone);
        // The state application rolls: give the applier effect hit rate first (discipline 145).
        withCone.getAttribute(AttributeType.EFFECT_HIT_RATE)
                .addModifier(DoubleValue.Modifier.pure(2.0, DoubleValue.Modifier.ModifierSource.BUFF, 230350));
        battle.fireTriggers(TriggerEvent.BREAK, withCone, target, 0, 0);
        // ⚠ ADD_STACK builds a COUNTER, not a state hasState() reports (measured: hasState was false while the
        // damage ratio below did move). So the stacking is asserted where it is observable: the ratio and the spec.
        int spec = 0;
        for (var rule : withCone.getTriggerTable().matching(TriggerEvent.BREAK,
                new com.laosun.aluminium.models.TriggerTable.TriggerContext(withCone, withCone, target, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("ADD_STACK".equals(effect.getOp())) {
                    spec++;
                    System.out.println("[23035] spec stack max=" + effect.getMaxStacks() + " turns=" + effect.getTurns());
                    Assertions.assertEquals(2, effect.getMaxStacks(), "at most two layers");
                }
                if ("MODIFY_DAMAGE_TAKEN".equals(effect.getOp())) {
                    System.out.println("[23035] spec taken percent=" + effect.getPercent()
                            + " damage_type=" + effect.getDamageType() + " per_stack=" + effect.getPerStack());
                    Assertions.assertEquals("BREAK", effect.getDamageType(), "scoped to break damage");
                }
            }
        }
        Assertions.assertEquals(1, spec, "one stacking effect from this cone");
        double breakWith = battle.applyDamage(target, new Damage(withCone, target, DamageElement.FIRE, DamageType.BREAK, 1000));
        double ordinary = battle.applyDamage(target, new Damage(withCone, target, DamageElement.FIRE, DamageType.NORMAL, 1000));
        // A second, clean enemy for the reference readings.
        Enemy reference = EnemyFactory.create(MONSTER, 90, 1);
        Battle plain = new Battle(List.of(without), List.of(reference), new Random(0));
        plain.startBattle();
        double breakPlain = plain.applyDamage(reference, new Damage(without, reference, DamageElement.FIRE, DamageType.BREAK, 1000));
        double ordinaryPlain = plain.applyDamage(reference, new Damage(without, reference, DamageElement.FIRE, DamageType.NORMAL, 1000));
        System.out.println("[23035] break " + breakWith + " vs " + breakPlain + " ratio=" + (breakWith / breakPlain)
                + " ; ordinary " + ordinary + " vs " + ordinaryPlain + " ratio=" + (ordinary / ordinaryPlain));
        Assertions.assertEquals(1.18, breakWith / breakPlain, 1e-6, "the burn raises BREAK damage by 18%");
        Assertions.assertEquals(1.0, ordinary / ordinaryPlain, 1e-6,
                "and NORMAL damage is untouched: the modifier is scoped to BREAK");
    }

    @Test
    public void theStateCarriesAPartyCritDamageAura() {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(23038, LEVEL, false, 1));
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = battle(wearer, ally);
        // ⚠ No "before" reading in THIS battle: BATTLE_START already applied the aura (measured: 0.98 = 0.5 + 0.48),
        // so the baseline has to come from a battle with no cone at all (discipline 141).
        Character reference = CharacterFactory.create(ALLY, LEVEL);
        Character plainWearer = CharacterFactory.create(WEARER, LEVEL);
        Battle plainBattle = battle(plainWearer, reference);
        double baseline = reference.getAttribute(AttributeType.CRIT_ATTACK).get();
        battle.fireTriggers(TriggerEvent.FOLLOW_UP, wearer, enemyOf(battle, wearer), 0, 0);
        double afterFollowUp = ally.getAttribute(AttributeType.CRIT_ATTACK).get();
        System.out.println("[23038] ally crit damage baseline(no cone)=" + baseline + " with cone=" + afterFollowUp
                + " ; state on the wearer=" + wearer.getBuffManager().hasState("谕示"));
        Assertions.assertEquals(baseline + 0.48, afterFollowUp, 1e-9, "the aura gives the party 48 points of crit damage");
        // ⚠ The duration has to be PINNED: every reading here happens in the same turn, so shortening the aura is
        // invisible to them (measured: 2 -> 1 turn gave reds 0). Discipline 147.
        int pinnedTurns = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.FOLLOW_UP,
                new com.laosun.aluminium.models.TriggerTable.TriggerContext(wearer, wearer, wearer, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("CRIT_ATTACK".equals(effect.getAttribute()) && "all_allies".equals(effect.getTarget())) {
                    pinnedTurns++;
                    System.out.println("[23038] spec aura percent=" + effect.getPercent()
                            + " turns=" + effect.getTurns() + " target=" + effect.getTarget());
                    Assertions.assertEquals(0.48, effect.getPercent(), 1e-9, "rank 1 states 48%");
                    Assertions.assertEquals(2, effect.getTurns(), "for two turns");
                }
            }
        }
        Assertions.assertEquals(1, pinnedTurns, "exactly one party aura rule on FOLLOW_UP");
        Assertions.assertTrue(wearer.getBuffManager().hasState("谕示"), "carried by the state");
        // A battle without the cone: the same stimulus changes nothing.
        plainBattle.fireTriggers(TriggerEvent.FOLLOW_UP, plainWearer, enemyOf(plainBattle, plainWearer), 0, 0);
        System.out.println("[23038] without the cone, after the same stimulus: "
                + reference.getAttribute(AttributeType.CRIT_ATTACK).get() + " (baseline " + baseline + ")");
        Assertions.assertEquals(baseline, reference.getAttribute(AttributeType.CRIT_ATTACK).get(), 1e-9,
                "no cone, no aura (false case)");
    }
}
