package com.laosun.aluminium.models.buff;

/**
 * A <b>named counter</b>: a buff that carries no modifier at all and exists only to be <b>counted</b>.
 *
 * <p><b>The sentence that needs it.</b> Hanya's (寒鸦) Skill: "每当我方目标对[承负]状态下的敌方目标施放 <b>2</b> 次普攻、战技、终结技后，立即
 * 为我方恢复 1 个战技点；[承负] ...会在触发 <b>2</b> 次战技点恢复效果后自动解除" - two thresholds over two different things, and
 * neither is a turn count or a probability: they are "how many times has this happened so far".
 *
 * <p><b>Why a buff and not a new kind of state.</b> The engine's existing stacking already means "several instances that
 * accumulate and can be removed one at a time" ({@code StatModifierBuff} with {@code maxStacks}, pinned by
 * {@code BuffManagerTest}), and it already gives every instance a lifetime. All that was missing was (a) a carrier with
 * <b>no</b> stat attached - a counter must not change the panel - and (b) a way to <b>read</b> the count, which is the
 * {@code self_stacks:<name>} / {@code target_stacks:<name>} condition.
 *
 * <p>Note: Its display name is the key both of those use, so it is set by the {@code ADD_STACK} op
 * ({@code AbstractBuff.buffName}); {@code REMOVE_STATE <name>} clears the whole counter, and {@code REMOVE_STACK} takes
 * one off - the two directions the documents ask for ("触发 2 次后自动解除" vs "消耗 1 层").
 */
public class StackBuff extends AbstractBuff {

    /**
     * @param name what it is counting (the key {@code *_stacks:<name>} reads; never blank)
     * @param turns     how many of the owner's turns it lasts, at least 1
     * @param permanent {@code true} = lasts until the battle ends and is never counted down ({@code turns} is then
     *                  only a placeholder)
     * @throws IllegalArgumentException when the name is blank or the turn count is below 1
     */
    public StackBuff(String name, int turns, boolean permanent, int maxStacks) {
        super(turns, false, permanent);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("StackBuff needs a name: it is the key `*_stacks:<name>` reads");
        }
        setBuffName(name.trim());
        this.maxStacks = Math.max(1, maxStacks);
    }

    /** How many marks this counter accepts ({@code 1} = a plain flag) - read by {@code BuffManager.addStackable}. */
    private final int maxStacks;

    @Override
    public int maxStacks() {
        return maxStacks;
    }

    /** A counter that lasts {@code turns} of its owner's turns, capped at {@code maxStacks} marks. */
    public StackBuff(String name, int turns, int maxStacks) {
        this(name, turns, false, maxStacks);
    }

    /** A single-mark flag with the given lifetime. */
    public StackBuff(String name, int turns) {
        this(name, turns, false, 1);
    }

    /**
     * Note: <b>Stackable by construction, and that is the whole point.</b> The engine's rule is "the same kind refreshes
     * instead of stacking; stacking is opt-in" ({@code BuffManagerTest.sameKindBuffRefreshesInsteadOfStacking}), so a
     * counter whose instances were not stackable would silently stay at <b>1</b> however often it was marked - measured:
     * the first version of this class did exactly that, and the test that caught it is
     * {@code StackCounterTest.bothRemovalDirectionsWork}.
     */
    @Override
    public boolean isStackable() {
        return true;
    }

    /** Two marks of the same counter group together; a different name is a different counter. */
    @Override
    public Object stackGroupKey() {
        return "stack:" + getBuffName();
    }

    /** A counter never blocks anything: it is a number, not a state. */
    @Override
    public boolean canAct() {
        return true;
    }

    /** Note: Nothing happens on its owner's turn: a counter is only ever read (and removed) by the content that made it. */
    @Override
    public void tickEffect(com.laosun.aluminium.models.CanHit owner) {
        // deliberately nothing
    }

    /** Note: Applying it does nothing either: the buff itself IS the count (see the class comment). */
    @Override
    public void applyEffect(com.laosun.aluminium.models.CanHit owner) {
        // deliberately nothing
    }

    /** Note: And nothing happens when it comes off: the count is the buffs, so removing one is the whole effect. */
    @Override
    public void removeBuff(com.laosun.aluminium.models.CanHit owner) {
        // deliberately nothing
    }
}
