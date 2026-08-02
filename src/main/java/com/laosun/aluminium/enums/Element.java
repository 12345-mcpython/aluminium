package com.laosun.aluminium.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * The seven damage elements of Honkai: Star Rail.
 *
 * <p>Each element has:
 * <ul>
 *   <li>{@link #boostAttribute} — the {@link AttributeType} that increases damage
 *   of this element (used in the 增伤区 of the damage pipeline, HSR.md §2.2).</li>
 *   <li>A weakness-break effect (HSR.md §3.2) applied when an enemy's toughness
 *   is depleted by this element.</li>
 * </ul>
 */
@Getter
public enum Element {
    PHYSICAL("physical", AttributeType.PHYSICAL_DAMAGE_BOOST, BreakEffect.BLEED),
    FIRE("fire", AttributeType.FIRE_DAMAGE_BOOST, BreakEffect.BURN),
    ICE("ice", AttributeType.ICE_DAMAGE_BOOST, BreakEffect.FREEZE),
    THUNDER("thunder", AttributeType.THUNDER_DAMAGE_BOOST, BreakEffect.SHOCK),
    WIND("wind", AttributeType.WIND_DAMAGE_BOOST, BreakEffect.WIND_SHEAR),
    QUANTUM("quantum", AttributeType.QUANTUM_DAMAGE_BOOST, BreakEffect.ENTANGLEMENT),
    IMAGINARY("imaginary", AttributeType.IMAGINARY_DAMAGE_BOOST, BreakEffect.IMPRISONMENT);

    private static final Map<String, Element> BY_STRING = new HashMap<>();

    static {
        for (Element element : values()) {
            BY_STRING.put(element.string, element);
        }
    }

    /** String identifier used in the game data files. */
    public final String string;
    /** The attribute that boosts this element's damage. */
    public final AttributeType boostAttribute;
    /** The break effect applied on toughness depletion. */
    public final BreakEffect breakEffect;

    Element(String string, AttributeType boostAttribute, BreakEffect breakEffect) {
        this.string = string;
        this.boostAttribute = boostAttribute;
        this.breakEffect = breakEffect;
    }

    /**
     * Looks up an element by its data-file string (case-insensitive).
     *
     * @param string e.g. "fire", "ice"
     * @return the matching element
     * @throws IllegalArgumentException if the element is unknown
     */
    public static Element fromString(String string) {
        Element element = BY_STRING.get(string.toLowerCase());
        if (element == null) {
            throw new IllegalArgumentException("Unknown Element: " + string);
        }
        return element;
    }

    /**
     * The effect triggered when an enemy's toughness is fully depleted.
     *
     * <p>Values follow the in-game v1.0 behavior:
     * <ul>
     *   <li>Physical — Bleed DoT</li>
     *   <li>Fire — Burn DoT</li>
     *   <li>Wind — Wind Shear DoT</li>
     *   <li>Thunder — Shock DoT</li>
     *   <li>Ice — Freeze (skip action, bonus damage on unfreeze)</li>
     *   <li>Quantum — Entanglement (action delay + extra damage on recovery)</li>
     *   <li>Imaginary — Imprisonment (action delay + SPD down)</li>
     * </ul>
     */
    public enum BreakEffect {
        BLEED, BURN, FREEZE, SHOCK, WIND_SHEAR, ENTANGLEMENT, IMPRISONMENT
    }
}
