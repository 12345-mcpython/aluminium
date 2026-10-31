package com.laosun.aluminium.trigger;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.SkillEffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.skill.Skill;

import java.util.ArrayList;
import java.util.List;

/**
 * The <b>castability gate a data row states</b>: {@code skill_effects.json}'s {@code castable_when}.
 *
 * <p><b>The gap this closes.</b> A sentence can gate a cast on a <b>resource</b> - 「长夜月的【忆质】大于等于 #3 点…时施放」
 * (1141307, 16 points). The engine had no spelling for it, so the cast went through with whatever the counter held; for a
 * price quoted <i>per point</i> that reads as "0 points, 0 damage" - a wrong number with no symptom. The conditions are
 * spelled exactly as a rule's {@code when}, because a second condition language would be the thing that drifts.
 *
 * <p><b>Whose counter.</b> A memosprite's gate is about its <b>summoner</b>: 「长夜月」's 亿质 lives on 1413, while the
 * caster is the servant. So the condition's owner is the caster's master when the caster is a summon, and the caster
 * otherwise - the same subject the servant's damage rows read ({@code Summon.damageBase}).
 *
 * <p>⚠ <b>A refusal is silent at this layer and loud in the plan.</b> {@link #allows} answers {@code false} exactly like
 * an empty energy bar; a caller that expected the cast (a replay) reports "not drivable"/"refused" rather than a number.
 */
public final class SkillCastability {

    private SkillCastability() {
    }

    /**
     * Whether the cast is allowed by the skill row's own gate.
     *
     * @param skill  the skill about to be cast
     * @param caster the unit casting it
     * @param battle the battle it happens in
     * @return {@code true} when the row states no gate, or every spelled condition holds
     */
    public static boolean allows(Skill skill, CanHit caster, Battle battle) {
        // 「单场战斗中只能施放 1 次」: history, not a resource - the battle remembers whether the one cast was spent.
        if (battle != null && !battle.singleUseLeft(skill)) {
            return false;
        }
        // 「无法施放战技 / 终结技」: a state on the caster bans the slot. Reader: 1315 Boothill's
        // 【绝命对峙】 ("cannot cast Skill while in it").
        if (caster != null && caster.getBuffManager() != null) {
            String banned = switch (skill.getSkillSlot()) {
                case 2 -> "skill";
                case 3 -> "ultimate";
                default -> null;
            };
            if (banned != null && caster.getBuffManager().suppresses(banned)) {
                return false;
            }
        }
        List<TriggerTable.Condition> conditions = conditionsOf(skill);
        if (conditions.isEmpty()) {
            return true;
        }
        CanHit owner = caster instanceof Summon summon && summon.getMaster() != null
                ? summon.getMaster() : caster;
        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(
                owner, caster, null, 0, 0, null, battle, SkillCategory.UNSPECIFIED);
        for (TriggerTable.Condition condition : conditions) {
            if (!condition.test(ctx)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The row's conditions, parsed once and remembered on the spec.
     *
     * @param skill the skill
     * @return the conditions ({@code empty} when the row states none)
     */
    private static List<TriggerTable.Condition> conditionsOf(Skill skill) {
        if (skill == null || skill.getData() == null) {
            return List.of();
        }
        SkillEffectSpec spec = com.laosun.aluminium.data.SkillEffects.forSkill(skill);
        if (spec == null || spec.getCastableWhen() == null || spec.getCastableWhen().isEmpty()) {
            return List.of();
        }
        List<TriggerTable.Condition> parsed = spec.getCastableConditions();
        if (parsed != null) {
            return parsed;
        }
        // A synthetic rule only carries the `source` string into a parse error; its field is final-ish, so it is set the
        // way the project sets Gson-only beans (there is no builder for one).
        TriggerSpec synthetic = new TriggerSpec();
        try {
            var source = TriggerSpec.class.getDeclaredField("source");
            source.setAccessible(true);
            source.set(synthetic, "skill " + skill.getCid() + " slot " + skill.getSkillSlot() + " castable_when");
        } catch (ReflectiveOperationException ignored) {
            // the message then names no source, which is not worth failing a cast over
        }
        List<TriggerTable.Condition> built = new ArrayList<>();
        for (String raw : spec.getCastableWhen()) {
            built.add(TriggerConditionParser.parseCondition(raw, synthetic));
        }
        spec.setCastableConditions(List.copyOf(built));
        return spec.getCastableConditions();
    }
}
