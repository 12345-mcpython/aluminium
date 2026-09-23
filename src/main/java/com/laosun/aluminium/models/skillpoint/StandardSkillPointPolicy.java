package com.laosun.aluminium.models.skillpoint;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Resource;
import com.laosun.aluminium.models.Skill;

/**
 * 标准战技点策略（P8-4）：开局 3、上限 5、**我方**普攻 +1、战技 -1、其余中性。
 *
 * <p>规则表（见 {@code engine.md} §9.6 的完整对照与差距清单）：
 *
 * <table border="1">
 *   <tr><th>技能类别</th><th>战技点</th></tr>
 *   <tr><td>{@link SkillCategory#NORMAL}</td><td>+{@link Constant#SKILL_POINT_GAIN_BASIC}</td></tr>
 *   <tr><td>{@link SkillCategory#BPSKILL}</td><td>-1，不够则**不能出手**</td></tr>
 *   <tr><td>其他（含 {@code ULTRA}、地图技能、天赋/追加攻击的 {@code UNSPECIFIED}）</td>
 *       <td>中性</td></tr>
 * </table>
 *
 * <p><b>为什么只算我方</b>：敌人也走 {@code Battle.performAction} 出手，技能同样是
 * {@code Normal} —— 不判阵营的话敌人每打一下我方的战技点就 +1。
 * 判据用 {@link com.laosun.aluminium.enums.Camp#PLAYER}（"我方单位"）而不是
 * "是不是玩家操控"：将来加友方召唤物（忆灵，P9-4）时它们**也应该**供点，
 * 这个行为由 {@code SkillPointGameParityTest} 钉住。
 *
 * <p>⚠ <b>已知偏差</b>（{@code DOC_VS_CODE.md} §F 的 <b>F-3</b>）：这里对
 * {@code NORMAL} 一刀切 +1，而游戏里**强化普攻有例外** —— 波提欧的强化普攻
 * 「无法恢复战技点」、青雀的强化普攻「恢复 1 个战技点」。数据里两者都是
 * {@code "Normal"}（无单独类型），所以本实现**对青雀正确、对波提欧错误**。
 * ⚠ **不要改成"强化普攻一律 +0"**（会把青雀改坏）—— 正解是**每个技能自带
 * 战技点增量字段**（数据补全）。本类留了 {@link #gainForCast} 作为覆盖点。
 *
 * <p>⚠ <b>角色级修正没接</b>（同 §F 的 F-4）：布洛妮娅「战技 50% 概率 +1」、
 * 素裳「打击破目标战技 +1」、花火「上限 +2」等全部要等 P8-7 触发器表。
 * 本类**刻意不知道任何角色** —— 加这些时请继承并覆盖 {@link #gainForCast}
 * （或由 P8-7 的效果表驱动），**不要**在这里写 {@code cid} 判断。
 */
public class StandardSkillPointPolicy implements SkillPointPolicy {

    /**
     * 战技点这个资源本身。用 {@link Resource} 而不是裸 {@code int}，
     * 是为了和 P8-8 的层数资源共用一个有边界语义的抽象（见该类说明）。
     */
    private final Resource resource;

    /**
     * 用标准值构造：上限 {@link Constant#SKILL_POINT_MAX}、开局
     * {@link Constant#SKILL_POINT_START}。
     */
    public StandardSkillPointPolicy() {
        this(Constant.SKILL_POINT_MAX, Constant.SKILL_POINT_START);
    }

    /**
     * 用指定的上限/开局构造（给测试与将来的"上限被光锥/角色抬高"用，见 §F 的 F-1）。
     *
     * @param max     常规上限
     * @param initial 开局值（夹到 {@code [0, max]}）
     */
    public StandardSkillPointPolicy(int max, int initial) {
        this.resource = new Resource("skill_point", max, initial);
    }

    /**
     * 暴露底层资源 —— 给"改上限 / 配溢出额度"这类队伍级修正用
     * （花火的上限 +2、溢出储存 10 点都属于这一类）。
     */
    public Resource resource() {
        return resource;
    }

    @Override
    public boolean onSkillCast(CanHit user, Skill skill) {
        if (user == null || user.getCamp() != com.laosun.aluminium.enums.Camp.PLAYER) {
            return true;                        // 敌方行动不碰我方战技点
        }
        SkillCategory category = categoryOf(skill);
        if (category == null) {
            return true;                        // 没有技能数据（敌人技能/空技能）→ 中性
        }
        return switch (category) {
            case NORMAL -> {
                gain(gainForCast(user, skill, category));
                yield true;
            }
            case BPSKILL -> spend();
            // ULTRA / MAZE / MAZE_NORMAL / ASSIST / ELATION_DAMAGE /
            // UNSPECIFIED（天赋·追加攻击）/ UNKNOWN（数据不认识）→ 中性
            default -> true;
        };
    }

    /**
     * 一次普攻**该回多少**战技点（默认 {@link Constant#SKILL_POINT_GAIN_BASIC}）。
     *
     * <p>这是本类的**首要覆盖点**：将来"花火在队伍里 +1"「强化普攻不回点」这类
     * 角色级修正，由子类或 P8-7 的效果表覆盖它产生，而**不需要改 {@code Battle}**。
     *
     * @param user     出手者
     * @param skill    技能
     * @param category 已解析好的类别（调用方保证非 {@code null}）
     * @return 要加的战技点（{@code <= 0} 表示不加）
     */
    protected int gainForCast(CanHit user, Skill skill, SkillCategory category) {
        return Constant.SKILL_POINT_GAIN_BASIC;
    }

    /**
     * 解析技能类别。
     *
     * <p>⚠ 走 {@code SkillData.getCategory()} 而不是裸字符串 {@code switch} ——
     * 后者在数据侧改拼写或新增取值时会**静默失配**（见 {@code DOC_VS_CODE.md} §F 的 F-6）。
     *
     * @return {@code null} 表示"没有类别可言"（技能或技能数据为空），调用方按中性处理
     */
    protected SkillCategory categoryOf(Skill skill) {
        if (skill == null || skill.getData() == null) {
            return null;
        }
        return skill.getData().getCategory();
    }

    @Override
    public int getValue() {
        return resource.getValue();
    }

    @Override
    public int getMax() {
        return resource.getMax();
    }

    @Override
    public int gain(int delta) {
        return resource.gainClamped(delta);
    }

    @Override
    public boolean canAfford() {
        return resource.getValue() > 0;
    }

    @Override
    public boolean spend() {
        return resource.spendExactly(1);
    }
}
