package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

/**
 * One trigger rule from {@code resources/characters/<cid>.json} (P8-7):
 * "on &lt;event&gt;, when &lt;conditions&gt;, do &lt;effects&gt;".
 *
 * <p>This is the unit that replaces a `XxxTalent.java` class. The engine only interprets it --
 * it never learns which character it belongs to, which is the whole point of the P8-0 three-way
 * split.
 *
 * <p>⚠ Read the conditions as follows: the owning character is called <b>self</b> in
 * {@link #when}; the unit that caused the event is <b>actor</b>. So
 * {@code "when": ["actor != self"]} means "someone else on my side acted" -- which is how
 * Robin's "after an ally attacks" talent is expressed.
 */
@Getter
@ToString
@NoArgsConstructor
public class TriggerSpec {

    /**
     * The event name, matching {@link com.laosun.aluminium.enums.TriggerEvent}.
     */
    @SerializedName("on")
    private String on;

    /**
     * Conditions that must all hold, in the small DSL understood by
     * {@link com.laosun.aluminium.models.TriggerTable}. An empty or absent list means
     * "always".
     */
    @SerializedName("when")
    private List<String> when;

    /**
     * The effects to run, in order.
     */
    @SerializedName("do")
    private List<EffectSpec> doEffects;

    /**
     * How many of the <b>owner's own turns</b> must pass between two firings — the game's
     * 「该效果每回合只能触发1次」 is {@code 1}. Absent = no limit.
     *
     * <p>Counted in the owner's turns, not in events: the counter is decremented when the turn of the
     * character whose table this is begins. That is why a rule reacting to other people's actions still
     * means "once per <i>my</i> turn" — the limit belongs to the rule's owner, not to whoever happened
     * to trigger it. A value below 1 is rejected at load time.
     */
    @SerializedName("cooldown")
    private Integer cooldown;

    /**
     * {@code true} = the rule fires at most once per battle (「单场战斗中只能触发1次」).
     *
     * <p>Deliberately not the same field as {@link #cooldown}: a cooldown comes back after a few turns,
     * this never does. Stating both at once is refused at load time — the author has to mean one of
     * them, and "once per battle, but also every 2 turns" has no reading that is not a mistake.
     */
    @SerializedName("once_per_battle")
    private Boolean oncePerBattle;

    /**
     * Where the rule came from (trace id, talent name, character-doc reference).
     *
     * <p>Not used by the engine at all: it exists so that anyone reading the JSON -- or a failing
     * test -- can trace a number back to its source document. The project's rule is that every
     * effect states its origin.
     */
    @SerializedName("source")
    private String source;

    /**
     * Free-form note, typically "why this number" or a {@code TODO data} marker when a value is a
     * placeholder rather than a measured one.
     */
    @SerializedName("note")
    private String note;
}
