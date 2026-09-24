package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.ResourceScope;

/**
 * A **team-level** numeric resource: it has a current value, a maximum capacity, and an optional
 * maximum overflow allowance.
 *
 * <p><b>Why this class has to exist</b>: skill points (SP) (P8-4) and P8-8's stack resources
 * (Acheron's 【残梦】, Feixiao's 【飞黄】, Phainon's 【火种】, Cyrene's 【追忆】, Castorice's 【新蕊】…)
 * are essentially the same thing — a counter that some event adds to or subtracts from, that has a
 * capacity cap, and that fires a signal when full. Without extracting it, every character would need
 * a "because of some character" branch inside the engine (violating P8-0's three-way split).
 *
 * <p><b>Three boundaries (all measured in practice, not written off the cuff)</b>:
 * <ol>
 *   <li>The normal path ({@link #gainClamped} / {@link #gain}) **never exceeds {@link #getMax()}**;</li>
 *   <li>Overflow is **explicit and capped**: only after {@link #setMaxOverflow} has configured a
 *       limit will {@link #gain} store the value above {@code max}, and it is capped at
 *       {@code max + maxOverflow}.
 *       ⚠ By default {@code maxOverflow == 0}, so "overflowing by accident with gain" is impossible;</li>
 *   <li>{@link #setValue} is an **unprotected raw write** (clamped down to 0, up to
 *       {@code max + maxOverflow}), intended for "save restoration / debugging" — normal gameplay
 *       should go through gain/spend.</li>
 * </ol>
 *
 * <p>Why overflow is needed: the game really does have mechanics like "cap is 5 but can temporarily
 * be stored up to 10" — Sparkle's ultimate 「restores 4/6 skill points; if skill points overflow when
 * restoring, the overflowed skill point count is recorded, up to 10 points」 (see
 * {@code 1306_花火.md}).
 *
 * <p><b>Threading model</b>: like {@code Battle}, used single-threaded, with no synchronization —
 * one battle is advanced on one thread.
 *
 * @see com.laosun.aluminium.models.skillpoint.SkillPointPolicy
 */
public class Resource {

    /**
     * Resource identifier (e.g. {@code "skill_point"}), used for logging and for the future
     * ResourceManager registry.
     */
    private final String id;

    /**
     * Who this resource belongs to (P8-8). See {@link ResourceScope}.
     */
    private final ResourceScope scope;

    /**
     * The normal cap. {@link #getValue()} only exceeds it while in an overflow state.
     */
    private final int max;

    private int value;

    /**
     * Maximum overflow amount (default 0 = overflow not allowed). See point 2 of the class docs.
     */
    private int maxOverflow;

    /**
     * Notified when the resource goes from "not full" to "full" (the rising edge only).
     *
     * <p><b>Why an edge rather than a level.</b> Feixiao's 【飞黄】 is spent the moment it reaches its
     * threshold, so a level signal would still fire once in practice — but Cyrene sits at her cap for
     * many gains in a row (pool 24, and she can keep collecting into overflow up to 27). A level
     * signal would fire on every one of those extra gains, which is not what "reached the cap" means.
     * The rising edge fires exactly once per arrival.
     *
     * <p>The listener takes the resource so it can read the id and the value; it is deliberately not
     * given the battle (the engine has no business knowing what a listener does with it).
     */
    private java.util.function.Consumer<Resource> onBecameFull = r -> {
    };

    /**
     * @param id      resource identifier
     * @param max     normal cap
     * @param initial initial value (clamped to {@code [0, max]})
     * @throws IllegalArgumentException {@code id} is blank, or {@code max < 0}
     */
    public Resource(String id, int max, int initial) {
        this(id, ResourceScope.SELF, max, initial);
    }

    /**
     * @param id      resource identifier
     * @param scope   who the resource belongs to
     * @param max     normal cap
     * @param initial initial value (clamped to {@code [0, max]})
     * @throws IllegalArgumentException {@code id} is blank, {@code scope} is null, or {@code max < 0}
     */
    public Resource(String id, ResourceScope scope, int max, int initial) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Resource id must not be blank");
        }
        if (scope == null) {
            throw new IllegalArgumentException("Resource scope must not be null (id=" + id + ")");
        }
        if (max < 0) {
            throw new IllegalArgumentException("Resource max must be >= 0, got " + max);
        }
        this.id = id;
        this.scope = scope;
        this.max = max;
        this.value = clamp(initial);
    }

    public String getId() {
        return id;
    }

    /** Who this resource belongs to (P8-8). */
    public ResourceScope getScope() {
        return scope;
    }

    public int getValue() {
        return value;
    }

    public int getMax() {
        return max;
    }

    public int getMaxOverflow() {
        return maxOverflow;
    }

    /**
     * How far the current value is from the normal cap (0 when full or overflowing).
     */
    public int missingToMax() {
        return Math.max(0, max - value);
    }

    /**
     * Whether the **normal** cap has been reached (also true while overflowing).
     */
    public boolean isFull() {
        return value >= max;
    }

    /**
     * Whether the **absolute** cap has been reached (including the overflow allowance), i.e.
     * "no more can be added at all".
     */
    public boolean isCapped() {
        return value >= absoluteMax();
    }

    public boolean isEmpty() {
        return value <= 0;
    }

    /**
     * Adds a value and **clamps it to the normal cap** (overflow not allowed), returning the amount
     * **actually credited**.
     *
     * <p>When {@code delta <= 0} nothing is done ("adding a negative" is a caller bug; silently
     * ignoring it is friendlier than throwing).
     *
     * @return the amount actually added (smaller than {@code delta} when capped; may be 0)
     */
    public int gainClamped(int delta) {
        if (delta <= 0) {
            return 0;
        }
        boolean wasFull = isFull();
        int before = value;
        value = Math.min(max, value + delta);
        int gained = value - before;
        fireIfJustBecameFull(wasFull, gained);
        return gained;
    }

    /**
     * Adds a value, **allowed to overflow** up to {@link #getMaxOverflow()} (equivalent to
     * {@link #gainClamped} when no overflow is configured), returning the amount **actually credited**.
     *
     * <p>"Overflow" MUST be expressed **explicitly** by the caller: the default overflow allowance is
     * 0, so this method's default behavior is exactly the same as {@link #gainClamped}. Only a
     * resource with a configured overflow allowance (for example skill points while Sparkle is on the
     * team) can ever be stored above its cap.
     */
    public int gain(int delta) {
        if (delta <= 0) {
            return 0;
        }
        boolean wasFull = isFull();
        int before = value;
        value = Math.min(absoluteMax(), value + delta);
        int gained = value - before;
        fireIfJustBecameFull(wasFull, gained);
        return gained;
    }

    /**
     * Spends a value, never dropping below 0, returning the amount **actually spent**.
     *
     * @return the amount actually spent; 0 when the resource is empty (the caller should use this to
     *         decide that "this spend did not succeed")
     */
    public int spend(int delta) {
        if (delta <= 0) {
            return 0;
        }
        int before = value;
        value = Math.max(0, value - delta);
        return before - value;
    }

    /**
     * Spends exactly the given value, **spending none of it at all if there is not enough** (atomic
     * semantics).
     *
     * <p>This is where it differs from {@link #spend}: when skill points are insufficient it MUST be
     * "this spend did not go through", not "spend down to 0". {@link #spend} is "spend as much as you
     * can", which suits things like DOT damage.
     *
     * @return whether the spend succeeded (false and the value unchanged when the resource is
     *         insufficient)
     */
    public boolean spendExactly(int delta) {
        if (delta <= 0) {
            return true;
        }
        if (value < delta) {
            return false;
        }
        value -= delta;
        return true;
    }

    /**
     * Sets the maximum overflow allowance ({@code overflow < 0} is treated as 0).
     *
     * <p><b>Invariant</b>: {@code value ∈ [0, max + maxOverflow]} **always holds**.
     * So when the allowance is lowered, any stock above the new absolute cap is **clamped away**
     * (for example {@code max=5, overflow=10, value=15} → set overflow to 0 → value becomes 5).
     *
     * <p>Why lose stock rather than let the value go out of range: once the invariant is broken, all
     * subsequent {@code isCapped()} / {@code missingToMax()} / {@code gain()} decisions become wrong,
     * and **silently so** (the first version did exactly that: it changed the allowance without
     * clamping the value, so value could stay at 15 while the absolute cap was 5 — a silent illegal
     * state).
     *
     * <p>In practice this is only triggered by things like "a buff that provides the overflow
     * allowance is removed mid-battle", and clamping away is the right answer (the capacity is gone,
     * so the excess naturally cannot be kept).
     */
    public void setMaxOverflow(int overflow) {
        this.maxOverflow = Math.max(0, overflow);
        // Re-clamp once to guarantee the invariant is not broken (covering both "allowance shrinks"
        // and "allowance grows")
        this.value = clamp(this.value);
    }

    /**
     * Raw write (unprotected, clamping only): for save restoration / debugging.
     *
     * <p>The clamping range is {@code [0, max + maxOverflow]}.
     */
    public void setValue(int newValue) {
        this.value = clamp(newValue);
    }

    /**
     * Clears to 0 (for operations like resetting at the start of a battle; to restore the initial
     * value, reconstruct or use setValue).
     */
    public void clear() {
        this.value = 0;
    }

    private int absoluteMax() {
        return max + maxOverflow;
    }

    /**
     * Registers the "became full" listener (see the field docs for why it is edge-triggered).
     *
     * @param listener the listener; {@code null} removes it
     */
    public void setOnBecameFull(java.util.function.Consumer<Resource> listener) {
        this.onBecameFull = listener == null ? r -> {
        } : listener;
    }

    /**
     * Fires the "became full" signal when this gain is the one that arrived at the cap.
     *
     * <p>Two conditions, both required:
     * <ul>
     *   <li>it was <b>not</b> full before and is full now — the rising edge, so sitting at the cap
     *       across several gains (or gaining into overflow) does not re-fire;</li>
     *   <li>something was <b>actually credited</b> ({@code gained > 0}) — a gain that was entirely
     *       clamped away is not "arriving at the cap", it is "already there and nothing happened".</li>
     * </ul>
     */
    private void fireIfJustBecameFull(boolean wasFull, int gained) {
        if (!wasFull && gained > 0 && isFull()) {
            onBecameFull.accept(this);
        }
    }

    private int clamp(int raw) {
        return Math.max(0, Math.min(absoluteMax(), raw));
    }

    @Override
    public String toString() {
        return id + "=" + value + "/" + max + (maxOverflow > 0 ? "(+" + maxOverflow + ")" : "");
    }
}
