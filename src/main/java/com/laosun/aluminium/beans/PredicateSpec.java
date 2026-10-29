package com.laosun.aluminium.beans;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

/**
 * One <b>predicate</b>: a structured condition that can be composed, named and reused, where {@code when} only offers a
 * flat list of ANDed condition strings.
 *
 * <p><b>Two kinds, one shape.</b> A combinator states {@code terms} / {@code term}; an <b>entity predicate</b> states
 * {@code entity} (which unit is being asked about) and {@code json} - a map of <b>sub-predicate to value</b>, the same
 * shape Minecraft's entity predicate has:
 *
 * <pre>{@code
 * { "type": "entity", "entity": "target",
 *   "json": { "is_ally": true, "hp_pct": { "max": 0.5 }, "stacks": { "name": "充能", "min": 3 } } }
 * }</pre>
 *
 * <p>Why a map rather than one field per sub-predicate: the set of questions grows, and a map keeps one closed
 * vocabulary in one place ({@code EntityPredicates}) instead of a bean field per question. Every key and every value
 * shape is checked when the table loads, so a misspelled sub-predicate is a load failure rather than a rule that never
 * fires.
 *
 * <p>⚠ <b>Not a number provider.</b> That one produces <i>which</i> number; this one answers <i>whether</i>.
 */
@Getter
@ToString
public final class PredicateSpec {

    /** {@code all_of} / {@code any_of} / {@code not} / {@code entity} / {@code ref}. */
    @SerializedName("type")
    private String type;

    /** {@code all_of} / {@code any_of}: the predicates to combine, in the order they are stated. */
    @SerializedName("terms")
    private List<PredicateSpec> terms;

    /** {@code not}: the predicate to invert. */
    @SerializedName("term")
    private PredicateSpec term;

    /** {@code ref}: a named predicate from {@code game/data/predicates.json}. */
    @SerializedName("ref")
    private String ref;

    /** {@code entity}: which unit - {@code self} / {@code actor} / {@code target}. */
    @SerializedName("entity")
    private String entity;

    /**
     * {@code entity}: the sub-predicates, as {@code "<sub-predicate>": <value>}. The vocabulary and the value shapes are
     * {@code EntityPredicates}' (one place, checked at load).
     */
    @SerializedName("json")
    private JsonObject json;
}
