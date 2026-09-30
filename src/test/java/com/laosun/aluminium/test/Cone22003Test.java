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
 * Light cone 22003 (round 247, corrected in 248): the Max HP constant belongs to the engine's ability_property, not to us.
 *
 * <p>Writing it as a rule as well DOUBLE-COUNTED it: measured 4350.02 with the rule against 4060.02 without, i.e. our rule added a second 12%
 * on top of the one Weapon already grants. What is genuinely ours is the other half: after losing OR recovering HP, crit damage +18% for two
 * turns, at most once per turn.
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
    public void bothHalvesOfTheDisjunctionAreWritten() {
        Character unit = wearer(true);
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
    public void theConstantHalfComesFromAbilityPropertyNotFromUs() {
        Character rank1 = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1));
        Character rank5 = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 5));
        Character without = CharacterFactory.create(WEARER, LEVEL);
        System.out.println("[22003] maxHp rank1=" + rank1.getMaxHp() + " rank5=" + rank5.getMaxHp()
                + " without=" + without.getMaxHp());
        Assertions.assertTrue(rank1.getMaxHp() > without.getMaxHp(), "the cone grants Max HP via ability_property");
        Assertions.assertTrue(rank5.getMaxHp() > rank1.getMaxHp(), "and the grant scales with the superimposition");
        for (var rule : rank1.getTriggerTable().matching(TriggerEvent.BATTLE_START,
                new TriggerTable.TriggerContext(rank1, rank1, rank1, 0, 0))) {
            for (var effect : rule.effects()) {
                Assertions.assertNotEquals("HEALTH", effect.getAttribute(),
                        "we must NOT write the constant half ourselves: the engine already does");
            }
        }
    }

    @Test
    public void losingHpRaisesCritDamageAndTheControlWithoutTheConeDoesNot() {
        Character withCone = wearer(true);
        Character without = wearer(false);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(withCone, without), List.of(enemy), new Random(0));
        battle.startBattle();
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
    public void recoveringHpAlsoRaisesCritDamage() {
        Character healer = CharacterFactory.create(WEARER, LEVEL);
        Character target = wearer(true);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(healer, target), List.of(enemy), new Random(0));
        battle.startBattle();
        // NOT via battle.heal on a damaged target: losing HP would already fire the OTHER half of the disjunction, and the two
        // write the same modifier (same value, so the second replaces the first and nothing moves). Emitting HEALED directly
        // isolates this half, which is exactly what the clause says ("after recovering HP").
        double before = target.getAttribute(AttributeType.CRIT_ATTACK).get();
        battle.fireTriggers(TriggerEvent.HEALED, healer, target, 0, 200.0);
        double afterHeal = target.getAttribute(AttributeType.CRIT_ATTACK).get();
        System.out.println("[22003] heal half: crit " + before + " -> " + afterHeal);
        Assertions.assertEquals(before + 0.18, afterHeal, 1e-9, "recovering HP raises crit damage by 18 points");
    }
}
