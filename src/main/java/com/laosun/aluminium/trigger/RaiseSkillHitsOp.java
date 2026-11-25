package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.CanHit;

/**
 * {@code RAISE_SKILL_HITS}: add damage segments to a data slot, for the rest of the battle.
 *
 * <p>The DATA SLOT, not a {@code SkillType} letter: the executor knows the slot number. A negative amount takes the
 * segments back, which is how a rule states a duration.
 *
 * <p>The effect's own helper was inlined here when its arguments moved into {@link RaiseSkillHitsArgs}: a helper that
 * takes the bean had to be skipped over on every firing, and what it held was two decisions (which slot, how many)
 * plus a loop over {@code ctx.targets()}.
 */
final class RaiseSkillHitsOp implements OpHandler<RaiseSkillHitsArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RAISE_SKILL_HITS;
    }

    @Override
    public RaiseSkillHitsArgs parse(EffectSpec effect) {
        return new RaiseSkillHitsArgs(effect.getSkillId() == null ? 0 : effect.getSkillId(), effect);
    }

    @Override
    public void validate(RaiseSkillHitsArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        if (args.slot() <= 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " needs \"skill_id\": the data slot whose hit count changes (source: "
                            + rule.getSource() + ")");
        }
        TriggerValidator.requireAmount(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, RaiseSkillHitsArgs args) {
        CanHit owner = ctx.firing().owner();
        if (owner == null) {
            throw new IllegalStateException(
                    "RAISE_SKILL_HITS ran without a rule owner, so there is no combatant whose hits to raise; this is "
                            + "an engine fault");
        }
        Magnitude magnitude = args.magnitude();
        int amount = magnitude.getValue() != null
                ? (int) Math.round(TriggerMagnitudes.valueOf(magnitude, ctx.firing()))
                : magnitude.getAmount() == null ? 0 : (int) Math.round(magnitude.getAmount());
        for (CanHit target : ctx.targets()) {
            target.raiseSkillHits(args.slot(), amount);
        }
    }
}
