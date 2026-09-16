package com.laosun.aluminium.models.event;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;

import java.util.List;

/**
 * Attack-level event: fired once per attack, after every damage segment of that attack has
 * been settled.
 *
 * <p>Unlike {@link DamageEvent} (per damage instance), this describes "我方的这一次攻击" and is
 * broadcast to <b>every ally</b>（{@code battle.characters}）—— 这正是第三方 kit 的落点：
 * 知更鸟的【协奏】、缇宝的结界都挂在她们自己身上，主C 攻击时她们才出手（"我方目标每次施放攻击后"）。
 *
 * <p>It is fired only from {@code SkillExecutor.execute}. Damage that is not part of a skill
 * activation（附加伤害 / 真伤 / DOT / 击破）goes straight through {@code Battle.applyDamage}
 * and therefore never fires it — 官方定义里附加伤害"不视为造成了 1 次攻击"，这同时天然避免了
 * "附加伤害击杀 → 再触发附加伤害"的递归。
 *
 * @param battle      the running battle
 * @param attacker    the ally that performed the attack
 * @param mainTarget  the target the attacker selected（AOE 时它可能不是命中顺序里的第一个）
 * @param hitTargets  targets the attack actually connected with（含当场死亡的，按命中顺序去重）
 * @param totalDamage 本次攻击各段结算值之和（TODO data (D2)：溢出是否计入尚未定论）
 */
public interface AttackEvent {
    default void afterAttack(Battle battle, CanHit attacker, CanHit mainTarget,
                             List<? extends CanHit> hitTargets, double totalDamage) {
    }
}
