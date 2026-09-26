package com.laosun.aluminium.models.buff;

import com.laosun.aluminium.models.CanHit;

/**
 * Taunt (P5-2): **a pure marker, with no numeric value**.
 *
 * <p>Semantics (the author's definition, not "raise the aggro value by a percentage"):
 * <blockquote>
 * As long as the taunt buff is attached, the attacker's **single-target attacks** and the **centre of a blast
 * attack** can only select the individual the taunt is attached to. **It works in both directions** — the same
 * applies when our side uses single-target/blast attacks on the enemy.
 * </blockquote>
 *
 * <p>So it is **not** a multiplier inside {@code aggroOf} (a multiplier can only raise the probability, it cannot
 * achieve "can only select"), but a **hard constraint** at the target-selection stage, living in
 * {@code TargetSelector}:
 * <ul>
 *   <li>if the candidate set contains "a living individual carrying this buff" → return it directly, skipping the
 *       aggro weighting;</li>
 *   <li>an AoE attack hits everyone anyway, so it is unaffected;</li>
 *   <li>if the taunter is dead / is not on the side being attacked → the constraint lapses and we fall back to
 *       aggro weighting (a corpse must not be force-selected).</li>
 * </ul>
 *
 * <p>The same template as {@link VulnerabilityBuff}: a post-move buff (its duration ticks down with
 * {@code afterMove}), it changes no attributes and has no persistent state.
 */
public class TauntBuff extends AbstractBuff {

    /**
     * @param duration the number of turns it lasts
     */
    public TauntBuff(int duration) {
        super(duration, false);
    }

    @Override
    public boolean canAct() {
        return true;
    }

    @Override
    public void applyEffect(CanHit target) {
        // a pure marker: it changes no attributes
    }

    @Override
    public void removeBuff(CanHit target) {
        // as above: there is no persistent state to clear
    }

    @Override
    public void tickEffect(CanHit target) {
        decreaseDuration();
    }
}
