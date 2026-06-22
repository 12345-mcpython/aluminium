package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.RelicType;
import com.laosun.aluminium.utils.AttributeBuilder;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;

import java.util.ArrayList;
import java.util.List;

import static com.laosun.aluminium.Constant.PERCENT_TO_BASE;

/**
 * A collection of up to 6 relics (one per {@link RelicType}) equipped on a character.
 *
 * <p>Manages per-slot storage and provides methods to aggregate all relic attributes
 * into an {@link AttributeBuilder} or a raw value map.
 */
public final class RelicSuit {
    /** Hand-slot relic. */
    public Relic hand;
    /** Head-slot relic. */
    public Relic head;
    /** Body-slot relic. */
    public Relic body;
    /** Boot-slot relic. */
    public Relic boot;
    /** Ball (planar sphere) relic. */
    public Relic ball;
    /** Line (link rope) relic. */
    public Relic line;
    /** All relics in this suit (for iteration). */
    public final List<Relic> total = new ArrayList<>();

    /**
     * Adds a relic to the appropriate slot based on its type.
     *
     * @param relic the relic to add; null is silently ignored
     */
    public void addToSuit(Relic relic) {
        if (relic == null) return;
        switch (relic.relicType) {
            case RelicType.HAND -> hand = relic;
            case RelicType.HEAD -> head = relic;
            case RelicType.BODY -> body = relic;
            case RelicType.BOOT -> boot = relic;
            case RelicType.BALL -> ball = relic;
            case RelicType.LINE -> line = relic;
        }
        total.add(relic);
    }

    /**
     * Adds multiple relics at once.
     *
     * @param relics the relics to add
     */
    public void addMore(Relic... relics) {
        for (Relic relic : relics) {
            addToSuit(relic);
        }
    }

    /**
     * Computes the total sum of all main and sub-attribute values across all relics.
     *
     * @param relicValue the map to accumulate values into (modified in-place)
     */
    public void calcTotalValue(Object2DoubleOpenHashMap<AttributeType> relicValue) {
        if (total.isEmpty()) {
            return;
        }

        for (Relic relic : total) {
            if (relic == null) {
                continue;
            }

            Relic.Attribute mainAttr = relic.getMainAttribute();
            if (mainAttr != null) {
                relicValue.addTo(mainAttr.type(), mainAttr.value());
            }

            List<Relic.Attribute> subAttrs = relic.getSubAttributes();
            if (subAttrs != null && !subAttrs.isEmpty()) {
                for (Relic.Attribute subAttr : subAttrs) {
                    if (subAttr == null) {
                        continue;
                    }
                    relicValue.addTo(subAttr.type(), subAttr.value());
                }
            }
        }
    }

    /**
     * Aggregates all relic attributes and appends them as modifiers to the builder.
     *
     * @param attributeBuilder the builder to append modifications to
     */
    public void appendTo(AttributeBuilder attributeBuilder) {
        if (total.isEmpty()) return;

        Object2DoubleOpenHashMap<AttributeType> aggregate = new Object2DoubleOpenHashMap<>();

        for (Relic relic : total) {
            if (relic == null) continue;
            Relic.Attribute main = relic.getMainAttribute();
            if (main != null) addToAggregate(aggregate, main);
            List<Relic.Attribute> subs = relic.getSubAttributes();
            if (subs != null) {
                for (Relic.Attribute sub : subs) {
                    if (sub != null) addToAggregate(aggregate, sub);
                }
            }
        }

        for (var entry : aggregate.object2DoubleEntrySet()) {
            AttributeType type = entry.getKey();
            double value = entry.getDoubleValue();
            if (PERCENT_TO_BASE.containsKey(type)) {
                attributeBuilder.addPercent(type, value, DoubleValue.Modifier.ModifierSource.RELIC);
            } else {
                if (type.isPercent) {
                    attributeBuilder.addPercentPoint(type, value, DoubleValue.Modifier.ModifierSource.RELIC);
                } else {
                    attributeBuilder.addPure(type, value, DoubleValue.Modifier.ModifierSource.RELIC);
                }
            }
        }
    }

    private void addToAggregate(Object2DoubleOpenHashMap<AttributeType> agg, Relic.Attribute attr) {
        AttributeType type = attr.type();
        agg.addTo(type, attr.value());
    }

    private void appendAttribute(AttributeBuilder attributeBuilder, Relic.Attribute attribute) {
        AttributeType at = attribute.type();
        if (PERCENT_TO_BASE.containsKey(at)) {
            attributeBuilder.addPercent(at, attribute.value(), DoubleValue.Modifier.ModifierSource.RELIC);
        } else {
            if (at.isPercent) {
                attributeBuilder.addPercentPoint(at, attribute.value(), DoubleValue.Modifier.ModifierSource.RELIC);
            } else {
                attributeBuilder.addPure(at, attribute.value(), DoubleValue.Modifier.ModifierSource.RELIC);
            }
        }
    }

    @Override
    public String toString() {
        Object2DoubleOpenHashMap<AttributeType> relicValue = new Object2DoubleOpenHashMap<>();
        calcTotalValue(relicValue);
        return relicValue.toString();
    }
}
