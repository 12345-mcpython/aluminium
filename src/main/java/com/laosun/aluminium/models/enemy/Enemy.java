package com.laosun.aluminium.models.enemy;

import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.utils.AttributeBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.laosun.aluminium.enums.AttributeType.*;

/**
 * An enemy combatant in the action queue.
 *
 * <p>Enemies are typically aligned with {@link Camp#ENEMY}; their attributes are scaled by
 * level / instance / elite-group multipliers in {@code EnemyScaler} and assembled by
 * {@code EnemyFactory}.
 */
@Getter
@Setter
@ToString(callSuper = true)
public class Enemy extends CanHit {

    /**
     * Per-element resistance (0.2 = 20% RES). An element missing from the table has no
     * resistance. Filled from {@code monster_config.json}'s {@code damage_resistance}.
     */
    private Map<DamageElement, Double> damageResist = Map.of();

    /**
     * Specific debuff resistance (P6-1): the key is a {@code STAT_*} string from the data,
     * the value is the **mitigation ratio**.
     *
     * <p>Example: Ice Edge {@code {"STAT_CTRL_Frozen": 1}} = fully immune to freeze
     * (1 = 100% resistance). It participates as the last factor {@code (1 - specific)}
     * of {@code Battle.hitChance}.
     *
     * <p>Measured against the data: 999 of the 2649 monsters carry this column; the keys
     * that occur include
     * {@code STAT_CTRL_Frozen / STAT_CTRL / STAT_Confine / STAT_Entangle /
     * STAT_DOT_Burn / STAT_DOT_Electric / STAT_DOT_Poison} and so on.
     */
    private Map<String, Double> debuffResist = Map.of();

    /**
     * Weakness elements (from {@code stance_weak} in {@code monster_config.json}). Empty by
     * default = no weakness (102 entries in the data lack this item).
     *
     * <p>This is **data**: hitting a weakness element allows toughness reduction; the
     * judgement and the toughness-reduction mechanic are implemented in P4-2.
     */
    private Set<DamageElement> stanceWeak = Set.of();

    /**
     * Current toughness (P4 toughness reduction decreases it; the value is given by
     * {@code EnemyScaler}: template × level group × instance multiplier).
     */
    private double stance;

    /**
     * Max toughness (= the initial {@link #stance}).
     */
    private double maxStance;

    /**
     * Number of toughness bars (multi-bar toughness, from the template's {@code stance_count}).
     */
    private int stanceCount;

    /**
     * This monster's own toughness element (template {@code stance_type}).
     */
    private DamageElement stanceType;

    /**
     * This monster's summon roster (P9-4): the monster ids it may bring onto the field, straight from
     * {@code monster_config.json}'s {@code summon_id} (non-positive entries already dropped at load).
     *
     * <p>An empty roster is the norm (1957 of 2649 monsters) and means "this monster summons nothing".
     * Keeping the roster on the instance rather than looking the config up again at summon time means the
     * <b>caller</b> can decide when, without also having to know which table the data came from — the same
     * split as {@link #phases} (data here, timing in the caller).
     */
    private List<Integer> summonIds = List.of();

    /**
     * Whether it is in the broken state (judged in P4-2, recovered in P4-4).
     */
    private boolean broken;

    /**
     * The element of this break (used by P4-3 break damage / P4-5 DOT type).
     */
    private DamageElement brokenElement;

    /**
     * Remaining turns of the broken state (turn skipping / action delay is maintained by
     * P4-4; P4-1 only keeps the field).
     */
    private int brokenRemainTurns;

    /**
     * Phase table (P9-5): a skill that becomes the active one once the enemy is at or below an HP ratio,
     * kept in <b>ascending</b> threshold order.
     *
     * <p>{@link #activeSkill()} reads the current HP every time it is asked, so a phase change needs no
     * bookkeeping at all — no flag to flip, no transition to schedule. That is deliberate: the alternative
     * ("hold the HP bar at 1 and advance the phase when it would have died") is the approach the roadmap
     * warns about, because {@code CanHit.takeDamage} sets {@code death = true} the moment HP reaches 0, so
     * a locked bar either skips the phase or lets the enemy be hit after it is already down. A
     * <b>multi-HP-bar</b> boss does need locking, and the safe way is {@code setInvulnerable(true)}
     * followed by an explicit HP reset — that is not implemented here and stays registered as its own item.
     */
    private final List<PhaseSkill> phases = new ArrayList<>();

    /**
     * One phase: at or below {@code hpRatio} of max HP, {@code skill} is what this enemy acts with.
     */
    public record PhaseSkill(double hpRatio, Skill skill) {
    }

    /**
     * Registers a phase. Thresholds are kept sorted, so they may be added in any order.
     *
     * @param hpRatio the HP ratio (0.5 = half) at or below which {@code skill} takes over
     * @param skill   the skill to act with from that point on
     */
    public void setPhaseSkill(double hpRatio, Skill skill) {
        if (skill == null) {
            return;
        }
        phases.add(new PhaseSkill(hpRatio, skill));
        phases.sort(java.util.Comparator.comparingDouble(PhaseSkill::hpRatio));
    }

    /**
     * How many phases are registered (0 = no phase behaviour, which is every enemy today).
     */
    public int phaseCount() {
        return phases.size();
    }

    /**
     * The skill to act with <b>right now</b>, or {@code null} when no phase applies.
     *
     * <p>The <b>lowest</b> registered threshold the enemy is at or below wins, which is what makes the list
     * ascending: the first match is the tightest one, so a boss at 20% uses its 20% phase and not its 80%
     * one. {@code null} means "no phase behaviour applies" and the caller should use the enemy's ordinary
     * {@code COMMON} skill — returning {@code null} rather than that skill keeps this class from having to
     * know which slot the fallback lives in.
     */
    public Skill activeSkill() {
        double maxHp = getMaxHp();
        double ratio = maxHp <= 0 ? 1.0 : getCurrentHp() / maxHp;
        for (PhaseSkill phase : phases) {
            if (ratio <= phase.hpRatio()) {
                return phase.skill();
            }
        }
        return null;
    }

    public Enemy(String name, Camp camp, DoubleValue[] attributes) {
        super(name, camp, attributes);
    }

    public Enemy(String name, DoubleValue[] attributes) {
        super(name, Camp.ENEMY, attributes);
    }

    public static Enemy fromAttributes(String name, double health, double defence, double attack, double speed) {
        AttributeBuilder attributeBuilder = new AttributeBuilder();
        attributeBuilder.setBase(HEALTH, health);
        attributeBuilder.setBase(DEFENCE, defence);
        attributeBuilder.setBase(ATTACK, attack);
        attributeBuilder.setBase(SPEED, speed);
        return new Enemy(name, attributeBuilder.build());
    }

    /**
     * Whether the given element is one of this enemy's weaknesses.
     *
     * <p>P4-2 uses this as the single judgement point for "weakness toughness reduction" —
     * do not reach for {@link #stanceWeak} directly any more, or a future change to the
     * judgement rule will miss the call sites.
     *
     * @param element the damage element of an incoming hit ({@code null} → {@code false})
     * @return {@code true} if the element is a weakness
     */
    public boolean isWeakTo(DamageElement element) {
        return element != null && stanceWeak.contains(element);
    }

    /**
     * Whether it has a toughness bar ({@code maxStance > 0}).
     *
     * <p>P4-2's toughness-reduction / break judgement goes through here uniformly — do not
     * each reach for {@link #maxStance}: the data really does contain monsters with 0 toughness.
     *
     * @return {@code true} if this enemy can be broken at all
     */
    public boolean hasToughnessBar() {
        return maxStance > 0;
    }

    /**
     * Toughness reduction (P4-2 calls this once per damage segment).
     *
     * <p><b>Reaching zero does not break automatically</b> — the break judgement has to
     * distinguish weakness break from non-weakness toughness reduction (the P4-2 stance), so
     * this method is only responsible for deducting and clamping at 0. A target that is
     * already broken is no longer reduced before it recovers (the toughness bar is empty).
     *
     * <p><b>Returns the amount actually consumed (H-4)</b>: break damage must be settled on
     * "how much did this segment really shave off", not on the skill's nominal toughness
     * reduction — 10 points of toughness left taking a 30-point skill means only 10 counts.
     * Callers must also note: super break (P4-6) uses the **excess** {@code amount - consumed},
     * so the return value of this method and the nominal value the caller holds are needed
     * together; do not keep only one of them.
     *
     * @param amount toughness reduction points (the skill's {@code stance_list} value ×
     *               weakness / non-weakness coefficient)
     * @return the points actually deducted from the toughness bar (0 = nothing shaved off:
     * already broken / non-positive / bar already empty)
     */
    public double reduceStance(double amount) {
        if (broken || amount <= 0 || stance <= 0) {
            // stance <= 0: the toughness bar is already empty (normally that coincides with
            // broken, but this guard must not assume the caller always goes in order). Only by
            // blocking it explicitly is the return value guaranteed to mean min(amount,
            // remaining toughness); otherwise super break would compute "excess = amount - 0 =
            // the whole hit" and conjure a super break out of an empty toughness bar.
            return 0;
        }
        double consumed = Math.min(stance, amount);
        stance -= consumed;
        return consumed;
    }

    /**
     * Enter the broken state (P4-2 calls this when toughness reaches zero).
     *
     * @param element the element that caused the break ({@code null} = unknown, not asserted)
     */
    public void breakEnemy(DamageElement element) {
        broken = true;
        brokenElement = element;
        stance = 0;
    }

    /**
     * Leave the broken state and refill the toughness bar (P4-4: called when the broken
     * duration in turns ends).
     *
     * <p>Bar-by-bar consumption for multi-bar toughness ({@link #stanceCount} {@code > 1})
     * is left to P4-4; this task only restores the value to full.
     */
    public void recoverFromBroken() {
        broken = false;
        brokenElement = null;
        brokenRemainTurns = 0;
        stance = maxStance;
    }
}
