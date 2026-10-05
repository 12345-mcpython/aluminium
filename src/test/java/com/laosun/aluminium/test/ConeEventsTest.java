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
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Cones 21016 (burn the attacker) and 21041 (a debuff-fed stack plus an EHR threshold). Both use events that already
 * exist -- TAKING_HIT and DEBUFF_APPLIED -- and their constant halves come from ability_property.
 */
public class ConeEventsTest {
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer(int cone) {
        return cone == 0 ? CharacterFactory.create(WEARER, LEVEL)
                : CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(cone, LEVEL, false, 1));
    }

    @Test
    public void beingHitBurnsTheEnemyAndTheConditionIsTargeted() {
        Character unit = wearer(21016);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        // Note: The wearer's OWN file already has TAKING_HIT rules, so counting rules would be wrong (measured: 2).
        // Count the op instead: only our cone applies a burn.
        int burns = 0;
        var dot = (com.laosun.aluminium.beans.EffectSpec) null;
        for (var rule : unit.getTriggerTable().matching(TriggerEvent.TAKING_HIT,
                new TriggerTable.TriggerContext(unit, enemy, unit, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("APPLY_DOT".equals(effect.getOp())) {
                    burns++;
                    dot = effect;
                }
            }
        }
        Assertions.assertEquals(1, burns, "exactly one burn rule comes from this cone");
        Assertions.assertNotNull(dot, "the burn rule is present");
        System.out.println("[21016] op=" + dot.getOp() + " element=" + dot.getElement() + " scale=" + dot.getScale()
                + " percent=" + dot.getPercent() + " turns=" + dot.getTurns() + " chance=" + dot.getBaseChance());
        Assertions.assertEquals("APPLY_DOT", dot.getOp());
        Assertions.assertEquals("self_attr:DEFENCE", dot.getScale(),
                "the burn is 40% of the WEARER's defence; scale names the owner's attribute");
        Assertions.assertEquals(0.4, dot.getPercent(), 1e-9, "rank 1 states 40% of DEF");
        Assertions.assertEquals(2, dot.getTurns(), "for two turns");
        // Note: The data says 100%-120% at the five ranks, and an unstated chance IS 100% (TriggerTable: "an unstated chance is 100% and has no number to raise"), so the field is omitted.
        Assertions.assertNull(dot.getBaseChance(),
                "the data says 100-120%, so base_chance is omitted (unstated = 100%)");
        // Behaviour: the burn lands on the enemy when the WEARER is hit. Note: A base_chance effect still ROLLS against
        // the target's effect RES, so the applier needs effect hit rate to make this deterministic -- measured: with a
        // bare wearer the roll failed and the enemy carried 0 DoTs.
        unit.getAttribute(AttributeType.EFFECT_HIT_RATE)
                .addModifier(DoubleValue.Modifier.pure(2.0, DoubleValue.Modifier.ModifierSource.BUFF, 210160));
        battle.applyDamage(unit, new Damage(enemy, unit, DamageElement.PHYSICAL, DamageType.NORMAL, 20));
        int afterHit = enemy.getBuffManager().countBuffs(DotBuff.class);
        System.out.println("[21016] dots on the enemy after the wearer was hit=" + afterHit);
        Assertions.assertEquals(1, afterHit, "the burn landed");
        // ... and the FALSE case: hitting the ALLY must not burn anything.
        Enemy second = EnemyFactory.create(MONSTER, 90, 1);
        Battle other = new Battle(List.of(unit, ally), List.of(second), new Random(0));
        other.startBattle();
        ally.getAttribute(AttributeType.EFFECT_HIT_RATE)
                .addModifier(DoubleValue.Modifier.pure(2.0, DoubleValue.Modifier.ModifierSource.BUFF, 210161));
        other.applyDamage(ally, new Damage(second, ally, DamageElement.PHYSICAL, DamageType.NORMAL, 20));
        System.out.println("[21016] dots after the ALLY was hit=" + second.getBuffManager().countBuffs(DotBuff.class));
        Assertions.assertEquals(0, second.getBuffManager().countBuffs(DotBuff.class),
                "the rule is about the wearer being hit, not any ally");
    }

    @Test
    public void aDebuffFedStackRaisesDamageUpToItsCap() {
        Character unit = wearer(21041);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        double before = unit.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        for (int i = 0; i < 4; i++) {
            battle.fireTriggers(TriggerEvent.DEBUFF_APPLIED, unit, enemy, 0, 0);
        }
        double after = unit.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        System.out.println("[21041] damage boost " + before + " -> " + after + " (delta=" + (after - before) + ")");
        Assertions.assertEquals(0.06 * 3, after - before, 1e-9, "four debuffs cap at three stacks of 6 points");
    }

    @Test
    public void theEhrThresholdGatesTheAttackBonus() {
        Character high = wearer(21041);
        Character low = wearer(21041);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(high, low), List.of(enemy), new Random(0));
        double lowBase = low.getAttribute(AttributeType.ATTACK).baseValue();
        high.getAttribute(AttributeType.EFFECT_HIT_RATE)
                .addModifier(DoubleValue.Modifier.pure(0.8, DoubleValue.Modifier.ModifierSource.BUFF, 210401));
        battle.startBattle();
        double highAtk = high.getAttribute(AttributeType.ATTACK).get();
        double lowAtk = low.getAttribute(AttributeType.ATTACK).get();
        System.out.println("[21041] highEhr atk=" + highAtk + " lowEhr atk=" + lowAtk
                + " (expected +0.2*base=" + (0.2 * lowBase) + ")");
        Assertions.assertEquals(0.2 * lowBase, highAtk - lowAtk, lowBase * 0.02,
                "only the wearer at or above 80% effect hit rate gets the attack bonus");
    }
}
