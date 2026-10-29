package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

import java.util.List;

/**
 * One entry of {@code data/skill_effects.json}: <b>how to read a non-damaging skill's
 * parameters</b>.
 *
 * <p>Why this table has to exist: {@code skills.json} carries a bare {@code param_list}, and the
 * meaning of each entry is written only in the skill's description prose. Measured, there is no
 * general convention - Natasha's heal is a percentage of <i>her</i> Max HP with the flat term at
 * index 3, Luocha's is a percentage of <b>ATK</b> with the flat term at index 1, Asta's speed buff
 * is a flat +36 and March th's is +6%. Guessing an index produces a wrong number that reports
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
     * {@code "Restore"} or {@code "Defence"} - the same vocabulary as {@code skill_effect}.
     */
    @SerializedName("effect")
    private String effect;

    /**
     * What the percentage scales off: {@code healer_max_hp}, {@code target_max_hp}, {@code atk},
     * {@code def}, {@code target_missing_hp}, {@code max_energy} or {@code base} (flat only).
     *
     * <p>{@code healer_*} means the caster's, {@code target_*} the recipient's - a distinction that
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
     * The skill's damage <b>segments</b>, as fractions of its own multiplier row (「分裂比」).
     *
     * <p>Reader: 1002 Dan Heng (丹恒) - 「官方最大命中次数 2」 for his basic attack (0.45 + 0.55 of the row's
     * multiplier) and <b>4</b> for his Skill (0.30 + 0.15 + 0.15 + 0.40). The <b>total is unchanged</b> (the fractions sum
     * to 1), and that is the point: what changes is how many damage <b>instances</b> the cast settles - each with its own
     * crit roll and its own events, which is what 「战技造成的伤害<b>触发暴击时</b>」 counts in the game.
     *
     * <p>Note: Absent means "one instance", which is every other skill's behaviour and the state before this field
     * existed. The data is generated from the ability config's segment table; only the rows that were derived are
     * listed, and a skill without an entry is not an approximation - it is the single-instance shape the whole engine
     * used before.
     */
    @SerializedName("segments")
    private List<Double> segments;

    /**
     * The damage <b>parts</b> one parameter row states, in the order the sentence settles them, each reaching its own
     * target shape - or {@code null} for the ordinary reading (the whole row is one shape, taken from
     * {@code skills.json}'s {@code skill_effect}).
     *
     * <p>Reader: 1415 Cyrene (昔涟)'s enhanced basic 「向着爱与明天♪」 (141508), whose sentence is 「对指定敌方单体造成等同于昔涟
     * #3% 生命上限的冰伤，<b>随后</b>对敌方全体造成等同于昔涟 #1% 生命上限的冰伤」 - two instances in one row, and the
     * description's own order is the settlement order. The row's {@code skill_effect} says {@code AoEAttack}, so without
     * this field it settles as one area hit and the single-target half is silently missing; the game's own max-hit column
     * says the row is two instances ({@code skill_max_hits.json}, {@code avatars.1415.Skill11 = 2}).
     *
     * <p>Note: The shares are not in this table - each part names a parameter of the row
     * ({@link DamagePart#getIndex()}), so a level change moves them with it and there is no second copy of the
     * multiplier.
     */
    @SerializedName("damage_parts")
    private List<DamagePart> damageParts;

    /**
     * A share that <b>grows with a resource</b>: 「每有 #3 点【X】，则额外造成 #2%」 - or {@code null} for the ordinary
     * fixed row share.
     *
     * <p>Reader: 11413/1, 长夜's 忆灵技1. The sentence names two more parameters than the ordinary row and the game
     * <b>folds the extra into the same instances</b> - the recorded fight's one cast reads 0.09 where the Lv8 row says
     * 0.06, i.e. {@code (10 / 4) x 0.12} added, not two extra hits. That is why this is a share modifier and not an
     * {@code ADD_DAMAGE} rule: extra instances would each roll their own crit and land their own events, and the game's
     * payload shows six instances, not twelve.
     *
     * <p>Whose resource: the row says 「长夜月当前每有…」 - the <b>summoner's</b>, read through the same "who owns this
     * sheet" rule the damage base uses ({@link com.laosun.aluminium.models.CanHit#damageBase}).
     */
    @SerializedName("resource_share")
    private ResourceShare resourceShare;

    /**
     * A share that is a <b>count</b> of a resource rather than a fraction: 「当前每持有 1 点【X】对主目标造成 #1% 生命上限…，
     * 对其他敌方目标造成 #2%」.
     *
     * <p>Reader: 11413/7, 「迷梦，流失，如露」. Distinct from {@link #getResourceShare()} on purpose - that one <b>adds</b> a
     * step share to a fixed one (「每有 4 点额外造成 12%」), this one <b>multiplies</b> the whole share by how many points
     * are held, and prices the main target separately from the rest. The recorded fight's cast is 3.0 on the main and 1.5
     * on the other target = {@code 0.06 x 50} and {@code 0.03 x 50} at the Lv1 row, so the count and the two parameters
     * are all that is needed.
     */
    @SerializedName("per_resource_share")
    private PerResourceShare perResourceShare;

    /**
     * {@code share = parameter[main or other] x (points held)}, both read at the cast's level.
     */
    @Getter
    public static class PerResourceShare {

        /**
         * The resource's id as the trigger tables spell it - 1413's 「**亿质**」.
         */
        @SerializedName("resource")
        private String resource;

        /**
         * Which parameter prices the <b>aimed</b> target (「对主目标… #1%」 ⇒ 0-based {@code 0}).
         */
        @SerializedName("main_param")
        private int mainParam;

        /**
         * Which parameter prices <b>every other</b> enemy (「对其他敌方目标… #2%」 ⇒ 0-based {@code 1}).
         */
        @SerializedName("other_param")
        private int otherParam;
    }

    /**
     * How a resource turns into extra share: {@code (held / step) x share}, both terms read from the same level row.
     */
    @Getter
    public static class ResourceShare {

        /**
         * The resource's id as the trigger tables spell it - 1413's 「**亿质**」 (the data's own spelling, not 「忆质」).
         */
        @SerializedName("resource")
        private String resource;

        /**
         * Which parameter is the divisor (「每有 #3 点」 ⇒ 0-based {@code 2}), read at the cast's level.
         */
        @SerializedName("step_param")
        private int stepParam;

        /**
         * Which parameter is the extra share (「额外造成 #2%」 ⇒ 0-based {@code 1}), read at the cast's level.
         */
        @SerializedName("share_param")
        private int shareParam;
    }

    /**
     * One damage part of a multi-part row: which shape it reaches, and which parameter of the row it takes.
     */
    @Getter
    public static class DamagePart {

        /**
         * The shape this part reaches, in the {@code skill_effect} vocabulary ({@code "SingleAttack"} /
         * {@code "AoEAttack"}) - the spelling {@link com.laosun.aluminium.enums.SkillEffectType#fromString} parses.
         */
        @SerializedName("shape")
        private String shape;

        /**
         * Index into the skill's parameter row, <b>0-based</b> like {@link Param#getIndex()} (the description writes
         * {@code #1}).
         */
        @SerializedName("index")
        private int index;
    }

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
