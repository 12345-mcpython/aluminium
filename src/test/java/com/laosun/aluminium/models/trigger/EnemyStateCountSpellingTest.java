package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.TriggerSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The load-time half of {@code enemy_state_count:} - a spelling with no state after it is refused when the
 * table is READ, not silently accepted as a rule that can never fire.
 *
 * <p>⚠ Why this lives in the engine's own package: {@code TriggerConditionParser.parseCondition} is
 * package-private on purpose (the DSL's entry point is the table loader), and the alternative - writing a
 * character whose file is deliberately broken - would put a permanently red rule in the shipped corpus. This is
 * the one shape that can only be exercised on a hand-built spec.
 *
 * <p>⚠ What is NOT checked here, and must not be: whether the STATE NAME exists. The engine does not own that
 * vocabulary (content does), so guessing would refuse names a character legitimately declares in its own file.
 * {@code has_state} draws exactly the same line, and these two spellings must not diverge - the runtime test
 * {@code EnemyStateCountTest} is the other half.
 */
public class EnemyStateCountSpellingTest {

    private static TriggerSpec spec() {
        return new TriggerSpec();
    }

    /**
     * An empty state name is a rule that can never fire, so it is refused at load time.
     */
    @Test
    public void anEmptyStateNameIsRefused() {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> TriggerConditionParser.parseCondition("enemy_state_count: >= 1", spec()),
                "「enemy_state_count:」 with nothing after it has no meaning; accepting it would ship a guard "
                        + "that is permanently false");
        Assertions.assertTrue(thrown.getMessage().contains("enemy_state_count"),
                "the message must name the spelling so the author can find it: " + thrown.getMessage());
    }

    /**
     * A well-formed spelling is ACCEPTED, and is a numeric variable (so it can stand on either side of a
     * comparison, like every other count).
     *
     * <p>⚠ Asserted on the parse, not on a battle: what this case answers is "the loader knows this word",
     * which is the part that would otherwise fail at start-up on a character that ships.
     *
     * <p>⚠ The second assertion is a TWO-VARIABLE comparison on purpose: that branch only runs for a token the
     * parser recognises as a numeric variable, so it proves the recognition without widening
     * {@code isNumericVariable} to package visibility just for a test.
     */
    @Test
    public void aWellFormedSpellingParses() {
        Assertions.assertNotNull(TriggerConditionParser.parseCondition("enemy_state_count:弱点击破 >= 1", spec()),
                "the spelling the corpus now uses must be readable");
        Assertions.assertNotNull(
                TriggerConditionParser.parseCondition("enemy_state_count:弱点击破 >= enemy_count", spec()),
                "it is a count, so it must be usable opposite another count (the two-variable branch reads "
                        + "both sides as numeric variables)");
    }

    /**
     * The message for an unknown variable teaches the new spelling, so the next author does not have to find
     * this class to learn it.
     */
    @Test
    public void theUnknownVariableMessageMentionsIt() {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> TriggerConditionParser.parseCondition("enemies_broken >= 1", spec()));
        Assertions.assertTrue(thrown.getMessage().contains("enemy_state_count"),
                "the 'known numeric variables' message must list the vocabulary, this spelling included: "
                        + thrown.getMessage());
    }
}
