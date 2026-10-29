package com.laosun.aluminium.trigger;

import lombok.Getter;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The closed set of trigger-effect ops: every spelling an effect's {@code "op"} may carry.
 *
 * <p><b>Why an enum, and what it buys.</b> {@code TriggerValidator} rejects an unknown op at load
 * time by asking whether the name is in a set, and the effect paths then dispatch on the same name.
 * With the vocabulary written down as strings, the compiler can only ever catch a typo inside a
 * {@code switch} arm that has already been reached: a misspelt {@code case} silently becomes
 * unreachable, which is the failure the project's whole closed-set discipline exists to prevent.
 * As constants, a misspelt name does not compile, and a {@code switch} over the enum can be
 * exhaustive.
 *
 * <p>Note: {@code wired} marks whether the engine can actually perform the op. An op that is
 * declared but whose prerequisite phase has not landed is <b>listed here on purpose</b> rather than
 * treated as a typo, so the load-time error can say <i>why</i> it is refused instead of claiming the
 * name does not exist. {@link TriggerVocabulary} derives its {@code WIRED} and {@code PLANNED} sets
 * from this flag, so the two can no longer drift -- which is the one thing a second copy of the
 * vocabulary always eventually does.
 *
 * <p>Note: {@code @Getter} sits on the {@code wired} field rather than on the enum, so what it
 * generates is exactly {@code isWired()} -- the accessor this class has always exposed -- and nothing
 * else. It stays a hand-written-looking API on purpose: {@code enums.ResourceScope} and
 * {@code enums.TriggerEvent} carry the same flag under the same name.
 */
public enum TriggerOperator {

    ABSORB_DAMAGE(true),
    ADD_DAMAGE(true),
    ADD_ELEMENTAL_WEAKNESS(true),
    ADD_STACK(true),
    ADVANCE(true),
    APPLY_BUFF(true),
    APPLY_CONTROL(true),
    APPLY_DOT(true),
    APPLY_REGEN(true),
    BOOST_DAMAGE(true),
    BOOST_TOUGHNESS(true),
    CAST_SKILL(true),
    COMMAND_SUMMON(true),
    CONSUME_HP(true),
    DAMAGE(true),
    DELEGATE_DAMAGE(true),
    DISMISS_SUMMON(true),
    DISPEL(true),
    EXTEND_BUFF(true),
    EXTRA_TURN(true),
    GAIN_ENERGY(true),
    GAIN_RESOURCE(true),
    GAIN_SKILL_POINT(true),
    HEAL(true),
    INSERT_ACTION(true),
    MODIFY_ATTR(true),
    MODIFY_DAMAGE_TAKEN(true),
    MODIFY_RULE(true),
    RAISE_ENERGY_TO(true),
    RAISE_RESOURCE_CAP(true),
    RAISE_SKILL_HITS(true),
    RAISE_SKILL_LEVEL(true),
    RAISE_SKILL_POINT_CAP(true),
    /**
     * A heal bonus whose applicability is decided per heal rather than per unit: "when I heal a target below
     * 30% HP, my healing is raised by 50%".
     *
     * <p>It exists because neither of the two obvious spellings is correct. A stat on the healer cannot see who
     * is being healed, so it would raise every heal; and no rule can raise it just before a heal, because there
     * is no pre-heal event. The condition travels in the effect's {@code target_when} and is evaluated by
     * {@code Battle.calculateHeal}, which is the only place holding the healer, the recipient and the battle.
     */
    REGISTER_HEAL_BONUS(true),
    /**
     * Declared in the roadmap; its prerequisite phase has not landed, so nothing performs it.
     */
    REDUCE_TOUGHNESS(false),
    REMOVE_BUFF(true),
    REMOVE_RESOURCE(true),
    REMOVE_STACK(true),
    REMOVE_STATE(true),
    REPLACE_SKILL(true),
    RESET_RESOURCE_CAP(true),
    RESET_RULE_CHANCE(true),
    RESET_TRIGGER_LIMIT(true),
    RESIST_DEBUFF(true),
    SHIELD(true),
    SPEND_RESOURCE(true),
    SPEND_STEPS(true),
    START_COUNTDOWN(true),
    SUMMON(true),
    SUMMON_SERVANT(true),
    SUPER_BREAK(true),
    TAUNT(true),
    TICK_DOT(true);

    /**
     * Whether the engine can perform this op today. {@code false} is a statement about the ENGINE,
     * not about the data: a rule naming such an op is refused while its table is compiled, and the
     * error names the prerequisite phase rather than claiming the name does not exist.
     *
     * <p>The accessor is Lombok's, and it must keep being named {@code isWired}: the enums
     * {@code ResourceScope} and {@code TriggerEvent} spell the same flag the same way. Note: Written
     * as {@code isWired()} rather than as a {@code {@link}} on purpose -- the method is generated, so
     * a link to it resolves to nothing. {@link #isPlanned()} is the mirror, named for the reader that
     * asks the opposite question.
     */
    @Getter
    private final boolean wired;

    TriggerOperator(boolean wired) {
        this.wired = wired;
    }

    /**
     * The mirror of {@code isWired()}, named for the reader that asks this question.
     */
    public boolean isPlanned() {
        return !wired;
    }

    /**
     * Every op the engine can perform.
     *
     * <p>Returned as a set of <b>names</b> rather than of constants because that is what the
     * character files hold, and what the load-time check compares against. Deriving it here is the
     * point: the vocabulary has one spelling of the list, and this is a view of it.
     */
    public static Set<String> wiredNames() {
        return names(true);
    }

    /**
     * Every op declared but not yet performed -- see {@code isWired()}.
     */
    public static Set<String> plannedNames() {
        return names(false);
    }

    private static Set<String> names(boolean wired) {
        return java.util.Arrays.stream(values())
                .filter(op -> op.wired == wired)
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * The op an effect's {@code "op"} names, or {@code null} when nothing matches.
     *
     * <p>Lenient about case and surrounding whitespace, because the reading code has always been:
     * the spellings are data, and the shipped files are upper-case but nothing guarantees it. Case
     * folding is {@link Locale#ROOT} so a Turkish locale cannot turn {@code INSERT_ACTION} into a
     * different word.
     *
     * <p>Note: Returns {@code null} rather than throwing so the caller can say <i>which</i> kind of
     * wrong this is -- an unknown name is a typo, a known-but-unwired one is a phase that has not
     * landed, and those are different messages for the author.
     */
    public static TriggerOperator parse(String written) {
        if (written == null) {
            return null;
        }
        String name = written.trim().toUpperCase(Locale.ROOT);
        if (name.isEmpty()) {
            return null;
        }
        for (TriggerOperator op : values()) {
            if (op.name().equals(name)) {
                return op;
            }
        }
        return null;
    }
}
