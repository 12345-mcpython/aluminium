package com.laosun.aluminium;

import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.beans.RelicMainAttribute;
import com.laosun.aluminium.beans.RelicSubAttribute;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.JSONReader;

import java.util.Map;


public final class Constant {
    // <star level> part attribute <base bonus> <double value>
    public static final RelicMainAttribute RELIC_MAIN_ATTRIBUTES;
    public static final RelicSubAttribute RELIC_SUB_ATTRIBUTES;
    public static final Map<Integer, Weapon> WEAPONS;

    static {
        RELIC_MAIN_ATTRIBUTES = JSONReader.fromJSON("main_attribute.json", RelicMainAttribute.class);
        RELIC_SUB_ATTRIBUTES = JSONReader.fromJSON("sub_attribute.json", RelicSubAttribute.class);
        WEAPONS = JSONReader.fromJSON("weapons.json", new TypeToken<Map<Integer, Weapon>>() {
        }.getType());
    }
}
