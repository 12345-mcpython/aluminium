package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

/**
 * One trigger rule from {@code resources/characters/<cid>.json}:
 * "on &lt;event&gt;, when &lt;conditions&gt;, do &lt;effects&gt;".
 *
 * <p>This is the unit that replaces a `XxxTalent.java` class. The engine only interprets it --
 * it never learns which character it belongs to, which is the whole point of the three-way
 * split.
 *
 * <p>Note: Read the conditions as follows: the owning character is called <b>self</b> in
 * {@link #when}; the unit that caused the event is <b>actor</b>. So
 * {@code "when": ["actor != self"]} means "someone else on my side acted" -- which is how
 * Robin's "after an ally attacks" talent is expressed.
 */
@Getter
@ToString
@NoArgsConstructor
public class TriggerSpec {

    /**
     * The event name, matching {@link com.laosun.aluminium.enums.TriggerEvent}.
     */
    @SerializedName("on")
    private String on;

    /**
     * Extra events this rule also listens to (one rule listening to several events), e.g. "when casting the Skill and the Ultimate".
     *
     * <p>Note: The primary {@link #on} stays REQUIRED even when this is present, on purpose: four separate places parse it with
     * {@code TriggerEvent.fromString(spec.getOn())} to validate which event a clause may hang on, and leaving them alone is what makes this addition
     * behaviour-preserving for every rule that ships today.
     */
    @SerializedName("on_any")
    private java.util.List<String> onAny;

    /**
     * Conditions that must all hold, in the small DSL understood by
     * {@link com.laosun.aluminium.models.TriggerTable}. An empty or absent list means
     * "always".
     */
    @SerializedName("when")
    private List<String> when;

    /**
     * An optional <b>name for this rule</b>, so that another rule can raise one of its numbers
     * ({@code MODIFY_RULE}'s {@code "rule"} field).
     *
     * <p><b>Why it exists.</b> An Eidolon (星魂) or a trace (行迹) can say "the number of times the talent's counter effect can trigger each turn is <b>increased by 1</b>" or
     * "the base chance to freeze the enemy target is <b>increased by 15%</b>" - sentences that modify a number that already exists on another rule
     * in the same file. Without a name there is nothing to point at, and the two ways to fake it are both wrong: a
     * second rule with the raised number <i>adds</i> firings (a {@code per_turn: 3} rule next to the {@code per_turn: 2}
     * one = five per turn) and a second chance rule <i>rolls twice</i> (1 − 0.5  x  0.35 = 82.5% instead of 65%).
     *
     * <p>Note: Names are unique <b>per file</b> (two rules with one id are refused at load, since a reference would be
     * ambiguous) and a reference must resolve inside the same file - a relic rule is shared by every wearer and has
     * no way to know whose table it lands in.
     */
    @SerializedName("id")
    private String id;

    /**
     * The effects to run, in order.
     */
    @SerializedName("do")
    private List<EffectSpec> doEffects;

    /**
     * How many of the <b>owner's own turns</b> must pass between two firings - the game's
     * "该效果每回合只能触发1次" is {@code 1}. Absent = no limit.
     *
     * <p>Counted in the owner's turns, not in events: the counter is decremented when the turn of the
     * character whose table this is begins. That is why a rule reacting to other people's actions still
     * means "once per <i>my</i> turn" - the limit belongs to the rule's owner, not to whoever happened
     * to trigger it. A value below 1 is rejected at load time.
     */
    @SerializedName("cooldown")
    private Integer cooldown;

    /**
     * How many times this rule may fire in <b>one of its owner's turns</b> - "this effect can trigger <b>2</b> times
     * <b>per turn</b>" is {@code per_turn: 2}. Absent = no per-turn cap.
     *
     * <p><b>Why {@code cooldown} is not enough.</b> {@code cooldown: 1} says "at most once per own turn", which
     * is the {@code N = 1} case of this field and nothing more: "每回合可触发2次" needs a <b>count</b> within one
     * turn, and before this field the only choices were to fire on every event (a wrong number with nothing to
     * see) or not to write the mechanic at all. 10 of the 9character documents state such a limit.
     *
     * <p>Note: Counted in the <b>owner's</b> turns, exactly like {@link #cooldown}: a rule that reacts to other
     * people's actions still means "twice per <i>my</i> turn", and the counter is cleared where the cooldown is
     * decremented ({@code CanHit.tickTriggerCooldowns}, at the start of the owner's own turn).
     *
     * <p>May be combined with {@code once_per_battle} (a per-turn cap and a per-battle cap are cumulative) but
     * <b>not</b> with {@code cooldown}: "at most 2 per turn, but only every other turn" has two readings that
     * disagree, so the pair is refused at load time rather than silently resolved one way.
     */
    @SerializedName("per_turn")
    private Integer perTurn;

    /**
     * Whose firings {@link #perTurn} and {@link #cooldown} count, when it is not the rule owner - the count-and-reset
     * family (2026-09-30; readers 1305, 120, 1403) counts on the marked TARGET, on the TRIGGERER, or per UNIT.
     * One of {@code self} / {@code target} / {@code actor}; absent means the owner, which is what every
     * shipped rule already does, so omitting it changes nothing.
     */
    @SerializedName("per_subject")
    private String perSubject;

    /**
     * {@code true} = the rule fires at most once per battle ("单场战斗中只能触发1次").
     *
     * <p>Deliberately not the same field as {@link #cooldown}: a cooldown comes back after a few turns,
     * this never does. Stating both at once is refused at load time - the author has to mean one of
     * them, and "once per battle, but also every 2 turns" has no reading that is not a mistake.
     */
    @SerializedName("once_per_battle")
    private Boolean oncePerBattle;

    /**
     * "该效果每次攻击只可触发 1 次": at most one firing per ATTACK.
     *
     * <p><b>Why {@code per_turn} is not enough.</b> An attack can settle several instances - a blast hits three enemies, a
     * multi-hit skill connects six times - and all of them are one attack. A per-turn cap cannot tell those apart from
     * three separate attacks in the same turn, which is exactly the distinction "每次攻击只可触发 1 次" makes.
     *
     * <p>The boundary is the engine's own: {@code Battle.fireAfterAttack} ends an attack, so the next instance belongs to
     * the next one. Cumulative with the other caps (they are all upper bounds).
     */
    /**
     * [WAVE-LIMITED] at most one firing per WAVE (2026-10-02; reader: 1506's warehouse skill).
     *
     * <p><b>Why neither neighbour can say it.</b> {@code per_turn} counts one unit's turns and {@code once_per_battle} covers the
     * whole fight; a wave is neither. So it gets the treatment {@code once_per_attack} got: a SEQUENCE comparison, which needs no
     * reset because a new wave is a new number ({@code Battle.waveSequence()}).
     */
    @SerializedName("once_per_wave")
    private Boolean oncePerWave;

    /** The per-wave cap, as the engine reads it. Written out because the builder reads this exact name. */
    public Boolean getOncePerWave() {
        return oncePerWave;
    }
    @SerializedName("once_per_attack")
    private Boolean oncePerAttack;

    /**
     * "每次攻击最多通过该方式… N 次": at most N firings per ATTACK (cone 23008 states 3).
     *
     * <p>The same dimension as {@link #oncePerAttack}, with a count. Both mean "counted per attack, not per turn", so
     * stating them together would be two readings of one cap -- refused, like {@code cooldown} next to {@code per_turn}.
     */
    @SerializedName("per_attack")
    private Integer perAttack;

    /**
     * A fixed probability that the rule fires at all - "有 X% 的固定概率…" ({@code 0.35} = 35%). Absent = always.
     *
     * <p>Rolled against the battle's <b>injected</b> random source (never a fresh one), so a seeded battle stays
     * reproducible and a test can make the flip deterministic. A failed roll <b>costs nothing</b>: neither a
     * cooldown nor a once-per-battle flag is started, because nothing happened.
     *
     * <p>A fraction of 1, and it must be strictly positive: {@code chance: 1} is legal and simply means "always"
     * (it does not even consume a draw), while {@code chance: 0} would be a rule that can never do anything - 
     * a mistake, not a way to spell "off".
     */
    @SerializedName("chance")
    private Double chance;

    /**
     * The Eidolon rank (星魂, Eidolon) this rule needs: "unlocked at Eidolon N". Absent = the rule is not gated.
     *
     * <p>An Eidolon's <b>mechanic</b> is written like every other one - as a rule in
     * {@code resources/characters/<cid>.json} - and this field is the whole of what makes it an Eidolon: the
     * interpreter compares it with the rank the assembly point handed to the character. So the engine still knows
     * nothing about Eidolons beyond a number, and {@code eidolons.json}'s names and descriptions stay reference
     * material for the rule's {@code source}.
     */
    @SerializedName("min_eidolon")
    private Integer minEidolon;

    /**
     * Where the rule came from (trace id, talent name, character-doc reference).
     *
     * <p>Not used by the engine at all: it exists so that anyone reading the JSON -- or a failing
     * test -- can trace a number back to its source document. The project's rule is that every
     * effect states its origin.
     */
    @SerializedName("source")
    private String source;

    /**
     * Free-form note, typically "why this number" or a {@code TODO data} marker when a value is a
     * placeholder rather than a measured one.
     */
    @SerializedName("note")
    private String note;
}
