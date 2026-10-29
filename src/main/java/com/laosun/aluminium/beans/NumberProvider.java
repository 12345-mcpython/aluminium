package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

/**
 * A <b>number provider</b>: one JSON object that says <i>where a number comes from</i>, so the same spelling can be
 * reused by every op that needs a share or a flat amount - and so a rule can compose two sources instead of waiting
 * for a new field name per combination.
 *
 * <p><b>Why this exists.</b> A magnitude used to name its source by <i>field</i>: {@code percent_from_skill_param},
 * {@code percent_from_cast_param}, {@code percent_from_summon_param}, {@code percent_from_resource}, and the four
 * {@code amount_*} twins. Each is one spelling of "read a number from somewhere", and every new combination ("60% of
 * the owner's #1 plus 2") needed either a new field or a new op. This bean is that family's data form: the source is
 * a {@link #type}, and the arithmetic types compose providers.
 *
 * <p><b>Where it is read.</b> {@code TriggerMagnitudes.shareOf} ({@code percent_from}) and
 * {@code TriggerMagnitudes.constantTerm} ({@code amount_from}); the interpreter never sees this object except through
 * {@code TriggerMagnitudes}. The type set is closed and checked at load time by {@code TriggerValidator}.
 *
 * <p>⚠ <b>A provider is not a condition.</b> The condition DSL decides <i>whether</i> an effect runs; a provider only
 * produces a number. The two share the vocabulary where it is obvious ({@code resource}), and nowhere else - a
 * provider cannot branch (a {@code conditional} type is deliberately absent, see AGENT.md).
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
public final class NumberProvider {

    /**
     * One of the closed set in {@code NumberProviders}: {@code const} / {@code skill_param} / {@code cast_param} /
     * {@code summon_param} / {@code resource} / {@code add} / {@code sub} / {@code mul} / {@code div} / {@code min} /
     * {@code max} / {@code negate} / {@code abs} / {@code avg}.
     */
    @SerializedName("type")
    private String type;

    /** {@code const}: the number itself. */
    @SerializedName("value")
    private Double value;

    /** {@code skill_param}: the owner's skill slot, a {@code SkillType} name ({@code SKILL} / {@code ULTRA} / ...). */
    @SerializedName("skill")
    private String skill;

    /** {@code *_param}: the column of the row, 1-based, spelled as the documents spell their {@code #N}. */
    @SerializedName("index")
    private Integer index;

    /** {@code summon_param}: the memosprite's data slot (1 / 3 / 5 ...), which no {@code SkillType} can name. */
    @SerializedName("slot")
    private Integer slot;

    /** {@code resource}: the resource's name, as the character's own {@code resources} block declares it. */
    @SerializedName("resource")
    private String resource;

    /** {@code resource}: {@code true} = read it in basis points (the capture's own unit), then divide by 10000. */
    @SerializedName("basis_points")
    private Boolean basisPoints;

    /** {@code add} / {@code mul} / {@code min} / {@code max} / {@code avg}: the inputs, in the order they are stated. */
    @SerializedName("inputs")
    private List<NumberProvider> inputs;

    /** {@code sub} / {@code div}: the left operand. */
    @SerializedName("left")
    private NumberProvider left;

    /** {@code sub} / {@code div}: the right operand. */
    @SerializedName("right")
    private NumberProvider right;

    /** {@code negate} / {@code abs}: the operand. */
    @SerializedName("input")
    private NumberProvider input;

    /** {@code attr}: the attribute's name as {@code AttributeType} spells it ({@code ATTACK}, {@code DEFENCE}, ...). */
    @SerializedName("attribute")
    private String attribute;

    /** {@code attr} / {@code stacks}: whose number ({@code owner} = the rule owner, {@code target}). Default owner. */
    @SerializedName("whose")
    private String whose;

    /** {@code attr}: keep only the part above this threshold ({@code max(0, value - above)}), like {@code self_attr_above:}. */
    @SerializedName("above")
    private Double above;

    /** {@code stacks}: the counter's name, as {@code APPLY_BUFF} spells it. */
    @SerializedName("buff")
    private String buff;

    /** {@code uniform}: the interval's low end (inclusive). */
    @SerializedName("min")
    private NumberProvider min;

    /** {@code uniform}: the interval's high end (exclusive). */
    @SerializedName("max")
    private NumberProvider max;

    /** {@code weighted_list}: the weighted choices. */
    @SerializedName("distribution")
    private List<Weighted> distribution;

    /**
     * One entry of a {@code weighted_list}: a weight and the provider it selects.
     */
    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    public static final class Weighted {

        /** The relative weight; must be positive. */
        @SerializedName("weight")
        private Double weight;

        /** The provider this entry contributes when picked. */
        @SerializedName("value")
        private NumberProvider value;
    }
}
