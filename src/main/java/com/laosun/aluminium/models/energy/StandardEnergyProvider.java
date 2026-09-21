package com.laosun.aluminium.models.energy;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Skill;

import java.util.Set;

/**
 * 常规回能：普攻 20 / 战技 30 / 终结技 5 / 受击 10 / 击杀 5 / 击破 5。
 *
 * <p>数据来源见 {@code ROADMAP.md} 的 P3-0：普攻/战技/终结技取自 tbgd
 * {@code AvatarSkillConfig.SPBase}，受击/击杀/击破的基础值由角色文档反推（文档只写「额外恢复 N 点」）。
 *
 * <p><b>技能回能已改为读数据</b>（{@code SkillData.spBase}，见 {@link com.laosun.aluminium.models.SkillData}）：
 * <ul>
 *   <li>{@code spBase} 为 {@code null} → **这个技能不回能**。这是"层数/特殊资源"角色
 *       （飞霄、黄泉、遐蝶、白厄、昔涟、银狼LV.999）的诚实表达 —— 他们一个技能都不涨能量。
 *       修之前引擎照发 20/30/5，等于凭空给她们造出能量。</li>
 *   <li>否则按数据给（多数是 20/30/5，爻光普攻是 30）。</li>
 * </ul>
 * 常量 {@link Constant#ENERGY_GAIN_BASIC} 等现在只是**兜底/文档**用途（数据缺失时用）。
 *
 * <p>暂时**不处理**的：多段/弹射技能段数乘算（需要 {@code SPHitRatio}，本项目数据里没有）；
 * 追加攻击（{@code AttackType} 为空或非 Normal/BPSkill 的技能）不回能；
 * 秘技 / 迷宫技能不回能；角色级加成与特殊来源交给各自的 provider（P8-3）。
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
            case "Normal", "BPSkill" -> gainOf(skill.getData().getSpBase());
            default -> null;    // Ultra 走 onUltCast；Maze / 追加攻击等本阶段不回能
        };
    }

    @Override
    public EnergyGain onUltCast(CanHit user, Skill skill) {
        // 终结技的回能与技能同一口径：数据里 sp_base 一律 5，但"不回能"的角色是 null。
        Double base = skill == null || skill.getData() == null ? null : skill.getData().getSpBase();
        return gainOf(base);
    }

    /**
     * {@code spBase} → 回能量；{@code null} 或非正数表示**这个技能不回能**。
     *
     * <p>为什么 null 必须当成"不回能"而不是"用默认值兜底"：那 6 个特殊资源角色
     * 在游戏里确实一点能量都不涨，兜底会直接改变她们的强度。
     */
    private static EnergyGain gainOf(Double spBase) {
        if (spBase == null || spBase <= 0) {
            return null;
        }
        return EnergyGain.normal(spBase);
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
