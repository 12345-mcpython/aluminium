package com.laosun.aluminium.enums;

/**
 * Who a {@link com.laosun.aluminium.models.Resource} belongs to (P8-8).
 *
 * <p>This is the difference between "my own stacks" and "our team's stacks", and the game has both:
 * <ul>
 *   <li>{@link #SELF} — one counter per character. Acheron's 【残梦】, Feixiao's 【飞黄】,
 *       Phainon's 【火种】 and Cyrene's 【追忆】 are all personal;</li>
 *   <li>{@link #PARTY} — one counter shared by the whole team, so gaining it on any member moves the
 *       same number (March 7th's 【充能】and Dahlia's shared stacks are the named examples).</li>
 * </ul>
 *
 * <p>⚠ Only {@link #SELF} is <b>wired</b> today (see
 * {@link #isWired()}). {@link #PARTY} is declared because the data vocabulary must be stable and
 * because a party-scoped resource needs something this task does not have yet: an owner that
 * outlives any single character (a per-battle registry). Putting it on each character would silently
 * give the team four independent copies instead of one shared pool — a wrong answer that no
 * exception would ever report, so the declaration is explicit about not being ready.
 */
public enum ResourceScope {
    /**
     * One counter per character; stored on that character.
     */
    SELF("SELF", true),
    /**
     * One counter shared by the team. Declared, <b>not wired</b> — see the class docs.
     */
    PARTY("PARTY", false);

    private final String value;
    private final boolean wired;

    ResourceScope(String value, boolean wired) {
        this.value = value;
        this.wired = wired;
    }

    /** The string used in data files. */
    public String value() {
        return value;
    }

    /**
     * Whether the engine currently supports this scope.
     *
     * <p>Data asking for an unwired scope is rejected at load time rather than silently behaving as
     * {@link #SELF} — the failure mode of "four private copies instead of one shared pool" is
     * invisible at runtime.
     */
    public boolean isWired() {
        return wired;
    }

    /**
     * Parses a data value, case-insensitively.
     *
     * @param raw the data value
     * @return the matching scope, or {@code null} when unknown
     */
    public static ResourceScope fromString(String raw) {
        if (raw == null) {
            return null;
        }
        for (ResourceScope scope : values()) {
            if (scope.value.equalsIgnoreCase(raw.trim())) {
                return scope;
            }
        }
        return null;
    }
}
