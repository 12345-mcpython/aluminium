package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.Character;
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
 * {@code times} / {@code times_from} repeat the <b>effect</b>, not only a damage settlement.
 *
 * <p><b>Why this file exists.</b> Until 2026-10-31 the repeat count was read inside the {@code DAMAGE} branch only, so
 * the same field on any other op was silently ignored - and the clause that needed it most ("for each enemy hit, gain 1
 * stack", Argenti's talent) counted one stack for a three-target cast. The reader is still the same number: the effect
 * loop in {@code TriggerInterpreter.applyUnchecked} repeats a non-{@code DAMAGE} effect, while {@code DAMAGE} keeps its
 * own loop (it re-draws a random target per repetition).
 *
 * <p><b>The control half.</b> Each positive case below is paired with one where the same clause has no count, so a
 * regression that always repeats or never repeats is red on one side.
 */
public class EffectTimesTest {

    private static final int OWNER = 1002;
    private static final int LEVEL = 80;
    private static final String STATE = "probeTimes";

    /**
     * {@code times: 3} on a stack effect applies three instances.
     */
    @Test
    public void timesRepeatsAStackEffect() {
        Fixture f = new Fixture(effect("ADD_STACK", "buff", STATE, "amount", 1.0,
                "maxStacks", 99, "permanent", Boolean.TRUE, "target", "self", "times", 3));

        Assertions.assertEquals(3, f.owner.getBuffManager().stacksOf(STATE),
                "「this effect happens 3 times」: three instances of the named stack, not one");
    }

    /**
     * The control: without {@code times} the same effect applies once.
     */
    @Test
    public void withoutTimesTheEffectAppliesOnce() {
        Fixture f = new Fixture(effect("ADD_STACK", "buff", STATE, "amount", 1.0,
                "maxStacks", 99, "permanent", Boolean.TRUE, "target", "self"));

        Assertions.assertEquals(1, f.owner.getBuffManager().stacksOf(STATE),
                "no count means one application - the field is opt-in, not a default of N");
    }

    /**
     * {@code times_from: "hit_count"} follows the attack: three targets hit give three applications.
     */
    @Test
    public void timesFromHitCountFollowsTheAttack() {
        Fixture f = new Fixture(effect("ADD_STACK", "buff", STATE, "amount", 1.0,
                "maxStacks", 99, "permanent", Boolean.TRUE, "target", "self", "timesFrom", "hit_count"));

        f.fire(3);
        Assertions.assertEquals(3, f.owner.getBuffManager().stacksOf(STATE),
                "「每击中 1 个敌方目标，获得 1 层」: an AoE that hits three targets grants three");

        f.fire(1);
        Assertions.assertEquals(4, f.owner.getBuffManager().stacksOf(STATE),
                "and a single-target hit grants one more");
    }

    /**
     * The control: a zero hit count repeats nothing (the same clause on a firing that hit nobody).
     */
    @Test
    public void zeroHitsApplyNothing() {
        Fixture f = new Fixture(effect("ADD_STACK", "buff", STATE, "amount", 1.0,
                "maxStacks", 99, "permanent", Boolean.TRUE, "target", "self", "timesFrom", "hit_count"));

        f.fire(0);
        Assertions.assertEquals(0, f.owner.getBuffManager().stacksOf(STATE),
                "no target hit means no stack: the sentence's subject is the target that was hit");
    }

    /**
     * {@code times: 0} is refused at load: an effect that runs zero times would vanish without a word.
     */
    @Test
    public void zeroTimesIsRefusedAtLoad() {
        EffectSpec stack = effect("ADD_STACK", "buff", STATE, "amount", 1.0,
                "maxStacks", 99, "permanent", Boolean.TRUE, "target", "self", "times", 0);
        TriggerSpec rule = TriggerSpecs.rule("BATTLE_START", List.of(), stack);

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(rule)));
        Assertions.assertTrue(refused.getMessage().contains("times"), refused.getMessage());
    }

    // ==================================================================

    /**
     * One battle with a two-event table: the start rule proves the plain count, the attack rule proves the event-fed
     * count on the same effect shape.
     */
    private static final class Fixture {
        private final Character owner;
        private final Battle battle;

        private Fixture(EffectSpec effect) {
            TriggerSpec start = TriggerSpecs.rule("BATTLE_START", List.of(), effect);
            TriggerSpec attack = TriggerSpecs.rule("BASIC_ATTACK", List.of("actor == self"), effect);
            TriggerSpecs.set(attack, "id", "times_on_hits");
            owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
            owner.setTriggerTable(new TriggerTable(OWNER, List.of(start, attack)));
            battle = new Battle(List.of(owner), List.of(BattleFixture.monster()), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        private void fire(int hits) {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.BASIC_ATTACK, owner, battle.enemies().getFirst(), hits, 0));
        }
    }

    private static EffectSpec effect(String op, Object... pairs) {
        EffectSpec spec = new EffectSpec();
        TriggerSpecs.set(spec, "op", op);
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i + 1] != null) {
                TriggerSpecs.set(spec, (String) pairs[i], pairs[i + 1]);
            }
        }
        return spec;
    }
}




