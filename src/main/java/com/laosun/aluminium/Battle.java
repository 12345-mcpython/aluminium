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

    /** 开始战斗（主循环） */
    public void startBattle() {
        System.out.println("===== BATTLE START =====");
        actionQueue.initialize();

        // 战斗主循环：直到某一阵营全灭
        while (!battleEnded) {
            // 1. 打印队列状态
            actionQueue.printStatus();

            // 2. 推进时间到下一个角色行动
            double timePassed = actionQueue.progressTime();
            System.out.printf("PUSH TIME %.1f ，MOVE CHARACTER：%n", timePassed);

            // 3. 获取并执行角色行动
            CanHit next = actionQueue.getNextCombatant();
            if (next == null) {
                System.out.println("NO CHARACTER END");
                battleEnded = true;
                break;
            }

            System.out.printf("MOVE [%s] MOVE%n", next.getName());
            next.performAction(this);

            // 4. 检查战斗结束条件
            checkBattleEnd();
            if (battleEnded) break;

            // 5. 重置行动角色的长度，重新排序队列
            actionQueue.resetCombatantLength(next);
        }

        // 战斗结束总结
        System.out.println("\n===== BATTLE END =====");
        if (winningCamp != null) {
            System.out.printf("WIN：%s%n", winningCamp.name());
        } else {
            System.out.println("NO WIN");
        }
    }

    /** 检查战斗结束条件（某一阵营全灭） */
    private void checkBattleEnd() {
        boolean playerWiped = actionQueue.isCampWiped(Camp.PLAYER);
        boolean enemyWiped = actionQueue.isCampWiped(Camp.ENEMY);

        if (playerWiped && enemyWiped) {
            battleEnded = true;
        } else if (playerWiped) {
            battleEnded = true;
            winningCamp = Camp.ENEMY;
        } else if (enemyWiped) {
            battleEnded = true;
            winningCamp = Camp.PLAYER;
        }
    }

    /** 工具方法：获取某阵营的存活目标（用于行动选择） */
    public List<CanHit> getAliveTargets(Camp camp) {
        return actionQueue.getAliveByCamp(camp);
    }
}