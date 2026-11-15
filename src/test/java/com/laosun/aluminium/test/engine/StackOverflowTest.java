package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
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
 * {@code ADD_STACK}'s {@code overflow_to} / {@code overflow_max}: 「雪衣会<b>累计溢出的【恶报】层数</b>，最多累计 6 层」.
 *
 * <p><b>Why a field on the op.</b> The overflow happens INSIDE the per-layer grant loop, at the moment the counter is
 * full - a second op could not see it without replaying the accounting. When the main counter cannot take a layer, the
 * layer goes to the bank instead of being dropped; the bank has its own ceiling.
 */
public class StackOverflowTest {
    private static final int WEARER = 1214;
    private static final int LEVEL = 80;

    /**
     * A gain larger than the remaining room: what fits stays, the rest is banked.
     */
    @Test
    public void theExcessGoesToTheBank() {
        Run run = new Run(6, 5);              // 6 of 8 already, then gain 5 -> 2 fit, 3 banked
        Assertions.assertEquals(8, run.wearer.getBuffManager().stacksOf("char:1214/retribution"), "the counter is full");
        Assertions.assertEquals(3, run.wearer.getBuffManager().stacksOf("恶报溢出"),
                "the three layers the counter could not take were kept");
    }

    /**
     * The bank has its own ceiling: 「最多累计 6 层」.
     */
    @Test
    public void theBankStopsAtItsOwnCap() {
        Run run = new Run(7, 20);             // room for 1, bank ceiling 6 -> 1 fits, 6 banked, the rest dropped
        Assertions.assertEquals(8, run.wearer.getBuffManager().stacksOf("char:1214/retribution"));
        Assertions.assertEquals(6, run.wearer.getBuffManager().stacksOf("恶报溢出"),
                "the bank stops at six even though nineteen layers were offered");
    }

    /**
     * Control: without {@code overflow_to} the excess is dropped, exactly as before this field existed.
     */
    @Test
    public void withoutTheFieldTheExcessIsStillDropped() {
        Run run = new Run(7, 20, false);
        Assertions.assertEquals(8, run.wearer.getBuffManager().stacksOf("char:1214/retribution"));
        Assertions.assertEquals(0, run.wearer.getBuffManager().stacksOf("恶报溢出"), "nobody banked anything");
    }

    // ==================================================================

    private static final class Run {
        private final Character wearer;
        private final Battle battle;

        Run(int start, int gain) {
            this(start, gain, true);
        }

        Run(int start, int gain, boolean overflow) {
            wearer = CharacterFactory.create(WEARER, LEVEL);
            Enemy enemy = BattleFixture.monster();
            battle = new Battle(List.of(wearer), List.of(enemy), BattleFixture.seeded());

            EffectSpec seed = stack(start, null, null);
            EffectSpec grant = stack(gain, overflow ? "恶报溢出" : null, overflow ? 6 : null);
            wearer.setTriggerTable(new TriggerTable(WEARER, List.of(
                    TriggerSpecs.rule("BATTLE_START", List.of(), seed),
                    TriggerSpecs.rule("TURN_START", List.of(), grant))));
            battle.startBattle();
            battle.processRequests();
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, wearer, null, 0, 0));
            battle.processRequests();
        }

        private static EffectSpec stack(int amount, String overflowTo, Integer overflowMax) {
            EffectSpec effect = new EffectSpec();
            TriggerSpecs.set(effect, "op", "ADD_STACK");
            TriggerSpecs.set(effect, "buff", "char:1214/retribution");
            TriggerSpecs.set(effect, "amount", (double) amount);
            TriggerSpecs.set(effect, "maxStacks", 8);
            TriggerSpecs.set(effect, "permanent", true);
            TriggerSpecs.set(effect, "target", "self");
            if (overflowTo != null) {
                TriggerSpecs.set(effect, "overflowTo", overflowTo);
                TriggerSpecs.set(effect, "overflowMax", overflowMax);
            }
            return effect;
        }
    }
}
