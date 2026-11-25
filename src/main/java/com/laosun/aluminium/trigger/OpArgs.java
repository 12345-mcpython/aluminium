package com.laosun.aluminium.trigger;

/**
 * What one effect's op was parsed into, <b>once</b>, while its table was compiled.
 *
 * <p><b>Why an op needs a parsed form at all.</b> {@code EffectSpec} is a single bean with 139 optional fields, and
 * every op reads a different subset of them. Reading that subset at <em>fire</em> time means the same spelling is
 * interpreted again on every hit, and - worse - the load-time check ({@link OpHandler#validate}) and the run-time
 * read ({@link OpHandler#apply}) can disagree about what the fields mean. An op's args are the shape that both halves
 * see, so they cannot drift.
 *
 * <p><b>Why several ops return {@link NoArgs} for now.</b> Their whole vocabulary is a magnitude and a selector, and
 * both are resolved today by {@code TriggerMagnitudes} / {@code TriggerTargets} straight off the {@link
 * com.laosun.aluminium.beans.EffectSpec}. Those two resolvers are the next thing to be replaced by parsed value
 * objects; until then an args record for such an op would be an empty box with a comment claiming otherwise. The
 * migration order is deliberate: dispatch first (this class), the spelling second.
 */
public interface OpArgs {

    /** The answer for an op that states nothing beyond its target. */
    OpArgs NONE = new NoArgs();

    /**
     * An op with no arguments of its own.
     */
    record NoArgs() implements OpArgs {
    }
}
