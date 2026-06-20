package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.RelicType;
import com.laosun.aluminium.utils.AttributeBuilder;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;

import java.util.ArrayList;
import java.util.List;

import static com.laosun.aluminium.Constant.PERCENT_TO_BASE;

public final class RelicSuit {
    public Relic hand;
    public Relic head;
    public Relic body;
    public Relic boot;
    public Relic ball;
    public Relic line;
    public final List<Relic> total = new ArrayList<>();

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

    public void addMore(Relic... relics) {
        for (Relic relic : relics) {
            addToSuit(relic);
        }
    }

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
