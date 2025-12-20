package com.laosun.aluminium;

import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.*;
import com.laosun.aluminium.models.Character;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 战斗核心类：管理战斗流程、结束条件、角色行动
 */
@Getter
@Setter
public class Battle {
    private final Queue actionQueue;
    private boolean battleEnded = false;
    private Camp winningCamp = null;
    private Signal currentMove;

    public Battle(Queue actionQueue) {
        this.actionQueue = actionQueue;
    }

    /**
     * 开始战斗（主循环）
     */
    public void startBattle() {
        System.out.println("===== BATTLE START =====");
        actionQueue.initialize();
        actionQueue.getCombatantQueue().forEach(combatant -> {
            combatant.onBattleStart(this, combatant);
        });
    }

    public void pushTime() {
        actionQueue.progressTime();
    }

    public boolean askTopMove() {
        Signal top = actionQueue.getNextCombatant();
        top.getCanHit().onBeforeMove(this);
        if (top.getCanHit().getHealth() == 0) {
            return false;
        }
        currentMove = top;
        return true;
    }

    /**
     * 工具方法：获取某阵营的存活目标（用于行动选择）
     */
    public List<CanHit> getAliveTargets(Camp camp) {
        return actionQueue.getAliveByCamp(camp);
    }

    public boolean performSkill(CanHit performer, SkillRequest skillRequest, List<CanHit> targets) {
        if (currentMove == null && skillRequest.skill != SkillType.ULTRA) {
            IO.println("Can't perform " + skillRequest.skill);
            return false;
        }
        if (performer instanceof Character character) {
            Skill s = switch (skillRequest.skill) {
                case ULTRA -> character.getCharacterSkills().ultra;
                case COMMON -> character.getCharacterSkills().common;
                case SKILL -> character.getCharacterSkills().skill;
                case TALENT -> character.getCharacterSkills().talent;
                case SUMMON_SKILL -> character.getCharacterSkills().summonSkill;
                case SUMMON_TALENT -> character.getCharacterSkills().summonTalent;
            };
            s.performSkill(performer, targets);
        } else if (performer instanceof Enemy e) {

        }
        return true;
    }
}