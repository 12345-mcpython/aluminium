package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.enemy.Enemy;

/**
 * Weakness break damage (P4-3).
 *
 * <pre>
 * break damage = break base (level) × (1 + break effect) × toughness reduction value × DEF zone × RES zone × reduction zone
 * </pre>
 *
 * <p><b>Cannot crit, does not take ATK / DMG boost</b> — so only
 * {@code break base × (1 + break effect) × toughness reduction value} is folded into
 * {@link Damage}'s base; the DEF zone / RES zone are assembled centrally by {@link Battle#applyDamage},
 * and DMG boost and crit are blocked by {@link DamageType#BREAK}'s own {@code (crittable=false, boostable=false)}.
 *
 * <p><b>Units MUST come as a matched set (HSR.md §7.1)</b>: the doc gives level 80 "base break base 3767"
 * (toughness reduction unit "conventional", basic attack = 1) and "super break 376.7"
 * (toughness reduction unit "point", basic attack = 10). This class uses
 * {@code breaking_rate.json / 10 = 376.75535}, so the {@code stanceDamage} passed in
 * **MUST be on the "point" scale** (e.g. 30-point basic attack × 2.5 break bonus = 112.5).
 * If it ever switches to 3767, the toughness reduction value must be divided by 10 in step,
 * otherwise it is off by 10×; P4-6 super break uses the same scale.
 */
public final class BreakDamageCalculator {

    private BreakDamageCalculator() {
    }

    /**
     * Break base for the given level (already {@code /10} in project units).
     *
     * @param attacker the attacker (its level is used)
     * @return break base (level 80 = 376.75535)
     * @throws IllegalArgumentException no data for that level (fail fast, never silently compute 0)
     */
    public static double breakBaseOf(CanHit attacker) {
        Double raw = Constant.BREAKING_RATE.get(attacker.getLevel());
        if (raw == null) {
            throw new IllegalArgumentException("No breaking rate for level " + attacker.getLevel());
        }
        return raw / 10.0;                                   // the data file holds 10× values
    }

    /**
     * Builds one instance of break damage (**not settled**; the caller takes it to
     * {@link Battle#applyDamage}).
     *
     * <p>The energy gain policy is decided by the caller: this instance is extra damage
     * **derived from the same attack**, so {@code Battle.reduceToughness} settles it with
     * {@code EnergyGrant.KILL_ONLY} — it grants no energy gain to the target being hit
     * ("one attack action grants the target only one energy gain"), but a kill is still
     * credited to the attacker.
     *
     * <p>Note that here we **deliberately do not set** {@code notCountsAsAttack()}:
     * break damage itself **is attack damage** (it is just not "one attack action").
     * Marking it as "does not count as an attack" would also disable any future effect
     * that triggers "when dealing attack damage". "It is a derived segment" is the energy
     * gain parameter's business, not that flag's.
     *
     * @param attacker     the one causing the break (level decides the break base, attributes decide break effect)
     * @param enemy        the target being broken
     * @param element      break element (= the element of the segment that triggered the break)
     * @param stanceDamage the toughness points reduced this time (the skill's {@code stance_list} value, unit "point")
     * @return a {@link DamageType#BREAK} damage with base already folded
     * @throws IllegalArgumentException no break base for that level (missing data, fail fast)
     */
    public static Damage build(CanHit attacker, Enemy enemy, DamageElement element, double stanceDamage) {
        double breakingEffect = attacker.getAttribute(AttributeType.BREAKING_EFFECT).get();
        return new Damage(attacker, enemy, element, DamageType.BREAK,
                breakBaseOf(attacker) * (1 + breakingEffect) * stanceDamage);
    }

    /**
     * Builds one instance of **super break** damage (P4-6): same structure as {@link #build},
     * plus one independent DMG boost zone, with the type switched to
     * {@link DamageType#SUPER_BREAK}.
     *
     * <pre>
     * base = break base (level) × (1 + break effect) × excess toughness reduction value × (1 + super break boost)
     * </pre>
     *
     * <p>Two differences from break damage:
     * <ul>
     *   <li>The {@code superBreakStance} passed in is the **excess portion** (nominal toughness
     *       reduction value − value actually reduced), not the whole instance's toughness reduction
     *       value — see {@link Battle.StanceResult}. In the instance that breaks the toughness,
     *       the earlier portion of the toughness reduction has already been used for break damage;
     *       only the excess half may be used here, otherwise the same nominal value is used twice;</li>
     *   <li>It additionally multiplies {@code 1 + Constant.SUPER_BREAK_BOOST} (an independent
     *       DMG boost zone, unrelated to the regular DMG boost zone).</li>
     * </ul>
     *
     * <p>Cannot crit, does not take regular DMG boost — blocked by
     * {@code DamageType.SUPER_BREAK}'s own two flags, so here too only base is folded and the
     * DEF zone / RES zone are assembled centrally by {@link Battle#applyDamage}.
     *
     * <p><b>Why {@code notCountsAsAttack()} is set here but not in {@link #build}</b>:
     * the two entry points have different "settlement contexts" —
     * <ul>
     *   <li>{@link #build} (break damage) is settled inside {@code Battle.reduceToughness},
     *       where {@code EnergyGrant.KILL_ONLY} can already be passed explicitly, so there is no
     *       need to express "derived segment" through a flag, and the semantics of "break damage
     *       is attack damage" are preserved;</li>
     *   <li>this method (super break) is constructed by {@code SkillExecutor} and settled through
     *       the **public entry point** {@link Battle#applyDamage}, where there is nowhere to pass
     *       energy gain parameters, so this semantics can only be encoded on the damage object.</li>
     * </ul>
     * The effect is the same: neither grants energy gain to the target being hit, and both credit
     * kills to the attacker (the kill side does not look at this flag).
     *
     * @param attacker         the caster (level decides the break base, attributes decide break effect)
     * @param enemy            the target (should be in the broken state)
     * @param element          the element of this segment
     * @param superBreakStance the portion of toughness reduction exceeding the remaining toughness
     * @return a {@link DamageType#SUPER_BREAK} damage with base already folded
     * @throws IllegalArgumentException no break base for that level (missing data, fail fast)
     */
    public static Damage buildSuperBreak(CanHit attacker, Enemy enemy, DamageElement element,
                                         double superBreakStance) {
        double breakingEffect = attacker.getAttribute(AttributeType.BREAKING_EFFECT).get();
        double base = breakBaseOf(attacker) * (1 + breakingEffect) * superBreakStance
                * (1 + Constant.SUPER_BREAK_BOOST);
        return new Damage(attacker, enemy, element, DamageType.SUPER_BREAK, base).notCountsAsAttack();
    }
}
