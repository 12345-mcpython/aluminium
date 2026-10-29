package com.laosun.aluminium.models.battle;

import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.entity.CanHit;
import com.laosun.aluminium.models.damage.Damage;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.buff.RegenBuff;

import java.util.HashMap;
import java.util.Map;

/**
 * The start-of-turn pass: damage over time and heal over time.
 *
 * <p><b>Why this is separate.</b> Both are "settle every buff of this kind on one unit, and let the
 * buff manager tick the duration afterwards" - one pass, one snapshot rule, one place where the stack
 * ceiling is applied. The buff manager cannot do it itself because {@code tickEffect} has no battle
 * handle, so the settlement lives here instead of in the buff.
 */
public final class TickService {

    private final DamageService damage;
    private final SustainService sustain;

    /**
     * @param damage  the settlement path
     * @param sustain the heal path
     */
    public TickService(DamageService damage, SustainService sustain) {
        this.damage = damage;
        this.sustain = sustain;
    }

    /**
     * Settles every DOT on one unit, first applied first settled.
     *
     * @param target the carrier
     * @return the total settled
     */
    public double dots(CanHit target) {
        if (target == null || target.isDeath()) {
            return 0;
        }
        double total = 0;
        Map<DamageElement, Integer> paidPerState = new HashMap<>();
        for (DotBuff dot : target.getBuffManager().allBuffsOf(DotBuff.class)) {
            int paidLayers = paidPerState.merge(dot.getElement(), 1, Integer::sum);
            if (dot.getMaxStacks() > 0 && paidLayers > dot.getMaxStacks()) {
                continue;
            }
            if (target.isDeath()) {
                break;
            }
            Damage instance = new Damage(dot.getSource(), target, dot.getElement(),
                    DamageType.DOT, dot.getBaseDamage());
            total += damage.applyDamage(target, instance, DamageService.EnergyGrant.KILL_ONLY);
        }
        return total;
    }

    /**
     * Settles one extra instance of one DOT state right now, at a share of its current damage.
     *
     * @param target  the carrier
     * @param element the state's element
     * @param percent the share (0.85 = 85%)
     * @return the total settled
     */
    public double dotStateNow(CanHit target, DamageElement element, double percent) {
        if (target == null || target.isDeath() || element == null) {
            return 0;
        }
        double total = 0;
        Map<DamageElement, Integer> paidPerState = new HashMap<>();
        for (DotBuff dot : target.getBuffManager().allBuffsOf(DotBuff.class)) {
            int paid = paidPerState.merge(dot.getElement(), 1, Integer::sum);
            if (dot.getElement() != element) {
                continue;
            }
            if (dot.getMaxStacks() > 0 && paid > dot.getMaxStacks()) {
                continue;
            }
            Damage instance = new Damage(dot.getSource(), target, dot.getElement(), DamageType.DOT,
                    dot.getBaseDamage() * percent);
            total += damage.applyDamage(target, instance, DamageService.EnergyGrant.KILL_ONLY);
        }
        return total;
    }

    /**
     * Settles every heal over time on one unit.
     *
     * @param target the carrier
     * @return the total restored
     */
    public double regens(CanHit target) {
        if (target == null || target.isDeath()) {
            return 0;
        }
        double total = 0;
        for (RegenBuff regen : target.getBuffManager().allBuffsOf(RegenBuff.class)) {
            if (target.isDeath()) {
                break;
            }
            total += sustain.heal(regen.getSource(), target, regen.getBaseHeal());
        }
        return total;
    }
}
