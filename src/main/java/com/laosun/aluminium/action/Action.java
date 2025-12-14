package com.laosun.aluminium.action;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;

/**
 * 行动接口：所有角色行动（攻击、技能、物品）都需实现此接口
 */
public interface Action {
    /**
     * 执行行动
     * @param battle 战斗实例（用于获取目标、触发事件）
     * @param performer 行动执行者
     * @return 行动是否成功（如目标已死则返回false）
     */
    boolean execute(Battle battle, CanHit performer);
}