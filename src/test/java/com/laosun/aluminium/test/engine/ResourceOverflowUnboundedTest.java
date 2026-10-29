package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.ResourceSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@code RAISE_RESOURCE_CAP} with {@code unbounded: true}: a sentence that <b>removes</b> a resource's overflow allowance
 * instead of widening it by a count.
 *
 * <p>Reader: 1408 Phainon (白厄)'s Eidolon 6 - 「【火种】的<b>可溢出点数不再拥有上限</b>」. A count cannot state "removed":
 * any number written there is a cap the sentence denies, and the one number that would work in practice
 * ({@code Integer.MAX_VALUE - 3}) overflows the allowance arithmetic itself on the way in, which
 * {@code setMaxOverflow} then clamps to 0 - "no limit" would read as "no overflow at all".
 *
 * <p>⚠ Both spellings are pinned on the same declaration (cap 5, overflow 2, so 7 without any rule): "no limit" lets 100
 * through, while a count of 3 lets exactly 3 more through. A case that only ever tested the flagged rule would pass just
 * as well if the flag did nothing at all.
 */
public class ResourceOverflowUnboundedTest {
    private static final int OWNER = 1003;
    private static final int LEVEL = 80;
    private static final String COUNTER = "counter";
    private static final int MAX = 5;
    private static final int OVERFLOW = 2;

    /**
     * With the flag: 100 points asked for, 100 points credited (the cap is the engine's own {@code Integer.MAX_VALUE}).
     */
    @Test
    public void unboundedOverflowCreditsEverything() {
        int credited = credited(100, true, null);
        System.out.println("[engine] unbounded overflow: asked 100, credited " + credited);
        Assertions.assertEquals(100, credited, "「不再拥有上限」⇒ 一点都不丢");
    }

    /**
     * And the count spelling still means what it meant: an allowance of 3 on top of the declared 2.
     */
    @Test
    public void aCountStillAddsUpInsteadOfRemovingTheAllowance() {
        int credited = credited(100, false, 3.0);
        System.out.println("[engine] declared overflow 2 + raised by 3: asked 100, credited " + credited);
        Assertions.assertEquals(MAX + OVERFLOW + 3, credited, "「再宽 3 点」仍然只到 max + overflow + 3");
    }

    // ==================================================================

    /**
     * How much of {@code amount} reaches the counter after the {@code BATTLE_START} rule ran.
     *
     * @param unbounded {@code true} to state {@code unbounded: true}
     * @param count     the flat {@code amount} to raise by, or {@code null} when {@code unbounded} is used
     */
    private static int credited(int amount, boolean unbounded, Double count) {
        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        // Note: `CharacterFactory.create` registers the resources of the table it BUILT, so a table installed afterwards
        // does not register anything by itself (measured: the raise then failed with "this unit neither declares nor has
        // it"). The declaration is registered by hand, which is also what makes its cap/overflow the config under test.
        owner.getResources().register(COUNTER, MAX, 0, OVERFLOW);
        EffectSpec raise = new EffectSpec();
        TriggerSpecs.set(raise, "op", "RAISE_RESOURCE_CAP");
        TriggerSpecs.set(raise, "resource", COUNTER);
        TriggerSpecs.set(raise, "target", "self");
        if (unbounded) {
            TriggerSpecs.set(raise, "unbounded", true);
        } else {
            TriggerSpecs.set(raise, "amount", count);
        }
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("BATTLE_START", List.of(), raise)),
                List.of(new ResourceSpec(COUNTER, MAX, 0, OVERFLOW, null, null, null))));
        Battle battle = new Battle(List.of(owner), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle.characters().getFirst().getResources().gain(COUNTER, amount);
    }
}
