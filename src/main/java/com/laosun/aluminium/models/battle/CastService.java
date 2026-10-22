package com.laosun.aluminium.models.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.skill.Skill;

import java.util.List;

/**
 * The action-time cast gate: can the current actor cast this skill, what does it cost in skill points,
 * and does an ultimate have to be settled through the ultimate path.
 *
 * <p><b>Why this is separate.</b> The ultimate's cost used to live only in {@code castUltra}, so a
 * caller that reached {@code useSkill} directly cast a 120-cost ultimate for free. The gate now lives
 * where the cast actually happens; this object is that gate, so no front end can bypass it by choosing
 * the other entry point.
 */
public final class CastService {

    private final Battle battle;
    private final SkillPoints skillPoints;
    private final UltimateService ultimates;
    private final SkillRequests requests;

    /**
     * @param battle      the battle whose current actor casts
     * @param skillPoints the party's skill-point pool
     * @param ultimates   the ultimate path
     * @param requests    the pending-cast queue
     */
    public CastService(Battle battle, SkillPoints skillPoints, UltimateService ultimates, SkillRequests requests) {
        this.battle = battle;
        this.skillPoints = skillPoints;
        this.ultimates = ultimates;
        this.requests = requests;
    }

    /**
     * Casts the given skill for the current actor.
     *
     * @param skill  the skill
     * @param target its targets
     * @return whether the cast was accepted
     */
    public boolean useSkill(Skill skill, List<? extends CanHit> target) {
        if (battle.currentMove() == null || skill == null || target == null) {
            return false;
        }
        CanHit user = battle.currentMove().getCanHit();
        if (user.isDeath()) {
            return false;
        }
        if (skill == user.getSkills().get(SkillType.ULTRA)) {
            if (!ultimates.isReady(user)) {
                return false;
            }
            CanHit extraTurnActor = battle.queue().getExtraTurnActor();
            if (extraTurnActor != null && extraTurnActor != user) {
                return false;
            }
            if (!skillPoints.onSkillCast(skill, user)) {
                return false;
            }
            requests.add(skill, user, target);
            return ultimates.settle(user, skill);
        }
        if (!skillPoints.onSkillCast(skill, user)) {
            return false;
        }
        requests.add(skill, user, target);
        return true;
    }

    /**
     * Performs the current actor's action with the turn and skill-point gates.
     *
     * @param skill   the skill
     * @param targets its targets
     * @return whether the action was accepted
     */
    public boolean performAction(Skill skill, List<? extends CanHit> targets) {
        if (battle.currentMove() == null || skill == null || targets == null) {
            return false;
        }
        CanHit actor = battle.currentMove().getCanHit();
        if (actor.isDeath()) {
            return false;
        }
        if (!actor.getBuffManager().canAct()) {
            return false;
        }
        return useSkill(skill, targets);
    }
}
