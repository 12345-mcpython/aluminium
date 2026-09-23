package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.models.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 重构（2026-09-23）引入的两个通用抽象：
 * {@link SkillCategory}（数据里的 {@code attack_type} 枚举化）与
 * {@link Resource}（有边界的队伍级资源）。
 *
 * <p>它们存在的理由都是**稳定性**：
 * <ul>
 *   <li>{@code SkillCategory} 消灭"裸字符串 switch → 数据改拼写就静默失配"这一类 bug
 *       （{@code DOC_VS_CODE.md} §F 的 F-6）；</li>
 *   <li>{@code Resource} 给"上限 / 溢出 / 原子消耗"一个有名字的边界语义，
 *       战技点与 P8-8 的层数资源共用（{@code DOC_VS_CODE.md} §F 的 F-1/F-7）。
 * </ul>
 */
public class SkillCategoryAndResourceTest {

    // ==================================================================
    // SkillCategory：解析必须稳健（数据是外部产物）
    // ==================================================================

    /** 数据里真实存在的 7 个取值都要解析到位。 */
    @Test
    public void knownValuesRoundTrip() {
        Assertions.assertEquals(SkillCategory.NORMAL, SkillCategory.fromString("Normal"));
        Assertions.assertEquals(SkillCategory.BPSKILL, SkillCategory.fromString("BPSkill"));
        Assertions.assertEquals(SkillCategory.ULTRA, SkillCategory.fromString("Ultra"));
        Assertions.assertEquals(SkillCategory.MAZE_NORMAL, SkillCategory.fromString("MazeNormal"));
        Assertions.assertEquals(SkillCategory.MAZE, SkillCategory.fromString("Maze"));
        Assertions.assertEquals(SkillCategory.ASSIST, SkillCategory.fromString("Assist"));
        Assertions.assertEquals(SkillCategory.ELATION_DAMAGE, SkillCategory.fromString("ElationDamage"));
    }

    /** {@code value()} 必须能反查回数据原值 —— 否则将来写回数据会对不上。 */
    @Test
    public void valueMatchesTheDataString() {
        Assertions.assertEquals("Normal", SkillCategory.NORMAL.value());
        Assertions.assertEquals("BPSkill", SkillCategory.BPSKILL.value());
        Assertions.assertEquals("Ultra", SkillCategory.ULTRA.value());
        Assertions.assertEquals("MazeNormal", SkillCategory.MAZE_NORMAL.value());
        Assertions.assertEquals("Maze", SkillCategory.MAZE.value());
        Assertions.assertEquals("Assist", SkillCategory.ASSIST.value());
        Assertions.assertEquals("ElationDamage", SkillCategory.ELATION_DAMAGE.value());
    }

    /**
     * {@code null} / 空串 → {@code UNSPECIFIED}，**不是** {@code UNKNOWN}。
     *
     * <p>这个区分很重要：天赋与追加攻击在数据里的 {@code attack_type} 就是空
     * （实测 94 条），那是**合法的空**，不是"数据有问题"。
     */
    @Test
    public void nullAndBlankAreUnspecifiedNotUnknown() {
        Assertions.assertEquals(SkillCategory.UNSPECIFIED, SkillCategory.fromString(null));
        Assertions.assertEquals(SkillCategory.UNSPECIFIED, SkillCategory.fromString(""));
        Assertions.assertEquals(SkillCategory.UNSPECIFIED, SkillCategory.fromString("   "));
        Assertions.assertTrue(SkillCategory.UNSPECIFIED.isUnspecified());
        Assertions.assertFalse(SkillCategory.UNSPECIFIED.isKnownValue(), "合法空不算'认识的取值'");
    }

    /**
     * ⚠ <b>核心稳定性断言</b>：不认识的取值**不抛异常**，降级成 {@code UNKNOWN}。
     *
     * <p>数据是外部产物 —— 多一个新类型就炸引擎是稳定性问题。这里选择
     * "安全降级 + 可观测"（{@code isKnownValue()} 让调用方决定要不要出声）。
     */
    @Test
    public void unknownValueDegradesInsteadOfThrowing() {
        SkillCategory weird = SkillCategory.fromString("SomeFutureType");
        Assertions.assertEquals(SkillCategory.UNKNOWN, weird);
        Assertions.assertFalse(weird.isKnownValue());
        Assertions.assertFalse(weird.isUnspecified(), "不认识 ≠ 合法空，两者要能区分");

        // 极端输入也不能炸
        Assertions.assertEquals(SkillCategory.UNKNOWN, SkillCategory.fromString("\u0000"));
        Assertions.assertEquals(SkillCategory.UNKNOWN, SkillCategory.fromString("Normal2"));
    }

    /** 大小写不敏感 + 去空白 —— 数据侧风格不统一时不该失配。 */
    @Test
    public void parsingIsCaseInsensitiveAndTrims() {
        Assertions.assertEquals(SkillCategory.NORMAL, SkillCategory.fromString("normal"));
        Assertions.assertEquals(SkillCategory.BPSKILL, SkillCategory.fromString("bpskill"));
        Assertions.assertEquals(SkillCategory.ULTRA, SkillCategory.fromString("  Ultra  "));
        Assertions.assertEquals(SkillCategory.ELATION_DAMAGE, SkillCategory.fromString("elationdamage"));
    }

    /** 只有普攻/战技/终结技算"战斗内主动出手"；地图技能与空值都不算。 */
    @Test
    public void combatActionClassification() {
        Assertions.assertTrue(SkillCategory.NORMAL.isCombatAction());
        Assertions.assertTrue(SkillCategory.BPSKILL.isCombatAction());
        Assertions.assertTrue(SkillCategory.ULTRA.isCombatAction());

        Assertions.assertFalse(SkillCategory.MAZE_NORMAL.isCombatAction(), "地图普攻在战斗外");
        Assertions.assertFalse(SkillCategory.MAZE.isCombatAction(), "秘技在战斗外");
        Assertions.assertFalse(SkillCategory.UNSPECIFIED.isCombatAction(), "天赋/追加攻击不是主动出手");
        Assertions.assertFalse(SkillCategory.UNKNOWN.isCombatAction(), "不认识的取值不能默认当成主动出手");
        Assertions.assertFalse(SkillCategory.ASSIST.isCombatAction());
        Assertions.assertFalse(SkillCategory.ELATION_DAMAGE.isCombatAction());
    }

    // ==================================================================
    // Resource：三个边界
    // ==================================================================

    /** 常规路径不越过上限，且返回**实际**入账量。 */
    @Test
    public void gainClampedStopsAtMax() {
        Resource r = new Resource("sp", 5, 3);

        Assertions.assertEquals(2, r.gainClamped(2), "3 + 2 = 5，全额入账");
        Assertions.assertEquals(5, r.getValue());
        Assertions.assertTrue(r.isFull());

        Assertions.assertEquals(0, r.gainClamped(1), "已满 → 实际入账 0");
        Assertions.assertEquals(0, r.gainClamped(100), "一次加 100 也是 0");
        Assertions.assertEquals(5, r.getValue());
    }

    /** 加非正数是调用方的 bug：静默忽略，**不能**变成扣值。 */
    @Test
    public void nonPositiveGainIsIgnored() {
        Resource r = new Resource("sp", 5, 3);

        Assertions.assertEquals(0, r.gainClamped(0));
        Assertions.assertEquals(0, r.gainClamped(-5));
        Assertions.assertEquals(0, r.gain(-5));
        Assertions.assertEquals(3, r.getValue(), "不能被'加负数'扣下去");
    }

    /** 默认**不允许溢出**：没配额度时 {@code gain} 与 {@code gainClamped} 等价。 */
    @Test
    public void overflowIsOffByDefault() {
        Resource r = new Resource("sp", 5, 5);
        Assertions.assertEquals(0, r.getMaxOverflow());

        Assertions.assertEquals(0, r.gain(3), "没配溢出额度 → 加不进去");
        Assertions.assertEquals(5, r.getValue());
    }

    /**
     * 配了溢出额度才能存到上限之上，且**封在 max + overflow**。
     *
     * <p>对应花火终结技「恢复 4/6 个战技点，若恢复时战技点溢出，则记录溢出的战技点数，
     * 最多记录 10 点」（{@code 1306_花火.md}）—— 引擎侧只提供"可溢出且封顶"的能力。
     */
    @Test
    public void overflowIsExplicitAndCapped() {
        Resource r = new Resource("sp", 5, 5);
        r.setMaxOverflow(10);

        Assertions.assertEquals(6, r.gain(6), "5 + 6 = 11（≤ 15）全额入账");
        Assertions.assertEquals(11, r.getValue());
        Assertions.assertTrue(r.isFull(), "超过常规上限了，当然算 full");
        Assertions.assertFalse(r.isCapped(), "但还没到 5 + 10 = 15");

        Assertions.assertEquals(4, r.gain(100), "15 - 11 = 4，封在绝对上限");
        Assertions.assertEquals(15, r.getValue());
        Assertions.assertTrue(r.isCapped());
    }

    /**
     * 下调溢出额度会把**越界的存量夹掉**，保证不变式 {@code value ≤ max + overflow} 成立。
     *
     * <p>⚠ 这是我第一版写错的地方：当时只改额度不夹值，于是能造出
     * {@code max=5, overflow=0, value=15} 这种非法状态 —— 之后所有
     * {@code isCapped()} / {@code gain()} 的判断都会失准，而且**不报错**。
     */
    @Test
    public void loweringOverflowReclampsToKeepTheInvariant() {
        Resource r = new Resource("sp", 5, 5);
        r.setMaxOverflow(10);
        r.gain(10);
        Assertions.assertEquals(15, r.getValue());
        Assertions.assertTrue(r.isCapped());

        r.setMaxOverflow(0);
        Assertions.assertEquals(5, r.getValue(), "越界的存量被夹到新的绝对上限");
        Assertions.assertTrue(r.isCapped(), "5 就是现在的绝对上限");
        Assertions.assertTrue(r.isFull());
        Assertions.assertEquals(0, r.missingToMax(), "不变式没破：value ≤ max");
        Assertions.assertEquals(0, r.gain(1), "满的，加不进去");
    }

    /** 上调额度不会动存量，只是之后能加更多。 */
    @Test
    public void raisingOverflowKeepsCurrentValue() {
        Resource r = new Resource("sp", 5, 3);
        r.setMaxOverflow(10);

        Assertions.assertEquals(3, r.getValue(), "存量不动");
        Assertions.assertEquals(8, r.gain(8), "5 + 10 = 15 是新绝对上限");
        Assertions.assertEquals(11, r.getValue());
    }

    /** 负的溢出额度视为 0。 */
    @Test
    public void negativeOverflowIsClampedToZero() {
        Resource r = new Resource("sp", 5, 0);
        r.setMaxOverflow(-3);
        Assertions.assertEquals(0, r.getMaxOverflow());
        Assertions.assertEquals(5, r.gain(5));
        Assertions.assertEquals(0, r.gain(1));
    }

    /** {@code spend} = "能扣多少扣多少"；{@code spendExactly} = "不够就一点都不扣"。 */
    @Test
    public void spendVersusSpendExactly() {
        Resource r = new Resource("sp", 5, 3);

        // 能扣多少扣多少（DOT 掉血那类语义）
        Assertions.assertEquals(3, r.spend(10), "只有 3，全扣掉");
        Assertions.assertEquals(0, r.getValue());

        // 原子语义（战技点那类：不足 = 这次没花出去）
        r.setValue(2);
        Assertions.assertTrue(r.spendExactly(2));
        Assertions.assertEquals(0, r.getValue());

        Assertions.assertFalse(r.spendExactly(1), "不够 → 失败");
        Assertions.assertEquals(0, r.getValue(), "失败的消耗不能扣成负数");
    }

    /** {@code setValue} 是无保护的原始写入，但**仍然夹取**（存档恢复用）。 */
    @Test
    public void setValueClampsButDoesNotFail() {
        Resource r = new Resource("sp", 5, 0);

        r.setValue(-10);
        Assertions.assertEquals(0, r.getValue());
        r.setValue(999);
        Assertions.assertEquals(5, r.getValue());

        r.setMaxOverflow(3);
        r.setValue(999);
        Assertions.assertEquals(8, r.getValue(), "夹到 max + overflow");
    }

    /** 初始值也会被夹取 —— 构造时给越界的值不该炸，也不该留下非法状态。 */
    @Test
    public void initialValueIsClamped() {
        Assertions.assertEquals(5, new Resource("sp", 5, 99).getValue());
        Assertions.assertEquals(0, new Resource("sp", 5, -99).getValue());
    }

    /** 非法构造参数要**快速失败**（这类是编码错误，不是数据错误，不该静默）。 */
    @Test
    public void invalidConstructionFailsFast() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Resource(null, 5, 0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Resource("  ", 5, 0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Resource("sp", -1, 0));

        // max = 0 是合法的：一个"只读"资源
        Resource zero = new Resource("zero", 0, 0);
        Assertions.assertTrue(zero.isFull());
        Assertions.assertTrue(zero.isCapped());
        Assertions.assertEquals(0, zero.gain(5));
    }

    /** {@code missingToMax} 与 {@code isEmpty} 的边界。 */
    @Test
    public void queryHelpers() {
        Resource r = new Resource("sp", 5, 0);
        Assertions.assertTrue(r.isEmpty());
        Assertions.assertEquals(5, r.missingToMax());

        r.gain(3);
        Assertions.assertFalse(r.isEmpty());
        Assertions.assertEquals(2, r.missingToMax());

        r.gain(2);
        Assertions.assertEquals(0, r.missingToMax());

        r.setMaxOverflow(5);
        r.gain(3);
        Assertions.assertEquals(0, r.missingToMax(), "溢出时也是 0，不是负数");
    }
}
