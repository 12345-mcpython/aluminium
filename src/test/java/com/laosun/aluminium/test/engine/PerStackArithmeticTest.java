package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * "For each layer, +5%": the share is a percentage of the attribute's <b>base</b>, and layers add.
 *
 * <p><b>Why this file exists.</b> The ledger carried an open item that read: "two layers only reach x1.078125, the
 * sentence wants x1.10". Measured here, the engine was right and the expectation was wrong: an ATTACK% share is
 * <b>additive on the base</b> (total = base x (1 + other shares + 0.05 x layers)), so on a unit that already carries
 * another share the <i>ratio of totals</i> is necessarily below 1.10. With the +28% the old probe must have carried,
 * (1.28 + 0.10) / 1.28 = 1.078125 - the reported number, exactly. The lesson is the ledger's own: compare the
 * <b>delta in base units</b>, never the ratio of two totals.
 *
 * <p>The fixture therefore asserts deltas, and its premise is stated out loud: the unit must already carry another ATK
 * share, or the test could not tell "share of base" from "share of the current total".
 */
public class PerStackArithmeticTest {

    private static final int OWNER = 1002;
    private static final int LEVEL = 80;
    private static final String LAYER = "探针层";
    private static final double SHARE = 0.05;

    /**
     * One layer adds 5% of the base, two add 10%, three add 15% - and they add rather than compound.
     */
    @Test
    public void eachLayerIsAPercentOfTheBaseAndLayersAdd() {
        Fixture f = new Fixture(false);
        double base = f.base();
        double start = f.attack();

        Assertions.assertTrue(start > base,
                "fixture premise: the unit already carries another ATK share, so a ratio-of-totals expectation would be "
                        + "wrong (base=" + base + ", start=" + start + ")");

        f.fire();
        Assertions.assertEquals(SHARE * base, f.attack() - start, 1e-9,
                "1 layer: +5% of the BASE");
        f.fire();
        Assertions.assertEquals(2 * SHARE * base, f.attack() - start, 1e-9,
                "2 layers: +10% of the base - a second layer ADDS another 5%, it does not multiply the first");
        f.fire();
        Assertions.assertEquals(3 * SHARE * base, f.attack() - start, 1e-9,
                "3 layers: +15% of the base");
        Assertions.assertEquals(3, f.owner.getBuffManager().stacksOf(LAYER), "three layers are on the unit");
    }

    /**
     * The live form ({@code per_stack_live}) reads the counter on every attribute read and lands on the same numbers.
     */
    @Test
    public void theLiveFormFollowsTheSameCounter() {
        Fixture f = new Fixture(true);
        double base = f.base();
        double start = f.attack();

        for (int layer = 1; layer <= 3; layer++) {
            f.fire();
            Assertions.assertEquals(layer * SHARE * base, f.attack() - start, 1e-9,
                    "live form, " + layer + " layer(s): +" + (layer * 5) + "% of the base");
        }
    }

    /**
     * The control: no layer means no share (a per_stack modifier does not silently add one layer).
     */
    @Test
    public void noLayersAddNothing() {
        Fixture f = new Fixture(false);
        double start = f.attack();
        Assertions.assertEquals(start, f.attack(), 1e-9, "nothing fired, nothing added");
        Assertions.assertEquals(0, f.owner.getBuffManager().stacksOf(LAYER));
    }

    // ==================================================================

    private static final class Fixture {
        private final Character owner;
        private final Battle battle;

        private Fixture(boolean live) {
            owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
            EffectSpec stack = new EffectSpec();
            TriggerSpecs.set(stack, "op", "ADD_STACK");
            TriggerSpecs.set(stack, "buff", LAYER);
            TriggerSpecs.set(stack, "amount", 1.0);
            TriggerSpecs.set(stack, "maxStacks", 99);
            TriggerSpecs.set(stack, "permanent", Boolean.TRUE);
            TriggerSpecs.set(stack, "target", "self");

            EffectSpec attr = new EffectSpec();
            TriggerSpecs.set(attr, "op", "MODIFY_ATTR");
            TriggerSpecs.set(attr, "attribute", "ATTACK");
            TriggerSpecs.set(attr, "percent", SHARE);
            TriggerSpecs.set(attr, "perStack", "self_stacks:" + LAYER);
            TriggerSpecs.set(attr, "perStackLive", live ? Boolean.TRUE : null);
            TriggerSpecs.set(attr, "permanent", Boolean.TRUE);
            TriggerSpecs.set(attr, "target", "self");

            TriggerSpec rule = TriggerSpecs.rule("BASIC_ATTACK", List.of("actor == self"), stack, attr);
            TriggerSpecs.set(rule, "id", "per_stack_probe");
            owner.setTriggerTable(new TriggerTable(OWNER, List.of(rule)));
            battle = new Battle(List.of(owner, CharacterFactory.create(1001, LEVEL)),
                    List.of(BattleFixture.monster()), BattleFixture.seeded());
            battle.startBattle();
        }

        private double base() {
            return owner.getAttribute(AttributeType.ATTACK).baseValue();
        }

        private double attack() {
            return owner.getAttribute(AttributeType.ATTACK).get();
        }

        private void fire() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.BASIC_ATTACK, owner,
                    battle.enemies().getFirst(), 1, 0));
        }
    }
}
