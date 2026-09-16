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
     * 造一发击破伤害（**不结算**；调用方拿它去 {@link Battle#applyDamage}）。
     *
     * @param attacker     造成击破的人（等级决定击破基数、属性决定击破特攻）
     * @param enemy        被击破的目标
     * @param element      击破元素（= 触发击破那一段的元素）
     * @param stanceDamage 这一次削掉的韧性点数（技能 {@code stance_list} 的值，单位「点」）
     * @return 已折好 base 的 {@link DamageType#BREAK} 伤害
     * @throws IllegalArgumentException 该等级没有击破基数（数据缺失，fail fast）
     */
    public static Damage build(CanHit attacker, Enemy enemy, DamageElement element, double stanceDamage) {
        Double raw = Constant.BREAKING_RATE.get(attacker.getLevel());
        if (raw == null) {
            throw new IllegalArgumentException("No breaking rate for level " + attacker.getLevel());
        }
        double breakBase = raw / 10.0;                       // 数据文件是 10 倍值
        double breakingEffect = attacker.getAttribute(AttributeType.BREAKING_EFFECT).get();
        return new Damage(attacker, enemy, element, DamageType.BREAK,
                breakBase * (1 + breakingEffect) * stanceDamage);
    }
}
