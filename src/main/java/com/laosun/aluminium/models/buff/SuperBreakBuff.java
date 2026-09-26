package com.laosun.aluminium.models.buff;

import com.laosun.aluminium.models.CanHit;

/**
 * The trigger marker for super break (P4-6): **a pure marker, with no numeric value**.
 *
 * <p>Semantics (matching Trailblazer · Harmony's ultimate 【伴舞】: "after attacking an enemy
 * target that is in the weakness-broken state, converts this attack's toughness reduction
 * value into 1 instance of super break damage") — it is attached to the **attacker**; it
 * decides "can this attack produce a super break segment", and how much is produced is
 * computed by {@code SkillExecutor} under the convention below.
 *
 * <h2>How the toughness reduction value is split (do not get this wrong)</h2>
 * Let the enemy's remaining toughness be {@code T} and the skill's nominal toughness
 * reduction be {@code S}:
 * <pre>
 *   case                        break damage uses    super break damage uses
 *   T &gt; S (not emptied)        none                 none
 *   S &gt;= T (broken this hit)   min(S, T), actual    max(0, S - T), the excess
 *   enemy already broken (T = 0) none                S (the whole hit counts as excess)
 * </pre>
 * The two chains split the same {@code S}, and their sum is always {@code S} — nothing
 * double-counted, nothing missed. Therefore "the hit that broke the toughness" produces
 * break damage and super break damage **at the same time** (example: skill 60, monster 30
 * toughness → skill damage + 30 break damage + 30 super break damage).
 *
 * <p>Note that super break still **only works on weakness elements** — it inherits the
 * premise that "only weakness hits reduce toughness" (see {@code Battle.reduceToughness}),
 * so a non-weakness attack on an already-broken enemy produces no super break segment.
 *
 * <p>The template is the same as {@link VulnerabilityBuff}: it does not change attributes and
 * has no persistent state to clean up, so {@code applyEffect} / {@code removeBuff} are both
 * empty and only {@code tickEffect} decrements the duration. The difference from that class
 * is: this one does not even implement {@code DamageEvent} — it does not inject a zone at
 * settlement time; instead {@code SkillExecutor} actively builds one extra
 * {@code DamageType.SUPER_BREAK} damage segment.
 *
 * @see com.laosun.aluminium.enums.DamageType#SUPER_BREAK
 */
public class SuperBreakBuff extends AbstractBuff {

    /**
     * @param duration duration in turns (same template as {@link VulnerabilityBuff}: a
     *                 post-move buff, decremented with {@code afterMove})
     */
    public SuperBreakBuff(int duration) {
        super(duration, false);
    }

    @Override
    public boolean canAct() {
        return true;
    }

    @Override
    public void applyEffect(CanHit target) {
        // Pure marker: changes no attributes
    }

    @Override
    public void removeBuff(CanHit target) {
        // Same as above: no persistent state to clear
    }

    @Override
    public void tickEffect(CanHit target) {
        decreaseDuration();
    }
}
