package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * The `elation_base` scale: an Elation damage instance's base comes from the loaded level table, and NOT from
 * attack power -- which is the definition the spec gives at {@code ROADMAP.md:1262}
 * ({@code 基础值 × 欢愉倍率 × (1+欢愉度) × (1+增笑) × (1+笑点×5/(笑点+240))}, "禁攻击力/属性增伤").
 *
 * <p>That definition is what makes the judge two-sided without pinning a magic number: the same rule, run
 * against two different attack values, must deal the SAME damage. A rule that quietly scaled off attack would
 * fail here.
 */
public class ElationBaseScaleTest {
    private static final int PEARL = 1503;
    private static final int MONSTER = 1002011;

    @Test
    public void theBaseComesFromTheTableAndNotFromAttack() {
        double low = damageWithAttack(0.0);
        double high = damageWithAttack(2.0);
        System.out.println("[elation_base] damage with no attack boost = " + low
                + " ; with +200% attack = " + high);

        Assertions.assertTrue(low > 0, "the instance settles at all (the scale resolved)");
        Assertions.assertEquals(low, high, 1e-9,
                "Elation damage bans attack power, so raising ATTACK must not change it");
    }

    /** Fires a rule-driven ELATION instance and returns the damage the enemy took. */
    private static double damageWithAttack(double attackBoost) {
        Character pearl = CharacterFactory.create(PEARL, 80);
        var enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(pearl, CharacterFactory.create(1204, 80)), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character mine = battle.characters.getFirst();

        EffectSpec instance = TriggerSpecs.damage(null, 0, null, "target");
        TriggerSpecs.set(instance, "scale", "elation_base");
        TriggerSpecs.set(instance, "percent", 1.0);
        TriggerSpecs.set(instance, "element", "Ice");
        TriggerSpecs.set(instance, "damageType", "ELATION");

        // Raise attack through a RULE rather than the attribute API, which has no add() here.
        var rules = new java.util.ArrayList<com.laosun.aluminium.beans.TriggerSpec>();
        if (attackBoost > 0) {
            rules.add(TriggerSpecs.rule("TURN_START", List.of("actor == self"),
                    TriggerSpecs.modifyAttr("ATTACK", attackBoost, 999)));
        }
        rules.add(TriggerSpecs.rule("ULT_CAST", List.of("actor == self"), instance));
        mine.setTriggerTable(new TriggerTable(PEARL, rules));

        // The table is installed after startBattle(), so BATTLE_START has already passed: drive the boost's own
        // event, then read what the instance settles.
        if (attackBoost > 0) {
            battle.fireTriggers(TriggerEvent.TURN_START, mine, mine, 0, 0);
        }

        double before = enemy.getCurrentHp();
        battle.fireTriggers(TriggerEvent.ULT_CAST, mine, battle.enemies.getFirst(), 0, 0);
        return before - enemy.getCurrentHp();
    }
}
