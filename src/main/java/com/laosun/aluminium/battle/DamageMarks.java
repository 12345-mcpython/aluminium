package com.laosun.aluminium.battle;

import com.laosun.aluminium.entity.CanHit;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-action marks: who has already been lethally hit, and who has already lost HP.
 *
 * <p><b>Why this is separate.</b> Both lists answer "once per action" questions and are cleared at the
 * same boundary. Keeping them next to the damage pipeline made them look like damage state; they are
 * action-scoped marks, and the clear is the only place their lifetime is decided.
 */
public final class DamageMarks {

    private final List<CanHit> lethal = new ArrayList<>();
    private final List<CanHit> lostHp = new ArrayList<>();

    /**
     * How much HP each unit has lost in this action - the AMOUNT, where {@link #lostHp} only records that it happened.
     *
     * <p><b>Why the sum has to be kept here.</b> 「单次受到攻击中累计损失的生命值超过最大生命值的 25%」 (light cone 21038
     * Flames Afar) is a question about a SUM over the instances of one action, and no single event carries it: {@code HP_LOST}
     * fires per instance with that instance's loss. The boundary is already decided here ({@link #clear} at the start of an
     * action), so summing at the same boundary is the one place where "this action" has a fixed meaning.
     */
    private final java.util.Map<CanHit, Double> lostAmount = new java.util.LinkedHashMap<>();

    /**
     * @param target the unit that took a lethal blow
     */
    public void markLethal(CanHit target) {
        if (!lethal.contains(target)) {
            lethal.add(target);
        }
    }

    /**
     * @param target the unit that lost HP
     */
    public void markLostHp(CanHit target) {
        if (!lostHp.contains(target)) {
            lostHp.add(target);
        }
    }

    /**
     * The same mark, with how much was lost: the per-action SUM that 「单次…累计损失」 asks about.
     *
     * @param target the unit that lost HP
     * @param amount the HP it lost in this instance ({@code > 0})
     */
    public void markLostHp(CanHit target, double amount) {
        markLostHp(target);
        if (amount > 0) {
            lostAmount.merge(target, amount, Double::sum);
        }
    }

    /**
     * @param target the unit
     * @return how much HP it has lost in this action ({@code 0} if none)
     */
    public double hpLostThisAction(CanHit target) {
        return lostAmount.getOrDefault(target, 0.0);
    }

    /**
     * Clears both marks at the start of a new action.
     */
    public void clear() {
        lethal.clear();
        lostHp.clear();
        lostAmount.clear();
    }

    /**
     * @return the units lethally hit this action
     */
    public List<CanHit> lethalThisAction() {
        return List.copyOf(lethal);
    }

    /**
     * @return the units that lost HP this action
     */
    public List<CanHit> lostHpThisAction() {
        return List.copyOf(lostHp);
    }
}
