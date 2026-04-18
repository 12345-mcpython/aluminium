package com.laosun.aluminium.models;

import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RelicSuit {
    public Relic hand;
    public Relic head;
    public Relic body;
    public Relic boot;
    public Relic ball;
    public Relic line;
    public List<Relic> total = new ArrayList<>();

    public void addToSuit(Relic relic) {
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
        if (total == null || total.isEmpty()) {
            return;
        }

        for (Relic relic : total) {
            if (relic == null) {
                continue;
            }

            Relic.Attribute mainAttr = relic.getMainAttribute();
            if (mainAttr != null) {
                relicValue.addTo(mainAttr.left(), mainAttr.right());
            }

            List<Relic.Attribute> subAttrs = relic.getSubAttributes();
            if (subAttrs != null && !subAttrs.isEmpty()) {
                for (Relic.Attribute subAttr : subAttrs) {
                    if (subAttr == null) {
                        continue;
                    }
                    relicValue.addTo(subAttr.left(), subAttr.right());
                }
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
