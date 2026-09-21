package com.laosun.aluminium.models.energy;

import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Skill;

import java.util.Set;

/**
 * **不走常规能量**的角色的 {@link EnergyProvider}：5 个钩子全部返回 {@code null}（= 不入账）。
 *
 * <p>适用对象是"层数/特殊资源"角色 —— 他们在游戏里攒的不是能量，
 * 而是【追忆】/【新蕊】/【火种】/点数之类的资源（飞霄 1220、黄泉 1308、遐蝶 1407、
 * 白厄 1408、昔涟 1415、银狼LV.999 1506）。装配点见
 * {@link com.laosun.aluminium.utils.CharacterFactory#create(int, int)}。
 *
 * <p><b>为什么必须是一个独立 provider，而不是在 {@link StandardEnergyProvider} 里判空</b>：
 * <ul>
 *   <li>这是**设计归类**（"这个角色不用常规能量体系"），不是单条数据事实。
 *       P8-0 的三分法把这类判断归给 provider / 装配点，那里也是唯一允许出现 {@code cid} 的地方。</li>
 *   <li>常规 provider 有 5 个钩子。只堵技能那两条（{@code sp_base == null}）会留下
 *       {@code onTakingHit} / {@code onKill} / {@code onBreak} 三条照发能量 —— 实测后果很严重：
 *       {@code castUltra} 的门槛是 {@code currentEnergy >= maxEnergy}，而黄泉的上限只有 **9**、
 *       飞霄/白厄只有 **12**，所以"挨一两下"就能凑满并**放出一个本不该存在的终结技**
 *       （他们的槽位 3 确实是 {@code Ultra} 技能）。</li>
 * </ul>
 *
 * <p>接上它之后，这些角色的表现是"能量恒为 0、永远放不出终结技"—— 这是**显式且可测**的状态，
 * 而不是靠数据巧合挡住一条、漏掉三条。等 P8-8 的 {@code Resource} 抽象落地，
 * 把这个 provider 换成真的资源实现即可（接线点已经就位）。
 *
 * <p>⚠ 注意区分 {@code maxEnergy == 0}（**没有能量条**，如遐蝶 1407）：那种情况
 * {@code CanHit.gainEnergy} 本身就是 no-op，用不用本 provider 都一样。
 * 本 provider 管的是"**有**能量池但不该从常规途径涨"的角色（黄泉 9、飞霄 12…）。
 */
public class NoConventionalEnergyProvider implements EnergyProvider {

    @Override
    public EnergyGain onSkillCast(CanHit user, Skill skill, Set<? extends CanHit> hitTargets) {
        return null;
    }

    @Override
    public EnergyGain onUltCast(CanHit user, Skill skill) {
        return null;
    }

    @Override
    public EnergyGain onTakingHit(CanHit target, Damage damage) {
        return null;
    }

    @Override
    public EnergyGain onKill(CanHit attacker, CanHit target) {
        return null;
    }

    @Override
    public EnergyGain onBreak(CanHit attacker, CanHit target) {
        return null;
    }
}
