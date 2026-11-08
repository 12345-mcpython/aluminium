package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.buff.StatModifierBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerInterpreter;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@code while_summon_count}: a modifier that lives only while the rule owner still has a <b>living summon</b> on the field.
 *
 * <p>Reader: 1222 Lingsha (灵砂) 星魂 6, 「【浮元】在场时，使敌方全体全属性抗性降低 20%」 - the modifier sits on the ENEMIES while the
 * question ("is my summon still out?") is about the RULE OWNER. The four older gates each ask a different fact - a state
 * ({@code while_state}), a counter ({@code while_stacks}), an HP bar ({@code while_hp_percent}) or one of those on the
 * carrier ({@code while_holder_state} / {@code while_holder_hp_percent}) - and none of them can answer "an entity of mine is
 * on the field".
 *
 * <p>This is the engine half on its own, with no content file involved: the same separation {@code HolderStateGateTest}
 * documents, so a broken gate and a broken DSL wiring cannot be mistaken for each other.
 */
public class SummonCountGateTest {
    private static final double EPS = 1e-9;

    @Test
    public void aModifierGatedOnTheOwnersSummonIsDroppedWithIt() {
        Character master = CharacterFactory.create(1222, 80);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(master), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();

        enemy.getBuffManager().addBuff(gatedOnTheOwnersSummons(battle, master));
        Assertions.assertEquals(0.2, enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get(), EPS,
                "precondition: the modifier is on the victim");

        // Nothing of the owner's is on the field: the gate is already closed, and a turn boundary takes the modifier off.
        Assertions.assertTrue(battle.summonCountOf(master) == 0, "precondition: no summon yet");
        enemy.getBuffManager().beforeMove();
        Assertions.assertEquals(0.0, enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get(), EPS,
                "the owner has nothing on the field, so the modifier goes");

        // A panel-less summon opens the gate; a turn boundary now leaves the modifier alone.
        battle.placePanelLessSummon(master, "测试召唤物", 90.0);
        battle.processRequests();
        Assertions.assertTrue(battle.summonCountOf(master) == 1, "precondition: one summon is out");
        enemy.getBuffManager().addBuff(gatedOnTheOwnersSummons(battle, master));
        enemy.getBuffManager().beforeMove();
        Assertions.assertEquals(0.2, enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get(), EPS,
                "a living summon keeps the gate open");

        // It perishes (the engine's "it went away, nobody killed it"): the gate closes at the next boundary.
        battle.summonsOf(master).getFirst().perish();
        Assertions.assertTrue(battle.summonCountOf(master) == 0, "precondition: the summon left");
        enemy.getBuffManager().beforeMove();
        Assertions.assertEquals(0.0, enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get(), EPS,
                "the summon is gone, so the modifier goes with it");
    }

    /** A -20% all-type-resistance debuff whose life depends on the owner having at least one living summon. */
    private static StatModifierBuff gatedOnTheOwnersSummons(Battle battle, Character master) {
        StatModifierBuff reduction = StatModifierBuff.of(AttributeType.RESISTANCE_REDUCTION, "pure", 0.2,
                "debuff", 2, false, true, 1);
        reduction.gateSummons(1, () -> battle.summonCountOf(master));
        return reduction;
    }

    /**
     * The two load-time refusals this field has: a threshold of zero (a gate that never closes), and the field on an op that does
     * not read it (accepted and never asked is exactly the "silently ignored" family the validator exists for).
     */
    @Test
    public void theFieldIsRefusedWhenItCouldNotWork() {
        EffectSpec zero = gatedEffect();
        TriggerSpecs.set(zero, "whileSummonCount", 0);
        IllegalArgumentException threshold = Assertions.assertThrows(IllegalArgumentException.class,
                () -> TriggerInterpreter.validate(zero, TriggerSpecs.rule("SUMMONED", List.of(), zero)));
        Assertions.assertTrue(threshold.getMessage().contains("while_summon_count"), threshold.getMessage());
        Assertions.assertTrue(threshold.getMessage().contains("0"), threshold.getMessage());

        EffectSpec wrongOp = gatedEffect();
        TriggerSpecs.set(wrongOp, "op", "DAMAGE");
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> TriggerInterpreter.validate(wrongOp, TriggerSpecs.rule("SUMMONED", List.of(), wrongOp)));
        Assertions.assertTrue(refused.getMessage().contains("MODIFY_ATTR"), refused.getMessage());
    }

    private static EffectSpec gatedEffect() {
        EffectSpec effect = TriggerSpecs.modifyAttr("RESISTANCE_REDUCTION", 0.2, null, true, null, null, "all_enemies");
        TriggerSpecs.set(effect, "whileSummonCount", 1);
        return effect;
    }
}
