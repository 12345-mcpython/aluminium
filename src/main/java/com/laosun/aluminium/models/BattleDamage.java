package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.buff.ControlBuff;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.enemy.Enemy;

/**
 * Assembles and settles one damage instance: the boost, crit, defence, toughness-reduction and
 * resistance zones, plus the two break riders that hang off a weakness break.
 *
 * <p><b>Why this is its own class.</b> Battle is 140 methods over one state machine, and every
 * method that matters reads its instance state -- so the split had to be chosen by what a
 * candidate group READS, not by what it is called. This group reads exactly two things, both
 * already public: the injected rng (crit rolls) and battle.getLastUltEnergySpent() (the "per 1 point of
 * energy spent" rider). Its four outbound calls are all public API, which is what makes it
 * movable without touching a single signature the tests use.
 *
 * <p>Note: applyAdditionalDamage and applyTrueDamage deliberately did NOT come along. They
 * settle through Battle.applyDamage(target, instance, EnergyGrant.KILL_ONLY), and that private
 * entry point is what DamagePipelineTest pins as the only way to settle an instance -- the
 * invariant M-40 left behind. Moving them would mean widening that entry point or the grant
 * enum, which is the one thing the invariant exists to prevent.
 *
 * <p>The zone order below is the whole subject of this class and is not arbitrary: each step
 * assumes the previous one has run (the instance conditions fire before the zones are read,
 * because afterwards the number is final).
 */
public final class BattleDamage {

    private BattleDamage() {
    }


    /**
     * The two halves of zone assembly that run BEFORE the {@code DEALING_DAMAGE} announcement:
     * the DMG boost zone and the instance conditions.
     *
     * <p><b>Why the split falls exactly here.</b> The announcement is not a notification bolted on
     * at the end -- it fires mid-assembly on purpose, because a rule answering it may MUTATE the
     * instance ({@code BOOST_DAMAGE}), and everything after this point (crit, defence, resistance)
     * has to read the mutated numbers. So the announcing half cannot live here: it calls
     * {@code Battle.fireTriggers}, and that method staying private is what
     * {@code DamagePipelineTest} pins -- its invariant counts the public methods of Battle that
     * take a Damage, and requires the assembly entry point to be private to Battle.
     *
     * @param damage the hit being assembled
     */
    public static void applyZones(Battle battle, Damage damage) {
        CanHit attacker = damage.getAttacker();
        CanHit defender = damage.getDefender();

        // 0) The base layer lives in Damage.toValue: the skill multiplier plus any absolute addend
        //    ("increase the value by an amount equal to 30% of March 7th (三月七)'s defence"), added BEFORE every zone below so it crits and is
        //    boosted exactly like the multiplier. Note: Deliberately not a percentage in the boost zone: that would only
        //    equal the sentence when the base happened to equal the attribute. See Damage#addFlat.

        // 1) DMG boost zone: element boost + all-type boost (break/super break/true damage are skipped
        //  automatically by BoostArea.applies)
        AttributeType elementBoost = AttributeType.getBoostByElement(damage.getElement());
        if (elementBoost != null) {
            damage.addBoost(attacker.getAttribute(elementBoost).get());
        }
        damage.addBoost(attacker.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get());
        // ...plus the follow-up-only boost, which exists because set 115's 2-piece must raise
        // follow-up damage without touching any other attack. Gated on the damage type, not on a flag:
        // additional damage is the engine's one representation of a follow-up attack.
        if (damage.getType() == DamageType.ADDITIONAL) {
            damage.addBoost(attacker.getAttribute(AttributeType.FOLLOW_UP_DAMAGE_BOOST).get());
        }
        // ...and the same shape for damage over time (322's "additionally increases the damage over time dealt by the wearer by X%"): gated on the
        // damage TYPE, because that is what the sentence names. Note: DoT is NOT one of the types the boost zone skips
        // (break / super break / true damage are), so this reaches the tick -- checked before writing it, since an
        // attribute nothing reads would look exactly like a working rule.
        if (damage.getType() == DamageType.DOT) {
            damage.addBoost(attacker.getAttribute(AttributeType.DOT_DAMAGE_BOOST).get());
        }

        // ...and a memosprite-only sibling ("additionally increases the crit damage dealt by the wearer's memosprite by X%", light cone 2105). Gated on WHO is attacking
        // rather than on the damage's type or cast category: a memosprite's skill is an ordinary NORMAL damage like any other.
        // Note: `instanceof Summon` would be an approximation -- the documents distinguish memosprites from ordinary summons -- so the test
        // is the precise one: the attacker IS the unit that its master's memospriteOf returns.
        // ...and the memosprite sibling, gated on the damage TYPE the game itself names: `DamageType.MEMORY` is memosprite damage
        // (GLOSSARY.md), and the constant has been declared since the type table was written -- its javadoc even notes that only
        // some of the constants are in use. Note: This replaces the earlier heuristic (`attacker == memospriteOf(master)` plus
        // reading the MASTER's attribute), which existed only because the type had not been found: the type is the game's own
        // vocabulary, and it holds for a memosprite that outlives its master.
        if (damage.getType() == DamageType.MEMORY) {
            // Note: Two facts, both measured: the TYPE is what decides whether this clause applies (memosprite damage), and the VALUE comes
            // from the memosprite's master -- a panel inherits only the attributes its spec NAMES (`SummonFactory` snapshots
            // them, `MemospriteSpec` says nothing else crosses), so an ability granted to the wearer is invisible on the
            // memosprite itself. Reading the attacker alone measured exactly 1.0, i.e. no boost at all.
            CanHit owner = attacker instanceof Summon summon && summon.getMaster() != null
                    ? summon.getMaster() : attacker;
            damage.addBoost(owner.getAttribute(AttributeType.MEMOSPRITE_DAMAGE_BOOST).get());
        }

        // Scoped boosts: "increases the damage dealt by basic attacks / Skills / ultimates by X%". These cannot be gated on the damage
        // *type* the way the follow-up boost above is -- a basic attack and a skill are both
        // DamageType.NORMAL -- so they are gated on the category of the cast that produced this instance,
        // which SkillExecutor threads through (`Damage.getCastCategory`).
        //
        // Deliberately conservative about *which* instance carries the category: only the cast's own damage
        // does. Break / super break / DOT / additional instances stay UNSPECIFIED, so "the Skill's DMG" does
        // not silently grow to mean "the break damage that Skill caused" -- if content ever needs that, it is
        // a decision to take on purpose, not a side effect of this wiring. (A follow-up therefore collects
        // FOLLOW_UP_DAMAGE_BOOST and never one of these, which is what the texts mean by a follow-up attack.)
        AttributeType scopeBoost = damage.getCastCategory().damageBoost();
        if (scopeBoost != null) {
            damage.addBoost(attacker.getAttribute(scopeBoost).get());
        }

        // Damage-instance conditions: "increases the damage dealt to a target in state X by Y%". Fired *before* the zones
        // are read, because afterwards the number is final and all a rule could do is describe it. `target` is
        // the one about to take the damage; a rule that changes this instance uses BOOST_DAMAGE, which mutates
        // the instance itself -- the instance is the state, so there is no buff to attach, nothing to clean up,
        // and nothing that can leak into the next hit.
        //
        // It fires for every instance the engine settles, DOT ticks and break damage included: those are damage
        // too, and a rule that means "attacks only" says so with its own conditions.
        // "per 1 point of energy spent" (light cone 23062) scales off THIS cast's spend, which is why it rides on the
        // instance: the settlement reads the instance, and this is the only event that hands it over.
        // Note: The guard is load-bearing: battle.getLastUltEnergySpent() is written only on the ultimate path and never
        // cleared, so without it every later hit -- basics, DOT ticks, break damage -- would inherit it, an
        // error with no symptom.
        // The guard is our OWN fact, not another component's: battle.getLastUltEnergySpent() is set only on the ultimate
        // path and cleared right after it settles, so "> 0" means exactly "this hit is that ultimate".
        // Note: An earlier version asked damage.getCastCategory == ULTRA instead, and measured, that does NOT
        // hold on the castImmediate path -- the write was skipped and the clause silently read 0.
        if (battle.getLastUltEnergySpent() > 0) {
            damage.withCastEnergySpent(battle.getLastUltEnergySpent());
        }
    }

    /**
     * Lowers a settled hit by every absorber the victim carries, spending the payer's counter for each.
     *
     * <p>The mechanic (1503 Pearl (真珠)'s talent): 「每点【好活当赏】等同于 200 点抵御值，我方目标受到伤害时，真珠可消耗抵御值
     * 为我方目标抵挡 60% 的伤害」 - the counter belongs to <b>Pearl</b> while the absorber sits on an <b>ally</b>, so
     * the resource is read from the payer and not from the victim.
     *
     * <p>Order and arithmetic, all three of which the corpus's own words decide:
     * <ol>
     *   <li>the share is taken of the <b>settled</b> amount ("受到伤害时" - what the target was about to take);</li>
     *   <li>a point pays for {@code unitAmount} of damage, so the points needed are {@code want / unitAmount},
     *       rounded <b>up</b>: a point is a whole thing and a fraction of one cannot be spent;</li>
     *   <li>whatever the counter can actually pay for is what gets blocked, so a victim with 1 point behind them
     *       blocks 200 and keeps the hit's remainder - <b>partial absorption</b>, not an all-or-nothing save.</li>
     * </ol>
     *
     * <p>⚠ The deduction is derived from the points actually spent rather than from the amount the share asked for.
     * Rounding up means those differ: needing 1.5 points blocks the whole 300 those points buy, not just the 300 that
     * were needed. That is deliberate and matches "抵御值" being a pool of whole points.
     *
     * @param victim  the unit the hit is landing on
     * @param settled the amount after the zones
     * @return the amount to deduct from HP instead
     */
    public static double absorbsInto(Battle battle, CanHit victim, double settled) {
        if (settled <= 0) {
            return settled;
        }
        for (com.laosun.aluminium.models.buff.AbsorbBuff absorber : victim.getBuffManager().absorbers()) {
            CanHit payer = absorber.getPayer();
            if (payer == null || payer.isDeath()) {
                continue;                                        // a fallen payer's counter blocks nothing
            }
            double share = settled * absorber.getPercent();      // what this absorber may block at most
            if (share <= 0) {
                continue;
            }
            int affordable = payer.getResources().value(absorber.getResource());
            if (affordable <= 0) {
                continue;
            }
            int needed = (int) Math.ceil(share / absorber.getUnitAmount());
            int spent = payer.getResources().spend(absorber.getResource(), Math.min(needed, affordable));
            if (spent <= 0) {
                continue;
            }
            double blocked = Math.min(spent * absorber.getUnitAmount(), settled);
            settled -= blocked;
            if (settled <= 0) {
                return 0;
            }
        }
        return settled;
    }

    /**
     * The shield amount a given provider's shield is actually worth: {@code amount  x  (1 + shield amount provided boost)}.
     *
     * <p><b>One formula, two paths.</b> A {@code SHIELD} effect reaches the field either as a raw grant (no
     * {@code turns}) or through a {@link com.laosun.aluminium.models.buff.ShieldBuff} (timed), and "the shield amount provided" has to
     * mean the same number in both - otherwise a shield would be worth 120 for three turns and 100 forever after the
     * duration came off, which is exactly the class of silent discrepancy this project keeps hunting. The buff
     * snapshots this value when it is constructed; the raw path calls it here.
     *
     * <p>Note: The boost is read <b>once, at grant time</b>, and frozen into the shield (the same convention
     * {@code MODIFY_ATTR}'s derived values use): a shield already standing does not grow when its giver is
     * strengthened later.
     *
     * @param provider whose boost applies ({@code null} = none)
     * @param amount   the requested amount
     * @return the amount after the boost, never negative
     */
    public static double boostedShield(CanHit provider, double amount) {
        double base = Math.max(0, amount);
        if (provider == null) {
            return base;
        }
        return base * (1 + provider.getAttribute(AttributeType.SHIELD_BOOST).get());
    }

    /**
     * Weakness break's attached damage over time: only Fire/Lightning/Physical/Wind have it.
     * Ice = Frozen, Quantum = Entanglement, Imaginary = Imprisonment - those three carry <b>no effect at
     * all</b> today, they are not merely "a different DOT".
     *
     * <p>The per-element numbers come from {@code Constant.BREAK_EFFECTS} (the table: dot ratio,
     * dot turns, control type), which replaced the old "one ratio and one duration for all four
     * elements" pair of scalars. That table also names the control type of the other three elements,
     * but naming it is where it stops: the control state machine does not exist yet, so
     * {@code hasDot()} is false for them and this method returns above. The honest state is "the four
     * DOT elements are data now; the three control elements are labelled but inert".
     *
     * <p>The DOT is attached as an ordinary {@code DotBuff}, so nothing here decides how it is stored,
     * counted down or removed - that is the buff system's job, and it is what lets the same burn land
     * on a character as on an enemy.
     *
     * @param attacker the breaker (the DOT's source, and the damage's attacker)
     * @param enemy    the target that was broken
     * @param element  the break element
     */
    public static void attachBreakDot(Battle battle, CanHit attacker, Enemy enemy, DamageElement element) {
        Constant.BreakEffect effect = Constant.BREAK_EFFECTS.get(element);
        if (effect == null || !effect.hasDot()) {
            return;
        }
        enemy.getBuffManager().addBuff(new DotBuff(attacker, element,
                BreakDamageCalculator.breakBaseOf(attacker) * effect.dotRatio(), effect.dotTurns()));
    }

    /**
     * The control part of a weakness break: the element's own extra action delay, plus the control
     * state itself.
     *
     * <p><b>What the three non-damaging elements do, and how that was decided.</b> The plan said "during the frozen period, the damage taken
     * is +30%, apply control" - a guess. The encyclopedia text was probed instead, and it says something else,
     * consistently across every source that describes the states:
     * <ul>
     *  <li>Frozen - {@code "while in the frozen state, the enemy target cannot act and at the start of every turn takes ice damage equal to #4% of the caster's ATK"}
     *       (深寒徘徊者 / 永冬灾影 / March 7th (三月七) / Gepard (杰帕德) / Jingliu (镜流), six independent entries). The damage is <b>ice
     *       damage over time</b>, not a "taken +30%" multiplier - which is why it is <b>not</b> implemented
     *  here: it is a DOT whose ratio is not in the data, and {@code BreakEffect.dotRatio} is that field.
     *  See the TODO in rather than a number invented here.</li>
     *  <li>Imprisonment - {@code "while in the imprisoned state, the enemy target's action is delayed by #2% and its speed is reduced by #4%"} (Welt (瓦尔特)).</li>
     *  <li>Entanglement - {@code ""Entanglement" makes the enemy's action be delayed, and deals additional quantum damage to it on its next action"}
     *       (explicitly about a weakness break with Quantum). The delayed damage is again a DOT, left to the same
     *       TODO.</li>
     * </ul>
     * So all three are <b>action delay</b>, and the difference is Frozen = cannot act versus Imprisonment/Entanglement = acts but slower
     * (a {@code SPEED} debuff). Both are existing primitives, and since they are the two parts of
     * {@code ControlBuff} - which is what gives a control its <b>name</b>, so "the frozen state" can be asked about no
     * matter which path applied it.
     *
     * <p><b>No effect-hit roll, and no {@code resistKey} lookup, on this path.</b> A break is not a resisted
     * debuff: it happens because the toughness bar emptied. {@code ControlEffect.resistKey} is for the
     * <i>skill</i>-applied case, which goes through {@link #tryApplyDebuff}, and letting a monster's
     * {@code STAT_CTRL_*} resistance cancel a break would make a weakness break silently do nothing.
     *
     * @param enemy   the enemy that was just broken
     * @param element the break element
     */
    public static void attachBreakControl(Battle battle, Enemy enemy, DamageElement element) {
        Constant.BreakEffect breakEffect = Constant.BREAK_EFFECTS.get(element);
        if (breakEffect == null) {
            return;
        }
        // Order: the state first, the one-off push last.
        //
        // NOTE: apply the state first, then the one-off push: that is the order the data describes them in,
        // not a correctness requirement. A push applied *before* the slow is recomputed away by the
        // reschedule, which makes the element's extra delay unobservable (measured: 28.409 with and without
        // it). That loss is fixed at its source -- the ledgers are synchronised and the progress is not
        // capped from above -- so a push survives a speed change either way.
        Constant.ControlEffect control = breakEffect.controlEffect();
        if (control != null) {
            // One buff, three parts: before this, the composition was written out here as "a
            // StunBuff, plus a SPEED debuff if the element slows" -- which left the state with no name, so
            // nothing could ask "the frozen state" about a break-frozen unit, and an ability-applied control (which
            // needs exactly the same composition plus a resistance roll) had no shared place to live.
            enemy.getBuffManager().addBuff(new ControlBuff(control, control.turns()));
        }
        if (breakEffect.delayPercent() > 0) {
            // An instant push, not a buff: Imprisonment/Entanglement's "action delay" is a one-off on the action bar, so it must
            // not expire with the state (a state that lasts 1 turn leaving a delay that outlives it is the
            // data's own behaviour). Kept outside the control block so that an element may carry extra
            // delay without a state, and so the four DOT elements (delayPercent = 0) are untouched.
            battle.delayMovePercent(enemy, breakEffect.delayPercent());
        }
    }
}
