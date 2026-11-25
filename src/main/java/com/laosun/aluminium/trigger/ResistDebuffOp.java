package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.buff.AbstractBuff;
import com.laosun.aluminium.buff.ClassResistBuff;
import com.laosun.aluminium.buff.DebuffShieldBuff;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.enums.DebuffClass;

/**
 * {@code RESIST_DEBUFF}: raise one unit's resistance to a class of negative effect, or install a charge shield.
 *
 * <p>Two shapes. A <b>charge shield</b> ({@code charges} + {@code cooldown}) has no percentage and no duration of its
 * own - the cooldown is its clock - while a class resistance is a buff on the carrier (timed, or permanent for a
 * trace). A resistance of 0 is refused: it is a rule that provably does nothing.
 *
 * <p>The helper was inlined here when the arguments moved into {@link ResistDebuffArgs}: what it held was the
 * two-shape decision plus the buff construction. The lifetime and tick-owner anchors still come from the effect
 * ({@code until} / {@code ticks_on}), which is a different cluster and has not been parsed yet.
 */
final class ResistDebuffOp implements OpHandler<ResistDebuffArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RESIST_DEBUFF;
    }

    @Override
    public ResistDebuffArgs parse(EffectSpec effect) {
        boolean permanent = TriggerLifetimes.unticked(effect);
        return new ResistDebuffArgs(
                effect.getKind(),
                effect.getCharges(),
                effect.getCooldown(),
                effect.getTurns(),
                permanent,
                effect,
                effect.getBuff());
    }

    @Override
    public void validate(ResistDebuffArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        if (args.charges() != null) {
            if (args.charges() < 1) {
                throw new IllegalArgumentException(
                        "Op " + op + " has \"charges\": " + args.charges()
                                + ", but a charge shield must refuse at least one application (source: "
                                + rule.getSource() + ")");
            }
            if (args.cooldown() == null || args.cooldown() < 0) {
                throw new IllegalArgumentException(
                        "Op " + op + " states \"charges\" but no non-negative \"cooldown\" (the turns "
                                + "before the charge comes back) (source: " + rule.getSource() + ")");
            }
            TriggerValidator.requireNoStackArguments(effect, op, rule);
            return;
        }
        TriggerValidator.requireDebuffClass(effect, op, rule);
        TriggerValidator.requirePercent(effect, op, rule);
        TriggerValidator.requireValueFraction(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
        TriggerValidator.requireDuration(effect, op, rule);
        TriggerValidator.requireTickOwner(effect, op, rule);
        // `buff` is READ: the name the resistance carries, so "immune to control-class while in the [协奏] state" can
        // be taken off together with the state it belongs to. Everything else is refused.
        if (effect.getAmount() != null || effect.getScale() != null || effect.getAttribute() != null
                || effect.getSkill() != null
                || effect.getDamageParam() != null || effect.getElement() != null
                || effect.getControl() != null || effect.getBaseChance() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " takes a class (\"kind\") and a \"percent\", plus how long it lasts "
                            + "(and an optional \"buff\" name so a state's own effect can be removed with "
                            + "the state); it has no amount / scale / attribute / skill / element / control / "
                            + "base_chance (source: " + rule.getSource() + ")");
        }
        if (args.magnitude().getPercent() != null
                && (args.magnitude().getPercent() <= 0 || args.magnitude().getPercent() > 1)) {
            throw new IllegalArgumentException(
                    "Op " + op + " has \"percent\": " + args.magnitude().getPercent()
                            + ", but a class resistance is a fraction of 1 in (0, 1] (0.35 = \"raise the chance "
                            + "to resist ... by 35%\", 1.0 = \"immune\"); a resistance of 0 is a rule that provably "
                            + "does nothing (source: " + rule.getSource() + ")");
        }
    }

    @Override
    public void apply(OpContext ctx, ResistDebuffArgs args) {
        if (args.charges() != null) {
            // 「抵抗 1 次负面效果，2 回合后可再次触发」: a charge shield, not a percentage.
            int cooldown = args.cooldown() == null ? 0 : args.cooldown();
            for (CanHit target : ctx.targets()) {
                target.getBuffManager().addBuff(TriggerInterpreter.named(
                        TriggerInterpreter.withSource(new DebuffShieldBuff(args.charges(), cooldown), ctx.firing()),
                        args.buffName()));
            }
            return;
        }
        DebuffClass kind = DebuffClass.fromString(args.kind());
        // kind may be absent: RESIST_DEBUFF without a class means ANY negative state (1409's immunity clause).
        for (CanHit target : ctx.targets()) {
            // ⚠ The placeholder has to be substituted HERE, not in `parse`: a charge shield states no `turns` at all,
            // and a `?:` that mixes the int placeholder with the boxed field unboxes it - measured as an NPE at load.
            int turns = args.permanent()
                    ? TriggerEffects.UNBOUNDED_DURATION_PLACEHOLDER
                    : (args.turns() == null ? 0 : args.turns());
            AbstractBuff resist = TriggerInterpreter.withSource(TriggerLifetimes.withTickOwner(
                    TriggerLifetimes.withLifetime(
                            new ClassResistBuff(kind, TriggerMagnitudes.shareOf(args.magnitude(), ctx.firing()),
                                    turns, args.permanent()),
                            ctx.effect()),
                    ctx.effect(), ctx.firing()), ctx.firing());
            target.getBuffManager().addBuff(TriggerInterpreter.named(resist, args.buffName()));
        }
    }
}
