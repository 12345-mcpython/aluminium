package com.laosun.aluminium.models.ai;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.buff.TauntBuff;

import java.util.List;
import java.util.Random;

/**
 * Target selection (P5-4): decides "who this attack hits".
 *
 * <p>Two strategies, in priority order:
 * <ol>
 *   <li><b>Taunt as a hard constraint</b>: if a living taunter exists among the candidates → pick it
 *       directly (see {@link TauntBuff}). Only applies to {@link Intent#SINGLE} and
 *       {@link Intent#BLAST} — the **center** of a single-target and of a blast is constrained; AOE
 *       already hits everyone, so nothing needs choosing; bounces are random per hit and are not
 *       constrained.</li>
 *   <li><b>Aggro-weighted random</b>: draw one with probability
 *       {@code that unit's aggro / total candidate aggro}. Preservation (存护) 150 is more likely to
 *       be hit than the usual 100.</li>
 * </ol>
 *
 * <p><b>What counts as the candidate set</b>: the caller MUST pass "the living opponents". Do not
 * filter by faction here yourself — the single outlet in the engine for "who can be selected as a
 * target" is {@code Battle.targetableEnemies()} (for the enemy side) plus the caller's own filtered
 * friendly list; selecting a corpse is exactly where corpse-hitting comes from.
 */
public final class TargetSelector {

    private TargetSelector() {
    }

    /**
     * The "intent" of this attack — decides whether taunt constrains target selection.
     */
    public enum Intent {
        /**
         * Single-target attack: constrained by taunt.
         */
        SINGLE,
        /**
         * Blast attack (center + adjacent): the **center** is constrained by taunt.
         */
        BLAST,
        /**
         * AOE: hits everyone, not constrained by taunt.
         */
        AOE,
        /**
         * Bounce/random: each hit is random on its own, not constrained by taunt.
         */
        RANDOM
    }

    /**
     * Select one primary target according to the intent.
     *
     * @param battle     the battle in progress (used to look up aggro)
     * @param candidates candidate targets (**must already be filtered for death**; an empty list
     *                   returns {@code null})
     * @param intent     attack intent
     * @param rng        injected random source (so results are reproducible)
     * @return the selected target; {@code null} if there is no candidate
     */
    public static CanHit select(Battle battle, List<? extends CanHit> candidates, Intent intent, Random rng) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        CanHit taunted = findTaunter(candidates, intent);
        if (taunted != null) {
            return taunted;                     // taunt is a hard constraint: skip the random roll
        }
        return weighted(battle, candidates, rng);
    }

    /**
     * A living taunter among the candidates (in theory there should be only one per candidate set;
     * when there are several, take the first).
     *
     * @return the taunter; none / the intent is not constrained → {@code null}
     */
    private static CanHit findTaunter(List<? extends CanHit> candidates, Intent intent) {
        if (intent != Intent.SINGLE && intent != Intent.BLAST) {
            return null;                        // AOE hits everyone, bounces are random per hit: unaffected by taunt
        }
        for (CanHit candidate : candidates) {
            if (!candidate.isDeath() && candidate.getBuffManager().hasBuff(TauntBuff.class)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Aggro-weighted random.
     */
    private static CanHit weighted(Battle battle, List<? extends CanHit> candidates, Random rng) {
        double total = 0;
        for (CanHit candidate : candidates) {
            total += battle.aggroOf(candidate);
        }
        if (total <= 0) {
            return candidates.getFirst();        // all weights are 0: degrade to taking the first, no division by zero
        }
        double roll = rng.nextDouble() * total;
        for (CanHit candidate : candidates) {
            roll -= battle.aggroOf(candidate);
            if (roll <= 0) {
                return candidate;
            }
        }
        return candidates.getLast();             // fallback for floating-point error
    }
}
