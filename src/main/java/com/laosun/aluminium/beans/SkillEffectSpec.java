package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

import java.util.List;

/**
 * One entry of {@code data/skill_effects.json} (P10-3): <b>how to read a non-damaging skill's
 * parameters</b>.
 *
 * <p>Why this table has to exist: {@code skills.json} carries a bare {@code param_list}, and the
 * meaning of each entry is written only in the skill's description prose. Measured, there is no
 * general convention — Natasha's heal is a percentage of <i>her</i> Max HP with the flat term at
 * index 3, Luocha's is a percentage of <b>ATK</b> with the flat term at index 1, Asta's speed buff
 * is a flat +36 and March 7th's is +6%. Guessing an index produces a wrong number that reports
 * nothing, so the table is generated and the engine only reads it.
 *
 * <p>Provenance is kept in {@link #getSource()} and {@link #getFormula()}: the parameter indices come
 * from the {@code #N} placeholders in the skill description (the number <i>is</i> the index), and the
 * scaling attribute is cross-checked against {@code ConfigAbility}'s {@code FormulaType} wherever one
 * exists. The generator <b>aborts</b> if the two disagree rather than writing a wrong value.
 */
@Getter
public class SkillEffectSpec {

    /**
     * {@code "Restore"} or {@code "Defence"} — the same vocabulary as {@code skill_effect}.
     */
    @SerializedName("effect")
    private String effect;

    /**
     * What the percentage scales off: {@code healer_max_hp}, {@code target_max_hp}, {@code atk},
     * {@code def}, {@code target_missing_hp}, {@code max_energy} or {@code base} (flat only).
     *
     * <p>{@code healer_*} means the caster's, {@code target_*} the recipient's — a distinction that
     * cannot be dropped: "of Natasha's Max HP" and "of their respective Max HP" look identical once
     * reduced to "Max HP", and mixing them up silently heals for the wrong amount.
     */
    @SerializedName("scale")
    private String scale;

    /**
     * Which parameter row entries to sum, and whether each is a percentage or a flat amount.
     */
    @SerializedName("params")
    private List<Param> params;

    /**
     * The parameter holding the duration, for effects that persist; {@code null} when there is none.
     */
    @SerializedName("turns_param")
    private Integer turnsParam;

    /**
     * The authoritative {@code ConfigAbility} formula this entry was cross-checked against.
     */
    @SerializedName("formula")
    private String formula;

    /**
     * Human-readable provenance. The engine never reads it.
     */
    @SerializedName("source")
    private String source;

    /**
     * One term of the effect: {@code raw = params[index]}, then either scaled or added flat.
     */
    @Getter
    public static class Param {

        /**
         * Index into the skill's parameter row (already 0-based: the description writes {@code #1}).
         */
        @SerializedName("index")
        private int index;

        /**
         * {@code "percent"} (multiply by the scale) or {@code "flat"} (add as-is).
         */
        @SerializedName("kind")
        private String kind;
    }
}
