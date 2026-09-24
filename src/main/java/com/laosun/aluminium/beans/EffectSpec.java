package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * One effect inside a trigger (P8-7): "do this".
 *
 * <p>Every effect is a {@code (op, ...args)} pair. The {@code op} vocabulary is deliberately
 * restricted to <b>capabilities the engine already has</b> -- the trigger table is an interpreter
 * over existing engine operations, not a second engine. See
 * {@link com.laosun.aluminium.models.TriggerInterpreter} for the implemented ops and
 * {@code ROADMAP.md} P8-7 for the planned ones.
 *
 * <p>Which fields an op reads depends on the op (for example {@code GAIN_ENERGY} uses
 * {@link #amount} while {@code APPLY_BUFF} would use {@link #buffId}); unused fields stay
 * {@code null} in the JSON. The interpreter validates the required ones and reports the trigger's
 * origin when something is missing.
 */
@Getter
@ToString
@NoArgsConstructor
public class EffectSpec {

    /**
     * The operation name, e.g. {@code "GAIN_ENERGY"}.
     */
    @SerializedName("op")
    private String op;

    /**
     * Magnitude for numeric ops (energy, resource stacks, damage multiplier, ...).
     */
    @SerializedName("amount")
    private Double amount;

    /**
     * Attribute name for {@code MODIFY_ATTR}, matching {@code AttributeType} (e.g. {@code "ATTACK"}).
     */
    @SerializedName("attribute")
    private String attribute;

    /**
     * A percentage argument: the modifier for {@code MODIFY_ATTR} (0.5 = +50%), or the fraction for
     * {@code ADVANCE} (0.5 = skip half of the target's **remaining** time to act, matching
     * {@code Queue.advanceActionByPercent}, which requires 0.0–1.0).
     */
    @SerializedName("percent")
    private Double percent;

    /**
     * Duration in turns, where the op needs one.
     *
     * <p>For {@code MODIFY_ATTR} this is <b>exactly one of</b> this field and {@link #permanent}: a
     * modifier either lasts N of the owner's turns or lasts until the battle ends ("for the rest of the
     * battle"), and the interpreter rejects a rule that states neither or both.
     */
    @SerializedName("turns")
    private Integer turns;

    /**
     * For {@code MODIFY_ATTR}: whether the modifier lasts <b>until the battle ends</b> rather than for a
     * number of turns.
     *
     * <p>Spelled as a flag on purpose. The alternative — a large {@code turns} value — would be a magic
     * number that the engine still counts down once per turn, so "unbounded" would only mean "longer
     * than the battle probably lasts". The buff is instead never ticked at all
     * ({@code AbstractBuff.isPermanent()}), which is what makes the duration exact.
     *
     * <p>Absent/{@code false} means "use {@link #turns}"; there is no default duration.
     */
    @SerializedName("permanent")
    private Boolean permanent;

    /**
     * For {@code MODIFY_ATTR}: how many copies of the modifier may <b>accumulate</b> ("this effect can
     * stack up to #N time(s)").
     *
     * <p>Absent or {@code 1} keeps the engine's historical replace-on-same-kind rule — re-applying the
     * same attribute modifier refreshes it instead of adding a second one
     * ({@code BuffManagerTest.sameKindBuffRefreshesInsteadOfStacking}). Above 1 the modifier becomes a
     * stack: every application adds another instance until the cap is reached, further applications
     * change nothing, and each stack is removable on its own
     * ({@code BuffManager.addStackable}).
     *
     * <p>{@code "stacks"} is accepted as an alias, because the game text says "stacking up to #N
     * time(s)" and an author writing the rule reads that word first.
     */
    @SerializedName("max_stacks")
    private Integer maxStacks;

    /**
     * Alias of {@link #maxStacks}, from the wording of the effect text ("stacking up to N time(s)").
     * Stating both is rejected at load time rather than silently picking one.
     */
    @SerializedName("stacks")
    private Integer stacks;

    /**
     * Resource id for {@code GAIN_RESOURCE} / {@code SPEND_RESOURCE} (e.g. {@code "tribbie_charge"}).
     */
    @SerializedName("resource")
    private String resource;

    /**
     * Buff kind for {@code APPLY_BUFF}.
     */
    @SerializedName("buff")
    private String buff;

    /**
     * Who the effect applies to: {@code "self"} (the default -- the character whose table fired),
     * {@code "target"} (the subject of the event: whoever lost HP, was healed, was hit, ...), or
     * {@code "attacker"} (whoever <b>caused</b> the event).
     *
     * <p>{@code "attacker"} is what a counter needs: "I was hit, so I hit the one who hit me".
     *
     * <p>Absent means {@code "self"}, which keeps "my own resource" rules terse -- they are the
     * overwhelming majority.
     */
    @SerializedName("target")
    private String target;

    /**
     * Which skill slot a {@code DAMAGE} effect takes its attack from, by
     * {@link com.laosun.aluminium.enums.SkillType} name (e.g. {@code "TALENT"}).
     *
     * <p>The talent slot is the usual source for a follow-up attack: in the game the talent is the
     * passive that <b>releases</b> it, which is also why the talent's own {@code attack_type} is empty
     * (a passive is not a swing). Its data still carries the full attack payload -- effect shape,
     * element, toughness values and the per-level multiplier -- so the engine reads the attack from
     * there instead of duplicating numbers into the character file.
     */
    @SerializedName("skill")
    private String skill;

    /**
     * Which entry of the skill's per-level parameter row is the damage multiplier.
     *
     * <p>⚠ This exists because <b>no single index works</b>. Basic attacks and skills happen to carry
     * their multiplier first, but for talents it moves around:
     * <ul>
     *   <li>Clara 1107 — {@code [1, 0.8, 0.1]} → index 1;</li>
     *   <li>Moze 1223 — {@code [0.15, 3, 0.8]} → index 2;</li>
     *   <li>Dahlia 1321 — {@code [0.15, 5, 1, 35, 0.3]} → index 2.</li>
     * </ul>
     * Guessing a fixed index would quietly compute the wrong number for a whole class of abilities, so
     * the parameter's meaning is stated per skill in the data instead.
     */
    @SerializedName("damage_param")
    private Integer damageParam;

    /**
     * Whether a {@code DAMAGE} effect <b>counts as an attack</b>.
     *
     * <p>{@code true} — a follow-up attack: a real hit, so it participates in attack-level events and
     * the target's "on being hit" energy.
     * {@code false} — supplementary damage (the officially defined 附加伤害/真伤, which explicitly
     * "does not count as dealing 1 attack").
     *
     * <p>Absent means {@code false}: the conservative reading, and what the engine's existing
     * additional-damage path already implements.
     */
    @SerializedName("as_attack")
    private Boolean asAttack;

    /**
     * Whether {@link #amount} is <b>per target hit</b> rather than a flat total.
     *
     * <p>This distinction is not cosmetic — the game states both forms and they differ:
     * <ul>
     *   <li>Robin's talent: "after an ally attacks an enemy, restore <b>2</b> energy" — a flat 2 per
     *       attack, no matter how many targets it hit;</li>
     *   <li>Tribbie's trace: "for <b>each target hit</b>, restore <b>1.50</b> energy" — 1.5 × the
     *       number of targets.</li>
     * </ul>
     * When {@code true}, the interpreter multiplies {@link #amount} by the event's hit count; when
     * absent or {@code false} the amount is used as-is. It is opt-in so that a rule which happens to
     * fire on an attack event does not silently start scaling.
     */
    @SerializedName("per_target")
    private Boolean perTarget;

    /**
     * The stack cap, whichever spelling the rule used.
     *
     * <p>Read by the interpreter <b>after</b> it has rejected "both spellings stated at once", so the
     * two can never disagree here. Returns {@code null} when the rule says nothing about stacking,
     * which is the "replace, do not stack" default.
     *
     * @return the declared stack cap, or {@code null}
     */
    public Integer stackCap() {
        return maxStacks != null ? maxStacks : stacks;
    }
}
