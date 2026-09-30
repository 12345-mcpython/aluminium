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
 * Light cone 22003: a constant Max HP half, and a crit-damage half for "after losing OR recovering HP, once per turn".
 *
 * <p>The disjunction is two rules; the per-turn limit is the rule-level cooldown; the constant half is a rule because cones have no properties row.
 */
public class Cone22003Test {
    private static final int CONE = 22003;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer(boolean withCone) {
        return withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
    }

    @Test
    public void theConeCarriesTheConstantHalfAndBothHalvesOfTheDisjunction() {
        Character unit = wearer(true);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        int maxHp = 0;
        for (var rule : unit.getTriggerTable().matching(TriggerEvent.BATTLE_START,
                new TriggerTable.TriggerContext(unit, unit, enemy, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("MODIFY_ATTR".equals(effect.getOp()) && "HEALTH".equals(effect.getAttribute())) {
                    maxHp++;
                    System.out.println("[22003] max hp rule id=" + rule.id() + " percent=" + effect.getPercent());
                    Assertions.assertEquals(0.12, effect.getPercent(), 1e-9, "rank 1 states 12%");
                }
            }
        }
        Assertions.assertEquals(1, maxHp, "exactly one constant half");
        int halves = 0;
        for (var event : List.of(TriggerEvent.HP_LOST, TriggerEvent.HEALED)) {
            for (var rule : unit.getTriggerTable().matching(event,
                    new TriggerTable.TriggerContext(unit, unit, unit, 0, 0))) {
                for (var effect : rule.effects()) {
                    if ("MODIFY_ATTR".equals(effect.getOp()) && "CRIT_ATTACK".equals(effect.getAttribute())) {
                        halves++;
                        System.out.println("[22003] " + event + " rule id=" + rule.id()
                                + " percent=" + effect.getPercent() + " turns=" + effect.getTurns());
                        Assertions.assertEquals(0.18, effect.getPercent(), 1e-9, "rank 1 states 18%");
                        Assertions.assertEquals(2, effect.getTurns(), "for two turns");
                    }
                }
            }
        }
        Assertions.assertEquals(2, halves, "one rule per half of the disjunction");
    }

    @Test
    public void theConstantHalfRaisesMaxHpAndTheLossHalfRaisesCritDamage() {
        Character withCone = wearer(true);
        Character without = wearer(false);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(withCone, without), List.of(enemy), new Random(0));
        battle.startBattle();
        System.out.println("[22003] maxHp withCone=" + withCone.getMaxHp() + " without=" + without.getMaxHp());
        Assertions.assertTrue(withCone.getMaxHp() > without.getMaxHp(), "the constant half raises Max HP");
        double before = withCone.getAttribute(AttributeType.CRIT_ATTACK).get();
        battle.applyDamage(withCone, new Damage(enemy, withCone, DamageElement.PHYSICAL, DamageType.NORMAL, 20));
        double afterLoss = withCone.getAttribute(AttributeType.CRIT_ATTACK).get();
        double controlBefore = without.getAttribute(AttributeType.CRIT_ATTACK).get();
        battle.applyDamage(without, new Damage(enemy, without, DamageElement.PHYSICAL, DamageType.NORMAL, 20));
        double controlAfter = without.getAttribute(AttributeType.CRIT_ATTACK).get();
        System.out.println("[22003] withCone crit " + before + " -> " + afterLoss
                + " ; without " + controlBefore + " -> " + controlAfter);
        Assertions.assertEquals(before + 0.18, afterLoss, 1e-9, "losing HP raises crit damage by 18 points");
        Assertions.assertEquals(controlBefore, controlAfter, 1e-9, "without the cone nothing happens");
    }

    @Test
    public void theMaxHpHalfScalesWithTheSuperimposition() {
        // \u26a0 A cone ALSO contributes its own base HP stat, so "with vs without" mixes two causes. The rank contrast
        // isolates the ability: the base stat does not change with superimposition, only the 12% -> 24% does.
        Character rank1 = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1));
        Character rank5 = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 5));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(rank1, rank5), List.of(enemy), new Random(0));
        battle.startBattle();
        int halves = 0;
        for (var rule : rank5.getTriggerTable().matching(TriggerEvent.BATTLE_START,
                new TriggerTable.TriggerContext(rank5, rank5, enemy, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("MODIFY_ATTR".equals(effect.getOp()) && "HEALTH".equals(effect.getAttribute())) {
                    halves++;
                    System.out.println("[22003] rank5 max hp rule percent=" + effect.getPercent());
                    Assertions.assertEquals(0.24, effect.getPercent(), 1e-9, "rank 5 states 24%");
                }
            }
        }
        Assertions.assertEquals(1, halves, "the rank-5 file has its own constant half");
        System.out.println("[22003] maxHp rank1=" + rank1.getMaxHp() + " rank5=" + rank5.getMaxHp());
        Assertions.assertTrue(rank5.getMaxHp() > rank1.getMaxHp(),
                "the higher rank grants a bigger Max HP share (base stat is rank-independent)");
    }

    @Test
    public void recoveringHpAlsoRaisesCritDamage() {
        Character healer = CharacterFactory.create(WEARER, LEVEL);
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(healer, wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        double before = wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
        battle.heal(healer, wearer, 200);
        double afterHeal = wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
        System.out.println("[22003] heal half: crit " + before + " -> " + afterHeal);
        Assertions.assertEquals(before + 0.18, afterHeal, 1e-9, "recovering HP raises crit damage by 18 points");
    }
}
