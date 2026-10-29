package com.laosun.aluminium.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.damage.BattleDamage;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.trigger.TriggerPayload;

import java.util.List;

/**
 * Damage assembly and settlement.
 *
 * <p><b>Why this is separate.</b> The zone arithmetic lives in {@link BattleDamage}; this is the
 * ordered settlement around it - assembly, absorption, HP/lethal/death facts, energy and the final
 * observer hand-off. The one rule that shapes the order is that {@code DEALING_DAMAGE} must sit between
 * the zone halves, because a rule answering it can mutate the instance every later zone reads.
 */
public final class DamageService {

    /**
     * Which energy gains one instance may settle.
     */
    public enum EnergyGrant {
        /** The main instance of an attack: hit energy and kill energy. */
        ALL,
        /** A derived instance (break / DOT / additional): kill energy only. */
        KILL_ONLY,
        /** No energy at all. */
        NONE
    }

    /**
     * Announces HP loss.
     */
    @FunctionalInterface
    public interface HpLossSink {
        /**
         * @param target the victim
         * @param before HP before
         * @param after  HP after
         * @param source the attacker
         * @param amount the HP lost
         */
        void accept(CanHit target, double before, double after, CanHit source, double amount);
    }

    /**
     * Announces a kill.
     */
    @FunctionalInterface
    public interface KillSink {
        /**
         * @param attacker the killer
         * @param victim   the killed unit
         */
        void accept(CanHit attacker, CanHit victim);
    }

    private final Battle battle;
    private final TriggerBus bus;
    private final DamageMarks marks;
    private final EnergyService energy;
    private final BattleObservers observers;
    private final HpLossSink hpLossSink;
    private final KillSink killSink;

    /**
     * @param battle      the battle owning the units
     * @param bus         the trigger bus
     * @param marks       the per-action damage marks
     * @param energy      the energy crediting service
     * @param observers   the observer registry
     * @param hpLossSink  announces HP loss
     * @param killSink    announces a kill
     */
    public DamageService(Battle battle, TriggerBus bus, DamageMarks marks, EnergyService energy,
                         BattleObservers observers, HpLossSink hpLossSink, KillSink killSink) {
        this.battle = battle;
        this.bus = bus;
        this.marks = marks;
        this.energy = energy;
        this.observers = observers;
        this.hpLossSink = hpLossSink;
        this.killSink = killSink;
    }

    /**
     * Settles one hit as the main instance of its attack.
     *
     * @param target the combatant taking the hit
     * @param damage the hit
     * @return the settled damage, or {@code 0} if the target was already dead
     */
    public double applyDamage(CanHit target, Damage damage) {
        return applyDamage(target, damage, EnergyGrant.ALL);
    }

    /**
     * Settles one hit with an explicit energy grant.
     *
     * @param target the combatant taking the hit
     * @param damage the hit
     * @param grant  which energy gains this instance may settle
     * @return the settled damage
     */
    public double applyDamage(CanHit target, Damage damage, EnergyGrant grant) {
        if (target.isDeath() || target.isInvulnerable()) {
            return 0;
        }
        double settled = assemble(damage);
        settled = BattleDamage.absorbsInto(battle, target, settled);
        bus.addSettledDamage(settled);
        double hpBefore = target.getCurrentHp();
        boolean lethal = settled >= hpBefore;
        boolean died;
        if (lethal) {
            target.takeDamage(settled, false);
            marks.markLethal(target);
            bus.fireForAlly(TriggerPayload.AllySubject.of(TriggerEvent.LETHAL_DAMAGE, target, target, 0));
            died = target.getCurrentHp() <= 0 && !target.getBuffManager().defersDeath();
            if (died) {
                target.perish();
            }
        } else {
            died = target.takeDamage(settled);
        }
        double hpLoss = hpBefore - target.getCurrentHp();
        double shieldAbsorbed = target.getLastShieldAbsorbed();
        bus.fireForAlly(TriggerPayload.AllySubject.of(TriggerEvent.TAKING_HIT, damage.getAttacker(), target,
                settled));
        if (hpLoss > 0) {
            marks.markLostHp(target);
            hpLossSink.accept(target, hpBefore, target.getCurrentHp(), damage.getAttacker(), hpLoss);
            bus.fireForAlly(TriggerPayload.AllySubject.of(TriggerEvent.HP_LOST, damage.getAttacker(), target,
                    hpLoss));
        }
        bus.fire(TriggerPayload.DamagePayload.of(TriggerEvent.DAMAGE_SETTLED, damage.getAttacker(), target,
                settled, damage, damage.getCastCategory(), 0, List.of()));
        if (died) {
            killSink.accept(damage.getAttacker(), target);
            bus.fire(TriggerPayload.DamagePayload.of(TriggerEvent.DEATH, target, target, 0.0, damage,
                    damage.getCastCategory(), 0, List.of()));
            bus.fire(TriggerPayload.Subject.of(TriggerEvent.KILL, damage.getAttacker(), target, 0, castOf(damage)));
        }
        grantHitAndKillEnergy(target, damage, died, grant);
        double applied = shieldAbsorbed + hpLoss;
        for (Battle.DamageObserver observer : observers.damageObservers()) {
            observer.onDamageSettled(damage, applied, hpLoss, shieldAbsorbed, died);
        }
        return applied;
    }

    /**
     * Assembles and settles one hit's zones.
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

    private void grantHitAndKillEnergy(CanHit target, Damage damage, boolean died, EnergyGrant grant) {
        if (!died) {
            grantHitEnergy(target, damage, grant);
            return;
        }
        grantKillEnergy(target, damage, grant);
    }

    private void grantHitEnergy(CanHit target, Damage damage, EnergyGrant grant) {
        if (grant != EnergyGrant.ALL || !damage.isCountsAsAttack()) {
            return;
        }
        var hitGain = target.getEnergyProvider().onTakingHit(target, damage);
        if (hitGain != null) {
            energy.apply(target, hitGain);
        }
    }

    private void grantKillEnergy(CanHit target, Damage damage, EnergyGrant grant) {
        if (grant == EnergyGrant.NONE) {
            return;
        }
        CanHit attacker = damage.getAttacker();
        if (attacker == null) {
            return;
        }
        var killGain = attacker.getEnergyProvider().onKill(attacker, target);
        if (killGain != null) {
            energy.apply(attacker, killGain);
        }
    }

    private static SkillCategory castOf(Damage damage) {
        if (damage == null) {
            return SkillCategory.UNSPECIFIED;
        }
        SkillCategory own = damage.getCastCategory();
        if (own != null && own != SkillCategory.UNSPECIFIED) {
            return own;
        }
        SkillCategory cause = damage.getCausedByCast();
        return cause == null ? SkillCategory.UNSPECIFIED : cause;
    }
}
