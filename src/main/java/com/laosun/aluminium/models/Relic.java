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
import java.util.concurrent.ThreadLocalRandom;

/**
 * A single piece of equipment (relic) with a main attribute and up to 4 sub-attributes.
 *
 * <p>Relics come in 6 types ({@link RelicType}: HEAD, BODY, HAND, BOOT, BALL, LINE),
 * have a star rating (2-5), and a level (0-15). The main attribute's final value is
 * computed as {@code base + bonus * level}. Sub-attributes use the formula
 * {@code base * (promoteLevel + 1) + bonus * attributeLevel}.
 *
 * <p>Multiple creation paths are supported:
 * <ul>
 *   <li>{@link #createRandomLevelZero} — generates a random level-0 relic</li>
 *   <li>{@link #createBySetting} — reconstructs a relic from a JSON/object specification</li>
 *   <li>{@link Builder} — fluent builder for precise control</li>
 * </ul>
 */
@Getter
@Setter
@ToString
public class Relic {
    /** The pool of valid sub-attribute types. */
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
    /** Current upgrade level (0-15). */
    public int level;
    /** Display name of the relic. */
    public String name;
    /** Star rating (2-5). */
    public int star;
    /** Equipment slot this relic occupies. */
    public RelicType relicType;
    /** The main (primary) attribute of this relic. */
    public Attribute mainAttribute;
    /** The sub-attributes (3-4 entries) of this relic. */
    public List<Attribute> subAttributes;

    /**
     * Directly constructs a relic with the given properties.
     *
     * @param level         upgrade level
     * @param star          star rating
     * @param relicType     equipment slot
     * @param mainAttribute the main attribute
     * @param subAttributes the sub-attributes
     * @return the constructed relic
     */
    public static Relic create(int level, int star, RelicType relicType, Attribute mainAttribute, List<Attribute> subAttributes) {
        Relic relic = new Relic();
        relic.level = level;
        relic.star = star;
        relic.relicType = relicType;
        relic.mainAttribute = mainAttribute;
        relic.subAttributes = subAttributes;
        return relic;
    }

    /**
     * Creates a random level-0 relic with a valid main attribute and 3-4 sub-attributes.
     *
     * <p>The main attribute is randomly selected from the pool valid for the given
     * relic type. Sub-attributes are randomly picked (without duplicates of the
     * main attribute) from {@link #SUB_ATTRIBUTE_LIST}.
     *
     * @param relicType the equipment slot
     * @param star      star rating (2-5)
     * @return the generated relic
     * @throws RelicException if star is outside 2-5
     */
    public static Relic createRandomLevelZero(@NotNull RelicType relicType, int star) {
        if (star <= 2 || star > 5) {
            throw new RelicException("Star level expected star 2-5 but given " + star);
        }
        var partValue = Constant.RELIC_MAIN_ATTRIBUTES.getAttributeByStar(star).get(relicType);
        var entry = MapUtils.getRandomEntry(partValue);
        var mainAttribute = new Attribute(entry.getKey(), entry.getValue().base());
        var subCandidates = new ArrayList<>(SUB_ATTRIBUTE_LIST);
        subCandidates.removeIf(e -> e.equals(mainAttribute.type));

        var selectedSubEntries = MapUtils.getRandomList(subCandidates, ThreadLocalRandom.current().nextInt(3, 5));
        var subAttributes = selectedSubEntries.stream()
                .map(e -> new Attribute(e, Constant.RELIC_SUB_ATTRIBUTES.getAttributeGroupByStar(star).getAttributeByName(e).base()))
                .toList();
        return create(0, star, relicType, mainAttribute, subAttributes);
    }

    /**
     * Reconstructs a relic from a structured setting specification.
     *
     * @param relicType the equipment slot
     * @param star      star rating (2-5)
     * @param level     upgrade level (0-15)
     * @param setting   the attribute configuration
     * @return the reconstructed relic
     */
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

    /**
     * Reconstructs a relic from a JSON string specification.
     *
     * @param relicType the equipment slot
     * @param star      star rating
     * @param level     upgrade level
     * @param data      JSON string in {@link Setting} format
     * @return the reconstructed relic
     */
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

    /**
     * A serializable specification for reconstructing a relic's attribute configuration.
     *
     * <p>Used for persisting relic state as JSON or programmatic specification.
     */
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

        private static final Gson GSON = new Gson();

        /** Deserializes a setting from a JSON string. */
        public static Setting fromJson(String json) {
            return GSON.fromJson(json, Setting.class);
        }

        /** Returns the single main attribute type. */
        public AttributeType getMainAttribute() {
            return mainAttribute.entrySet().iterator().next().getKey();
        }

        /** Returns the main attribute's level. */
        public Integer getMainAttributeLevel() {
            return mainAttribute.entrySet().iterator().next().getValue();
        }

        /** Returns all sub-attribute types in this setting. */
        public List<AttributeType> getSubAttributes() {
            return new ArrayList<>(subAttributes.keySet());
        }

        /** Sub-attribute promotion and level state. */
        public record AttributeLevel(@SerializedName("promote_level") int promoteLevel,
                                     @SerializedName("attribute_level") int attributeLevel) {
        }
    }

    /**
     * An attribute entry on a relic with type, numeric value, and optional promote level.
     */
    public record Attribute(AttributeType type, double value, int promoteLevel) {
        public Attribute(AttributeType type, double value) {
            this(type, value, 0);
        }
    }

    /**
     * Creates a new {@link Builder} for programmatic relic construction.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for creating relics with precise attribute control.
     */
    public static class Builder {
        private int star = 5;
        private int level = 0;
        private RelicType type;
        private AttributeType mainAttributeType;
        private final List<SubAttributeSpec> subSpecs = new ArrayList<>();

        private record SubAttributeSpec(AttributeType type, int promoteLevel, int attributeLevel) {
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

        public Builder mainAttribute(AttributeType type) {
            this.mainAttributeType = type;
            return this;
        }

        public Builder subAttribute(AttributeType type, int promoteLevel, int attributeLevel) {
            subSpecs.add(new SubAttributeSpec(type, promoteLevel, attributeLevel));
            return this;
        }

        /**
         * Builds the relic from the accumulated configuration.
         *
         * @return the constructed relic
         * @throws IllegalStateException if type or main attribute not set
         * @throws RelicException       if the main attribute is invalid for the relic type
         */
        public Relic build() {
            if (type == null) {
                throw new IllegalStateException("Relic type must be set");
            }
            if (mainAttributeType == null) {
                throw new IllegalStateException("Main attribute must be set");
            }

            var mainAttrMap = Constant.RELIC_MAIN_ATTRIBUTES.getAttributeByStar(star).get(type);
            if (!mainAttrMap.containsKey(mainAttributeType)) {
                throw new RelicException("Main attribute '" + mainAttributeType +
                        "' is not valid for relic type " + type);
            }
            var mainAttrValue = mainAttrMap.get(mainAttributeType);
            double mainFinal = mainAttrValue.base() + mainAttrValue.bonus() * level;
            Attribute mainAttr = new Attribute(mainAttributeType, mainFinal);

            List<Attribute> subAttrs = new ArrayList<>();
            var subAttrGroup = Constant.RELIC_SUB_ATTRIBUTES.getAttributeGroupByStar(star);
            for (SubAttributeSpec spec : subSpecs) {
                var subVal = subAttrGroup.getAttributeByName(spec.type);
                double subFinal = subVal.base() * (spec.promoteLevel + 1) +
                        subVal.bonus() * spec.attributeLevel;
                subAttrs.add(new Attribute(spec.type, subFinal, spec.promoteLevel));
            }

            return Relic.create(level, star, type, mainAttr, subAttrs);
        }
    }
}
