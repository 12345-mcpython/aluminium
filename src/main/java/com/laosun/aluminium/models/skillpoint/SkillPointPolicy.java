package com.laosun.aluminium.models.skillpoint;

import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Skill;

/**
 * 战技点的**策略**：把"该不该花、花多少、怎么涨"从 {@code Battle} 里抽出来。
 *
 * <p><b>为什么要有这个接口</b>（这是 P8-4 复核后追加的架构决定，见
 * {@code DOC_VS_CODE.md} §F 的 <b>F-8</b>）：战技点是**队伍级**资源，但它的规则会长出来 ——
 * 布洛妮娅「施放战技时 50% 概率恢复 1 点」、素裳「对击破目标施放战技后恢复 1 点」、
 * 花火「上限额外 +2」、过客 4 件套「战斗开始时恢复 1 点」…
 * 如果这些都往 {@code Battle.useSkill} 里加分支，{@code Battle} 会堆满
 * "因为某个角色"的判断，正好违反 P8-0 的三分法。
 *
 * <p>抽成策略后：
 * <ul>
 *   <li>{@code Battle} 只知道"问一次策略能不能出手"（{@link #onSkillCast}），
 *       **不认识任何角色**；</li>
 *   <li>角色级修正将来由**别的实现**（或本实现读取外部注册的效果表）注入，
 *       注入点是装配点（{@code CharacterFactory}，P8-0 唯一允许出现 {@code cid} 的地方）
 *       或 P8-7 的触发器表；</li>
 *   <li>引擎侧不新增对"某个角色"的依赖 —— 这是这条抽象存在的全部理由。</li>
 * </ul>
 *
 * <p><b>与 {@code EnergyProvider} 的分工</b>（照抄那套成功模式，别混）：
 * <ul>
 *   <li>{@code EnergyProvider} 是**每个单位一份**的，因为能量条是**个人**资源；</li>
 *   <li>本接口是**每场战斗一份**的，因为战技点是**全队共享**的。</li>
 * </ul>
 *
 * <p>⚠ 本接口**不承诺**能表达"每当战技点被消耗时…"这类**事件驱动**的角色机制
 * （米沙「我方全体每消耗 1 个战技点 → 下次终结技 +1 段」、花火「我方消耗战技点时
 * 额外回 1 点能量」）。那些要监听"战技点被消耗"这件**事**，属 P8-7 的触发器表
 * （需要新事件 {@code SkillPointSpentEvent}）—— 见 {@code DOC_VS_CODE.md} §F 的 F-4。
 */
public interface SkillPointPolicy {

    /**
     * 某个单位**即将施放**一个技能时调用一次，由策略决定战技点的增减。
     *
     * <p>调用时机是"决定出手"那一层（{@code Battle.useSkill}），**不是**结算伤害之后 ——
     * 与"出手"原子，避免出现"没花出去却打出来了"。
     *
     * @param user  出手者（非 {@code null}；调用方已判过"还活着"）
     * @param skill 要施放的技能（非 {@code null}；其 {@code getData()} 可能为 {@code null}，
     *              例如敌人的 {@code EnemySkill}）
     * @return 这次出手是否**可以继续**；{@code false} 表示资源不足，调用方必须放弃这次出手
     */
    boolean onSkillCast(CanHit user, Skill skill);

    /** 当前值。 */
    int getValue();

    /** 常规上限。 */
    int getMax();

    /**
     * 直接加值（封在常规上限内），返回实际入账量。
     *
     * <p>给"普攻 +1"之外的显式来源用（秘技、遗器、角色机制）。
     */
    int gain(int delta);

    /** 是否够消耗一次（{@code > 0}）。 */
    boolean canAfford();

    /**
     * 扣掉一次消耗。
     *
     * @return 是否成功；{@code false} 表示不足（值不变）
     */
    boolean spend();
}
