package com.laosun.aluminium.models.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.buff.AbstractBuff;
import com.laosun.aluminium.models.trigger.TriggerPayload;

/**
 * The one chokepoint every landed debuff passes through.
 *
 * <p><b>Why this is separate.</b> The hit check, the class resistance, the buff installation and the
 * "which family landed" fact are one sequence; splitting them between callers is how a debuff ends up
 * bypassing effect hit rate. Keeping the sequence here gives the engine one spelling of "apply a
 * debuff".
 */
public final class DebuffService {

    private final Battle battle;
    private final TriggerBus bus;

    /**
     * @param battle the battle whose chance and seeded RNG are used
     * @param bus    the trigger bus
     */
    public DebuffService(Battle battle, TriggerBus bus) {
        this.battle = battle;
        this.bus = bus;
    }

    /**
     * Applies a debuff if it hits.
     *
     * @param caster            the applier
     * @param target            the target
     * @param buff              the buff
     * @param baseChance        the base chance
     * @param specificResistKey a specific resistance key, or {@code null}
     * @return whether it landed
     */
    public boolean tryApply(CanHit caster, CanHit target, AbstractBuff buff,
                            double baseChance, String specificResistKey) {
        if (caster == null || target == null || buff == null || target.isDeath()) {
            return false;
        }
        double classResist = target.getBuffManager().debuffResistOf(buff.debuffClass());
        double chance = battle.hitChance(caster, target, baseChance, specificResistKey) * (1 - classResist);
        if (!(battle.getRng().nextDouble() < Math.clamp(chance, 0, 1))) {
            return false;
        }
        target.getBuffManager().addBuff(buff);
        battle.noteAppliedDebuffClass(buff.debuffClass());
        bus.fire(TriggerPayload.Subject.of(TriggerEvent.DEBUFF_APPLIED, caster, target, 0, null));
        return true;
    }
}
