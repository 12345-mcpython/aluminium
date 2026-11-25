package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;

/**
 * {@code RAISE_SKILL_LEVEL}: raise one of the owner's skill slots by N levels for this battle.
 *
 * <p>The level a skill is read at is character data plus this battle's raises, and one op raises it - never a second
 * copy of the rule at another level. Three ways to name the slot ({@code skill}, {@code skills}, and a memosprite's
 * {@code skill_id}) are mutually exclusive, because each names a different thing.
 */
final class RaiseSkillLevelOp implements OpHandler<RaiseSkillLevelArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RAISE_SKILL_LEVEL;
    }

    @Override
    public RaiseSkillLevelArgs parse(EffectSpec effect) {
        double delta = effect.getAmount() == null ? 0 : effect.getAmount();
        return new RaiseSkillLevelArgs(delta,
                Boolean.TRUE.equals(effect.getMemosprite()),
                effect.getSkillId(),
                effect.getSkills() == null ? null : java.util.List.copyOf(effect.getSkills()),
                effect.getSkill());
    }

    @Override
    public void validate(RaiseSkillLevelArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireEvent(rule, op, TriggerEvent.BATTLE_START);
        if (Boolean.TRUE.equals(effect.getMemosprite())) {
            // "忆灵技等级+1": the OWNER's memosprite, addressed by its DATA SLOT. Both halves are stated because each is
            // a different mistake: `skill` would name a character slot, and no `skill_id` would raise nothing at all.
            if (effect.getSkills() != null) {
                throw new IllegalArgumentException(
                        "Op " + op + " states \"memosprite\" and \"skills\": a memosprite's slots are data "
                                + "slots, stated one at a time as \"skill_id\"; \"skills\" lists character "
                                + "SkillType slots (source: " + rule.getSource() + ")");
            }
            if (effect.getSkill() != null && !effect.getSkill().isBlank()) {
                throw new IllegalArgumentException(
                        "Op " + op + " states both \"skill\" and \"memosprite\": a memosprite's skills are "
                                + "addressed by \"skill_id\" (their data slot), while \"skill\" names one of "
                                + "the CHARACTER's SkillType slots - which is a different skill "
                                + "(source: " + rule.getSource() + ")");
            }
            if (effect.getSkillId() == null || effect.getSkillId() <= 0) {
                throw new IllegalArgumentException(
                        "Op " + op + " states \"memosprite\" but no \"skill_id\": name the memosprite's DATA "
                                + "SLOT there (a positive number, e.g. 1) "
                                + "(source: " + rule.getSource() + ")");
            }
        } else if (effect.getSkills() != null) {
            if (effect.getSkill() != null && !effect.getSkill().isBlank()) {
                throw new IllegalArgumentException(
                        "Op " + op + " states both \"skill\" and \"skills\": state one slot as \"skill\" or "
                                + "several as \"skills\" (source: " + rule.getSource() + ")");
            }
            if (effect.getSkillId() != null) {
                throw new IllegalArgumentException(
                        "Op " + op + " states \"skill_id\" without \"memosprite\": that field names one of a "
                                + "memosprite's data slots; character slots are stated as \"skill\" or "
                                + "\"skills\" (source: " + rule.getSource() + ")");
            }
            TriggerValidator.requireSkillSlots(effect.getSkills(), op, rule);
        } else {
            if (effect.getSkillId() != null) {
                throw new IllegalArgumentException(
                        "Op " + op + " states \"skill_id\" without \"memosprite\": that field names one of a "
                                + "memosprite's data slots; a character's slot is stated as \"skill\" "
                                + "(a SkillType) (source: " + rule.getSource() + ")");
            }
            TriggerValidator.requireSkill(effect, op, rule);
        }
        double amount = effect.getAmount() == null ? 0 : effect.getAmount();
        if (amount < 1 || amount != Math.floor(amount)) {
            throw new IllegalArgumentException(
                    "Op " + op + " raises a skill's level, so \"amount\" is a count of levels: a whole number "
                            + ">= 1, got " + (effect.getAmount() == null ? "nothing" : amount)
                            + " (source: " + rule.getSource() + ")");
        }
        TriggerValidator.rejectSkillLevelExtras(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, RaiseSkillLevelArgs args) {
        TriggerInterpreter.raiseSkillLevel(args, ctx.firing());
    }
}
