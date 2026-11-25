package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.CanHit;

import java.util.List;

/**
 * {@code REGISTER_HEAL_BONUS}: a heal bonus whose applicability is decided per heal, not per unit.
 *
 * <p>"When I heal a target below 30% HP" cannot be an attribute (one stat on the healer would raise every heal) and
 * cannot be a fire-time rule (no pre-heal event exists), so the condition rides on the effect's {@code target_when} and
 * is asked by {@code Battle.calculateHeal}.
 *
 * <p>The helper was inlined here when the arguments moved into {@link RegisterHealBonusArgs}: what it held was the
 * "no condition and no missing-HP scaling is a mistake" decision plus the registration loop, and both belong beside the
 * op they are about.
 */
final class RegisterHealBonusOp implements OpHandler<RegisterHealBonusArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.REGISTER_HEAL_BONUS;
    }

    @Override
    public RegisterHealBonusArgs parse(EffectSpec effect) {
        return new RegisterHealBonusArgs(effect, Boolean.TRUE.equals(effect.getScaleByMissingHp()));
    }

    @Override
    public void validate(RegisterHealBonusArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requirePercent(effect, op, rule);
        if (args.scaleByMissingHp()) {
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
    public void apply(OpContext ctx, RegisterHealBonusArgs args) {
        CanHit owner = ctx.firing().owner();
        if (owner == null) {
            return;
        }
        List<TriggerTable.Condition> conditions = ctx.firing().targetFilter();
        if ((conditions == null || conditions.isEmpty()) && !args.scaleByMissingHp()) {
            // A bonus with no condition would apply to every heal, which is exactly the wrong number this op exists to
            // avoid. Refused loudly rather than registered unconditionally.
            throw new IllegalStateException(
                    "REGISTER_HEAL_BONUS needs a target_when condition saying WHICH heals it applies to "
                            + "(an unconditional version would raise every heal, including on a full-HP ally)");
        }
        double percent = TriggerMagnitudes.statesShare(args.magnitude())
                ? TriggerMagnitudes.shareOf(args.magnitude(), ctx.firing())
                : 0;
        if (conditions == null || conditions.isEmpty()) {
            owner.addConditionalHealBonus(null, percent, args.scaleByMissingHp());
            return;
        }
        for (TriggerTable.Condition condition : conditions) {
            owner.addConditionalHealBonus(condition, percent, args.scaleByMissingHp());
        }
    }
}
