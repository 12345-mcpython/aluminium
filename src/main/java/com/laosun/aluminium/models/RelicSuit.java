package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.AttributeType;
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
            case Relic.Type.HAND -> hand = relic;
            case Relic.Type.HEAD -> head = relic;
            case Relic.Type.BODY -> body = relic;
            case Relic.Type.BOOT -> boot = relic;
            case Relic.Type.BALL -> ball = relic;
            case Relic.Type.LINE -> line = relic;
        }
        total.add(relic);
    }

    public void addMore(Relic... relics) {
        for (Relic relic : relics) {
            addToSuit(relic);
        }
    }

    public void calcTotalValue(Object2DoubleOpenHashMap<String> relicValue) {
        if (total.isEmpty()) {
            return;
        }

        for (Relic relic : total) {
            if (relic == null) {
                continue;
            }

            Relic.Attribute mainAttr = relic.getMainAttribute();
            if (mainAttr != null) {
                relicValue.addTo(mainAttr.getName(), mainAttr.getValue());
            }

            List<Relic.Attribute> subAttrs = relic.getSubAttributes();
            if (subAttrs != null && !subAttrs.isEmpty()) {
                for (Relic.Attribute subAttr : subAttrs) {
                    if (subAttr == null) {
                        continue;
                    }
                    relicValue.addTo(subAttr.getName(), subAttr.getValue());
                }
            }
        }
    }

    public void appendTo(AttributeBuilder attributeBuilder) {
        if (total.isEmpty()) return;

        // 聚合容器：AttributeType -> 总数值（百分比的按原始值累加）
        Object2DoubleOpenHashMap<AttributeType> aggregate = new Object2DoubleOpenHashMap<>();

        for (Relic relic : total) {
            if (relic == null) continue;
            // 处理主属性
            Relic.Attribute main = relic.getMainAttribute();
            if (main != null) addToAggregate(aggregate, main);
            // 处理副属性
            List<Relic.Attribute> subs = relic.getSubAttributes();
            if (subs != null) {
                for (Relic.Attribute sub : subs) {
                    if (sub != null) addToAggregate(aggregate, sub);
                }
            }
        }

        // 批量应用到 AttributeBuilder
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
        AttributeType type = attr.getType();
        agg.addTo(type, attr.getValue());
    }

    private void appendAttribute(AttributeBuilder attributeBuilder, Relic.Attribute attribute) {
        AttributeType at = attribute.getType();
        if (PERCENT_TO_BASE.containsKey(at)) {
            attributeBuilder.addPercent(at, attribute.getValue(), DoubleValue.Modifier.ModifierSource.RELIC);
        } else {
            if (at.isPercent) {
                attributeBuilder.addPercentPoint(at, attribute.getValue(), DoubleValue.Modifier.ModifierSource.RELIC);
            } else {
                attributeBuilder.addPure(at, attribute.getValue(), DoubleValue.Modifier.ModifierSource.RELIC);
            }
        }
    }

    @Override
    public String toString() {
        Object2DoubleOpenHashMap<String> relicValue = new Object2DoubleOpenHashMap<>();
        calcTotalValue(relicValue);
        return relicValue.toString();
    }
}
