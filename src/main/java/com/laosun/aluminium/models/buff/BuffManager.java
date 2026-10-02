package com.laosun.aluminium.models.buff;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
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
                removeBuff(existed);
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
                removeBuff(buff);
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
     * How much this unit resists one <b>class</b> of negative state (「抵抗控制类负面状态的概率提高35%」).
     *
     * <p>The sum of every {@link ClassResistBuff} of that class, clamped to 1: two sources <b>add</b>, because they
     * are two boosts to one probability rather than two independent rolls. {@code 1.0} means immunity — the same
     * mechanism at its limit, which is how 「免疫控制类负面状态」 and 「抵抗…的概率提高35%」 end up sharing one vocabulary.
     *
     * <p>⚠ A {@code null} class (a state nobody classified) answers {@code 0}: it is not protected by any class
     * resistance, which is the safe direction for content.
     *
     * @param kind the family being applied, or {@code null}
     * @return the resistance fraction in {@code [0, 1]}
     */
    public double debuffResistOf(com.laosun.aluminium.enums.DebuffClass kind) {
        if (kind == null) {
            return 0;
        }
        double total = 0;
        for (ClassResistBuff resist : allBuffsOf(ClassResistBuff.class)) {
            if (resist.getKind() == kind) {
                total += resist.getPercent();
            }
        }
        return Math.min(1, total);
    }

    /**
     * Adds turns to every <b>timed</b> buff on this unit that {@code source} applied <b>and that the rule named</b>,
     * and reports how many.
     *
     * <p><b>Why the two filters.</b> Every sentence in this family identifies the buff by its <b>origin</b> —
     * 「<b>战技提供的</b>护盾持续时间增加1回合」 (三月七 加护) — and then by <b>what it is</b>: 「战技对指定我方目标造成的
     * <b>伤害提高效果</b>的持续时间增加1回合」 (布洛妮娅 星魂 6), 「<b>天赋使敌方目标陷入的</b>风化状态的持续时间延长1回合」
     * (桑博), 「对于已拥有【<b>生息</b>】的我方目标…延长1回合」 (白露). The origin is exact ({@code AbstractBuff.source});
     * the "what" is a <b>name</b> in the same vocabulary the condition DSL already reads — a {@code StateBuff}'s own
     * name, a DOT's element name (灼烧), a control's name (冻结), or {@link #SHIELD_STATE} for a shield — or, when
     * the sentence names no state at all but an <i>effect</i> (「伤害提高效果」), the <b>attribute</b> the modifier sits
     * on.
     *
     * <p>⚠ <b>Both filters are required</b>, and "everything of mine on that unit" is deliberately not a spelling:
     * 布洛妮娅's DEFENCE trace buff from {@code BATTLE_START} can still be ticking when she casts her Skill, and a
     * filter that said "all of mine" would silently lengthen that too — a wrong number with nothing to report,
     * which is exactly what the name/attribute axis exists to prevent.
     *
     * <p>⚠ <b>Permanent buffs are skipped</b> ({@code permanent} means "never ticked", so there is no countdown to
     * lengthen), and so are event-bound ones, which the interpreter builds as permanent. Nothing to lengthen is
     * <b>not</b> an error — the common shape fires on the same cast that applied the buff, and "it is not there"
     * is an ordinary empty case, the same reading {@code DISPEL} has.
     *
     * <p>⚠ Iterating over a snapshot (M-12), like every other traversal here.
     *
     * @param source    who must have applied the buff (usually the rule's owner)
     * @param stateName a state's name (or {@link #SHIELD_STATE}), or {@code null} when the filter is by attribute
     * @param attribute the attribute the modifier sits on, or {@code null} when the filter is by name
     * @param turns     how many turns to add
     * @return how many buffs were lengthened ({@code 0} when none matched)
     */
    public int extendBuffsFrom(CanHit source, String stateName, AttributeType attribute, int turns) {
        if (source == null || turns <= 0 || (stateName == null && attribute == null)) {
            return 0;
        }
        int extended = 0;
        for (AbstractBuff buff : List.copyOf(buffs)) {
            if (buff.isPermanent() || buff.getSource() != source || !isNamed(buff, stateName, attribute)) {
                continue;
            }
            buff.extendDuration(turns);
            extended++;
        }
        return extended;
    }

    /**
     * The name a rule uses for a shield ({@code "buff": "护盾"}).
     *
     * <p>It is <b>not</b> part of {@link #hasState}'s vocabulary, deliberately: a shield is a <i>scalar</i> on the
     * combatant, and a raw grant ({@code Battle.grantShield}) leaves no buff behind — so 「有盾」 and
     * 「处于护盾状态」 would answer differently in exactly the case where the difference is invisible. The name
     * lives here, where it means "the timed shield buff", and the condition DSL keeps its own spelling
     * ({@code has_shield}).
     */
    public static final String SHIELD_STATE = "护盾";

    /**
     * Whether one buff is the one a rule named — by state name (or 护盾), or by the attribute a modifier sits on.
     *
     * <p>The name vocabulary is the same one the documents and {@code has_state} use, and the translation from a
     * DOT's element to its name ({@code FIRE} → 灼烧) is {@link #DOT_STATES}, reversed here so the two directions
     * cannot drift.
     */
    private static boolean isNamed(AbstractBuff buff, String stateName, AttributeType attribute) {
        if (attribute != null) {
            return buff instanceof StatModifierBuff modifier && modifier.getAttribute() == attribute;
        }
        if (buff instanceof ShieldBuff) {
            return SHIELD_STATE.equals(stateName);
        }
        if (buff instanceof ControlBuff control) {
            return stateName.equals(control.getName());
        }
        if (buff instanceof DotBuff dot) {
            return stateName.equals(stateNameOf(dot.getElement()));
        }
        if (buff instanceof StateBuff state) {
            return stateName.equals(state.getState());
        }
        return false;
    }

    /**
     * The document's name for a DOT's element ({@code FIRE} → 灼烧), or {@code null} for an element no document
     * names as a state.
     */
    private static String stateNameOf(DamageElement element) {
        for (Map.Entry<String, DamageElement> entry : DOT_STATES.entrySet()) {
            if (entry.getValue() == element) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * The same lookup, for callers outside this class.
     *
     * <p><b>Who needs it.</b> The trigger interpreter records what a cast actually applied
     * ({@code Battle.recordCastApplied}) when it attaches a DOT, and a rule that counts those applications
     * (「每使1个目标陷入灼烧」) has to spell the name the documents use. Both sides therefore ask this one table rather
     * than keeping a second element→name map that could drift from it.
     *
     * @param element the DOT's element, or {@code null}
     * @return 灼烧 / 触电 / 裂伤 / 风化, or {@code null}
     */
    /** The document's name for 「韧性被削减至 0」 (see {@link #hasState}). */
    public static final String BROKEN_STATE = "弱点击破";

    public static String dotStateName(DamageElement element) {
        return stateNameOf(element);
    }

    /**
     * Whether {@code stateName} is a name the engine's state tables know as a <b>rolled</b> state.
     *
     * <p>Used to validate {@code "scale": "cast_applied:<状态名>"} at load time: the closed set is the control states
     * (冻结 / 纠缠 / 禁锢) and the four DOT states (灼烧 / 触电 / 裂伤 / 风化) — the states that reach the field
     * through {@code Battle.tryApplyDebuff}, which is what the counter counts. ⚠ 嘲讽 is deliberately absent: no
     * document counts taunts, and whether the marker belongs to the control class is still an open decision (see
     * ROADMAP).
     *
     * @param stateName the name to check (a document spelling)
     * @return {@code true} when it names a state this engine rolls for
     */
    public static boolean isRolledStateName(String stateName) {
        if (stateName == null || stateName.isBlank()) {
            return false;
        }
        return Constant.CONTROL_STATES.containsKey(stateName) || DOT_STATES.containsKey(stateName);
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
        // \u2605 The carrier's OWN turn just ended (2026-09-30): values scoped to \u300c\u540c\u4e00\u56de\u5408\u5185\u300d go away here. This runs beside the
        // duration tick, which is the same "this unit's turn is over" moment -- one place, not two.
        removeWithLifetime(AbstractBuff.Lifetime.TURN_END);
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
        tickBuff(instance, early);
    }

    /**
     * Spends the duration of the buffs on this unit whose <b>clock</b> belongs to {@code clockOwner} (M-42 ④).
     *
     * <p>Called by {@code Battle} at somebody else's turn boundary, for every other unit on the field: 星期日's
     * 【蒙福者】 lives on the ally and is spent by <b>his</b> turns. Our own boundary is skipped — that one has
     * already gone through {@link #beforeMove()} / {@link #afterMove()}.
     *
     * @param clockOwner the unit whose turn boundary this is
     * @param early      {@code true} = the "before the move" half, {@code false} = the "after the move" half
     */
    public void tickForeign(CanHit clockOwner, boolean early) {
        if (clockOwner == null || clockOwner == instance) {
            return;
        }
        tickBuff(clockOwner, early);
    }

    /**
     * Removes every buff on this unit whose clock belongs to {@code clockOwner} — the anchor's death (M-42 ③).
     *
     * <p>⚠ Without this, an anchored buff is a <b>leak</b>: its clock was somebody else's turns, and that somebody
     * will never take another one. 星期日's 【蒙福者】 says it outright (「当星期日陷入无法战斗状态时，【蒙福者】效果
     * 也会被解除」), and the generic reason is stronger than the sentence — "spend it on my turns" is meaningless
     * once I am gone.
     *
     * @param clockOwner the unit that just died
     * @return how many buffs were removed
     */
    public int removeBuffsAnchoredTo(CanHit clockOwner) {
        if (clockOwner == null) {
            return 0;
        }
        int removed = 0;
        for (AbstractBuff buff : List.copyOf(buffs)) {
            if (buff.ticksOn(clockOwner) && clockOwner != instance) {
                if (buffs.remove(buff)) {
                    buff.removeBuff(instance);
                    removed++;
                }
            }
        }
        return removed;
    }

    /**
     * The tick itself, for the buffs on this manager whose clock is {@code clockOwner}.
     *
     * <p>⚠ Iterated over a snapshot, not with {@code removeIf} (M-12). {@code tickEffect} and {@code removeBuff}
     * are buff code: a buff may attach or remove another buff while it ticks, and {@code removeIf} walks the live
     * list — a {@code ConcurrentModificationException} raised from the middle of a turn boundary, or a silently
     * skipped buff. Removal is stated explicitly here instead, which is the same thing {@code removeIf} did.
     */
    private void tickBuff(CanHit clockOwner, boolean early) {
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
            if (!buff.ticksOn(clockOwner)) {
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
        // Named buffs (2026-09-30): an effect *of* a state carries the state's own name, so 「处于结界中时」 must
        // answer yes for the modifier that carries it -- the zone is a MODIFY_ATTR with `buff: "结界"`, not a
        // StateBuff. ⚠ Over the manager's own list rather than `allBuffsOf`: that helper compares classes EXACTLY,
        // and a modifier may be a SUBCLASS. ⚠ Unnamed buffs are skipped, which is what keeps this loop from touching
        // anything that existed before the field did -- and that is why widening this read is safe for every
        // existing rule that gates on a name nothing carries.
        for (AbstractBuff named : List.copyOf(buffs)) {
            if (!named.getBuffName().isEmpty() && wanted.equals(named.getBuffName())) {
                return true;
            }
        }
        // Engine states: the names below are not StateBuff names -- they are states the engine represents with
        // a buff of its own. Resolving them here is what makes 「对处于灼烧状态的目标造成的伤害提高」 and
        // 「冻结状态下的敌方目标」 expressible without inventing a second fact for "this unit is burning/frozen".
        //
        // ⚠ Controls joined this table on 2026-09-27, when ControlBuff gave them a name. Before that the comment
        // here said they were "deliberately NOT in this table yet ... adding them later changes no JSON" -- and
        // that is exactly what happened: the break path and a skill-applied control now produce the SAME buff, so
        // 「冻结」 answers the same thing whichever one froze the unit (a break-frozen enemy used to have no name
        // at all, so a rule gated on 「冻结状态」 would have silently missed it).
        for (ControlBuff control : allBuffsOf(ControlBuff.class)) {
            if (wanted.equals(control.getName())) {
                return true;
            }
        }
        // 「弱点击破状态」 (2026-09-28, corpus 58 hits / 15 files): the state a unit is in while its toughness bar is
        // empty. It is a fact about the ENEMY rather than a buff anyone applied, so it belongs in this engine-state table
        // -- the same reason 「灼烧」 and 「冻结」 are resolved here.
        if (BROKEN_STATE.equals(wanted)) {
            return instance instanceof com.laosun.aluminium.models.enemy.Enemy enemy && enemy.isBroken();
        }
        // ★ A named DOT answers to its NAME too (2026-09-30): the element table below only knows 灼烧/触电/裂伤/风化,
        // while the corpus also asks about 【游丝】 by name. A named THUNDER DOT therefore answers BOTH 游丝 and 触电 -- which is
        // exactly the sentence 「【游丝】状态下也被视为陷入了触电状态」.
        for (DotBuff dot : allBuffsOf(DotBuff.class)) {
            if (wanted.equals(dot.getBuffName())) {
                return true;
            }
        }
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
     * Removes every buff that {@link #hasState(String)} would report for that name — the other half of the pair.
     *
     * <p><b>Why a "remove the named state" primitive.</b> 「仅对…<b>最新的</b>施放目标生效」 is a very common sentence
     * (星期日's 【蒙福者】), and the engine had no way to say "take that state off the others": {@link #removeDebuffs}
     * only reaches <b>negative</b> buffs, and a named state is usually a positive one. Resolving the name here also
     * means the DoT spellings behave the same on this side as they do on the {@code has_state} side (removing 「触电」
     * takes the thunder DoT off), which is the "one name, one meaning" rule that method documents.
     *
     * <p>The traversal and the removal both go through the snapshot copy (M-12), exactly like {@link #removeDebuffs},
     * so a buff that reacts to being removed cannot disturb the iteration.
     *
     * @param state the state name as the data spells it (trimmed; blank or {@code null} = nothing to do)
     * @return how many buffs were removed; {@code 0} is the normal answer for "it was not there", not an error
     */
    /**
     * Whether any buff on this unit carries {@code suspendsTurns} — 「不会进入自己的回合」 (see
     * {@link AbstractBuff#isSuspendsTurns()}).
     *
     * <p>Asked by {@code Battle.beforeMove}, which is the only place a turn can be let pass without the unit.
     */
    /**
     * Takes up to {@code max} layers off the buffs carrying one <b>name</b> — the named twin of
     * {@link #removeStacks(AttributeType, int)} (2026-09-28).
     *
     * <p>「每次我方目标回合结束时，移除驭空 1 层【鸣弦号令】」 is why: 【鸣弦号令】 is a <b>stackable named modifier</b>, and
     * neither existing spelling could take one layer off it — {@code REMOVE_STATE <name>} removes the named buffs
     * <i>entirely</i> (by design: a state is on or off), and {@code REMOVE_STACK} works by <b>attribute</b>. The name
     * filter is the same one {@code stacksOf} and {@code extendBuffsFrom} use, so 「按名字」 means one thing in this class.
     *
     * <p>⚠ Removing nothing is not an error, for the same reason {@code REMOVE_STACK} gives: the sentence is "at the end
     * of every ally's turn, remove 1 layer", and it fires on turns where there is none left.
     *
     * @param name the name as the data spells it ({@code null}/blank = nothing)
     * @param max  how many layers to take off at most
     * @return how many were actually removed
     */
    public int removeNamedStacks(String name, int max) {
        if (name == null || name.isBlank() || max <= 0) {
            return 0;
        }
        String wanted = name.trim();
        int removed = 0;
        for (AbstractBuff buff : List.copyOf(buffs)) {
            if (removed >= max) {
                break;
            }
            if (wanted.equals(buff.getBuffName())) {
                removeBuff(buff);
                removed++;
            }
        }
        return removed;
    }

    /**
     * Removes up to {@code max} <b>positive, temporary</b> buffs — 「解除敌方 N 个增益效果」 (2026-09-28).
     *
     * <p>It is the mirror of the {@code DISPEL} op, and the direction is the whole point: that one cleans <i>our own</i>
     * side of negative effects, while this one strips an <b>enemy's</b> benefits. The filter is a decision taken per buff
     * class ({@code isDebuff()}), the same one {@code DISPEL} relies on, so neither can be tricked into removing the wrong
     * half.
     *
     * <p>⚠ <b>Permanent buffs are skipped on purpose.</b> A relic's bonus or a trace is not a 「增益效果」 in the sense the
     * sentences use — nobody's skill dispels a loadout — and removing one would be a wrong number with no symptom.
     *
     * <p>Removing nothing is not an error (the rule fires on every cast, and most casts find nothing to strip).
     *
     * @param max how many to remove at most
     * @return how many were actually removed
     */
    public int removeBuffs(int max) {
        if (max <= 0) {
            return 0;
        }
        int removed = 0;
        for (AbstractBuff buff : List.copyOf(buffs)) {
            if (removed >= max) {
                break;
            }
            if (!buff.isDebuff() && !buff.isPermanent()) {
                removeBuff(buff);
                removed++;
            }
        }
        return removed;
    }

    /**
     * The attacker's total toughness-reduction bonus (「削韧值提高 X%」), summed over the boosts it carries.
     *
     * <p>Read at exactly one place, {@code SkillExecutor.applyStanceDamage}, where a nominal reduction becomes a settled
     * one. ⚠ Summed rather than taken as the largest: two sentences that both say 「提高」 stack on each other, which is
     * why {@code ToughnessBoostBuff.isSameKind} says two of them are never "the same buff".
     */
    public double toughnessBoost() {
        double total = 0;
        for (AbstractBuff buff : List.copyOf(buffs)) {
            if (buff instanceof com.laosun.aluminium.models.buff.ToughnessBoostBuff boost) {
                total += boost.getPercent();
            }
        }
        return total;
    }

    /**
     * How many buffs on this unit carry the given <b>name</b> — 「已经累计了几次」 (2026-09-28).
     *
     * <p>⚠ A counter is <b>several buffs with one name</b>, not one buff with a count field: that is the engine's
     * existing stacking ( {@code BuffManagerTest} pins "same kind refreshes, opt-in stacking accumulates"), and it is
     * what makes {@code REMOVE_STACK} (take one off) and {@code REMOVE_STATE <name>} (clear the lot) both work on a
     * counter without either op learning anything new.
     *
     * @param name the name as the data spells it ({@code null}/blank = 0)
     * @return how many named buffs are carried
     */
    public int stacksOf(String name) {
        if (name == null || name.isBlank()) {
            return 0;
        }
        String wanted = name.trim();
        int count = 0;
        for (AbstractBuff buff : List.copyOf(buffs)) {
            if (wanted.equals(buff.getBuffName()) || (buff instanceof StackBuff stack
                    && wanted.equals(stack.getBuffName()))) {
                count++;
            }
        }
        return count;
    }

    /**
     * Removes every buff whose lifetime names {@code lifetime} -- the cast-scoped one is the current reader.
     *
     * <p>\u26a0 Over the manager's own list, not {@code allBuffsOf}: that helper compares classes exactly and a modifier may be
     * a subclass (the same trap {@link #suspendsTurns} documents).
     *
     * @param lifetime which end-of-event lifetime to drop
     * @return how many were removed
     */
    public int removeWithLifetime(AbstractBuff.Lifetime lifetime) {
        int removed = 0;
        for (AbstractBuff buff : List.copyOf(buffs)) {
            if (buff.getLifetimes().contains(lifetime)) {
                removeBuff(buff);
                removed++;
            }
        }
        return removed;
    }

    public boolean suspendsTurns() {
        // ⚠ Over the manager's own list, NOT `allBuffsOf`/`instanceof`: that helper compares classes exactly
        // (`buff.getClass() == kind`), and a modifier may be a *subclass* of StatModifierBuff -- a per-class scan
        // silently found nothing (measured: the party ATK boost survived 「退出【协奏】状态」 for exactly this reason).
        for (AbstractBuff buff : List.copyOf(buffs)) {
            if (buff.isSuspendsTurns()) {
                return true;
            }
        }
        return false;
    }

    public int removeState(String state) {
        if (state == null || state.isBlank()) {
            return 0;
        }
        String wanted = state.trim();
        int removed = 0;
        for (StateBuff buff : allBuffsOf(StateBuff.class)) {
            if (wanted.equals(buff.getState())) {
                removeBuff(buff);
                removed++;
            }
        }
        // Controls, like hasState: removing 「冻结」 takes the whole state off (its act lock, its slow and its
        // per-turn damage -- they are one ControlBuff, so they cannot come apart here).
        for (ControlBuff control : allBuffsOf(ControlBuff.class)) {
            if (wanted.equals(control.getName())) {
                removeBuff(control);
                removed++;
            }
        }

        // Named buffs (2026-09-28): an effect *of* a state carries the state's own name, so 「退出【协奏】状态」 takes the
        // state, its stat boost and its immunity off with one statement. ⚠ Unnamed buffs are skipped, which is what
        // keeps this loop from touching anything that existed before the field did. ⚠ Over the manager's own list
        // rather than `allBuffsOf`: that helper compares classes exactly, and a modifier may be a subclass.
        for (AbstractBuff named : List.copyOf(buffs)) {
            if (!named.getBuffName().isEmpty() && wanted.equals(named.getBuffName())) {
                removeBuff(named);
                removed++;
            }
        }
        DamageElement dotElement = DOT_STATES.get(wanted);
        if (dotElement == null) {
            return removed;
        }
        for (DotBuff dot : allBuffsOf(DotBuff.class)) {
            if (dot.getElement() == dotElement) {
                removeBuff(dot);
                removed++;
            }
        }
        return removed;
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
