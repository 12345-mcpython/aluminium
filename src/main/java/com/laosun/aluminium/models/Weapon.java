package com.laosun.aluminium.models;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.Translate;
import com.laosun.aluminium.beans.WeaponData;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.utils.AttributeBuilder;
import com.laosun.aluminium.utils.LevelPromotionCalc;
import lombok.*;

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
public class Weapon implements Cloneable {
    /**
     * Display name (bilingual).
     */
    /**
 * The light cone's id, which names its rule file under {@code resources/light_cones/}.
     * It was already in hand at construction and simply not kept.
     */
    private int wid;

    /**
 * The light cone's <b>superimposition rank</b> (1..5), which selects the skill row whose values apply.
     *
     * <p>Note: Before this the engine always read the FIRST row - i.e. it modelled rank 1 without saying so. The default stays
     * 1, so every existing caller behaves exactly as before; a caller that knows the wearer's rank can now say it.
     */
    private int rank = 1;
    private Translate name;
    /**
     * Skill description text.
     */
    private String description;
    /**
     * Base health contributed by this weapon.
     */
    private double health;
    /**
     * Base attack contributed by this weapon.
     */
    private double attack;
    /**
     * Base defence contributed by this weapon.
     */
    private double defence;
    /**
     * Weapon type string (e.g. "Destruction").
     */
    private String type;
    /**
     * Ability properties that provide attribute modifiers.
     */
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
    public static Weapon build(int wid, int level, boolean isPromote, int rank) {
        WeaponData wp = Constant.WEAPONS.get(wid);
        List<WeaponAttribute> weaponAttribute = new ArrayList<>();
        if (wp == null) {
            throw new RuntimeException("Weapon not found!");
        }
        // Note: By RANK, not always the first row: the rows are the five superimposition ranks, and taking
        // `getFirst()` silently modelled rank 1 for every light cone in the game.
        WeaponData.SkillData row = wp.weaponSkillData().stream()
                .filter(candidate -> candidate.level() == rank)
                .findFirst()
                .orElseGet(() -> wp.weaponSkillData().getFirst());
        for (WeaponData.SkillData.AbilityProperty p : row.abilityProperties()) {
            weaponAttribute.add(new WeaponAttribute(AttributeType.fromString(p.attribute()), p.value()));
        }

        double rate = LevelPromotionCalc.calcWeaponRate(level, isPromote);
        return new Weapon(wid, rank, wp.name(), "", wp.health() * rate,
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
    public static Weapon build(int wid, int level, boolean isPromote) {
        return build(wid, level, isPromote, 1);
    }

    public static Weapon build(int wid, int level) {
        return build(wid, level, false, 1);
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

    @Override
    @SneakyThrows
    public Weapon clone() {
        Weapon clone = (Weapon) super.clone();
        clone.weaponAttribute = new ArrayList<>(weaponAttribute);
        return clone;
    }

    /**
     * An ability property that maps an attribute type to a value.
     */
    public record WeaponAttribute(AttributeType attribute, double value) {
    }
}
