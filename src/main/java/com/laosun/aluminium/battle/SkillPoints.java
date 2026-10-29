package com.laosun.aluminium.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.skillpoint.SkillPointPolicy;
import com.laosun.aluminium.skillpoint.StandardSkillPointPolicy;
import com.laosun.aluminium.trigger.TriggerPayload;

import java.util.function.IntConsumer;
import java.util.function.ObjIntConsumer;

/**
 * The party's skill-point pool and the rules that move it.
 *
 * <p><b>Why this is separate.</b> The policy already owns the arithmetic; what {@code Battle} owned was
 * the wiring - which listener reports gains and spends, and which trigger events a direct grant fires.
 * That wiring is one subject, and keeping it next to the turn code made the pool look like turn state.
 */
public final class SkillPoints {

    private final Battle battle;
    private final IntConsumer onGained;
    private final ObjIntConsumer<CanHit> onSpent;
    private SkillPointPolicy policy = new StandardSkillPointPolicy();

    /**
     * @param battle   the battle whose trigger tables hear direct grants
     * @param onGained announces a policy-reported gain
     * @param onSpent  announces a policy-reported spend
     */
    public SkillPoints(Battle battle, IntConsumer onGained, ObjIntConsumer<CanHit> onSpent) {
        this.battle = battle;
        this.onGained = onGained;
        this.onSpent = onSpent;
        listen();
    }

    /**
     * @return the current policy
     */
    public SkillPointPolicy policy() {
        return policy;
    }

    /**
     * Installs a policy and rewires its event reports.
     *
     * @param policy the new policy
     */
    public void setPolicy(SkillPointPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("a battle needs a skill point policy");
        }
        this.policy = policy;
        listen();
    }

    /**
     * @return the current skill points
     */
    public int value() {
        return policy.getValue();
    }

    /**
     * @return the regular cap
     */
    public int max() {
        return policy.getMax();
    }

    /**
     * @param points the amount
     * @return whether the pool holds at least that many
     */
    public boolean hasAtLeast(int points) {
        return policy.hasAtLeast(points);
    }

    /**
     * @return whether one skill is affordable
     */
    public boolean canAfford() {
        return policy.canAfford();
    }

    /**
     * Restores points, capped; fires the direct-gain/overflow events.
     *
     * @param n how many to add
     */
    public void gain(int n) {
        int before = policy.getValue();
        policy.gain(n);
        int gained = policy.getValue() - before;
        if (gained > 0) {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.SKILL_POINT_GAINED, null, null, 0, gained));
        }
        int overflowed = n - gained;
        if (overflowed > 0) {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.SKILL_POINT_OVERFLOWED, null, null, 0, overflowed));
        }
    }

    /**
     * @return whether one point was spent
     */
    public boolean spend() {
        return policy.spend();
    }

    /**
     * @param skill the skill
     * @param user  the caster
     * @return whether the action may continue
     */
    public boolean onSkillCast(Skill skill, CanHit user) {
        return policy.onSkillCast(user, skill);
    }

    /**
     * Reports the standard policy's own in-cast movements.
     */
    private void listen() {
        if (policy instanceof StandardSkillPointPolicy standard) {
            standard.setListener(new StandardSkillPointPolicy.Listener() {
                @Override
                public void onGained(int amount) {
                    onGained.accept(amount);
                }

                @Override
                public void onSpent(CanHit spender, int amount) {
                    onSpent.accept(spender, amount);
                }
            });
        }
    }
}
