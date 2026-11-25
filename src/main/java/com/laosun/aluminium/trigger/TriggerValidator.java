package com.laosun.aluminium.trigger;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.NumberProvider;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.buff.BuffManager;
import com.laosun.aluminium.enums.*;

import java.util.Locale;
import java.util.Set;

/**
 * The rules an effect must satisfy to be loadable -- checked once, when a character's table
 * is compiled, rather than when the rule fires.
 *
 * <p><b>Why this is separate from {@link TriggerInterpreter}.</b> The interpreter answers
 * "what does this effect do when the rule fires"; this answers "may this effect exist at
 * all". They ran as one 6800-line class, and the difference matters because the two halves
 * fail in opposite directions: the interpreter's job is to make a wired op behave, while
 * this one's job is to make an unwritable rule impossible to ship. A rule that names an op
 * nobody implements, a misspelt target selector, or a duration that cannot coexist with its
 * op is rejected here, with the source line, at load time. The alternative -- accepting it
 * and having it do nothing -- is the failure mode the whole closed-set vocabulary exists to
 * prevent, because a rule that never fires reports nothing.
 *
 * <p>Nothing here touches a {@link Battle}: an effect is validated when its file is read,
 * long before there is a battle to run it in. That is also why the helpers below build
 * error messages from the spec rather than from engine state.
 */
final class TriggerValidator {

    private TriggerValidator() {
    }

    /**
     * The ops that read a number and therefore accept {@code value}: the magnitude readers
     * ({@code TriggerMagnitudes.magnitudeOf} / {@code derivedMagnitude} / {@code literalBase} / {@code grantAmount} /
     * {@code constantTerm} / {@code dotMagnitude}), the share readers ({@code shareOf}, where the value is the
     * fraction), and the count readers (rounded to a whole count). An op that reads no number
     * ({@code MODIFY_RULE}) refuses {@code value} at load time - silently ignoring it would be the failure mode this
     * validator exists for.
     */
    private static final Set<String> VALUE_OPS = Set.of(
            "DAMAGE", "ADD_DAMAGE", "HEAL", "SHIELD", "MODIFY_ATTR", "APPLY_DOT", "APPLY_CONTROL",
            "GAIN_ENERGY", "GAIN_RESOURCE", "SPEND_RESOURCE", "REMOVE_RESOURCE", "CONSUME_HP",
            "MODIFY_DAMAGE_TAKEN", "BOOST_DAMAGE", "ADVANCE", "BOOST_TOUGHNESS", "SUPER_BREAK",
            "TICK_DOT", "RAISE_ENERGY_TO",
            "GAIN_SKILL_POINT", "ADD_STACK", "REMOVE_STACK", "REMOVE_BUFF", "DISPEL",
            "RAISE_RESOURCE_CAP", "RAISE_SKILL_HITS", "RAISE_SKILL_POINT_CAP",
            // The energy bar's overflow allowance: 1512's 「终结技最多储存 2 次」 states it as ONE FULL BAR, so the magnitude
            // has to be read off her own Max Energy rather than written as a number.
            "RAISE_ENERGY_BANK",
            "ABSORB_DAMAGE", "RESIST_DEBUFF");

    /**
     * Every legacy magnitude field. Stating one beside {@code value} would be two answers to one question; the pair
     * is refused rather than silently preferring either.
     */
    private static void rejectValueWithLegacyMagnitude(EffectSpec effect, String op, TriggerSpec spec) {
        java.util.List<String> stated = new java.util.ArrayList<>();
        if (effect.getScale() != null && !effect.getScale().isBlank()) {
            stated.add("scale");
        }
        if (effect.getSkill() != null && !effect.getSkill().isBlank()) {
            stated.add("skill");
        }
        if (effect.getDamageParam() != null) {
            stated.add("damage_param");
        }
        if (effect.getDamageLevel() != null) {
            stated.add("damage_level");
        }
        if (effect.getElationFrom() != null && !effect.getElationFrom().isBlank()) {
            stated.add("elation_from");
        }
        if (effect.getPercent() != null) {
            stated.add("percent");
        }
        if (effect.getRatio() != null) {
            stated.add("ratio");
        }
        if (effect.getAmount() != null) {
            stated.add("amount");
        }
        if (effect.getPercentFrom() != null) {
            stated.add("percent_from");
        }
        if (effect.getAmountFrom() != null) {
            stated.add("amount_from");
        }
        if (effect.getPercentFromCastParam() != null) {
            stated.add("percent_from_cast_param");
        }
        if (effect.getPercentFromSkillParam() != null) {
            stated.add("percent_from_skill_param");
        }
        if (effect.getPercentFromSummonParam() != null) {
            stated.add("percent_from_summon_param");
        }
        if (effect.getPercentFromResource() != null) {
            stated.add("percent_from_resource");
        }
        if (effect.getAmountFromCastParam() != null) {
            stated.add("amount_from_cast_param");
        }
        if (effect.getAmountFromSkillParam() != null) {
            stated.add("amount_from_skill_param");
        }
        if (effect.getAmountFromSummonParam() != null) {
            stated.add("amount_from_summon_param");
        }
        if (effect.getAmountFromAttr() != null) {
            stated.add("amount_from_attr");
        }
        if (effect.getAmountFromResource() != null) {
            stated.add("amount_from_resource");
        }
        if (effect.getAmountPercent() != null) {
            stated.add("amount_percent");
        }
        if (Boolean.TRUE.equals(effect.getAmountFromEvent())) {
            stated.add("amount_from_event");
        }
        if (Boolean.TRUE.equals(effect.getAmountFromPrevious())) {
            stated.add("amount_from_previous");
        }
        if (Boolean.TRUE.equals(effect.getSpendAll())) {
            stated.add("spendAll");
        }
        if (Boolean.TRUE.equals(effect.getOverflowOnly())) {
            stated.add("overflowOnly");
        }
        if (!stated.isEmpty()) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"value\" (one whole magnitude) together with "
                            + String.join(" / ", stated) + ": value replaces the scale/percent/amount family, and "
                            + "two spellings of one number have no reading (source: " + spec.getSource() + ")");
        }
    }


    static void validateOne(EffectSpec effect, TriggerSpec spec) {
        validateWhileStacks(effect, spec);
        validateWhileSummonCount(effect, spec);
        validatePrefer(effect, spec);
        // Note: A derived ceiling is read by APPLY_DOT (a DoT's cap), MODIFY_ATTR and - since 1414's 「护盾量可以叠加」 - SHIELD.
        // Every other op refuses it: silently ignoring a field is exactly the kind of mistake these closed sets exist to prevent.
        // ⚠ `cap_amount` (a CONSTANT ceiling) is deliberately not in this check: it is read by MODIFY_DAMAGE_TAKEN as well (1218's
        // 【烬煨】 and 1306's 【幻相】 state one), and listing it here would reject two files that have been correct for months.
        if ((effect.getCapScale() != null || effect.getCapPercent() != null)
                && !"APPLY_DOT".equalsIgnoreCase(String.valueOf(effect.getOp()).trim())
                && !"MODIFY_ATTR".equalsIgnoreCase(String.valueOf(effect.getOp()).trim())
                && !"SHIELD".equalsIgnoreCase(String.valueOf(effect.getOp()).trim())) {
            throw new IllegalArgumentException(
                    "Op " + effect.getOp() + " states a derived ceiling (\"cap_scale\"/\"cap_percent\"), which only "
                            + "APPLY_DOT, MODIFY_ATTR and SHIELD read today (source: " + spec.getSource() + ")");
        }
        // 「最高不超过…的 300%」 only means something for a shield that ADDS to the standing one: a replacing shield has nothing
        // to clamp against, so a ceiling without `add_to_shield` would be a field nobody reads.
        if ("SHIELD".equalsIgnoreCase(String.valueOf(effect.getOp()).trim())
                && !Boolean.TRUE.equals(effect.getAddToShield())
                && (effect.getCapScale() != null || effect.getCapPercent() != null || effect.getCapAmount() != null)) {
            throw new IllegalArgumentException(
                    "Op SHIELD states a ceiling but not \"add_to_shield\": a replacing shield overwrites, so there is nothing to "
                            + "clamp (source: " + spec.getSource() + ")");
        }

        String op = TriggerInterpreter.normalizeOp(effect, spec);
        // `times` repeats the effect itself; DAMAGE states its own number below (a settlement repeated N times), and
        // every other op is repeated by the interpreter's effect loop. A count of nothing is a typo, not a shorthand:
        // 0 repetitions would make the whole effect disappear without a word (the "silently ignored" family).
        if (effect.getTimes() != null && effect.getTimes() < 1 && !"DAMAGE".equals(op)) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"times\" = " + effect.getTimes() + ", which runs nothing: the effect would "
                            + "disappear silently (source: " + spec.getSource() + ")");
        }
        if (effect.getRatio() != null && !"MODIFY_ATTR".equals(op)) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"ratio\", which only MODIFY_ATTR reads: a ratio names one of the rule "
                            + "owner's attributes and how much of it a modifier adds (source: " + spec.getSource() + ")");
        }
        // `value` is the one-expression spelling of a whole magnitude. Validate it here, once, and refuse the ops
        // whose reader only wants a share (they would silently ignore it otherwise).
        if (effect.getValue() != null) {
            if (!VALUE_OPS.contains(op)) {
                throw new IllegalArgumentException(
                        "Op " + op + " states \"value\", which only " + VALUE_OPS + " read: an op that reads a bare "
                                + "share or a count has no place for a whole magnitude "
                                + "(source: " + spec.getSource() + ")");
            }
            NumberProviders.validate(effect.getValue(), "value", op, spec);
            rejectValueWithLegacyMagnitude(effect, op, spec);
        }
        requireBlocksOnlyOnStates(effect, op, spec);
        requireCountsAsOnNamedBuffs(effect, op, spec);
        requireTargetSelector(effect, op, spec);
        // Note: `cast_target` (who a COMMANDED cast is aimed at) is read by CAST_SKILL and by nothing else, so stating it
        // anywhere else is refused rather than dropped silently -- the same shape as the `cap_scale` and `damage_type`
        // checks around it, and the reason both of those exist.
        if (effect.getCastTarget() != null && !effect.getCastTarget().isBlank() && !"CAST_SKILL".equals(op)) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"cast_target\" (" + effect.getCastTarget() + "), but only CAST_SKILL reads "
                            + "it: it names the unit a COMMANDED cast is aimed at, which no other op performs "
                            + "(source: " + spec.getSource() + ")");
        }
        // Note: A damage-type scope is only meaningful for the ops that actually READ it, and one of them accepts it
        // while ignoring it (BOOST_DAMAGE, fixed: "follow-up attacks only" silently raised every hit). The
        // closed set below is the fix's other half -- stating a scope an op cannot honour is refused at load time, never
        // silently dropped.
        if (effect.getDamageType() != null && !effect.getDamageType().isBlank()
                && !TriggerVocabulary.DAMAGE_TYPE_READERS.contains(op)) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"damage_type\", but only " + TriggerVocabulary.DAMAGE_TYPE_READERS
                            + " read it; an op that ignores it would silently apply to every damage type "
                            + "(source: " + spec.getSource() + ")");
        }

        // The same shape for the element scope: only MODIFY_DAMAGE_TAKEN reads it, so stating it anywhere else is refused
        // rather than dropped silently. ⚠ This half was missing when the field was added, and the audit found it: the field
        // was accepted by every op, so a scoped-looking rule on another op was in fact unscoped.
        if (effect.getScopeElement() != null && !effect.getScopeElement().isBlank()
                && !TriggerVocabulary.SCOPE_ELEMENT_READERS.contains(op)) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"scope_element\", but only " + TriggerVocabulary.SCOPE_ELEMENT_READERS
                            + " read it; an op that ignores it would silently apply to every element "
                            + "(source: " + spec.getSource() + ")");
        }
        // The same shape for ADD_DAMAGE's insertion point: only that op reads `after_zones`, and stating it elsewhere
        // would look like a placement while being dropped - the "no unknown argument" hole this file closes one field at a time.
        if (effect.getAfterZones() != null && !"ADD_DAMAGE".equalsIgnoreCase(op)) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"after_zones\", but only ADD_DAMAGE reads it: this op would add its number "
                            + "into the base layer and leave the flag unread (source: " + spec.getSource() + ")");
        }
        // `scope_category` narrows a damage-taken zone to one CAST CATEGORY («受到的终结技伤害提高 8%»): only
        // MODIFY_DAMAGE_TAKEN reads it, and an op that ignored it would apply to every category -- the silent reading.
        if (effect.getScopeCategory() != null && !effect.getScopeCategory().isBlank()
                && !"MODIFY_DAMAGE_TAKEN".equalsIgnoreCase(op)) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"scope_category\", which only MODIFY_DAMAGE_TAKEN reads; ignoring it would apply the "
                            + "effect to every cast category (source: " + spec.getSource() + ")");
        }        // `from_self` narrows a damage-taken zone to the owner's own damage. Only MODIFY_DAMAGE_TAKEN reads it, and only
        // its VULNERABILITY arm does: a negative share builds a ReductionBuff, which has no source filter, so accepting it
        // there would silently ignore the scope.
        if (effect.getFromSelf() != null) {
            if (!"MODIFY_DAMAGE_TAKEN".equals(op)) {
                throw new IllegalArgumentException(
                        "Op " + op + " states \"from_self\", but only MODIFY_DAMAGE_TAKEN reads it: it narrows a "
                                + "damage-taken zone to the damage the rule owner dealt (source: " + spec.getSource() + ")");
            }
            if (effect.getPercent() != null && effect.getPercent() < 0) {
                throw new IllegalArgumentException(
                        "Op " + op + " states \"from_self\" with a negative share (" + effect.getPercent()
                                + "), but the reduction arm has no attacker filter; state a positive share for a "
                                + "vulnerability (source: " + spec.getSource() + ")");
            }
        }        // `skills` is RAISE_SKILL_LEVEL's plural spelling; on every other op it would be dropped silently, which is
        // the same failure the two scope guards above close.
        if (effect.getSkills() != null && !"RAISE_SKILL_LEVEL".equals(op)) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"skills\", but only RAISE_SKILL_LEVEL reads it: it names the skill slots "
                            + "whose levels a battle raises (source: " + spec.getSource() + ")");
        }
        // `per_steps` and `SPEND_STEPS` are the two halves of one primitive - "every N points of a counter" as a repeat
        // COUNT and as a SPEND - so both spellings are checked here, at LOAD time, for every op that may state them.
        if (effect.getPerSteps() != null && !effect.getPerSteps().isBlank()) {
            requireCounterSteps(effect.getPerSteps().trim(), "per_steps", spec);
            if (Boolean.TRUE.equals(effect.getAmountFromPrevious())) {
                // `previousCredited` is what the PREVIOUS effect booked, and a repeat overwrites it once per iteration:
                // this effect's own read would see the last repetition's number rather than the previous effect's.
                throw new IllegalArgumentException(
                        "Op " + op + " states both \"per_steps\" and \"amount_from_previous\": a repeat overwrites the "
                                + "amount the previous effect booked once per iteration, so the share would be read off the "
                                + "last repetition instead of off the previous effect (source: " + spec.getSource() + ")");
            }
        }
        // `steps` is SPEND_STEPS's own spelling (it consumes milestones); on any other op it would be silently ignored.
        if (effect.getSteps() != null && !effect.getSteps().isBlank() && !"SPEND_STEPS".equals(op)) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"steps\", which only SPEND_STEPS reads: it consumes the milestones a "
                            + "\"per_steps\" effect counted (source: " + spec.getSource() + ")");
        }
        // `cast_counted_laughs` is a fact about a COMMANDED CAST ("make it cast, counting 20 laughs"), so only
        // CAST_SKILL reads it - the same closed-set reasoning as `cast_target` above. A negative count is refused
        // outright: it would fold a laugh factor below 1, which no sentence means.
        if (effect.getCastCountedLaughs() != null) {
            if (!"CAST_SKILL".equals(op)) {
                throw new IllegalArgumentException(
                        "Op " + op + " states \"cast_counted_laughs\" (" + effect.getCastCountedLaughs() + "), but only "
                                + "CAST_SKILL reads it: it is the laugh count the cast IT PERFORMS is settled with, and "
                                + "no other op performs one (source: " + spec.getSource() + ")");
            }
            if (effect.getCastCountedLaughs() < 0) {
                throw new IllegalArgumentException(
                        "Op " + op + " states a negative \"cast_counted_laughs\" (" + effect.getCastCountedLaughs()
                                + "); a laugh count is a number of points (source: " + spec.getSource() + ")");
            }
        }
        if (TriggerVocabulary.PLANNED.contains(op)) {
            throw new IllegalArgumentException(
                    "Trigger op '" + op + "' is declared in the roadmap but not wired yet "
                            + "(see ROADMAP / TriggerInterpreter); rule source:" + spec.getSource());
        }
        if (!TriggerVocabulary.WIRED.contains(op)) {
            throw new IllegalArgumentException(
                    "Unknown trigger op '" + op + "' (source: " + spec.getSource() + ")");
        }
        TriggerOperator operator = TriggerOperator.parse(op);
        if (operator == null) {
            throw new IllegalArgumentException("Trigger op " + op + " is not in TriggerOperator; the validator and the dispatcher share that vocabulary");
        }
        // No per-op switch. Every wired op validates itself now (its handler's `validate`), and what is left here is
        // the catch-all an op with no handler used to get - kept as a plain statement rather than a one-arm switch,
        // because it is a rule about the registry, not about an op. It is unreachable while every wired op has a
        // handler; TriggerDispatchCoverageTest asserts that, so this is a guard against the two lists disagreeing.
        // ⚠ Applying it to a HANDLED op would refuse what the handler allows: APPLY_DOT states `max_stacks`
        // ("at most N layers"), which this generic stack-family refusal rejects - measured by 1108 Sampo failing to
        // load when the default stopped distinguishing the two.
        if (OpRegistry.find(operator) == null) {
            requireNoStackArguments(effect, op, spec);
        }
    }

    /**
     * Validates {@code "ticks_on"} at load time: one value, spelled exactly, and only on ops that create a timed
     * buff - the same "fail while the file is read" discipline as every other argument.
     *
     * <p>Note: <b>A refusal can be invalidated by a later widening, and this one was</b>. Until
     * {@code "summon"} existed, this method also refused {@code "ticks_on"} together with {@code "permanent": true},
     * because "nothing counts it down" made the field meaningless. That premise is gone: the same field is now the
     * <b>anchor</b> as well, so a permanent buff anchored to another unit means exactly "<i>that unit</b> disappears,
     * so the buff ends</i>" - which is a sentence two documents actually write (1402's [至高之姿], and the
     * dead-dragon family). Read the <b>reason</b> a refusal was written, not just the code: what it was protecting against
     * may have stopped being true.
     *
     * <p>{@code "blocks"} belongs here for the same reason: it is a property of a STATE, so only the op that applies one
     * may state it - a ban written on {@code MODIFY_ATTR} would be accepted and never read, which is exactly the silent
     * hole the closed sets exist for.
     */
    static void requireBlocksOnlyOnStates(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getBlocks() == null || effect.getBlocks().isBlank()) {
            return;
        }
        if (!"APPLY_BUFF".equalsIgnoreCase(op)) {
            throw new IllegalArgumentException("Op " + op + " states \"blocks\": \"" + effect.getBlocks()
                    + "\"; only APPLY_BUFF applies a state, so only it can say what that state suppresses"
                    + " (source: " + spec.getSource() + ")");
        }
    }

    /**
     * {@code counts_as} / {@code counts_as_requires} belong to the buff an effect CREATES, so only the two ops that
     * create a <b>named</b> one may state them: {@code APPLY_BUFF} (a state) and {@code ADD_STACK} (a counter, whose
     * name already answers {@code has_state}). On any other op the fields would be accepted by the parser and read by
     * nobody - the silent hole this file has a closed-set rule about.
     *
     * <p>The pair is checked together: {@code counts_as_requires} alone would attach a prerequisite to nothing, which is
     * a rule that reads as "only while X" and applies always.
     */
    static void requireCountsAsOnNamedBuffs(EffectSpec effect, String op, TriggerSpec spec) {
        boolean hasNames = effect.getCountsAs() != null && !effect.getCountsAs().isEmpty();
        boolean hasRequires = effect.getCountsAsRequires() != null && !effect.getCountsAsRequires().isBlank();
        if (!hasNames) {
            if (hasRequires) {
                throw new IllegalArgumentException("Op " + op + " states \"counts_as_requires\": \""
                        + effect.getCountsAsRequires() + "\" without \"counts_as\": the prerequisite only narrows a list "
                        + "of alias names, so on its own it would apply to nothing (source: " + spec.getSource() + ")");
            }
            return;
        }
        if (!"APPLY_BUFF".equalsIgnoreCase(op) && !"ADD_STACK".equalsIgnoreCase(op)) {
            throw new IllegalArgumentException("Op " + op + " states \"counts_as\", but only APPLY_BUFF and ADD_STACK "
                    + "create the named buff the aliases are stamped onto; on any other op they would never be read "
                    + "(source: " + spec.getSource() + ")");
        }
        for (String name : effect.getCountsAs()) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Op " + op + " has a blank entry in \"counts_as\": a state name is "
                        + "the word {@code has_state} is asked with, so an empty one can never match "
                        + "(source: " + spec.getSource() + ")");
            }
        }
    }

    static void requireTickOwner(EffectSpec effect, String op, TriggerSpec spec) {
        String ticksOn = effect.getTicksOn();
        if (ticksOn == null || ticksOn.isBlank()) {
            return;
        }
        String spelling = ticksOn.trim();
        if (!"self".equals(spelling) && !"summon".equals(spelling)) {
            throw new IllegalArgumentException(
                    "Op " + op + " has \"ticks_on\": \"" + ticksOn + "\", which is not a spelling it knows; the "
                            + "clocks a document has asked for are the RULE OWNER's (\"ticks_on\": \"self\") and its "
                            + "MEMOSPRITE's (\"ticks_on\": \"summon\" - \"<the summon> disappears, so the buff "
                            + "ends\") (say nothing for the ordinary case: the unit that carries the buff) "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    /**
     * The lifetimes a rule states, spelled the way the file writes them, for error messages.
     */
    static String spellUntil(EffectSpec effect) {
        return effect.getUntil().stream().map(name -> "\"" + name + "\"").toList().toString();
    }

    /**
     * Validates the magnitude of a {@code HEAL} / {@code SHIELD} effect: either a flat {@code amount}, or
     * {@code scale} + {@code percent}.
     *
     * <p>Both spellings of each mistake are refused, because both would otherwise be silently ignored by Gson:
     * an {@code amount} together with a {@code scale} (which wins?), a {@code scale} without a
     * {@code percent} ("some share of a Max HP"), a {@code percent} without a {@code scale} (a percentage of
     * what?), and a {@code scale} together with {@code per_target} (a share of a Max HP is already per unit).
     */
    static void requireAmountOrScale(EffectSpec effect, String op, TriggerSpec spec,
                                     Set<String> scales, String what) {
        if (effect.getValue() != null) {
            return;
        }
        // The flat half is validated here too: this family's scaled path never calls `requireAmount`.
        requireSingleFlatSource(effect, op, spec);
        String scale = effect.getScale() == null ? null : effect.getScale().trim();
        if (scale == null || scale.isEmpty()) {
            // A percentage without a scale is refused *before* the missing amount, because the author clearly
            // meant the scaled spelling: "requires amount" alone would send them looking in the wrong place.
            // Note: All four share spellings are refused here, not just `percent`: a share read out of a skill row
            // ("#1% of DEF") needs a scale exactly as much as a literal does, and accepting it would fall through to
            // `requireAmount`, whose message ("requires amount") points at the wrong field.
            if (statesShare(effect)) {
                throw new IllegalArgumentException(
                        "Op " + op + " states a share but no \"scale\": a share (\"percent\" / "
                                + "\"percent_from_cast_param\" / \"percent_from_skill_param\" / \"percent_from_resource\")"
                                + " says how much of SOMETHING, and the scale says what that something is; the flat "
                                + "spelling states \"amount\" instead "
                                + "(source: " + spec.getSource() + ")");
            }
            requireAmount(effect, op, spec);
            return;
        }
        // Note: `amount` together with a `scale` is the shape "equal to X% of DEF + 60": the scale is the share OF
        // something, and the amount is the flat addend. It is refused here ("which one wins?"), which is why
        // no shipped rule states both -- so allowing it now changes no existing content.
        // Note: "no share" is asked with `statesShare`, not `getPercent`: a share read out of the owner's own skill row
        // (`percent_from_skill_param`) is a share, and a rule that mixes a level-aware share with a literal constant is
        // well-formed -- refusing it would send its author looking for a `percent` they deliberately did not write.
        if (effect.getAmount() != null && !statesShare(effect)) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"scale\" together with \"amount\" but no \"percent\": the scale says what "
                            + "the share is OF, so the share itself is missing "
                            + "(source: " + spec.getSource() + ")");
        }
        // The same refusal for the level-aware flat spelling: it is an addend to a share, not a magnitude by itself.
        if (effect.getAmountFromSkillParam() != null && !statesShare(effect)) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"scale\" together with \"amount_from_skill_param\" but no share: the scale "
                            + "says what the share is OF, and #<index> is the flat addend to it "
                            + "(source: " + spec.getSource() + ")");
        }
        if (!scales.contains(scale) && !scale.startsWith(TriggerTable.CAST_APPLIED_PREFIX)
                && !scale.startsWith(TriggerInterpreter.PARTY_RESOURCE_PREFIX)
                && !scale.startsWith(TriggerInterpreter.SELF_RESOURCE_PREFIX)) {
            throw new IllegalArgumentException(
                    "Op " + op + " has unknown \"scale\": '" + effect.getScale() + "'; known scales for this op "
                            + "are " + String.join(", ", scales.stream().sorted().toList())
                            + " (source: " + spec.getSource() + ")");
        }
        if (scale.startsWith(TriggerTable.CAST_APPLIED_PREFIX)) {
            requireCastAppliedScale(effect, op, spec, scale);
        }
        requirePercent(effect, op, spec);
        if (effect.getPerTarget() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " cannot combine \"scale\" with \"per_target\": the scale is already a share "
                            + "of one unit's " + what + " (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates {@code "scale": "cast_applied:&lt;state name&gt;"} - a per-<b>landed</b>-application multiplier.
     *
     * <p>Two things are checked, and both are mistakes that would otherwise be silent:
     * <ul>
     *   <li><b>the state name is one the engine rolls for</b> ({@link BuffManager#isRolledStateName}): frozen / entangled /
     *       imprisoned and the four DOT states. A typo would make the counter answer 0 forever - a rule that pays nothing,
     *       with nothing to report;</li>
     *   <li><b>the rule fires on a cast</b>. The record only exists while a cast's own events are being delivered
     *       ({@code Battle.beginCast} ... {@code endCastOutcome}); on {@code TAKING_HIT}, {@code TURN_START} or
     *       {@code KILL} it would read the <i>previous</i> cast's numbers or 0 - the kind of stale value this project
     *       refuses to leave implicit. The four cast events are the ones {@code SkillExecutor} fires inside that
     *       window.</li>
     * </ul>
     */
    static void requireCastAppliedScale(EffectSpec effect, String op, TriggerSpec spec, String scale) {
        String state = scale.substring(TriggerTable.CAST_APPLIED_PREFIX.length()).trim();
        if (!BuffManager.isRolledStateName(state)) {
            throw new IllegalArgumentException(
                    "Op " + op + " counts applications of '" + state + "', which is not a state this engine rolls "
                            + "for; known names are the control states (" + String.join(" / ",
                            Constant.CONTROL_STATES.keySet().stream().sorted().toList())
                            + ") and the DOT states (burn / shock / bleed / wind shear), spelled as the documents spell them "
                            + "(source: " + spec.getSource() + ")");
        }
        TriggerEvent event = TriggerEvent.fromString(spec.getOn());
        if (event == null || !CAST_EVENTS.contains(event)) {
            throw new IllegalArgumentException(
                    "Op " + op + " reads \"scale\": \"" + scale + "\" — the number of targets THIS CAST applied the "
                            + "state to — but the rule fires on " + spec.getOn() + ", outside the window in which that "
                            + "count exists (it is cleared once the cast's own events have been delivered). Cast "
                            + "events: " + CAST_EVENTS.stream().map(Enum::name).sorted()
                            .collect(java.util.stream.Collectors.joining(", "))
                            + " (source: " + spec.getSource() + ")");
        }
    }

    /**
     * "at most N stacks": the only stack-family field a DOT may state, and it must be a real ceiling.
     */
    static void requireDotStackCap(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.stackCap() == null) {
            return;
        }
        if (effect.stackCap() <= 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"max_stacks\": " + effect.stackCap()
                            + ", but a layer ceiling is a positive count (say nothing for an uncapped DOT) (source: "
                            + spec.getSource() + ")");
        }
    }

    /**
     * Refuses every field a {@code START_COUNTDOWN} effect does not read.
     */
    static void rejectCountdownExtras(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getPercent() != null || effect.getScale() != null || effect.getTurns() != null
                || effect.getAttribute() != null || effect.getElement() != null
                || effect.getControl() != null || effect.getBaseChance() != null
                || effect.getKind() != null || effect.getStacks() != null
                || effect.getDamageParam() != null || effect.getDamageLevel() != null
                || effect.getAsAttack() != null || effect.getPerTarget() != null
                || effect.getCritRate() != null || effect.getCritDamage() != null
                || effect.getRule() != null || effect.getSkill() != null
                || effect.getResource() != null || effect.getAmount() != null
                || effect.getPermanent() != null || effect.getUntil() != null
                || effect.getTicksOn() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " places one countdown and states nothing else: \"speed\" (its fixed speed) and "
                            + "optionally \"buff\" (the name it shows in logs) "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    /**
     * Exactly <b>one</b> amendment per {@code MODIFY_RULE}: a firing count, a probability, a value or a duration.
     *
     * <p>Note: Stated rather than inferred: two at once would make "which number did the author mean" a guess, and the
     * guess would be a silent one (the extra field would simply be ignored).
     */
    static void requireOneAmendment(EffectSpec effect, String op, TriggerSpec spec) {
        int stated = 0;
        for (Object candidate : new Object[]{effect.getAmount(), effect.getBaseChance(), effect.getPercent(),
                effect.getEffectPercent(), effect.getEffectPercentScale(), effect.getEffectPercentFromResource(),
                effect.getEffectAmount(), effect.getEffectTurns(), effect.getEffectMaxStacks(), effect.getCooldownDelta()}) {
            if (candidate != null) {
                stated++;
            }
        }
        if (stated != 1) {
            throw new IllegalArgumentException(
                    "Op " + op + " must state exactly ONE amendment -- \"amount\" (firings per turn), \"base_chance\" "
                            + "(probability), \"effect_percent\" (raise a value), \"effect_turns\" (raise a "
                            + "duration), \"effect_amount\" (layers an application grants) or \"cooldown_delta\" (raise or lower a cooldown) -- but " + stated
                            + " were stated (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates the optional {@code scale} of a {@code MODIFY_ATTR}, and the flat {@code amount} that comes with it.
     *
     * <p>Two silent holes are closed here, both of the "the rule loads and does something other than what the file
     * says" kind:
     * <ul>
     *   <li>a {@code scale} this op cannot read (a {@code HEAL}/{@code SHIELD} scale such as {@code target_max_hp},
     *       or a typo) would otherwise be ignored, and the modifier would use {@code percent} as a literal - a
     *       number that has nothing to do with the sentence;</li>
     *   <li>an {@code amount} <b>without</b> a scale. It is ignored outright (nothing on this op read it),
     *       and it is exactly what an author writes for "plus a flat part"; now that it means that, the case it
     *       cannot mean is refused instead of being dropped.</li>
     * </ul>
     */
    static void requireDerivedScale(EffectSpec effect, String op, TriggerSpec spec, boolean allowFlat) {
        requireDerivedScale(effect, op, spec, allowFlat, false);
    }

    /**
     * The same, optionally accepting {@code percent} BESIDE one row/counter share as a FRACTION of it
     * (「原伤害的 50%」). Only the damage path passes {@code true}; see {@link #requirePercent(EffectSpec, String, TriggerSpec, boolean)}.
     */
    static void requireDerivedScale(EffectSpec effect, String op, TriggerSpec spec, boolean allowFlat,
                                    boolean percentMayScaleRow) {
        if (effect.getValue() != null || effect.getRatio() != null) {
            return;
        }
        boolean derived = effect.getScale() != null && !effect.getScale().isBlank();
        if (!derived) {
            if (effect.getAmount() != null && !allowFlat) {
                throw new IllegalArgumentException(
                        "Op " + op + " states \"amount\" without a \"scale\": a plain modifier's magnitude is "
                                + "\"percent\", so the amount would be ignored. Either say what it is a share of "
                                + "(\"scale\": \"" + TriggerConditionParser.SELF_ATTR_PREFIX + "BREAKING_EFFECT\" plus "
                                + "\"percent\") or write the number into \"percent\" "
                                + "(source: " + spec.getSource() + ")");
            }
            return;
        }
        // A counter scale names a counter, not an attribute: check the shape and stop there.
        String scale = effect.getScale().trim();
        // The owner MEMOSPRITE's attribute. Accepted HERE and not in `scaleAttribute`, because that one is shared
        // with the DAMAGE path, where the subject is the attacker -- teaching it this spelling there would make damage quietly
        // read the wrong unit. The attribute name is still checked, so a typo is loud at load time.
        // A parameter of the skill that produced the event. "equal to #1% of Demiurge (德谬歌)'s Max HP": #1 is the skill own parameter
        // and it RUNS WITH ITS LEVEL, so a literal `percent` would freeze one level -- the damage path reads it the same way, through
        // `multiplierOf`. The index is checked here; whether the event has such a skill can only be known when it fires.
        if (scale.startsWith(TriggerTable.CAST_SKILL_PARAM_PREFIX)) {
            String raw2 = scale.substring(TriggerTable.CAST_SKILL_PARAM_PREFIX.length()).trim();
            if (raw2.isEmpty()) {
                throw new IllegalArgumentException(
                        "Op " + op + " scales off a skill parameter but names no index: \"" + scale
                                + "\" (source: " + spec.getSource() + ")");
            }
            try {
                if (Integer.parseInt(raw2) < 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException notAnIndex) {
                throw new IllegalArgumentException(
                        "Op " + op + " scales off skill parameter \"" + raw2
                                + "\", which is not a zero-based index (source: " + spec.getSource() + ")");
            }
            requirePercent(effect, op, spec, percentMayScaleRow);
            return;
        }
        // The ACTOR's own attribute (1415 memosprite skill 10, whose share is of the CASTER's Max HP). Accepted here for
        // the same reason as the line below: `scaleAttribute` is shared with the DAMAGE path, where the subject is already the attacker
        // and a second spelling there would be a second meaning.
        if (scale.startsWith(TriggerTable.ACTOR_ATTR_PREFIX)) {
            String actorAttr = scale.substring(TriggerTable.ACTOR_ATTR_PREFIX.length()).trim();
            if (actorAttr.isEmpty()) {
                throw new IllegalArgumentException(
                        "Op " + op + " scales off the actor but names no attribute: \"" + scale
                                + "\" (source: " + spec.getSource() + ")");
            }
            AttributeType.fromString(actorAttr);
            requirePercent(effect, op, spec, percentMayScaleRow);
            return;
        }
        // The owner's OWN skill, named by slot: `<SKILLTYPE>:<index>`. Checked here for the reason the family below gives --
        // `scaleAttribute` is shared with the DAMAGE path, where the subject is already the attacker.
        // Note: The parsing lives in `requireSkillParamSpelling`, which `amount_from_skill_param` shares: one spelling,
        // one definition, so the two cannot drift.
        if (scale.startsWith(TriggerTable.SKILL_PARAM_PREFIX)) {
            requireSkillParamSpelling(scale.substring(TriggerTable.SKILL_PARAM_PREFIX.length()).trim(), "scale", op, spec);
            requirePercent(effect, op, spec, percentMayScaleRow);
            return;
        }
        if (scale.startsWith(TriggerTable.SUMMON_ATTR_PREFIX)) {
            String summonAttr = scale.substring(TriggerTable.SUMMON_ATTR_PREFIX.length()).trim();
            if (summonAttr.isEmpty()) {
                throw new IllegalArgumentException(
                        "Op " + op + " scales off the owner's memosprite but names no attribute: \"" + scale
                                + "\" (source: " + spec.getSource() + ")");
            }
            AttributeType.fromString(summonAttr);
            requirePercent(effect, op, spec, percentMayScaleRow);
            return;
        }
        // The STATE-HOLDER's own attribute (`holder_attr:<STATE>:<ATTRIBUTE>`). Accepted/checked here for the reason the
        // actor branch above gives, plus one of its own: unlike `holder_of:` in `target` (where naming the state IS the
        // whole answer), a scale must also say WHICH stat, because "use [美学底本]'s stats" (1503) does not say it and
        // defaulting to one would silently invent the game's choice. The split is on the LAST colon: state names
        // themselves contain colons in this project's content (1503's 渲染理性，解构欢愉 is checked below).
        if (scale.startsWith(TriggerTable.HOLDER_ATTR_PREFIX)) {
            String rest = scale.substring(TriggerTable.HOLDER_ATTR_PREFIX.length());
            int lastColon = rest.lastIndexOf(':');
            if (lastColon < 0 || rest.substring(0, lastColon).isBlank() || rest.substring(lastColon + 1).isBlank()) {
                throw new IllegalArgumentException(
                        "Op " + op + " scales off the holder of a state but does not name it as <STATE>:<ATTRIBUTE>: \""
                                + scale + "\"; e.g. \"holder_attr:同袍:DEFENCE\" (source: " + spec.getSource() + ")");
            }
            AttributeType.fromString(rest.substring(lastColon + 1).trim());
            requirePercent(effect, op, spec, percentMayScaleRow);
            return;
        }
        if (scale.startsWith(TriggerConditionParser.SELF_STACKS_PREFIX)
                || scale.startsWith(TriggerConditionParser.TARGET_STACKS_PREFIX)) {
            String name = scale.substring(scale.indexOf(':') + 1).trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException(
                        "Op " + op + " scales off a counter but names none: \"" + scale + "\" "
                                + "(source: " + spec.getSource() + ")");
            }
            requirePercent(effect, op, spec, percentMayScaleRow);
            return;
        }
        // The spelling and (for the attribute family) the name; also re-checks `percent`, because a scale with no
        // magnitude is not a number ("some share of my Speed" says nothing about how much).
        TriggerMagnitudes.scaleAttribute(effect, op, spec);
        requirePercent(effect, op, spec, percentMayScaleRow);
    }

    /**
     * Validates an effect's optional {@code target} selector <b>at load time</b>.
     *
     * <p>This closes a silent-typo hole: the resolver falls back to "the owner" for anything
     * it did not recognise, so {@code "target": "atacker"} behaved exactly like {@code "self"} -
     * the rule fired, nothing was reported, and the character just quietly did the wrong thing. The
     * condition variables are already a closed set for the same reason; the selector is now too.
     *
     * <p>Absent means {@code "self"}, which is the common case and stays implicit so the JSON can
     * stay terse.
     */
    static void requireTargetSelector(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getTarget() == null || effect.getTarget().isBlank()) {
            if (effect.getCastTarget() != null && !effect.getCastTarget().isBlank()) {
                requireSelectorSpelling(effect.getCastTarget(), "cast_target", op, spec);
            }
            return;
        }
        // A selector FAMILY, admitted by prefix: `ally_cid:<cid>` names a character outright. The closed set below stays closed --
        // this is an explicit door, not a fallback, which is why it is checked BEFORE the membership test.
        if (effect.getTarget().startsWith(TriggerTargets.ALLIES_OF_PATH_PREFIX)) {
            // Same door, for paths: the name must be one the engine knows, so a typo is refused at load rather than silently targeting nobody.
            String rest = effect.getTarget().substring(TriggerTargets.ALLIES_OF_PATH_PREFIX.length()).trim();
            if (TriggerTargets.pathNamed(rest) == null) {
                throw new IllegalArgumentException(
                        "Op " + op + " names \"" + effect.getTarget() + "\", whose part after \""
                                + TriggerTargets.ALLIES_OF_PATH_PREFIX + "\" must be a path -- its Chinese name or the enum's (source: "
                                + spec.getSource() + ")");
            }
            return;
        }
        if (effect.getTarget().startsWith(TriggerTargets.ALLY_CID_PREFIX)) {
            String rest = effect.getTarget().substring(TriggerTargets.ALLY_CID_PREFIX.length()).trim();
            try {
                Integer.parseInt(rest);
            } catch (NumberFormatException notANumber) {
                throw new IllegalArgumentException(
                        "Op " + op + " names \"" + effect.getTarget() + "\", whose part after \"" + TriggerTargets.ALLY_CID_PREFIX
                                + "\" must be a cid (source: " + spec.getSource() + ")");
            }
            return;
        }
        requireSelectorSpelling(effect.getTarget(), "target", op, spec);
        if (effect.getCastTarget() != null && !effect.getCastTarget().isBlank()) {
            requireSelectorSpelling(effect.getCastTarget(), "cast_target", op, spec);
        }
    }

    /**
     * One selector spelling, checked against the closed set plus the parameterised {@code holder_of:} prefix.
     *
     * <p>Split out of {@link #requireTargetSelector} when {@code cast_target} arrived: the two fields
     * take the <b>same</b> vocabulary, and two copies of this check would be able to drift apart.
     *
     * @param value the selector as written
     * @param field the field's name, for the message ({@code target} / {@code cast_target})
     */
    static void requireSelectorSpelling(String value, String field, String op, TriggerSpec spec) {
        String selector = value.trim().toLowerCase(Locale.ROOT);
        if (!TriggerVocabulary.TARGET_SELECTORS.contains(selector) && !selector.startsWith(TriggerVocabulary.HOLDER_OF_PREFIX)
                && !selector.startsWith(TriggerVocabulary.LOWEST_STACKS_ENEMY_PREFIX)
                && !selector.startsWith(TriggerVocabulary.HIGHEST_STACKS_ENEMY_PREFIX)
                && !selector.startsWith(TriggerTargets.ALLIES_OF_PATH_EXCEPT_PREFIX)
                && !selector.startsWith(TriggerTargets.ENEMIES_WITH_STATE_PREFIX)) {
            throw new IllegalArgumentException(
                    "Op " + op + " names an unknown \"" + field + "\" selector '" + value
                            + "' (known: " + String.join(" / ", TriggerVocabulary.TARGET_SELECTORS.stream().sorted().toList())
                            + ", the state-holder spelling " + TriggerVocabulary.HOLDER_OF_PREFIX + "<state>"
                            + ", and the marked-enemy spelling " + TriggerTargets.ENEMIES_WITH_STATE_PREFIX + "<state>"
                            + "); falling back to the owner would make a typo behave like self "
                            + "(source: " + spec.getSource() + ")");
        }
        // Note: The prefix must be spelled exactly (lower-case, with the colon): the state name behind it is DATA and is
        // read as written, so a selector that only matches case-insensitively would slice the name in the wrong place.
        if (value.trim().startsWith(TriggerVocabulary.HOLDER_OF_PREFIX) && value.trim().length() == TriggerVocabulary.HOLDER_OF_PREFIX.length()) {
            throw new IllegalArgumentException(
                    "Op " + op + " writes \"" + value.trim() + "\" with no state after \""
                            + TriggerVocabulary.HOLDER_OF_PREFIX + "\"; write the state whose holder the effect speaks about, e.g. \""
                            + TriggerVocabulary.HOLDER_OF_PREFIX + "同袍\" (source: " + spec.getSource() + ")");
        }
    }

    static void requireAmount(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getValue() != null) {
            return;
        }
        requireSingleFlatSource(effect, op, spec);
        if (effect.getAmount() == null && effect.getAmountFrom() == null
                && effect.getAmountFromAttr() == null
                && effect.getAmountFromResource() == null
                && !Boolean.TRUE.equals(effect.getAmountFromEvent())
                && !Boolean.TRUE.equals(effect.getAmountFromPrevious())
                && effect.getAmountFromSkillParam() == null
                && effect.getAmountFromCastParam() == null
                && effect.getAmountFromSummonParam() == null) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"amount\" -- a literal, or one of \"amount_from_attr\" / "
                            + "\"amount_from_resource\" / \"amount_from_skill_param\" / \"amount_from_cast_param\" / "
                            + "\"amount_from_summon_param\" / \"amount_from_event\" / "
                            + "\"amount_from_previous\" (source: " + spec.getSource() + ")");
        }
    }

    /**
     * The flat part of a magnitude is stated <b>once</b>, and a skill parameter is spelled either
     * {@code "<SKILLTYPE>:<index>"} (a character's own slot) or {@code "<DATA SLOT>:<index>"} (its memosprite's).
     *
     * <p>Called from both magnitude families, because the scaled path ({@code scale} + share + {@code amount}) never
     * reaches {@link #requireAmount}: a check that lived only there would let
     * {@code "scale" + "amount" + "amount_from_skill_param"} through, and Gson would then have two flat answers with
     * nothing to say which won.
     */
    static void requireSingleFlatSource(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getValue() != null) {
            return;
        }
        java.util.List<String> stated = new java.util.ArrayList<>();
        if (effect.getAmount() != null) {
            stated.add("amount");
        }
        if (effect.getAmountFromCastParam() != null) {
            stated.add("amount_from_cast_param");
        }
        if (effect.getAmountFromSkillParam() != null) {
            stated.add("amount_from_skill_param");
        }
        if (effect.getAmountFromSummonParam() != null) {
            stated.add("amount_from_summon_param");
        }
        if (effect.getAmountFrom() != null) {
            stated.add("amount_from");
        }
        if (stated.size() > 1) {
            throw new IllegalArgumentException(
                    "Op " + op + " states more than one flat source (" + String.join(" / ", stated)
                            + "): the constant term comes from exactly one of them "
                            + "(source: " + spec.getSource() + ")");
        }
        if (effect.getAmountFromSkillParam() != null) {
            requireSkillParamSpelling(effect.getAmountFromSkillParam(), "amount_from_skill_param", op, spec);
        }
        if (effect.getAmountFromSummonParam() != null) {
            requireSummonParamSpelling(effect.getAmountFromSummonParam(), "amount_from_summon_param", op, spec);
        }
        NumberProviders.validate(effect.getAmountFrom(), "amount_from", op, spec);
    }

    /**
     * Validates the {@code "<SKILLTYPE>:<index>"} spelling shared by {@code percent_from_skill_param} and
     * {@code amount_from_skill_param}.
     *
     * <p>Checked at load time for the reason the {@code scale} family gives: the slot is a closed vocabulary
     * ({@link SkillType}) and the index is a column, so a typo would otherwise read a row that has no such member,
     * or none at all, only once the rule fires -- or worse, read the neighbouring member silently.
     *
     * @param spelled the value as the file states it
     * @param field   the JSON key, for the message (so the author knows which of the two spellings to look at)
     * @param op      the op, for the message
     * @param spec    the rule, for its {@code source}
     * @return the parsed slot
     */
    static SkillType requireSkillParamSpelling(String spelled, String field, String op, TriggerSpec spec) {
        String[] parts = spelled.split(":", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"" + field + "\": \"" + spelled + "\", which does not name it as "
                            + "<SKILLTYPE>:<index> (e.g. \"SKILL:3\") (source: " + spec.getSource() + ")");
        }
        SkillType slot;
        try {
            slot = SkillType.valueOf(parts[0].trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException notASlot) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"" + field + "\": \"" + spelled + "\", whose slot \"" + parts[0]
                            + "\" is not a SkillType (source: " + spec.getSource() + ")");
        }
        try {
            if (Integer.parseInt(parts[1].trim()) < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException notAnIndex) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"" + field + "\": \"" + spelled + "\", whose \"" + parts[1]
                            + "\" is not a zero-based index (source: " + spec.getSource() + ")");
        }
        return slot;
    }

    /**
     * Validates that an amount is present <b>and positive</b>.
     *
     * <p>Used by the ops where zero is not a legal no-op but a silent bug: {@code REMOVE_STACK amount: 0}
     * (or a negative) would load happily and remove nothing, which is the "the rule fires and nothing
     * happens" symptom this project keeps closing. Ops where a zero amount is meaningful on purpose (a
     * {@code HEAL 0} a content author might use as a placeholder) keep using {@link #requireAmount}.
     */
    static void requirePositiveAmount(EffectSpec effect, String op, TriggerSpec spec) {
        requireAmount(effect, op, spec);
        if (effect.getValue() != null) {
            // The provider is the count; a literal `amount` is absent by construction. A constant zero is caught
            // by the value's own validation only if it is a literal; a provider cannot be inspected at load time.
            return;
        }
        if (effect.getAmount() <= 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " needs a positive \"amount\" but has " + effect.getAmount()
                            + "; a removal of 0 would load and silently do nothing "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    /**
     * A {@code value} that is a literal fraction must be in {@code (0, 1]} for the ops that take a share.
     * A non-literal provider cannot be read before the battle exists; it is accepted and its reader decides.
     */
    static void requireValueFraction(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getValue() == null) {
            return;
        }
        NumberProvider provider = effect.getValue();
        if (!"const".equalsIgnoreCase(String.valueOf(provider.getType()).trim())) {
            return;
        }
        Double written = provider.getValue();
        double value = written == null ? Double.NaN : written;
        if (!(value > 0 && value <= 1)) {
            throw new IllegalArgumentException(
                    "Op " + op + " takes a share in (0, 1], but \"value\" is the constant " + written
                            + " (0.35 = 35%, 1.0 = 100%); a share of 0 would provably do nothing "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    /*
     * Whether the effect states a <b>share</b>, in any of its four spellings.
     *
     * <p>It exists so that "is a share stated?" has one answer: {@code requirePercent} accepts four spellings, and a
     * caller that asked only about {@code percent} would call a {@code percent_from_skill_param} rule "neither"
     * (measured once: that shape NPEs on a null {@code amount} -- see {@code PercentFromSkillParamTest}).
     */
    /**
     * Whether the effect states a <b>share</b>, in any of its five spellings.
     *
     * <p>Note: The definition moved to {@link TriggerMagnitudes#statesShare} because the READER is what breaks when a
     * spelling is missing from it ({@code MODIFY_ATTR}'s derived/flat fork). This is the delegate, so the loader and
     * the interpreter cannot answer the question differently.
     */
    static boolean statesShare(EffectSpec effect) {
        return TriggerMagnitudes.statesShare(effect);
    }

    /**
     * Validates the {@code "<DATA SLOT>:<index>"} spelling of the memosprite spellings
     * ({@code percent_from_summon_param} / {@code amount_from_summon_param}).
     *
     * <p>The sibling of {@link #requireSkillParamSpelling} for the units whose skills are keyed by an int data slot
     * instead of a {@code SkillType}: the slot is a positive number (the game's servant table starts at 1), the index is
     * zero-based, and both are checked here so a typo is a load-time failure rather than a fire-time one.
     */
    static void requireSummonParamSpelling(String spelled, String field, String op, TriggerSpec spec) {
        String[] parts = spelled.split(":", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"" + field + "\": \"" + spelled + "\", which does not name it as "
                            + "<DATA SLOT>:<index> (e.g. \"1:0\") (source: " + spec.getSource() + ")");
        }
        try {
            if (Integer.parseInt(parts[0].trim()) <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException notASlot) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"" + field + "\": \"" + spelled + "\", whose slot \"" + parts[0]
                            + "\" is not a memosprite data slot (a positive number, e.g. 1) "
                            + "(source: " + spec.getSource() + ")");
        }
        try {
            if (Integer.parseInt(parts[1].trim()) < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException notAnIndex) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"" + field + "\": \"" + spelled + "\", whose \"" + parts[1]
                            + "\" is not a zero-based index (source: " + spec.getSource() + ")");
        }
    }

    static void requirePercent(EffectSpec effect, String op, TriggerSpec spec) {
        requirePercent(effect, op, spec, false);
    }

    /**
     * The same, optionally allowing {@code percent} BESIDE one row/counter share, as a fraction OF it.
     *
     * <p>Only the damage path passes {@code true}, and only where the base is an attribute share: there the pair means
     * 「原伤害的 50%」 - the row's share, times that fraction (reader: 1206 Sushang (素裳)'s extra 【剑势】rolls, whose base
     * would otherwise freeze at the Lv10 row and under-count by 9% once Eidolon 5 raises the skill). How the pair is
     * resolved lives in {@code TriggerMagnitudes.shareOf}; what this check keeps is the other half of the old guard:
     * two ROW spellings are still refused, because a row and a counter cannot both be the share.
     *
     * @param percentMayScaleRow {@code true} to accept {@code percent} as a multiplier on the single row share
     */
    static void requirePercent(EffectSpec effect, String op, TriggerSpec spec, boolean percentMayScaleRow) {
        if (effect.getValue() != null) {
            return;
        }
        // Note: All five spellings are collected before anything is refused, so the message can name them and the
        // "exactly one" rule is stated once. The earlier version was a chain of ifs, and each new spelling had to
        // remember to check the ones before it -- which is how `percent_from_resource` once slipped through.
        java.util.List<String> stated = new java.util.ArrayList<>();
        if (effect.getPercent() != null) {
            stated.add("percent");
        }
        if (effect.getPercentFromCastParam() != null) {
            stated.add("percent_from_cast_param");
        }
        if (effect.getPercentFromSkillParam() != null) {
            stated.add("percent_from_skill_param");
        }
        if (effect.getPercentFromResource() != null) {
            stated.add("percent_from_resource");
        }
        if (effect.getPercentFromSummonParam() != null) {
            stated.add("percent_from_summon_param");
        }
        if (effect.getPercentFrom() != null) {
            stated.add("percent_from");
        }
        // "50% of the original": `percent` (the fraction) beside exactly one row/counter share (the base).
        boolean rowFraction = percentMayScaleRow && effect.getPercent() != null && stated.size() == 2;
        if (stated.size() > 1 && !rowFraction) {
            throw new IllegalArgumentException(
                    "Op " + op + " states more than one share (" + String.join(" / ", stated)
                            + "); the share comes from exactly one of them (source: " + spec.getSource() + ")");
        }
        if (stated.isEmpty()) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"percent\" - or one of its spellings "
                            + "(\"percent_from_cast_param\" / \"percent_from_skill_param\" / "
                            + "\"percent_from_summon_param\" / \"percent_from_resource\") "
                            + "(source: " + spec.getSource() + ")");
        }
        // The spellings are checked HERE rather than only where they are read: a rule whose slot or index is malformed
        // would otherwise load happily and fail the first time it fires (or read the neighbouring column silently).
        if (effect.getPercentFromSkillParam() != null) {
            requireSkillParamSpelling(effect.getPercentFromSkillParam(), "percent_from_skill_param", op, spec);
        }
        if (effect.getPercentFromSummonParam() != null) {
            requireSummonParamSpelling(effect.getPercentFromSummonParam(), "percent_from_summon_param", op, spec);
        }
        NumberProviders.validate(effect.getPercentFrom(), "percent_from", op, spec);
    }

    /**
     * Validates that an effect does <b>not</b> claim a duration.
     *
     * <p>Used by the ops whose effect is over the moment it is applied ({@code BOOST_DAMAGE} changes one damage
     * instance). A {@code turns} written there would be silently ignored by Gson and by the interpreter - the
     * author would see a rule that loads and behaves as if the field were not there.
     */
    static void requireNoDuration(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getTurns() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " has no duration (it acts on a single moment), but states \"turns\": "
                            + effect.getTurns() + " (source: " + spec.getSource() + ")");
        }
        if (Boolean.TRUE.equals(effect.getPermanent())) {
            // The same hole as `turns`, one field over: `permanent` would be read, validated, and then ignored,
            // so the author would believe a permanent effect was installed. Only ops that create a buff may say
            // it, and those do not come through here.
            throw new IllegalArgumentException(
                    "Op " + op + " creates no buff, so \"permanent\" has nothing to keep alive; it acts on a "
                            + "single moment (source: " + spec.getSource() + ")");
        }
        if (TriggerLifetimes.eventBound(effect)) {
            throw new IllegalArgumentException(
                    "Op " + op + " creates no buff, so it has nothing an \"until\" could end; it states "
                            + "\"until\": " + spellUntil(effect) + " (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates that an effect is <b>not</b> given a {@code target}.
     *
     * <p>Used by ops that act on a unit no selector could name - {@code SUMMON} brings out the rule owner's
     * own memosprite, so a {@code target} there would be read by Gson, validated by
     * {@link #requireTargetSelector} (it is a real selector) and then <b>ignored</b>: the author would get a
     * rule that loads, fires and does something other than what the file says.
     *
     * <p>Note: The same hole exists for the ops that credit the owner by definition ({@code GAIN_ENERGY},
     * {@code GAIN_SKILL_POINT}, {@code BOOST_DAMAGE}, ...): they ignore a stray {@code target} today. No shipped
     * rule does that (checked), so it is latent rather than live, and it is registered rather than
     * fixed here because widening the guard would change the accepted vocabulary of seven ops at once.
     */
    static void requireNoTarget(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getTarget() != null && !effect.getTarget().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " does not take a \"target\" (it acts on the rule's own owner), but states "
                            + "\"target\": \"" + effect.getTarget() + "\" (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates that an op is on the one event that hands it what it acts on.
     *
     * <p>The "fail at load, not mid-battle" rule again: {@code BOOST_DAMAGE} changes the damage instance being
     * settled and only {@link TriggerEvent#DEALING_DAMAGE} hands one over; {@code DELEGATE_DAMAGE} changes the cast
     * being set up and only {@link TriggerEvent#CAST_SETUP} hands one over. A rule on any other event would load,
     * fire, and have nothing to change - so it is refused while the file is read.
     */
    static void requireEvent(TriggerSpec spec, String op, TriggerEvent expected) {
        TriggerEvent actual = TriggerEvent.fromString(spec.getOn());
        if (actual != expected) {
            throw new IllegalArgumentException(
                    "Op " + op + " only means something on " + expected.value() + ": that is the event that hands "
                            + "over what it changes. This rule is on " + spec.getOn()
                            + " (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates that a percentage is present and <b>does something</b>.
     *
     * <p>For {@code MODIFY_DAMAGE_TAKEN} zero is the trap: it would attach a buff that multiplies nothing,
     * so the rule would load, fire and be invisible. The sign is meaningful (positive = vulnerability,
     * negative = reduction), so only zero is refused.
     */
    static void requireNonZeroPercent(EffectSpec effect, String op, TriggerSpec spec) {
        requirePercent(effect, op, spec);
        // Note: null-safe: a rule may state its share as `percent_from_resource`, in which case `percent` is null and this comparison unboxes it.
        if (effect.getPercent() != null && effect.getPercent() == 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " has \"percent\": 0, which would attach a buff that changes nothing; the "
                            + "magnitude is the point (positive = damage taken is raised, negative = damage taken is lowered) "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates the {@code attribute} name of a {@code MODIFY_ATTR} / {@code COMMAND_SUMMON} effect
     * <b>at load time</b>.
     *
     * <p>The "fail at load, not mid-battle" rule (the same one behind the op vocabulary) applies to
     * arguments too: a misspelled attribute must be reported when the table is read, not when the
     * character finally takes the action that fires the rule.
     *
     * <p>The four builder-only {@code *_PERCENT} variants are rejected here as well, because
     * {@link com.laosun.aluminium.entity.CanHit#getAttribute(AttributeType)} returns {@code null}
     * for them: targeting one would raise a {@code NullPointerException} in the middle of a battle
     * instead of "this attribute cannot hold a buff".
     */
    static void requireAttribute(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getAttribute() == null || effect.getAttribute().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"attribute\" (e.g. ATTACK / DEFENCE / SPEED / CRIT_ATTACK) "
                            + "(source: " + spec.getSource() + ")");
        }
        AttributeType attribute;
        try {
            attribute = AttributeType.fromString(effect.getAttribute());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Op " + op + " names an unknown attribute '" + effect.getAttribute()
                            + "' (source: " + spec.getSource() + ")");
        }
        if (attribute.isPercentVariant()) {
            throw new IllegalArgumentException(
                    "Op " + op + " cannot use '" + effect.getAttribute()
                            + "': it is a builder-only input key, not a runtime attribute, so reading it "
                            + "mid-battle would fail (or silently do nothing). Name the base attribute "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates that an {@code APPLY_BUFF} effect names the state it applies <b>at load time</b>.
     *
     * <p>Same discipline as {@link #requireAttribute}: a missing {@code buff} would otherwise reach
     * {@code StateBuff}, whose constructor rejects it - but that would be a mid-battle exception in the
     * middle of a turn, instead of "this rule file is wrong" while it is being read.
     */
    static void requireBuff(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getBuff() == null || effect.getBuff().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"buff\" (the state name as the rule text spells it, e.g. "
                            + "\"协奏\") (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates how long the buff lasts - <b>exactly one</b> of {@code turns}, {@code permanent: true} and
     * {@code until}.
     *
     * <p>There is deliberately no default: an omitted duration would be either "forever" (wrong: most
     * buffs in this game expire) or a number this class invented. Making the author write it is the
     * same call as {@code damage_param} having no default.
     *
     * <p>Stating <b>two</b> is rejected for the same reason a typo is: the answers disagree, and
     * silently letting one win would produce a rule that does not do what its text says. That includes
     * {@code until} + {@code turns} - the one expires on an event, the other on a turn boundary.
     */
    static void requireDuration(EffectSpec effect, String op, TriggerSpec spec) {
        boolean permanent = Boolean.TRUE.equals(effect.getPermanent());
        boolean hasUntil = TriggerLifetimes.eventBound(effect);
        if (permanent && effect.getTurns() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " has both \"turns\" (" + effect.getTurns()
                            + ") and \"permanent\": true; they are two different durations and only "
                            + "one may be stated (source: " + spec.getSource() + ")");
        }
        if (hasUntil && (effect.getTurns() != null || permanent)) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"until\": " + spellUntil(effect) + " together with "
                            + (permanent ? "\"permanent\": true" : "\"turns\": " + effect.getTurns())
                            + "; \"until\" IS the duration (the buff ends when its owner does that), so the "
                            + "other one has to go (source: " + spec.getSource() + ")");
        }
        if (hasUntil) {
            // Every name is checked, not just the first: "普攻或战技" written as a list must not let a typo in
            // the second entry load (the buff would then end on one event while the file claims two).
            for (String name : effect.getUntil()) {
                String normalized = name.trim().toLowerCase(Locale.ROOT);
                // `next_skill:<slot>`: the lifetime that names ONE row (「下一次强化普攻」), not a category. Its parameter
                // is the data slot a cast reports; "8x" or "0" would load and then never end, so the number is checked here.
                if (normalized.startsWith(TriggerLifetimes.NEXT_SKILL_SLOT_PREFIX)) {
                    String slot = normalized.substring(TriggerLifetimes.NEXT_SKILL_SLOT_PREFIX.length()).trim();
                    try {
                        if (Integer.parseInt(slot) <= 0) {
                            throw new NumberFormatException();
                        }
                    } catch (NumberFormatException notANumber) {
                        throw new IllegalArgumentException(
                                "Op " + op + " has \"until\": '" + name + "', whose slot is not a positive integer; "
                                        + "write it as \"next_skill:8\" (the data slot a cast reports) "
                                        + "(source: " + spec.getSource() + ")");
                    }
                    continue;
                }
                if (!LIFETIMES.contains(normalized)) {
                    throw new IllegalArgumentException(
                            "Op " + op + " has unknown \"until\": '" + name + "' in " + spellUntil(effect)
                                    + "; known lifetimes are "
                                    + String.join(", ", LIFETIMES.stream().sorted().toList())
                                    + " (or \"next_skill:<slot>\", see TRIGGER.md) "
                                    + "(source: " + spec.getSource() + ")");
                }
            }
            return;
        }
        if (permanent) {
            return;
        }
        if (effect.getTurns() == null) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"turns\" (how long the buff lasts), \"permanent\": true (until "
                            + "the battle ends) or \"until\" (until its owner attacks, e.g. "
                            + "\"next_attack\"); there is no default (source: " + spec.getSource() + ")");
        }
        if (effect.getTurns() <= 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " has a non-positive \"turns\" (" + effect.getTurns()
                            + "); such a buff would expire before it could do anything. Use "
                            + "\"permanent\": true for an effect with no turn limit "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates the optional stack cap of a {@code MODIFY_ATTR} effect <b>at load time</b>.
     *
     * <p>Same discipline as the rest of the vocabulary ("reject loudly at load, naming the phase"): a
     * cap of {@code 0} would create a modifier that never applies, a negative one is meaningless, and a
     * misspelled argument name would otherwise be ignored by Gson and quietly leave the rule
     * non-stacking - the exact "rule fires, nothing accumulates" symptom the author would never notice.
     *
     * <p>Also rejects the two spellings being stated at once, and any use of the argument on an op that
     * has no stacking concept.
     */
    static void requireStackCap(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getMaxStacks() != null && effect.getStacks() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " states both \"max_stacks\" and its alias \"stacks\"; use one "
                            + "(source: " + spec.getSource() + ")");
        }
        Integer cap = effect.stackCap();
        if (cap == null) {
            return;
        }
        if (cap <= 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " has a non-positive stack cap (" + cap
                            + "); such a modifier would never apply (source: " + spec.getSource() + ")");
        }
        if (cap > Constant.MAX_STACKS_LIMIT) {
            throw new IllegalArgumentException(
                    "Op " + op + " has a stack cap of " + cap + ", above the engine's limit of "
                            + Constant.MAX_STACKS_LIMIT + " (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Rejects the stacking / "until the battle ends" arguments on ops that do not have those concepts.
     *
     * <p>A Gson field is simply {@code null} when the JSON omits it, which means an argument written on
     * the <b>wrong op</b> is silently ignored - the author sees the rule load and the effect never
     * stack. That is the same class of silent failure the closed op vocabulary exists to prevent, so the
     * arguments are checked here rather than being read only by {@code MODIFY_ATTR}.
     *
     * <p>Note: The two arguments do <b>not</b> belong to the same set of ops, which is why they are checked
     * separately. {@code permanent} is a <i>duration</i>, and both buff-granting ops have one:
     * {@code MODIFY_ATTR} grants a modifier and {@code APPLY_BUFF} grants a state, and "until the battle ends" is a
     * real duration for a state (Jingliu (镜流)'s [转魄] lasts the whole battle). {@code max_stacks} is a
     * <i>modifier</i> concept and stays exclusive to {@code MODIFY_ATTR}: two states of the same name refresh
     * each other rather than accumulating.
     */
    static void requireNoStackArguments(EffectSpec effect, String op, TriggerSpec spec) {
        // Note: Relaxed for a STACKABLE STATE: `max_stacks` is otherwise refused on every op but MODIFY_ATTR, and a
        // stackable state needs a cap -- that is the whole point of "counted into that state".
        if (!Boolean.TRUE.equals(effect.getStackable())
                && (effect.getMaxStacks() != null || effect.getStacks() != null)) {
            throw new IllegalArgumentException(
                    "Op " + op + " does not support \"max_stacks\"/\"stacks\"; only MODIFY_ATTR "
                            + "accumulates (source: " + spec.getSource() + ")");
        }
        if (Boolean.TRUE.equals(effect.getPermanent()) && !TriggerVocabulary.OPS_WITH_DURATION.contains(op)) {
            throw new IllegalArgumentException(
                    "Op " + op + " does not support \"permanent\"; only "
                            + String.join(" / ", TriggerVocabulary.OPS_WITH_DURATION.stream().sorted().toList())
                            + " has a duration (source: " + spec.getSource() + ")");
        }
    }

    /**
     * The same parse, for the LOAD time callers: a message about a rule has to name the rule.
     */
    static void requireCounterSteps(String written, String spelling, TriggerSpec spec) {
        try {
            TriggerInterpreter.parseCounterSteps(written, spelling);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException(malformed.getMessage() + " (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates the state an {@code APPLY_CONTROL} names, at load time, against the engine's closed table.
     *
     * <p>A control is a <b>known state</b>, not a free-form name: frozen is one thing (it blocks acting), and a rule
     * that misspelled it would attach nothing at all - the same silent-failure shape the target-selector and
     * condition-variable closed sets exist to close. The message lists the names so the author has the fix in hand.
     */
    static void requireControl(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getControl() == null || effect.getControl().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"control\" (which state to apply; known: "
                            + String.join(" / ", Constant.CONTROL_STATES.keySet().stream().sorted().toList())
                            + ") (source: " + spec.getSource() + ")");
        }
        if (!Constant.CONTROL_STATES.containsKey(effect.getControl().trim())) {
            throw new IllegalArgumentException(
                    "Op " + op + " names the control state '" + effect.getControl() + "', which the engine does "
                            + "not know (known: "
                            + String.join(" / ", Constant.CONTROL_STATES.keySet().stream().sorted().toList())
                            + "); a misspelled state would attach nothing at all "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates the duration of an {@code APPLY_CONTROL} (required and positive).
     * <p>Required rather than defaulted: "enter the frozen state" with no turn count would be a <b>permanent</b> lock -
     * a wrong mechanic that reads like a working rule, which is the one thing this vocabulary refuses. The
     * documents always state it ("lasting 1 turn").
     * The delta of an EXTEND_BUFF: non-zero and signed (the game does `SetModifierValue{Add, LifeTime}` on a named modifier).
     */
    static void requireSignedTurns(EffectSpec effect, String op, TriggerSpec spec) {
        Integer turns = effect.getTurns();
        if (turns == null || turns == 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires a non-zero \"turns\" (add turns, or a negative number to take them away); zero would "
                            + "read like a working rule and change nothing (source: " + spec.getSource() + ")");
        }
    }

    static void requirePositiveTurns(EffectSpec effect, String op, TriggerSpec spec) {
        Integer turns = effect.getTurns();
        if (turns == null) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"turns\" (how many of the victim's turns the state lasts, e.g. "
                            + "\"lasting 1 turn\"); without it the control would never end "
                            + "(source: " + spec.getSource() + ")");
        }
        if (turns <= 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"turns\": " + turns + ", but a control has to last at least one of "
                            + "the victim's turns (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates {@code base_chance} (base chance) when stated: a fraction of 1, and above 0.
     *
     * <p>Absent means "always lands", which is how a rule with no roll is written - so {@code 0} is a mistake
     * rather than a way to spell "never" (a state that can never land is not a mechanic), and {@code 1} is legal
     * and simply means the pipeline still runs (effect hit rate can never make it <i>more</i> than certain).
     */
    static void requireBaseChance(EffectSpec effect, String op, TriggerSpec spec) {
        Double chance = effect.getBaseChance();
        if (chance == null) {
            return;
        }
        if (!(chance > 0) || chance > 1) {
            throw new IllegalArgumentException(
                    "Op " + op + " has \"base_chance\": " + chance + ", but a base chance is a fraction of 1 "
                            + "(0.5 = \"50% base chance\"); omit the field entirely for a state that always lands "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    /**
     * Refuses the magnitude family ({@code amount} / {@code scale} / {@code percent} / {@code attribute} /
     * {@code buff}) on an op that has no use for any of them.
     * <p>Same reasoning as {@code requireNoTarget} and {@code requireNoStackArguments}: a field the interpreter
     * never reads is a rule that says one thing and does another. The message names the field, because
     * "which of my six fields was ignored" is otherwise a guessing game.
     * Refuses the fields that would be a <b>second reading</b> of a skill's row, on the ops that cast a named skill
     * through the engine's own {@code SkillExecutor} path ({@code CAST_SKILL}).
     *
     * <p>Why this is a refusal rather than "just ignore it": the op reads the multiplier from the row's first column,
     * the neighbouring column for a blast, the element, the base attribute and the damage type from the skill's own
     * data. An author who writes {@code damage_param} is asking for a different reading -- and the honest answer is
     * no, because a second reading is how this op came to be broken in three ways at once (see {@code castSkill}).
     * A field the engine silently ignores is the failure mode this project ranks worst.
     */
    static void requireNoRowArguments(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getDamageParam() != null || effect.getDamageLevel() != null || effect.getAttribute() != null
                || effect.getElement() != null || effect.getAmount() != null || effect.getScale() != null
                || effect.getPercent() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " casts the named skill with that skill's OWN row, so it takes no "
                            + "\"damage_param\" / \"damage_level\" / \"attribute\" / \"element\" / \"amount\" / "
                            + "\"scale\" / \"percent\": those would be a second reading of numbers the engine "
                            + "already reads once, and the engine's own cast path would ignore them without a word "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates a state's own per-turn damage ({@code element} + a magnitude) or its absence, on
     * {@code APPLY_CONTROL}.
     *
     * <p>Two shapes are refused rather than resolved:
     * <ul>
     *   <li>a magnitude with <b>no element</b> - a number nothing can attach as damage (the author probably meant
     *       a bare {@code APPLY_DOT}, which is a different op);</li>
     *   <li>an element with <b>no magnitude</b> - a DOT that would settle 0 damage every turn, which reads as a
     *       working rule and does nothing.</li>
     * </ul>
     * Everything else ({@code attribute} / {@code buff}) is refused too: this op reads neither.
     */
    static void requireStateDamage(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getAttribute() != null || effect.getBuff() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " takes a control state, its turns, an optional base chance and an optional "
                            + "per-turn damage ({element} + amount/scale/percent); it has no \"attribute\" / "
                            + "\"buff\" (source: " + spec.getSource() + ")");
        }
        boolean anyMagnitude = effect.getAmount() != null || effect.getScale() != null
                || effect.getPercent() != null || effect.getValue() != null;
        if (effect.getElement() == null || effect.getElement().isBlank()) {
            if (anyMagnitude) {
                throw new IllegalArgumentException(
                        "Op " + op + " states a magnitude (amount/scale/percent) but no \"element\": there is "
                                + "nothing to attach that damage to. A state that deals no damage states neither; "
                                + "a damage-over-time with no control is a separate op, APPLY_DOT "
                                + "(source: " + spec.getSource() + ")");
            }
            return;
        }
        requireElement(effect, op, spec, false);
        requireDotMagnitude(effect, op, spec);
    }

    /*
     * Validates the element of a per-turn damage: one of {@code DamageElement}'s spellings.
     * <p>An unknown name would otherwise become a DOT that applies to nothing - {@code DamageElement.fromString}
     * answers {@code null} for "Unknown" and for typos alike, so the check has to be here.
     * Validates an {@code element} slot against {@link DamageElement}.
     *
     * <p>{@link TriggerInterpreter#ATTACKER_ELEMENT} is accepted only when the caller says so: the damage ops pass
     * {@code true} because they always have an attacker to read, while {@code APPLY_DOT} /
     * {@code APPLY_CONTROL} pass {@code false} -- their instance is the rule's own, so "the attacker's element"
     * is a sentence with no subject.
     */
    /**
     * Validates the optional {@code scope_element} of a damage-taken zone: an optional argument, but a CLOSED-SET one.
     *
     * <p>⚠ Optional, so an absent one passes. But a misspelling must not: {@link DamageElement#fromString} answers {@code null}
     * for "Unknown" and for typos alike, and {@code parseScopeElement} turns that null into "no scope", i.e. a zone that applies to
     * EVERY element -- a wrong number with no symptom, which is the hole the closed-set checks in this class exist to close.
     *
     * @param effect the effect
     * @param op     the op, for the message
     * @param spec   the rule, for the message
     */
    static void requireScopeElement(EffectSpec effect, String op, TriggerSpec spec) {
        String raw = effect.getScopeElement();
        if (raw == null || raw.isBlank()) {
            return;
        }
        if (DamageElement.fromString(raw.trim()) == null) {
            throw new IllegalArgumentException(
                    "Op " + op + " names scope_element '" + raw + "', which is not a DamageElement spelling "
                            + "(e.g. Fire / Ice / Wind / Thunder / Physical / Quantum / Imaginary); an unknown element would "
                            + "widen the zone to every element instead of narrowing it (source: " + spec.getSource() + ")");
        }
    }

    static void requireElement(EffectSpec effect, String op, TriggerSpec spec, boolean allowAttackerElement) {
        // `TICK_DOT` may name NO element: "其当前承受的**所有**持续伤害立即产生 75% 的伤害" (1005 Kafka's Skill)
        // settles one instance per DoT state, so the element is not the rule's to state. Every other op still must.
        if ("TICK_DOT".equals(op) && (effect.getElement() == null || effect.getElement().isBlank())) {
            return;
        }
        if (allowAttackerElement && effect.getElement() != null
                && TriggerInterpreter.ATTACKER_ELEMENT.equalsIgnoreCase(effect.getElement().trim())) {
            return;
        }
        if (effect.getElement() == null || effect.getElement().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"element\" (which damage element the per-turn damage deals, e.g. "
                            + "\"Ice\" / \"Fire\" / \"Quantum\") (source: " + spec.getSource() + ")");
        }
        if (DamageElement.fromString(effect.getElement()) == null) {
            throw new IllegalArgumentException(
                    "Op " + op + " names element '" + effect.getElement() + "', which is not a DamageElement "
                            + "spelling (e.g. Ice / Fire / Wind / Thunder / Physical / Quantum / Imaginary); an "
                            + "unknown element would attach nothing (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates that the effect states <b>true damage</b>.
     *
     * <p>Required by the one scale that reads an <b>already settled</b> total
     * ({@link TriggerInterpreter#SETTLED_DAMAGE_THIS_EVENT}): that number has been through every zone once. A rider settled through the
     * zones again would land at an unknown multiple of the share its text states, and - unlike {@code original_damage},
     * which divides by the ONE triggering instance's own factor - a sum over several instances has no single factor to
     * divide out. True damage settles at exactly 1, which is what makes a stated "20%" mean 20%.
     */
    static void requireTrueDamage(EffectSpec effect, String op, TriggerSpec spec) {
        String stated = effect.getDamageType() == null ? "" : effect.getDamageType().trim();
        if (!"TRUE".equalsIgnoreCase(stated)) {
            throw new IllegalArgumentException(
                    "Op " + op + " scales by the SETTLED total damage of the firing, so it must state \"damage_type\": "
                            + "\"TRUE\": the total has already been through every damage zone, and a rider that went "
                            + "through them again would settle at an unknown multiple of the share it states (it says '"
                            + (stated.isEmpty() ? "(none)" : stated) + "') (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates how much damage each of the victim's turns takes: a flat {@code amount}, or a derived value
     * ({@code scale} + {@code percent}, optionally plus {@code amount} as a constant term), or neither-and-both
     * is refused.
     *
     * <p>Note: Derived from the <b>rule owner</b> ({@code self_attr:<ATTRIBUTE>} / {@code self_max_energy}) and
     * computed when the effect lands, then frozen into the DOT - the same "computed once when it triggers" semantics the other
     * derived values follow (a later buff on the applier must not change damage that is already burning).
     */
    static void requireDotMagnitude(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getValue() != null) {
            return;
        }
        // The flat spelling is shared with every other magnitude: one source, and a `<SKILLTYPE>:<index>` spelling that
        // is checked here rather than when the DOT fires. Note: this family used to REFUSE the level-aware constant
        // because `dotMagnitude` read only a literal; that reader now goes through `constantTerm`, which is the same
        // one the derived magnitudes use.
        requireSingleFlatSource(effect, op, spec);
        boolean derived = effect.getScale() != null;
        if (!derived) {
            if (effect.getAmount() == null && effect.getAmountFromSkillParam() == null) {
                throw new IllegalArgumentException(
                        "Op " + op + " requires a magnitude for its per-turn damage: either \"amount\" (a flat "
                                + "number, or \"amount_from_skill_param\" for one from the owner's own skill row) or "
                                + "\"scale\" + \"percent\" (a share of one of the rule owner's own "
                                + "attributes, e.g. \"self_attr:ATTACK\" with 0.6) "
                                + "(source: " + spec.getSource() + ")");
            }
            if (effect.getPercent() != null) {
                throw new IllegalArgumentException(
                        "Op " + op + " states \"percent\" without \"scale\": a percent is a share of *something*, "
                                + "and the something is the scale (source: " + spec.getSource() + ")");
            }
            return;
        }
        // The spelling, and (for the attribute family) the name: `scaleAttribute` is the one reader of that
        // vocabulary, shared with MODIFY_ATTR's derived value so the two cannot drift.
        // Note: "equal to 24.00% of its own Max HP" (1111 卢卡): the VICTIM's own maximum is the one scale
        // outside the owner's attributes a DOT may name. Accepted HERE rather than by widening
        // scaleAttribute, whose other reader (MODIFY_ATTR) must keep refusing it.
        if ("target_max_hp".equals(effect.getScale().trim())) {
            requirePercent(effect, op, spec);
            return;
        }
        TriggerMagnitudes.scaleAttribute(effect, op, spec);
        requirePercent(effect, op, spec);
    }

    /**
     * Refuses the arguments {@code APPLY_DOT} has no use for ({@code attribute} / {@code buff} / {@code skill} /
     * {@code damage_param}).
     *
     * <p>Same reasoning as {@code requireNoTarget} / {@code requireNoStackArguments}: a field the interpreter never
     * reads is a rule that says one thing and does another.
     */
    static void requireNoMalformedArguments(EffectSpec effect, String op, TriggerSpec spec) {
        // `buff` is allowed as the DOT's NAME: "make it enter the [游丝] state" names a state the element
        // alone cannot express. It is a name only -- the DOT's identity for merging stays its element.
        if (effect.getAttribute() != null || effect.getSkill() != null
                || effect.getDamageParam() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " takes an element, a magnitude and a duration; it has no \"attribute\" / "
                            + "\"buff\" / \"skill\" / \"damage_param\" (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates the <b>one</b> filter {@code EXTEND_BUFF} needs: {@code "buff"} (a state's name, or shield) or
     * {@code "attribute"} (the attribute a modifier sits on).
     *
     * <p>Note: <b>Exactly one, and it is required.</b> Without a filter the op would mean "lengthen everything I have on
     * that unit", and that is a wrong number waiting to happen: Bronya (布洛妮娅)'s DEFENCE trace buff from {@code BATTLE_START}
     * is still ticking when she casts her Skill, so her Eidolon (星魂) 6 would silently lengthen that one too. Stating both is
     * refused for the same reason - there is no reading in which a buff is named by a state <i>and</i> an attribute.
     *
     * <p>The <b>name</b> is checked against the closed spellings the engine knows (the four DOT names, the three
     * control names, shield) and otherwise accepted as a {@code StateBuff} name - which is free-form by design, exactly
     * like {@code has_state}'s argument. The <b>attribute</b> is a name from {@code AttributeType}.
     */
    static void requireExtendFilter(EffectSpec effect, String op, TriggerSpec spec) {
        boolean byName = effect.getBuff() != null && !effect.getBuff().isBlank();
        boolean byAttribute = effect.getAttribute() != null && !effect.getAttribute().isBlank();
        if (byName && byAttribute) {
            throw new IllegalArgumentException(
                    "Op " + op + " states both \"buff\" and \"attribute\"; a buff is named one way or the other "
                            + "(a state's name, or the attribute a modifier sits on) -- keep one "
                            + "(source: " + spec.getSource() + ")");
        }
        if (!byName && !byAttribute) {
            // "extend all of one's own buff effects by 1 turn" (readers: 1506 Silver Wolf (银狼) LV.999's talent and its Eidolon 2).
            // Note: The refusal below still stands -- what it argues against is saying nothing at all,
            // whereas what is let through here is stating the whole set explicitly -- the author said what was to be extended,
            // so that refusal's reason ("it would extend buffs the sentence never mentions") does not hold here.
            if (effect.getKind() != null && "all".equalsIgnoreCase(effect.getKind().trim())) {
                return;
            }
            throw new IllegalArgumentException(
                    "Op " + op + " needs the buff it lengthens: \"buff\" (a state's name such as burn / frozen / "
                            + "\"" + BuffManager.SHIELD_STATE + "\", or any named state), \"attribute\" (e.g. "
                            + "ALL_DAMAGE_TYPE_BOOST for a \"damage boost effect\"), or an explicit "
                            + "\"kind\": \"all\" for \"all buff effects\". Without one it would mean \"everything "
                            + "I have on that unit\", which would lengthen buffs the sentence never mentions "
                            + "(source: " + spec.getSource() + ")");
        }
        if (byAttribute) {
            AttributeType attribute;
            try {
                attribute = AttributeType.fromString(effect.getAttribute());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Op " + op + " names unknown attribute '" + effect.getAttribute()
                                + "'; use a name from AttributeType, e.g. ALL_DAMAGE_TYPE_BOOST / ATTACK "
                                + "(source: " + spec.getSource() + ")");
            }
            if (attribute.isPercentVariant()) {
                throw new IllegalArgumentException(
                        "Op " + op + " names '" + effect.getAttribute() + "', one of the four *_PERCENT "
                                + "AttributeBuilder input keys; a modifier is never stored on one of those, so "
                                + "nothing could ever match " + "(source: " + spec.getSource() + ")");
            }
        }
        if (effect.getAmount() != null || effect.getScale() != null || effect.getPercent() != null
                || effect.getSkill() != null || effect.getDamageParam() != null
                || effect.getElement() != null || effect.getControl() != null || effect.getBaseChance() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " adds turns to a buff that already exists and states nothing else: the buff it "
                            + "lengthens keeps its own numbers (no amount / scale / percent / element / control / "
                            + "base_chance / skill) (source: " + spec.getSource() + ")");
        }
    }

    static void requireRuleReference(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getRule() == null || effect.getRule().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " needs \"rule\": the id of the rule whose number it raises (that rule states "
                            + "\"id\": \"…\"), because otherwise there is nothing to point at "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    /**
     * Refuses every field a {@code RAISE_SKILL_LEVEL} effect does not read.
     *
     * <p>Note: It cannot reuse {@link #rejectAmendmentExtras}: that one forbids {@code skill} outright (a
     * {@code MODIFY_RULE} names a <b>rule</b>, never a slot), and this op's whole meaning is <b>which</b> slot is
     * raised. Written out rather than parameterised because the two lists really are different statements - the
     * shared-looking version made the op refuse the field it needs (measured: five cases went red with
     * "raises exactly one number on the rule it names").
     */
    static void rejectSkillLevelExtras(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getPercent() != null || effect.getScale() != null || effect.getTurns() != null
                || effect.getPermanent() != null || effect.getUntil() != null
                || effect.getAttribute() != null || effect.getBuff() != null
                || effect.getTarget() != null || effect.getResource() != null
                || effect.getDamageParam() != null || effect.getDamageLevel() != null
                || effect.getElement() != null || effect.getControl() != null
                || effect.getBaseChance() != null || effect.getKind() != null
                || effect.getStacks() != null || effect.getTicksOn() != null
                || effect.getAsAttack() != null || effect.getPerTarget() != null
                || effect.getCritRate() != null || effect.getCritDamage() != null
                || effect.getRule() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " raises skill slots' levels and states nothing else: \"skill\" (one slot) or "
                            + "\"skills\" (several) and \"amount\" (how many levels), and it reads no other field "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    /**
     * {@code while_stacks} and {@code while_stacks_min} are read as a pair: a threshold with no counter (or a counter with no
     * threshold) is half a sentence, and half a sentence silently behaves like "always on".
     */
    static void validateWhileStacks(EffectSpec effect, TriggerSpec spec) {
        boolean hasName = effect.getWhileStacks() != null && !effect.getWhileStacks().isBlank();
        boolean hasMin = effect.getWhileStacksMin() != null;
        if (hasName && !hasMin) {
            throw new IllegalArgumentException(
                    "Effect states \"while_stacks\" (" + effect.getWhileStacks() + ") but no \"while_stacks_min\": the pair is "
                            + "read together, and without the threshold the modifier would never turn off "
                            + "(source: " + spec.getSource() + ")");
        }
        if (!hasName && hasMin) {
            throw new IllegalArgumentException(
                    "Effect states \"while_stacks_min\" (" + effect.getWhileStacksMin() + ") but no \"while_stacks\": there is "
                            + "no counter to compare it against (source: " + spec.getSource() + ")");
        }
    }
    /**
     * {@code prefer} is read by the {@code random_enemy} selector alone.
     *
     * <p>「优先选择…」 narrows a random draw; on any other selector there is no draw to narrow, so the field would be accepted
     * and never consulted - a condition that reads as one and behaves like a comment, which is exactly the family this
     * validator exists for. The same reasoning as `cap_scale` (only {@code APPLY_DOT} reads it).
     */
    static void validatePrefer(EffectSpec effect, TriggerSpec spec) {
        if (effect.getPrefer() == null || effect.getPrefer().isEmpty()) {
            return;
        }
        String target = effect.getTarget() == null ? "" : effect.getTarget().trim();
        if (!"random_enemy".equals(target)) {
            throw new IllegalArgumentException(
                    "Effect states \"prefer\" but its target is \"" + target + "\"; a preference only narrows the random draw "
                            + "of \"random_enemy\" (source: " + spec.getSource() + ")");
        }
        for (String raw : effect.getPrefer()) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException(
                        "Effect states \"prefer\" with a blank condition: every entry must say what makes a candidate "
                                + "preferred (source: " + spec.getSource() + ")");
            }
        }
    }

    /**
     * {@code while_summon_count} must state a least count of at least one: 「在场时」 means "one is there", and {@code 0} would
     * be a gate that can never fail - a field that reads as a condition and behaves like "always on".
     *
     * <p>Its only reader today is {@code MODIFY_ATTR} (the same asymmetry {@code while_hp_percent} has), so a rule that writes
     * it on another op would be accepted and never asked.
     */
    static void validateWhileSummonCount(EffectSpec effect, TriggerSpec spec) {
        Integer least = effect.getWhileSummonCount();
        if (least == null) {
            return;
        }
        if (least < 1) {
            throw new IllegalArgumentException(
                    "Effect states \"while_summon_count\": " + least + "; it is the LEAST number of living summons that keeps "
                            + "the modifier alive, so 0 would be a gate that never closes (source: " + spec.getSource() + ")");
        }
        if (!"MODIFY_ATTR".equalsIgnoreCase(String.valueOf(effect.getOp()).trim())) {
            throw new IllegalArgumentException(
                    "Op " + effect.getOp() + " states \"while_summon_count\", which only MODIFY_ATTR attaches today "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    static void rejectAmendmentExtras(EffectSpec effect, String op, TriggerSpec spec, String kept) {
        boolean bothOrNeither = "amount".equals(kept) ? effect.getPercent() != null : effect.getAmount() != null;
        if (bothOrNeither || effect.getScale() != null || effect.getTurns() != null
                || effect.getPermanent() != null || effect.getUntil() != null
                || effect.getAttribute() != null || effect.getBuff() != null || effect.getSkill() != null
                || effect.getTarget() != null || effect.getResource() != null
                || effect.getDamageParam() != null || effect.getDamageLevel() != null
                || effect.getElement() != null || effect.getControl() != null
                || effect.getBaseChance() != null || effect.getKind() != null
                || effect.getStacks() != null || effect.getTicksOn() != null
                || effect.getAsAttack() != null || effect.getPerTarget() != null
                || effect.getCritRate() != null || effect.getCritDamage() != null
                || (!"cooldown_delta".equals(kept) && effect.getCooldownDelta() != null)) {
            throw new IllegalArgumentException(
                    "Op " + op + " raises exactly one number on the rule it names: either \"amount\" (how many more "
                            + "times per turn) or \"percent\" (how much more likely), and it reads no other field "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates {@code RESIST_DEBUFF}'s {@code "kind"}: one of the classes the documents name
     * ({@code DebuffClass}), refused at load time with the list in the message.
     *
     * <p>Same reasoning as every other closed vocabulary here: a misspelled class would attach a resistance to a family
     * that no state belongs to - a rule that loads, fires, and does nothing, with nothing to report.
     */
    static void requireDebuffClass(EffectSpec effect, String op, TriggerSpec spec) {
        // `RESIST_DEBUFF` is the ONE op that may state no class: 1409's 小伊卡 is 「免疫负面效果」 - every family,
        // including the states that belong to none of the two classes (a stat-down, a vulnerability). Omission is
        // meaningful there ("any"), so it must not be confused with the misspelling this check exists to catch.
        if ("RESIST_DEBUFF".equals(op) && (effect.getKind() == null || effect.getKind().isBlank())) {
            return;
        }
        if (effect.getKind() == null || effect.getKind().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"kind\" (which class of negative state it resists; known: "
                            + String.join(" / ", DebuffClass.names()) + ") (source: " + spec.getSource() + ")");
        }
        if (DebuffClass.fromString(effect.getKind()) == null) {
            throw new IllegalArgumentException(
                    "Op " + op + " names the class '" + effect.getKind() + "', which the documents do not: known "
                            + "classes are " + String.join(" / ", DebuffClass.names())
                            + " (\"control-class\" and \"DoT-class\"); a resistance to a family nobody belongs to would do "
                            + "nothing (source: " + spec.getSource() + ")");
        }
    }

    /**
     * The argument rules the three resource-moving ops share, because they read the same fields in different
     * directions: {@code spendAll} only means something for a spend, {@code to_max} only for a gain, and a
     * {@code self_resource:} share only for a removal.
     *
     * <p>Kept as one method rather than copied into the three handlers: those three arms were one grouped
     * {@code case} here, and the whole point of the move is that the rule has <b>one</b> statement. The handler decides
     * <b>which</b> check applies; this decides what the check says.
     *
     * @param effect the effect
     * @param op     the op's name
     * @param spec   the rule, for {@code source}
     */
    static void requireResourceMovement(EffectSpec effect, String op, TriggerSpec spec) {
        if (Boolean.TRUE.equals(effect.getSpendAll())) {
            // "consume all [X]": a spend with no stated SIZE -- and stating one beside it is a contradiction, so it is
            // refused rather than silently preferring one.
            if (!"SPEND_RESOURCE".equals(op)) {
                throw new IllegalArgumentException(
                        "Op " + op + " states \"spendAll\", which only means something for SPEND_RESOURCE "
                                + "(source: " + spec.getSource() + ")");
            }
            if (effect.getAmount() != null) {
                throw new IllegalArgumentException(
                        "Op " + op + " states both \"spendAll\" and \"amount\": \"all of it\" and a fixed "
                                + "number are different claims (source: " + spec.getSource() + ")");
            }
        } else {
            // 「清空累计治疗数值的 50%」: REMOVE_RESOURCE may name a SHARE of one of the owner's own counters instead
            // of a flat amount (`scale: self_resource:<id>` + `percent`). Gated on the prefix so every other spelling
            // keeps requiring an amount.
            String removeScale = effect.getScale() == null ? "" : effect.getScale().trim();
            if ("REMOVE_RESOURCE".equals(op) && removeScale.startsWith(TriggerInterpreter.SELF_RESOURCE_PREFIX)) {
                requirePercent(effect, op, spec);
            } else if (TriggerInterpreter.isCastParamScale(effect)) {
                requireNoStackArguments(effect, op, spec);
            } else {
                if (!Boolean.TRUE.equals(effect.getOverflowOnly()) && !Boolean.TRUE.equals(effect.getSpendAll())) {
                    requireAmount(effect, op, spec);
                }
            }
        }
        requireResource(effect, op, spec);
        // "最多获得 50 点": an allowance that is not positive would forbid every gain, which is a rule that does
        // nothing rather than a strict one - so it is refused beside the other "silently no-op" shapes.
        if (effect.getMaxPerRound() != null && effect.getMaxPerRound() <= 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"max_per_round\" = " + effect.getMaxPerRound()
                            + ", which allows no gain at all; give a positive allowance or omit the field"
                            + " (source: " + spec.getSource() + ")");
        }
        // `to_max` says "however much it is short", which is a claim about a GAIN - the other two ops move the
        // counter the other way, where it would be a contradiction rather than a shortcut.
        if (Boolean.TRUE.equals(effect.getToMax()) && !"GAIN_RESOURCE".equals(op)) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"to_max\", which only means something for GAIN_RESOURCE "
                            + "(source: " + spec.getSource() + ")");
        }
        requireNoStackArguments(effect, op, spec);
    }

    static void requireResource(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getResource() == null || effect.getResource().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"resource\" (source: " + spec.getSource() + ")");
        }
    }

    static void requireSkill(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getSkill() == null || effect.getSkill().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"skill\" (which slot the attack comes from, e.g. TALENT) "
                            + "(source: " + spec.getSource() + ")");
        }
        try {
            SkillType.valueOf(effect.getSkill().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Op " + op + " names an unknown skill slot '" + effect.getSkill()
                            + "' (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates a {@code skills} list: at least one slot, every name a {@link SkillType}, and no slot twice.
     *
     * <p>A duplicate is refused because each entry raises the same slot again: {@code ["SKILL", "SKILL"]} with
     * {@code amount: 9} would raise it by 18, which is not "the file's slots are nine levels up" but a counting
     * accident that looks exactly like the intended line.
     */
    static void requireSkillSlots(java.util.List<String> skills, String op, TriggerSpec spec) {
        if (skills.isEmpty()) {
            throw new IllegalArgumentException(
                    "Op " + op + " states an empty \"skills\": list at least one slot, or omit the field to raise "
                            + "one \"skill\" (source: " + spec.getSource() + ")");
        }
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (String raw : skills) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException(
                        "Op " + op + " has a blank entry in \"skills\"; every entry must name a skill slot "
                                + "(source: " + spec.getSource() + ")");
            }
            String slot = raw.trim().toUpperCase(Locale.ROOT);
            try {
                SkillType.valueOf(slot);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Op " + op + " names an unknown skill slot '" + raw + "' in \"skills\" "
                                + "(source: " + spec.getSource() + ")");
            }
            if (!seen.add(slot)) {
                throw new IllegalArgumentException(
                        "Op " + op + " names the skill slot '" + raw + "' twice in \"skills\"; each entry raises "
                                + "that slot, so the duplicate would raise it twice "
                                + "(source: " + spec.getSource() + ")");
            }
        }
    }

    /**
     * Validates a <b>stated crit</b> on a damage instance: {@code crit_rate: 1} plus {@code crit_damage: X}.
     *
     * <p>Note: <b>Two legal rates, and they are the two ways of taking the roll away</b>:
     * {@code 1.0} = "always crits, no roll" (1505's riders) and {@code 0.0} = "<b>never</b> crits, no roll" - the
     * second one exists because a rule that copies a settled damage needs a <b>controlled</b> comparison: two battles
     * consume {@code Random} differently, so without it one instance crits and the other does not, and the ratio reads
     * exactly like a zone discrepancy (measured: 260.23584 -> 520.45168, a clean 2.0 that cost four rounds).
     * Note: Any other number is still refused: a probabilistic rate is the {@code CRIT_CHANCE} attribute, and these two
     * are not probabilities at all - they mean "the roll does not happen".
     */
    static void requireFixedCrit(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getCritRate() == null && effect.getCritDamage() == null) {
            return;
        }
        if (effect.getCritRate() == null || effect.getCritDamage() == null) {
            throw new IllegalArgumentException(
                    "Op " + op + " states half a fixed crit: \"crit_rate\" (100% or 0%) and \"crit_damage\" (e.g. 1.5 "
                            + "for 150%; with a 0% rate the damage is what the instance settles without a crit) go "
                            + "together, because neither number is usable without the other "
                            + "(source: " + spec.getSource() + ")");
        }
        if (effect.getCritRate() != 1.0 && effect.getCritRate() != 0.0) {
            throw new IllegalArgumentException(
                    "Op " + op + " has \"crit_rate\": " + effect.getCritRate() + ", but the spellings this engine has "
                            + "are 1.0 (\"always crits, no roll\") and 0.0 (\"never crits, no roll\"): a probabilistic "
                            + "crit rate is the CRIT_CHANCE attribute, and \"fixed\" means the roll does not happen at "
                            + "all (source: " + spec.getSource() + ")");
        }
        if (effect.getCritRate() == 1.0 && effect.getCritDamage() <= 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " has \"crit_damage\": " + effect.getCritDamage() + ", which is not a crit damage "
                            + "(1.5 = 150%) (source: " + spec.getSource() + ")");
        }
    }

    static void requireDamageParam(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getDamageParam() == null) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"damage_param\" (which parameter of the skill row is the "
                            + "multiplier); there is no default because the index differs per ability "
                            + "(source: " + spec.getSource() + ")");
        }
        if (effect.getDamageParam() < 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " has a negative \"damage_param\" (source: " + spec.getSource() + ")");
        }
        // The row, when stated, must be a real 1-based level. Whether the skill's data actually HAS that many
        // rows is checked when the effect fires: a rule does not know its owner's cid while it is being read.
        if (effect.getDamageLevel() != null && effect.getDamageLevel() < 1) {
            throw new IllegalArgumentException(
                    "Op " + op + " has \"damage_level\": " + effect.getDamageLevel()
                            + "; levels are 1-based, and 0 or below would read a row that cannot exist "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    /**
     * The things a scaled {@code HEAL} / {@code SHIELD} amount may be a percentage of. Closed on purpose, like
     * every other vocabulary here: a typo has to be rejected at load time, and the two spellings are the ones
     * the content actually uses (see {@link EffectSpec#getScale()}).
     */
    static final Set<String> SCALES =
            Set.of("target_max_hp", "target_lost_hp", "owner_max_hp", "actor_max_hp", "owner_def", "owner_attack",
                    "elation_base");

    /**
     * The one scale {@code GAIN_ENERGY} accepts: a share of the <b>receiving</b> unit's maximum energy.
     *
     * <p>"restore energy equal to #1[f1]% of Max Energy to a designated single ally" (星期日's ultimate). It cannot be a flat number,
     * because the maximum is per character (姬子 120 / 星期日 130 / 翡翠 140) - writing 20 would be wrong for all of
     * them, and it would look right for whichever one the author happened to check.
     */
    static final Set<String> ENERGY_SCALES = Set.of("target_max_energy");


    /**
     * The events during which a cast's landed-application counts are readable.
     *
     * <p>Not "every event a cast can cause": {@code CAST_SETUP} fires <b>before</b> anything has landed (so it reads
     * 0 by construction) and the other three are the delivered cast events. Keeping the list here, next to the
     * validation that uses it, is what makes the window one fact instead of a comment.
     */
    static final Set<TriggerEvent> CAST_EVENTS = Set.of(
            TriggerEvent.CAST_SETUP, TriggerEvent.BASIC_ATTACK, TriggerEvent.SKILL_CAST, TriggerEvent.ULT_CAST);

    /**
     * The lifetimes {@code "until"} accepts. Closed on purpose, like every other vocabulary here: an author
     * writing {@code "next_atack"} must hear about it while the file is read, not by watching a buff that
     * quietly never expires.
     */
    static final Set<String> LIFETIMES = Set.of("next_attack", "next_skill", "cast_end", "next_ultimate", "turn_end",
            "next_turn_start", "next_elation_skill");

}


