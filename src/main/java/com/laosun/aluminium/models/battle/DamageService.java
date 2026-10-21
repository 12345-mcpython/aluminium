package com.laosun.aluminium.models.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.BattleDamage;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.trigger.TriggerPayload;

import java.util.List;

/**
 * The damage assembly half: the {@code DEALING_DAMAGE} announcement, then crit, defence, toughness
 * reduction and resistance.
 *
 * <p><b>Why this is separate.</b> The zone arithmetic lives in {@link BattleDamage}; this is the
 * ordered settlement around it, where the announcement must sit between the halves because a rule
 * answering {@code DEALING_DAMAGE} can mutate the instance every later zone reads.
 */
public final class DamageService {

    private final Battle battle;
    private final TriggerBus bus;

    /**
     * @param battle the battle whose random source is used
     * @param bus    the trigger bus that announces the damage events
     */
    public DamageService(Battle battle, TriggerBus bus) {
        this.battle = battle;
        this.bus = bus;
    }

    /**
     * Assembles and settles one hit.
     *
     * @param damage the hit
     * @return the final damage, floored at 1
     */
    public double assemble(Damage damage) {
        CanHit attacker = damage.getAttacker();
        CanHit defender = damage.getDefender();

        BattleDamage.applyZones(battle, damage);

        // The announcement sits between the halves: a rule may mutate this instance, and every zone
        // below must read the mutated numbers.
        bus.fire(TriggerPayload.DamagePayload.of(TriggerEvent.DEALING_DAMAGE, attacker, defender,
                damage.getSkillBaseValue(), damage, damage.getCastCategory(), damage.getSkillKey(), List.of()));

        if (damage.getType().isCrittable() && !damage.isCritFixed()) {
            double critRate = attacker.getAttribute(AttributeType.CRIT_CHANCE).get() + damage.getExtraCritChance();
            boolean isCrit = critRate > 0 && battle.getRng().nextDouble() < critRate;
            damage.crit(isCrit, attacker.getAttribute(AttributeType.CRIT_ATTACK).get() + damage.getExtraCritDamage());
        }
        if (damage.critArea().isCrit()) {
            bus.fire(TriggerPayload.Subject.of(TriggerEvent.CRIT_DEALT, attacker, defender, 0,
                    damage.getCastCategory()));
        }

        damage.defence(attacker.getLevel(),
                defender.getAttribute(AttributeType.DEFENCE).get(),
                attacker.getAttribute(AttributeType.DEFENCE_IGNORE).get());

        if (defender instanceof Enemy toughnessHolder && toughnessHolder.hasToughnessBar()
                && (!toughnessHolder.isBroken() || damage.getType() == DamageType.BREAK)) {
            damage.addReduction(Constant.TOUGHNESS_DAMAGE_REDUCTION);
        }

        double rawResist = defender instanceof Enemy enemy
                ? enemy.getDamageResist().getOrDefault(damage.getElement(), 0.0)
                : 0.0;
        rawResist -= defender.getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
        rawResist += defender.getAttribute(AttributeType.ALL_TYPE_RESISTANCE).get();
        damage.resist(rawResist, attacker.getAttribute(AttributeType.DAMAGE_PENETRATION).get());

        attacker.onDamage(battle, damage);
        defender.onDamage(battle, damage);

        return Math.max(1, damage.toValue());
    }
}
