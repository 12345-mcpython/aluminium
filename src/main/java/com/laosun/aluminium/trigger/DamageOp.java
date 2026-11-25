package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.enums.TriggerEvent;

import java.util.List;

/**
 * {@code DAMAGE}: settle one damage instance per victim, {@code times} over.
 *
 * <p>{@code times} repeats the <b>whole</b> settlement and the target is re-drawn inside the loop, which is what
 * "randomly a single enemy, each time" means. {@code split} makes the stated magnitude the <b>total</b>, divided among
 * the victims that can really take it - an invulnerable victim is counted (its share is simply not settled) but a
 * corpse is not, because counting it would make part of the total vanish with nothing to see.
 *
 * <p><b>Two ways to state the base</b>, and the check below is mostly about keeping them apart: a SKILL ROW
 * ({@code skill} + {@code damage_param}), or a LITERAL ratio ({@code scale} + {@code percent}, plus {@code element}
 * because no skill lends one). A literal ratio may also be a {@code parts} sum, the event's own amount, the settled
 * total, the original damage, or a share of a Max HP - each with its own requirement, and each refused beside the
 * others because two bases would mean the number twice.
 */
final class DamageOp implements OpHandler<DamageArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.DAMAGE;
    }

    @Override
    public DamageArgs parse(EffectSpec effect) {
        return new DamageArgs(
                effect.getAttacker(),
                effect.getBlast(),
                effect.getSkill(),
                effect.getElement(),
                effect.getElationFrom(),
                effect.getDamageType(),
                effect.getPercentPerAllyState(),
                effect.getAllyState(),
                effect.getAllyStates() == null ? null : java.util.List.copyOf(effect.getAllyStates()),
                Boolean.TRUE.equals(effect.getOrdinary()),
                effect.getStance(),
                Boolean.TRUE.equals(effect.getIgnoreWeakness()),
                effect.getCritRate(),
                effect.getCritDamage(),
                effect.getCastCategory(),
                Boolean.TRUE.equals(effect.getUnboosted()),
                effect.getCountedLaughs(),
                Boolean.TRUE.equals(effect.getSplit()),
                Boolean.TRUE.equals(effect.getPerTarget()),
                effect.getTimes());
    }

    @Override
    public void validate(DamageArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        // `times` says "settle N independent times"; `per_target` says "multiply this one settlement by the event's
        // hit count". Together they have two readings, so the pair is refused.
        if (effect.getTimes() != null && effect.getTimes() < 1) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"times\" = " + effect.getTimes() + ", which settles nothing: "
                            + "it must be at least 1 (source: " + rule.getSource() + ")");
        }
        if (effect.getTimes() != null && Boolean.TRUE.equals(effect.getPerTarget())) {
            throw new IllegalArgumentException(
                    "Op " + op + " cannot combine \"times\" with \"per_target\": one repeats the whole "
                            + "settlement and the other multiplies a single one, so the pair has two "
                            + "readings (source: " + rule.getSource() + ")");
        }
        // "造成 90% 的虚数属性欢愉伤害，由敌方全体均分": the stated magnitude is the TOTAL, divided among the victims.
        if (Boolean.TRUE.equals(effect.getSplit())) {
            if (Boolean.TRUE.equals(effect.getPerTarget())) {
                throw new IllegalArgumentException(
                        "Op " + op + " states both \"split\" and \"per_target\": one divides the stated total "
                                + "among the victims and the other multiplies it by how many were hit, so the "
                                + "pair has two readings (source: " + rule.getSource() + ")");
            }
            String splitTarget = TriggerTargets.normalizeTarget(effect);
            if (!TriggerVocabulary.TARGET_ALL_ENEMIES.equals(splitTarget)) {
                throw new IllegalArgumentException(
                        "Op " + op + " states \"split\" with target \"" + splitTarget + "\": the divisor IS how "
                                + "many units the effect reaches, so the field only says something for a "
                                + "selector that reaches several - write \"" + TriggerVocabulary.TARGET_ALL_ENEMIES
                                + "\" (source: " + rule.getSource() + ")");
            }
        }
        if (effect.getSkill() == null || effect.getSkill().isBlank()) {
            String literalScale = effect.getScale() == null ? "" : effect.getScale().trim();
            if (effect.getParts() != null && !effect.getParts().isEmpty()) {
                // 「100% 攻击力 + 150% 防御力」: several base terms summed into ONE instance. The rest of this chain
                // describes a SINGLE base, so the two spellings are mutually exclusive; every term faces the same
                // requirement a lone base would (a scale plus a share).
                if (!literalScale.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Op " + op + " states both \"parts\" and \"scale\": the parts ARE the base "
                                    + "(source: " + rule.getSource() + ")");
                }
                for (EffectSpec.Part part : effect.getParts()) {
                    if (part.getScale() == null || part.getScale().isBlank()) {
                        throw new IllegalArgumentException(
                                "Op " + op + " has a part with no \"scale\": every term needs the base it is a share of "
                                        + "(source: " + rule.getSource() + ")");
                    }
                    if (part.getPercent() == null && part.getPercentFromSkillParam() == null) {
                        throw new IllegalArgumentException(
                                "Op " + op + " has a part stating no share: give it \"percent\" or "
                                        + "\"percent_from_skill_param\" (source: " + rule.getSource() + ")");
                    }
                }
                TriggerValidator.requireElement(effect, op, rule, false);
            } else if (Boolean.TRUE.equals(effect.getAmountFromEvent())) {
                // 「等同于本次攻击总伤害值 #1% 的真实伤害」: the base is the number the event ANNOUNCES, and only one
                // event announces an attack's settled total. It is `amount_from_event` (+ `percent`), NOT
                // `scale: "event_amount"` - that spelling was withdrawn as a duplicate; what was missing is this reader.
                TriggerValidator.requireEvent(rule, op, TriggerEvent.ATTACK_FINISHED);
                if (!literalScale.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Op " + op + " states both \"amount_from_event\" and \"scale\": the event's amount "
                                    + "and a scale are two different bases (source: " + rule.getSource() + ")");
                }
                TriggerValidator.requirePercent(effect, op, rule);
                TriggerValidator.requireElement(effect, op, rule, true);
                // The announced total has already been through every zone: a rider that settles through them again
                // multiplies them a second time, and a sum has no single factor to divide by.
                TriggerValidator.requireTrueDamage(effect, op, rule);
            } else if (TriggerInterpreter.SETTLED_DAMAGE_THIS_EVENT.equals(literalScale)) {
                // "equal to X% of the TOTAL damage value of this one": the share is taken of the SETTLED total, which
                // has already been through every zone. A rider settled through the zones again would multiply them a
                // second time, and a SUM over several instances has no single factor to divide by - so the only honest
                // reader of this scale is true damage.
                TriggerValidator.requirePercent(effect, op, rule);
                TriggerValidator.requireElement(effect, op, rule, true);
                TriggerValidator.requireTrueDamage(effect, op, rule);
            } else if ("original_damage".equals(literalScale)) {
                TriggerValidator.requireEvent(rule, op, TriggerEvent.DAMAGE_SETTLED);
                TriggerValidator.requirePercent(effect, op, rule);
                TriggerValidator.requireElement(effect, op, rule, true);
            } else {
                boolean maxHpShare = "owner_max_hp".equals(literalScale) || "target_max_hp".equals(literalScale)
                        || "actor_max_hp".equals(literalScale);
                if (maxHpShare) {
                    TriggerValidator.requirePercent(effect, op, rule);
                } else {
                    // `true` = 「原伤害的 50%」: `percent` may be a FRACTION of the one row share stated beside it. It is
                    // the damage path's own reading; every other op keeps the "exactly one share" refusal.
                    TriggerValidator.requireDerivedScale(effect, op, rule, false, true);
                }
                TriggerValidator.requireElement(effect, op, rule, true);
            }
        } else {
            TriggerValidator.requireSkill(effect, op, rule);
            TriggerValidator.requireDamageParam(effect, op, rule);
        }
        TriggerValidator.requireNoStackArguments(effect, op, rule);
        TriggerValidator.requireFixedCrit(effect, op, rule);
        // 「该次欢愉伤害使用【美学底本】的属性值计算」: there is no Elation formula to redirect unless this IS Elation
        // damage, and the base it borrows is the table's row for the HOLDER's level - so the pair is required.
        if (effect.getElationFrom() != null) {
            if (effect.getElationFrom().isBlank()) {
                throw new IllegalArgumentException(
                        "Op " + op + " states a blank \"elation_from\": name the state whose holder the "
                                + "un-underlined terms come from (source: " + rule.getSource() + ")");
            }
            if (!"ELATION".equalsIgnoreCase(
                    effect.getDamageType() == null ? "" : effect.getDamageType().trim())) {
                throw new IllegalArgumentException(
                        "Op " + op + " states \"elation_from\" without \"damage_type\": \"ELATION\": the Elation "
                                + "formula is the only thing whose terms can be borrowed, so the field says "
                                + "nothing here (source: " + rule.getSource() + ")");
            }
            if (!"elation_base".equals(effect.getScale() == null ? "" : effect.getScale().trim())) {
                throw new IllegalArgumentException(
                        "Op " + op + " states \"elation_from\" with scale \""
                                + (effect.getScale() == null ? "(none)" : effect.getScale())
                                + "\": the borrowed base IS the level table's row, so the scale must be "
                                + "\"elation_base\" (source: " + rule.getSource() + ")");
            }
        }
    }

    @Override
    public void apply(OpContext ctx, DamageArgs args) {
        // `times` may be a provider ("once per stack") or the event's hit count, so the count itself is still resolved at
        // fire time; only what the sentence STATES is in the arguments.
        int times = TriggerInterpreter.timesCount(ctx.battle(), ctx.effect(), ctx.firing());
        if (times <= 0) {
            return;
        }
        for (int repeat = 0; repeat < times; repeat++) {
            List<CanHit> victims = ctx.targets();
            int divisor = 1;
            if (args.split()) {
                divisor = 0;
                for (CanHit victim : victims) {
                    if (victim != null && !victim.isDeath()) {
                        divisor++;
                    }
                }
                divisor = Math.max(1, divisor);
            }
            for (CanHit victim : victims) {
                TriggerInterpreter.damage(args, ctx.effect(), ctx.battle(), ctx.firing(), victim, divisor);
            }
        }
    }
}
