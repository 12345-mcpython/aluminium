package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The {@code eidolon} numeric condition: the owner's Eidolon rank, as a value a rule can compare.
 *
 * <p>Reader: 1503 Pearl (真珠)'s Eidolon 4, whose doubling clause carries the SAME conditions as the clause it doubles.
 * The rule-level {@code min_eidolon} gates a whole rule and that is usually enough, but not when an Eidolon clause
 * MODIFIES an effect the base kit performs on the same event: the two rules then carry identical conditions and
 * effects and differ only in a rule-level field, and {@code ContentDuplicateGuardTest} refused exactly that as "one
 * sentence written twice" - measured, not imagined. So the rank became readable inside a clause.
 *
 * <p>Boundaries are the point of a threshold, so the readings are the ranks either side of 3: a case that only checked
 * "fired at 6" would pass for an implementation that never fires below 6, and one that only checked "did not fire at 0"
 * would pass for one that fires at 3 where the clause says 4.
 */
public class EidolonConditionTest {
    private static final int PEARL = 1503;
    private static final int ALLY = 1204;
    private static final String MARK = "星魂门标记";

    /**
     * A clause gated on the rank fires at and above it, and not below.
     */
    @Test
    public void theConditionHoldsAtAndAboveTheRank() {
        for (int rank : new int[]{2, 3, 4, 5}) {
            int mark = markerAt(rank, "eidolon >= 3");
            System.out.println("[eidolon_condition] rank " + rank + " with \"eidolon >= 3\": mark " + mark);
            if (rank >= 3) {
                Assertions.assertEquals(1, mark, "the condition holds at rank " + rank);
            } else {
                Assertions.assertEquals(0, mark, "and does not hold at rank " + rank);
            }
        }
    }

    /**
     * It is a NUMBER, so the other comparisons work too - not a boolean "has that exact rank".
     */
    @Test
    public void theConditionCanCompareBothWays() {
        Assertions.assertEquals(1, markerAt(6, "eidolon == 6"), "== 6 holds at rank 6");
        Assertions.assertEquals(0, markerAt(5, "eidolon == 6"), "and not at rank 5");
        Assertions.assertEquals(1, markerAt(2, "eidolon <= 2"), "<= 2 holds at rank 2");
        Assertions.assertEquals(0, markerAt(3, "eidolon <= 2"), "and not at rank 3");
        System.out.println("[eidolon_condition] == and <= both answered");
    }

    /**
     * A unit with no Eidolons answers 0, which unlocks nothing.
     *
     * <p>Read on a summon, which is a combatant with no rank at all: the convention is the same one the rule-level gate
     * uses, so the two spellings of "I have no Eidolons" agree.
     */
    @Test
    public void aUnitWithNoEidolonsAnswersZero() {
        Assertions.assertEquals(0, markerAt(0, "eidolon >= 1"),
                "rank 0 does not satisfy >= 1, so nothing that needs an Eidolon can fire for it");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * The marked stacks a rank-{@code rank} Pearl ends up with, for a rule gated on {@code condition}.
     */
    private static int markerAt(int rank, String condition) {
        Character pearl = CharacterFactory.create(PEARL, 80, false, null, null, rank);
        Battle battle = new Battle(List.of(pearl, CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character her = battle.characters.getFirst();

        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "ADD_STACK");
        TriggerSpecs.set(effect, "buff", MARK);
        TriggerSpecs.set(effect, "amount", 1.0);
        TriggerSpecs.set(effect, "permanent", Boolean.TRUE);
        TriggerSpecs.set(effect, "target", "self");
        her.setTriggerTable(new TriggerTable(PEARL, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(condition), effect))));
        battle.fireTriggers(TriggerEvent.BATTLE_START, her, her, 0, 0);
        battle.processRequests();
        return her.getBuffManager().stacksOf(MARK);
    }
}
