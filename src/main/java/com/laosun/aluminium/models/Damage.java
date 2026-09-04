package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.DamageElement;
import lombok.Getter;
import lombok.NonNull;

import java.util.Objects;

/**
 * The object which will process in calculateDamage.
 * <p>
 * Only build when the skill cause the damage.
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
    /**
     * the skill base value
     * eg: Deals Ice DMG equal to 140% of C's ATK to one enemy. ATK * 1.40 is the base value.
     */
    private final double skillBaseValue;

    public Damage(CanHit attacker, CanHit defender, DamageElement element, double skillBaseValue) {
        this.attacker = attacker;
        this.defender = defender;
        this.element = Objects.requireNonNull(element);
        this.skillBaseValue = skillBaseValue;
    }
}
