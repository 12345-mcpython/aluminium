package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.models.DoubleValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * A modifier can resolve its share at READ time.
 *
 * <p>The reader family is the largest registered one in GAPS: fourteen documents say "for every 1 stack the owner has, ...",
 * in sustained auras, where the number has to follow the stacks. A snapshot can only be right at the instant it is taken,
 * and re-attaching on every change would stack the buff itself.
 */
public class LiveModifierTest {
    /** The value follows the count, with the modifier attached once. */
    @Test
    public void aLiveModifierFollowsTheCount() {
        int[] stacks = {1};
        DoubleValue attack = new DoubleValue(100);
        attack.addModifier(DoubleValue.Modifier.livePercent(
                () -> 0.14 * stacks[0], DoubleValue.Modifier.ModifierSource.BUFF, 1));

        System.out.println("[live] stacks=1 -> " + attack.get());
        Assertions.assertEquals(114.0, attack.get(), 1e-9, "one stack is 14%");

        stacks[0] = 5;
        System.out.println("[live] stacks=5 -> " + attack.get());
        Assertions.assertEquals(170.0, attack.get(), 1e-9, "five stacks are 70%, without re-attaching anything");

        stacks[0] = 0;
        Assertions.assertEquals(100.0, attack.get(), 1e-9, "and back to the base when the stacks are gone");
    }

    /** Control: a stored modifier is a snapshot, which is what the family cannot use. */
    @Test
    public void aStoredModifierDoesNotFollow() {
        int[] stacks = {1};
        DoubleValue attack = new DoubleValue(100);
        attack.addModifier(DoubleValue.Modifier.addPercent(
                0.14 * stacks[0], DoubleValue.Modifier.ModifierSource.BUFF));

        stacks[0] = 5;
        Assertions.assertEquals(114.0, attack.get(), 1e-9,
                "the stored one stays where it was taken -- the documented reason a new kind was needed");
    }
}
