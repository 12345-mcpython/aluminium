package com.laosun.aluminium.models.buff;

import com.laosun.aluminium.models.CanHit;
import lombok.Getter;

/**
 * <b>"This attack's toughness reduction is higher"</b>: "使本次攻击的削韧值提高 100%" (120 Yukong's (驭空) talent),
 * "强化普攻的前 2 段攻击对指定敌方单体的削韧值提高 50%" (131 Rappa (乱破)).
 *
 * <p><b>Why a buff rather than a field on the damage instance.</b> The reduction is not part of the damage: it is a second
 * number that travels beside it - {@code SkillExecutor} settles the damage first ({@code applyDamage}, where
 * {@code DEALING_DAMAGE} fires) and only then reduces toughness ({@code applyStanceDamage}) - and it is read at exactly
 * <b>one</b> place. A timed buff on the attacker is what the documents describe ("本次攻击"/"前 2 段" are the attacker
 * speaking about their own attack) and it needs no lifetime vocabulary the engine does not already have.
 *
 * <p>Note: <b>Where it must NOT live</b>: inside {@code Battle.reduceToughness}. That method receives "the nominal reduction of
 * this instance" and is also called by enemy skills and by the demo script; folding a modifier into it would silently
 * change both.
 */
public class ToughnessBoostBuff extends AbstractBuff {

    /** How much extra reduction, as a fraction ({@code 1.0} = "提高 100%"). */
    @Getter
    private final double percent;

    /**
     * @param percent how much extra reduction ({@code 1.0} = "提高 100%")
     * @param turns   how many of the owner's turns it lasts
     */
    public ToughnessBoostBuff(double percent, int turns) {
        super(turns, false);
        this.percent = percent;
    }

    @Override
    public void applyEffect(CanHit owner) {
        // nothing on its own: the multiplier is read where a reduction is about to happen
    }

    @Override
    public void removeBuff(CanHit owner) {
        // nothing: the reduction it influenced is already settled
    }

    @Override
    public void tickEffect(CanHit owner) {
        decreaseDuration();
    }

    @Override
    public boolean canAct() {
        return true;
    }

    /** Two of these on one attacker add up ("提高 50%" on top of "提高 100%"), so they are never "the same buff". */
    @Override
    public boolean isSameKind(AbstractBuff other) {
        return false;
    }
}
