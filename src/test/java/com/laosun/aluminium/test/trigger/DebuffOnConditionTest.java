package com.laosun.aluminium.test.trigger;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The condition {@code <subject>_debuff:<ATTR>}: "that party carries a negative modifier on this attribute".
 *
 * <p>The FALSE case is the point: an attribute that was RAISED must not satisfy it, and neither must a lowered
 * attribute on somebody else. A condition that only looked at "is the number lower than its base" would pass the first
 * reading and fail these.
 */
public class DebuffOnConditionTest {
    private static final int UNIT = 1205;
    private static final int OTHER = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int MODIFIER_ID = 990001;

    private Character owner;
    private Enemy enemy;
    private Battle battle;

    private void build(String condition) {
        owner = CharacterFactory.create(UNIT, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        // The proven spelling: a bare EffectSpec filled through TriggerSpecs.set (TriggerSpecs.effect has no
        // (op, key, value) overload -- measured).
        var pay = new com.laosun.aluminium.beans.EffectSpec();
        TriggerSpecs.set(pay, "op", "GAIN_ENERGY");
        TriggerSpecs.set(pay, "amount", 7.0);
        TriggerSpecs.set(pay, "target", "self");
        owner.setTriggerTable(new TriggerTable(UNIT, List.of(TriggerSpecs.rule("DEALING_DAMAGE",
                List.of("actor == self", condition), pay))));
        battle = new Battle(List.of(owner), List.of(enemy), new Random(0));
        battle.startBattle();
    }

    private boolean matches() {
        return !owner.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(owner, owner, enemy, 0, 0, null, battle, null)).isEmpty();
    }

    @Test
    public void aLoweredAttributeSatisfiesIt() {
        build("target_debuff:DEFENCE");
        Assertions.assertFalse(matches(), "nothing lowered yet");
        enemy.getAttribute(AttributeType.DEFENCE)
                .addModifier(DoubleValue.Modifier.addPercent(-0.3, DoubleValue.Modifier.ModifierSource.DEBUFF, MODIFIER_ID));
        System.out.println("[debuff] after lowering the enemy's defence: matches=" + matches());
        Assertions.assertTrue(matches(), "a DEBUFF-sourced modifier is the fact the sentence names");
    }

    @Test
    public void aRaisedAttributeDoesNot() {
        build("target_debuff:DEFENCE");
        enemy.getAttribute(AttributeType.DEFENCE)
                .addModifier(DoubleValue.Modifier.addPercent(0.3, DoubleValue.Modifier.ModifierSource.BUFF, MODIFIER_ID));
        System.out.println("[debuff] after RAISING the enemy's defence: matches=" + matches());
        Assertions.assertFalse(matches(), "DEF Reduction (防御力被降低) is not DEF Boost (防御力被提高) -- the false case");
    }

    @Test
    public void aLoweredAttributeOnSomebodyElseDoesNot() {
        build("self_debuff:DEFENCE");
        enemy.getAttribute(AttributeType.DEFENCE)
                .addModifier(DoubleValue.Modifier.addPercent(-0.3, DoubleValue.Modifier.ModifierSource.DEBUFF, MODIFIER_ID));
        System.out.println("[debuff] the ENEMY is lowered, the condition asks about SELF: matches=" + matches());
        Assertions.assertFalse(matches(), "the condition names its party (false case)");
    }

    @Test
    public void anUnknownAttributeIsRefusedWithAMessage() {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> build("target_debuff:NOT_AN_ATTRIBUTE"));
        System.out.println("[debuff] refused: " + thrown.getMessage());
        // `AttributeType.fromString` THROWS for an unknown spelling rather than returning null (measured:
        // "Unknown AttributeType: NOT_AN_ATTRIBUTE"), so the message comes from the enum and not from my own branch.
        Assertions.assertTrue(thrown.getMessage().contains("Unknown AttributeType"),
                "the loader says which attribute it did not recognise");
    }
}
