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
 * The provider types that read a <b>live</b> number rather than a table row: {@code attr} (an attribute),
 * {@code stacks} (a counter) and the two random ones ({@code uniform} / {@code weighted_list}).
 *
 * <p><b>Why each of these is worth a judge.</b>
 * <ul>
 *   <li>{@code attr} and {@code stacks} are the provider spellings of the {@code self_attr:} / {@code self_stacks:}
 *       <b>scales</b>. The two must agree - a provider that read a different subject (the caster instead of the rule
 *       owner, say) would be a wrong number with no symptom, because both spellings still "work".</li>
 *   <li>The random types are the only providers whose value is not a function of the battle state, so the judge has to
 *       pin <b>determinism</b> as well as the interval: a seeded battle must reproduce, or a replay stops meaning
 *       anything.</li>
 *   <li>{@code weighted_list} with a <b>zero weight</b> is the spelling for a fallback that never fires while the main
 *       entry is alive; an implementation that divided by the entry count instead of the total weight would pick it
 *       anyway, and the number would only be wrong sometimes.</li>
 * </ul>
 */
public class NumberProviderSourcesTest {

    private static final int OWNER = 1001;              // 三月七: the DEF-driven shield is her own kit
    private static final int LEVEL = 80;

    /**
     * {@code attr} reads the rule owner's sheet - and gives the same shield the literal would.
     */
    @Test
    public void anAttrProviderReadsTheOwnersSheet() {
        double defence = CharacterFactory.create(OWNER, LEVEL).getAttribute(AttributeType.DEFENCE).get();

        double viaProvider = shieldAfter(shielding(provider("attr", p -> p.setAttribute("DEFENCE"))));
        double viaLiteral = shieldAfter(literal(defence));

        Assertions.assertEquals(viaLiteral, viaProvider, 1e-9,
                "{\"type\": \"attr\", \"attribute\": \"DEFENCE\"} must be the number the literal states");
        Assertions.assertEquals(defence, viaProvider, 1e-6, "and that number is her DEFENCE");
    }

    /**
     * {@code above} keeps only the excess, the {@code self_attr_above:} reading.
     */
    @Test
    public void attrAboveCountsOnlyTheExcess() {
        double defence = CharacterFactory.create(OWNER, LEVEL).getAttribute(AttributeType.DEFENCE).get();

        double shield = shieldAfter(shielding(provider("attr", p -> {
            p.setAttribute("DEFENCE");
            p.setAbove(defence - 100);
        })));

        Assertions.assertEquals(100, shield, 1e-6, "DEFENCE above \"DEFENCE - 100\" is 100");
    }

    /**
     * {@code stacks} reads a counter, after the same rule has raised it - the order inside one rule is the order the
     * effects are written.
     */
    @Test
    public void aStacksProviderReadsTheCounter() {
        EffectSpec raise = new EffectSpec();
        TriggerSpecs.set(raise, "op", "ADD_STACK");
        TriggerSpecs.set(raise, "buff", "充能");
        TriggerSpecs.set(raise, "amount", 3.0);
        TriggerSpecs.set(raise, "maxStacks", 5);
        TriggerSpecs.set(raise, "permanent", true);
        TriggerSpecs.set(raise, "target", "self");

        EffectSpec gain = new EffectSpec();
        TriggerSpecs.set(gain, "op", "GAIN_ENERGY");
        TriggerSpecs.set(gain, "amountFrom", provider("stacks", p -> p.setBuff("充能")));
        TriggerSpecs.set(gain, "target", "self");

        double energy = energyAfter(TriggerSpecs.rule("BATTLE_START", null, raise, gain));
        Assertions.assertEquals(3, energy, 1e-9, "3 stacks must grant exactly 3 energy");
    }

    /**
     * {@code uniform} stays inside its interval, and a seeded battle reproduces its draw.
     */
    @Test
    public void aUniformProviderDrawsInsideTheIntervalAndReproduces() {
        double low = 4;
        double high = 9;
        EffectSpec effect = shielding(provider("uniform", p -> {
            p.setMin(constant(low));
            p.setMax(constant(high));
        }));

        double first = shieldAfter(effect, BattleFixture.seeded());
        double second = shieldAfter(effect, BattleFixture.seeded());

        Assertions.assertTrue(first >= low && first < high,
                "a uniform draw must land in [" + low + ", " + high + "), was " + first);
        Assertions.assertEquals(first, second, 1e-12,
                "the same seed must draw the same number, or a replay stops reproducing");
    }

    /**
     * A zero-weight entry is never picked: with weights 1 and 0 the answer is always the first.
     */
    @Test
    public void aWeightedListSkipsZeroWeightEntries() {
        EffectSpec effect = shielding(provider("weighted_list", p -> {
            NumberProvider.Weighted picked = new NumberProvider.Weighted();
            picked.setWeight(1.0);
            picked.setValue(constant(7));
            NumberProvider.Weighted never = new NumberProvider.Weighted();
            never.setWeight(0.0);
            never.setValue(constant(3));
            p.setDistribution(List.of(picked, never));
        }));

        // Note: across SEEDS, not one fixed roll: with a single `alwaysRoll(0.0)` the first eligible entry always wins,
        // so an implementation that treated weight 0 as "live" would pass. Every seed must still answer 7.
        for (int seed = 0; seed < 32; seed++) {
            Assertions.assertEquals(7, shieldAfter(effect, new java.util.Random(seed)), 1e-9,
                    "the zero-weight entry must never win (seed " + seed + ")");
        }
    }

    /**
     * The new types are refused at load when their required fields are missing.
     */
    @Test
    public void theNewTypesAreRefusedWhenTheirFieldsAreMissing() {
        assertRefused(provider("attr", p -> { }), "attribute");
        assertRefused(provider("stacks", p -> { }), "buff");
        assertRefused(provider("uniform", p -> p.setMin(constant(1))), "max");
        assertRefused(provider("weighted_list", p -> p.setDistribution(List.of())), "distribution");
    }

    // --- fixtures ---------------------------------------------------------------------------------------------------

    private static void assertRefused(NumberProvider provider, String missing) {
        IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(TriggerSpecs.rule("BATTLE_START", null, shielding(provider)))),
                "a provider missing \"" + missing + "\" must be refused at load");
        Assertions.assertTrue(error.getMessage().contains(missing),
                "the message names the missing field: " + error.getMessage());
    }

    private static double shieldAfter(EffectSpec effect) {
        return shieldAfter(TriggerSpecs.rule("BATTLE_START", null, effect), BattleFixture.seeded());
    }

    private static double shieldAfter(EffectSpec effect, java.util.Random random) {
        return shieldAfter(TriggerSpecs.rule("BATTLE_START", null, effect), random);
    }

    private static double shieldAfter(TriggerSpec rule, java.util.Random random) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Battle battle = new Battle(List.of(owner), List.of(BattleFixture.monster()), random);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(rule)));
        battle.startBattle();
        return owner.getShield();
    }

    private static double energyAfter(TriggerSpec rule) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setCurrentEnergy(0);
        Battle battle = new Battle(List.of(owner), List.of(BattleFixture.monster()), BattleFixture.seeded());
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(rule)));
        battle.startBattle();
        return owner.getCurrentEnergy();
    }

    private static EffectSpec shielding(NumberProvider amount) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "SHIELD");
        TriggerSpecs.set(effect, "amountFrom", amount);
        TriggerSpecs.set(effect, "target", "self");
        return effect;
    }

    private static EffectSpec literal(double amount) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "SHIELD");
        TriggerSpecs.set(effect, "amount", amount);
        TriggerSpecs.set(effect, "target", "self");
        return effect;
    }

    private static NumberProvider provider(String type, java.util.function.Consumer<NumberProvider> fill) {
        NumberProvider provider = new NumberProvider();
        provider.setType(type);
        fill.accept(provider);
        return provider;
    }

    private static NumberProvider constant(double value) {
        NumberProvider provider = new NumberProvider();
        provider.setType("const");
        provider.setValue(value);
        return provider;
    }
}
