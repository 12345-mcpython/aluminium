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

@Getter
@Setter
@ToString
@AllArgsConstructor
public class Weapon {
    private Translate name;
    private String description;
    private double health;
    private double attack;
    private double defence;
    private String type;
    private List<WeaponAttribute> weaponAttribute;


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

    public static Weapon build(int wid, int level) {
        return build(wid, level, false);
    }

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

    public record WeaponAttribute(AttributeType attribute, double value) {
    }
}
