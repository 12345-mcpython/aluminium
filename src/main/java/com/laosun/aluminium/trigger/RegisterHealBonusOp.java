package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code REGISTER_HEAL_BONUS}: a heal bonus whose applicability is decided per heal, not per unit.
 *
 * <p>The condition is the whole op: without one it would raise every heal the owner performs, including on a healthy
 * ally. A bonus <b>scaled by how hurt the recipient is</b> may omit it (at full HP it grants nothing anyway), and the
 * conditions themselves are parsed by {@code TriggerTable.compile}, which refuses a misspelling.
 */
final class RegisterHealBonusOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.REGISTER_HEAL_BONUS;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requirePercent(effect, op, rule);
        if (Boolean.TRUE.equals(effect.getScaleByMissingHp())) {
            return;
        }
        if (effect.getTargetWhen() == null || effect.getTargetWhen().isEmpty()) {
            throw new IllegalArgumentException(
                    "Op " + op + " needs a \"target_when\" saying which heals it applies to: the condition "
                            + "is the whole op, and an unconditional version would raise every heal, "
                            + "including on a full-HP ally (source: " + rule.getSource() + ")");
        }
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.registerHealBonus(ctx.effect(), ctx.firing());
    }
}
