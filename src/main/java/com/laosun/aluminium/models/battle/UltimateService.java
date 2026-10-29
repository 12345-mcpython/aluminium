package com.laosun.aluminium.models.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.entity.CanHit;
import com.laosun.aluminium.models.energy.EnergyGain;
import com.laosun.aluminium.models.skill.Skill;

import java.util.List;

/**
 * The ultimate's gate, cost, cast and settlement.
 *
 * <p><b>Why this is separate.</b> "Can this unit cast its ultimate" and "what does casting it cost and
 * credit" are one subject, and both are read/written from places that have nothing else to do with the
 * turn loop. Keeping them next to the turn code made the gate look like a turn rule; the ordering
 * (zero the bar, settle the body, then credit the cast's own gain) lives here once.
 */
public final class UltimateService {

    private final Battle battle;
    private double lastEnergySpent;

    /**
     * @param battle the battle whose unit casts
     */
    public UltimateService(Battle battle) {
        this.battle = battle;
    }

    /**
     * Whether this unit can cast its ultimate right now.
     *
     * @param user the unit
     * @return whether the gate is open
     */
    public boolean isReady(CanHit user) {
        if (user == null) {
            return false;
        }
        if (user.getBuffManager().hasState(Battle.ULTIMATE_ACTIVATED_STATE)) {
            return true;
        }
        return user.getEnergyProvider().canCastUltra(user, energyCost(user));
    }

    /**
     * The energy this unit needs to cast its ultimate: the skill data's {@code spNeed} when stated,
     * otherwise its max energy.
     *
     * @param user the unit
     * @return the threshold
     */
    public double energyCost(CanHit user) {
        if (user == null) {
            return Double.MAX_VALUE;
        }
        Skill ultra = user.getSkills().get(SkillType.ULTRA);
        Double spNeed = ultra == null || ultra.getData() == null ? null : ultra.getData().getSpNeed();
        if (spNeed != null && spNeed > 0) {
            return spNeed;
        }
        return user.getMaxEnergy();
    }

    /**
     * Casts the unit's ultimate.
     *
     * @param user    the caster
     * @param targets its targets
     * @return whether it was cast
     */
    public boolean cast(CanHit user, List<? extends CanHit> targets) {
        if (user == null || user.isDeath() || !isReady(user)) {
            return false;
        }
        CanHit extraTurnActor = battle.queue().getExtraTurnActor();
        if (extraTurnActor != null && extraTurnActor != user) {
            return false;
        }
        Skill ultra = user.getSkills().get(SkillType.ULTRA);
        if (ultra == null) {
            return false;
        }
        if (!battle.requestSkill(ultra, user, targets)) {
            return false;
        }
        return settle(user, ultra);
    }

    /**
     * @return the energy the ultimate now settling consumed, or {@code 0}
     */
    public double lastEnergySpent() {
        return lastEnergySpent;
    }

    /**
     * Zero the bar, settle the body, then credit the cast's own gain.
     *
     * @param user  the caster
     * @param ultra its ultimate
     * @return always {@code true}
     */
    public boolean settle(CanHit user, Skill ultra) {
        lastEnergySpent = user.getCurrentEnergy();
        user.setCurrentEnergy(0);
        battle.processRequests();
        EnergyGain ultraGain = user.getEnergyProvider().onUltCast(user, ultra);
        if (ultraGain != null) {
            battle.applyEnergyGain(user, ultraGain);
            lastEnergySpent = 0;
        }
        return true;
    }
}
