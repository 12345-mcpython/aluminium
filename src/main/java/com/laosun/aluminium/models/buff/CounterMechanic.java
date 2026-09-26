package com.laosun.aluminium.models.buff;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.event.HpLossEvent;

/**
 * Counter-attack (P9-5): while this is attached, losing HP hits the one who caused it back.
 *
 * <p><b>Retraction (2026-09-24).</b> A previous revision of this Javadoc carried an "OPEN DEFECT" claiming
 * the reaction fired far too often when both sides wore one, based on a measurement showing an enemy lose
 * 5902 where one attack explained 260. <b>There was no defect: that measurement was taken after an extra
 * attack</b>, so it quietly included a second exchange. Re-measured with a trace, both sides wearing a
 * counter and exactly one attack produce one bounded exchange — the enemy takes the attack (260.24), the
 * hero takes the counter (237.23), and the answering counter is refused.
 *
 * <p>The lesson outlives the retraction: the test that "failed to verify the guard" watched the
 * <b>hero's</b> HP, which is identical whether or not the chain is bounded, so it could not see the
 * difference. What moves is the <b>other side's</b> HP. When one side of a comparison refuses to move,
 * suspect the choice of observable before concluding the engine is wrong.
 *
 * <p><b>Which hook, and why it is not the obvious one.</b> {@code DamageEvent.onDamage} runs
 * <i>before</i> a hit is settled and exists to inject damage zones into that hit — a counter needs the
 * moment <i>after</i> the hit landed, which is {@link HpLossEvent#onHpLoss}. That hook also hands over
 * {@code source}, the one who caused the instance, which is exactly the counter's target ("the entity
 * that cast the skill", not necessarily the character itself).
 *
 * <p><b>Generic on purpose.</b> Nothing here is enemy-specific: it is introduced for boss mechanics
 * (P9-5) but a character could carry it too, and it deliberately does not duplicate the follow-up shape
 * that {@code TriggerInterpreter}'s {@code DAMAGE} op already expresses — both end up in
 * {@link Battle#applyAdditionalDamage}, so a counter is {@code ADDITIONAL} damage that
 * <b>does not count as an attack</b> (the victim gains no energy, no toughness is reduced).
 *
 * <p><b>It installs no attribute</b>, like the injection-type buffs: {@code applyEffect} and
 * {@code removeBuff} are empty because the buff only reacts. It does need to be attached, which is how
 * {@code BuffManager.onHpLoss} reaches it.
 *
 * <p>⚠ <b>Recursion</b> is handled by {@link Battle#runCounter}: two units wearing a counter would
 * otherwise hit each other forever, and this path is not covered by
 * {@code Battle}'s trigger-depth guard. See that method for why.
 */
public class CounterMechanic extends AbstractBuff implements HpLossEvent {

    private final DamageElement element;

    /**
     * Fraction of the <b>wearer's ATK</b> that the counter deals.
     */
    private final double ratio;

    /**
     * @param duration turns the counter stays attached
     * @param element  damage element of the counter; {@code null} = physical (a counter with no element
     *                 would silently skip the element boost zone)
     * @param ratio    counter damage = wearer's ATK × this
     */
    public CounterMechanic(int duration, DamageElement element, double ratio) {
        super(duration, false);
        this.element = element == null ? DamageElement.PHYSICAL : element;
        this.ratio = ratio;
    }

    public double getRatio() {
        return ratio;
    }

    public DamageElement getElement() {
        return element;
    }

    @Override
    public boolean canAct() {
        return true;
    }

    @Override
    public void applyEffect(CanHit target) {
        // No attribute to install: this buff only reacts. It still has to be attached, which is how
        // BuffManager.onHpLoss finds it.
    }

    @Override
    public void removeBuff(CanHit target) {
        // Nothing to take off, for the same reason.
    }

    @Override
    public void tickEffect(CanHit target) {
        decreaseDuration();
    }

    /**
     * Hits the attacker back when the wearer really loses HP.
     *
     * <p>Not fired when there is nobody to counter ({@code source == null}, e.g. a DOT whose applier has
     * died), when the wearer hit themselves, or once either side is down — a corpse does not counter, and
     * hitting a corpse is pointless.
     *
     * <p>{@code target} is the wearer (this is its own buff), so it is read from the parameter rather than
     * from {@link #owner}, which is only written at attach time.
     */
    @Override
    public void onHpLoss(Battle battle, CanHit target, double before, double after,
                         CanHit source, double amount) {
        // ⚠ Only react to MY OWN loss. Battle.broadcastHpLoss dispatches to both parties' buffs, so
        // without this check a counter attached to A would also fire when B loses HP -- and it would
        // counter on A's behalf using the wrong attacker/defender pair. Caught by
        // BossMechanicTest.aMutualPairOfCountersDoesNotEscalate, which saw more than one counter's worth
        // of damage because of it.
        if (target != owner) {
            return;
        }
        if (source == null || source == target || target.isDeath() || source.isDeath()) {
            return;
        }
        double base = target.getAttribute(AttributeType.ATTACK).get() * ratio;
        battle.runCounter(() -> battle.applyAdditionalDamage(target, source, element, base));
    }

    @Override
    public String toString() {
        return "CounterMechanic[" + element + " " + (ratio * 100) + "% of ATK, "
                + remainingDuration + "t]";
    }
}
