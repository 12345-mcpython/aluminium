package com.laosun.aluminium.models.energy;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Skill;

import java.util.Set;

/**
 * 常规回能：普攻 20 / 战技 30 / 终结技 5 / 受击 10 / 击杀 5 / 击破 5。
 *
 * <p>数据来源见 {@code ROADMAP.md} 的 P3-0：普攻/战技/终结技的常规档取自 tbgd
 * {@code AvatarSkillConfig.SPBase}（终结技一律 5，多段技能按每段折算后总量同为 30），
 * 受击/击杀/击破的基础值由角色文档反推（文档只写「额外恢复 N 点」），
 * 因此数值集中放在 {@link Constant#ENERGY_GAIN_BASIC} 一类的常量里。
 *
 * <p><b>为什么又回到常量</b>（2026-09-21）：曾短暂改成读 {@code SkillData.spBase}，
 * 但数据里多段/弹射技能的 {@code spBase} 是**每段值**（艾丝妲 6、瓦尔特 10），
 * 乘段数才对，而段数乘算依赖能力配置的 {@code SPHitRatio}（本项目数据里没有）——
 * 直接取原值会让那 6 个角色偏低。常量给出的是**正确总量**，所以退回常量更准。
 * 数据化的正路见 ROADMAP P3-4（聚合 {@code SPHitRatio} 后再接）。
 *
 * <p><b>不走常规能量的角色不在这里判断</b>：飞霄/黄泉/遐蝶/白厄/昔涟/银狼LV.999
 * 用的是层数/特殊资源，由 {@link NoConventionalEnergyProvider} 在装配点注入 ——
 * 见 {@code CharacterFactory} 与 {@code engine.md} §9.5。
 *
 * <p>暂时**不处理**的：追加攻击（{@code AttackType} 为空或非 Normal/BPSkill 的技能）不回能，
 * 秘技 / 迷宫技能不回能，角色级加成与特殊来源交给各自的 provider（P8-3）。
 */
public class StandardEnergyProvider implements EnergyProvider {

    @Override
    public EnergyGain onSkillCast(CanHit user, Skill skill, Set<? extends CanHit> hitTargets) {
        if (skill == null || skill.getData() == null) {
            return null;
        }
        // 追加攻击/天赋槽位的攻击类型在数据里是空的（null），不能直接 switch
        String attackType = skill.getData().getSkillType();
        if (attackType == null) {
            return null;
        }
        return switch (attackType) {
            case "Normal" -> EnergyGain.normal(Constant.ENERGY_GAIN_BASIC);
            case "BPSkill" -> EnergyGain.normal(Constant.ENERGY_GAIN_SKILL);
            default -> null;    // Ultra 走 onUltCast；Maze / 追加攻击等本阶段不回能
        };
    }

    @Override
    public EnergyGain onUltCast(CanHit user, Skill skill) {
        return EnergyGain.normal(Constant.ENERGY_GAIN_ULTRA);
    }

    @Override
    public EnergyGain onTakingHit(CanHit target, Damage damage) {
        return damage == null ? null : EnergyGain.normal(Constant.ENERGY_GAIN_HIT);
    }

    @Override
    public EnergyGain onKill(CanHit attacker, CanHit target) {
        return attacker == null ? null : EnergyGain.normal(Constant.ENERGY_GAIN_KILL);
    }

    @Override
    public EnergyGain onBreak(CanHit attacker, CanHit target) {
        return attacker == null ? null : EnergyGain.normal(Constant.ENERGY_GAIN_BREAK);
    }
}
