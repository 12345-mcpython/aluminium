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

/**
 * Cones 23030 (aggro, whose number only the upstream data states, plus a follow-up stack) and 23032 (a state plus a
 * break-effect threshold). Every judge keeps a reading where the condition is FALSE.
 */
public class ConeAggroAndStateTest {
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer(int cone) {
        return cone == 0 ? CharacterFactory.create(WEARER, LEVEL)
                : CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(cone, LEVEL, false, 1));
    }

    @Test
    public void theAggroRatioComesFromTheUpstreamRow() {
        Character unit = wearer(23030);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        double aggro = unit.getAttribute(AttributeType.AGGRO_ADDED_RATIO).get();
        System.out.println("[23030] aggro added ratio=" + aggro);
        Assertions.assertEquals(5.0, aggro, 1e-9,
                "the text only says 'a large increase'; upstream OnStack names AggroAddedRatio from SkillEquip Index 4 = 5");
        Assertions.assertEquals(0.0, wearer(0).getAttribute(AttributeType.AGGRO_ADDED_RATIO).get(), 1e-9,
                "and a cone-less wearer carries none");
    }

    @Test
    public void theFireDanceStacksRaiseFollowUpDamage() {
        Character unit = wearer(23030);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        // Deterministic instances: never crit, so the only variable is FOLLOW_UP_DAMAGE_BOOST.
        double noStack = battle.applyAdditionalDamage(unit, enemy, DamageElement.FIRE, 1000, 0.0, 1.5);
        for (int i = 0; i < 4; i++) {
            battle.fireTriggers(TriggerEvent.ULT_CAST, unit, unit, 0, 0);
        }
        double stacked = battle.applyAdditionalDamage(unit, enemy, DamageElement.FIRE, 1000, 0.0, 1.5);
        int pinned = 0;
        for (var rule : unit.getTriggerTable().matching(TriggerEvent.ULT_CAST,
                new TriggerTable.TriggerContext(unit, unit, unit, 0, 0))) {
            var effects = rule.effects();
            if (effects.size() == 2 && "ADD_STACK".equals(effects.get(0).getOp())) {
                pinned++;
                System.out.println("[23030] stack cap=" + effects.get(0).getMaxStacks() + " perStack="
                        + effects.get(1).getPercent() + " turns=" + effects.get(0).getTurns());
                Assertions.assertEquals(2, effects.get(0).getMaxStacks(), "at most two layers");
                Assertions.assertEquals(0.36, effects.get(1).getPercent(), 1e-9, "36% per layer");
            }
        }
        Assertions.assertEquals(1, pinned, "exactly one stack rule from this cone");
        System.out.println("[23030] follow-up damage noStack=" + noStack + " stacked=" + stacked
                + " ratio=" + (stacked / noStack));
        Assertions.assertEquals(1.72, stacked / noStack, 1e-6,
                "four ultimates cap at two layers of 36%: (1 + 0.72) / 1");
    }

    /** One hit against a FRESH enemy per configuration, so no reading inherits another's damage or state. */
    private double hitWith(int cone, boolean applyState, double breakingEffectBonus) {
        Character unit = wearer(cone);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        if (breakingEffectBonus > 0) {
            unit.getAttribute(AttributeType.BREAKING_EFFECT)
                    .addModifier(DoubleValue.Modifier.pure(breakingEffectBonus, DoubleValue.Modifier.ModifierSource.BUFF, 230321));
        }
        if (applyState) {
            unit.getAttribute(AttributeType.EFFECT_HIT_RATE)
                    .addModifier(DoubleValue.Modifier.pure(2.0, DoubleValue.Modifier.ModifierSource.BUFF, 230320));
            battle.fireTriggers(TriggerEvent.ULT_CAST, unit, enemy, 0, 0);
        }
        return battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.PHYSICAL, DamageType.NORMAL, 1000));
    }

    @Test
    public void theForgottenStateRaisesDamageAndTheThresholdRaisesItMore() {
        // ⚠ No spec count here: `matching` EVALUATES conditions, and this context's target does not carry the state,
        // so a count would be 0 by construction (measured). The three behaviour readings below pin both halves instead.
        double plain = hitWith(23032, false, 0);
        double withState = hitWith(23032, true, 0);
        double atThreshold = hitWith(23032, true, 1.5);
        double thresholdOnly = hitWith(23032, false, 1.5);
        System.out.println("[23032] plain=" + plain + " withState=" + withState + " atThreshold=" + atThreshold
                + " thresholdOnly=" + thresholdOnly);
        System.out.println("[23032] ratios: state=" + (withState / plain) + " state+break=" + (atThreshold / plain)
                + " breakOnly=" + (thresholdOnly / plain));
        Assertions.assertEquals(1.1, withState / plain, 1e-6, "a forgotten enemy takes 10% more");
        Assertions.assertEquals(1.18, atThreshold / plain, 1e-6,
                "at 150% break effect the extra 8% stacks on top of the 10%");
        Assertions.assertEquals(1.0, thresholdOnly / plain, 1e-6, "the threshold needs the state to matter");
    }
}
