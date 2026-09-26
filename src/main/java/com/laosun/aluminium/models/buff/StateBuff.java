package com.laosun.aluminium.models.buff;

import com.laosun.aluminium.models.CanHit;
import lombok.Getter;

/**
 * A named state (【协奏】/【转魄】/【增幅】/【失重】…) that lasts a number of turns and otherwise does nothing.
 *
 * <p><b>Why this class exists.</b> The rule texts of this game constantly say "while in state X"
 * (「处于【协奏】状态时」/「【转魄】状态下」/「【失重】状态下的目标」), and the trigger table could not ask
 * about any of them — so a mechanic that is otherwise pure data needed a Java class per character, which is
 * exactly what P8-0/P8-7 exist to prevent.
 *
 * <p><b>A state is not a new kind of thing here: it is an ordinary buff that carries a name.</b> That is the
 * lesson the DOT migration already taught (a DOT became an ordinary buff and thereby gained duration,
 * stacking, dispel and {@code hasBuff} visibility for free). A state inherits all of it, and the condition
 * side only has to learn one new question: {@code self has_state 协奏}.
 *
 * <p><b>Inert on purpose.</b> {@link #applyEffect} and {@link #removeBuff} do nothing: a state is a
 * <i>fact about a unit</i> that rules read, not a modifier. Whatever a state is supposed to change is
 * expressed as its own effect conditioned on {@code has_state}, which keeps "what the state is" and "what
 * the state does" in separate places and avoids modelling one mechanic twice.
 *
 * <p><b>Identity is the name, not the class.</b> {@link #isSameKind} compares state names. The default
 * "same class = same buff" ({@link AbstractBuff#isSameKind}) would make applying 【协奏】 evict 【转魄】 —
 * two unrelated states of the same character, one of them silently gone (the same trap registered as L-14).
 * Re-applying the <b>same</b> state refreshes its duration, which is what the engine's ordinary rule means by
 * "refreshes" and what the game text means by it too.
 *
 * <p><b>Duration ticks late</b> ({@code early = false}, i.e. in {@code afterMove}): a state applied during a
 * turn must not be counted down at the start of that same turn, so "lasts 2 turns" is two of the owner's
 * turns — the same convention {@link StatModifierBuff} uses for buffs granted by an ally.
 */
@Getter
public class StateBuff extends AbstractBuff {

    /**
     * The state's name, exactly as the data spells it (e.g. {@code "协奏"}). Never blank.
     */
    private final String state;

    /**
     * A state that lasts {@code turns} of its owner's turns.
     *
     * @param state the state name as the data spells it (trimmed; must not be blank)
     * @param turns how many of the owner's turns it lasts, at least 1
     * @throws IllegalArgumentException when the name is blank or the turn count is below 1
     */
    public StateBuff(String state, int turns) {
        this(state, turns, false);
    }

    /**
     * @param permanent {@code true} = the state lasts until the battle ends and is never counted down
     *                  ({@link AbstractBuff#isPermanent()}); {@code turns} is then only a placeholder
     */
    public StateBuff(String state, int turns, boolean permanent) {
        super(turns, false, permanent);
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("StateBuff needs a state name");
        }
        // The same guard DotBuff learned the hard way (L-12): a non-positive duration is not a zero-length
        // state, it is a state that exists for the whole turn it was applied in and disappears at its end.
        if (!permanent && turns < 1) {
            throw new IllegalArgumentException(
                    "StateBuff '" + state + "' must last at least 1 turn, got " + turns
                            + " (use permanent: true for a state with no turn limit)");
        }
        this.state = state.trim();
    }

    /**
     * A state never stops its owner acting — otherwise a state wearing off would freeze its owner for a turn.
     *
     * <p>Control states (眩晕/冻结/纠缠…) are <b>not</b> states in this sense: they are their own buffs that
     * return {@code false} here, and they are applied by their own mechanics, not by {@code APPLY_BUFF}.
     */
    @Override
    public boolean canAct() {
        return true;
    }

    /**
     * A named state is <b>not</b> classified as a debuff, and that is a decision rather than an oversight: its
     * side is decided by the rule that applied it ({@code APPLY_BUFF} carries a name and a duration, not a sign),
     * and 【协奏】 and 【失重】 are opposite kinds of thing under one mechanism. The safe direction is "not a
     * debuff" — a state is never removed by 解除负面效果 by accident — and when content needs a dispellable
     * state, {@code APPLY_BUFF} gains the side instead of this class guessing.
     */
    @Override
    public boolean isDebuff() {
        return false;
    }

    /**
     * Nothing to attach: a state changes no attribute (see the class javadoc).
     */
    @Override
    public void applyEffect(CanHit target) {
    }

    /**
     * Nothing to take off, for the same reason as {@link #applyEffect}.
     */
    @Override
    public void removeBuff(CanHit target) {
    }

    /**
     * The whole of a state's own behaviour: burn one turn off.
     */
    @Override
    public void tickEffect(CanHit target) {
        decreaseDuration();
    }

    /**
     * Two states are the same kind only when they carry the same <b>name</b> (see the class javadoc).
     *
     * @param other the buff being applied
     * @return {@code true} only for a state with an equal name, so unrelated states coexist and the same
     *         state refreshes instead of stacking
     */
    @Override
    public boolean isSameKind(AbstractBuff other) {
        return other instanceof StateBuff otherState && state.equals(otherState.state);
    }
}
