package com.laosun.aluminium.models.skillpoint;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Resource;
import com.laosun.aluminium.models.Skill;

/**
 * Standard skill point (SP) policy (P8-4): start at 3, cap 5, **our side's** basic attack +1,
 * skill -1, everything else neutral.
 *
 * <p>Rule table (see the full comparison and gap list in {@code engine.md} §9.6):
 *
 * <table border="1">
 *   <tr><th>Skill category</th><th>Skill points</th></tr>
 *   <tr><td>{@link SkillCategory#NORMAL}</td><td>+{@link Constant#SKILL_POINT_GAIN_BASIC}</td></tr>
 *   <tr><td>{@link SkillCategory#BPSKILL}</td><td>-1; if there are not enough, the unit **cannot act**</td></tr>
 *   <tr><td>Everything else (including {@code ULTRA}, map skills, and the {@code UNSPECIFIED} of talents/follow-up attacks)</td>
 *       <td>neutral</td></tr>
 * </table>
 *
 * <p><b>Why only our side is counted</b>: enemies also act through {@code Battle.performAction}, and
 * their skills are {@code Normal} as well — without a camp check, every enemy hit would give our side
 * +1 skill point. The test uses {@link com.laosun.aluminium.enums.Camp#PLAYER} ("a unit on our side")
 * rather than "is it player-controlled": when friendly summons (memosprite, P9-4) are added later they
 * **should** also supply points, and this behaviour is pinned by {@code SkillPointGameParityTest}.
 *
 * <p>⚠ <b>Known deviation</b> (<b>F-3</b> in §F of {@code DOC_VS_CODE.md}): here {@code NORMAL} is
 * uniformly +1, whereas in the game **enhanced basic attacks have exceptions** — Boothill's (波提欧)
 * enhanced basic attack "cannot restore skill points", while Qingque's (青雀) enhanced basic attack
 * "restores 1 skill point". In the data both are {@code "Normal"} (there is no separate type), so this
 * implementation is **correct for Qingque and wrong for Boothill**.
 * ⚠ **Do not change it to "enhanced basic attacks are always +0"** (that would break Qingque) — the
 * right fix is a **per-skill skill-point delta field** (a data completion). This class leaves
 * {@link #gainForCast} as the override point.
 *
 * <p>⚠ <b>Character-level modifiers are not wired up</b> (F-4 in the same §F): Bronya's (布洛妮娅)
 * "50% chance to +1 on skill", Sushang's (素裳) "+1 on a skill that hits a broken target", Sparkle's
 * (花火) "cap +2" and so on all have to wait for the P8-7 trigger table. This class **deliberately knows
 * no character** — when adding these, extend and override {@link #gainForCast} (or drive it from the
 * P8-7 effect table), and do **not** write {@code cid} checks here.
 */
public class StandardSkillPointPolicy implements SkillPointPolicy {

    /**
     * The skill point resource itself. {@link Resource} is used instead of a bare {@code int} so that
     * it shares one bounded-semantics abstraction with P8-8's stack resources (see that class's
     * description).
     */
    private final Resource resource;

    /**
     * Construct with the standard values: cap {@link Constant#SKILL_POINT_MAX}, start
     * {@link Constant#SKILL_POINT_START}.
     */
    public StandardSkillPointPolicy() {
        this(Constant.SKILL_POINT_MAX, Constant.SKILL_POINT_START);
    }

    /**
     * Construct with the given cap/start value (for tests and for the future "cap raised by a light
     * cone/character", see F-1 in §F).
     *
     * @param max     the conventional cap
     * @param initial the start value (clamped to {@code [0, max]})
     */
    public StandardSkillPointPolicy(int max, int initial) {
        this.resource = new Resource("skill_point", max, initial);
    }

    /**
     * Exposes the underlying resource — for team-level modifiers like "change the cap / configure an
     * overflow allowance" (Sparkle's (花火) cap +2 and the 10-point overflow store both belong here).
     */
    public Resource resource() {
        return resource;
    }

    /**
     * Reporting hook for the skill points' **actual change** (P8-6).
     *
     * <p>Why the policy reports it instead of {@code Battle} comparing the before/after values: the
     * policy is the only component that knows "whether it actually went up this time and by how much"
     * (for example, when already at cap after a basic attack the actual credited amount is 0, and no
     * event should be fired). {@code Battle} is only responsible for broadcasting the reported events
     * to the team — that way {@code Battle} still **does not need to know the skill point rules**
     * (see F-8 in §F).
     */
    public interface Listener {
        /** Actually credited {@code amount} points ({@code > 0}). */
        void onGained(int amount);

        /** Actually spent {@code amount} points ({@code > 0}). */
        void onSpent(int amount);
    }

    private static final Listener NO_OP = new Listener() {
        @Override
        public void onGained(int amount) {
        }

        @Override
        public void onSpent(int amount) {
        }
    };

    private Listener listener = NO_OP;

    /**
     * Attach the change-reporting hook. Passing {@code null} is equivalent to removing it (back to
     * a no-op).
     *
     * @param listener the reporting hook
     */
    public void setListener(Listener listener) {
        this.listener = listener == null ? NO_OP : listener;
    }

    @Override
    public boolean onSkillCast(CanHit user, Skill skill) {
        if (user == null || user.getCamp() != com.laosun.aluminium.enums.Camp.PLAYER) {
            return true;                        // an enemy action does not touch our skill points
        }
        SkillCategory category = categoryOf(skill);
        if (category == null) {
            return true;                        // no skill data (enemy skill / empty skill) → neutral
        }
        return switch (category) {
            case NORMAL -> {
                // use gain()'s **return value** (the actual credited amount) instead of the nominal
                // one: it is 0 when already at cap, and no event should be fired
                int gained = gain(gainForCast(user, skill, category));
                if (gained > 0) {
                    listener.onGained(gained);
                }
                yield true;
            }
            case BPSKILL -> {
                if (spend()) {
                    listener.onSpent(1);
                    yield true;
                }
                yield false;                    // not enough → the action does not happen, and **no** spend event is fired
            }
            // ULTRA / MAZE / MAZE_NORMAL / ASSIST / ELATION_DAMAGE /
            // UNSPECIFIED (talent·follow-up attack) / UNKNOWN (a value the data does not recognise) → neutral
            default -> true;
        };
    }

    /**
     * How many skill points one basic attack **should restore** (default
     * {@link Constant#SKILL_POINT_GAIN_BASIC}).
     *
     * <p>This is the class's **primary override point**: character-level modifiers such as "Sparkle
     * (花火) in the team gives +1" or "an enhanced basic attack restores no points" are produced by a
     * subclass or the P8-7 effect table overriding it, and **without changing {@code Battle}**.
     *
     * @param user     the acting unit
     * @param skill    the skill
     * @param category the already-resolved category (the caller guarantees it is not {@code null})
     * @return the skill points to add ({@code <= 0} means add none)
     */
    protected int gainForCast(CanHit user, Skill skill, SkillCategory category) {
        return Constant.SKILL_POINT_GAIN_BASIC;
    }

    /**
     * Resolves the skill category.
     *
     * <p>⚠ Go through {@code SkillData.getCategory()} and not a bare-string {@code switch} — the
     * latter **silently mismatches** when the data side changes a spelling or adds a new value (see
     * F-6 in §F of {@code DOC_VS_CODE.md}).
     *
     * @return {@code null} means "there is no category to speak of" (the skill or its skill data is
     * empty); the caller treats it as neutral
     */
    protected SkillCategory categoryOf(Skill skill) {
        if (skill == null || skill.getData() == null) {
            return null;
        }
        return skill.getData().getCategory();
    }

    @Override
    public int getValue() {
        return resource.getValue();
    }

    @Override
    public int getMax() {
        return resource.getMax();
    }

    @Override
    public int gain(int delta) {
        return resource.gainClamped(delta);
    }

    @Override
    public boolean canAfford() {
        return resource.getValue() > 0;
    }

    @Override
    public boolean spend() {
        return resource.spendExactly(1);
    }
}
