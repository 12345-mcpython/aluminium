package com.laosun.aluminium.models.buff;

import com.laosun.aluminium.models.entity.CanHit;
import com.laosun.aluminium.models.damage.BattleDamage;

/**
 * Absorbs part of an incoming hit by <b>spending another unit's resource</b> - the shape of 1503 Pearl (真珠)'s talent:
 * 「真珠可使用【好活当赏】作为抵御值，每点【好活当赏】等同于 #5 点抵御值，我方目标受到伤害时，真珠可消耗抵御值为我方目标
 * 抵挡 #2% 的伤害」.
 *
 * <p>Why this is its own buff rather than a {@link ReductionBuff} with a number. A reduction is "this hit is smaller",
 * with no cost and no floor; this is "this hit is smaller <b>because a counter paid for it</b>", so the amount blocked
 * depends on how many points the payer actually holds, and the payer is frequently <b>not</b> the unit being hit. Three
 * things therefore have to travel together and cannot be expressed as a plain percentage:
 *
 * <ul>
 *   <li>{@code unitAmount} - how much damage ONE point is worth (the corpus's 200);</li>
 *   <li>{@code resource} - <b>whose</b> counter pays, read from {@code bearer} rather than from {@link #owner};</li>
 *   <li>{@code percent} - the share of the hit that may be blocked at all (the corpus's 60%), which caps the spend
 *       independently of the counter's size.</li>
 * </ul>
 *
 * <p>⚠ <b>Partial absorption is the documented behaviour</b> and the reason the cap is a {@code min}: when the counter
 * cannot cover the share, it blocks what it can ("按剩余点数按比例抵挡" - the author's ruling) rather than failing
 * outright. Because a point is worth 200 damage and the spend is deducted per whole point, a counter of 1 blocks up to
 * 200 and can leave a remainder smaller than one point's worth.
 *
 * <p>The hook is {@link com.laosun.aluminium.models.damage.BattleDamage#absorbsInto}: absorption reads the SETTLED amount (after every
 * damage zone, which is what "受到伤害时" means) and then lowers it, before the lethal check and before any HP moves.
 */
public class AbsorbBuff extends AbstractBuff {

    /**
     * Which counter pays. Read from the PAYER, not from this buff's holder.
     */
    private final String resource;

    /**
     * How much damage one point of {@link #resource} is worth (200 in the corpus).
     */
    private final double unitAmount;

    /**
     * The share of the hit that may be blocked at all (0.6 = 60%).
     */
    private final double percent;

    /**
     * The unit whose counter pays - normally the rule's owner, i.e. Pearl, while the buff sits on an ally.
     */
    private final CanHit payer;

    public AbsorbBuff(int duration, boolean permanent, String resource, double unitAmount, double percent,
                      CanHit payer) {
        super(duration, false, permanent);
        if (resource == null || resource.isBlank()) {
            throw new IllegalArgumentException("AbsorbBuff needs the name of the resource that pays for it");
        }
        if (!(unitAmount > 0)) {
            throw new IllegalArgumentException("AbsorbBuff needs a positive unit_amount (damage per point), got "
                    + unitAmount);
        }
        if (!(percent > 0 && percent <= 1)) {
            throw new IllegalArgumentException("AbsorbBuff needs a share in (0, 1], got " + percent);
        }
        if (payer == null) {
            throw new IllegalArgumentException("AbsorbBuff needs the unit whose counter pays (see `payer`)");
        }
        this.resource = resource;
        this.unitAmount = unitAmount;
        this.percent = percent;
        this.payer = payer;
    }

    public String getResource() {
        return resource;
    }

    public double getUnitAmount() {
        return unitAmount;
    }

    public double getPercent() {
        return percent;
    }

    public CanHit getPayer() {
        return payer;
    }

    /**
     * A short label for logs and judge output: "60% of the hit, paid at 200 per point of 好活当赏".
     *
     * <p>Worth having because the interesting failures here are all about WHICH counter paid and how much it had, not
     * about the arithmetic.
     */
    public String describe() {
        return String.format("%.0f%% of the hit, paid at %.0f per point of %s from %s",
                percent * 100, unitAmount, resource, payer == null ? "?" : payer.getName());
    }

    @Override
    public boolean canAct() {
        return true;
    }

    @Override
    public void applyEffect(CanHit target) {
        // changes no attributes: it acts at settlement time
    }

    @Override
    public void removeBuff(CanHit target) {
        // no persistent state to undo
    }

    @Override
    public void tickEffect(CanHit target) {
        decreaseDuration();
    }

    @Override
    public boolean isSameKind(AbstractBuff other) {
        // Note: Two absorbers are NOT the same kind even though they are the same class. Unlike a stat modifier -
        // where "same attribute from a different rule replaces" is the established behaviour - two absorbers backed by
        // DIFFERENT counters are two separate defenses, and evicting one for the other would silently halve a unit's
        // protection. Same counters and same rate are the same absorber, so refreshing replaces.
        if (!(other instanceof AbsorbBuff sibling)) {
            return false;
        }
        return resource.equals(sibling.resource)
                && Double.compare(unitAmount, sibling.unitAmount) == 0
                && Double.compare(percent, sibling.percent) == 0
                && payer == sibling.payer;
    }

    @Override
    public boolean isStackable() {
        return false;      // "该效果无法叠加" shapes, and refreshing is what re-declaring it means
    }

    @Override
    public int maxStacks() {
        return 1;
    }
}
