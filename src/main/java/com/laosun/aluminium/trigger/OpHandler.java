package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * Everything the engine knows about <b>one</b> op: how to read it, how to refuse it, how to perform it.
 *
 * <p><b>Why this exists.</b> An op used to be one arm in a 1,320-line validation switch and another arm in a
 * 466-line dispatch switch, plus a row in two hand-written documentation tables - seven to nine places for one
 * decision, and the tables had already drifted (the interpreter's documented 19 of 63 ops, and its {@code DAMAGE}
 * row was contradicted by the validator). With one handler per op, the three questions about an op are answered in
 * one file, and the answers cannot disagree because {@link #validate} and {@link #apply} read the same
 * {@link OpArgs}.
 *
 * <p><b>The three phases, and why they are separate.</b>
 * <ul>
 *   <li>{@link #parse} runs while the table is compiled. It is the <b>only</b> reader of the op's spelling. It must
 *       be pure: no battlefield, no state, no rule - which is what makes it safe to call from a unit test, and what
 *       lets an amendment re-parse a copied effect without a rule in hand.</li>
 *   <li>{@link #validate} runs once, right after {@link #parse}, and is where a nonsense combination is refused
 *       <b>at load time</b>. It receives the rule so its message can quote {@code source}, which is the difference
 *       between "somewhere in this character" and a line the author can look at.</li>
 *   <li>{@link #apply} runs on every firing. It reads only {@link OpArgs} and {@link OpContext}.</li>
 * </ul>
 *
 * <p><b>What a handler is not.</b> It is not a place for a new engine capability. An op whose prerequisite phase
 * does not exist yet stays {@code wired: false} in {@link TriggerOperator} and is refused at load with the phase
 * named; the handler for a wired op performs what the engine already does.
 *
 * @param <A> the parsed shape this op needs
 */
interface OpHandler<A extends OpArgs> {

    /**
     * @return the op this handler performs
     */
    TriggerOperator op();

    /**
     * Reads the effect's spelling into the shape {@link #validate} and {@link #apply} share.
     *
     * <p>Pure on purpose: the same effect may be parsed twice (once at load, once more when {@code MODIFY_RULE}
     * hands a copy to the dispatcher), and a parse that read the battlefield would make the two answers differ.
     *
     * @param effect the effect as it was written
     * @return the parsed shape
     */
    A parse(EffectSpec effect);

    /**
     * Refuses an effect that could never work, naming the rule that wrote it.
     *
     * @param args   the parsed shape
     * @param effect the effect as it was written (for fields the shape deliberately does not copy)
     * @param rule   the rule it belongs to (for {@code source})
     */
    void validate(A args, EffectSpec effect, TriggerSpec rule);

    /**
     * Performs the op.
     *
     * @param ctx  the battlefield, the firing context and this effect
     * @param args the parsed shape
     */
    void apply(OpContext ctx, A args);
}
