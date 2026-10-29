package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

/**
 * One <b>predicate</b>: a structured condition that can be composed, named and reused, where {@code when} only offers a
 * flat list of ANDed condition strings.
 *
 * <p><b>Why this exists at all.</b> The condition DSL has three limits that a data rule cannot work around:
 * <ul>
 *   <li><b>no OR</b> - a rule's {@code when} is an implicit AND, so "an ally, or the wearer himself" needs two rules
 *       that then have to agree about everything else;</li>
 *   <li><b>no nesting</b> - "A and (B or C)" is not expressible, and listing {@code B or C} as two conditions makes it
 *       "A and B and C";</li>
 *   <li><b>no reuse</b> - the same four-condition guard is copied into every rule that needs it, and a correction has
 *       to find every copy.</li>
 * </ul>
 *
 * <p>This bean is the data form of a composed condition: a {@code type} and, depending on it, either a {@code spell}
 * (one of the existing condition strings, parsed by the same parser {@code when} uses), a list of {@code terms}
 * ({@code all_of} / {@code any_of}), one {@code term} ({@code not}), or a {@code ref} to a named predicate in
 * {@code game/predicates.json}.
 *
 * <p>⚠ <b>A predicate is not a number provider.</b> That one produces <i>which</i> number; this one answers
 * <i>whether</i>. They meet at {@code when_predicates} (this) and {@code percent_from} / {@code amount_from} (that).
 */
@Getter
@ToString
public final class PredicateSpec {

    /** {@code all_of} / {@code any_of} / {@code not} / {@code condition} / {@code ref}. */
    @SerializedName("type")
    private String type;

    /** {@code all_of} / {@code any_of}: the predicates to combine, in the order they are stated. */
    @SerializedName("terms")
    private List<PredicateSpec> terms;

    /** {@code not}: the predicate to invert. */
    @SerializedName("term")
    private PredicateSpec term;

    /** {@code condition}: one condition string, exactly as {@code when} spells it. */
    @SerializedName("spell")
    private String spell;

    /** {@code ref}: a named predicate from {@code game/predicates.json}. */
    @SerializedName("ref")
    private String ref;
}
