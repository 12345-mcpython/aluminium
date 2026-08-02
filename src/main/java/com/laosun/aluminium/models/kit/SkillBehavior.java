package com.laosun.aluminium.models.kit;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;

import java.util.List;

/**
 * A special skill behavior: how one (cid, skillId) pair executes when the
 * generic data-driven {@code DataSkill} handlers are not sufficient
 * (special param layouts, HP costs, control effects, DoT detonation...).
 *
 * <p>Behaviors are looked up per skill via the character's
 * {@link CharacterKit#skillBehavior(int)}. If none is registered, the skill
 * falls back to the generic executor.
 */
public interface SkillBehavior {

    /**
     * Executes the skill.
     *
     * @param battle  the battle context
     * @param context the skill execution context (params, stance, desc)
     * @param user    the caster
     * @param targets the designated target list
     */
    void execute(Battle battle, SkillContext context, CanHit user, List<? extends CanHit> targets);
}
