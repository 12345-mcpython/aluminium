package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EnemySkillData;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;

import java.util.List;

/**
 * 敌人技能（P5-3）：从 {@code enemy_skills.json} 的数据驱动，不是硬编码。
 *
 * <p>与角色技能的区别：它**不走** {@link SkillData}/倍率表（那是角色技能的结构），
 * 而是直接用"攻击力 × 倍率 × 段数"出伤害。所以 {@link #getData()} 返回 {@code null}，
 * {@link #execute} 全自定义 —— 这也是 {@code SkillExecutor} 不该被它复用的原因
 * （角色技能那套削韧/形状分派逻辑对敌人不适用：敌人不打韧性条）。
 *
 * <p>每段独立走 {@link Battle#applyDamage}：**每段独立判定暴击、独立结算**（与角色技能一致）。
 *
 * <p>⚠ 倍率的来源见 {@link EnemySkillData}：数据源里没有敌人技能表，这些值是猜的。
 *
 * @param element    伤害元素（生成时已从数据 / 怪物 {@code stance_type} 解析好，不为 null）
 * @param multiplier 倍率（伤害 base = 攻击力 × multiplier）
 * @param hits       段数（至少 1）
 * @param type       伤害类型
 */
public class EnemySkill extends Skill {

    private final DamageElement element;
    private final double multiplier;
    private final int hits;
    private final DamageType type;

    public EnemySkill(DamageElement element, double multiplier, int hits, DamageType type) {
        this.element = element == null ? DamageElement.PHYSICAL : element;
        this.multiplier = multiplier;
        this.hits = Math.max(1, hits);
        this.type = type == null ? DamageType.NORMAL : type;
    }

    public DamageElement getElement() {
        return element;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public int getHits() {
        return hits;
    }

    @Override
    public int getLevel() {
        return 1;
    }

    /**
     * {@inheritDoc}
     *
     * @return 永远 {@code null}：敌人技能不用角色的倍率表，执行逻辑全在 {@link #execute}
     */
    @Override
    public SkillData getData() {
        return null;
    }

    /**
     * 对主目标连续打 {@link #hits} 段。
     *
     * <p>只打"主目标"：敌人的多段技能在这里是"同一目标多段"，不做扩散/群攻
     * （那需要按技能形状分派，留到 P9-2 接真实技能表时再做）。
     *
     * @param battle 进行中的战斗
     * @param user   施加者（敌人）
     * @param target 调用方选好的目标列表（只取第一个）
     */
    @Override
    public void execute(Battle battle, CanHit user, List<? extends CanHit> target) {
        if (target == null || target.isEmpty()) {
            return;
        }
        CanHit victim = target.getFirst();
        if (victim == null || victim.isDeath()) {
            return;
        }
        double base = user.getAttribute(AttributeType.ATTACK).get() * multiplier;
        for (int i = 0; i < hits; i++) {
            if (victim.isDeath()) {
                break;                               // 中途打死就不再补刀（不鞭尸）
            }
            battle.applyDamage(victim, new Damage(user, victim, element, type, base));
        }
    }
}
