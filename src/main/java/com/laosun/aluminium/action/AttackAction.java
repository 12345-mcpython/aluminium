package com.laosun.aluminium.action;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;

/**
 * 普通攻击行动（基础行动实现）
 */
// TODO: 应为每个角色设计单独的 Action!
public class AttackAction implements Action {
    private final CanHit target; // 攻击目标

    public AttackAction(CanHit target) {
        this.target = target;
    }

    @Override
    public boolean execute(Battle battle, CanHit performer) {
        // 校验执行者和目标是否存活
        if (!performer.isAlive() || !target.isAlive()) {
            System.out.printf("[%s] ATTACK FAILURE！DEATH OR CAN'T MOVE%n", performer.getName());
            return false;
        }

        // 计算伤害（可自定义公式：攻击 - 目标防御，最低1点伤害）
        double damage = Math.max(1, performer.getInBattleAttack() - target.getInBattleDefence());
        target.takeDamage(damage);

        // 输出战斗日志
        System.out.printf("[%s] to [%s] simple attack！ %.0f dmg，hp：%.0f%n",
                performer.getName(), target.getName(), damage, target.getInBattleHealth());

        // 触发行动后事件（如buff触发、反击等）
        performer.onAfterAction(battle, target,this);
        target.onBeAttacked(battle, performer, this);
        return true;
    }
}