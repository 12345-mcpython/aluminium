package com.laosun.aluminium.models.buffs;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.AbstractBuff;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.event.HpLossEvent;

/**
 * Counter-attack (P9-5): while this is attached, losing HP hits the one who caused it back.
 *
 * <p>⚠ <b>OPEN DEFECT (measured 2026-09-24): the counter fires far more often than it should when both
 * sides wear one.</b> One real character (Himeko) basic-attacking one real enemy (Ice Edge), crit
 * disabled, ratio 0.5, {@code Battle.runCounter}'s nesting guard <i>in place</i>:
 *
 * <pre>
 *   enemy counter only        : hero lost  237.23   enemy lost    260.24
 *   both sides counter        : hero lost  237.23   enemy lost   5902.69
 * </pre>
 *
 * The hero's loss is identical, so the hero takes exactly one counter — but the enemy loses ~23x more
 * than the hero's single attack can explain, i.e. <b>this buff's reaction is being invoked repeatedly</b>
 * on the enemy's side, and the nesting guard does not bound it. The mechanism is not yet understood:
 * candidates are that {@code Battle.broadcastHpLoss} reaches this buff once per party (making the guard's
 * depth never exceed the first increment in the way expected) or that the counter's own damage is
 * broadcast back to the counter's owner.
 *
 * <p>Do not build balance or boss content on this buff until that is explained and covered by a test that
 * fails without the fix. The hero-side behaviour (one counter, and the counter does not feed the one it
 * hits) is correct and pinned by {@code BossMechanicTest}.
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

    /** Fraction of the <b>wearer's ATK</b> that the counter deals. */
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
