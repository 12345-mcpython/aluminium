package com.laosun.aluminium;

import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.beans.CharacterData;
import com.laosun.aluminium.beans.RelicMainAttribute;
import com.laosun.aluminium.beans.RelicSubAttribute;
import com.laosun.aluminium.beans.WeaponData;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.utils.JSONReader;

import java.util.Map;


public final class Constant {
    // <star level> part attribute <base bonus> <double value>
    public static final RelicMainAttribute RELIC_MAIN_ATTRIBUTES;
    public static final RelicSubAttribute RELIC_SUB_ATTRIBUTES;
    public static final Map<Integer, WeaponData> WEAPONS;
    public static final Map<Integer, CharacterData> CHARACTERS;

    public static final Map<AttributeType, AttributeType> PERCENT_TO_BASE = Map.of(
            AttributeType.HEALTH_PERCENT, AttributeType.HEALTH,
            AttributeType.ATTACK_PERCENT, AttributeType.ATTACK,
            AttributeType.DEFENCE_PERCENT, AttributeType.DEFENCE,
            AttributeType.SPEED_PERCENT, AttributeType.SPEED
    );

    static {
        RELIC_MAIN_ATTRIBUTES = JSONReader.fromJSON("main_attribute.json", RelicMainAttribute.class);
        RELIC_SUB_ATTRIBUTES = JSONReader.fromJSON("sub_attribute.json", RelicSubAttribute.class);
        WEAPONS = JSONReader.fromJSON("weapons.json", new TypeToken<Map<Integer, WeaponData>>() {
        }.getType());
        CHARACTERS = JSONReader.fromJSON("character_data.json", new TypeToken<Map<Integer, CharacterData>>() {
        }.getType());
    }
}
