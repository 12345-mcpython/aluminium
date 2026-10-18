package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * {@code "scale": "self_resource:<id>"} - a damage instance (or a modifier) whose base is <b>one of the rule owner's
 * own counters</b>.
 *
 * <p>Reader: 1409 Hyacine (风堇)'s 忆灵技1, 「造成等同于<b>本场战斗风堇与小伊卡累计治疗数值</b> #1% 的风属性伤害并清空累计治疗
 * 数值的 50%」. The counter could already be gained, spent and read in a <b>condition</b>, and a battle-PARTY counter
 * could already be a magnitude - but "this unit's counter, as the base of a number" had no spelling at all.
 *
 * <p>What is measured: the stated percent is applied to the counter <b>linearly and exactly once</b> (a half share
 * halves the damage), and a counter the owner does not have is <b>loud</b> rather than 0 - the latter is the whole
 * reason the spelling checks instead of answering 0.
 */
public class SelfResourceMagnitudeTest {

    private static final int SUSHANG = 1206;
    private static final int LEVEL = 80;
    private static final String COUNTER = "试算";

    @Test
    public void theStatedPercentIsAppliedToTheOwnersCounter() {
        double half = damageFrom(0.5, 4000);
        double quarter = damageFrom(0.25, 4000);

        Assertions.assertTrue(half > 0, "the instance must deal damage at all");
        Assertions.assertEquals(2.0, half / quarter, 0.02,
                "0.5 vs 0.25 of the same counter must differ by exactly two: half=" + half + ", quarter=" + quarter);
    }

    @Test
    public void theCounterItselfScalesTheDamageLinearly() {
        double fourThousand = damageFrom(0.5, 4000);
        double twoThousand = damageFrom(0.5, 2000);

        Assertions.assertEquals(2.0, fourThousand / twoThousand, 0.02,
                "twice the counter is twice the damage: " + fourThousand + " vs " + twoThousand);
    }

    @Test
    public void aCounterTheOwnerDoesNotHaveIsLoudRatherThanZero() {
        Character hero = CharacterFactory.create(SUSHANG, LEVEL);
        Enemy enemy = Enemy.fromAttributes("Dummy", 60000, 500, 100, 90);
        Battle battle = new Battle(List.of(hero), List.of(enemy), fixed());
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "scale", "self_resource:" + COUNTER);   // never registered
        TriggerSpecs.set(effect, "percent", 0.5);
        TriggerSpecs.set(effect, "element", "Physical");
        TriggerSpecs.set(effect, "target", "all_enemies");
        hero.setTriggerTable(new TriggerTable(SUSHANG, List.of(TriggerSpecs.rule(
                TriggerEvent.BATTLE_START.name(), List.of(), effect))));

        RuntimeException thrown = Assertions.assertThrows(RuntimeException.class, battle::startBattle);
        Assertions.assertTrue(String.valueOf(thrown.getMessage()).contains(COUNTER),
                "the failure has to name the counter so the author can find it: " + thrown.getMessage());
    }

    /**
     * Fires one {@code self_resource} DAMAGE at the given share of the given counter and returns what it cost the
     * enemy.
     */
    private static double damageFrom(double percent, int counterValue) {
        Character hero = CharacterFactory.create(SUSHANG, LEVEL);
        Enemy enemy = Enemy.fromAttributes("Dummy", 60000, 500, 100, 90);
        Battle battle = new Battle(List.of(hero), List.of(enemy), fixed());
        hero.getResources().register(COUNTER, 1_000_000, counterValue);
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "scale", "self_resource:" + COUNTER);
        TriggerSpecs.set(effect, "percent", percent);
        TriggerSpecs.set(effect, "element", "Physical");
        TriggerSpecs.set(effect, "target", "all_enemies");
        hero.setTriggerTable(new TriggerTable(SUSHANG, List.of(TriggerSpecs.rule(
                TriggerEvent.BATTLE_START.name(), List.of(), effect))));
        double before = enemy.getCurrentHp();
        battle.startBattle();
        return before - enemy.getCurrentHp();
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(1.0);
    }
}
