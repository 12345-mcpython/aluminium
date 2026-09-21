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
import java.util.function.Consumer;

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
     * <p>若替换的是 {@code SPEED} 且数值真的变了，会通知 {@link #notifySpeedChanged()}
     * —— 这是"速度变化 → 重排行动条"（P7 修正 E2）的**唯一触发点**，
     * 所以改速度请走这里（或改 {@code DoubleValue} 之后再调 {@code notifySpeedChanged()}），
     * 不要在别处偷偷改速度属性。
     *
     * @param attributeType the attribute to set
     * @param value         the new value object
     */
    public void setAttribute(AttributeType attributeType, DoubleValue value) {
        DoubleValue previous = attributes[attributeType.ordinal()];
        attributes[attributeType.ordinal()] = value;
        if (attributeType == AttributeType.SPEED) {
            double oldSpeed = previous == null ? 0 : previous.get();
            double newSpeed = value == null ? 0 : value.get();
            if (oldSpeed != newSpeed) {
                notifySpeedChanged();
            }
        }
    }

    /**
     * Returns the maximum hit points from the HEALTH attribute.
     */
    public double getMaxHp() {
        return attributes[AttributeType.HEALTH.ordinal()].get();
    }

    /**
     * 速度属性变化时的回调（P7 修正 E2）。
     *
     * <p>{@code Battle} 构造时把它指到"重排该单位的行动时间"（{@code Queue.refreshSpeed}）。
     * 在此之前 {@code Signal} 缓存的 {@code speed} 只在几个"重置周期"的时机被刷新，
     * 于是加速/减速不会立刻反映到行动条上。
     */
    private transient Consumer<CanHit> speedChangeListener;

    /**
     * 通知"这个单位的速度变了"。由 {@code Battle.onSpeedChanged} 调用，
     * 别在别处直接调（否则行动条重排的规则会散落）。
     */
    public void notifySpeedChanged() {
        if (speedChangeListener != null) {
            speedChangeListener.accept(this);
        }
    }

    /**
     * 由 {@code Battle} 注入：速度变化时怎么重排行动条。
     *
     * @param listener 回调；{@code null} = 不通知（例如没有队列的单元测试）
     */
    public void setSpeedChangeListener(Consumer<CanHit> listener) {
        this.speedChangeListener = listener;
    }

    /**
     * 当前护盾值（P6-3）。{@code 0} = 没有盾。
     *
     * <p>护盾**先于 HP 被扣**（{@link #takeDamage(double)}），且**不叠加**：
     * 新盾由 {@code Battle.grantShield} 直接覆盖旧值，不做相加。
     */
    @Setter
    private double shield = 0;

    /**
     * 上一次 {@link #takeDamage(double)} 被护盾挡掉的量（P6-3）。
     *
     * <p>存在的理由：护盾吸收的伤害**也是这一击造成的伤害** ——
     * {@code Battle.applyDamage} 的返回值要把"打进盾里的部分"算进去，
     * 否则"打在有盾的目标上"会显示成造成 0 伤害（击杀回能/攻击事件总伤害都会失真）。
     * 每次 {@code takeDamage} 都会重写它。
     */
    @Setter
    private double lastShieldAbsorbed = 0;

    /**
     * Applies damage to this entity, reducing current HP.
     * If HP drops to zero or below, the entity is marked dead.
     *
     * <p><b>护盾先扣（P6-3）</b>：伤害先由 {@link #shield} 吸收，盾被打空后剩下的才扣 HP。
     * 所以"有盾时不会死"是自动成立的；被吸收的量记在 {@link #lastShieldAbsorbed}。
     *
     * @param damage the amount of damage to take
     * @return {@code true} if the entity died from this damage
     */
    public boolean takeDamage(double damage) {
        lastShieldAbsorbed = 0;                      // 每次结算先清空，避免读到上一次的值
        if (death || damage <= 0) {
            return false;
        }
        if (shield > 0) {
            double absorbed = Math.min(shield, damage);
            shield -= absorbed;
            damage -= absorbed;
            // ⚠ 必须在**任何 return 之前**赋值：盾把伤害全吃掉时下面会提前 return，
            //   漏掉这行会让调用方读到上一次的陈旧值（实测过一次：返回伤害翻倍）。
            lastShieldAbsorbed = absorbed;
            if (damage <= 0) {
                return false;                        // 全被盾吃掉：HP 不动，当然也没死
            }
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
