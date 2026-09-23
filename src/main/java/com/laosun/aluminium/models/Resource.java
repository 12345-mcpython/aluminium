package com.laosun.aluminium.models;

/**
 * 一个**队伍级**的数值资源：有当前值、最大容量，可选最大可溢出上限。
 *
 * <p><b>为什么要有这个类</b>：战技点（P8-4）和 P8-8 的层数资源
 * （黄泉【残梦】、飞霄【飞黄】、白厄【火种】、昔涟【追忆】、遐蝶【新蕊】…）本质是
 * 同一个东西 —— 一个会被"某个事件"加减、有容量上限、满了要发信号的计数器。
 * 不抽出来的话，每个角色都要在引擎里加一条"因为某个角色"的分支（违反 P8-0 三分法）。
 *
 * <p><b>三个边界（都实测过，不是随手写的）</b>：
 * <ol>
 *   <li>常规路径（{@link #gainClamped} / {@link #gain}）**不会超过 {@link #getMax()}**；</li>
 *   <li>溢出是**显式且封顶**的：只有 {@link #setMaxOverflow} 配了上限之后，
 *       {@link #gain} 才会把值存到 {@code max} 之上，且封在 {@code max + maxOverflow}。
 *       ⚠ 默认 {@code maxOverflow == 0}，所以"不小心用 gain 就溢出"是不可能的；</li>
 *   <li>{@link #setValue} 是**无保护的原始写入**（向下夹到 0，向上夹到 {@code max + maxOverflow}），
 *       给"存档恢复 / 调试"用 —— 常规玩法请走 gain/spend。</li>
 * </ol>
 *
 * <p>为什么需要溢出：游戏里确实有"上限 5 但可以临时存到 10"的机制 ——
 * 花火终结技「恢复 4/6 个战技点，若恢复时战技点溢出，则记录溢出的战技点数，
 * 最多记录 10 点」（见 {@code 1306_花火.md}）。
 *
 * <p><b>线程模型</b>：和 {@code Battle} 一样按单线程使用，不做同步 ——
 * 一场战斗在一个线程里推进。
 *
 * @see com.laosun.aluminium.models.skillpoint.SkillPointPolicy
 */
public class Resource {

    /**
     * 资源标识（如 {@code "skill_point"}），用于日志与将来的 ResourceManager 注册表。
     */
    private final String id;

    /**
     * 常规上限。{@link #getValue()} 只有在溢出状态下才会超过它。
     */
    private final int max;

    private int value;

    /**
     * 最大可溢出量（默认 0 = 不允许溢出）。见类说明第 2 条。
     */
    private int maxOverflow;

    /**
     * @param id       资源标识
     * @param max      常规上限
     * @param initial  初始值（会夹到 {@code [0, max]}）
     * @throws IllegalArgumentException {@code id} 为空，或 {@code max < 0}
     */
    public Resource(String id, int max, int initial) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Resource id must not be blank");
        }
        if (max < 0) {
            throw new IllegalArgumentException("Resource max must be >= 0, got " + max);
        }
        this.id = id;
        this.max = max;
        this.value = clamp(initial);
    }

    public String getId() {
        return id;
    }

    public int getValue() {
        return value;
    }

    public int getMax() {
        return max;
    }

    public int getMaxOverflow() {
        return maxOverflow;
    }

    /**
     * 当前值离常规上限还差多少（已满或溢出时为 0）。
     */
    public int missingToMax() {
        return Math.max(0, max - value);
    }

    /**
     * 是否达到**常规**上限（溢出时也为 true）。
     */
    public boolean isFull() {
        return value >= max;
    }

    /**
     * 是否已到**绝对**上限（含溢出额度），即"再加也加不进去了"。
     */
    public boolean isCapped() {
        return value >= absoluteMax();
    }

    public boolean isEmpty() {
        return value <= 0;
    }

    /**
     * 加值并**夹在常规上限**内（不允许溢出），返回**实际入账**的量。
     *
     * <p>{@code delta <= 0} 时什么都不做（"加负数"是调用方的 bug，静默忽略比抛异常友好）。
     *
     * @return 实际加进去的量（因为封顶而少于 {@code delta} 时更小；可能为 0）
     */
    public int gainClamped(int delta) {
        if (delta <= 0) {
            return 0;
        }
        int before = value;
        value = Math.min(max, value + delta);
        return value - before;
    }

    /**
     * 加值，**可溢出**到 {@link #getMaxOverflow()} 为止（未配溢出时等价于 {@link #gainClamped}），
     * 返回**实际入账**的量。
     *
     * <p>"溢出"这件事必须由调用方**显式**表达：默认溢出额度是 0，所以这个方法的默认行为
     * 与 {@link #gainClamped} 完全一致。只有配过溢出额度的资源（例如花火在队伍里时的战技点）
     * 才可能存到上限之上。
     */
    public int gain(int delta) {
        if (delta <= 0) {
            return 0;
        }
        int before = value;
        value = Math.min(absoluteMax(), value + delta);
        return value - before;
    }

    /**
     * 扣值，不会低于 0，返回**实际扣掉**的量。
     *
     * @return 实际扣除量；资源为空时是 0（调用方应据此判断"这次消耗没成功"）
     */
    public int spend(int delta) {
        if (delta <= 0) {
            return 0;
        }
        int before = value;
        value = Math.max(0, value - delta);
        return before - value;
    }

    /**
     * 扣掉指定的值，**不够就一点都不扣**（原子语义）。
     *
     * <p>与 {@link #spend} 的区别在这里：战技点不足时必须是"这次没花出去"，
     * 而不是"花到 0 为止"。{@link #spend} 是"能扣多少扣多少"，适合 DOT 掉血那类。
     *
     * @return 是否扣成功（资源不足时 false，且值不变）
     */
    public boolean spendExactly(int delta) {
        if (delta <= 0) {
            return true;
        }
        if (value < delta) {
            return false;
        }
        value -= delta;
        return true;
    }

    /**
     * 设置最大溢出额度（{@code overflow < 0} 视为 0）。
     *
     * <p><b>不变式</b>：{@code value ∈ [0, max + maxOverflow]} **永远成立**。
     * 所以下调额度时，超出新绝对上限的存量会被**夹掉**（例如
     * {@code max=5, overflow=10, value=15} → 把 overflow 设为 0 → value 变成 5）。
     *
     * <p>为什么宁可丢存量也不能让值越界：不变量一旦破了，之后所有
     * {@code isCapped()} / {@code missingToMax()} / {@code gain()} 的判断全部失准，
     * 而且**不报错**（第一版就是这样：只改额度不夹值，于是 value 可以停在 15
     * 而绝对上限是 5，属于静默的非法状态）。
     *
     * <p>现实里这条只会被"战斗中卸下提供溢出额度的 buff"这类情况触发，
     * 且夹掉是合理的（容量没了，多出来的自然存不住）。
     */
    public void setMaxOverflow(int overflow) {
        this.maxOverflow = Math.max(0, overflow);
        // 重新夹一次，保证不变式不被破坏（含"额度变小"与"额度变大"两个方向）
        this.value = clamp(this.value);
    }

    /**
     * 原始写入（无保护，只做夹取）：给存档恢复 / 调试用。
     *
     * <p>夹取范围是 {@code [0, max + maxOverflow]}。
     */
    public void setValue(int newValue) {
        this.value = clamp(newValue);
    }

    /**
     * 清空到 0（战斗开始时重置那类操作用；要恢复到初始值请重新构造或 setValue）。
     */
    public void clear() {
        this.value = 0;
    }

    private int absoluteMax() {
        return max + maxOverflow;
    }

    private int clamp(int raw) {
        return Math.max(0, Math.min(absoluteMax(), raw));
    }

    @Override
    public String toString() {
        return id + "=" + value + "/" + max + (maxOverflow > 0 ? "(+" + maxOverflow + ")" : "");
    }
}
