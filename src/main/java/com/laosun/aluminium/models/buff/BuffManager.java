package com.laosun.aluminium.models.buff;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.event.*;
import com.laosun.aluminium.models.skill.Skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Owns a unit's buffs: attach / remove / tick, and dispatch the event callbacks to them.
 *
 * <p><b>Every traversal of {@link #buffs} goes over {@link List#copyOf} — keep it that way (M-12).</b>
 * The dispatch methods call <i>into</i> buff code, and attaching a buff from a reaction is ordinary
 * content, not an error: additional damage applies vulnerability, a counter attaches a marker, a
 * damage reaction boosts its owner (that last one is a literal test case). Iterating the live list made
 * every one of those a {@link java.util.ConcurrentModificationException} thrown from inside damage
 * settlement — the hardest place to diagnose — or, where the list happened to tolerate it, a silently
 * skipped buff. {@code processBuffTick} had the same hole through {@code removeIf}, whose predicate
 * calls {@code tickEffect} / {@code removeBuff}.
 *
 * <p>The cost is a small copy per traversal (a unit carries a handful of buffs); the alternative is a
 * crash in the middle of a turn. The read-only queries ({@code hasBuff} / {@code countBuffs} /
 * {@code findBuff} / {@code allBuffsOf}) copy for the same reason: one rule with no exceptions is what
 * stops a later edit from re-opening the hole in just one of them.
 *
 * <p>{@code buffs} is never handed out (P1-7), so this class is also the only place that can get it wrong.
 */
public class BuffManager {
    private CanHit instance;
    private final List<AbstractBuff> buffs = new ArrayList<>();
    private boolean blocked = false;

    public BuffManager(CanHit instance) {
        this.instance = instance;
    }

    public void addBuff(AbstractBuff buff) {
        if (buff == null) {
            return;
        }
        if (buff.isStackable()) {
            addStackable(buff);
            return;
        }
        for (int i = buffs.size() - 1; i >= 0; i--) {
            AbstractBuff existed = buffs.get(i);
            if (existed.isSameKind(buff)) {
                buffs.remove(i);
                existed.removeBuff(instance);
            }
        }
        buff.setOwner(instance);      // C-1: record the owner, so buffs that inject zones per side can tell which side they stand on
        buffs.add(buff);
        buff.applyEffect(instance);
    }

    /**
     * Attaches one <b>stackable</b> buff, enforcing its cap (the generic "stacks up to N times"
     * primitive).
     *
     * <p><b>Why this is a separate path instead of a change to {@link AbstractBuff#isSameKind}.</b>
     * {@code isSameKind} answers "should re-applying this replace the old one?", and the engine's
     * answer has always been yes — {@code BuffManagerTest.sameKindBuffRefreshesInsteadOfStacking} and
     * {@code BuffRuleTest.theSameBuffAgainRefreshesInsteadOfStacking} pin exactly that. Stacking is a
     * <i>different</i> question ("should these accumulate?"), so it is answered by a different
     * predicate ({@code isStackable} / {@code stackGroupKey}) and the replace rule stays untouched
     * for every buff that does not opt in (including a plain {@code MODIFY_ATTR}).
     *
     * <p><b>Cap enforcement: further applications are dropped.</b> Once {@code maxStacks} instances
     * are attached, another one is not added and no existing stack is touched. The alternatives were
     * rejected for concrete reasons:
     * <ul>
     *   <li><i>evict the oldest stack</i> — that is a rolling window, not "stacking up to N": a 5-stack
     *       buff would never reach its documented maximum effect;</li>
     *   <li><i>refresh the timer of the existing stacks</i> — means something different ("re-applying
     *       extends it") and would be wrong for the "for the rest of the battle" case, which has no
     *       timer to refresh.</li>
     * </ul>
     * "The buff is already at its cap" is the reading the game text supports ("can stack up to N
     * time(s)"), and it is the one that keeps the modifier count exactly equal to the effective stack
     * count.
     *
     * <p><b>Removal stays exact and per-stack.</b> Every stack is an ordinary buff instance with its
     * own {@code id}, so {@link #removeBuff(AbstractBuff)} (or expiry, or {@link #clearAll()}) takes
     * off exactly one stack's modifier: the attribute returns to the value implied by the remaining
     * stacks with no residue, because {@code StatModifierBuff.removeBuff} removes the modifier
     * <i>by id</i>.
     *
     * @param buff the stackable buff to attach
     */
    private void addStackable(AbstractBuff buff) {
        Object key = buff.stackGroupKey();
        if (key == null) {
            // A stackable buff without a group key cannot be counted, so its cap could never be
            // enforced. Refusing loudly beats an unbounded list, and the message says which class.
            throw new IllegalStateException(
                    buff.getClass().getSimpleName() + " reports isStackable() but has no stack group "
                            + "key, so its maxStacks(" + buff.maxStacks() + ") cannot be enforced");
        }
        int attached = 0;
        for (AbstractBuff existed : buffs) {
            if (existed.isStackable() && key.equals(existed.stackGroupKey())) {
                attached++;
            }
        }
        if (attached >= buff.maxStacks()) {
            return;                                  // at the cap: another application changes nothing
        }
        buff.setOwner(instance);
        buffs.add(buff);
        buff.applyEffect(instance);
    }

    /**
     * How many instances of a buff of the given class are currently attached.
     *
     * <p>Exists because "at how many stacks am I?" is a legitimate question for content and tests,
     * and the alternative — handing the buff list out — is the P1-7 decision this class deliberately
     * keeps (see {@link #hasBuff(Class)}). Matching is by exact class, the same convention as
     * {@code hasBuff}.
     *
     * @param kind the buff type to count
     * @return the number of attached instances; 0 for {@code null}
     */
    public int countBuffs(Class<? extends AbstractBuff> kind) {
        if (kind == null) {
            return 0;
        }
        int count = 0;
        for (AbstractBuff buff : List.copyOf(buffs)) {
            if (buff.getClass() == kind) {
                count++;
            }
        }
        return count;
    }

    /**
     * Removes one stack of a buff kind, if any ("consumes a stack").
     *
     * <p>Implemented here rather than by exposing the list, for the reason in {@link #hasBuff(Class)}.
     *
     * @param kind the buff type
     * @return {@code true} = a stack was found and removed
     */
    public boolean removeOneBuff(Class<? extends AbstractBuff> kind) {
        if (kind == null) {
            return false;
        }
        for (int i = buffs.size() - 1; i >= 0; i--) {
            AbstractBuff buff = buffs.get(i);
            if (buff.getClass() == kind) {
                buffs.remove(i);
                buff.removeBuff(instance);
                return true;
            }
        }
        return false;
    }

    /**
     * Removes up to {@code count} <b>debuffs</b> ("解除 N 个负面效果"), newest first.
     *
     * <p>The "which one comes off" choice is the same LIFO order {@link #removeStack} and
     * {@link #removeOneBuff(Class)} use, and it is a <b>decision</b>: the game's texts do not say, and picking
     * deterministically is what makes a dispel testable at all. The order is stated here so that a reader — or a
     * future "remove the oldest instead" — has one place to change.
     *
     * <p>Nothing to dispel is <b>not</b> an error (the return value reports what happened): 「受到攻击时解除自身
     * 1 个负面效果」 fires on every hit, including the ones where there is nothing negative on you.
     *
     * @param count how many to remove at most (non-positive removes nothing)
     * @return how many were actually removed
     */
    public int removeDebuffs(int count) {
        if (count <= 0) {
            return 0;
        }
        // Snapshot + newest-first, for the reasons in the class javadoc (M-12) and above.
        List<AbstractBuff> snapshot = List.copyOf(buffs);
        int removed = 0;
        for (int i = snapshot.size() - 1; i >= 0 && removed < count; i--) {
            AbstractBuff buff = snapshot.get(i);
            if (buff.isDebuff()) {
                removeBuff(buff);
                removed++;
            }
        }
        return removed;
    }

    /**
     * How many negative effects are attached — 「目标身上有几个负面效果」, which several rules condition on.
     *
     * @return the debuff count (never negative)
     */
    public int debuffCount() {
        int count = 0;
        for (AbstractBuff buff : List.copyOf(buffs)) {
            if (buff.isDebuff()) {
                count++;
            }
        }
        return count;
    }

    public void removeBuff(AbstractBuff buff) {
        if (buff == null || !buffs.remove(buff)) return;
        buff.removeBuff(instance);
    }

    /**
     * Removes up to {@code count} {@link StatModifierBuff} instances on one attribute, <b>newest first</b>.
     *
     * <p><b>Why this exists.</b> "…stacking up to 3 time(s). At the start of the wearer's turn or after using
     * Ultimate, removes 1 stack(s) of this effect" (relic set 131 「星如我见的领航员」) is a shape that appears
     * in many texts, and the engine already models the stacking half ({@code MODIFY_ATTR} with
     * {@code max_stacks}). What was missing is the way back: a stack could grow but never shrink, so such an
     * effect could only be modelled by dropping half of its text. The data side of that is the trigger op
     * {@code REMOVE_STACK}; this is the primitive under it.
     *
     * <p>Order matches {@link #removeOneBuff(Class)}: the stack added <b>most recently</b> goes first, which
     * is what a counter does — and with equal-valued stacks the choice is invisible anyway.
     *
     * <p>Nothing to remove is <b>not</b> an error (the return value reports what happened): a rule that says
     * "at the start of the wearer's turn, removes 1 stack" fires on every turn, including the ones where the
     * count is already zero.
     *
     * <p>Removal goes through {@link #removeBuff(AbstractBuff)}, so the attribute returns to exactly the value
     * the remaining stacks add up to — the modifier is dropped by id and nothing is recomputed.
     *
     * @param attribute the attribute whose modifier stacks to remove
     * @param count     how many to remove at most (non-positive removes nothing)
     * @return how many were actually removed
     */
    public int removeStacks(AttributeType attribute, int count) {
        if (attribute == null || count <= 0) {
            return 0;
        }
        // Snapshot + newest-first, for the reasons in the class javadoc (M-12) and above.
        List<AbstractBuff> snapshot = List.copyOf(buffs);
        int removed = 0;
        for (int i = snapshot.size() - 1; i >= 0 && removed < count; i--) {
            AbstractBuff buff = snapshot.get(i);
            if (buff instanceof StatModifierBuff modifier && modifier.getAttribute() == attribute) {
                removeBuff(buff);
                removed++;
            }
        }
        return removed;
    }

    public boolean canAct() {
        if (blocked) {
            return false;
        }
        for (AbstractBuff buff : List.copyOf(buffs)) {
            if (!buff.canAct()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Settles early buffs before the owner's move (tick duration, remove expired).
     */
    public void beforeMove() {
        blocked = false;
        processBuffTick(true);
    }

    /**
     * Settles late buffs after the owner's move (tick duration, remove expired).
     */
    public void afterMove() {
        processBuffTick(false);
    }

    /**
     * Lets every buff that reacts to damage touch the zones of the hit being settled.
     * Called by {@link CanHit#onDamage(Battle, Damage)} before {@code Damage.toValue()}.
     */
    public void onDamage(Battle battle, Damage damage) {
        for (AbstractBuff buff : List.copyOf(buffs)) {
            if (buff instanceof DamageEvent event) {
                event.onDamage(battle, damage);
            }
        }
    }

    /**
     * Lets every buff react to a finished attack（知更鸟【协奏】/缇宝结界的"after our side attacks"）.
     * Called by {@link CanHit#afterAttack(Battle, CanHit, CanHit, List, double)}.
     */
    public void afterAttack(Battle battle, CanHit attacker, CanHit mainTarget,
                            List<? extends CanHit> hitTargets, double totalDamage) {
        for (AbstractBuff buff : List.copyOf(buffs)) {
            if (buff instanceof AttackEvent event) {
                event.afterAttack(battle, attacker, mainTarget, hitTargets, totalDamage);
            }
        }
    }

    /**
     * Lets every buff react to a skill being cast（P8-6）—— **non-damaging skills also fire it**,
     * so "restore skill points after casting a skill" (Bronya / Sushang) effects of that kind can
     * also receive healing / shield skills.
     * Called by {@link CanHit#onSkillCast(Battle, CanHit, Skill, List, List)}.
     */
    public void onSkillCast(Battle battle, CanHit user, Skill skill,
                            List<? extends CanHit> hitTargets, List<? extends CanHit> targets) {
        for (AbstractBuff buff : List.copyOf(buffs)) {
            if (buff instanceof SkillCastEvent event) {
                event.onSkillCast(battle, user, skill, hitTargets, targets);
            }
        }
    }

    /**
     * Lets every buff react to an energy credit（P8-6）. {@code actuallyAdded} is the
     * value that really landed after the cap.
     * Called by {@link CanHit#onEnergyGain(Battle, CanHit, double)}.
     */
    public void onEnergyGain(Battle battle, CanHit target, double actuallyAdded) {
        for (AbstractBuff buff : List.copyOf(buffs)) {
            if (buff instanceof EnergyEvent event) {
                event.onEnergyGain(battle, target, actuallyAdded);
            }
        }
    }

    /**
     * Lets every buff react to real HP loss（P8-6）—— the part absorbed by a shield does not count,
     * so this event does not fire while the shield is unbroken.
     * Called by {@link CanHit#onHpLoss(Battle, CanHit, double, double, CanHit, double)}.
     */
    public void onHpLoss(Battle battle, CanHit target, double before, double after,
                         CanHit source, double amount) {
        for (AbstractBuff buff : List.copyOf(buffs)) {
            if (buff instanceof HpLossEvent event) {
                event.onHpLoss(battle, target, before, after, source, amount);
            }
        }
    }

    /**
     * Lets every buff react to real healing（P8-6）. Called by
     * {@link CanHit#onHeal(Battle, CanHit, CanHit, double)}.
     */
    public void onHeal(Battle battle, CanHit healer, CanHit target, double actuallyHealed) {
        for (AbstractBuff buff : List.copyOf(buffs)) {
            if (buff instanceof HealEvent event) {
                event.onHeal(battle, healer, target, actuallyHealed);
            }
        }
    }

    /**
     * Lets every buff react to a kill（P8-6）—— including additional damage / true damage finishing blows.
     * Called by {@link CanHit#onKill(Battle, CanHit, CanHit)}.
     */
    public void onKill(Battle battle, CanHit attacker, CanHit victim) {
        for (AbstractBuff buff : List.copyOf(buffs)) {
            if (buff instanceof KillEvent event) {
                event.onKill(battle, attacker, victim);
            }
        }
    }

    /**
     * Lets every buff react to a weakness break（P8-6）—— fires only once at the moment toughness hits zero.
     * Called by {@link CanHit#onBreak(Battle, CanHit, CanHit, DamageElement)}.
     */
    public void onBreak(Battle battle, CanHit attacker, CanHit target, DamageElement element) {
        for (AbstractBuff buff : List.copyOf(buffs)) {
            if (buff instanceof BreakEvent event) {
                event.onBreak(battle, attacker, target, element);
            }
        }
    }

    /**
     * Lets every buff react to a skill point being gained（P8-6）.
     * Called by {@link CanHit#onSkillPointGained(Battle, int)}.
     */
    public void onSkillPointGained(Battle battle, int amount) {
        for (AbstractBuff buff : List.copyOf(buffs)) {
            if (buff instanceof SkillPointEvent event) {
                event.onSkillPointGained(battle, amount);
            }
        }
    }

    /**
     * Lets every buff react to a skill point being **really** spent（P8-6）——
     * this event does not fire when skill points are insufficient and the action does not go through.
     * Called by {@link CanHit#onSkillPointSpent(Battle, int)}.
     */
    public void onSkillPointSpent(Battle battle, int amount) {
        for (AbstractBuff buff : List.copyOf(buffs)) {
            if (buff instanceof SkillPointEvent event) {
                event.onSkillPointSpent(battle, amount);
            }
        }
    }

    private void processBuffTick(boolean early) {
        // ⚠ Iterated over a snapshot, not with `removeIf` (M-12). `tickEffect` and `removeBuff` are buff
        // code: a buff may attach or remove another buff while it ticks, and `removeIf` walks the live list
        // -- a ConcurrentModificationException raised from the middle of a turn boundary, or a silently
        // skipped buff. Removal is stated explicitly here instead, which is the same thing `removeIf` did.
        // ⚠ Iterated over a snapshot, not with `removeIf` (M-12). `tickEffect` and `removeBuff` are buff
        // code: a buff may attach or remove another buff while it ticks, and `removeIf` walks the live list
        // -- a ConcurrentModificationException raised from the middle of a turn boundary, or a silently
        // skipped buff. Removal is stated explicitly here instead, which is the same thing `removeIf` did.
        for (AbstractBuff buff : List.copyOf(buffs)) {
            if (buff.isPermanent()) {
                // "For the rest of the battle": no turn limit, so it is never counted down and never
                // expires. Skipping the tick (rather than using a huge duration) is what makes the
                // duration exact instead of merely long; the buff leaves only via an explicit removal.
                continue;
            }
            if (buff.isEarlyBuff != early) {
                continue;
            }
            boolean couldAct = buff.canAct();
            buff.tickEffect(instance);
            if (buff.duration() <= 0) {
                // `remove` by identity: AbstractBuff does not override equals, and a buff that already
                // removed itself during the tick simply is not there any more.
                buffs.remove(buff);
                buff.removeBuff(instance);
                if (!couldAct) {
                    blocked = true;
                }
            }
        }
    }

    /**
     * Whether a certain kind of buff is on us (needed by P4-6 / P8-7 / P10-2 alike).
     *
     * <p>Why "ask about a kind" instead of "hand the list out" (the P1-7 design decision):
     * iteration and matching stay inside the manager, so the outside cannot get a mutable list,
     * and therefore there is no opening for "a caller mutating the list causing
     * {@code ConcurrentModificationException}".
     *
     * <p>Matches exactly by {@code getClass()}, the same convention as
     * {@link AbstractBuff#isSameKind(AbstractBuff)} — a subclass does not count as its parent
     * (if you need "does it have some bloodline", pass the parent yourself and use a new method
     * with {@code isInstance} semantics; do not quietly loosen it here).
     *
     * @param kind the buff type to query
     * @return {@code true} = a buff of this kind is on us
     */
    public boolean hasBuff(Class<? extends AbstractBuff> kind) {
        if (kind == null) {
            return false;
        }
        for (AbstractBuff buff : List.copyOf(buffs)) {
            if (buff.getClass() == kind) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a <b>named</b> state (【协奏】/【转魄】/【增幅】…) is currently attached.
     *
     * <p>Same family as {@link #hasBuff(Class)}, with one difference that is the whole point of states:
     * the caller keys by the state's <b>name</b>, not by its class. A state is data ({@link StateBuff}
     * carries a name from the rule file), so a rule that says 「处于【协奏】状态时」 has no class to name.
     *
     * <p>Traversal goes through {@link #allBuffsOf(Class)}, i.e. the same snapshot copy as every other
     * read here (M-12), so a state that removes or attaches another state while being queried cannot
     * disturb the iteration.
     *
     * @param state the state name as the data spells it (trimmed; blank or {@code null} = never present)
     * @return {@code true} = a {@link StateBuff} with that name is on us
     */
    public boolean hasState(String state) {
        if (state == null || state.isBlank()) {
            return false;
        }
        String wanted = state.trim();
        for (StateBuff buff : allBuffsOf(StateBuff.class)) {
            if (wanted.equals(buff.getState())) {
                return true;
            }
        }
        // Engine states: the four names below are not StateBuff names — they are the DoT elements, which the
        // engine has represented as an ordinary DotBuff since P10-0. Resolving them here is what makes
        // 「对处于灼烧状态的目标造成的伤害提高」 (and 「触电状态下的敌方目标被消灭时」) expressible without
        // inventing a second fact for "this unit is burning".
        //
        // ⚠ Controls (冻结 / 纠缠 / 禁锢) are deliberately NOT in this table yet: P10-2 models them as a
        // combination of a control buff and an action delay, so "is this unit frozen" needs its own definition
        // rather than a guess that would half-work. Adding them later changes no JSON.
        DamageElement dotElement = DOT_STATES.get(wanted);
        if (dotElement == null) {
            return false;
        }
        for (DotBuff dot : allBuffsOf(DotBuff.class)) {
            if (dot.getElement() == dotElement) {
                return true;
            }
        }
        return false;
    }

    /**
     * The game's own names for the four damage-over-time states, and the buff fact behind each.
     *
     * <p>Kept here rather than in a data file because it is a <b>translation</b>, not a table of numbers: the
     * rule texts say 「灼烧」/「触电」/「裂伤」/「风化」 and the engine says {@code DotBuff(element)}. It is the
     * only place that knows the two spellings are the same thing.
     */
    private static final Map<String, DamageElement> DOT_STATES = Map.of(
            "灼烧", DamageElement.FIRE,
            "触电", DamageElement.THUNDER,
            "裂伤", DamageElement.PHYSICAL,
            "风化", DamageElement.WIND);

    /**
     * Takes the first buff of that type on us, or {@code null} if there is none (needed since P5-2:
     * the target selector must obtain **the taunter itself**, merely knowing "whether there is one"
     * is not enough).
     *
     * <p>It is a strict superset of {@link #hasBuff(Class)} ({@code findBuff(X) != null} means "there is one").
     * It still **does not expose {@code getBuffs()}**: keeping iteration inside the manager is the P1-7
     * decision, and handing out the mutable list would add one more opening for
     * {@code ConcurrentModificationException}.
     *
     * @param kind the buff type to query
     * @param <T>  the buff type
     * @return the first buff of that type, or {@code null} if there is none
     */
    public <T extends AbstractBuff> T findBuff(Class<T> kind) {
        if (kind == null) {
            return null;
        }
        for (AbstractBuff buff : List.copyOf(buffs)) {
            if (buff.getClass() == kind) {
                return kind.cast(buff);
            }
        }
        return null;
    }

    /**
     * <b>A snapshot</b> of every buff of the given class, in <b>attachment order</b> (oldest first).
     *
     * <p><b>Why a snapshot and not the list.</b> The P1-7 decision is that {@code getBuffs()} does not
     * exist, because a caller holding the live list can mutate it (or trip over a
     * {@code ConcurrentModificationException}). A fresh copy keeps that guarantee — the caller may
     * remove or add buffs while iterating without disturbing the manager — while still answering the
     * one question {@link #findBuff(Class)} cannot: "all of them, in what order".
     *
     * <p>That question is why this exists at all. {@code Battle.tickDots(CanHit)} has to settle every
     * DOT on a unit, and the engine's documented rule is <b>"first applied, first settled"</b>
     * (HSR.md §7) — so order is part of the contract, not an implementation detail, and it is the
     * order {@code buffs} already keeps.
     *
     * <p>Matching is by exact class, the same convention as {@link #hasBuff(Class)} / {@link
     * #countBuffs(Class)} / {@link #findBuff(Class)} (a subclass does not count as its parent).
     *
     * @param kind the buff type to collect
     * @param <T>  the buff type
     * @return a new list of the attached instances, oldest first; empty for {@code null}
     */
    public <T extends AbstractBuff> List<T> allBuffsOf(Class<T> kind) {
        if (kind == null) {
            return List.of();
        }
        List<T> found = new ArrayList<>();
        for (AbstractBuff buff : List.copyOf(buffs)) {
            if (buff.getClass() == kind) {
                found.add(kind.cast(buff));
            }
        }
        return found;
    }

    public void clearAll() {
        for (AbstractBuff buff : List.copyOf(buffs)) {
            buff.removeBuff(instance);
        }
        buffs.clear();
        // M-5: also drop the `blocked` flag. It is set when a control buff expires on the very turn it was
        // blocking, so clearing every buff without clearing it leaves a unit that can never act again --
        // and dispelling it is exactly what a caller would try next.
        blocked = false;
    }

    public boolean isEmpty() {
        return buffs.isEmpty();
    }
}
