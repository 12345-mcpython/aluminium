package com.laosun.aluminium.models;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.Translate;
import com.laosun.aluminium.beans.WeaponData;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.utils.AttributeBuilder;
import com.laosun.aluminium.utils.LevelPromotionCalc;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

import static com.laosun.aluminium.Constant.PERCENT_TO_BASE;

/**
 * A weapon (light cone) equipped by a character.
 *
 * <p>Weapons provide base stat bonuses (HP/ATK/DEF) and ability properties
 * that are applied as modifiers during attribute calculation.
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
public class Weapon {
    /** Display name (bilingual). */
    private Translate name;
    /** Skill description text. */
    private String description;
    /** Base health contributed by this weapon. */
    private double health;
    /** Base attack contributed by this weapon. */
    private double attack;
    /** Base defence contributed by this weapon. */
    private double defence;
    /** Weapon type string (e.g. "Destruction"). */
    private String type;
    /** Ability properties that provide attribute modifiers. */
    private List<WeaponAttribute> weaponAttribute;

    /**
     * Builds a weapon from game data by ID, applying level scaling.
     *
     * @param wid       the weapon's game ID
     * @param level     weapon level (1-80)
     * @param isPromote whether the weapon is promoted at the ascension threshold
     * @return the constructed weapon
     * @throws RuntimeException if the weapon ID is not found
     */
    public static Weapon build(int wid, int level, boolean isPromote) {
        WeaponData wp = Constant.WEAPONS.get(wid);
        List<WeaponAttribute> weaponAttribute = new ArrayList<>();
        if (wp == null) {
            throw new RuntimeException("Weapon not found!");
        }
        for (WeaponData.SkillData.AbilityProperty p : wp.weaponSkillData().getFirst().abilityProperties()) {
            weaponAttribute.add(new WeaponAttribute(AttributeType.fromString(p.attribute()), p.value()));
        }

        double rate = LevelPromotionCalc.calcWeaponRate(level, isPromote);
        return new Weapon(wp.name(), "", wp.health() * rate,
                wp.attack() * rate,
                wp.defence() * rate,
                wp.type(), weaponAttribute);
    }

    /**
     * Builds a weapon without promotion.
     *
     * @param wid   the weapon's game ID
     * @param level weapon level (1-80)
     * @return the constructed weapon
     */
    public static Weapon build(int wid, int level) {
        return build(wid, level, false);
    }

    /**
     * Applies this weapon's ability modifiers to the attribute builder.
     *
     * @param atb the builder to append to
     */
    public void appendTo(AttributeBuilder atb) {
        for (WeaponAttribute wa : weaponAttribute) {
            if (PERCENT_TO_BASE.containsKey(wa.attribute)) {
                atb.addPercent(wa.attribute, wa.value, DoubleValue.Modifier.ModifierSource.WEAPON);
            } else {
                if (wa.attribute.isPercent) {
                    atb.addPercentPoint(wa.attribute, wa.value, DoubleValue.Modifier.ModifierSource.WEAPON);
                } else {
                    atb.addPure(wa.attribute, wa.value, DoubleValue.Modifier.ModifierSource.WEAPON);
                }
            }
        }
    }

    /**
     * An ability property that maps an attribute type to a value.
     */
    public record WeaponAttribute(AttributeType attribute, double value) {
    }
}
