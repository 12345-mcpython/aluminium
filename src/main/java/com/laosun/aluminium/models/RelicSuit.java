package com.laosun.aluminium.models;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.RelicSet;
import com.laosun.aluminium.data.RelicSets;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.RelicType;
import com.laosun.aluminium.utils.AttributeBuilder;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import lombok.SneakyThrows;

import java.util.ArrayList;
import java.util.List;

import static com.laosun.aluminium.Constant.PERCENT_TO_BASE;

/**
 * A collection of up to 6 relics (one per {@link RelicType}) equipped on a character.
 *
 * <p>Manages per-slot storage and provides methods to aggregate all relic attributes
 * into an {@link AttributeBuilder} or a raw value map.
 *
 * <p><b>Set bonuses are applied here.</b> On top of the main and sub attributes of the pieces, both
 * {@link #calcTotalValue(Object2DoubleOpenHashMap)} and {@link #appendTo(AttributeBuilder)} add the effect of
 * every relic set the character wears enough pieces of: a {@code require == 2} effect needs two relics
 * sharing a {@link Relic#setId}, a {@code require == 4} effect needs four. The numbers come from
 * {@link Constant#RELIC_SETS} and each property is resolved with
 * {@link AttributeType#fromGameProperty(String)} — a property name the engine cannot map throws with the
 * name instead of being skipped.
 *
 * <p>⚠ <b>What is still not applied</b>: a 4-piece bonus that is an <em>ability</em> rather than stats
 * ("at the start of the battle, immediately regenerates 1 Skill Point") is not a stat-sheet effect and
 * this class contributes nothing for it. Such abilities are executed by <b>trigger rules</b>
 * ({@code resources/relic_sets/<setId>.json}, loaded by
 * {@link com.laosun.aluminium.data.RelicTriggerTables} and attached at the assembly point), not here —
 * see that class. An ability whose text the current op vocabulary cannot express is registered as an
 * explicit gap ({@code relic_sets/_unmodelled.json}, reported as {@code F-10} in {@code DOC_VS_CODE.md})
 * rather than being silently forgotten; {@link RelicSet.Effect#hasAbility()} and
 * {@code RelicSetTest}'s "every effect is either stats or a named ability" invariant keep the split
 * visible.
 */
public final class RelicSuit implements Cloneable {
    /**
     * All relics in this suit (for iteration).
     */
    private final List<Relic> total = new ArrayList<>();
    /**
     * Hand-slot relic.
     */
    public Relic hand;
    /**
     * Head-slot relic.
     */
    public Relic head;
    /**
     * Body-slot relic.
     */
    public Relic body;
    /**
     * Boot-slot relic.
     */
    public Relic boot;
    /**
     * Ball (planar sphere) relic.
     */
    public Relic ball;
    /**
     * Line (link rope) relic.
     */
    public Relic line;

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
     * Computes the total of everything the suit contributes: the main and sub attributes of every relic
     * plus the bonuses of every relic set worn in sufficient numbers.
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

        addSetBonusValues(relicValue);
    }

    /**
     * Aggregates all relic attributes and set bonuses and appends them as modifiers to the builder.
     *
     * <p>The percent/flat decision is the same for an affix and for a set bonus: a
     * {@link Constant#PERCENT_TO_BASE} attribute (health/attack/defence/speed percent) is added as a
     * percentage of its base attribute, any other {@link AttributeType#isPercent} attribute is a
     * percentage-point value, and everything else is flat.
     *
     * @param attributeBuilder the builder to append modifications to
     */
    public void appendTo(AttributeBuilder attributeBuilder) {
        if (total.isEmpty()) return;

        Object2DoubleOpenHashMap<AttributeType> aggregate = new Object2DoubleOpenHashMap<>();
        calcTotalValue(aggregate);

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

    /**
     * The set bonuses the suit currently satisfies.
     *
     * <p>Derived from the equipped pieces on every call (there is no cached state to invalidate when a relic
     * is swapped). Besides being the implementation of the bonus maths, this is how a caller can ask "which
     * bonuses are live" — including the ability-only effects listed in
     * {@link RelicSet.Effect#hasAbility()}, which this class can select but not execute.
     *
     * <p>⚠ The order of the returned list is <b>unspecified</b> across different sets (the piece counts come
     * out of a hash map); within one set the effects keep the data's order, so a 2-piece effect precedes its
     * 4-piece effect. Callers that need a stable order should not rely on it.
     *
     * @return the effects whose {@code require} the worn piece count meets; empty when no set is complete
     */
    public List<RelicSet.Effect> activeEffects() {
        List<RelicSet.Effect> active = new ArrayList<>();
        for (Int2IntMap.Entry worn : piecesPerSet().int2IntEntrySet()) {
            for (RelicSet.Effect effect : RelicSets.require(worn.getIntKey()).effects()) {
                if (worn.getIntValue() >= effect.require()) {
                    active.add(effect);
                }
            }
        }
        return active;
    }

    /**
     * How many pieces of each set are worn, keyed by set id. Relics that belong to no set
     * ({@link Constant#RELIC_SET_NONE}) are left out: they can never complete a set.
     *
     * <p>Public because the count is not only the bonus maths' private business: the assembly point
     * ({@code CharacterFactory}) asks the same question to decide which relic sets' <b>trigger
     * rules</b> a character carries ({@code RelicTriggerTables}). Deriving it twice -- once here and
     * once there -- would be two definitions of "four pieces of a set" that could drift apart.
     *
     * <p>A fresh map on every call (there is no cached state to invalidate when a relic is swapped),
     * so the caller may mutate it freely.
     *
     * @return set id → worn piece count; empty for an empty suit
     */
    public Int2IntOpenHashMap piecesPerSet() {
        Int2IntOpenHashMap piecesPerSet = new Int2IntOpenHashMap();
        for (Relic relic : total) {
            if (relic == null || relic.setId == Constant.RELIC_SET_NONE) {
                continue;
            }
            piecesPerSet.addTo(relic.setId, 1);
        }
        return piecesPerSet;
    }

    /**
     * Adds the value of every satisfied set bonus to the map.
     *
     * <p>An unknown set id is an error rather than "no bonus": it means the relic was built with an id that
     * does not exist in the data, and quietly ignoring it would make the character a few percent weaker with
     * nothing to show for it.
     */
    private void addSetBonusValues(Object2DoubleOpenHashMap<AttributeType> relicValue) {
        for (Int2IntMap.Entry worn : piecesPerSet().int2IntEntrySet()) {
            RelicSet set = RelicSets.require(worn.getIntKey());
            for (RelicSet.Effect effect : set.effects()) {
                if (worn.getIntValue() < effect.require()) {
                    continue;
                }
                for (RelicSet.Property property : effect.properties()) {
                    relicValue.addTo(AttributeType.fromGameProperty(property.type()), property.value());
                }
            }
        }
    }

    @Override
    public String toString() {
        Object2DoubleOpenHashMap<AttributeType> relicValue = new Object2DoubleOpenHashMap<>();
        calcTotalValue(relicValue);
        return relicValue.toString();
    }

    @SneakyThrows
    @Override
    public RelicSuit clone() {
        RelicSuit cp = new RelicSuit();
        if (hand != null) cp.addToSuit(hand.clone());
        if (head != null) cp.addToSuit(head.clone());
        if (body != null) cp.addToSuit(body.clone());
        if (boot != null) cp.addToSuit(boot.clone());
        if (ball != null) cp.addToSuit(ball.clone());
        if (line != null) cp.addToSuit(line.clone());
        return cp;
    }
}
