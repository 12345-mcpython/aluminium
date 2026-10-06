package com.laosun.aluminium.models.skillpoint;

import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.skill.Skill;

/**
 * The policy for skill points (SP): it pulls "should it be spent, how much, and how does it grow" out of
 * {@code Battle}.
 *
 * <p><b>Why this interface exists</b> (an architecture decision added after a design review, see <b>F-8</b> of
 * {@code ROADMAP.md} §12.5): skill points are a team-level resource, but their rules keep growing - 
 * Bronya's (布洛妮娅) "50% chance to restore 1 point when casting the skill", Sushang's (素裳) "restore 1 point
 * after casting the skill on a broken target", Sparkle's (花火) "max +2", the 4-piece Passerby set "restore 1 point
 * at the start of battle" ...
 * If all of these were added as branches inside {@code Battle.useSkill}, {@code Battle} would fill up with
 * "because some character" checks, which is exactly what the three-way split forbids.
 *
 * <p>After extracting it into a policy:
 * <ul>
 *   <li>{@code Battle} only knows how to "ask the policy once whether it can act" ({@link #onSkillCast}),
 *       and knows no character at all;</li>
 *   <li>character-level corrections will be injected in future by another implementation (or by this one
 *       reading an externally registered effect table); the injection point is the assembly point
 *       ({@code CharacterFactory}, the only place a {@code cid} may appear) or the trigger table;</li>
 *   <li>the engine side gains no new dependency on "some character" - that is the entire reason this abstraction
 *       exists.</li>
 * </ul>
 *
 * <p><b>The split of labour with {@code EnergyProvider}</b> (copy that successful pattern, do not confuse them):
 * <ul>
 *   <li>{@code EnergyProvider} is one per unit, because the energy bar is a personal resource;</li>
 *   <li>this interface is one per battle, because skill points are shared by the whole team.</li>
 * </ul>
 *
 * <p>Note: This interface does not promise to express event-driven character mechanics such as "whenever a
 * skill point is spent ..." (Misha's (米沙) "every 1 skill point our side spends to +1 hit on the next ultimate",
 * Sparkle's (花火) "when our side spends a skill point, gain 1 extra energy"). Those need to listen to the event
 * "a skill point was spent", which belongs to the trigger table (it needs a new event
 * {@code SkillPointSpentEvent}) - see F-4 of {@code ROADMAP.md} §12.5.
 */
public interface SkillPointPolicy {

    /**
     * Called once when a unit is about to cast a skill; the policy decides how the skill points change.
     *
     * <p>The call timing is the "decide to act" layer ({@code Battle.useSkill}), not after the damage is
     * settled - it is atomic with "acting", which avoids "it was never spent yet the hit came out".
     *
     * @param user  the caster (not {@code null}; the caller has already checked "still alive")
     * @param skill the skill to cast (not {@code null}; its {@code getData()} may be {@code null},
     *              for example an enemy's {@code EnemySkill})
     * @return whether this action may continue; {@code false} means there are not enough resources and the
     * caller must abandon this action
     */
    boolean onSkillCast(CanHit user, Skill skill);

    /**
     * The current value.
     */
    int getValue();

    /**
     * The regular maximum.
     */
    int getMax();

    /**
     * Grants a bonus on top of the conventional cap; a negative {@code delta} takes one away.
     *
     * <p>The cap itself is not a constant: Sparkle's talent raises the team's skill point cap while she is
     * on the field. A policy owns the value, so the change has to travel through the policy rather than
     * through {@code Battle}, the same way spending and gaining do.
     *
     * @param delta the change to the cap (may be negative)
     * @return the effective cap after the change
     */
    int raiseMax(int delta);

    /**
     * Adds a value directly (capped at the regular maximum) and returns the amount actually credited.
     *
     * <p>For explicit sources other than "basic attack +1" (techniques, relics, character mechanics).
     */
    int gain(int delta);

    /**
     * Whether there is enough for one spend ({@code > 0}).
     */
    boolean canAfford();

    /**
     * Whether the pool holds at least {@code points} skill points right now.
     *
     * <p>{@link #canAfford()} only ever answers "can the next action be paid for", which is one point for a
     * skill. A clause that asks a different question -- light cone 23046 gates on the CAP, and the count
     * itself is what other content reads -- needs the count, so the narrower question is kept and this one
     * sits beside it rather than replacing it.
     *
     * @param points the number of points to check for
     * @return whether the pool holds that many (a non-positive argument is trivially true)
     */
    boolean hasAtLeast(int points);

    /**
     * Spends one charge.
     *
     * @return whether it succeeded; {@code false} means not enough (the value is unchanged)
     */
    boolean spend();
}
