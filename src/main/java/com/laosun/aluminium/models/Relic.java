package com.laosun.aluminium.models;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.RelicType;
import com.laosun.aluminium.exceptions.RelicException;
import com.laosun.aluminium.utils.MapUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Getter
@Setter
@ToString
public class Relic {
    public static final List<AttributeType> SUB_ATTRIBUTE_LIST = List.of(
            AttributeType.HEALTH,
            AttributeType.ATTACK,
            AttributeType.DEFENCE,
            AttributeType.HEALTH_PERCENT,
            AttributeType.ATTACK_PERCENT,
            AttributeType.DEFENCE_PERCENT,
            AttributeType.SPEED,
            AttributeType.CRIT_CHANCE,
            AttributeType.CRIT_ATTACK,
            AttributeType.EFFECT_HIT_RATE,
            AttributeType.EFFECT_RESISTANCE,
            AttributeType.BREAKING_EFFECT
    );
    public int level;
    public String name;
    public int star;
    public RelicType relicType;
    public Attribute mainAttribute;
    public List<Attribute> subAttributes;

    public static Relic create(int level, int star, RelicType relicType, Attribute mainAttribute, List<Attribute> subAttributes) {
        Relic relic = new Relic();
        relic.level = level;
        relic.star = star;
        relic.relicType = relicType;
        relic.mainAttribute = mainAttribute;
        relic.subAttributes = subAttributes;
        return relic;
    }

    public static Relic createRandomLevelZero(@NotNull RelicType relicType, int star) {
        if (star <= 2 || star > 5) {
            throw new RelicException("Star level expected star 2-5 but given " + star);
        }
        var partValue = Constant.RELIC_MAIN_ATTRIBUTES.getAttributeByStar(star).get(relicType);
        var entry = MapUtils.getRandomEntry(partValue);
        var mainAttribute = new Attribute(entry.getKey(), entry.getValue().base());
        var subCandidates = new ArrayList<>(SUB_ATTRIBUTE_LIST);
        subCandidates.removeIf(e -> e.equals(mainAttribute.type));

        var selectedSubEntries = MapUtils.getRandomList(subCandidates, new Random().nextInt(3, 5));
        var subAttributes = selectedSubEntries.stream()
                .map(e -> new Attribute(e, Constant.RELIC_SUB_ATTRIBUTES.getAttributeGroupByStar(star).getAttributeByName(e).base()))
                .toList();
        return create(0, star, relicType, mainAttribute, subAttributes);
    }

    public static Relic createBySetting(@NotNull RelicType relicType, int star, int level, Setting setting) {
        checkLegal(setting);
        var subAttributesValueMap = Constant.RELIC_SUB_ATTRIBUTES.getAttributeGroupByStar(star);
        var mainAttributeValue = Constant.RELIC_MAIN_ATTRIBUTES.getAttributeByStar(star).get(relicType).get(setting.getMainAttribute());
        if (mainAttributeValue == null) {
            throw new RelicException(String.format("Main attribute '%s' not found in such Relic.Type: '%s'", setting.getMainAttribute(), relicType.getType()));
        }
        var mainAttributeBase = mainAttributeValue.base();
        var mainAttributeBonus = mainAttributeValue.bonus();
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

    public static Relic createBySetting(@NotNull RelicType relicType, int star, int level, String data) {
        return createBySetting(relicType, star, level, Setting.fromJson(data));
    }

    private static void checkLegal(Relic.Setting setting) {
        if (setting.getMainAttributeLevel() > 15 || setting.getMainAttributeLevel() < 0) {
            throw new RelicException(String.format("Main attribute `%s` level must be between 0 and 15", setting.getMainAttribute()));
        }

        for (Map.Entry<AttributeType, Setting.AttributeLevel> attributeLevelEntry : setting.subAttributes.entrySet()) {
            if (attributeLevelEntry.getValue().attributeLevel > (attributeLevelEntry.getValue().promoteLevel + 1) * 2) {
                throw new RelicException(String.format("Sub attribute `%s` level must be between 0 and " + (attributeLevelEntry.getValue().attributeLevel - 1) * 2, attributeLevelEntry.getKey()));
            }
        }
    }

    @Getter
    @ToString
    public static class Setting {
        @SerializedName("main_attribute")
        private final Map<AttributeType, Integer> mainAttribute;
        @SerializedName("sub_attributes")
        private final Map<AttributeType, AttributeLevel> subAttributes;

        public Setting(Map<AttributeType, Integer> mainAttributeL,
                       Map<AttributeType, AttributeLevel> subAttributesL) {
            this.mainAttribute = mainAttributeL;
            this.subAttributes = subAttributesL;
        }

        public static Setting fromJson(String json) {
            return new Gson().fromJson(json, Setting.class);
        }

        public AttributeType getMainAttribute() {
            return mainAttribute.entrySet().iterator().next().getKey();
        }

        public Integer getMainAttributeLevel() {
            return mainAttribute.entrySet().iterator().next().getValue();
        }

        public List<AttributeType> getSubAttributes() {
            return new ArrayList<>(subAttributes.keySet());
        }

        public record AttributeLevel(@SerializedName("promote_level") int promoteLevel,
                                     @SerializedName("attribute_level") int attributeLevel) {
        }
    }

    @Getter
    public static class Attribute {
        private final AttributeType type;
        private final double value;
        private final int promoteLevel;

        public Attribute(AttributeType type, double value, int promoteLevel) {
            this.type = type;
            this.value = value;
            this.promoteLevel = promoteLevel;
        }

        public Attribute(AttributeType type, double value) {
            this(type, value, 0);
        }

        @Override
        public String toString() {
            return "<Attribute name=" + type + ", value=" + value + ", promoteLevel=" + promoteLevel + ">";
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int star = 5;          // 默认 5 星
        private int level = 0;
        private RelicType type;
        private AttributeType mainAttributeName;
        private final List<SubAttributeSpec> subSpecs = new ArrayList<>();

        private record SubAttributeSpec(AttributeType name, int promoteLevel, int attributeLevel) {
        }

        public Builder star(int star) {
            this.star = star;
            return this;
        }

        public Builder level(int level) {
            this.level = level;
            return this;
        }

        public Builder type(RelicType type) {
            this.type = type;
            return this;
        }

        public Builder mainAttribute(AttributeType name) {
            this.mainAttributeName = name;
            return this;
        }

        public Builder subAttribute(AttributeType name, int promoteLevel, int attributeLevel) {
            subSpecs.add(new SubAttributeSpec(name, promoteLevel, attributeLevel));
            return this;
        }

        public Relic build() {
            if (type == null) {
                throw new IllegalStateException("Relic type must be set");
            }
            if (mainAttributeName == null) {
                throw new IllegalStateException("Main attribute must be set");
            }

            var mainAttrMap = Constant.RELIC_MAIN_ATTRIBUTES.getAttributeByStar(star).get(mainAttributeName);
            if (!mainAttrMap.containsKey(mainAttributeName)) {
                throw new RelicException("Main attribute '" + mainAttributeName +
                        "' is not valid for relic type " + type);
            }
            var mainAttrValue = mainAttrMap.get(mainAttributeName);
            double mainFinal = mainAttrValue.base() + mainAttrValue.bonus() * level;
            Attribute mainAttr = new Attribute(mainAttributeName, mainFinal);

            List<Attribute> subAttrs = new ArrayList<>();
            var subAttrGroup = Constant.RELIC_SUB_ATTRIBUTES.getAttributeGroupByStar(star);
            for (SubAttributeSpec spec : subSpecs) {
                var subVal = subAttrGroup.getAttributeByName(spec.name);
                double subFinal = subVal.base() * (spec.promoteLevel + 1) +
                        subVal.bonus() * spec.attributeLevel;
                subAttrs.add(new Attribute(spec.name, subFinal, spec.promoteLevel));
            }

            return Relic.create(level, star, type, mainAttr, subAttrs);
        }
    }
}
