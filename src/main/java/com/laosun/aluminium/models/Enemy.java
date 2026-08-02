package com.laosun.aluminium.models;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.HardLevelGroup;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.Element;
import com.laosun.aluminium.enums.SkillAttackType;
import com.laosun.aluminium.utils.AttributeBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.laosun.aluminium.enums.AttributeType.*;

/**
 * An enemy combatant with toughness and elemental weaknesses.
 *
 * <p>Enemy stats follow HSR.md §1.2:
 * {@code 敌人属性 = 基础值 × 等级组系数 × 自身调整系数 × Π精英组别系数 + 自身调整值}
 * where the 等级组系数 comes from {@code hard_level_group.json}.
 *
 * <p>Enemies own a toughness bar ({@link #maxToughness}) and a set of
 * {@link #weaknesses}. Hitting a weakness reduces toughness (HSR.md §3.2);
 * at zero the enemy enters {@link #broken} state and the break effect of the
 * breaking element is applied (HSR.md §3.2).
 */
@Getter
@Setter
@ToString(callSuper = true)
public class Enemy extends CanHit {

    /** The enemy's toughness bar (韧性). 0 means broken. */
    private double maxToughness;
    /** Current toughness. */
    private double currentToughness;
    /** Whether the enemy is in the broken (击破) state. */
    private boolean broken = false;
    /** The element that caused the current break. */
    private Element breakElement = null;
    /** Elements this enemy is weak to. */
    private final Set<Element> weaknesses = new HashSet<>();
    /**
     * Temporarily implanted weaknesses (e.g. Silver Wolf 注入), keyed by
     * remaining turns in the enemy's own turns.
     */
    private final Map<Element, Integer> temporaryWeaknesses = new EnumMap<>(Element.class);
    /** Elemental resistance per element. */
    private final Map<Element, Double> resistances = new EnumMap<>(Element.class);
    /** The simple AI skill list this enemy uses in battle. */
    private List<EnemySkill> enemySkills = List.of();

    /**
     * A simple AI attack used by enemies.
     *
     * @param name        display name
     * @param element     damage element
     * @param multiplier  ATK multiplier of the attack
     * @param attackType  single-target or AoE
     * @param toughnessHit toughness damage dealt on hit (1/2/3 units of 30)
     */
    public record EnemySkill(String name, Element element, double multiplier,
                             SkillAttackType attackType, double toughnessHit) {
        public EnemySkill {
            if (multiplier <= 0) throw new IllegalArgumentException("multiplier must be > 0");
            if (toughnessHit < 0) throw new IllegalArgumentException("toughnessHit must be >= 0");
        }

        public EnemySkill(String name, Element element, double multiplier, SkillAttackType attackType) {
            this(name, element, multiplier, attackType, 1);
        }
    }

    public Enemy(String name, Camp camp, DoubleValue[] attributes) {
        super(name, camp, attributes);
    }

    public Enemy(String name, DoubleValue[] attributes) {
        super(name, Camp.ENEMY, attributes);
    }

    /**
     * Builds an enemy from raw base stats (L1) scaled by the difficulty-1
     * level-group ratio of {@code hard_level_group.json}.
     *
     * @param name          display name
     * @param level         enemy level (1-100)
     * @param baseHealth    base (L1) max HP before ratio scaling
     * @param baseAttack    base attack
     * @param baseDefence   base defence
     * @param baseSpeed     base speed
     * @param baseToughness base toughness
     * @param baseElement   the enemy's main element
     * @param weaknesses    elements this enemy is weak to
     * @param resistances   per-element resistance multipliers
     * @param skills        AI skills
     * @return the fully scaled enemy
     */
    public static Enemy fromTemplate(String name, int level,
                                     double baseHealth, double baseAttack, double baseDefence,
                                     double baseSpeed, double baseToughness,
                                     Element baseElement, Set<Element> weaknesses,
                                     Map<Element, Double> resistances, List<EnemySkill> skills) {
        HardLevelGroup ratio = Constant.levelGroupRatio(level);
        double health = baseHealth * ratio.healthRatio();
        double attack = baseAttack * ratio.attackRatio();
        double defence = baseDefence * ratio.defenceRatio();
        double speed = baseSpeed * ratio.speedRatio();
        double toughness = baseToughness * ratio.stanceRatio();

        AttributeBuilder atb = new AttributeBuilder();
        atb.setBase(HEALTH, health)
                .setBase(DEFENCE, defence)
                .setBase(ATTACK, attack)
                .setBase(SPEED, speed)
                .setBase(CRIT_CHANCE, 0.0)
                .setBase(CRIT_ATTACK, 0.2);
        Enemy enemy = new Enemy(name, atb.build());
        enemy.setLevel(level);
        enemy.setElement(baseElement);
        enemy.setAggro(100);
        enemy.maxToughness = toughness;
        enemy.currentToughness = toughness;
        enemy.weaknesses.addAll(weaknesses);
        enemy.resistances.putAll(resistances);
        enemy.enemySkills = skills;
        return enemy;
    }

    // ─── Toughness & break (HSR.md §3.2) ───────────────────────────────

    /**
     * Reduces toughness by the given amount if the element matches a weakness.
     * Returns {@code true} if this hit triggered a break (toughness reaching zero).
     *
     * @param amount  toughness damage dealt
     * @param element the element of the attack
     * @return whether the enemy broke as a result of this hit
     */
    public boolean reduceToughness(double amount, Element element) {
        if (broken || amount <= 0 || !isWeakTo(element)) {
            return false;
        }
        currentToughness = Math.max(0, currentToughness - amount);
        if (currentToughness <= 0) {
            broken = true;
            breakElement = element;
            return true;
        }
        return false;
    }

    /**
     * Recovers toughness to full and clears the broken state.
     * Called when the broken enemy reaches its turn.
     */
    public void recoverToughness() {
        broken = false;
        currentToughness = maxToughness;
        breakElement = null;
    }

    /**
     * The toughness damage dealt by a given stance value (30 toughness = 1 unit).
     *
     * @param stanceRaw the raw stance value from skill data
     * @return the toughness damage in units of 1
     */
    public static double stanceToToughness(double stanceRaw) {
        return stanceRaw / 30.0;
    }

    /**
     * The effective resistance against the given element (clamped per HSR.md §2.5).
     */
    public double getResistance(Element element) {
        return resistances.getOrDefault(element, 0.0);
    }

    // ─── Temporary weaknesses (weakness implant) ───────────────────────

    /**
     * Implants a temporary weakness of the given element.
     *
     * @param element the element to implant
     * @param turns   how many of the enemy's turns it lasts
     */
    public void addTemporaryWeakness(Element element, int turns) {
        temporaryWeaknesses.merge(element, turns, Math::max);
    }

    /**
     * Extends an implanted weakness by the given turns.
     */
    public void extendTemporaryWeakness(Element element, int turns) {
        temporaryWeaknesses.merge(element, turns, Integer::sum);
    }

    /**
     * The remaining duration of a temporary weakness, 0 if not implanted.
     */
    public int getTemporaryWeaknessTurns(Element element) {
        return temporaryWeaknesses.getOrDefault(element, 0);
    }

    /**
     * Whether the given element currently breaks this enemy (base or implanted).
     */
    public boolean isWeakTo(Element element) {
        return weaknesses.contains(element) || temporaryWeaknesses.containsKey(element);
    }

    /**
     * Ticks temporary weaknesses down at the enemy's turn start.
     */
    public void tickTemporaryWeaknesses() {
        temporaryWeaknesses.entrySet().removeIf(entry -> {
            int left = entry.getValue() - 1;
            if (left <= 0) {
                return true;
            }
            entry.setValue(left);
            return false;
        });
    }

    // ─── Simple test helpers ───────────────────────────────────────────

    public static Enemy fromAttributes(String name, double health, double defence, double attack, double speed) {
        AttributeBuilder attributeBuilder = new AttributeBuilder();
        attributeBuilder.setBase(HEALTH, health);
        attributeBuilder.setBase(DEFENCE, defence);
        attributeBuilder.setBase(ATTACK, attack);
        attributeBuilder.setBase(SPEED, speed);
        return new Enemy(name, attributeBuilder.build());
    }
}
