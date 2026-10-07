package com.laosun.aluminium.models.enemy;

import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.utils.AttributeBuilder;
import com.laosun.aluminium.models.skill.Skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A made-up enemy, for tests and for the demo - a monster whose numbers and skills the caller states instead of the
 * data dump.
 *
 * <p>Why this exists rather than another {@code monsterId}: the dump decides what every real monster can do. A judge
 * that needs "an enemy with two skills", or one that dies at exactly the right moment, or one that is weak to Ice and
 * nothing else, cannot get there through {@code MonsterConfig} - and the values it picks out of the dump are then
 * load-bearing for a case that is not about the dump at all. {@link EnemyFactory#create} stays the way to build a real
 * monster; this is the way to build the enemy a case is actually about.
 *
 * <p><b>What it is NOT.</b> Not a substitute for the enemy line: no AI, no skill weights, no phase script, no target
 * choice beyond the engine's own. Those live in the data and are unmeasured - see the registry. This only gives an
 * enemy a stat sheet, an ordinary attack, and optionally more skills it switches to as it loses HP (the engine's
 * {@link Enemy#setPhaseSkill} mechanism, which is the whole of the "multiple skills" it has today).
 *
 * <p><b>Every field has a default</b>, so the common case is one call:
 * {@code StubEnemy.named("木桩").withHealth(10_000).build()}. A default that made the enemy unable to act, or
 * invulnerable, would turn every case that forgot a field into a case about that field; the defaults are therefore the
 * ordinary ones - 10,000 HP, attack 1,000, defence 1,000, speed 100, toughness 100 - and the name says it is a stub.
 */
public final class StubEnemy {

    /** The defaults: ordinary enough that forgetting a field does not change what a case is measuring. */
    private static final double DEFAULT_HEALTH = 10_000;
    private static final double DEFAULT_ATTACK = 1_000;
    private static final double DEFAULT_DEFENCE = 1_000;
    private static final double DEFAULT_SPEED = 100;
    private static final double DEFAULT_TOUGHNESS = 100;
    private static final double DEFAULT_MULTIPLIER = 1.0;

    private final String name;
    private double health = DEFAULT_HEALTH;
    private double attack = DEFAULT_ATTACK;
    private double defence = DEFAULT_DEFENCE;
    private double speed = DEFAULT_SPEED;
    private double effectResistance;
    private double effectHitRate;
    private Map<DamageElement, Double> damageResist = new LinkedHashMap<>();
    private double toughness = DEFAULT_TOUGHNESS;
    private DamageElement element = DamageElement.PHYSICAL;
    private DamageType damageType = DamageType.NORMAL;
    private double multiplier = DEFAULT_MULTIPLIER;
    private int hits = 1;
    private Set<DamageElement> weaknesses = Set.of();
    private Map<String, Double> debuffResist = Map.of();
    /** Extra skills, with the HP ratio at or below which each takes over. */
    private final List<double[]> thresholds = new ArrayList<>();
    private final List<Skill> extraSkills = new ArrayList<>();

    private StubEnemy(String name) {
        this.name = name;
    }

    /**
     * A stub with the given display name - give it one that says what the case is about, because the name reaches the
     * demo's output lines.
     */
    public static StubEnemy named(String name) {
        return new StubEnemy(name == null || name.isBlank() ? "木桩" : name);
    }

    /** A stub called 木桩 ("training dummy"), which is what most engine cases want. */
    public static StubEnemy dummy() {
        return named("木桩");
    }

    public StubEnemy withHealth(double value) {
        this.health = value;
        return this;
    }

    public StubEnemy withAttack(double value) {
        this.attack = value;
        return this;
    }

    public StubEnemy withDefence(double value) {
        this.defence = value;
        return this;
    }

    public StubEnemy withSpeed(double value) {
        this.speed = value;
        return this;
    }

    public StubEnemy withToughness(double value) {
        this.toughness = value;
        return this;
    }

    public StubEnemy withEffectResistance(double value) {
        this.effectResistance = value;
        return this;
    }

    public StubEnemy withEffectHitRate(double value) {
        this.effectHitRate = value;
        return this;
    }

    /**
     * Resistance to incoming damage, by element - the data's own shape ({@code damageResist} is a per-element column).
     *
     * <p>Note: Per element rather than one number, because that is what the engine reads; a single "resists
     * everything" helper would have to guess which elements to enumerate, and the interesting cases are usually about
     * ONE element.
     */
    public StubEnemy withDamageResistance(DamageElement element, double value) {
        this.damageResist = new LinkedHashMap<>(this.damageResist);
        this.damageResist.put(element, value);
        return this;
    }

    /** The same, applied to every element. */
    public StubEnemy withDamageResistanceAgainstEverything(double value) {
        for (DamageElement each : DamageElement.values()) {
            this.damageResist.put(each, value);
        }
        return this;
    }

    /** The element its ordinary attack deals, and (unless a weakness set is stated) the element it is weak to. */
    public StubEnemy withElement(DamageElement value) {
        if (value != null) {
            this.element = value;
        }
        return this;
    }

    public StubEnemy withDamageType(DamageType value) {
        if (value != null) {
            this.damageType = value;
        }
        return this;
    }

    /** The ordinary attack's multiplier and hit count. */
    public StubEnemy withAttackProfile(double multiplier, int hits) {
        this.multiplier = multiplier;
        this.hits = Math.max(1, hits);
        return this;
    }

    /** Which elements break its toughness. */
    public StubEnemy weakTo(DamageElement... elements) {
        this.weaknesses = elements == null ? Set.of() : Set.of(elements);
        return this;
    }

    public StubEnemy withDebuffResist(Map<String, Double> value) {
        if (value != null) {
            this.debuffResist = value;
        }
        return this;
    }

    /**
     * Another skill, used from {@code hpRatio} downwards - the engine's phase mechanism, which is the only way an
     * enemy can act with more than one ability today.
     *
     * @param hpRatio at or below this share of max HP, this skill is what the enemy acts with
     */
    public StubEnemy phase(double hpRatio, DamageElement element, double multiplier) {
        thresholds.add(new double[] {hpRatio});
        extraSkills.add(new EnemySkill(element == null ? this.element : element, multiplier, 1,
                DamageType.NORMAL));
        return this;
    }

    /** A second ordinary skill with a stated hit count, for cases that need a multi-hit enemy. */
    public StubEnemy phase(double hpRatio, DamageElement element, double multiplier, int hits) {
        thresholds.add(new double[] {hpRatio});
        extraSkills.add(new EnemySkill(element == null ? this.element : element, multiplier, Math.max(1, hits),
                DamageType.NORMAL));
        return this;
    }

    /**
     * The enemy.
     *
     * <p>Its {@code COMMON} skill is the stated ordinary attack, so it can always act; each {@link #phase} skill is
     * registered at its threshold. Note: the phase skills are registered by HP ratio, and the engine takes the
     * TIGHTEST threshold the enemy is at or below - the same rule {@link Enemy#activeSkill} documents.
     */
    public Enemy build() {
        AttributeBuilder attributes = new AttributeBuilder()
                .setBase(com.laosun.aluminium.enums.AttributeType.HEALTH, health)
                .setBase(com.laosun.aluminium.enums.AttributeType.ATTACK, attack)
                .setBase(com.laosun.aluminium.enums.AttributeType.DEFENCE, defence)
                .setBase(com.laosun.aluminium.enums.AttributeType.SPEED, speed)
                .setBase(com.laosun.aluminium.enums.AttributeType.EFFECT_RESISTANCE, effectResistance)
                .setBase(com.laosun.aluminium.enums.AttributeType.EFFECT_HIT_RATE, effectHitRate);
        Enemy enemy = new Enemy(name, attributes.build());
        enemy.setDamageResist(Map.copyOf(damageResist));
        enemy.setDebuffResist(debuffResist);
        enemy.setStanceWeak(weaknesses.isEmpty() ? Set.of(element) : weaknesses);
        enemy.setStance(toughness);
        enemy.setMaxStance(toughness);
        enemy.setStanceType(element);
        enemy.setSkill(SkillType.COMMON, new EnemySkill(element, multiplier, hits, damageType));
        for (int i = 0; i < extraSkills.size(); i++) {
            enemy.setPhaseSkill(thresholds.get(i)[0], extraSkills.get(i));
        }
        return enemy;
    }

    /**
     * A map from element name to resistance, for {@link #withDebuffResist} - the data's own shape.
     *
     * <p>Kept here so a case can state "resists frozen" without building a map by hand.
     */
    public static Map<String, Double> debuffResistance(Object... pairs) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out.put(String.valueOf(pairs[i]), ((Number) pairs[i + 1]).doubleValue());
        }
        return out;
    }
}
