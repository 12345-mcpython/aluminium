package com.laosun.aluminium.models.buff;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.event.AttackEvent;
import com.laosun.aluminium.models.event.SkillCastEvent;
import com.laosun.aluminium.models.skill.Skill;
import lombok.Getter;
import lombok.Setter;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractBuff implements Buff, AttackEvent, SkillCastEvent {
    protected CanHit source;
    protected int remainingDuration;
    protected final int id;
    protected final boolean isEarlyBuff;

    /**
     * Whether this buff has <b>no turn limit</b> ("for the rest of the battle").
     *
     * <p><b>Why a flag and not a huge number.</b> {@code remainingDuration} is an {@code int} the
     * manager decrements once per turn ({@code processBuffTick}), so "forever" spelled as
     * {@code Integer.MAX_VALUE} would be a magic number that still silently counts down, and any
     * other large constant would just move the same lie. A permanent buff is instead <b>never
     * ticked</b>: {@code BuffManager.processBuffTick} skips it, so the duration cannot drift and the
     * buff disappears only when it is removed explicitly (dispel / death / {@code clearAll}).
     *
     * <p>Default {@code false}: every existing buff keeps its turn count and keeps expiring.
     */
    @Getter
    protected final boolean permanent;

    /**
     * Whose <b>turn boundaries</b> count this buff's duration down; {@code null} = the unit that carries it
     * (M-42 ④).
     *
     * <p><b>Why the clock is not always the carrier.</b> 星期日's 【蒙福者】 says 「星期日自身每回合开始时
     * 【蒙福者】状态持续回合减1」 — the state sits on the ally, but its duration is spent by <b>his</b> turns.
     * Modelling that as an ordinary timed buff on the ally would count it down on the <i>ally's</i> turns: a
     * different number of turns in every real fight, with nothing to see. So the anchor is stated, and
     * {@code Battle} sweeps the other units for buffs anchored to whoever is taking their turn.
     *
     * <p>⚠ {@code null} is the default and must stay the ordinary case: every existing buff ticks on its carrier.
     * ⚠ An anchored buff outlives its carrier's usefulness if the anchor dies, so the anchor's death removes it
     * ({@code Battle.removeDeadCombatants} → {@link BuffManager#removeBuffsAnchoredTo(CanHit)}); without that a
     * buff whose clock never comes again would sit there for the rest of the battle.
     */
    private CanHit tickOwner;

    /**
     * Whether {@code who}'s turn boundary ticks this buff.
     *
     * @param who the unit whose turn is beginning or ending
     * @return {@code true} = this buff's duration is spent by that unit's turns
     */
    public boolean ticksOn(CanHit who) {
        CanHit clock = tickOwner == null ? owner : tickOwner;
        return clock != null && clock == who;
    }

    /**
     * Anchors this buff's duration to {@code clockOwner}'s turns instead of its carrier's.
     *
     * @param clockOwner the unit whose turns count it down
     */
    public void setTickOwner(CanHit clockOwner) {
        this.tickOwner = clockOwner;
    }

    /**
     * Who this buff is currently attached to (written by {@link BuffManager#addBuff(AbstractBuff)} at
     * attach time).
     *
     * <p>Why it exists: {@code Battle.assemble} broadcasts {@code DamageEvent} to **both the attacker and
     * the target**, and buffs like "vulnerability (易伤, a debuff on the target)" / "reduction (减伤, a buff
     * on the target)" that only inject a damage zone during settlement MUST know whether they are the target
     * of this instance — otherwise vulnerability attached to an enemy would also boost the damage when that
     * enemy itself attacks.
     *
     * <p>It is not the same thing as {@link #source} (the applier), do not confuse them: {@code source} is
     * who applied it, {@code owner} is who it is attached to.
     */
    @Setter
    protected CanHit owner;


    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(1);

    public AbstractBuff(int duration, boolean isEarlyBuff) {
        this(duration, isEarlyBuff, false);
    }

    /**
     * @param duration    turns until expiry; ignored when {@code permanent} is {@code true}
     * @param isEarlyBuff {@code true} = tick on {@code beforeMove}, {@code false} = on {@code afterMove}
     * @param permanent   {@code true} = the buff has no turn limit and is never ticked
     */
    public AbstractBuff(int duration, boolean isEarlyBuff, boolean permanent) {
        this.remainingDuration = duration;
        this.isEarlyBuff = isEarlyBuff;
        this.permanent = permanent;
        this.id = ID_GENERATOR.getAndIncrement();
    }

    /**
     * What ends this buff, when it is not a number of turns: 「持续到施放首次攻击后结束」 and friends.
     *
     * <p><b>Why this is on the base class.</b> A lifetime is a property of "how long does this last", which
     * every buff already has an opinion about ({@code remainingDuration} / {@code permanent}) — and the three
     * buff classes a rule can create ({@code StatModifierBuff}, {@code StateBuff}, and the damage-taken pair)
     * all need the same answer. Putting it here means the behaviour is implemented once and any future buff
     * can carry one by setting the field, rather than each class growing its own copy of "remove myself when
     * the owner attacks".
     *
     * <p>⚠ The default is an <b>empty set</b>: every existing buff keeps expiring exactly as it did, and a
     * buff only behaves differently when a rule says {@code "until": …}.
     */
    public enum Lifetime {
        /**
         * Ends after its owner finishes an attack that landed — a basic attack, a Skill or an Ultimate.
         */
        NEXT_ATTACK,
        /**
         * Ends after its owner casts a Skill.
         */
        NEXT_SKILL,
        /**
         * Ends after its owner casts an Ultimate.
         */
        NEXT_ULTIMATE
    }

    /**
     * The events that end this buff; <b>empty</b> means "no event-based end". Written by whoever creates the
     * buff (the trigger interpreter, from the rule's {@code "until": …}).
     *
     * <p><b>Why a set.</b> The game states durations as disjunctions — 「持续至装备者下次施放普攻<b>或</b>战技后」
     * (relic set 127) — and the buff ends at the <b>first</b> of the named events: a duration is one fact, however
     * many events can end it. The alternative shapes are both wrong, and both look like they work: naming one of
     * the two events makes a 战技 silently not end a buff that its text says it ends, and creating two buffs (one
     * per event) makes them <b>replace each other</b> on the same kind + target, so only the later one is ever up.
     */
    @Getter
    protected Set<Lifetime> lifetimes = EnumSet.noneOf(Lifetime.class);

    /**
     * Replaces the ending events with the ones a rule named; an empty collection means "no event-based end",
     * which is what a buff that states no {@code "until"} gets.
     */
    public void setLifetimes(Collection<Lifetime> lifetimes) {
        this.lifetimes.clear();
        if (lifetimes != null) {
            this.lifetimes.addAll(lifetimes);
        }
    }

    /**
     * Ends this buff when its <b>owner</b> finishes an attack.
     *
     * <p>The owner test is what keeps it to the right unit: the engine broadcasts "an attack happened" to
     * every member of our side ({@code AttackEvent} is how Robin's and Tribbie's third-party kits hear about
     * it), so without it a teammate's attack would consume somebody else's "for the next attack" buff.
     */
    @Override
    public void afterAttack(Battle battle, CanHit attacker, CanHit mainTarget,
                            List<? extends CanHit> hitTargets, double totalDamage) {
        if (lifetimes.contains(Lifetime.NEXT_ATTACK) && attacker != null && attacker == owner) {
            endNow();
        }
    }

    /**
     * Ends this buff when its <b>owner</b> casts the kind of skill one of its lifetimes names.
     *
     * <p>Which cast this is comes from the parsed skill data ({@code SkillCategory}), the same source the
     * trigger emitters use — never from a skill's name or slot. The notification arrives <b>before</b> the cast
     * trigger events, so a buff that a rule grants on this very cast is not ended by it.
     */
    @Override
    public void onSkillCast(Battle battle, CanHit user, Skill skill,
                            List<? extends CanHit> hitTargets, List<? extends CanHit> targets) {
        if (user == null || user != owner) {
            return;
        }
        SkillCategory category = skill == null || skill.getData() == null
                ? null
                : skill.getData().getCategory();
        if ((lifetimes.contains(Lifetime.NEXT_SKILL) && category == SkillCategory.BPSKILL)
                || (lifetimes.contains(Lifetime.NEXT_ULTIMATE) && category == SkillCategory.ULTRA)) {
            endNow();
        }
    }

    /**
     * Takes this buff off its owner through the owner's manager, so the modifier is dropped by id exactly the
     * way an expiry does it.
     *
     * <p>Safe to call while the manager is iterating its buffs: every traversal goes through
     * {@code List.copyOf} (M-12), which is what makes "a buff that removes itself on an event" possible at all.
     */
    private void endNow() {
        if (owner != null) {
            owner.getBuffManager().removeBuff(this);
        }
    }

    @Override
    public void setSource(CanHit h) {
        source = h;
    }

    @Override
    public CanHit getSource() {
        return source;
    }

    @Override
    public int duration() {
        return remainingDuration;
    }

    @Override
    public abstract boolean canAct();

    /**
     * Whether this buff is a <b>negative effect</b> (负面效果) — the thing 「解除 N 个负面效果」 removes and
     * 「目标身上有几个负面」 counts.
     *
     * <p><b>Where the classification lives, and why not abstract.</b> The default is {@code false}, and the
     * debuffs override it: the five of them are pinned together in {@code DebuffTest}'s table, so the set is
     * decided in one readable place instead of being implied by whichever classes happen to override a method.
     * The trade is deliberate — a new buff class starts as "not a debuff", which is the safe direction (it can
     * never be dispelled by accident), and it gets classified on purpose when its content arrives, exactly like
     * a relic ability is either authored or registered.
     *
     * <p>It answers a <b>game-semantics</b> question rather than a mechanical one, which is why a few answers
     * look surprising until you read the text: 「减伤」 is a <i>positive</i> effect even though it sits on the
     * defender ({@link ReductionBuff}), 「受到伤害提高」 is negative ({@link VulnerabilityBuff}), and a
     * {@link StatModifierBuff} answers by its {@code sourceRole} — the same sign that decided whether it landed
     * in the buff or the debuff half of the attribute.
     */
    public boolean isDebuff() {
        return false;
    }

    @Override
    public abstract void applyEffect(CanHit target);

    @Override
    public abstract void removeBuff(CanHit target);

    @Override
    public abstract void tickEffect(CanHit target);

    public boolean isSameKind(AbstractBuff other) {
        return this.getClass() == other.getClass();
    }

    /**
     * Whether this buff may accumulate alongside another instance of the same kind.
     *
     * <p>Default {@code false}: {@link BuffManager#addBuff} removes every same-kind buff first, which is
     * the engine's long-standing "casting the same buff again refreshes it" rule
     * ({@code BuffManagerTest.sameKindBuffRefreshesInsteadOfStacking}). A buff that really accumulates
     * overrides this and declares its cap through {@link #maxStacks()}.
     */
    public boolean isStackable() {
        return false;
    }

    /**
     * How many instances of this buff may be attached at once. Meaningful only when
     * {@link #isStackable()} is {@code true}; {@code 1} means "replace, do not stack".
     */
    public int maxStacks() {
        return 1;
    }

    /**
     * The key that decides which buffs are <b>stacks of one another</b>.
     *
     * <p>Deliberately separate from {@link #isSameKind}: "same kind" answers "should this replace
     * that?" (a behaviour the existing tests pin), while "same stack group" answers "should this
     * accumulate with that?". Folding them into one predicate would force every stackable buff to
     * also change its replace semantics.
     *
     * @return the group key; {@code null} (the default) means "never a stack sibling"
     */
    public Object stackGroupKey() {
        return null;
    }

    protected void decreaseDuration() {
        remainingDuration--;
    }
}