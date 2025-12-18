package com.laosun.aluminium.models.event;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Moveable;

/**
 * 战斗事件接口：支持更多灵活的事件回调
 */
public interface MoveableEvent {
    /** 战斗开始时触发 */
    default void onBattleStart(Battle battle, Moveable moveable) {}

    /** 行动前触发（如buff生效、加速） */
    default void onBeforeAction(Battle battle, Moveable moveable) {}

    /** 行动后触发（如持续伤害、能量恢复） */
    default void onAfterAction(Battle battle, Moveable moveable) {}

    /** 受到攻击时触发（如防御反击、伤害减免） */
    default void onBeAttacked(Battle battle, Moveable attacker) {}

    /** 受到伤害时触发（如生命值低于30%时激活护盾） */
    default void onTakeDamage(double damage) {}

    /** 角色死亡时触发（如遗言效果、队友buff） */
    default void onDeath() {}
}