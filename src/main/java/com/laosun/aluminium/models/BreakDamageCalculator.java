package com.laosun.aluminium.models;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;

/**
 * 击破伤害（P4-3）。
 *
 * <pre>
 * 击破伤害 = 击破基数(等级) × (1 + 击破特攻) × 削韧值 × 防御区 × 抗性区 × 减伤区
 * </pre>
 *
 * <p><b>不可暴击、不吃攻击力/增伤</b>——所以只把 {@code 击破基数 × (1+击破特攻) × 削韧值} 折进
 * {@link Damage} 的 base，防御区/抗性区交给 {@link Battle#applyDamage} 统一装配，
 * 增伤与暴击由 {@link DamageType#BREAK} 自己的 {@code (crittable=false, boostable=false)} 挡掉。
 *
 * <p><b>单位必须成套（HSR.md §7.1）</b>：文档给的是 80 级「基础击破基数 3767」（削韧单位"常规"，普攻=1）、
 * 「超击破 376.7」（削韧单位"点"，普攻=10）。本类用 {@code breaking_rate.json / 10 = 376.75535}，
 * 因此传入的 {@code stanceDamage} **必须是"点"刻度**（例：30 点普攻 × 2.5 击破加成 = 112.5）。
 * 哪天改用 3767，削韧值要同步 /10，否则差 10 倍；P4-6 超击破同刻度。
 */
public final class BreakDamageCalculator {

    private BreakDamageCalculator() {
    }

    /**
     * 该等级的击破基数（已按项目单位 {@code /10}）。
     *
     * @param attacker 攻击者（用它的等级）
     * @return 击破基数（80 级 = 376.75535）
     * @throws IllegalArgumentException 该等级没有数据（fail fast，别静默算成 0）
     */
    public static double breakBaseOf(CanHit attacker) {
        Double raw = Constant.BREAKING_RATE.get(attacker.getLevel());
        if (raw == null) {
            throw new IllegalArgumentException("No breaking rate for level " + attacker.getLevel());
        }
        return raw / 10.0;                                   // 数据文件是 10 倍值
    }

    /**
     * 造一发击破伤害（**不结算**；调用方拿它去 {@link Battle#applyDamage}）。
     *
     * <p>回能口径由调用方决定：这一发是**同一次攻击派生**出来的额外伤害，所以
     * {@code Battle.reduceToughness} 用 {@code EnergyGrant.KILL_ONLY} 结算它 ——
     * 不给受击方回能（"一次攻击行为只给受击方回一次能"），但击杀仍记给攻击者。
     *
     * <p>注意这里**刻意不置** {@code notCountsAsAttack()}：击破伤害本身**是攻击伤害**
     * （只是不是"一次攻击行为"）。把它标成"不算攻击"会让将来"造成攻击伤害时触发"的效果
     * 也一起失效。"派生段"这件事归回能参数管，不归那个标志管。
     *
     * @param attacker     造成击破的人（等级决定击破基数、属性决定击破特攻）
     * @param enemy        被击破的目标
     * @param element      击破元素（= 触发击破那一段的元素）
     * @param stanceDamage 这一次削掉的韧性点数（技能 {@code stance_list} 的值，单位「点」）
     * @return 已折好 base 的 {@link DamageType#BREAK} 伤害
     * @throws IllegalArgumentException 该等级没有击破基数（数据缺失，fail fast）
     */
    public static Damage build(CanHit attacker, Enemy enemy, DamageElement element, double stanceDamage) {
        double breakingEffect = attacker.getAttribute(AttributeType.BREAKING_EFFECT).get();
        return new Damage(attacker, enemy, element, DamageType.BREAK,
                breakBaseOf(attacker) * (1 + breakingEffect) * stanceDamage);
    }

    /**
     * 造一发**超击破**伤害（P4-6）：结构与 {@link #build} 相同，多一个独立增伤乘区、
     * 类型换成 {@link DamageType#SUPER_BREAK}。
     *
     * <pre>
     * base = 击破基数(等级) × (1 + 击破特攻) × 超出削韧值 × (1 + 超击破提高)
     * </pre>
     *
     * <p>与击破伤害的两点区别：
     * <ul>
     *   <li>传入的 {@code superBreakStance} 是**超出部分**（标称削韧值 − 实际削掉的值），
     *       不是整发削韧值 —— 见 {@link Battle.StanceResult}。破韧的那一发里，
     *       前面那部分削韧已经用于击破伤害，这里只能用超出的那一半，否则同一个标称值被用两次；</li>
     *   <li>多乘 {@code 1 + Constant.SUPER_BREAK_BOOST}（独立增伤乘区，与常规增伤区无关）。</li>
     * </ul>
     *
     * <p>不可暴击、不吃常规增伤 —— 由 {@code DamageType.SUPER_BREAK} 自己的两个 flag 挡掉，
     * 所以这里同样只折 base，防御区/抗性区交给 {@link Battle#applyDamage} 统一装配。
     *
     * <p><b>为什么这里置 {@code notCountsAsAttack()} 而 {@link #build} 不置</b>：
     * 两个入口的"结算上下文"不同 ——
     * <ul>
     *   <li>{@link #build}（击破伤害）是在 {@code Battle.reduceToughness} 内部结算的，
     *       那里已经能显式传 {@code EnergyGrant.KILL_ONLY}，所以不需要靠标志位表达"派生段"，
     *       也就能保住"击破伤害是攻击伤害"的语义；</li>
     *   <li>本方法（超击破）是由 {@code SkillExecutor} 构造后、经**公开入口**
     *       {@link Battle#applyDamage} 结算的，没有地方传回能参数，所以只能把这个语义
     *       编码在伤害对象上。</li>
     * </ul>
     * 效果一致：都不给受击方回能，击杀都记给攻击者（击杀侧不看这个标志）。
     *
     * @param attacker         施放者（等级决定击破基数、属性决定击破特攻）
     * @param enemy            目标（应处于击破状态）
     * @param element          这一段的元素
     * @param superBreakStance 超出剩余韧性的那部分削韧值
     * @return 已折好 base 的 {@link DamageType#SUPER_BREAK} 伤害
     * @throws IllegalArgumentException 该等级没有击破基数（数据缺失，fail fast）
     */
    public static Damage buildSuperBreak(CanHit attacker, Enemy enemy, DamageElement element,
                                         double superBreakStance) {
        double breakingEffect = attacker.getAttribute(AttributeType.BREAKING_EFFECT).get();
        double base = breakBaseOf(attacker) * (1 + breakingEffect) * superBreakStance
                * (1 + Constant.SUPER_BREAK_BOOST);
        return new Damage(attacker, enemy, element, DamageType.SUPER_BREAK, base).notCountsAsAttack();
    }
}
