package com.laosun.aluminium.models;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.Translate;
import com.laosun.aluminium.beans.WeaponData;
import com.laosun.aluminium.utils.LevelPromotionCalc;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

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

    public static Weapon build(int wid, int level, boolean isPromote) {
        WeaponData wp = Constant.WEAPONS.get(wid);
        if (wp == null) {
            throw new RuntimeException("Weapon not found!");
        }

        return new Weapon(wp.name(), "", wp.health() * LevelPromotionCalc.calcWeaponRate(level, isPromote),
                wp.attack() * LevelPromotionCalc.calcWeaponRate(level, isPromote),
                wp.defence() * LevelPromotionCalc.calcWeaponRate(level, isPromote),
                wp.type());
    }

    public static Weapon build(int wid, int level) {
        return build(wid, level, false);
    }
}
