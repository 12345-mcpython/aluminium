package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.event.AttackEvent;
import com.laosun.aluminium.models.event.BattleEvent;
import com.laosun.aluminium.models.event.DamageEvent;
import com.laosun.aluminium.models.event.MoveEvent;
import com.laosun.aluminium.models.energy.EnergyGain;
import com.laosun.aluminium.models.energy.EnergyProvider;
import com.laosun.aluminium.models.energy.StandardEnergyProvider;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.EnumMap;
import java.util.List;

/**
 * Abstract base for all entities that can participate in combat.
 *
 * <p>Holds the entity's name, faction ({@link Camp}), and an array of
 * {@link DoubleValue} combat attributes indexed by {@link AttributeType#ordinal()}.
 * Subclasses include {@link Character}, {@link Enemy}, and {@link Summon}.
 */
@Getter
@ToString
public abstract class CanHit implements BattleEvent, MoveEvent, DamageEvent, AttackEvent {
    /**
     * The display name of this entity.
     */
    private final String name;
    /**
     * Combat attributes indexed by {@link AttributeType#ordinal()}.
     */
    private final DoubleValue[] attributes;
    /**
     * Faction alignment.
     */
    private final Camp camp;

    /**
     * Combat level (P1-4): feeds the defence zone now, break base (P4) and enemy
     * stat scaling (P2-4) later. Defaults to 80 so existing code keeps working.
     */
    @Setter
    private int level = 80;

    @Setter
    private EnumMap<SkillType, Skill> skills;
    /**
     * Current hit points.
     */
    private double currentHp;
    /**
     * Whether this entity has been defeated.
     */
    private boolean death = false;

    /**
     * Temporarily cannot take damage: boss phase transition / invulnerability window
     * (转阶段无敌、锁血演出).
     *
     * <p>Orthogonal to {@link #death}: an invulnerable target is still a legal target
     * (an AOE still "hits" it, for 0 damage) but {@code Battle.applyDamage} settles
     * nothing on it — this is what keeps a transitioning boss from being 鞭尸.
     */
    @Setter
    private boolean invulnerable = false;

    /**
     * 能量当前值（P3）。没有能量条的角色恒为 0。
     */
    @Setter
    private double currentEnergy = 0;

    /**
     * 能量上限（P3）。{@code 0} = 没有能量条（1407 遐蝶就是这种）：{@link #hasEnergyBar()} 为
     * {@code false}，任何回能都不入账，也没有「满能量放大招」这回事。
     *
     * <p>真实上限来自 {@code character_data.json} 的 {@code max_energy}（93 个角色里只有遐蝶是
     * null）。离档的很多：飞霄/白厄 12、黄泉 9、昔涟 24、流萤/云璃/长夜月 240、银枝/爻光 180、
     * 阿格莱雅 350、绯英 480 —— 见 ROADMAP 的 P3-0 D 表。
     */
    @Setter
    private double maxEnergy = 0;

    /**
     * 回能规则（P3）。常规角色用 {@link StandardEnergyProvider}，特殊角色各自实现本接口。
     */
    @Setter
    private EnergyProvider energyProvider = new StandardEnergyProvider();

    private final BuffManager buffManager;

    // test event behavior
    public Runnable beforeMove = () -> {
    };
    public Runnable afterMove = () -> {
    };
    public Runnable onBattleStart = () -> {
    };

    /**
     * Constructs a combat entity.
     *
     * @param name       display name
     * @param camp       faction alignment
     * @param attributes pre-computed attribute array
     */
    public CanHit(String name, Camp camp, DoubleValue[] attributes) {
        this.name = name;
        this.camp = camp;
        this.attributes = attributes;
        this.currentHp = attributes[AttributeType.HEALTH.ordinal()].get();
        this.skills = new EnumMap<>(SkillType.class);
        this.buffManager = new BuffManager(this);
    }

    /**
     * Copy construction
     *
     * @param other what you want to copy
     */
    public CanHit(CanHit other) {
        // need to clone
        this.name = other.name;
        this.camp = other.camp;
        this.level = other.level;
        this.skills = new EnumMap<>(other.skills);
        this.attributes = other.attributes.clone();
        // don't need to clone
        this.currentHp = attributes[AttributeType.HEALTH.ordinal()].get();
        this.death = false;
        this.buffManager = new BuffManager(this);
        // 配置类字段要跟着复制；currentEnergy 是新战斗实例，故意从 0 开始
        this.maxEnergy = other.maxEnergy;
        this.currentEnergy = 0;
        this.energyProvider = other.energyProvider;
        this.beforeMove = () -> {
        };
        this.afterMove = () -> {
        };
        this.onBattleStart = () -> {
        };
    }

    /**
     * Returns the {@link DoubleValue} for the given attribute type.
     *
     * @param attributeType the attribute to look up
     * @return the corresponding value object, or {@code null} for percentage-type attributes
     */
    public DoubleValue getAttribute(AttributeType attributeType) {
        return attributes[attributeType.ordinal()];
    }

    public void setSkill(SkillType skillType, Skill skill) {
        skills.put(skillType, skill);
    }

    /**
     * Replaces the {@link DoubleValue} at the given attribute index.
     *
     * @param attributeType the attribute to set
     * @param value         the new value object
     */
    public void setAttribute(AttributeType attributeType, DoubleValue value) {
        attributes[attributeType.ordinal()] = value;
    }

    /**
     * Returns the maximum hit points from the HEALTH attribute.
     */
    public double getMaxHp() {
        return attributes[AttributeType.HEALTH.ordinal()].get();
    }

    /**
     * Applies damage to this entity, reducing current HP.
     * If HP drops to zero or below, the entity is marked dead.
     *
     * @param damage the amount of damage to take
     * @return {@code true} if the entity died from this damage
     */
    public boolean takeDamage(double damage) {
        if (death || damage <= 0) {
            return false;
        }
        currentHp -= damage;
        if (currentHp <= 0) {
            currentHp = 0;
            death = true;
            return true;
        }
        return false;
    }

    /**
     * Restores HP to this entity, capped at max HP.
     * Has no effect on dead entities.
     *
     * @param amount the amount to heal
     */
    public void heal(double amount) {
        if (death || amount <= 0) {
            return;
        }
        currentHp = Math.min(currentHp + amount, getMaxHp());
    }

    /**
     * 是否有能量条（{@code maxEnergy > 0}）。
     *
     * @return {@code true} if this entity has an energy bar
     */
    public boolean hasEnergyBar() {
        return maxEnergy > 0;
    }

    /**
     * 是否满能量（可以放终结技）。没有能量条的角色永远 {@code false}。
     *
     * @return {@code true} if the energy bar is full
     */
    public boolean isEnergyFull() {
        return hasEnergyBar() && currentEnergy >= maxEnergy;
    }

    /**
     * 入账一次回能（P3 唯一的能量增长口）。
     *
     * <p>公式（HSR.md §3.3）：{@code 最终获得能量 = 基础获得能量 × (1 + 能量恢复效率%)}；
     * {@link EnergyGain#affectedByEfficiency()} 为 {@code false} 时不吃效率加成。
     *
     * @param gain 一次回能描述
     * @return **实际入账值**（被上限截断后的值，不是理论回能值）
     */
    public double gainEnergy(EnergyGain gain) {
        if (gain == null || gain.amount() <= 0 || !hasEnergyBar()) {
            return 0;
        }
        double efficiency = gain.affectedByEfficiency()
                ? 1 + getAttribute(AttributeType.ENERGY_REGENERATION_RATE).get()
                : 1;
        double added = Math.min(maxEnergy - currentEnergy, gain.amount() * efficiency);
        currentEnergy += added;
        return added;
    }

    /**
     * 便捷入口：按基础值入账（走回能效率）。
     *
     * @param amount base energy
     * @return 实际入账值
     */
    public double gainEnergy(double amount) {
        return gainEnergy(EnergyGain.normal(amount));
    }

    @Override
    public void beforeMove(Battle battle) {
        beforeMove.run();
    }

    @Override
    public void afterMove(Battle battle) {
        afterMove.run();
    }

    @Override
    public void onBattleStart(Battle battle) {
        onBattleStart.run();
    }

    /**
     * Damage-settlement hook (P1-7), fired for both sides before the zones are multiplied.
     *
     * <p>The default relays to {@link BuffManager#onDamage(Battle, Damage)}, so buffs can
     * inject 易伤 / 减伤 / 虚弱. Subclasses that override it (character talents, boss
     * mechanics) <b>must call {@code super.onDamage(battle, damage)}</b>, otherwise their
     * own buffs stop working.
     */
    @Override
    public void onDamage(Battle battle, Damage damage) {
        buffManager.onDamage(battle, damage);
    }

    /**
     * Attack-level hook (P1-9), broadcast to every ally once an attack is fully settled.
     *
     * <p>The default relays to {@link BuffManager#afterAttack(Battle, CanHit, CanHit, List, double)},
     * so buffs like 知更鸟【协奏】/缇宝结界 can spawn 附加伤害 / 真伤 off someone else's attack.
     * Subclasses that override it <b>must call {@code super}</b>.
     */
    @Override
    public void afterAttack(Battle battle, CanHit attacker, CanHit mainTarget,
                            List<? extends CanHit> hitTargets, double totalDamage) {
        buffManager.afterAttack(battle, attacker, mainTarget, hitTargets, totalDamage);
    }
}
