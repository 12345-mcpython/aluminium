package com.laosun.aluminium.models.battle;

import com.laosun.aluminium.models.CanHit;

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
     * Clears both marks at the start of a new action.
     */
    public void clear() {
        lethal.clear();
        lostHp.clear();
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
