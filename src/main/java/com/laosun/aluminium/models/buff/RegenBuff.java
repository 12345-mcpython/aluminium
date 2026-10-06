package com.laosun.aluminium.models.buff;

import com.laosun.aluminium.models.CanHit;
import lombok.Getter;

/**
 * A <b>heal over time</b>: "目标每回合开始时为其回复等同于娜塔莎.20% 生命上限 + 192 的生命值，持续 2 回合".
 *
 * <p><b>Why it is the twin of {@link DotBuff}, down to the placement of the settle.</b> The engine already had the
 * damage half - a DOT is an ordinary early buff, and the <i>settlement</i> happens in
 * {@link com.laosun.aluminium.Battle#tickDots(CanHit)} because that is where the damage zones live
 * ({@code AbstractBuff.tickEffect} is handed no {@code Battle}, deliberately). A heal needs the same thing for the same
 * reason ({@code Battle.heal} reads the applier's {@code OUTGOING_HEALING_BOOST}), so this class holds the numbers and
 * the duration and nothing else, and {@code Battle.tickRegens(CanHit)} settles it - immediately after the DOT pass.
 *
 * <p>Note: <b>That ordering is the whole reason a HOT could not simply be a {@code TURN_START} rule plus a state.</b> The
 * duration is counted down by the buff manager's <i>early</i> tick, which runs <b>after</b> these settle passes and
 * before {@code TURN_START}: a {@code turns: 2} buff therefore settles on each of the next <b>two</b> turns, while a
 * {@code turns: 2} state read from a {@code TURN_START} rule is already gone by the second one. The naive version heals
 * once where the document says twice, and "fixing" it with {@code turns: 3} makes the count come out right while
 * misstating the duration - two different ways to be wrong without a symptom.
 *
 * <p>Note: Re-applying <b>refreshes</b> ({@link #isSameKind} is true, unlike a DOT): "同时目标每回合开始时…" describes one
 * regeneration that a second cast restarts, not a pile of them.
 */
public class RegenBuff extends AbstractBuff {

    /** Who applied it - the healer whose {@code OUTGOING_HEALING_BOOST} and whose stats the amount was derived from. */
    @Getter
    private final CanHit source;

    /** The flat amount settled each turn (already derived when the rule fired, and held). */
    @Getter
    private final double baseHeal;

    /**
     * @param source   the applier
     * @param name     the document's name for the effect ({@code "持续治疗"}), which is what {@code EXTEND_BUFF} and
     *                 {@code REMOVE_STATE} reach it by
     * @param baseHeal what to restore on each of the owner's turns
     * @param turns    how many of the owner's turns it lasts (each one settles)
     */
    public RegenBuff(CanHit source, String name, double baseHeal, int turns) {
        super(turns, true);                 // early, exactly like a DOT: it settles before the duration ticks
        this.source = source;
        this.baseHeal = baseHeal;
        setBuffName(name);
    }

    /** Note: Only the duration: the healing is settled by {@code Battle.tickRegens}, where the healer and the zones are. */
    @Override
    public void tickEffect(CanHit owner) {
        decreaseDuration();
    }

    /** A regeneration never stops its owner from acting: it is a benefit, not a state. */
    @Override
    public boolean canAct() {
        return true;
    }

    @Override
    public void applyEffect(CanHit owner) {
        // deliberately nothing: a regeneration changes nothing until its turn comes
    }

    @Override
    public void removeBuff(CanHit owner) {
        // deliberately nothing: the healing already happened, turn by turn
    }

    /** A second cast restarts the same regeneration instead of adding another one. */
    @Override
    public boolean isSameKind(AbstractBuff other) {
        return other instanceof RegenBuff;
    }
}
