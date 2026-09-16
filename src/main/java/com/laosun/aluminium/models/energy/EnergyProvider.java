package com.laosun.aluminium.models.energy;

import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Skill;

import java.util.Set;

/**
 * 回能规则（P3）。
 *
 * <p>常规角色用 {@link StandardEnergyProvider}（普攻 20 / 战技 30 / 终结技 5 / 受击 10 /
 * 击杀 5 / 击破 5）；特殊角色以后各自实现本接口，机制侧不需要为角色改一行代码。
 *
 * <p>约定：
 * <ul>
 *   <li>每个方法返回 {@code null} = 该事件不回能，实现类只覆盖自己关心的钩子。</li>
 *   <li>返回的 {@link EnergyGain} 只是「基础值 + 是否吃回能效率」，真正的入账由
 *   {@link CanHit#gainEnergy(EnergyGain)} 做（截断上限、返回实际入账值）。</li>
 *   <li>这里不做任何扣血/上限/死亡判定——那是调用方（{@code Battle}）的事。</li>
 * </ul>
 */
public interface EnergyProvider {

    /**
     * 施放技能（普攻 / 战技 / 其它非终结技槽位）时。
     *
     * @param user       施放者
     * @param skill      施放的技能
     * @param hitTargets 本次实际命中的目标（可能为空集合：增益/治疗类技能打不到人）
     * @return 回能描述，{@code null} = 不回能
     */
    default EnergyGain onSkillCast(CanHit user, Skill skill, Set<? extends CanHit> hitTargets) {
        return null;
    }

    /**
     * 施放终结技时（调用方已先清零，所以这里给的是"放完大招回多少"）。
     *
     * @param user  施放者
     * @param skill 终结技
     * @return 回能描述，{@code null} = 不回能
     */
    default EnergyGain onUltCast(CanHit user, Skill skill) {
        return null;
    }

    /**
     * 受到一次伤害后。
     *
     * @param target 被打的人
     * @param damage 已入账的那一发伤害（调用方只在 {@code isCountsAsAttack()} 时调用）
     * @return 回能描述，{@code null} = 不回能
     */
    default EnergyGain onTakingHit(CanHit target, Damage damage) {
        return null;
    }

    /**
     * 击杀目标后。
     *
     * @param attacker 击杀者（{@code damage.getAttacker()}）
     * @param target   被击杀的目标
     * @return 回能描述，{@code null} = 不回能
     */
    default EnergyGain onKill(CanHit attacker, CanHit target) {
        return null;
    }

    /**
     * 击破弱点后。
     *
     * @param attacker 造成击破的人
     * @param target   被击破的目标
     * @return 回能描述，{@code null} = 不回能
     */
    default EnergyGain onBreak(CanHit attacker, CanHit target) {
        return null;
    }
}
