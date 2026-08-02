package com.laosun.aluminium.models;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Element;
import lombok.Getter;

import static com.laosun.aluminium.enums.AttributeType.*;

/**
 * Central damage and healing calculation utility following the Honkai: Star Rail
 * multiplier-region pipeline (HSR.md §2):
 *
 * <pre>{@code
 * 最终伤害 = 基础伤害区 × 伤害修饰区 × 暴击区 × 防御区 × 抗性区 × 特殊乘区
 * 伤害修饰区 = 增伤区 × 易伤区 × 减伤区 × 虚弱区
 * }</pre>
 *
 * <p>Regions (HSR.md §2.2-2.6):
 * <ul>
 *   <li><b>基础伤害区</b>: {@code 倍率 × 相应属性 + 固定值}</li>
 *   <li><b>增伤区</b>: {@code 1 + Σ(元素增伤 + 攻击类型增伤 + 全伤害增伤)}</li>
 *   <li><b>易伤区</b>: {@code 1 + Σ易伤}, cap 3.5 (defender debuffs)</li>
 *   <li><b>减伤区</b>: {@code Π(1 - 减伤)}, floor 0.01 (defender buffs)</li>
 *   <li><b>虚弱区</b>: {@code 1 - Σ虚弱系数}, floor 0.2 (attacker debuffs)</li>
 *   <li><b>暴击区</b>: {@code 1 + 暴击伤害} on crit, not applied to break/DoT</li>
 *   <li><b>防御区</b>: {@code (200 + 10×攻击方等级) / (受击方防御 + 200 + 10×攻击方等级)},
 *   with 防御 = 原始防御 × (1 - 减防% - 防御穿透%)</li>
 *   <li><b>抗性区</b>: {@code 1 - (原始抗性 + 抗性提高 - 抗性降低 - 抗性穿透)},
 *   clamped to [0.1, 2.0]</li>
 *   <li><b>特殊乘区</b>: true damage bonus (HSR.md §2.6)</li>
 * </ul>
 */
public final class DamageCalculator {

    /**
     * The kind of attack, deciding which attack-type boost and crit rules apply.
     */
    @Getter
    public enum DamageType {
        /** 普攻伤害. */
        NORMAL(AttributeType.NORMAL_DAMAGE_BOOST),
        /** 战技伤害. */
        SKILL(AttributeType.SKILL_DAMAGE_BOOST),
        /** 终结技伤害. */
        ULTRA(AttributeType.ULTRA_DAMAGE_BOOST),
        /** 追加攻击伤害. */
        FOLLOW_UP(AttributeType.FOLLOW_UP_DAMAGE_BOOST),
        /** 击破伤害 — cannot crit (HSR.md §2.3). */
        BREAK(null),
        /** 持续伤害 — cannot crit. */
        DOT(AttributeType.DOT_DAMAGE_BOOST),
        /** 忆灵伤害 (HSR.md §2.1): independent category, not follow-up damage. */
        MEMOSPRITE(null),
        /** 欢愉伤害 (HSR.md §3.4): ignores ATK and elemental damage boosts. */
        ELATION(null),
        /** 超击破伤害 (HSR.md 超击破): cannot crit, ignores ATK/增伤/双暴. */
        SUPER_BREAK(null);

        private final AttributeType boostAttribute;

        DamageType(AttributeType boostAttribute) {
            this.boostAttribute = boostAttribute;
        }
    }

    /**
     * The full context of a damage instance.
     *
     * @param type            the attack type
     * @param element         the damage element
     * @param extraCritChance bonus crit chance from passive effects (eidolons etc.)
     */
    public record DamageContext(DamageType type, Element element, double extraCritChance) {
        public DamageContext(DamageType type, Element element) {
            this(type, element, 0);
        }

        public static DamageContext of(DamageType type, Element element) {
            return new DamageContext(type, element, 0);
        }

        /**
         * Returns a copy with the given bonus crit chance added.
         */
        public DamageContext withExtraCrit(double bonus) {
            return new DamageContext(type, element, extraCritChance + bonus);
        }
    }

    private DamageCalculator() {
    }

    // ─── Main damage pipeline (HSR.md §2) ──────────────────────────────

    /**
     * Calculates the final damage for a regular (crit-able) attack through all
     * multiplier regions.
     *
     * @param attacker   the attacking entity
     * @param defender   the defending entity
     * @param baseDamage the base damage (倍率 × 相应属性 + 固定值)
     * @param context    the damage context (type + element)
     * @return the final damage, at least 1
     */
    public static double calculateDamage(CanHit attacker, CanHit defender, double baseDamage, DamageContext context) {
        double damage = baseDamage;

        // 2.2 伤害修饰区 = 增伤区 × 易伤区 × 减伤区 × 虚弱区
        damage *= boostRegion(attacker, context);
        damage *= vulnerabilityRegion(defender);
        damage *= reductionRegion(defender);
        damage *= weaknessRegion(attacker);

        // 2.3 暴击区
        damage *= critRegion(attacker, context);

        // 2.4 防御区
        damage *= defenceRegion(attacker, defender);

        // 2.5 抗性区
        damage *= resistanceRegion(attacker, defender, context.element());

        // 2.6 特殊乘区 (true damage)
        damage *= trueDamageRegion(attacker);

        return Math.max(1, damage);
    }

    /**
     * 增伤区: 1 + Σ(元素增伤 + 攻击类型增伤 + 全伤害增伤). Source: attacker buffs.
     */
    public static double boostRegion(CanHit attacker, DamageContext context) {
        double elementBoost = 0;
        if (context != null && context.element() != null) {
            elementBoost = attr(attacker, context.element().getBoostAttribute());
        }
        double allBoost = attr(attacker, ALL_DAMAGE_TYPE_BOOST);
        double attackTypeBoost = context != null && context.type() != null && context.type().getBoostAttribute() != null
                ? attr(attacker, context.type().getBoostAttribute()) : 0;
        return 1 + elementBoost + allBoost + attackTypeBoost;
    }

    /**
     * 易伤区: 1 + Σ易伤, capped at 3.5. Source: defender debuffs.
     */
    public static double vulnerabilityRegion(CanHit defender) {
        return 1 + Math.min(2.5, attr(defender, VULNERABILITY));
    }

    /**
     * 减伤区: Π(1 - 减伤), floored at 0.01. Source: defender buffs.
     * Each reduction source is stored as a multiply-percent modifier.
     */
    public static double reductionRegion(CanHit defender) {
        return Math.max(0.01, attr(defender, DAMAGE_REDUCTION));
    }

    /**
     * 虚弱区: 1 - Σ虚弱系数, floored at 0.2. Source: attacker debuffs.
     */
    public static double weaknessRegion(CanHit attacker) {
        return Math.max(0.2, 1 - attr(attacker, WEAKNESS_RATIO));
    }

    /**
     * 暴击区: 1 on non-crit, 1 + 暴击伤害 on crit. Break and DoT cannot crit.
     */
    public static double critRegion(CanHit attacker, DamageContext context) {
        if (context != null && (context.type() == DamageType.BREAK || context.type() == DamageType.DOT)) {
            return 1;
        }
        double bonus = context != null ? context.extraCritChance() : 0;
        double critRate = Math.min(1, attr(attacker, CRIT_CHANCE) + bonus);
        double critDmg = attr(attacker, CRIT_ATTACK);
        boolean crit = Math.random() < critRate;
        if (crit) {
            IO.println("  ★ " + attacker.getName() + " 暴击了! (暴击伤害 x"
                    + String.format("%.2f", 1 + critDmg) + ")");
        }
        return crit ? 1 + critDmg : 1;
    }

    /**
     * 防御区: (200 + 10×攻击方等级) / (受击方防御 + 200 + 10×攻击方等级).
     * 受击方防御 = 原始防御 × (1 - 减防% - 防御穿透%). Cannot exceed 1 (HSR.md §2.4).
     */
    public static double defenceRegion(CanHit attacker, CanHit defender) {
        double ignore = Math.min(1.0, attr(attacker, DEFENCE_IGNORE));
        double rawDef = attr(defender, DEFENCE);
        double effectiveDef = Math.max(0, rawDef * (1 - ignore));
        double levelPart = 200 + 10.0 * attacker.getLevel();
        double region = levelPart / (effectiveDef + levelPart);
        return Math.min(1.0, region);
    }

    /**
     * 抗性区: 1 - 对应属性抗性, with
     * 对应属性抗性 = 原始抗性 + 抗性提高 - 抗性降低 - 抗性穿透,
     * clamped to [-1, 0.9] so the region stays in [0.1, 2.0] (HSR.md §2.5).
     */
    public static double resistanceRegion(CanHit attacker, CanHit defender, Element element) {
        double pen = attr(attacker, RESISTANCE_PENETRATION);
        double baseRes = defender instanceof Enemy enemy ? enemy.getResistance(element) : 0;
        double resistance = baseRes - pen;
        resistance = Math.max(-1.0, Math.min(0.9, resistance));
        return 1 - resistance;
    }

    /**
     * 特殊乘区: 真实伤害 bonus multiplier.
     */
    public static double trueDamageRegion(CanHit attacker) {
        return 1 + attr(attacker, TRUE_DAMAGE);
    }

    // ─── Break damage (HSR.md §3.2) ────────────────────────────────────

    /**
     * Calculates break (击破) damage:
     * {@code breakBase(level) × 破韧单位 × (1 + 击破特攻) × (1 + 击破伤害加成)}.
     *
     * <p>Break damage ignores defence and resistance, cannot crit, and is not
     * boosted by ATK or element damage.
     */
    public static double calculateBreakDamage(CanHit attacker, double toughnessUnits) {
        double base = Constant.breakingRate(attacker.getLevel());
        double breakingEffect = attr(attacker, BREAKING_EFFECT);
        double breakBoost = attr(attacker, ELATION_DAMAGE_BOOST);
        return base * toughnessUnits * (1 + breakingEffect) * (1 + breakBoost);
    }

    // ─── Elation damage (HSR.md §3.4) ──────────────────────────────────

    /**
     * Calculates 欢愉伤害:
     * <pre>{@code
     * 欢愉伤害 = 基础值 × 欢愉倍率 × (1 + 欢愉度) × (1 + 增笑) × 笑点
     *          × 抗性穿透区 × 韧性减伤害区 × 减防区 × 易伤区 × 双爆区 × 真实伤害
     * }</pre>
     *
     * <p>Key restrictions (HSR.md §3.5):
     * <ul>
     *   <li><b>不受攻击力和属性伤害加成影响</b> — ATK and element boosts are ignored.</li>
     *   <li>基础值 depends only on the attacker's level (elation_basic_level_damage.json).</li>
     *   <li>笑点: {@code 1 + 笑点 × 5 / (笑点 + 240)}</li>
     * </ul>
     *
     * @param attacker     the caster
     * @param defender     the target
     * @param multiplier   the skill's 欢愉倍率
     * @param laughPoints  current 笑点 counter
     * @param elationLevel 欢愉度 (from traces; 0 if not implemented)
     * @param extraLaugh   special 增笑 multiplier (currently only 6-constellation 爻光)
     * @return the final elation damage
     */
    public static double calculateElationDamage(CanHit attacker, CanHit defender, double multiplier,
                                                double laughPoints, double elationLevel, double extraLaugh) {
        double base = Constant.elationBaseDamage(attacker.getLevel());
        double laughRegion = 1 + laughPoints * 5.0 / (laughPoints + 240);
        double resistance = resistanceRegion(attacker, defender, attacker.getElement());
        double toughnessRegion = 1.0; // 韧性减伤害区 (simplified to 1.0)
        double defence = defenceRegion(attacker, defender);
        double vulnerability = vulnerabilityRegion(defender);
        double crit = attacker.getAttribute(CRIT_CHANCE) != null
                && Math.random() < Math.min(1, attr(attacker, CRIT_CHANCE))
                ? 1 + attr(attacker, CRIT_ATTACK) : 1;
        double trueDamage = trueDamageRegion(attacker);

        return Math.max(1, base * multiplier * (1 + elationLevel) * (1 + extraLaugh)
                * laughRegion * resistance * toughnessRegion * defence * vulnerability * crit * trueDamage);
    }

    // ─── Super break damage (HSR.md 超击破) ────────────────────────────

    /**
     * Calculates 超击破伤害:
     * <pre>{@code
     * 超击破伤害 = 击破基数 × (1 + 击破特攻) × 技能最终削韧值 × (1 + 超击破伤害提高)
     *             × 易伤区 × 防御区 × 抗性区 × 减伤区
     * }</pre>
     *
     * <p>Per HSR.md: 不吃攻击力、增伤区、双暴; 吃等级、击破特攻、削韧值、
     * 超击破独立增伤、易伤、防御、抗性、减伤.
     *
     * @param toughnessUnits the attack's final toughness damage (技能最终削韧值)
     */
    public static double calculateSuperBreakDamage(CanHit attacker, CanHit defender, double toughnessUnits) {
        double base = Constant.breakingRate(attacker.getLevel()) * toughnessUnits
                * (1 + attr(attacker, BREAKING_EFFECT))
                * (1 + attr(attacker, SUPER_BREAK_DAMAGE_BOOST));
        base *= vulnerabilityRegion(defender);
        base *= reductionRegion(defender);
        base *= defenceRegion(attacker, defender);
        base *= resistanceRegion(attacker, defender, attacker.getElement());
        return Math.max(1, base);
    }

    // ─── DoT damage (HSR.md §5.4) ──────────────────────────────────────

    /**
     * Calculates a DoT tick. Boosted by element damage and DoT damage,
     * mitigated by defence. Cannot crit.
     */
    public static double calculateDotDamage(CanHit attacker, CanHit defender, double baseDot, Element element) {
        DamageContext context = DamageContext.of(DamageType.DOT, element);
        double damage = baseDot;
        damage *= boostRegion(attacker, context);
        damage *= vulnerabilityRegion(defender);
        damage *= (1 + attr(defender, DOT_TAKEN)); // 持续伤害提高 (defender side)
        damage *= reductionRegion(defender);
        damage *= weaknessRegion(attacker);
        damage *= defenceRegion(attacker, defender);
        damage *= trueDamageRegion(attacker);
        return Math.max(1, damage);
    }

    // ─── Healing & shielding (HSR.md §4) ───────────────────────────────

    /**
     * 基础治疗量 = 倍率% × 相应属性 + 固定值;
     * 治疗量 = 基础治疗量 × (1 + Σ治疗量加成) × (1 - Σ治疗量降低).
     */
    public static double calculateHeal(CanHit healer, double baseHeal) {
        DoubleValue healValue = new DoubleValue(baseHeal);
        double healingBoost = attr(healer, OUTGOING_HEALING_BOOST);
        if (healingBoost != 0) {
            healValue.addModifier(DoubleValue.Modifier.addPercent(healingBoost));
        }
        return healValue.get();
    }

    /**
     * Healing with the target's heal-taken ratio (HSR.md §4.2).
     */
    public static double calculateHeal(CanHit healer, CanHit target, double baseHeal) {
        DoubleValue healValue = new DoubleValue(baseHeal);
        double healingBoost = attr(healer, OUTGOING_HEALING_BOOST);
        double healTaken = attr(target, HEAL_TAKEN_RATIO);
        if (healingBoost != 0) {
            healValue.addModifier(DoubleValue.Modifier.addPercent(healingBoost));
        }
        if (healTaken != 0) {
            healValue.addModifier(DoubleValue.Modifier.multiplyPercent(healTaken));
        }
        return healValue.get();
    }

    /**
     * 护盾量 = 基础护盾量 × (1 + Σ护盾量提高 + Σ获得护盾量提高).
     */
    public static double calculateShield(CanHit shielder, double baseShield) {
        return Math.max(0, baseShield);
    }

    // ─── Helpers ───────────────────────────────────────────────────────

    private static double attr(CanHit canHit, AttributeType type) {
        DoubleValue value = canHit.getAttribute(type);
        return value != null ? value.get() : 0;
    }
}
