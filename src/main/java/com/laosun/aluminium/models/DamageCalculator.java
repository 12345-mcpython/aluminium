package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.AttributeType;

import java.util.HashMap;
import java.util.Map;

import static com.laosun.aluminium.enums.AttributeType.*;

/**
 * Central damage and healing calculation utility for the combat system.
 *
 * <p>Uses {@link DoubleValue} for composable modifier stacking, following
 * the Honkai: Star Rail damage formula:
 * <pre>{@code
 * base = ATK * multiplier
 * damage = base * (1 + elemBoost + allBoost + pen) * defMult * (1 + critDmg)
 * }</pre>
 *
 * <p>Healing formula:
 * <pre>{@code
 * heal = HP * multiplier * (1 + healingBoost) * (1 + healTakenRatio)
 * }</pre>
 */
public final class DamageCalculator {

    private static final Map<String, AttributeType> ELEMENT_TO_BOOST = new HashMap<>();

    static {
        ELEMENT_TO_BOOST.put("ice", ICE_DAMAGE_BOOST);
        ELEMENT_TO_BOOST.put("fire", FIRE_DAMAGE_BOOST);
        ELEMENT_TO_BOOST.put("wind", WIND_DAMAGE_BOOST);
        ELEMENT_TO_BOOST.put("thunder", THUNDER_DAMAGE_BOOST);
        ELEMENT_TO_BOOST.put("physical", PHYSICAL_DAMAGE_BOOST);
        ELEMENT_TO_BOOST.put("quantum", QUANTUM_DAMAGE_BOOST);
        ELEMENT_TO_BOOST.put("imaginary", IMAGINARY_DAMAGE_BOOST);
    }

    private DamageCalculator() {
    }

    /**
     * Returns the {@link AttributeType} damage boost for the given element string.
     */
    public static AttributeType elementToBoost(String element) {
        return ELEMENT_TO_BOOST.get(element.toLowerCase());
    }

    /**
     * Calculates the damage dealt by an ATK-scaling skill using {@link DoubleValue}
     * modifier composition.
     *
     * @param attacker   the attacking entity
     * @param defender   the defending entity
     * @param element    the element type string of the damage
     * @return the calculated damage value
     */
    public static double calculateDamage(CanHit attacker, CanHit defender, String element) {
        double atk = attacker.getAttribute(ATTACK).get();

        DoubleValue damage = new DoubleValue(atk);

        AttributeType elemBoost = elementToBoost(element);
        double elementBonus = elemBoost != null ? attacker.getAttribute(elemBoost).get() : 0;
        double allTypeBonus = attacker.getAttribute(ALL_DAMAGE_TYPE_BOOST).get();
        double pen = attacker.getAttribute(DAMAGE_PENETRATION).get();
        double addPercent = elementBonus + allTypeBonus + pen;
        if (addPercent != 0) {
            damage.addModifier(DoubleValue.Modifier.addPercent(addPercent));
        }

        double ignore = Math.min(attacker.getAttribute(DEFENCE_IGNORE).get(), 1.0);
        double defenderDef = defender.getAttribute(DEFENCE).get();
        double effectiveDef = defenderDef * (1 - ignore);
        double defMult = 1 - (effectiveDef / (effectiveDef + 200 + 10 * 80));
        damage.addModifier(DoubleValue.Modifier.multiplyPercent(defMult - 1));

        double critRate = Math.min(attacker.getAttribute(CRIT_CHANCE).get(), 1.0);
        double critDmg = attacker.getAttribute(CRIT_ATTACK).get();
        if (Math.random() < critRate) {
            damage.addModifier(DoubleValue.Modifier.multiplyPercent(critDmg));
        }

        return damage.get();
    }

    /**
     * Calculates healing amount using {@link DoubleValue} modifier composition.
     *
     * @param healer     the healing entity
     * @param multiplier healing multiplier (percentage of healer's max HP)
     * @return the calculated heal amount
     */
    public static double calculateHeal(CanHit healer, double multiplier) {
        DoubleValue healValue = new DoubleValue(healer.getAttribute(HEALTH).get() * multiplier);
        double healingBoost = healer.getAttribute(OUTGOING_HEALING_BOOST).get();
        if (healingBoost != 0) {
            healValue.addModifier(DoubleValue.Modifier.addPercent(healingBoost));
        }
        return healValue.get();
    }

    /**
     * Calculates healing amount with target's heal taken ratio.
     *
     * @param healer     the healing entity
     * @param target     the target being healed
     * @param multiplier healing multiplier
     * @return the calculated heal amount
     */
    public static double calculateHeal(CanHit healer, CanHit target, double multiplier) {
        DoubleValue healValue = new DoubleValue(healer.getAttribute(HEALTH).get() * multiplier);
        double healingBoost = healer.getAttribute(OUTGOING_HEALING_BOOST).get();
        double healTaken = target.getAttribute(HEAL_TAKEN_RATIO).get();
        if (healingBoost != 0) {
            healValue.addModifier(DoubleValue.Modifier.addPercent(healingBoost));
        }
        if (healTaken != 0) {
            healValue.addModifier(DoubleValue.Modifier.multiplyPercent(healTaken));
        }
        return healValue.get();
    }
}
