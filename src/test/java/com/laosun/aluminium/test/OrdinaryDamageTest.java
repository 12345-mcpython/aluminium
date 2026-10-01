package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * A rule-driven ORDINARY damage instance (2026-09-30), reader 1505 \u7eef\u82f1\u2019s technique:
 * \u300c\u8fdb\u5165\u6218\u6597\u540e\uff0c\u5bf9\u654c\u65b9\u5168\u4f53\u9020\u6210\u7b49\u540c\u4e8e\u7eef\u82f1 100% \u653b\u51fb\u529b\u7684\u7269\u7406\u5c5e\u6027\u4f24\u5bb9\u300d.
 *
 * <p>\u2b50 \u26a0 The first version of this test believed an ordinary instance credits the VICTIM ENERGY inside `applyDamage` and measured
 * 0 -> 0: the crediting lives in the attack pipeline, not there, so that reading cannot tell the two paths apart. The real
 * discriminator is {@code countsAsAttack}: additional damage sets `notCountsAsAttack` (which is why a `damage_is_attack` rule must
 * not see it), while an ordinary instance does. A probe rule on the ALLY\u2019s own table measures exactly that -- and it is planted on
 * the ally on purpose, because replacing the table of the character under test would delete the very rule being judged.
 */
public class OrdinaryDamageTest {
    private static final int WEARER = 1505;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Runs the same battle, optionally with a probe on the ally that reacts only to damage which counts as an attack. */
    private double techniqueDamageFromTheFirstEnemy(boolean withProbe) {
        Character elation = CharacterFactory.create(WEARER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        if (withProbe) {
            EffectSpec boost = new EffectSpec();
            TriggerSpecs.set(boost, "op", "BOOST_DAMAGE");
            TriggerSpecs.set(boost, "percent", 0.5);
            ally.setTriggerTable(new TriggerTable(ALLY, List.of(
                    TriggerSpecs.rule("DEALING_DAMAGE", List.of("damage_is_attack"), boost))));
        }
        Enemy first = EnemyFactory.create(MONSTER, 90, 1);
        Enemy second = EnemyFactory.create(MONSTER, 90, 2);
        Battle battle = new Battle(List.of(elation, ally), List.of(first, second), new Random(0));
        double before = first.getCurrentHp() + second.getCurrentHp();
        battle.startBattle();
        return before - (first.getCurrentHp() + second.getCurrentHp());
    }

    @Test
    public void theTechniqueHitsEveryoneAndItsDamageCountsAsAnAttack() {
        double plain = techniqueDamageFromTheFirstEnemy(false);
        double probed = techniqueDamageFromTheFirstEnemy(true);
        System.out.println("[ordinary] the technique took " + plain + " in total, and " + probed
                + " when a `damage_is_attack` rule was watching (x" + (probed / plain) + ")");
        Assertions.assertTrue(plain > 0, "the technique hits both enemies at battle start");
        Assertions.assertTrue(probed > plain,
                "an ORDINARY instance counts as an attack, so the probe saw it -- additional damage would not");
    }

    /** \u2605 The shipped rule, read off the compiled table (discipline 232). */
    @Test
    public void theShippedRuleSaysOrdinary() {
        Character elation = CharacterFactory.create(WEARER, LEVEL);
        var rules = elation.getTriggerTable().rulesFor(TriggerEvent.BATTLE_START).stream()
                .filter(rule -> rule.id().endsWith("technique_damage")).toList();
        Assertions.assertEquals(1, rules.size(), "the technique\u2019s damage rule");
        var effect = rules.getFirst().effects().getFirst();
        System.out.println("[ordinary] spec " + rules.getFirst().id() + " percent=" + effect.getPercent()
                + " target=" + effect.getTarget() + " ordinary=" + effect.getOrdinary());
        Assertions.assertEquals(1.0, effect.getPercent(), 1e-9, "100% of her attack");
        Assertions.assertEquals("all_enemies", effect.getTarget(), "over the whole enemy side");
        Assertions.assertEquals(Boolean.TRUE, effect.getOrdinary(), "settled as an ordinary instance");
    }
}
