package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.NumberProvider;

import java.util.List;

/**
 * The <b>numbers</b> one effect can state, as one type - the input of the magnitude layer.
 *
 * <p><b>Why an interface and not a record yet.</b> The magnitude reader ({@link TriggerMagnitudes}, plus
 * {@link NumberProviders}) is what turns a spelling into a value, and it used to take the whole 139-field
 * {@link EffectSpec} - so it could, in principle, read any field, and "which numbers does an effect state" had no
 * answer anywhere. Every method below exists because that reader calls it and for no other reason; the list was
 * extracted from the code rather than designed.
 *
 * <p><b>Two things are deliberately not here.</b> The selector ({@code target} and its thirty prefixes) and the
 * lifetime ({@code turns} / {@code permanent} / {@code until}) are separate vocabularies with their own readers; mixing
 * them in would recreate the one big bean this type exists to break up.
 *
 * <p><b>Why {@link EffectSpec} implements it.</b> So the seam costs nothing today: the reader's parameter type changes
 * and every call site keeps passing the bean. The next step is a parsed record that implements this interface and is
 * built once while the table compiles - at which point the reader stops seeing the bean at all, and an op's args can
 * hold the numbers. That is the whole point of the split, and it is why this is an adapter now rather than a rewrite.
 *
 * <p><b>Note:</b> the getters keep Lombok's names on purpose. Renaming them would make the bean and this interface two
 * spellings of one thing, which is the drift every other part of this refactor is removing.
 */
public interface Magnitude {

    /** @return the flat amount, in points */
    Double getAmount();

    /** @return where the flat amount comes from, when it is not a literal */
    NumberProvider getAmountFrom();

    /** @return the flat amount as a cast skill's parameter index */
    Integer getAmountFromCastParam();

    /** @return whether the base is the amount the event announced */
    Boolean getAmountFromEvent();

    /** @return the flat amount as a skill-row cell ("SKILL:0") */
    String getAmountFromSkillParam();

    /** @return the flat amount as a summon's skill-row cell */
    String getAmountFromSummonParam();

    /** @return the share, as a fraction */
    Double getPercent();

    /** @return where the share comes from, when it is an expression */
    NumberProvider getPercentFrom();

    /** @return the share as a cast skill's parameter index */
    Integer getPercentFromCastParam();

    /** @return the share as a resource, in basis points */
    String getPercentFromResource();

    /** @return the share as a skill-row cell */
    String getPercentFromSkillParam();

    /** @return the share as a summon's skill-row cell */
    String getPercentFromSummonParam();

    /** @return how much each of the owner's ally-states adds to the share */
    Double getPercentPerAllyState();

    /** @return what a share is a share <b>of</b> ({@code self_attr:ATTACK}, {@code owner_max_hp}, ...) */
    String getScale();

    /** @return the whole magnitude as one expression */
    NumberProvider getValue();

    /** @return a derived ceiling's scale */
    String getCapScale();

    /** @return a derived ceiling's share */
    Double getCapPercent();

    /** @return a constant ceiling */
    Double getCapAmount();

    /** @return a base chance, as a fraction of 1 */
    Double getBaseChance();

    /** @return a base chance read out of a skill row */
    String getBaseChanceFromSkillParam();

    /** @return the ally-state whose layers scale the share */
    String getAllyState();

    /** @return the cap on those layers */
    Integer getAllyStateCap();

    /** @return the ally-states a share counts */
    List<String> getAllyStates();

    /** @return the resource's declared maximum */
    Double getMaxPoints();

    /** @return the cid a skill-row cell is read from, when it is not the owner's */
    Integer getSkillParamCid();

    /** @return the data slot a row belongs to */
    Integer getDamageParam();

    /** @return which level of a row to read */
    Integer getDamageLevel();

    /** @return the element the magnitude is stated in */
    String getElement();

    /** @return the damage type the magnitude is stated in */
    String getDamageType();

    /** @return the op, for the error messages this layer raises */
    String getOp();

    /** @return the multi-part base ("100% ATK + 150% DEF") */
    List<EffectSpec.Part> getParts();

    /**
     * @return whether the magnitude is multiplied by "how many units this event hit" ({@code per_target})
     *
     * <p>Here rather than beside the selector vocabulary because it multiplies the <b>number</b>: "each hit gives one
     * stack" is a magnitude of one per hit, not a different magnitude.
     */
    Boolean getPerTarget();

    /**
     * One term of a multi-part base, as a magnitude of its own.
     *
     * <p>Here because the parts <b>are</b> numbers: "100% ATK + 150% DEF" is two shares that get summed, and the reader
     * has to resolve each one the same way it resolves a lone base. It lives on this interface rather than on the bean
     * so the reader never has to hold the bean to walk the terms.
     *
     * @param part one term
     * @return that term, viewed as a magnitude
     */
    Magnitude forPart(EffectSpec.Part part);

    /**
     * The effect's own selector, for the handful of scales that name another unit's attribute
     * ({@code target_max_hp} and friends).
     *
     * <p>⚠ <b>The one selector this layer may read</b>, and it is a leak: a "share of the target's Max HP" needs to know
     * who the target is. It is documented instead of hidden because a parsed {@code Magnitude} will have to carry the
     * resolved unit rather than the spelling, and that is the change this method is waiting for.
     *
     * @return the target selector as written
     */
    String getTarget();
}
