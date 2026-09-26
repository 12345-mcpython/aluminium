package com.laosun.aluminium.models.buff;

import com.laosun.aluminium.models.CanHit;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractBuff implements Buff {
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