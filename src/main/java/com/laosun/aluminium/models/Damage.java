package com.laosun.aluminium.models;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.DoubleValue.Modifier;
import com.laosun.aluminium.models.DoubleValue.Modifier.ModifierSource;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One damage instance — always a single hit on a single target (P1-3 起自带乘区体系).
 *
 * <p>A skill that produces N hits builds N {@code Damage} objects; an instance is only
 * built when something actually has to be settled.
 *
 * <p><b>Zones.</b> Every zone is a subclass of {@link Area}: it takes exactly the
 * parameters it needs and outputs one multiplier. {@link #toValue()} walks
 * {@link #damageArea} and multiplies them together — that is the whole settlement.
 *
 * <p><b>Invariants</b> (violating one is a bug, not a style issue):
 * <ul>
 *   <li>{@link #skillBaseValue} stays out of every zone; it is only the starting
 *   point of the multiplication.</li>
 *   <li>Randomness does not live here. {@code Battle} rolls crit with its injected
 *   {@code Random} and hands the decided boolean to {@link #crit(boolean, double)};
 *   {@link CritArea} only keeps the books.</li>
 *   <li>Zone clamping (vulnerable cap, reduction floor, …) is declared by the
 *   subclass's {@link Area#min()}/{@link Area#max()} and applied by the {@code final}
 *   {@link Area#getRate()}, so no subclass can bypass it.</li>
 *   <li>"This hit does not use that zone" (break damage is not boosted, DOT cannot
 *   crit) is declared by {@link Area#applies(DamageType)} — never by hoping the
 *   caller does not add the modifier.</li>
 * </ul>
 */
@Getter
public class Damage {
    /**
     * attacker who create the attack.
     */
    private final CanHit attacker;
    /**
     * defender who defend this attack.
     */
    private final CanHit defender;
    /**
     * the skill element.
     */
    @NonNull
    private final DamageElement element;

    @NonNull
    private final DamageType type;
    /**
     * the skill base value
     * eg: Deals Ice DMG equal to 140% of C's ATK to one enemy. ATK * 1.40 is the base value.
     */
    private final double skillBaseValue;

    /**
     * Zone container: lazily filled, holds only the zones that were actually used
     * (a zone that never entered the list is equivalent to 1.0). Zones are pure
     * multipliers, so the iteration order does not matter.
     */
    @Getter(AccessLevel.NONE)
    private final List<Area> damageArea = new ArrayList<>();

    /**
     * True damage: skips every zone (controlled by {@link Constant#TRUE_DMG_SKIP_ZONES}).
     */
    private boolean trueDamage;
    /**
     * Whether this hit counts as an attack: additional / true damage segments are
     * {@code false} (P3 energy gain and P4 toughness only look at this).
     */
    private boolean countsAsAttack = true;

    public Damage(CanHit attacker, CanHit defender, DamageElement element, DamageType type, double skillBaseValue) {
        this.attacker = attacker;
        this.defender = defender;
        this.element = Objects.requireNonNull(element);
        this.type = Objects.requireNonNull(type);
        this.skillBaseValue = skillBaseValue;
    }

    /**
     * Compatibility constructor: the damage type defaults to {@link DamageType#NORMAL}.
     */
    public Damage(CanHit attacker, CanHit defender, DamageElement element, double skillBaseValue) {
        this(attacker, defender, element, DamageType.NORMAL, skillBaseValue);
    }

    // ==================================================================
    // 乘区取用口：懒创建，只走这里，保证「一个区最多一个实例」
    // ==================================================================

    @Getter(AccessLevel.NONE)
    private BoostArea boostArea;
    @Getter(AccessLevel.NONE)
    private VulnerableArea vulnerableArea;
    @Getter(AccessLevel.NONE)
    private ReductionArea reductionArea;
    @Getter(AccessLevel.NONE)
    private WeaknessArea weaknessArea;
    @Getter(AccessLevel.NONE)
    private CritArea critArea;
    @Getter(AccessLevel.NONE)
    private DefenceArea defenceArea;
    @Getter(AccessLevel.NONE)
    private ResistArea resistArea;

    /**
     * 增伤区（不存在时创建）。
     */
    public BoostArea boostArea() {
        if (boostArea == null) {
            damageArea.add(boostArea = new BoostArea());
        }
        return boostArea;
    }

    /**
     * 易伤区（不存在时创建）。
     */
    public VulnerableArea vulnerableArea() {
        if (vulnerableArea == null) {
            damageArea.add(vulnerableArea = new VulnerableArea());
        }
        return vulnerableArea;
    }

    /**
     * 减伤区（不存在时创建）。
     */
    public ReductionArea reductionArea() {
        if (reductionArea == null) {
            damageArea.add(reductionArea = new ReductionArea());
        }
        return reductionArea;
    }

    /**
     * 虚弱区（不存在时创建）。
     */
    public WeaknessArea weaknessArea() {
        if (weaknessArea == null) {
            damageArea.add(weaknessArea = new WeaknessArea());
        }
        return weaknessArea;
    }

    /**
     * 暴击区（不存在时创建）。
     */
    public CritArea critArea() {
        if (critArea == null) {
            damageArea.add(critArea = new CritArea());
        }
        return critArea;
    }

    /**
     * 防御区（不存在时创建）。
     */
    public DefenceArea defenceArea() {
        if (defenceArea == null) {
            damageArea.add(defenceArea = new DefenceArea());
        }
        return defenceArea;
    }

    /**
     * 抗性区（不存在时创建）。
     */
    public ResistArea resistArea() {
        if (resistArea == null) {
            damageArea.add(resistArea = new ResistArea());
        }
        return resistArea;
    }

    // ==================================================================
    // 装配口：参数语义在这里定死，Battle / Buff 只传数字
    // ==================================================================

    /**
     * 增伤区：加算（0.3 = 30%）。击破 / 超击破 / 真伤不吃本区。
     */
    public Damage addBoost(double pct) {
        return addBoost(pct, ModifierSource.UNKNOWN, 0);
    }

    /**
     * 增伤区（带来源）：来源信息便于按来源撤销与排错。
     */
    public Damage addBoost(double pct, ModifierSource source, int roleId) {
        boostArea().add(pct, source, roleId);
        return this;
    }

    /**
     * 易伤区：加算，读取倍率时统一下 cap。
     */
    public Damage addVulnerable(double pct) {
        return addVulnerable(pct, ModifierSource.UNKNOWN, 0);
    }

    public Damage addVulnerable(double pct, ModifierSource source, int roleId) {
        vulnerableArea().add(pct, source, roleId);
        return this;
    }

    /**
     * 减伤区：{@code r} 取 [0,1]，内部 clamp 后以 {@code -r} 进乘算（系数 = Π(1 - r)）。
     */
    public Damage addReduction(double pct) {
        return addReduction(pct, ModifierSource.UNKNOWN, 0);
    }

    public Damage addReduction(double pct, ModifierSource source, int roleId) {
        reductionArea().add(pct, source, roleId);
        return this;
    }

    /**
     * 虚弱区：系数 = 1 - Σ虚弱。
     */
    public Damage addWeakness(double pct) {
        return addWeakness(pct, ModifierSource.UNKNOWN, 0);
    }

    public Damage addWeakness(double pct, ModifierSource source, int roleId) {
        weaknessArea().add(pct, source, roleId);
        return this;
    }

    /**
     * 暴击区：骰子在外面（{@code Battle} 用注入的 Random），这里只记「暴没暴、暴伤多少」。
     */
    public Damage crit(boolean isCrit, double criticalDamage) {
        critArea().set(isCrit, criticalDamage);
        return this;
    }

    /**
     * 防御区：(200 + 10 × 攻击者等级) / (有效防御 + 200 + 10 × 攻击者等级)。
     *
     * @param attackerLevel   攻击者等级
     * @param defenderDefence 受击者防御力
     * @param defenceIgnore   无视防御比例，clamp 到 [0,1]
     */
    public Damage defence(int attackerLevel, double defenderDefence, double defenceIgnore) {
        defenceArea().set(attackerLevel, defenderDefence, defenceIgnore);
        return this;
    }

    /**
     * 抗性区：系数 = 1 - clamp(抗性 - 穿透, {@link Constant#RESIST_MIN}, {@link Constant#RESIST_MAX})。
     */
    public Damage resist(double resist, double penetration) {
        resistArea().set(resist, penetration);
        return this;
    }

    public Damage trueDamage() {
        this.trueDamage = true;
        return this;
    }

    public Damage notCountsAsAttack() {
        this.countsAsAttack = false;
        return this;
    }

    // ==================================================================
    // 清算
    // ==================================================================

    /**
     * Multiplies the base value by every zone that {@link Area#applies(DamageType) applies}
     * to this damage type.
     *
     * <p>Pure algebra: the minimum-damage clamp ({@code Math.max(1, …)}) stays in
     * {@code Battle.calculateDamage}.
     *
     * @return the final value of this hit
     */
    public double toValue() {
        if (trueDamage && Constant.TRUE_DMG_SKIP_ZONES) {
            return skillBaseValue;
        }
        double value = skillBaseValue;
        for (Area area : damageArea) {
            if (area.applies(type)) {
                value *= area.getRate();
            }
        }
        return value;
    }

    /**
     * Per-zone multiplier breakdown of this hit, for logs / UI（这一击由哪些乘区乘出来）。
     *
     * @return an insertion-ordered map: {@code base} → each zone name → {@code final}
     */
    public Map<String, Double> breakdown() {
        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("base", skillBaseValue);
        for (Area area : damageArea) {
            if (area.applies(type)) {
                breakdown.put(area.getClass().getSimpleName(), area.getRate());
            }
        }
        breakdown.put("final", toValue());
        return breakdown;
    }

    /**
     * A read-only snapshot of the zones used by this hit. For tests and logs only —
     * the settlement loop iterates the field directly.
     *
     * @return an immutable copy of the zone list, in creation order
     */
    public List<Area> getDamageArea() {
        return List.copyOf(damageArea);
    }

    // ==================================================================
    // 乘区家族：抽象契约 + 两条支线（可累加区 / 计算区）
    // ==================================================================

    /**
     * A damage zone: takes the parameters it needs, outputs one multiplier.
     *
     * <p>{@link #getRate()} is {@code final} on purpose — every zone's raw rate goes
     * through {@link #min()}/{@link #max()}, so a subclass cannot forget its cap
     * (vulnerable 3.5, reduction 0.01, …).
     */
    public abstract static class Area {
        /**
         * The raw, un-clamped multiplier of this zone.
         */
        protected abstract double rate();

        /**
         * The final multiplier of this zone, clamped into [{@link #min()}, {@link #max()}].
         */
        public final double getRate() {
            return Math.clamp(rate(), min(), max());
        }

        /**
         * Lower bound of this zone's multiplier.
         *
         * <p>No policy by default ({@code -∞}, symmetric with {@link #max()}'s {@code +∞}):
         * the base class does not invent bounds. Every official floor is declared by the
         * zone that owns it — reduction 0.01 and weakness 0.2 (HSR.md §2.2), resistance
         * 0.1 (HSR.md §2.5), crit 1.0.
         */
        protected double min() {
            return Double.NEGATIVE_INFINITY;
        }

        /**
         * Upper bound of this zone's multiplier; no cap by default.
         */
        protected double max() {
            return Double.POSITIVE_INFINITY;
        }

        /**
         * Whether this zone applies to the given damage type.
         *
         * <p>Break damage is not boosted, DOT cannot crit, … Declaring it here keeps the
         * rule in one place instead of relying on callers to skip the modifier.
         *
         * @param type the damage type of the hit being settled
         * @return {@code true} if this zone participates in {@link #toValue()}
         */
        public boolean applies(DamageType type) {
            return true;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(x" + getRate() + ")";
        }
    }

    /**
     * A zone that accumulates modifiers: internally a {@link DoubleValue} whose base is
     * fixed at 1.0, so the zone is always a multiplier. Subclasses only decide how their
     * inputs enter the {@code DoubleValue} and how the result is bounded.
     */
    public abstract static class PercentArea extends Area {
        protected final DoubleValue value = DoubleValue.one();

        /**
         * Sanity floor, <b>not</b> a game rule: an accumulating zone never returns a
         * negative multiplier, because a negative factor would flip the sign of the whole
         * hit. There is no negative 增伤/易伤 in the game data; zones with an <i>official</i>
         * floor (reduction 0.01, weakness 0.2 — HSR.md §2.2) override this.
         */
        @Override
        protected double min() {
            return 0.0;
        }

        protected void addPercent(double pct, ModifierSource source, int roleId) {
            value.addModifier(Modifier.addPercent(pct, source, roleId));
        }

        protected void multiplyPercent(double pct, ModifierSource source, int roleId) {
            value.addModifier(Modifier.multiplyPercent(pct, source, roleId));
        }

        /**
         * The un-clamped accumulated value, for assertions and debugging.
         */
        public DoubleValue raw() {
            return value;
        }

        /**
         * Removes every modifier contributed by the given source (buff expiry / dispel).
         */
        public void removeModifiersFrom(ModifierSource source, int roleId) {
            for (Modifier modifier : value.filterBySource(source)) {
                if (modifier.getSourceRoleId() == roleId) {
                    value.removeModifier(modifier);
                }
            }
        }

        @Override
        protected double rate() {
            return value.get();
        }
    }

    /**
     * 增伤区：{@code 1 + Σ增伤}。击破 / 超击破 / 真伤不吃本区。
     */
    public static final class BoostArea extends PercentArea {
        @Override
        public boolean applies(DamageType type) {
            return type.isBoostable();
        }

        public BoostArea add(double pct) {
            return add(pct, ModifierSource.UNKNOWN, 0);
        }

        public BoostArea add(double pct, ModifierSource source, int roleId) {
            addPercent(pct, source, roleId);
            return this;
        }
    }

    /**
     * 易伤区：{@code 1 + Σ易伤}，整体 cap 到 {@link Constant#VULNERABLE_CAP}。
     */
    public static final class VulnerableArea extends PercentArea {
        @Override
        protected double max() {
            return Constant.VULNERABLE_CAP;
        }

        public VulnerableArea add(double pct) {
            return add(pct, ModifierSource.UNKNOWN, 0);
        }

        public VulnerableArea add(double pct, ModifierSource source, int roleId) {
            addPercent(pct, source, roleId);
            return this;
        }
    }

    /**
     * 减伤区：{@code Π(1 - r)} —— 每个 {@code r} 进来先 clamp 到 [0,1]，乘积再以
     * {@link Constant#REDUCTION_MIN} 兜底。
     */
    public static final class ReductionArea extends PercentArea {
        @Override
        protected double max() {
            return 1.0;
        }

        @Override
        protected double min() {
            return Constant.REDUCTION_MIN;
        }

        public ReductionArea add(double r) {
            return add(r, ModifierSource.UNKNOWN, 0);
        }

        public ReductionArea add(double r, ModifierSource source, int roleId) {
            multiplyPercent(-Math.clamp(r, 0, 1), source, roleId);
            return this;
        }
    }

    /**
     * 虚弱区：{@code 1 - Σ虚弱}，以 {@link Constant#WEAKNESS_MIN} 兜底。
     */
    public static final class WeaknessArea extends PercentArea {
        @Override
        protected double min() {
            return Constant.WEAKNESS_MIN;
        }

        public WeaknessArea add(double w) {
            return add(w, ModifierSource.UNKNOWN, 0);
        }

        public WeaknessArea add(double w, ModifierSource source, int roleId) {
            addPercent(-w, source, roleId);
            return this;
        }
    }

    /**
     * 暴击区：{@code crit ? 1 + 暴伤 : 1}。骰子在 {@code Battle}，这里只记账。
     */
    public static final class CritArea extends Area {
        private boolean crit;
        private double criticalDamage;

        @Override
        public boolean applies(DamageType type) {
            return type.isCrittable();
        }

        public CritArea set(boolean isCrit, double criticalDamage) {
            this.crit = isCrit;
            this.criticalDamage = criticalDamage;
            return this;
        }

        @Override
        protected double min() {
            return 1.0;
        }

        @Override
        protected double rate() {
            return crit ? 1 + criticalDamage : 1.0;
        }
    }

    /**
     * 防御区：{@code (200 + 10L) / (effDef + 200 + 10L)}，
     * 其中 {@code effDef = def × (1 - clamp(无视防御))}。
     */
    public static final class DefenceArea extends Area {
        private int attackerLevel = 80;
        private double defenderDefence;
        private double defenceIgnore;

        public DefenceArea set(int attackerLevel, double defenderDefence, double defenceIgnore) {
            this.attackerLevel = attackerLevel;
            this.defenderDefence = defenderDefence;
            this.defenceIgnore = Math.clamp(defenceIgnore, 0, 1);
            return this;
        }

        @Override
        protected double max() {
            return 1.0;
        }

        @Override
        protected double rate() {
            double effectiveDefence = Math.max(0, defenderDefence * (1 - defenceIgnore));
            double levelTerm = Constant.DEFENCE_CONST + Constant.DEFENCE_PER_LEVEL * attackerLevel;
            return levelTerm / (effectiveDefence + levelTerm);
        }
    }

    /**
     * 抗性区：{@code 1 - clamp(抗性 - 穿透, RESIST_MIN, RESIST_MAX)}。
     */
    public static final class ResistArea extends Area {
        private double resist;
        private double penetration;

        public ResistArea set(double resist, double penetration) {
            this.resist = resist;
            this.penetration = penetration;
            return this;
        }

        @Override
        protected double min() {
            return 1 - Constant.RESIST_MAX;
        }

        @Override
        protected double max() {
            return 1 - Constant.RESIST_MIN;
        }

        @Override
        protected double rate() {
            // 抗性区 = 1 - 抗性，抗性取值范围 [-1, 0.9] ⇒ 抗性区 [0.1, 2.0]（HSR.md §2.5，负抗全效）
            double resolved = Math.clamp(resist - penetration, Constant.RESIST_MIN, Constant.RESIST_MAX);
            return 1 - resolved;
        }
    }
}
