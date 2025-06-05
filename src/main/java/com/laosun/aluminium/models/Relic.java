package com.laosun.aluminium.models;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.exceptions.RelicException;
import com.laosun.aluminium.utils.MapUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@Getter
@Setter
@ToString
public class Relic {
    public int level;
    public String name;
    public int star;
    public Type relicType;
    public Attribute mainAttribute;
    public List<Attribute> subAttributes;

    public static final List<String> SUB_ATTRIBUTE_LIST = List.of("health", "attack", "defence", "health_percent", "attack_percent", "defence_percent", "speed", "crit_chance", "crit_attack", "effect_hit_rate", "effect_resistance", "breaking_effect");

    public static Relic create(int level, int star, Type relicType, Attribute mainAttribute, List<Attribute> subAttributes) {
        Relic relic = new Relic();
        relic.level = level;
        relic.star = star;
        relic.relicType = relicType;
        relic.mainAttribute = mainAttribute;
        relic.subAttributes = subAttributes;
        return relic;
    }

    public static Relic createRandomLevelZero(@NotNull Type relicType, int star) {
        if (star <= 2 || star > 5) {
            throw new RelicException("Star level expected star 2-5 but given " + star);
        }
        var partValue = Constant.RELIC_MAIN_ATTRIBUTES.get(star).get(relicType.getType());
        var entry = MapUtils.getRandomEntry(partValue);
        var mainAttribute = new Attribute(entry.getKey(), entry.getValue().get("base"));
        var subCandidates = new ArrayList<>(SUB_ATTRIBUTE_LIST);
        subCandidates.removeIf(e -> e.equals(mainAttribute.left()));

        var selectedSubEntries = MapUtils.getRandomList(subCandidates, new Random().nextInt(3, 5));
        var subAttributes = selectedSubEntries.stream()
                .map(e -> new Attribute(e, Constant.RELIC_SUB_ATTRIBUTES.getAttributeGroupByStar(star).getAttributeByName(e).base()))
                .toList();
        return create(0, star, relicType, mainAttribute, subAttributes);
    }

    public static Relic createBySetting(@NotNull Type relicType, int star, int level, Setting setting) {
        var subAttributesValueMap = Constant.RELIC_SUB_ATTRIBUTES.getAttributeGroupByStar(star);
        var mainAttributeValue = Constant.RELIC_MAIN_ATTRIBUTES.get(star).get(relicType.getType()).get(setting.getMainAttribute());
        if (mainAttributeValue == null) {
            throw new RelicException(String.format("Main attribute '%s' not found in such Relic.Type: '%s'", setting.getMainAttribute(), relicType.getType()));
        }
        var mainAttributeBase = mainAttributeValue.get("base");
        var mainAttributeBonus = mainAttributeValue.get("bonus");
        var mainAttributeClass = new Attribute(setting.getMainAttribute(), mainAttributeBase + mainAttributeBonus * level);
        var subAttributeClasses = new ArrayList<Attribute>();
        for (var subAttributes : setting.getSubAttributes()) {
            var subAttributeValue = subAttributesValueMap.getAttributeByName(subAttributes);
            var subAttributeBase = subAttributeValue.base();
            var subAttributeBonus = subAttributeValue.bonus();
            var calc = subAttributeBase * (setting.subAttributes.get(subAttributes).promoteLevel + 1) + subAttributeBonus * setting.subAttributes.get(subAttributes).attributeLevel;
            subAttributeClasses.add(new Attribute(subAttributes, calc, setting.subAttributes.get(subAttributes).promoteLevel));
        }
        return create(level, star, relicType, mainAttributeClass, subAttributeClasses);
    }

    public static Relic createBySetting(@NotNull Type relicType, int star, int level, String data) {
        return createBySetting(relicType, star, level, Setting.fromJson(data));
    }

    @Getter
    @ToString
    public static class Setting {
        @SerializedName("main_attribute")
        private final Map<String, Integer> mainAttribute;
        @SerializedName("sub_attributes")
        private final Map<String, AttributeLevel> subAttributes;

        public Setting(Map<String, Integer> mainAttributeL,
                       Map<String, AttributeLevel> subAttributesL) {
            this.mainAttribute = mainAttributeL;
            this.subAttributes = subAttributesL;
        }

        public static Setting fromJson(String json) {
            return new Gson().fromJson(json, Setting.class);
        }

        public record AttributeLevel(@SerializedName("promote_level") int promoteLevel,
                                     @SerializedName("attribute_level") int attributeLevel) {
        }

        public String getMainAttribute() {
            return mainAttribute.entrySet().iterator().next().getKey();
        }

        public List<String> getSubAttributes() {
            return new ArrayList<>(subAttributes.keySet());
        }
    }

    public record Attribute(String left, double right, int promoteLevel) {
        public Attribute(String left, double right) {
            this(left, right, 0);
        }
    }

    @Getter
    public enum Type {

        HEAD("head"),
        HAND("hand"),
        BODY("body"),
        BOOT("boot"),
        BALL("ball"),
        LINE("line");

        private final String type;

        private static final Map<String, Type> MP = Map.ofEntries(Map.entry("head", HEAD), Map.entry("boot", BOOT),
                Map.entry("hand", HAND), Map.entry("body", BODY),
                Map.entry("ball", BALL), Map.entry("line", LINE));

        public static Type getType(String type) {
            return MP.get(type);
        }

        Type(String string) {
            this.type = string;
        }
    }
}
