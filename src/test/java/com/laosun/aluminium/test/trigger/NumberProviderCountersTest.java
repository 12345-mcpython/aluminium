package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.NumberProvider;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The providers that read a <b>counter or a bar</b>: {@code party_resource}, {@code energy}, {@code skill_point} and
 * {@code steps}.
 *
 * <p><b>Why these are worth a judge each.</b>
 * <ul>
 *   <li>{@code party_resource} lives on the <b>battle</b>, not on a unit: reading it from the owner would answer 0 for
 *       every file, which is a wrong number with no symptom.</li>
 *   <li>{@code energy} has two relations ({@code current} / {@code max}) that differ by a whole bar; a provider that
 *       always read the maximum would still "work".</li>
 *   <li>{@code skill_point} is the battle's, and it is the counter two of the shipped rules gate on.</li>
 *   <li>{@code steps} must agree with {@code per_steps}: both divide a counter into whole steps, and the provider is
 *       the one that states a number instead of a repeat count.</li>
 * </ul>
 *
 * <p>Measured through {@code GAIN_RESOURCE}: a resource is the one counter whose change is readable straight off the
 * character, so the assertion is the number the rule produced, not a derived HP or damage figure.
 */
public class NumberProviderCountersTest {

    /** 1413 长夜月 declares 亿质, so it is the resource the rules below pay into. */
    private static final int OWNER = 1413;
    private static final int LEVEL = 80;
    private static final String RESOURCE = "亿质";

    /**
     * {@code energy} reads the current bar, and {@code relation: "max"} the cap.
     */
    @Test
    public void energyReadsTheCurrentBarOrTheCap() {
        Assertions.assertEquals(40, gainFrom(provider("energy", p -> { }), 40), 1e-9,
                "the current energy is what \"of the current energy\" means");
        Assertions.assertEquals(CharacterFactory.create(OWNER, LEVEL).getMaxEnergy(),
                gainFrom(provider("energy", p -> p.setRelation("max")), 40), 1e-9,
                "relation \"max\" reads the cap instead");
    }

    /**
     * {@code skill_point} is the battle's pool: 3 at the start of a battle ({@code Constant.SKILL_POINT_START}).
     */
    @Test
    public void skillPointIsTheBattlesPool() {
        Assertions.assertEquals(3, gainFrom(provider("skill_point", p -> { }), 0), 1e-9,
                "a battle starts with 3 skill points");
    }

    /**
     * {@code steps} divides a counter into whole steps, the {@code per_steps} reading.
     */
    @Test
    public void stepsCountsWholeMilestones() {
        // DEFENCE is the counter: an attribute is one of the three things `steps` may count.
        double defence = CharacterFactory.create(OWNER, LEVEL).getAttribute(AttributeType.DEFENCE).get();
        double expected = Math.floor(defence / 100);

        Assertions.assertEquals(expected, gainFrom(provider("steps", p -> {
            p.setCounter("DEFENCE");
            p.setStep(100.0);
        }), 0), 1e-9, "floor(DEFENCE / 100) must be the count");
        Assertions.assertTrue(expected > 0, "the case would be vacuous at 0 (" + defence + ")");
    }

    /**
     * {@code party_resource} reads a battle-scoped counter, and a counter no file declares is loud rather than 0.
     */
    @Test
    public void partyResourceIsReadFromTheBattleAndMissingOnesAreLoud() {
        IllegalStateException error = Assertions.assertThrows(IllegalStateException.class,
                () -> gainFrom(provider("party_resource", p -> p.setResource("不存在的队伍资源")), 0),
                "an undeclared party counter is a wrong 0 waiting to happen, so it throws");
        Assertions.assertTrue(error.getMessage().contains("不存在的队伍资源"), error.getMessage());
    }

    /**
     * The four new types refuse a missing field at load.
     */
    @Test
    public void theNewTypesAreRefusedWhenTheirFieldsAreMissing() {
        assertRefused(provider("party_resource", p -> { }), "resource");
        assertRefused(provider("steps", p -> p.setCounter("DEFENCE")), "step");
        assertRefused(provider("steps", p -> p.setStep(10.0)), "counter");
        assertRefused(provider("energy", p -> p.setRelation("half")), "relation");
    }

    // --- fixtures ---------------------------------------------------------------------------------------------------

    /**
     * Runs one {@code BATTLE_START} rule that pays the resolved number into {@link #RESOURCE}, and returns what the
     * resource gained.
     */
    private static double gainFrom(NumberProvider amount, double energy) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setCurrentEnergy(energy);
        Battle battle = new Battle(List.of(owner), List.of(BattleFixture.monster()), BattleFixture.seeded());

        EffectSpec pay = new EffectSpec();
        TriggerSpecs.set(pay, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(pay, "resource", RESOURCE);
        TriggerSpecs.set(pay, "amountFrom", amount);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("BATTLE_START", null, pay))));

        double before = owner.getResources().value(RESOURCE);
        battle.startBattle();
        return owner.getResources().value(RESOURCE) - before;
    }

    private static void assertRefused(NumberProvider provider, String missing) {
        EffectSpec pay = new EffectSpec();
        TriggerSpecs.set(pay, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(pay, "resource", RESOURCE);
        TriggerSpecs.set(pay, "amountFrom", provider);
        TriggerSpec rule = TriggerSpecs.rule("BATTLE_START", null, pay);

        IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(rule)),
                "a provider missing \"" + missing + "\" must be refused at load");
        Assertions.assertTrue(error.getMessage().contains(missing),
                "the message names the missing field: " + error.getMessage());
    }

    private static NumberProvider provider(String type, java.util.function.Consumer<NumberProvider> fill) {
        NumberProvider provider = new NumberProvider();
        provider.setType(type);
        fill.accept(provider);
        return provider;
    }
}
