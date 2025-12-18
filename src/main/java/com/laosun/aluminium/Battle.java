package com.laosun.aluminium;

import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.models.CanHit;
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

    public Battle(Queue actionQueue) {
        this.actionQueue = actionQueue;
    }

    /**
     * 开始战斗（主循环）
     */
    public void startBattle() {
        System.out.println("===== BATTLE START =====");
        actionQueue.initialize();
    }

    public void pushTime() {
        actionQueue.progressTime();
    }

    /**
     * 工具方法：获取某阵营的存活目标（用于行动选择）
     */
    public List<CanHit> getAliveTargets(Camp camp) {
        return actionQueue.getAliveByCamp(camp);
    }
}