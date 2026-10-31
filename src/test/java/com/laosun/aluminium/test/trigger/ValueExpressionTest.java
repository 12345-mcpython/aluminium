package com.laosun.aluminium.test.trigger;

import com.google.gson.Gson;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@code value}: one expression for a whole magnitude, instead of {@code scale} + {@code percent} + {@code amount}.
 *
 * <p><b>What is pinned.</b> The expression is resolved by the same {@code NumberProviders} evaluator the
 * {@code percent_from} / {@code amount_from} spellings use, and it is the <b>whole</b> number - no op-specific scale
 * arithmetic is applied after it. It is accepted only on the ops whose reader asks for a magnitude; an op that asks for
 * a bare share refuses it, so the field cannot be silently ignored.
 */
public class ValueExpressionTest {

    private static final int LEVEL = 80;

    /**
     * A constant expression is the whole shield: no scale, no percent, no amount.
     */
    @Test
    public void aConstantExpressionIsTheWholeMagnitude() {
        Character original = CharacterFactory.create(1003, LEVEL);
        original.setTriggerTable(TriggerTables.of(9014));
        Battle battle = new Battle(List.of(original), List.of(BattleFixture.monster()),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Character hero = (Character) battle.allies().stream()
                .filter(unit -> unit instanceof Character)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the battle has no character of ours"));

        Assertions.assertEquals(123, hero.getShield(), 1e-9,
                "{\"type\":\"const\",\"value\":123} is the shield's number, not a share of anything");
    }

    /**
     * The loader refuses a second spelling of the same number.
     */
    @Test
    public void valueCannotBeCombinedWithTheLegacyFields() {
        String message = refusal("{\"op\":\"SHIELD\",\"value\":{\"type\":\"const\",\"value\":10},"
                + "\"percent\":0.5,\"turns\":1,\"target\":\"self\"}");
        Assertions.assertTrue(message.contains("value") && message.contains("percent"),
                "the message must name both spellings: " + message);
    }

    /**
     * A skill-row base is a second source of the same number, so it is refused beside {@code value} too.
     */
    @Test
    public void valueCannotBeCombinedWithASkillRow() {
        String message = refusal("{\"op\":\"DAMAGE\",\"value\":{\"type\":\"const\",\"value\":10},"
                + "\"skill\":\"SKILL\",\"damage_param\":0,\"element\":\"Ice\"}");
        Assertions.assertTrue(message.contains("value") && message.contains("skill"),
                "the message must name both sources: " + message);
    }

    /**
     * An op that does not read a number at all has no place for a magnitude; accepting it would silently do nothing.
     */
    @Test
    public void valueIsRefusedOnAnOpThatDoesNotReadANumber() {
        String message = refusal("{\"op\":\"MODIFY_RULE\",\"value\":{\"type\":\"const\",\"value\":10}}");
        Assertions.assertTrue(message.contains("value") && message.contains("MODIFY_RULE"),
                "the message must name the field and the op: " + message);
    }

    /**
     * The provider itself is validated at load time, like the other number-provider spellings.
     */
    @Test
    public void anUnknownProviderIsRefused() {
        String message = refusal("{\"op\":\"SHIELD\",\"value\":{\"type\":\"nope\"},\"turns\":1,"
                + "\"target\":\"self\"}");
        Assertions.assertTrue(message.contains("nope") || message.contains("unknown"),
                "the message must name the unknown provider type: " + message);
    }

    /**
     * The resource and HP-cost family reads {@code value} too: energy, gain/spend/remove a counter, and pay HP.
     */
    @Test
    public void valueWorksOnEnergyResourcesAndHpCost() {
        Character original = CharacterFactory.create(1003, LEVEL);
        // The table is installed after construction, so register the fixture's declared counter explicitly (the
        // assembly point does this from the file when the character is built).
        original.getResources().register("probe", 99, 10);
        original.setTriggerTable(TriggerTables.of(9015));
        Battle battle = new Battle(List.of(original), List.of(BattleFixture.monster()),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Character hero = (Character) battle.allies().stream()
                .filter(unit -> unit instanceof Character)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the battle has no character of ours"));

        Assertions.assertEquals(10, hero.getCurrentEnergy(), 1e-9, "fixed GAIN_ENERGY value");
        Assertions.assertEquals(12, hero.getResources().value("probe"),
                "10 initial + 5 gained - 2 spent - 1 removed");
        Assertions.assertEquals(hero.getMaxHp() - 100, hero.getCurrentHp(), 1e-6, "CONSUME_HP value");
    }

    /**
     * "All of it" and "this many" are different claims; the pair is refused rather than silently picking one.
     */
    @Test
    public void valueCannotBeCombinedWithSpendAll() {
        String message = refusal("{\"op\":\"SPEND_RESOURCE\",\"resource\":\"probe\","
                + "\"value\":{\"type\":\"const\",\"value\":10},\"spendAll\":true}");
        Assertions.assertTrue(message.contains("value") && message.contains("spendAll"),
                "the message must name both claims: " + message);
    }

    /**
     * A share-only op reads {@code value} as the share itself: {@code 0.5} = 50% of max energy.
     */
    @Test
    public void valueWorksOnAShareOnlyOp() {
        Character original = CharacterFactory.create(1003, LEVEL);
        original.setTriggerTable(TriggerTables.of(9016));
        Battle battle = new Battle(List.of(original), List.of(BattleFixture.monster()),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Character hero = (Character) battle.allies().stream()
                .filter(unit -> unit instanceof Character)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the battle has no character of ours"));

        Assertions.assertEquals(hero.getMaxEnergy() * 0.5, hero.getCurrentEnergy(), 1e-9,
                "RAISE_ENERGY_TO value 0.5 is a floor at 50% of max energy");
    }

    /**
     * Count ops read {@code value} as the count: three stacks added, one removed, and the resource cap widened by five.
     */
    @Test
    public void valueWorksOnCountOps() {
        Character original = CharacterFactory.create(1003, LEVEL);
        original.getResources().register("probe", 10, 0);
        original.setTriggerTable(TriggerTables.of(9018));
        Battle battle = new Battle(List.of(original), List.of(BattleFixture.monster()),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Character hero = (Character) battle.allies().stream()
                .filter(unit -> unit instanceof Character)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the battle has no character of ours"));

        Assertions.assertEquals(2, hero.getBuffManager().stacksOf("probe_stack"),
                "3 added - 1 removed");
        Assertions.assertEquals(5, hero.getResources().get("probe").getMaxOverflow(),
                "RAISE_RESOURCE_CAP value 5");
    }

    /**
     * The two remaining fraction ops: class resistance and damage absorption. A literal `value` outside (0, 1] is
     * refused at load, like the old `percent` range check.
     */
    @Test
    public void valueWorksOnResistAndAbsorb() {
        EffectSpec resist = new Gson().fromJson(
                "{\"op\":\"RESIST_DEBUFF\",\"value\":{\"type\":\"const\",\"value\":0.35},\"turns\":1}",
                EffectSpec.class);
        Assertions.assertDoesNotThrow(
                () -> new TriggerTable(1003, List.of(TriggerSpecs.rule("BATTLE_START", null, resist))),
                "RESIST_DEBUFF value 0.35 is a valid share");

        EffectSpec absorb = new Gson().fromJson(
                "{\"op\":\"ABSORB_DAMAGE\",\"resource\":\"probe\",\"unit_amount\":200,"
                        + "\"value\":{\"type\":\"const\",\"value\":0.6},\"turns\":1}",
                EffectSpec.class);
        Assertions.assertDoesNotThrow(
                () -> new TriggerTable(1003, List.of(TriggerSpecs.rule("BATTLE_START", null, absorb))),
                "ABSORB_DAMAGE value 0.6 is a valid share");

        String message = refusal("{\"op\":\"RESIST_DEBUFF\",\"value\":{\"type\":\"const\",\"value\":2},"
                + "\"turns\":1}");
        Assertions.assertTrue(message.contains("value") && message.contains("share"),
                "a literal share above 1 is refused: " + message);
    }

    private static String refusal(String effectJson) {
        EffectSpec effect = new Gson().fromJson(effectJson, EffectSpec.class);
        TriggerSpec rule = TriggerSpecs.rule("BATTLE_START", null, effect);
        return Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(1003, List.of(rule))).getMessage();
    }
}
