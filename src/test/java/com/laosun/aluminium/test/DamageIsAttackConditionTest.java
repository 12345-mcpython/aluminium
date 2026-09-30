package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * `damage_is_attack`: "the instance being settled counts as an attack".
 *
 * <p>\u2b50 Its implementation was inverted (measured 2026-09-30 while shipping cone 23008): it returned the negation, so a
 * rule guarded by it fired on ADDITIONAL damage and never on a real attack. This test states the contract directly, both
 * ways, so the next inversion is caught by the condition's own judge rather than by a reader two steps away.
 */
public class DamageIsAttackConditionTest {
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer;
    private Enemy enemy;

    /** A rule that only fires when the instance is an attack, observed through an ATTACK modifier. */
    private Battle battleWithGuardedRule() {
        wearer = CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        TriggerSpec rule = TriggerSpecs.rule("DEALING_DAMAGE", List.of("damage_is_attack"),
                TriggerSpecs.modifyAttr("ATTACK", 0.1, 1));
        wearer.setTriggerTable(new TriggerTable(WEARER, List.of(rule)));
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    @Test
    public void anOrdinaryAttackPassesTheGuard() {
        Battle battle = battleWithGuardedRule();
        double before = wearer.getAttribute(AttributeType.ATTACK).get();
        battle.applyDamage(enemy, new Damage(wearer, enemy, DamageElement.FIRE, DamageType.NORMAL, 100));
        double delta = wearer.getAttribute(AttributeType.ATTACK).get() - before;
        System.out.println("[guard] ordinary attack: ATTACK delta=" + delta);
        Assertions.assertTrue(delta > 0, "an ordinary attack counts as an attack -- the guard must pass");
    }

    @Test
    public void additionalDamageDoesNotPassTheGuard() {
        Battle battle = battleWithGuardedRule();
        double before = wearer.getAttribute(AttributeType.ATTACK).get();
        Damage extra = new Damage(wearer, enemy, DamageElement.FIRE, DamageType.ADDITIONAL, 100);
        battle.applyAdditionalDamage(wearer, enemy, DamageElement.FIRE, 100, 0.0, 1.5);
        double delta = wearer.getAttribute(AttributeType.ATTACK).get() - before;
        System.out.println("[guard] additional damage: ATTACK delta=" + delta + " (extra type=" + extra.getType() + ")");
        Assertions.assertEquals(0.0, delta, 1e-9,
                "a follow-up instance does not count as an attack -- which is why this guard exists");
    }
}
