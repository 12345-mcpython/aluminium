package com.laosun.aluminium.action;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;

/**
 * 技能行动（扩展行动实现，支持伤害倍率和速度消耗）
 */
// TODO: 应为每个角色设计单独的 Action!
public class SkillAction implements Action {
    private final CanHit target;          // 技能目标
    private final double damageMultiplier;// 伤害倍率
    private final double speedCost;       // 技能消耗（延长下次行动时间）

    public SkillAction(CanHit target, double damageMultiplier, double speedCost) {
        this.target = target;
        this.damageMultiplier = damageMultiplier;
        this.speedCost = speedCost;
    }

    @Override
    public boolean execute(Battle battle, CanHit performer) {
        if (!performer.isAlive() || !target.isAlive()) {
            System.out.printf("[%s] ATTACK FAILURE！DEATH OR CAN'T MOVE%n", performer.getName());
            return false;
        }

        // 计算技能伤害
        double damage = performer.getInBattleAttack() * damageMultiplier;
        target.takeDamage(damage);

        // 技能消耗：增加行动长度（下次行动更慢）
        performer.setLength(performer.getLength() + speedCost);

        // 输出日志
        System.out.printf("[%s] SKILL！[%s] %.0f DMG，HP：%.0f（LENGTH）%n",
                performer.getName(), target.getName(), damage, target.getInBattleHealth());

        // 触发事件
        performer.onAfterAction(battle, target, this);
        target.onBeAttacked(battle, performer, this);
        return true;
    }
}