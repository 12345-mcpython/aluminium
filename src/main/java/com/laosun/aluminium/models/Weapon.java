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
public class Weapon implements Cloneable {
    /**
     * Display name (bilingual).
     */
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
     * The light cone's conditional passive (光锥被动), interpreted from its
     * description. Always-on stat parts are covered by {@link #weaponAttribute}.
     */
    @Setter
    private Trace passiveTrace;

    /**
     * Constructs a weapon from base stats and ability properties.
     */
    public Weapon(Translate name, String description, double health, double attack, double defence,
                  String type, List<WeaponAttribute> weaponAttribute) {
        this.name = name;
        this.description = description;
        this.health = health;
        this.attack = attack;
        this.defence = defence;
        this.type = type;
        this.weaponAttribute = weaponAttribute;
    }

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
        Weapon weapon = new Weapon(wp.name(), "", wp.health() * rate,
                wp.attack() * rate,
                wp.defence() * rate,
                wp.type(), weaponAttribute);
        String chineseDesc = wp.skillDescription() != null ? wp.skillDescription().chinese() : "";
        List<String> propertyStrings = weaponAttribute.stream().map(a -> a.attribute().attributeString).toList();
        // 20*** 光锥使用手写被动 (LightConePassives), 其余回退到通用解释器.
        List<Double> skillValue = wp.weaponSkillData().getFirst().skillValue();
        Trace handWritten = com.laosun.aluminium.models.kit.LightConePassives.forWeapon(wid, skillValue);
        weapon.setPassiveTrace(handWritten != null ? handWritten
                : GenericPassives.interpretWeaponPassive(chineseDesc, skillValue, propertyStrings));
        return weapon;
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
