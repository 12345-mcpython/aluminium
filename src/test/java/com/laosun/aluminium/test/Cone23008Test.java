package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
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
 * Light cone 23008: after the wearer attacks, each distinct target hit restores energy, up to 3 times per attack; and the
 * wearer's ultimate raises the whole party's SPEED by 12 for one turn. The ATK constant comes from the row's
 * `ability_property`, so the rules must NOT state it again.
 */
public class Cone23008Test {
    private static final int CONE = 23008;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double ENERGY_PER_HIT = 3;
    private static final double SPEED_FLAT = 12;

    private Character wearer;
    private Enemy first;
    private Enemy second;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        first = EnemyFactory.create(MONSTER, 90, 1);
        second = EnemyFactory.create(MONSTER, 90, 2);
        Battle battle = new Battle(List.of(wearer), List.of(first, second), new Random(0));
        battle.startBattle();
        return battle;
    }

    private void attack(Battle battle, Enemy victim) {
        battle.applyDamage(victim, new Damage(wearer, victim, DamageElement.FIRE, DamageType.NORMAL, 100));
    }

    @Test
    public void eachTargetHitRestoresEnergyAndTheAttackCapsIt() {
        Battle battle = battle(true);
        double before = wearer.getCurrentEnergy();
        attack(battle, first);
        attack(battle, second);
        double twoTargets = wearer.getCurrentEnergy() - before;
        System.out.println("[23008] two targets in one attack: energy +" + twoTargets);
        Assertions.assertEquals(2 * ENERGY_PER_HIT, twoTargets, 1e-6, "one restore per distinct target hit");

        // The cap: five more instances inside the SAME attack, none of which may add anything.
        for (int i = 0; i < 5; i++) {
            attack(battle, first);
        }
        double afterCap = wearer.getCurrentEnergy() - before;
        System.out.println("[23008] seven instances in one attack: energy +" + afterCap + " (cap 3 restores)");
        Assertions.assertEquals(3 * ENERGY_PER_HIT, afterCap, 1e-6, "at most 3 restores per attack");

        // A new attack may restore again.
        battle.fireAfterAttack(wearer, first, List.of(first), 100);
        attack(battle, first);
        double next = wearer.getCurrentEnergy() - before;
        System.out.println("[23008] the next attack: energy +" + next);
        Assertions.assertEquals(4 * ENERGY_PER_HIT, next, 1e-6, "the cap is per attack, not per battle");
    }

    @Test
    public void theUltimateRaisesTheWholePartysSpeedForOneTurn() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1));
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        double wearerBefore = unit.getAttribute(AttributeType.SPEED).get();
        double allyBefore = ally.getAttribute(AttributeType.SPEED).get();
        battle.fireTriggers(TriggerEvent.ULT_CAST, unit, enemy, 0, 0);
        double wearerDelta = unit.getAttribute(AttributeType.SPEED).get() - wearerBefore;
        double allyDelta = ally.getAttribute(AttributeType.SPEED).get() - allyBefore;
        System.out.println("[23008] ultimate: wearer speed +" + wearerDelta + ", ally speed +" + allyDelta);
        Assertions.assertEquals(SPEED_FLAT, wearerDelta, 1e-6, "all allies includes the wearer");
        Assertions.assertEquals(SPEED_FLAT, allyDelta, 1e-6, "and reaches the ally");
    }

    @Test
    public void withoutTheConeNothingMovesAndTheConstantIsTheRowsNotOurs() {
        Battle battle = battle(false);
        double before = wearer.getCurrentEnergy();
        attack(battle, first);
        attack(battle, second);
        System.out.println("[23008] without the cone: energy +" + (wearer.getCurrentEnergy() - before));
        Assertions.assertEquals(before, wearer.getCurrentEnergy(), 1e-6, "no cone, no energy (false case)");

        Character armed = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1));
        Character plain = CharacterFactory.create(WEARER, LEVEL);
        double armedBonus = armed.getAttribute(AttributeType.ATTACK).get()
                / Math.max(1e-9, armed.getAttribute(AttributeType.ATTACK).baseValue());
        double plainBonus = plain.getAttribute(AttributeType.ATTACK).get()
                / Math.max(1e-9, plain.getAttribute(AttributeType.ATTACK).baseValue());
        System.out.println("[23008] ATTACK ratio armed=" + armedBonus + " plain=" + plainBonus);
        Assertions.assertEquals(1.24, armedBonus, 1e-9,
                "the 24% ATK constant comes from the row's ability_property, applied by the engine");
        Assertions.assertEquals(1.0, plainBonus, 1e-9, "and an unarmed unit has it not");
        // \u2b50 1.24 IS the proof that the constant is stated once: had this cone's rules written 24% as well, the ratio
        // would be about 1.54. No need to inspect the rule list for it.

        int energyRules = 0;
        // \u26a0 The context must CARRY a damage instance: `damage_is_attack` reads it, so a hand-built context without one
        // matches nothing (measured -- that was this test's first failure).
        Damage instance = new Damage(armed, first, DamageElement.FIRE, DamageType.NORMAL, 100);
        for (var rule : armed.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(armed, armed, first, 0, 0, instance, battle, null))) {
            for (var effect : rule.effects()) {
                if ("GAIN_ENERGY".equals(effect.getOp())) {
                    energyRules++;
                    System.out.println("[23008] spec energy=" + effect.getAmount() + " perAttack=" + rule.perAttack());
                    Assertions.assertEquals(ENERGY_PER_HIT, effect.getAmount(), 1e-9, "3 energy per hit at rank 1");
                    Assertions.assertEquals(3, rule.perAttack(), "at most 3 times per attack");
                }
            }
        }
        Assertions.assertEquals(1, energyRules, "exactly one energy rule from this cone");
    }
}
