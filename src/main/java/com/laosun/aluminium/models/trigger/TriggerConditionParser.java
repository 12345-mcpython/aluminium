package com.laosun.aluminium.models.trigger;

import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.*;
import com.laosun.aluminium.models.*;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.trigger.TriggerTable.Condition;
import com.laosun.aluminium.models.trigger.TriggerTable.PartyCondition;
import com.laosun.aluminium.models.trigger.TriggerTable.TriggerContext;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiles a rule's {@code when} text into a {@link Condition}: the condition half of the trigger
 * language, and the only place that reads its spelling.
 *
 * <p><b>Why this is its own class.</b> A condition is a small language of its own -- {@code
 * hit_count >= 3}, {@code self_attr:SPEED > 120}, {@code target has_state 灼烧},
 * {@code allies_with_path:同谐}, {@code from_skill SKILL} -- and its implementation is one
 * 461-line parse method, the eighteen helpers it calls, and twenty-four private condition classes
 * that implement {@link Condition}. Measured before moving: the only outside caller is
 * {@code TriggerTable.compile}, none of it reaches a helper it would leave behind, and none of the
 * condition classes is referenced outside the table at all (the five apparent references were
 * string literals and javadoc). The vocabulary is closed on purpose, so a typo is refused while the
 * table is built rather than silently never matching.
 *
 * <p>Note: <b>What deliberately stayed behind, and why each one had to.</b>
 * <ul>
 *   <li>the three PUBLIC nested types -- {@code CompiledRule}, {@code TriggerContext} and
 *       {@code Condition} -- because they are API rather than implementation;</li>
 *   <li>ten constants ({@code NUMERIC_VARIABLES}, {@code SELF_ATTR_PREFIX}, the three
 *       {@code *_STACKS_PREFIX}, {@code ALLIES_WITH_PATH_PREFIX}, {@code SELF_RESOURCE_PREFIX},
 *       {@code SUMMON_RESOURCE_PREFIX}, {@code STATE_SUBJECTS}), because they are the shared
 *       spelling of a numeric variable and {@link TriggerMagnitudes} reads the same ones to
 *       resolve a scale. A shared spelling belongs with the class that owns the vocabulary, so
 *       they are read here as {@code TriggerTable.X} rather than duplicated;</li>
 *   <li>{@code PartyCondition} (an interface has no {@code static} keyword, and the classes here
 *       implement it), {@code HasShield} (a parameter type of
 *       {@code TriggerTable.requireShieldSource}) and {@code Numeric} (pattern-matched in
 *       {@code TriggerTable} itself).</li>
 * </ul>
 */
final class TriggerConditionParser {

    private TriggerConditionParser() {
    }


    static Condition parseCondition(String raw, TriggerSpec spec) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Empty trigger condition (source: " + spec.getSource() + ")");
        }
        String text = raw.trim();

        // "变化的是[NAME]" (1506's forwarding clause). Note: FIRST: the chain below is long.
        // "施加的是控制类／持续伤害类负面状态" (1506's warehouse skill). Read like
        // `resource_changed:` -- a battle-level fact recorded at the chokepoint -- and placed beside it, first in the chain.
        java.util.regex.Matcher debuffClass =
                java.util.regex.Pattern.compile("debuff_class:([^\\s]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                        .matcher(text);
        if (debuffClass.find()) {
            return new AppliedDebuffClass(raw, debuffClass.group(1).trim());
        }

        java.util.regex.Matcher changed =
                java.util.regex.Pattern.compile("resource_changed:([^\\s]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                        .matcher(text);
        if (changed.find()) {
            return new ResourceChanged(raw, changed.group(1).trim());
        }

        // `!` negates the condition that follows it. The list of conditions is an AND, so without this the DSL
        // can only say "the target is on some Path" and never "a character on the 'Harmony' Path... cannot trigger it" (Sunday (星期日)'s Skill, the
        // first user) - and writing that as nine positive rules is the shape this prefix exists to avoid.
        if (text.startsWith("!")) {
            String inner = text.substring(1).trim();
            if (inner.isEmpty() || inner.startsWith("!")) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' is not a negation of anything: write \"!\" followed by one "
                                + "condition, e.g. \"!target has_path 同谐\" (source: " + spec.getSource() + ")");
            }
            Condition negated = parseCondition(inner, spec);
            if (!(negated instanceof PartyCondition party)) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' negates a condition that does not read a party "
                                + "(self / actor / target); only those can be negated, because for a number "
                                + "\"cannot read it\" and \"is zero\" are different facts. Write the opposite "
                                + "comparison instead, e.g. \"self_summon_count == 0\" "
                                + "(source: " + spec.getSource() + ")");
            }
            return new Negated(party, raw);
        }

        // `has_state`: "<who> has_state <name>". Checked before the operator branch because this shape has
        // no symbol operator at all -- without it, "self has_state 协奏" would be reported as an unknown
        // shorthand, which sends the author looking in the wrong place.
        Matcher stateEnded = STATE_ENDED_KEYWORD.matcher(text);
        if (stateEnded.find()) {
            String subject = normalize(text.substring(0, stateEnded.start()));
            String state = text.substring(stateEnded.end()).trim();
            return new StateEnded(requireCarriedParty(requireStateSubject(subject, raw, spec), raw, spec),
                    state, raw, spec);
        }

        Matcher hasState = HAS_STATE.matcher(text);
        if (hasState.find()) {
            String subject = normalize(text.substring(0, hasState.start()));
            String state = text.substring(hasState.end()).trim();
            return new HasState(requireCarriedParty(requireStateSubject(subject, raw, spec), raw, spec), state, raw,
                    spec);
        }

        // `has_weakness`: "<who> has_weakness Fire" - the target's weakness ELEMENT, which nothing else can ask.
        Matcher hasWeakness = HAS_WEAKNESS.matcher(text);
        if (hasWeakness.find()) {
            String subject = normalize(text.substring(0, hasWeakness.start()));
            String element = text.substring(hasWeakness.end()).trim();
            return new HasWeakness(requireCarriedParty(requireStateSubject(subject, raw, spec), raw, spec),
                    requireElement(element, raw, spec), raw, spec);
        }

        // `has_skill`: "<who> has_skill ELATION_SKILL" - does that party carry this skill slot at all
        // (8009/8010's "若目标拥有欢愉技"). Read before the operator branch, like the other keyword families.
        Matcher hasSkill = HAS_SKILL.matcher(text);
        if (hasSkill.find()) {
            String subject = normalize(text.substring(0, hasSkill.start()));
            String slot = text.substring(hasSkill.end()).trim();
            return new HasSkill(requireCarriedParty(requireStateSubject(subject, raw, spec), raw, spec),
                    requireSkillSlot(slot, raw, spec), raw, spec);
        }

        // `self has_same_path_ally` - a party question with no argument, checked with the other predicates.
        Matcher samePath = SAME_PATH_ALLY.matcher(text);
        if (samePath.find()) {
            String subject = normalize(text.substring(0, samePath.start()));
            String trailing = text.substring(samePath.end()).trim();
            if (!"self".equals(subject) || !trailing.isEmpty()) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' must be written \"self has_same_path_ally\": the question is about "
                                + "MY OWN side (which teammates share my Path), so it has no other subject and no "
                                + "argument (source: " + spec.getSource() + ")");
            }
            return new HasSamePathAlly(raw, spec);
        }

        // `is_ally`: "<who> is_ally" - no argument at all, so it is checked before the operator branch too.
        // `is_other_ally`: "<who> is_other_ally" -- same shape as `is_ally`, one clause stricter.
        Matcher hasCompanionAlly = HAS_COMPANION_ALLY.matcher(text);
        if (hasCompanionAlly.find()) {
            String subject = normalize(text.substring(0, hasCompanionAlly.start()));
            String trailing = text.substring(hasCompanionAlly.end()).trim();
            if (!trailing.isEmpty()) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' writes something after \"has_companion_ally\": it takes no argument "
                                + "(write \"self has_companion_ally\") (source: " + spec.getSource() + ")");
            }
            return new HasCompanionAlly(raw, spec);
        }

        Matcher isCompanionM = IS_COMPANION.matcher(text);
        if (isCompanionM.find()) {
            String subject = normalize(text.substring(0, isCompanionM.start()));
            String trailing = text.substring(isCompanionM.end()).trim();
            if (!trailing.isEmpty()) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' writes something after \"is_companion\": it takes no argument "
                                + "(write \"self is_companion\") (source: " + spec.getSource() + ")");
            }
            return new IsCompanion(requireCarriedParty(requireStateSubject(subject, raw, spec), raw, spec), raw);
        }

        Matcher isPartyFirst = IS_PARTY_FIRST.matcher(text);
        if (isPartyFirst.find()) {
            String subject = normalize(text.substring(0, isPartyFirst.start()));
            String trailing = text.substring(isPartyFirst.end()).trim();
            if (!trailing.isEmpty()) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' writes something after \"is_party_first\": it takes no argument "
                                + "(write \"self is_party_first\") (source: " + spec.getSource() + ")");
            }
            return new IsPartyFirst(requireCarriedParty(requireStateSubject(subject, raw, spec), raw, spec), raw);
        }

        Matcher isOtherAlly = IS_OTHER_ALLY.matcher(text);
        if (isOtherAlly.find()) {
            String subject = normalize(text.substring(0, isOtherAlly.start()));
            String trailing = text.substring(isOtherAlly.end()).trim();
            if (!trailing.isEmpty()) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' writes something after \"is_other_ally\": it takes no argument "
                                + "(write \"actor is_other_ally\") (source: " + spec.getSource() + ")");
            }
            return new IsOtherAlly(requireCarriedParty(requireStateSubject(subject, raw, spec), raw, spec), raw);
        }

        Matcher isEnemy = IS_ENEMY.matcher(text);
        if (isEnemy.find()) {
            String subject = normalize(text.substring(0, isEnemy.start()));
            String trailing = text.substring(isEnemy.end()).trim();
            if (!trailing.isEmpty()) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' writes something after \"is_enemy\": it takes no argument "
                                + "(write \"actor is_enemy\", or \"!actor is_enemy\" for the opposite) "
                                + "(source: " + spec.getSource() + ")");
            }
            return new IsEnemy(requireCarriedParty(requireStateSubject(subject, raw, spec), raw, spec), raw, spec);
        }

        Matcher isAlly = IS_ALLY.matcher(text);
        if (isAlly.find()) {
            String subject = normalize(text.substring(0, isAlly.start()));
            String trailing = text.substring(isAlly.end()).trim();
            if (!trailing.isEmpty()) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' writes something after \"is_ally\": it takes no argument "
                                + "(write \"target is_ally\", or \"!target is_ally\" for the opposite) "
                                + "(source: " + spec.getSource() + ")");
            }
            return new IsAlly(requireCarriedParty(requireStateSubject(subject, raw, spec), raw, spec), raw, spec);
        }

        Matcher isSummon = IS_SUMMON.matcher(text);
        if (isSummon.find()) {
            String subject = normalize(text.substring(0, isSummon.start()));
            String trailing = text.substring(isSummon.end()).trim();
            if (!trailing.isEmpty()) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' writes something after \"is_summon\": it takes no argument "
                                + "(write \"actor is_summon\", or \"!actor is_summon\" for the opposite) "
                                + "(source: " + spec.getSource() + ")");
            }
            return new IsSummon(requireStateSubject(subject, raw, spec), raw, spec);
        }

        // `has_path`: "<who> has_path 同谐" - the same shape, and for the same reason checked here first.
        Matcher hasPath = HAS_PATH.matcher(text);
        if (hasPath.find()) {
            String subject = normalize(text.substring(0, hasPath.start()));
            String name = text.substring(hasPath.end()).trim();
            return new HasPath(requireCarriedParty(requireStateSubject(subject, raw, spec), raw, spec),
                    requirePath(name, raw, spec), raw, spec);
        }

        // `has_shield`: "<who> has_shield" - argument-less like `is_ally` - or "<who> has_shield from_rule <id>",
        // which asks about the shield's ORIGIN rather than its existence ("the shield provided by the Skill", 1001 Eidolon 6).
        Matcher hasShield = HAS_SHIELD.matcher(text);
        if (hasShield.find()) {
            String subject = normalize(text.substring(0, hasShield.start()));
            String trailing = text.substring(hasShield.end()).trim();
            String ruleId = "";
            if (!trailing.isEmpty()) {
                Matcher fromRule = FROM_RULE.matcher(trailing);
                if (!fromRule.lookingAt()) {
                    throw new IllegalArgumentException(
                            "Condition '" + raw + "' writes something after \"has_shield\": it takes no argument, or "
                                    + "\"from_rule <id>\" to ask which rule created the shield "
                                    + "(source: " + spec.getSource() + ")");
                }
                ruleId = trailing.substring(fromRule.end()).trim();
                if (ruleId.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Condition '" + raw + "' says \"from_rule\" but names no rule; give the id of the rule "
                                    + "that creates the shield (that rule states \"id\": \"…\") "
                                    + "(source: " + spec.getSource() + ")");
                }
            }
            return new TriggerTable.HasShield(requireCarriedParty(requireStateSubject(subject, raw, spec), raw, spec),
                    ruleId, raw, spec);
        }

        // `is_slowed`: "<who> is_slowed" - argument-less like `has_shield`, and the question is about a STATE the
        // engine keeps only as arithmetic (a negative SPEED modifier). Reader: 1002 Dan Heng's ultimate ("目标处于
        // 减速状态时，伤害倍率提高 120%") and his trace 罡风; light cone 20011 asks the same of an enemy.
        Matcher isSlowed = IS_SLOWED.matcher(text);
        if (isSlowed.find()) {
            String subject = normalize(text.substring(0, isSlowed.start()));
            String trailing = text.substring(isSlowed.end()).trim();
            if (!trailing.isEmpty()) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' writes something after \"is_slowed\": it takes no argument "
                                + "(write \"target is_slowed\", or \"!target is_slowed\" for the opposite) "
                                + "(source: " + spec.getSource() + ")");
            }
            return new TriggerTable.IsSlowed(requireStateSubject(subject, raw, spec), raw);
        }

        // `from_skill COMMON|SKILL|ULTRA|TALENT`: the causing instance's cast category. No subject: the "who" is
        // already a separate condition (`actor == self`), and the sentence never names a second unit.
        // `damage_is_attack`: a bare keyword, no subject and no value. Kept positive on purpose -- see DAMAGE_IS_ATTACK.
        if (text.trim().equalsIgnoreCase(DAMAGE_IS_ATTACK)) {
            TriggerEvent event = TriggerEvent.fromString(spec.getOn());
            if (event == null || !DAMAGE_CARRYING_EVENTS.contains(event)) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' asks whether the instance is an attack, but " + spec.getOn()
                                + " carries no damage instance; it belongs on an event that settles one "
                                + "(source: " + spec.getSource() + ")");
            }
            return new DamageIsAttack(raw);
        }

        // The complement: the instance is additional damage. Positive in its own name, negative in what it tests -- see
        // DAMAGE_IS_ADDITIONAL for why that is the honest spelling and not a double negative.
        if (text.trim().equalsIgnoreCase(DAMAGE_IS_ADDITIONAL)) {
            TriggerEvent event = TriggerEvent.fromString(spec.getOn());
            if (event == null || !DAMAGE_CARRYING_EVENTS.contains(event)) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' asks whether the instance is additional damage, but " + spec.getOn()
                                + " carries no damage instance; it belongs on an event that settles one "
                                + "(source: " + spec.getSource() + ")");
            }
            return new DamageIsAdditional(raw);
        }

        // And whether the instance names a cast at all.
        if (text.trim().equalsIgnoreCase(DAMAGE_HAS_NO_CAST)) {
            TriggerEvent event = TriggerEvent.fromString(spec.getOn());
            if (event == null || !DAMAGE_CARRYING_EVENTS.contains(event)) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' asks whether the instance names a cast, but " + spec.getOn()
                                + " carries no damage instance (source: " + spec.getSource() + ")");
            }
            return new DamageHasNoCast(raw);
        }


        // `damage_element_is_self`: the bare-keyword sibling of `damage_is_attack`, one clause further in --
        // it asks about the instance's ELEMENT instead of its type, and compares it with the rule owner's own.
        if (text.trim().equalsIgnoreCase(DAMAGE_ELEMENT_IS_SELF)) {
            TriggerEvent event = TriggerEvent.fromString(spec.getOn());
            if (event == null || !DAMAGE_CARRYING_EVENTS.contains(event)) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' asks whether the instance shares my element, but " + spec.getOn()
                                + " carries no damage instance; it belongs on an event that settles one "
                                + "(source: " + spec.getSource() + ")");
            }
            return new DamageElementIsSelf(raw);
        }

        // `<who> is_same_element_as_self`: argument-less like `is_ally`, and stated about a UNIT rather than about an
        // instance. The subject is optional because a per-target filter already means `target`; any other subject is
        // refused rather than ignored, so a typo cannot look like a working rule.
        if (IS_OTHER_SAME_ELEMENT.matcher(text).find()) {
            return new IsSameElementAsSelf(raw, true);
        }
        if (SHARES_PATH_WITH_AN_ALLY.matcher(text).find()) {
            return new SharesPathWithAnAlly(raw);
        }
        Matcher sameElement = IS_SAME_ELEMENT.matcher(text);
        if (sameElement.find()) {
            String subject = normalize(text.substring(0, sameElement.start()));
            String trailing = text.substring(sameElement.end()).trim();
            if (!trailing.isEmpty()) {
                throw new IllegalArgumentException("Condition '" + raw + "' writes something after "
                        + "\"is_same_element_as_self\": it takes no argument (source: " + spec.getSource() + ")");
            }
            if (!subject.isEmpty() && !"target".equals(subject)) {
                throw new IllegalArgumentException("Condition '" + raw + "' states the subject '" + subject
                        + "' for \"is_same_element_as_self\"; it is asked about the unit being tested "
                        + "(write \"target is_same_element_as_self\", or nothing at all inside target_when) "
                        + "(source: " + spec.getSource() + ")");
            }
            return new IsSameElementAsSelf(raw);
        }

        // `<subject>_debuff:<ATTR>`: the party carries a negative modifier on that attribute -- "防御力被降低"
        // / "减速" name the ATTRIBUTE that was lowered, and a modifier's DEBUFF source is the fact that says so.
        Matcher debuffOn = DEBUFF_ON.matcher(text);
        if (debuffOn.find()) {
            String subject = normalize(debuffOn.group("subject"));
            String attribute = debuffOn.group("attribute") == null ? "" : debuffOn.group("attribute").trim();
            String trailing = text.substring(debuffOn.end()).trim();
            if (!trailing.isEmpty()) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' writes \"" + trailing + "\" after the attribute name; the keyword "
                                + "takes exactly one attribute, for example \"target_debuff:DEFENCE\" "
                                + "(source: " + spec.getSource() + ")");
            }
            return new HasDebuffOn(subject, attribute, raw, spec);
        }

        Matcher fromSkill = FROM_SKILL.matcher(text);
        if (fromSkill.find()) {
            String before = normalize(text.substring(0, fromSkill.start()));
            String slot = text.substring(fromSkill.end()).trim();
            if (!before.isEmpty()) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' writes something before \"from_skill\": the keyword takes no "
                                + "subject -- who caused it is its own condition, so write for example "
                                + "\"actor == self\" and \"from_skill SKILL\" (source: " + spec.getSource() + ")");
            }
            TriggerEvent event = TriggerEvent.fromString(spec.getOn());
            if (event == null || !CAST_CARRYING_EVENTS.contains(event)) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' asks which slot caused the event, but " + spec.getOn()
                                + " carries no cast category, so the condition could never hold; it works on "
                                + String.join(" / ", CAST_CARRYING_EVENTS.stream().map(Enum::name).sorted().toList())
                                + " (source: " + spec.getSource() + ")");
            }
            return new FromSkill(requireInBattleSlot(slot, raw, spec), raw, spec);
        }

        Matcher fromCategory = FROM_CATEGORY.matcher(text);
        if (fromCategory.find()) {
            String before = normalize(text.substring(0, fromCategory.start()));
            String stated = text.substring(fromCategory.end()).trim();
            if (!before.isEmpty()) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' writes something before \"from_category\": the keyword takes no "
                                + "subject -- who cast it is its own condition, so write for example "
                                + "\"actor == self\" and \"from_category ELATION_DAMAGE\" (source: " + spec.getSource() + ")");
            }
            // Two spellings, one fact: the skill data says `ElationDamage`, the enum's own name is `ELATION_DAMAGE`.
            // The project already lives with this pair for attributes (JSON name vs enum name), so both are accepted --
            // `fromString` for the data's spelling, `valueOf` for the engine's.
            SkillCategory wanted = SkillCategory.fromString(stated);
            if (wanted == null) {
                try {
                    wanted = SkillCategory.valueOf(stated.trim().toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException unknown) {
                    wanted = null;
                }
            }
            if (wanted == null || !wanted.isKnownValue()) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' names the cast category '" + stated + "', which the engine does not "
                                + "know; the categories are the ones the skill data spells (source: " + spec.getSource() + ")");
            }
            TriggerEvent event = TriggerEvent.fromString(spec.getOn());
            if (event == null || !CAST_CARRYING_EVENTS.contains(event)) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' asks the cast's category, but " + spec.getOn()
                                + " carries none, so the condition could never hold; it works on "
                                + String.join(" / ", CAST_CARRYING_EVENTS.stream().map(Enum::name).sorted().toList())
                                + " (source: " + spec.getSource() + ")");
            }
            return new FromCategory(wanted, raw);
        }

        if (!containsOperator(text)) {
            if ("self".equals(normalize(text))) {
                return new Equality("actor", "self", false);
            }
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' is not a known shorthand; use e.g. \"self\" or "
                            + "\"actor != self\" (source: " + spec.getSource() + ")");
        }

        String[] parts = splitOperator(text, spec);
        String left = normalize(parts[0]);
        String operator = parts[1];
        String right = normalize(parts[2]);

        if ("==".equals(operator) || "!=".equals(operator)) {
            boolean negated = "!=".equals(operator);
            // An identity comparison: one side names a PARTY (`self` = the rule's owner, `summon` = a summon
            // of the owner) and the other names WHICH party (`actor` / `target`). Written symmetrically, so
            // `summon == actor` reads the same as `actor == summon`.
            String term = IDENTITY_TERMS.contains(left) ? left
                    : IDENTITY_TERMS.contains(right) ? right : null;
            if (term != null) {
                String other = term.equals(left) ? right : left;
                String variable = requireIdentityVariable(other, raw, spec);
                requireCarriedParty(variable, raw, spec);
                return new Equality(variable, term, negated);
            }
            // Neither side is "self", so this is not an identity comparison - it is a numeric one, and
            // `hit_count == 2` has been in this DSL's documentation since its first version while the parser
            // reported it as "compares two variables" (found by a test that needed `== 0`). Fall
            // through to the numeric path; a comparison between two *names* still gets the clearer message.
            if (!isNumeric(left) && !isNumeric(right)) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' compares two variables; only comparisons against a party ("
                                + String.join(", ", IDENTITY_TERMS.stream().sorted().toList())
                                + "), or against a numeric literal (e.g. \"hit_count == 2\"), are "
                                + "supported (source: " + spec.getSource() + ")");
            }
        }

        // Numeric comparison against a literal.
        String variable;
        double literal;
        boolean literalOnLeft = isNumeric(left);
        if (literalOnLeft) {
            variable = right;
            literal = Double.parseDouble(left);
        } else if (isNumeric(right)) {
            variable = left;
            literal = Double.parseDouble(right);
        } else if (isNumericVariable(left) && isNumericVariable(right)) {
            // Two variables (light cone 21012). The left one keeps the existing slots and the right
            // one gets its own, so `target_hp_percent >= self_hp_percent` reads exactly as it is written.
            return new TriggerTable.Numeric(left, selfAttributeOf(left, raw, spec), selfResourceOf(left, raw, spec),
                    stacksNameOf(left, raw, spec), left.startsWith(TARGET_STACKS_PREFIX),
                    left.startsWith(ACTOR_STACKS_PREFIX), operator, 0, false, right,
                    selfAttributeOf(right, raw, spec), selfResourceOf(right, raw, spec),
                    stacksNameOf(right, raw, spec), right.startsWith(TARGET_STACKS_PREFIX),
                    right.startsWith(ACTOR_STACKS_PREFIX), pathOf(left, raw, spec), pathOf(right, raw, spec));
        } else {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' has no numeric literal on either side (source: "
                            + spec.getSource() + ")");
        }
        if (!NUMERIC_VARIABLES.contains(variable) && !variable.startsWith(SELF_ATTR_PREFIX)
                && !variable.startsWith(SELF_RESOURCE_PREFIX) && !variable.startsWith(SELF_STACKS_PREFIX)
                && !variable.startsWith(TARGET_STACKS_PREFIX) && !variable.startsWith(ACTOR_STACKS_PREFIX)
                && !variable.startsWith(SUMMON_RESOURCE_PREFIX)
                && !variable.startsWith(ALLIES_WITH_PATH_PREFIX)
                && !variable.startsWith(ENEMY_STATE_COUNT_PREFIX)) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' compares unknown variable '" + variable
                            + "'; known numeric variables: " + String.join(", ", knownNumericVariables())
                            + ", plus \"self_attr:<ATTRIBUTE>\" for one of my own attribute values, e.g. "
                            + "\"self_attr:SPEED >= 145\", \"self_resource:<NAME>\" for how much of one "
                            + "of MY declared resources I hold, e.g. \"self_resource:充能 >= 3\", "
                            + "\"self_stacks:<NAME>\" / \"target_stacks:<NAME>\" for how many times a named "
                            + "counter has been marked, e.g. \"target_stacks:承负 >= 2\", and "
                            + "\"enemy_state_count:<STATE>\" for how many living enemies are in that state, "
                            + "e.g. \"enemy_state_count:弱点击破 >= 1\" "
                            + "(source: " + spec.getSource() + ")");
        }
        // The state name is checked for SHAPE here; what "in that state" means is `BuffManager.hasState`'s
        // business and is deliberately not second-guessed - the same split `has_state` already makes.
        requireEnemyStateName(variable, raw, spec);
        boolean onActor = variable.startsWith(ACTOR_STACKS_PREFIX);
        return new TriggerTable.Numeric(variable, selfAttributeOf(variable, raw, spec),
                selfResourceOf(variable, raw, spec),
                onActor ? variable.substring(ACTOR_STACKS_PREFIX.length()).trim()
                        : stacksNameOf(variable, raw, spec),
                variable.startsWith(TARGET_STACKS_PREFIX), onActor, operator, literal, literalOnLeft, null, null,
                null, null, false, false, pathOf(variable, raw, spec), null);
    }

    /**
     * The Path an {@code allies_with_path:<PATH>} variable names, or {@code null} for every other variable.
     *
     * <p>The name is checked by the same {@code requirePath} that {@code has_path} uses, so an unknown Path is a
     * load-time error rather than a silent count of zero.
     */
    private static String pathOf(String variable, String raw, TriggerSpec spec) {
        if (!variable.startsWith(ALLIES_WITH_PATH_PREFIX)) {
            return null;
        }
        String name = variable.substring(ALLIES_WITH_PATH_PREFIX.length()).trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' writes \"" + ALLIES_WITH_PATH_PREFIX + "\" with no Path after it; "
                            + "give one, e.g. \"allies_with_path:elation >= 2\" (source: " + spec.getSource() + ")");
        }
        requirePath(name, raw, spec);
        return name;
    }

    /**
     * Refuses an {@code enemy_state_count:} variable with no state name after it.
     *
     * <p>Only the SHAPE is checked, and that is the same line {@code has_state} draws: the engine does not own
     * the vocabulary of state names (content does), so guessing whether a name exists would refuse names that a
     * character legitimately declares elsewhere in its own file. An empty name, by contrast, is not a name at
     * all - the rule would compare zero >= 1 forever, which is a guard that can never fire.
     *
     * @param variable the already-normalised variable token
     * @param raw      the original condition text, for the error message
     * @param spec     the owning rule, for the source
     */
    private static void requireEnemyStateName(String variable, String raw, TriggerSpec spec) {
        if (!variable.startsWith(ENEMY_STATE_COUNT_PREFIX)) {
            return;
        }
        if (variable.substring(ENEMY_STATE_COUNT_PREFIX.length()).trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' writes \"" + ENEMY_STATE_COUNT_PREFIX + "\" with no state after it; "
                            + "give one, e.g. \"enemy_state_count:弱点击破 >= 1\" (source: " + spec.getSource() + ")");
        }
    }

    /**
     * The attribute behind a {@code self_attr:<ATTRIBUTE>} variable, or {@code null} for a plain name.
     *
     * <p>Two load-time rejections, both of them things that would otherwise look like a working rule:
     * <ul>
     *   <li>an attribute name that is not in {@link AttributeType} - a typo would compare against nothing
     *       and the rule would simply never fire;</li>
     *   <li>one of the four {@code *_PERCENT} builder keys ({@link AttributeType#isPercentVariant()}). They
     *       are inputs to {@code AttributeBuilder}, not runtime attributes: the builder folds them into
     *       their base attribute and stores the slot as {@code null}, so reading one would NPE at fire time
     *        - during a battle, in the middle of a damage calculation. The author means {@code
     *       HEALTH_PERCENT}'s <em>base</em> here, so say which one they want instead.</li>
     * </ul>
     *
     * @param variable the already-normalised variable token
     * @param raw      the original condition text, for the error messages
     * @param spec     the owning rule, for the source
     * @return the attribute, or {@code null} when the variable is a plain numeric name
     */
    private static AttributeType selfAttributeOf(String variable, String raw, TriggerSpec spec) {
        if (!variable.startsWith(SELF_ATTR_PREFIX)) {
            return null;
        }
        String name = variable.substring(SELF_ATTR_PREFIX.length()).trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' writes \"" + SELF_ATTR_PREFIX + "\" with no attribute after it; "
                            + "give one, e.g. \"self_attr:SPEED >= 145\" (source: " + spec.getSource() + ")");
        }
        AttributeType type;
        try {
            type = AttributeType.fromString(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' reads unknown attribute '" + name + "'; use a name from "
                            + "AttributeType, e.g. SPEED / ATTACK / CRIT_CHANCE / BREAKING_EFFECT "
                            + "(source: " + spec.getSource() + ")");
        }
        if (type.isPercentVariant()) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' reads '" + name + "', which is one of the four *_PERCENT "
                            + "AttributeBuilder input keys: the builder folds it into its base attribute and "
                            + "leaves the runtime slot null, so this could only ever fail at fire time. Read "
                            + "the base attribute instead (" + type.attributeString.replace("_percent", "")
                            + ") (source: " + spec.getSource() + ")");
        }
        return type;
    }

    /**
     * The counter a {@code self_stacks:<NAME>} / {@code target_stacks:<NAME>} variable reads, or {@code null}.
     *
     * <p>Note: Two spellings rather than one plus a subject, following the house convention ({@code hp_percent} vs
     * {@code target_hp_percent}, {@code self_summon_count} vs {@code target_summon_count}): the subject is part of the
     * name, so a rule cannot read "somebody's count" without saying whose.
     */
    private static String stacksNameOf(String variable, String raw, TriggerSpec spec) {
        String prefix;
        if (variable.startsWith(SELF_STACKS_PREFIX)) {
            prefix = SELF_STACKS_PREFIX;
        } else if (variable.startsWith(TARGET_STACKS_PREFIX)) {
            prefix = TARGET_STACKS_PREFIX;
        } else {
            return null;
        }
        String name = variable.substring(prefix.length()).trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' writes \"" + prefix + "\" with no counter name after it; give the name "
                            + "an ADD_STACK effect created, e.g. \"" + prefix + "承负 >= 2\" "
                            + "(source: " + spec.getSource() + ")");
        }
        return name;
    }

    private static String selfResourceOf(String variable, String raw, TriggerSpec spec) {
        String prefix = variable.startsWith(SELF_RESOURCE_PREFIX) ? SELF_RESOURCE_PREFIX
                : variable.startsWith(SUMMON_RESOURCE_PREFIX) ? SUMMON_RESOURCE_PREFIX : null;
        if (prefix == null) {
            return null;
        }
        String name = variable.substring(prefix.length()).trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' writes \"" + prefix + "\" with no resource after "
                            + "it; give one of the resources the character declares, e.g. "
                            + "\"self_resource:充能 >= 3\" (source: " + spec.getSource() + ")");
        }
        // Note: Spelled `java.lang.Character`: this package has its own `Character`, and the unqualified name
        // would be the combatant rather than the code-point test.
        if (name.chars().anyMatch(c -> java.lang.Character.isWhitespace(c))) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' reads the resource \"" + name + "\", which contains whitespace; "
                            + "a condition is split on its operator and trimmed, so a name with a space in it "
                            + "cannot be read back (source: " + spec.getSource() + ")");
        }
        return name;
    }

    /**
     * The known numeric variable names, sorted, for the error message.
     *
     * <p>Derived from {@link #NUMERIC_VARIABLES} rather than typed out, so the message cannot drift
     * away from the set the parser actually accepts - a message that lies about the vocabulary is
     * worse than no message, because the author trusts it.
     */
    private static List<String> knownNumericVariables() {
        return NUMERIC_VARIABLES.stream().sorted().toList();
    }

    /**
     * Checks that an identity comparison names a variable the DSL knows.
     *
     * <p>The set is closed on purpose: a typo such as {@code actor == sself} must fail at load time
     * rather than evaluate to false forever.
     *
     * @param token the variable name (already lower-cased)
     * @param raw   the original condition text, for the error message
     * @param spec  the owning rule, for the source
     * @return the variable name
     */
    private static String requireIdentityVariable(String token, String raw, TriggerSpec spec) {
        if (!IDENTITY_VARIABLES.contains(token)) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' compares a known party with '" + token
                            + "', which is neither an identity variable ("
                            + String.join(", ", IDENTITY_VARIABLES.stream().sorted().toList())
                            + ": who caused the event / what it happened to) nor an identity term ("
                            + String.join(", ", IDENTITY_TERMS.stream().sorted().toList())
                            + ": the rule's owner / a summon of the rule's owner) (source: "
                            + spec.getSource() + ")");
        }
        return token;
    }

    /**
     * Checks that a {@code has_state} condition asks about a party the DSL knows.
     *
     * <p>{@code self} is allowed here (unlike in an identity comparison, where it would be the pointless
     * "self == self"): "which state am I in" is the most common question the condition exists for.
     *
     * @param subject the party token (already lower-cased)
     * @param raw     the original condition text, for the error message
     * @param spec    the owning rule, for the source
     * @return the party token
     */
    private static String requireStateSubject(String subject, String raw, TriggerSpec spec) {
        if (!STATE_SUBJECTS.contains(subject)) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' asks whether '" + subject + "' is in a named state, but the "
                            + "parties are " + String.join(", ", STATE_SUBJECTS.stream().sorted().toList())
                            + " (self = the character whose table fired, actor = who caused the event, "
                            + "target = what it happened to) (source: " + spec.getSource() + ")");
        }
        return subject;
    }

    /**
     * The slot a {@code has_skill} condition names, checked against the closed vocabulary of {@link SkillType}.
     *
     * <p>Refused while the file is read, for the reason {@code has_path}'s name is: a typo would silently become
     * "this unit does not carry that skill", i.e. a guard that permanently sends the rule down the branch it was
     * written to exclude - the failure mode that has no symptom.
     *
     * <p>Note: Unlike {@code from_skill} (which only accepts the four in-battle cast slots, because it asks about a
     * <b>cast</b> that already happened) this one accepts the whole enum: {@code ELATION_SKILL} is a real slot that
     * no cast event carries, and "拥有" is a question about the <b>kit</b>, not about this battle's events.
     */
    private static SkillType requireSkillSlot(String name, String raw, TriggerSpec spec) {
        SkillType slot;
        try {
            slot = SkillType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException notASlot) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' names the skill \"" + name + "\", which is not a slot this engine "
                            + "knows; the slots are " + String.join(" / ", skillSlots()) + " "
                            + "(source: " + spec.getSource() + ")");
        }
        return slot;
    }

    /**
     * The {@link SkillType} names, for the message a misspelled {@code has_skill} slot gets.
     */
    private static List<String> skillSlots() {
        return java.util.Arrays.stream(SkillType.values()).map(Enum::name).toList();
    }

    /**
     * Refuses a condition that reads a party the event <b>never carries</b>.
     *
     * <p>{@link TriggerEvent#BATTLE_START} is fired once, to every character's table, with <b>no</b> actor and no
     * target ({@code Battle.startBattle} to {@code fireTriggers(TriggerEvent.BATTLE_START)}). A rule written there
     * as {@code "when": ["actor == self"]} therefore <b>can never fire</b> - and it looks completely reasonable,
     * which is exactly the failure mode this DSL refuses everywhere else: a rule that loads, is filed under the
     * right event, and silently does nothing. (Written by hand first, caught by the rule failing to grant anything.) On this event "my own table" is already the unit of delivery - the table <i>is</i> the owner's -
     * so the condition was never needed.
     *
     * <p>{@code self} is always carried (the owner exists for every event) and is therefore always allowed.
     *
     * @param party the party token (already validated as a known one)
     * @param raw   the original condition text, for the message
     * @param spec  the owning rule, for the source and the event
     * @return the party token
     */
    private static String requireCarriedParty(String party, String raw, TriggerSpec spec) {
        if ("self".equals(party)) {
            return party;
        }
        if (TriggerEvent.fromString(spec.getOn()) == TriggerEvent.BATTLE_START) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' asks about '" + party + "', but " + TriggerEvent.BATTLE_START.value()
                            + " carries no actor and no target, so the rule could never fire. This event is delivered "
                            + "to every character's own table, so \"my own battle start\" needs no condition at all "
                            + "(source: " + spec.getSource() + ")");
        }
        return party;
    }

    private static boolean containsOperator(String text) {
        return text.contains("==") || text.contains("!=")
                || text.contains(">=") || text.contains("<=")
                || text.contains(">") || text.contains("<");
    }

    private static String[] splitOperator(String text, TriggerSpec spec) {
        for (String op : new String[]{">=", "<=", "==", "!=", ">", "<"}) {
            int at = text.indexOf(op);
            if (at >= 0) {
                String left = text.substring(0, at).trim();
                String right = text.substring(at + op.length()).trim();
                if (!left.isEmpty() && !right.isEmpty()) {
                    return new String[]{left, op, right};
                }
                break;
            }
        }
        throw new IllegalArgumentException(
                "Malformed condition '" + text + "' (source: " + spec.getSource() + ")");
    }

    private static String normalize(String token) {
        return token.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Whether a side may stand as a numeric variable on its own.
     */
    private static boolean isNumericVariable(String token) {
        String name = token == null ? "" : token.trim();
        return NUMERIC_VARIABLES.contains(name) || name.startsWith(SELF_ATTR_PREFIX)
                || name.startsWith(SELF_RESOURCE_PREFIX) || name.startsWith(SELF_STACKS_PREFIX)
                || name.startsWith(TARGET_STACKS_PREFIX) || name.startsWith(ACTOR_STACKS_PREFIX)
                || name.startsWith(ALLIES_WITH_PATH_PREFIX)
                || name.startsWith(ENEMY_STATE_COUNT_PREFIX);
    }

    private static boolean isNumeric(String token) {
        if (token.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(token);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * {@code !<condition>} - the condition fails exactly when the one after it passes.
     *
     * <p><b>Why a prefix and not a "not equal" spelling per family.</b> Every family would otherwise need its own
     * negation ({@code has_state} / {@code has_path} / the numeric comparisons), and the ones that already have
     * one ({@code actor != self}) would have two ways to say it. One prefix covers the ones that need it, and it
     * is read <b>before</b> anything else, so negating a {@code has_path} condition cannot be confused with the {@code !=}
     * operator.
     *
     * <p>Note: <b>Only party-reading conditions may be negated</b> (see {@link PartyCondition}), and the check is done
     * while the file is read. Two reasons, both about silence:
     * <ul>
     *   <li>"the party does not exist" must stay a <b>failure</b> in both polarities - for the numeric variables
     *       that fact is {@code NaN}, and {@code !(NaN > 0)} is <b>true</b>, i.e. negating a number would turn
     *       "cannot read it" into "matches";</li>
     *   <li>numbers already have the opposite spelling ({@code self_summon_count == 0}), so refusing the
     *       ambiguous one points the author at the better form instead of guessing.</li>
     * </ul>
     * The inner condition is compiled by the same {@code parseCondition}, so a negated typo is still refused with
     * the message that names the typo (not "unknown condition").
     */
    static final class Negated implements Condition {

        private final Condition inner;
        private final PartyCondition party;
        private final String raw;

        Negated(PartyCondition inner, String raw) {
            this.inner = inner;
            this.party = inner;
            this.raw = raw;
        }

        @Override
        public boolean test(TriggerContext ctx) {
            return party.partyOf(ctx) != null && !inner.test(ctx);
        }

        @Override
        public String source() {
            return raw;
        }
    }

    /**
     * Weakness test: {@code target has_weakness Fire} - "命中具有火属性弱点的敌人时" (relic set 316).
     *
     * <p><b>Why it needs a word of its own.</b> A weakness is a property of the <b>enemy</b>
     * ({@link com.laosun.aluminium.models.enemy.Enemy#isWeakTo}), not a buff and not an attribute: no existing
     * condition can ask about it, and the sentences that need it are otherwise pure data
     * ("命中具有 X 属性弱点的敌人时，装备者的击破特攻提高 20%，持续 2 回合").
     *
     * <p>Note: Only an {@link com.laosun.aluminium.models.enemy.Enemy} answers: a character (or a summon) has no weakness
     * bar, so the condition is <b>false</b> for one - the "cannot read it, therefore it fails" rule the whole family
     * follows, rather than "no weakness means no weakness to Fire".
     */
    static final class HasWeakness implements Condition, PartyCondition {

        private final String subject;
        private final DamageElement element;
        private final String raw;

        HasWeakness(String subject, DamageElement element, String raw, TriggerSpec spec) {
            this.subject = subject;
            this.element = element;
            this.raw = raw;
        }

        @Override
        public CanHit partyOf(TriggerContext ctx) {
            return switch (subject) {
                case "self" -> ctx.owner();
                case "actor" -> ctx.actor();
                case "target" -> ctx.target();
                default -> null;
            };
        }

        @Override
        public boolean test(TriggerContext ctx) {
            return partyOf(ctx) instanceof com.laosun.aluminium.models.enemy.Enemy enemy
                    && enemy.isWeakTo(element);
        }

        @Override
        public String source() {
            return raw;
        }
    }

    /**
     * The element a {@code has_weakness} condition names, checked against the closed enum.
     *
     * <p>Both spellings the data uses are accepted ({@code Fire} and {@code FIRE}); anything else is refused while the
     * file is read, because a misspelled element would make the condition silently false on every enemy - "when hitting an
     * enemy with a Fire weakness" would simply never fire, with nothing to see.
     */
    private static DamageElement requireElement(String name, String raw, TriggerSpec spec) {
        DamageElement element = DamageElement.fromString(name);
        if (element == null && name != null) {
            element = DamageElement.fromString(name.trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (element == null) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' names an unknown element '" + name + "'; write one of "
                            + java.util.Arrays.toString(DamageElement.values())
                            + " (source: " + spec.getSource() + ")");
        }
        return element;
    }

    /**
     * Party-composition test: {@code self has_same_path_ally} - "若至少存在一名与装备者命途相同的队友".
     *
     * <p><b>Why it reads the field.</b> A relic can be worn by anybody, so "与装备者命途相同" cannot be written as
     * a Path name: the rule has to compare whoever is wearing it against the rest of the side. That is a fact about
     * the battlefield, like {@code self_summon_count} - and it is the first condition whose answer depends on the
     * <b>party</b> rather than on the owner, the event or the subject.
     *
     * <p>Note: Three things it deliberately does not count: a <b>dead</b> ally (a corpse is not somebody on the field -
     * the same reading {@code Battle.summonsOf} uses), the wearer itself, and a Path the engine cannot name
     * ({@link com.laosun.aluminium.enums.Path#OTHER}): two units whose data carries an unknown Path are not "the same
     * Path", they are both unknown, and matching them would be a coincidence dressed as a rule.
     */
    static final class HasSamePathAlly implements Condition, PartyCondition {

        private final String raw;

        HasSamePathAlly(String raw, TriggerSpec spec) {
            this.raw = raw;
        }

        @Override
        public CanHit partyOf(TriggerContext ctx) {
            return ctx.owner();
        }

        @Override
        public boolean test(TriggerContext ctx) {
            if (ctx.battle() == null || !(ctx.owner() instanceof com.laosun.aluminium.models.Character me)) {
                return false;
            }
            com.laosun.aluminium.enums.Path mine = me.getPath();
            if (mine == com.laosun.aluminium.enums.Path.OTHER) {
                return false;
            }
            for (CanHit ally : ctx.battle().allies) {
                if (ally != me && !ally.isDeath() && ally instanceof com.laosun.aluminium.models.Character other && other.getPath() == mine) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String source() {
            return raw;
        }
    }

    /**
     * {@code <who> is_companion} ? the unit is one of the Trailblaze Companions (relic 32).
     */

    static final class IsCompanion implements Condition, PartyCondition {
        private final String subject;
        private final String raw;

        IsCompanion(String subject, String raw) {
            this.subject = subject;
            this.raw = raw;
        }

        @Override
        public CanHit partyOf(TriggerContext ctx) {
            return switch (subject) {
                case "self" -> ctx.owner();
                case "actor" -> ctx.actor();
                case "target" -> ctx.target();
                default -> null;
            };
        }

        @Override
        public boolean test(TriggerContext ctx) {
            CanHit who = partyOf(ctx);
            return isCompanion(who);
        }

        @Override
        public String source() {
            return raw;
        }
    }

    /**
     * {@code self has_companion_ally} ? somebody else on our side is a Trailblaze Companion (relic 32).
     */
    static final class HasCompanionAlly implements Condition, PartyCondition {
        private final String raw;

        HasCompanionAlly(String raw, TriggerSpec spec) {
            this.raw = raw;
        }

        @Override
        public CanHit partyOf(TriggerContext ctx) {
            return ctx.owner();
        }

        @Override
        public boolean test(TriggerContext ctx) {
            if (ctx.battle() == null || ctx.owner() == null) {
                return false;
            }
            for (CanHit ally : ctx.battle().allies) {
                if (ally != ctx.owner() && !ally.isDeath() && isCompanion(ally)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String source() {
            return raw;
        }
    }

    /**
     * {@code <who> is_party_first} ? the unit is the FIRST CHARACTER of the party. Its reader is relic 31:
     * "若装备者不是队伍第一名，则使队伍第一名的攻击力提高" -- one rule needs both halves, so this answers the condition
     * half and {@code party_first} (a target selector) answers the other.
     */

    static final class IsPartyFirst implements Condition, PartyCondition {
        private final String subject;
        private final String raw;

        IsPartyFirst(String subject, String raw) {
            this.subject = subject;
            this.raw = raw;
        }

        @Override
        public CanHit partyOf(TriggerContext ctx) {
            return switch (subject) {
                case "self" -> ctx.owner();
                case "actor" -> ctx.actor();
                case "target" -> ctx.target();
                default -> null;
            };
        }

        @Override
        public boolean test(TriggerContext ctx) {
            CanHit who = partyOf(ctx);
            if (who == null || ctx.battle() == null || ctx.battle().characters.isEmpty()) {
                return false;
            }
            return ctx.battle().characters.getFirst() == who;
        }

        @Override
        public String source() {
            return raw;
        }
    }

    /**
     * {@code <who> is_other_ally} ? the unit is on our side <b>and is not the rule's owner</b>.
     *
     * <p>The "other" half is the whole point: {@code is_ally} already answers "on our side", and the owner satisfies it. A rule
     * that means "my ALLY did something" must exclude its own actions, or the follow-up it grants would trigger itself.
     */

    static final class IsOtherAlly implements Condition, PartyCondition {
        private final String subject;
        private final String raw;

        IsOtherAlly(String subject, String raw) {
            this.subject = subject;
            this.raw = raw;
        }

        @Override
        public CanHit partyOf(TriggerContext ctx) {
            return switch (subject) {
                case "self" -> ctx.owner();
                case "actor" -> ctx.actor();
                case "target" -> ctx.target();
                default -> null;
            };
        }

        @Override
        public boolean test(TriggerContext ctx) {
            CanHit who = partyOf(ctx);
            if (who == null || who == ctx.owner() || ctx.battle() == null) {
                return false;
            }
            return ctx.battle().allies.contains(who);
        }

        @Override
        public String source() {
            return raw;
        }
    }

    /**
     * {@code <who> is_summon}: the subject is a memosprite, whatever its slot or its side in the party.
     */
    static final class IsSummon implements Condition {

        private final String subject;
        private final String raw;

        IsSummon(String subject, String raw, TriggerSpec spec) {
            this.subject = subject;
            this.raw = raw;
        }

        @Override
        public boolean test(TriggerContext ctx) {
            CanHit who = switch (subject) {
                case "self" -> ctx.owner();
                case "actor" -> ctx.actor();
                case "target" -> ctx.target();
                default -> null;
            };
            return who instanceof com.laosun.aluminium.models.Summon;
        }

        @Override
        public String source() {
            return raw;
        }
    }

    /**
     * Side test: {@code target is_ally} - "the unit this cast was aimed at is on our side".
     *
     * <p><b>Why the DSL needs it.</b> Three shipped relic abilities say "对<b>己方角色</b>施放终结技/战技时" (sets 114,
     * 118, 121). A cast event carries the unit it <b>aimed at</b> ({@code ctx.target()}), which is what
     * "对…施放" names - but the event says nothing about that unit's <b>side</b>, and a damaging ultimate aimed at an
     * enemy carries a target too. Without this predicate the rule would fire on every cast of that slot: an
     * over-trigger with nothing to report.
     *
     * <p>The side is taken from the battlefield ({@code battle.allies}, the same roster {@code all_allies} reads), so
     * a context with no battle answers "no" - the "cannot read it, therefore the condition fails" rule every
     * field-reading condition follows. Note: It is a {@link PartyCondition}, so {@code !target is_ally} means the
     * opposite <b>and</b> still fails when there is no target at all (a missing party must never become a match).
     */

    static final class IsAlly implements Condition, PartyCondition {

        private final String subject;
        private final String raw;

        IsAlly(String subject, String raw, TriggerSpec spec) {
            this.subject = subject;
            this.raw = raw;
        }

        @Override
        public CanHit partyOf(TriggerContext ctx) {
            return switch (subject) {
                case "self" -> ctx.owner();
                case "actor" -> ctx.actor();
                case "target" -> ctx.target();
                default -> null;
            };
        }

        @Override
        public boolean test(TriggerContext ctx) {
            CanHit who = partyOf(ctx);
            return who != null && ctx.battle() != null && ctx.battle().allies.contains(who);
        }

        @Override
        public String source() {
            return raw;
        }
    }

    /**
     * Side test: {@code actor is_enemy} - "the unit that caused this event is on the other side".
     */
    static final class IsEnemy implements Condition, PartyCondition {

        private final String subject;
        private final String raw;

        IsEnemy(String subject, String raw, TriggerSpec spec) {
            this.subject = subject;
            this.raw = raw;
        }

        @Override
        public CanHit partyOf(TriggerContext ctx) {
            return switch (subject) {
                case "self" -> ctx.owner();
                case "actor" -> ctx.actor();
                case "target" -> ctx.target();
                default -> null;
            };
        }

        @Override
        public boolean test(TriggerContext ctx) {
            CanHit who = partyOf(ctx);
            return who != null && ctx.battle() != null && ctx.battle().enemies.contains(who);
        }

        @Override
        public String source() {
            return raw;
        }
    }

    /**
     * {@code from_skill COMMON|SKILL|ULTRA|TALENT} - the instance that caused this event came from that slot.
     *
     * <p><b>Why the DSL needs it.</b> 1003 Himeko (姬子)'s Eidolon (星魂) 4 says "when casting the Skill and breaking an enemy target's weakness, Himeko additionally gains 1 point of charge" and
     * her ultimate pays per kill "每消灭1个敌方目标". {@code BREAK} and {@code KILL} already carry <b>who</b> caused
     * them ({@code actor}), but not <b>which ability</b>: without this term `actor == self` on a break would also pay for
     * a break left by her basic attack or by her talent's follow-up, which the text excludes - a wrong number with
     * nothing to report.
     *
     * <p>Note: <b>No subject.</b> "Who did it" is its own condition ({@code actor == self}), so writing one here would be a
     * second way to say the same thing.
     *
     * <p>Note: A break/kill with no causing instance (a rule that reduces toughness directly, an enemy skill that builds
     * its damage inline) answers <b>false</b>: the event happened, but nothing can say which skill produced it.
     */
    /**
     * {@code damage_is_attack} ? "the instance being settled counts as an attack".
     *
     * <p>Additional damage is an instance too, and it is deliberately marked {@code notCountsAsAttack()}, so this is the one
     * question that tells the two apart where it matters: a rule that would otherwise react to its own extra instance.
     */
    /**
     * "拥有任意相同命途的角色": whether the candidate has a Path twin on our side. A unit whose Path is
     * the placeholder ({@code Path.OTHER}) has no twin by definition -- the same refusal {@code has_same_path_ally} makes.
     */
    static final class AppliedDebuffClass implements Condition {
        private final String raw;
        private final com.laosun.aluminium.enums.DebuffClass expected;

        AppliedDebuffClass(String raw, String wanted) {
            this.raw = raw;
            this.expected = com.laosun.aluminium.enums.DebuffClass.fromString(wanted);
        }

        @Override
        public boolean test(TriggerContext ctx) {
            return ctx.battle() != null && expected == ctx.battle().lastAppliedDebuffClass();
        }

        @Override
        public String source() {
            return raw;
        }

        @Override
        public String toString() {
            return raw;
        }
    }

    /** "变化的是[NAME]": the guard that stops a forwarding rule from eating its own output. */
    /**
     * "施加的是[X]类负面状态": the family of the debuff that just landed (control / dot).
     */
    static final class ResourceChanged implements Condition {
        private final String raw;
        private final String resource;

        ResourceChanged(String raw, String resource) {
            this.raw = raw;
            this.resource = resource;
        }

        @Override
        public boolean test(TriggerContext ctx) {
            return ctx.battle() != null && resource.equals(ctx.battle().lastChangedResource());
        }

        @Override
        public String source() {
            return raw;
        }

        @Override
        public String toString() {
            return raw;
        }
    }

    static final class SharesPathWithAnAlly implements Condition, PartyCondition {
        private final String raw;

        SharesPathWithAnAlly(String raw) {
            this.raw = raw;
        }

        @Override
        public CanHit partyOf(TriggerContext ctx) {
            return ctx.target();
        }

        @Override
        public boolean test(TriggerContext ctx) {
            if (ctx.battle() == null || !(ctx.target() instanceof com.laosun.aluminium.models.Character candidate)) {
                return false;
            }
            com.laosun.aluminium.enums.Path mine = candidate.getPath();
            if (mine == null || mine == com.laosun.aluminium.enums.Path.OTHER) {
                return false;
            }
            for (CanHit ally : ctx.battle().allies) {
                if (ally != candidate && !ally.isDeath()
                        && ally instanceof com.laosun.aluminium.models.Character other && other.getPath() == mine) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String source() {
            return raw;
        }

        @Override
        public String toString() {
            return raw;
        }
    }

    /**
     * "与装备者相同属性的其他我方角色": the candidate's element against the rule owner's own. A unit with no
     * element (or the placeholder) is not "the same element" -- the same refusal {@code has_same_path_ally} makes about
     * unknown Paths, because matching two unknowns would be a coincidence dressed as a rule.
     */
    static final class IsSameElementAsSelf implements Condition, PartyCondition {
        private final String raw;
        private final boolean otherOnly;

        IsSameElementAsSelf(String raw) {
            this(raw, false);
        }

        IsSameElementAsSelf(String raw, boolean otherOnly) {
            this.raw = raw;
            this.otherOnly = otherOnly;
        }

        @Override
        public CanHit partyOf(TriggerContext ctx) {
            return ctx.target();
        }

        @Override
        public boolean test(TriggerContext ctx) {
            if (otherOnly && ctx.target() == ctx.owner()) {
                return false;          // "其他我方角色" does not include the wearer
            }
            com.laosun.aluminium.enums.DamageElement mine = elementOf(ctx.owner());
            com.laosun.aluminium.enums.DamageElement theirs = elementOf(ctx.target());
            return mine != null && mine == theirs;
        }

        private static com.laosun.aluminium.enums.DamageElement elementOf(CanHit unit) {
            if (unit instanceof com.laosun.aluminium.models.Character character) {
                return character.getElement();
            }
            if (unit instanceof com.laosun.aluminium.models.Summon summon
                    && summon.getMaster() instanceof com.laosun.aluminium.models.Character master) {
                return master.getElement();
            }
            return null;
        }

        @Override
        public String source() {
            return raw;
        }

        @Override
        public String toString() {
            return raw;
        }
    }

    /** The element half of {@link #DAMAGE_IS_ATTACK}: the instance's element against the rule owner's own. */
    /**
     * The complement of {@link #DAMAGE_IS_ATTACK}: the instance is additional damage, not an ordinary attack.
     */
    static final class DamageIsAdditional implements Condition {
        private final String raw;

        DamageIsAdditional(String raw) {
            this.raw = raw;
        }

        @Override
        public boolean test(TriggerContext ctx) {
            // Note: The negation is correct HERE and is wrong next door: `Damage.countsAsAttack` defaults to true, so `!...` is exactly
            // "additional damage", which is what this keyword names. In `damage_is_attack` the same expression meant the opposite of
            // that keyword's contract (cone 23008's clause read +0.0).
            return ctx.damage() != null && !ctx.damage().isCountsAsAttack();
        }

        @Override
        public String source() {
            return raw;
        }
    }

    static final class DamageElementIsSelf implements Condition {
        private final String raw;

        DamageElementIsSelf(String raw) {
            this.raw = raw;
        }

        @Override
        public boolean test(TriggerContext ctx) {
            if (ctx.damage() == null || ctx.damage().getElement() == null) {
                return false;
            }
            CanHit owner = ctx.owner();
            if (owner instanceof com.laosun.aluminium.models.Summon summon && summon.getMaster() != null) {
                owner = summon.getMaster();
            }
            if (!(owner instanceof com.laosun.aluminium.models.Character character)) {
                return false;
            }
            return character.getElement() != null && character.getElement() == ctx.damage().getElement();
        }

        @Override
        public String source() {
            return raw;
        }

        @Override
        public String toString() {
            return raw;
        }
    }

    /**
     * {@code damage_has_no_cast}: the instance being settled was produced by the ENGINE, not by a rule that named a cast.
     */
    static final class DamageHasNoCast implements Condition {
        private final String raw;

        DamageHasNoCast(String raw) {
            this.raw = raw;
        }

        @Override
        public boolean test(TriggerContext ctx) {
            // Note: Measured: the engine's own instances carry the ENUM `UNSPECIFIED`, not a null (the probe printed "castCategory=UNSPECIFIED"), so testing for null alone made this
            // condition permanently false.
            if (ctx.damage() == null) {
                return false;
            }
            var category = ctx.damage().getCastCategory();
            return category == null
                    || category == com.laosun.aluminium.enums.SkillCategory.UNSPECIFIED;
        }

        @Override
        public String source() {
            return raw;
        }
    }

    static final class DamageIsAttack implements Condition {
        private final String raw;

        DamageIsAttack(String raw) {
            this.raw = raw;
        }

        @Override
        public boolean test(TriggerContext ctx) {
            // Was `!ctx.damage().isCountsAsAttack()`, i.e. the exact opposite of the keyword's contract (measured:
            // a rule guarded by `damage_is_attack` fired on ADDITIONAL damage and never on a real attack --
            // cone 23008's energy clause read +0.0 because of it). `Damage.countsAsAttack` defaults to true, so the
            // negation made every ordinary attack fail the guard it was written to pass.
            return ctx.damage() != null && ctx.damage().isCountsAsAttack();
        }

        @Override
        public String source() {
            return raw;
        }
    }

    /**
     * {@code from_category}: the causing cast's category, read straight from the event's context.
     */
    static final class FromCategory implements Condition {

        private final SkillCategory wanted;
        private final String raw;

        FromCategory(SkillCategory wanted, String raw) {
            this.wanted = wanted;
            this.raw = raw;
        }

        @Override
        public boolean test(TriggerContext ctx) {
            return ctx.fromCast() != null && ctx.fromCast() == wanted;
        }

        @Override
        public String source() {
            return raw;
        }
    }

    static final class FromSkill implements Condition {

        private final SkillType slot;
        private final String raw;

        FromSkill(SkillType slot, String raw, TriggerSpec spec) {
            this.slot = slot;
            this.raw = raw;
        }

        @Override
        public boolean test(TriggerContext ctx) {
            SkillCategory wanted = SkillCategory.of(slot);
            return ctx.fromCast() != null && ctx.fromCast() == wanted;
        }

        @Override
        public String source() {
            return raw;
        }
    }

    /**
     * Reads the slot {@code from_skill} names, refusing anything that is not an <b>in-battle</b> cast.
     *
     * <p>Closed set, like every other vocabulary here: a typo would make the condition never true, and the technique / overworld
     * ({@code MAZE} / {@code TECHNIQUE}) never produce an in-battle damage instance at all - so they are refused with
     * the list instead of silently never matching.
     */
    private static SkillType requireInBattleSlot(String name, String raw, TriggerSpec spec) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' has no slot after \"from_skill\": it needs one of "
                            + inBattleSlots() + " (source: " + spec.getSource() + ")");
        }
        SkillType slot;
        try {
            slot = SkillType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' names the unknown skill slot '" + name + "'; known slots are "
                            + inBattleSlots() + " (source: " + spec.getSource() + ")");
        }
        if (SkillCategory.of(slot) == null) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' names the slot " + slot + ", which is not an in-battle cast "
                            + "(Technique / overworld basic attack produce no in-battle damage instance), so the condition could never "
                            + "hold; use one of " + inBattleSlots() + " (source: " + spec.getSource() + ")");
        }
        return slot;
    }

    private static String inBattleSlots() {
        return String.join(" / ", List.of(SkillType.COMMON.name(), SkillType.SKILL.name(),
                SkillType.ULTRA.name(), SkillType.TALENT.name()));
    }

    /**
     * Path test: {@code target has_path 同谐} / {@code self has_path 存护}.
     *
     * <p><b>Why the condition DSL needs it.</b> Sunday (星期日)'s Skill says "when 星期日 casts this skill on a character on the 'Harmony' Path,
     * <b>it cannot trigger</b> the immediate-action effect" - an exception keyed on the target's <b>Path</b>, which no other condition can
     * ask about. The Path is already engine knowledge ({@code Character.getPath()}, the aggro tier), so this is a
     * vocabulary addition, not new data.
     *
     * <p>Read off the party named on the left, and <b>only</b> a character has one: a summon (or an enemy with no
     * character data) answers "no", which is the "cannot read it, therefore the condition fails" rule
     * {@link HasState} follows. Note: A Path the build does not recognise is {@link
     * com.laosun.aluminium.enums.Path#OTHER} - that is <b>not</b> equal to any of the nine, so a rule asking for
     * Harmony (同谐) does not accidentally match it.
     */
    static final class HasPath implements Condition, PartyCondition {

        private final String subject;
        private final com.laosun.aluminium.enums.Path path;
        private final String raw;

        HasPath(String subject, com.laosun.aluminium.enums.Path path, String raw, TriggerSpec spec) {
            this.subject = subject;
            this.path = path;
            this.raw = raw;
        }

        @Override
        public CanHit partyOf(TriggerContext ctx) {
            return switch (subject) {
                case "self" -> ctx.owner();
                case "actor" -> ctx.actor();
                case "target" -> ctx.target();
                default -> null;
            };
        }

        @Override
        public boolean test(TriggerContext ctx) {
            CanHit who = partyOf(ctx);
            return who instanceof com.laosun.aluminium.models.Character character && character.getPath() == path;
        }

        @Override
        public String source() {
            return raw;
        }
    }

    /**
     * Kit test: {@code target has_skill ELATION_SKILL} / {@code self has_skill COMMON} - "若目标拥有欢愉技"
     * (8009/8010's ultimate).
     *
     * <p>Read off the party named on the left, exactly like {@link HasPath}, so the same three subjects
     * ({@code self} / {@code actor} / {@code target}) work and the {@code !} prefix comes for free
     * ({@link PartyCondition} is what the negation path asks for) - and the document needs both polarities in one
     * sentence.
     *
     * <p>Note: "Has it" means the slot holds a skill <b>whose data row is real</b> - not merely that the slot has a key.
     * {@code Character.Builder} fills every intrinsic slot, so a character with no such row still carries the slot,
     * holding {@code SkillData}'s not-found placeholder; that is why the test is
     * {@link com.laosun.aluminium.data.SkillData#isLoaded()} rather
     * than a null check (measured: Himeko (姬子) has an {@code ELATION_SKILL} key, and only 8010's is a real row. A
     * key-based first version answered "yes" for everyone - i.e. a guard that guards nothing, and the
     * {@code CAST_SKILL} it guards would then have thrown on every ally the document excludes).
     *
     * <p>Anything else - an enemy (whose {@code skills} map is empty), a unit whose slot was never filled - answers
     * {@code false}, which keeps this family's rule: "cannot read it, therefore the condition fails", and a missing
     * party is never the accidental reason a rule matched.
     */
    static final class HasSkill implements Condition, PartyCondition {

        private final String subject;
        private final SkillType slot;
        private final String raw;

        HasSkill(String subject, SkillType slot, String raw, TriggerSpec spec) {
            this.subject = subject;
            this.slot = slot;
            this.raw = raw;
        }

        @Override
        public CanHit partyOf(TriggerContext ctx) {
            return switch (subject) {
                case "self" -> ctx.owner();
                case "actor" -> ctx.actor();
                case "target" -> ctx.target();
                default -> null;
            };
        }

        @Override
        public boolean test(TriggerContext ctx) {
            CanHit who = partyOf(ctx);
            if (who == null || who.getSkills() == null) {
                return false;
            }
            var skill = who.getSkills().get(slot);
            return skill != null && skill.getData() != null && skill.getData().isLoaded();
        }

        @Override
        public String source() {
            return raw;
        }
    }

    /**
     * Named-state test: {@code self has_state 协奏} / {@code target has_state 触电}.
     *
     * <p>Reads the state off the party named on the left through
     * {@link com.laosun.aluminium.models.buff.BuffManager#hasState(String)}, so "the target is shocked" and
     * "I am in [转魄]" are the same mechanism - an ordinary buff that happens to be a
     * {@link com.laosun.aluminium.models.buff.StateBuff}. The state name is <b>not</b> lower-cased: it is
     * data, spelled by the rule file, and the two sides must agree exactly.
     *
     * <p>A party that does not exist for this event ({@code target} on an event with no subject)
     * <b>fails</b> the condition, exactly like {@link Equality} and {@link Numeric}: "the rule matched" must
     * never be the accidental outcome of a missing party.
     */
    /**
     * "That party carries a negative modifier on this attribute".
     *
     * <p>A party that does not exist for this event FAILS, exactly like {@link HasState}: "the rule matched" must never
     * be the accidental outcome of a missing party.
     */
    static final class HasDebuffOn implements Condition, PartyCondition {

        private final String subject;
        private final String attribute;
        private final String raw;

        HasDebuffOn(String subject, String attribute, String raw, TriggerSpec spec) {
            if (!STATE_SUBJECTS.contains(subject)) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' asks about a debuff on \"" + subject + "\"; the keyword names its "
                                + "party with one of " + STATE_SUBJECTS + " before \"_debuff:\" "
                                + "(source: " + spec.getSource() + ")");
            }
            if (attribute.isEmpty()) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' names no attribute after \"_debuff:\"; write the attribute whose "
                                + "value was lowered, for example \"target_debuff:DEFENCE\" "
                                + "(source: " + spec.getSource() + ")");
            }
            if (AttributeType.fromString(attribute) == null) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' names the attribute \"" + attribute + "\", which is not one this "
                                + "engine knows (source: " + spec.getSource() + ")");
            }
            this.subject = subject;
            this.attribute = attribute;
            this.raw = raw;
        }

        @Override
        public CanHit partyOf(TriggerContext ctx) {
            return switch (subject) {
                case "self" -> ctx.owner();
                case "actor" -> ctx.actor();
                case "target" -> ctx.target();
                default -> null;
            };
        }

        @Override
        public boolean test(TriggerContext ctx) {
            CanHit who = partyOf(ctx);
            if (who == null) {
                return false;
            }
            AttributeType type = AttributeType.fromString(attribute);
            return !who.getAttribute(type).filterBySource(DoubleValue.Modifier.ModifierSource.DEBUFF).isEmpty();
        }

        @Override
        public String source() {
            return raw;
        }
    }

    /**
     * "[X]结束时": the state NAMED X has just left the subject.
     *
     * <p>Note: It cannot be written as {@code has_state}: the removal happens first, so by the time this event
     * fires the carrier no longer has it. The name rides on the event instead.
     */
    static final class StateEnded implements Condition, PartyCondition {

        private final String subject;
        private final String state;
        private final String raw;

        StateEnded(String subject, String state, String raw, TriggerSpec spec) {
            if (state.isEmpty()) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' names no state after \"state_ended\" "
                                + "(source: " + spec.getSource() + ")");
            }
            this.subject = subject;
            this.state = state;
            this.raw = raw;
        }

        @Override
        public CanHit partyOf(TriggerContext ctx) {
            return switch (subject) {
                case "self" -> ctx.owner();
                case "actor" -> ctx.actor();
                case "target" -> ctx.target();
                default -> null;
            };
        }

        @Override
        public boolean test(TriggerContext ctx) {
            return ctx.battle() != null && state.equals(ctx.battle().getLastStateEndedName());
        }

        @Override
        public String source() {
            return raw;
        }
    }

    static final class HasState implements Condition, PartyCondition {

        private final String subject;
        private final String state;
        private final String raw;

        HasState(String subject, String state, String raw, TriggerSpec spec) {
            if (state.isEmpty()) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' names no state after \"has_state\" "
                                + "(source: " + spec.getSource() + ")");
            }
            this.subject = subject;
            this.state = state;
            this.raw = raw;
        }

        @Override
        public CanHit partyOf(TriggerContext ctx) {
            return switch (subject) {
                case "self" -> ctx.owner();
                case "actor" -> ctx.actor();
                case "target" -> ctx.target();
                default -> null;
            };
        }

        @Override
        public boolean test(TriggerContext ctx) {
            CanHit who = partyOf(ctx);
            return who != null && who.getBuffManager().hasState(state);
        }

        @Override
        public String source() {
            return raw;
        }
    }

    /**
     * Identity comparison: {@code actor == self} / {@code actor != self} / {@code target == self} /
     * {@code actor == summon} / …
     *
     * <p>{@code actor} is who caused the event, {@code target} is what it happened to; {@code self} is the
     * rule's owner and {@code summon} is one of that owner's summons. See {@link TriggerContext}.
     */
    static final class Equality implements Condition {

        private final String variable;
        /**
         * {@code self} or {@code summon} - which party the variable is compared against.
         */
        private final String otherToken;
        private final boolean negated;

        Equality(String variable, String otherToken, boolean negated) {
            this.variable = variable;
            this.otherToken = otherToken;
            this.negated = negated;
        }

        @Override
        public boolean test(TriggerContext ctx) {
            CanHit subject = switch (variable) {
                case "actor" -> ctx.actor();
                case "target" -> ctx.target();
                default -> null;
            };
            if (!"summon".equals(otherToken) && !"countdown".equals(otherToken)) {
                boolean equal = subject == ctx.owner();
                return negated != equal;
            }
            // Note: Both remaining terms are read off the FIELD, so a context with no battle cannot answer them and the
            // condition fails for BOTH polarities -- the same convention `summon` follows (answering "no battlefield,
            // therefore not mine" would make `actor != countdown` silently true everywhere; see the note below).
            if (ctx.battle() == null || ctx.owner() == null) {
                return false;
            }
            if ("countdown".equals(otherToken)) {
                boolean own = ctx.battle().countdownsOf(ctx.owner()).contains((Countdown) subject);
                return negated != own;
            }
            // "…is one of my summons" is read off the FIELD, so a context with no battle cannot answer it and
            // the condition fails - for BOTH polarities. That is the same rule `self_summon_count` follows
            // (NaN compares false against everything), and it matters more here: answering "no battlefield,
            // therefore not my summon" would make `actor != summon` silently TRUE for every event, i.e. a rule
            // that says "anyone but my summon attacked" would fire on everything. A wrong answer with no
            // symptom is what this convention exists to prevent.
            if (ctx.battle() == null || ctx.owner() == null) {
                return false;
            }
            // Identity, not headcount (cone 20022's "忆灵消失时移除…[缅怀]"): a summon that has ALREADY
            // DIED is still "one of my summons" -- the camp roster keeps its corpse, and the case that needs this is
            // exactly the KILL whose victim is that corpse. Note: `summonsOf` deliberately answers the LIVING question
            // (self_summon_count reads it, and a headcount of corpses is not what it means), so identity asks the unit
            // itself instead. Measured: with the living list, `target == summon` was false for a just-killed memosprite.
            boolean own = subject instanceof Summon summon && summon.getMaster() == ctx.owner();
            return negated != own;
        }

        @Override
        public String source() {
            return variable + (negated ? " != " : " == ") + otherToken;
        }
    }

    /**
     * The numeric variables {@code hit_count}, {@code hp_percent}, {@code target_debuff_count},
     * {@code self_summon_count} and their siblings are the complete, closed set of <b>plain</b> names (the
     * authoritative list is this, and a name must be added to all of that set, the value
     * switch below, and {@code UnitDisciplineTest}'s pin).
     *
     * <p>Each reads from a different place, which is why the names say so: {@code hit_count} comes from the
     * event, {@code hp_percent} from the rule's owner (a fact about me), and {@code target_debuff_count} from
     * the event's <b>subject</b> ("how many negative effects are on the target" - the count that matters is the one on the unit the
     * rule is talking about, not on me).
     *
     * <p>{@code self_summon_count} is the one that reads the <b>field</b> rather than the event or the owner:
     * how many living summons are out whose master is this rule's owner. It is spelled {@code self_}...
     * although {@code hp_percent} does not bother, because a bare {@code summon_count} would read like "how
     * many summons are on the battlefield" - a different question with a different answer, and one nobody has
     * asked for yet.
     *
     * <p>Note: {@code self_attr:<ATTRIBUTE>} and {@code self_resource:<NAME>} are the two parameterised
     * members: neither is listed above, and both are validated separately - the first against
     * {@link AttributeType}, the second for shape here and for existence where the character is assembled.
     */
    static final Set<String> NUMERIC_VARIABLES =
            Set.of("ally_count", "enemy_count", "hit_count", "weakness_hit_count", "target_weakness_count", "hp_percent", "target_hp_percent", "target_hp_percent_before", "target_debuff_count", "self_summon_count",
                    "target_summon_count", "self_max_energy", "self_energy_percent", "from_skill_id", "damage_skill_key", "target_dot_count",
                    "actor_hp_percent",
                    // "when entering combat, if the allies' Skill Points limit is #2 or higher" (reader: light cone 23046):
                    // the cap is not a field of the unit -- it belongs to the battle's skill point policy -- so the value
                    // comes from the battle, and a context without one answers NaN like every other unreadable variable.
                    "skill_point_max",
                    // The owner's Eidolon rank, as a condition. Note: `min_eidolon` already gates a whole RULE on it, and that
                    // is enough when the clause IS the Eidolon's ("【深度学习】时 ... 抗性穿透提高 20%"). It is not enough when an
                    // Eidolon clause MODIFIES an effect the base kit also has at the same event: the two rules then carry
                    // identical content and differ only in a rule-level field, which is exactly what the duplicate guard exists
                    // to catch (measured: 1503's Eidolon 4 doubling was refused as "one sentence written twice"). Reading the
                    // rank as a CONDITION puts the difference inside the clause, where the guard can see it and where a reader
                    // can see it too.
                    "eidolon",
                    // "施放欢愉技计入的笑点大于等于 20 个" (reader: 1506 Silver Wolf LV.999's trace 1506102). Distinct from
                    // `self_resource:笑点`: that reads a POOL's current value, while this reads the count Elation damage is
                    // actually computed from - the merged snapshots taken when 【好活当赏】 was granted. They differ whenever the
                    // pool has moved since the state arrived, which is exactly the case the state exists to describe.
                    "counted_laughs");

    /**
     * The prefix of one parameterised numeric variable: {@code self_attr:SPEED}.
     *
     * <p>Why the subject is fixed at {@code self} and not a general {@code <subject>_attr:<TYPE>}: every
     * attribute threshold in the shipped data is about the wearer ("the wearer's speed / crit rate / break effect / Max HP ..."),
     * and an axis with one used value is an axis nobody has tested. Adding {@code target_attr:} later is a
     * few lines in the same place once some content actually needs it.
     *
     * <p>Note: Package-private rather than private because {@code TriggerInterpreter} spells the <b>same</b> prefix for
     * an effect's {@code "scale"} ("raises the value by X% of <my own attribute>"): the condition's "read my attribute"
     * and the effect's "derive from my attribute" are one concept, and two literals would be able to drift.
     */
    static final String SELF_ATTR_PREFIX = "self_attr:";

    /**
     * The prefix of a parameterised numeric variable counting OUR side: {@code allies_with_path:<PATH>} -
     * "how many characters of that Path are on my team".
     *
     * <p><b>Why it was needed.</b> The Elation characters scale their own kits by how many Elation characters
     * stand with them: 1503 Pearl's Ultimate advances its target by 10%/15%/30% at 1/2/3 such allies and grants
     * an extra turn at 4, her Eidolon 1 raises the team's Elation by 10%/20%/60% at 2/3/4, and her Elation skill
     * adds 10%/15%/20%/40% damage at 1/2/3/4. The DSL could ask whether ONE unit has a Path ({@code has_path})
     * but not how many do, so none of those tiers could be written.
     *
     * <p>The Path name is validated with the same {@code requirePath} the {@code has_path} keyword uses, so a
     * typo is refused at load time rather than counting zero.
     */
    static final String ALLIES_WITH_PATH_PREFIX = "allies_with_path:";

    /**
     * The prefix of a whole-field count: {@code enemy_state_count:<STATE>} - "how many living enemies are in
     * that state right now".
     *
     * <p><b>Why it had to exist.</b> The corpus states conditions about the battlefield rather than about one
     * unit: 1206 Sushang's trace 破敌 reads "施放普攻或战技后，<b>若场上有敌方目标处于弱点击破状态</b>，则素裳的行动提前
     * 15%", and 1503 Pearl's Eidolon 1 states its Elation bonus over "队伍中欢愉命途角色数量". The DSL could ask
     * whether ONE named party is in a state ({@code <who> has_state <name>}), and could count the enemy camp as a
     * whole ({@code enemy_count}), but nothing could count <b>how many enemies satisfy a state</b> - so the
     * sentence had no spelling at all, and the honest options were to register it or to approximate it.
     *
     * <p>A COUNT rather than a boolean on purpose: "any enemy is broken" is {@code >= 1}, and the same variable
     * also spells "two enemies are broken" or "each broken enemy", which the corpus will ask for. The gain over a
     * boolean is free; the risk it adds is none, because the reader is one line.
     *
     * <p>The state name is resolved by the <b>same</b> {@code BuffManager.hasState} the {@code has_state}
     * keyword calls, so {@code target has_state 弱点击破} and {@code enemy_state_count:弱点击破 >= 1} cannot
     * disagree about what "in that state" means - including the engine's own aliases such as
     * {@code BuffManager.BROKEN_STATE}.
     *
     * <p>Note: The ally-side mirror ({@code ally_state_count:}) is deliberately NOT added. Every whole-field
     * count in the shipped data is about the enemy camp, and an axis with one used value is an axis nobody has
     * tested (§3.1) - add the sibling spelling when a sentence needs it.
     */
    static final String ENEMY_STATE_COUNT_PREFIX = "enemy_state_count:";

    /**
     * The prefix of the other parameterised numeric variable: {@code self_resource:<resource>} - "how many
     * stacks of [a resource] I have right now".
     *
     * <p><b>Why it had to exist.</b> A stack/charge resource is not an attribute (so {@code self_attr} cannot
     * read it), not energy (so {@code self_max_energy} cannot), and not a state (so {@code has_state} cannot):
     * it is a number that lives on the combatant's {@code ResourceManager}. 41 of the 9character documents
     * gate something on "charge reaches its cap" / "stacks >= N", which is the largest single hole in the corpus: the ops
     * to <b>write</b> such a resource exist, but nothing can read one back.
     *
     * <p>Note: Reading an <b>undeclared</b> resource answers {@code NaN}, never {@code 0} - "cannot read it, so the
     * condition fails", the same rule {@code self_summon_count} follows. {@code ResourceManager.value} answers
     * {@code 0} for an id nobody registered, and a rule silently gated on "0" is exactly the wrong answer with
     * no symptom this project refuses. (In a shipped character that state is impossible: the assembly point
     * refuses a rule that reads a resource its character never declares.)
     */
    static final String SELF_RESOURCE_PREFIX = "self_resource:";

    /**
     * A resource read off the rule owner's <b>summon</b> (1141526's [故事], which the sentence puts on the memosprite: "使德谬歌获得 1 点[故事]").
     *
     * <p>Why the actor will not do: `RESOURCE_CHANGED` is raised with the RULE'S OWNER as both actor and target (measured in `fireResourceChanged`: `CanHit holder = ctx.owner()`), and its
     * delta is measured on that owner's store. So a condition about the summon's own counter has to reach through the owner -- this prefix is that reach.
     */
    static final String SUMMON_RESOURCE_PREFIX = "summon_resource:";

    /**
     * The keyword of the "the named state just left that unit" condition.
     *
     * <p>Boundary-guarded for the same reason {@link #HAS_STATE} is: a state whose NAME contains the
     * keyword must not be mistaken for the operator. The answer comes from the event
     * ({@code TriggerContext.battle().getLastStateEndedName()}) rather than from the unit, because the state
     * is already gone by the time it fires -- which is exactly why the event carries the name.
     */
    private static final Pattern STATE_ENDED_KEYWORD =
            Pattern.compile("(?<![\\w])state_ended(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The keyword of the named-state condition, and the parties it may ask about.
     *
     * <p>The keyword has to stand alone (a state whose name contains {@code has_state} must not be
     * mistaken for the operator), hence the lookarounds rather than a plain {@code contains}.
     */
    private static final Pattern HAS_STATE =
            Pattern.compile("(?<![\\w])has_state(?![\\w])", Pattern.CASE_INSENSITIVE);

    static final Set<String> STATE_SUBJECTS = Set.of("self", "actor", "target");

    /**
     * The keyword of the "that attribute of mine/theirs was lowered" condition (cone 22000's
     * "攻击防御力被降低的敌方目标后恢复能量" and cone 21044's "处于防御降低或减速状态下的敌人").
     *
     * <p>Why not {@code DebuffClass}: that enum names the two FAMILIES the corpus groups states into (control, dot),
     * and a lowered attribute belongs to neither. The fact that actually exists in the engine is a MODIFIER whose source is
     * {@link DoubleValue.Modifier.ModifierSource#DEBUFF}, which is exactly what "被降低" asserts.
     */
    private static final Pattern DEBUFF_ON =
            Pattern.compile("(?<![\\w])(?<subject>self|actor|target)_debuff:(?<attribute>[A-Za-z_]+)",
                    Pattern.CASE_INSENSITIVE);

    /**
     * The {@code has_path} keyword, read exactly like {@link #HAS_STATE}.
     */
    private static final Pattern HAS_PATH =
            Pattern.compile("(?<![\\w])has_path(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code is_ally} keyword: "&lt;who&gt; is on OUR side" - read exactly like the other two predicates.
     */
    private static final Pattern IS_ALLY =
            Pattern.compile("(?<![\\w])is_ally(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code is_enemy} keyword: "&lt;who&gt; is on the OTHER side" - the mirror of {@link #IS_ALLY}.
     *
     * <p><b>Why it was needed.</b> The camp vocabulary was one-sided: a rule could say "a teammate attacked"
     * ({@code actor is_ally}) but not "an enemy did", so every clause about the other camp had to be written some
     * other way or not at all. That is the second half of F-5, and it became writable once an enemy's attack had
     * an event of its own ({@code TriggerEvent.ENEMY_ATTACK}).
     */
    private static final Pattern IS_ENEMY =
            Pattern.compile("(?<![\\w])is_enemy(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code is_summon} keyword: "&lt;who&gt; is a memosprite" -- the fourth predicate.
     *
     * <p>Note: Why it was needed: "对万敌施放时…" is 1415's memosprite skill 8, and the only spelling that names a SKILL is
     * {@code from_skill_id}, which carries the SLOT -- so on its own it also matches any other unit's slot-16 skill aimed at the same
     * target, enemies included. A condition about the CASTER's nature is what makes the trigger exact.
     */
    private static final Pattern IS_SUMMON =
            Pattern.compile("(?<![\\w])is_summon(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code is_other_ally} keyword: "<b>that unit is on our side and is not me</b>".
     *
     * <p>Note: Why it exists: the corpus says "卡芙卡的队友对敌方目标施放普攻后…" in 16 files, and neither existing spelling fits -
     * {@code actor is_ally} counts the owner herself (she is in {@code battle.allies}), while {@code !actor == self} is refused
     * because negation only applies to party conditions and an equality is not one.
     */
    private static final Pattern IS_OTHER_ALLY =
            Pattern.compile("(?<![\\w])is_other_ally(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code is_companion} keyword: "<b>that unit is a Trailblaze Companion</b>".
     */
    private static final Pattern IS_COMPANION =
            Pattern.compile("(?<![\\w])is_companion(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code has_companion_ally} keyword: "<b>somebody else on my side is a Trailblaze Companion</b>".
     */
    private static final Pattern HAS_COMPANION_ALLY =
            Pattern.compile("(?<![\\w])has_companion_ally(?![\\w])", Pattern.CASE_INSENSITIVE);

    private static final Pattern IS_PARTY_FIRST =
            Pattern.compile("(?<![\\w])is_party_first(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code has_same_path_ally} keyword: "somebody else on my side walks my Path".
     */
    private static final Pattern SAME_PATH_ALLY =
            Pattern.compile("(?<![\\w])has_same_path_ally(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code shares_path_with_an_ally} keyword: "<b>that unit walks the same Path as somebody else on our
     * side</b>". Its reader is light cone 21046's "if two or more of our characters share any one Path, make those characters...".
     *
     * <p>The existing {@code has_same_path_ally} answers for the PARTY (it looks from the rule's owner outward), so as a
     * per-target filter it would pass every candidate -- the sentence names "these characters", not the party. This one is asked
     * ABOUT a candidate, exactly like {@code is_same_element_as_self}.
     */
    private static final Pattern SHARES_PATH_WITH_AN_ALLY =
            Pattern.compile("(?<![\\w])shares_path_with_an_ally(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code is_same_element_as_self} keyword: "<b>that unit carries the same element as me</b>".
     *
     * <p>It is the per-CANDIDATE sibling of {@code damage_element_is_self}: that one asks about the damage instance
     * being settled, this one about a unit -- which is what a per-target filter ({@code target_when}) needs, because
     * there the candidate sits in {@code target} and the rule's owner in {@code owner()}. Its reader is relic set 312's
     * "与装备者相同属性的其他我方角色".
     */
    private static final Pattern IS_SAME_ELEMENT =
            Pattern.compile("(?<![\\w])is_same_element_as_self(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code is_other_same_element_as_self} variant: the same question with the owner excluded --
     * "与装备者相同属性的其他我方角色" (relic set 312). It exists separately because the
     * owner is not a candidate the sentence means, and because a filter on a BATTLE_START rule cannot state a
     * `target`-subjected condition at all (measured: the loader refuses those -- that event carries no
     * actor and no target, so only argument-less keywords work there).
     */
    private static final Pattern IS_OTHER_SAME_ELEMENT =
            Pattern.compile("(?<![\\w])is_other_same_element_as_self(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code has_weakness} keyword: "&lt;who&gt; is weak to this element".
     */
    private static final Pattern HAS_WEAKNESS =
            Pattern.compile("(?<![\\w])has_weakness(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code has_skill} keyword: "&lt;who&gt; carries this skill slot at all" - "若目标拥有欢愉技" (8009/8010).
     *
     * <p><b>Why a condition had to exist for this.</b> The sentence branches on whether the unit it names has the
     * skill, and nothing in the DSL could ask that: {@code has_state} asks about a buff, {@code has_path} about a
     * Path, {@code has_weakness} about an enemy's bar, and the party predicates about which side a unit is on.
     * The branch matters because the other one says something <i>else</i> ("若目标不拥有欢愉技，使其行动提前50%"),
     * so an unguarded rule would not merely fire too often - it would fire on exactly the units the document sends
     * down the other path.
     *
     * <p>Note: It is also the guard {@code CAST_SKILL} needs: that op <b>throws</b> when the named unit has no such
     * slot (see {@code TriggerEffects#castSkill}), so without this condition the auto-cast clause would explode
     * on every ally who has no Elation skill.
     */
    private static final Pattern HAS_SKILL =
            Pattern.compile("(?<![\\w])has_skill(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code has_shield} keyword: "&lt;who&gt; currently holds a shield" - read exactly like the other
     * argument-less predicates.
     */
    private static final Pattern HAS_SHIELD =
            Pattern.compile("(?<![\\w])has_shield(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code is_slowed} keyword: "<b>that unit's Speed is currently lowered</b>".
     *
     * <p>Read exactly like the other party predicates ({@link #requireStateSubject} for the subject, the same
     * {@code !} prefix for the opposite), and evaluated by {@code TriggerTable.IsSlowed}, which asks the SPEED
     * attribute for its negative modifiers - so a slow from ANY source counts, not only one this file applied.
     */
    private static final Pattern IS_SLOWED =
            Pattern.compile("(?<![\\w])is_slowed(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code from_rule} qualifier of {@code has_shield}: the shield was created by the rule with this id.
     */
    private static final Pattern FROM_RULE =
            Pattern.compile("(?<![\\w])from_rule(?![\\w])\\s*", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code from_skill} keyword: "the instance that caused this event came from this slot"
     * ("施放<b>战技</b>对敌方目标造成弱点击破时").
     */
    private static final Pattern FROM_SKILL =
            Pattern.compile("(?<![\\w])from_skill(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The {@code from_category} keyword: "the cast that caused this event was of this CATEGORY"
     * ("when the wearer casts an elation skill", light cone 21064 / 21066 / 23058 / 23064).
     *
     * <p>Why it exists next to {@code from_skill}: the slot names a SKILL SLOT ({@code SKILL} = an ordinary Skill), while
     * a memosprite's or an elation kit's cast is a category the slot vocabulary does not have at all --
     * {@code SkillCategory.ELATION_DAMAGE} is precisely the one {@code SkillType} lacks. Reading the category directly is
     * the honest spelling for those sentences; {@code from_skill} keeps its meaning for the slot ones.
     */
    private static final Pattern FROM_CATEGORY =
            Pattern.compile("(?<![\\w])from_category(?![\\w])", Pattern.CASE_INSENSITIVE);

    /**
     * The bare keyword {@code damage_is_attack}: "the instance being settled counts as an attack".
     *
     * <p><b>Why this exists</b>: additional damage is settled as a real instance with
     * {@code notCountsAsAttack()} set (see {@code Battle.applyAdditionalDamage}), and it fires {@code DEALING_DAMAGE} like any
     * other instance - so a rule that reacts to "my attack hit a burning target" would react to its own additional damage,
     * forever. {@code !} cannot express the guard (negation is only for party conditions), so the guard is stated positively:
     * this is true exactly when the instance is an ordinary attack, and false for additional damage.
     */
    static final String DAMAGE_IS_ATTACK = "damage_is_attack";

    /**
     * The bare keyword {@code damage_is_additional}: "the instance being settled is ADDITIONAL damage".
     *
     * <p>The complement of {@link #DAMAGE_IS_ATTACK}, and it has to be its own keyword: that one is stated positively on purpose (`!` is
     * only for party conditions), so a rule that needs the OTHER side -- 1415's ode of passage, "when Tribbie casts a follow-up attack and triggers the additional damage of Tribbie's zone, she additionally deals
     * #1 additional damage" -- had no way to say it. Note: The negation lives HERE, where the name announces it; `TriggerTable` records what happened
     * when it lived inside {@code damage_is_attack} instead: cone 23008's energy clause read +0.0, because every ordinary attack failed the
     * guard it was written to pass.
     */
    static final String DAMAGE_IS_ADDITIONAL = "damage_is_additional";

    /**
     * The bare keyword {@code damage_has_no_cast}: "the instance being settled names no cast" (1415's passage ode, whose extra hit must react to the ENGINE's
     * additional damage and not to its own).
     *
     * <p>Measured: the zone's additional damage arrives as {@code type=ADDITIONAL skillKey=0 castCategory=UNSPECIFIED}, while the instances a rule adds carry the {@code cast_category}
     * the rule states. `from_skill` cannot express this -- it parses a {@code SkillType}, so `from_skill UNSPECIFIED` is refused -- and without a discriminator the rule recurses.
     */
    static final String DAMAGE_HAS_NO_CAST = "damage_has_no_cast";

    /**
     * The bare keyword "造成与装备者相同属性的伤容" (readers: light cone 21011 and
     * relic set 312). No subject and no value, like {@link #DAMAGE_IS_ATTACK}: the second party is the RULE'S OWNER, so
     * "same Type as the wearer" is the only reading it can have.
     *
     * <p>The element lives on the unit ({@code Character.element}, set from the character data's own attribute), not on
     * the skill, and the damage instance carries the element it was built with -- so this compares the two directly. For a
     * memosprite owner the master's element is the one the sentence means, which is the same convention the MEMORY damage
     * hook uses in {@code Battle.assemble}.
     */
    static final String DAMAGE_ELEMENT_IS_SELF = "damage_element_is_self";

    /**
     * The events whose {@code TriggerContext} carries the <b>causing cast</b>, and therefore the only ones on which
     * {@code from_skill} can ever be true.
     *
     * <p>Note: Checked at load time, because the alternative is the failure mode this project keeps refusing: a rule that
     * loads, fires on every matching event, and silently never matches its condition. A kill and a weakness break both
     * happen while an instance is being settled ({@code Battle.applyDamage} / {@code Battle.reduceToughness}),
     * {@code DEALING_DAMAGE} is the instance's own event, and {@code ALLY_ATTACK} carries the cast's category
     * (it is the one event that fires <b>once per cast</b>, which is what "施放 2 次普攻/战技/终结技" counts).
     */
    private static final Set<TriggerEvent> DAMAGE_CARRYING_EVENTS =
            Set.of(TriggerEvent.DEALING_DAMAGE, TriggerEvent.BREAK, TriggerEvent.KILL, TriggerEvent.ALLY_ATTACK,
                    // The settled instance is still the same instance, so it carries it: that is what
                    // makes `damage_is_attack` available as the guard against "额外造成 N 次伤害" re-triggering
                    // itself (additional damage is not an attack).
                    TriggerEvent.DAMAGE_SETTLED);

    /**
     * The events whose context carries the <b>cast category</b>, which is what {@code from_skill} reads.
     *
     * <p>A superset of {@link #DAMAGE_CARRYING_EVENTS}: {@code CAST_SETUP} carries the category without a damage instance,
     * and it is precisely the event a rule needs for "施放…时" -- the modifier must exist while the cast's own
     * heal settles, and {@code ULT_CAST} / {@code SKILL_CAST} fire after that. Keeping the sets apart is what lets
     * {@code damage_is_attack} stay damage-only.
     */
    private static final Set<TriggerEvent> CAST_CARRYING_EVENTS =
            Set.of(TriggerEvent.DEALING_DAMAGE, TriggerEvent.BREAK, TriggerEvent.KILL, TriggerEvent.ALLY_ATTACK,
                    TriggerEvent.CAST_SETUP,
                    // The settled instance carries its own cast too: "终结技额外造成 2 次伤害…" needs
                    // to know WHICH skill produced the damage it is a share of, and `Battle.applyDamage` states the
                    // category when it announces the settlement (the same field KILL already rides).
                    TriggerEvent.DAMAGE_SETTLED,
                    TriggerEvent.ATTACK_FINISHED,
                    // A crit belongs to the instance that crit, and the instance names its cast: 「战技造成的伤害
                    // 触发暴击时」 (reader: 1002 Dan Heng) asks which slot produced the critting hit. Measured: this
                    // event had zero readers before that clause, so nothing was written against its absence.
                    TriggerEvent.CRIT_DEALT);

    /**
     * The resource behind a {@code self_resource:<NAME>} variable, or {@code null} for anything else.
     *
     * <p>Only the <b>shape</b> is checked here; whether the character declares that resource is checked where
     * the character is assembled, because a rule file cannot know its own cid and a <b>relic</b> rule is shared
     * by every wearer (the same reason {@code SUMMON} is checked there). Two shapes are refused outright:
     * <ul>
     *   <li>nothing after the prefix - a variable that reads no resource at all;</li>
     *   <li>a name containing whitespace, which the DSL cannot even preserve: the condition is split on the
     *       operator and both halves trimmed, so {@code self_resource:我的 资源 >= 3} would silently read
     *       "我的". A declaration refuses such a name for the same reason ({@code ResourceSpec}).</li>
     * </ul>
     *
     * @param variable the already-normalised variable token
     * @param raw      the original condition text, for the error messages
     * @param spec     the owning rule, for the source
     * @return the resource name, or {@code null} when the variable is not a resource read
     */
    /** The two spellings that read a named counter ({@code ADD_STACK}); the subject is the prefix. */
    /**
     * The two counter spellings, shared with the interpreter's {@code scale} family: a condition says
     * "I have 3 layers", and a scale says "the number is a share of how many layers I have" -- one wording, and making
     * them public is what keeps the two from drifting (the same reason {@link #SELF_ATTR_PREFIX} is not private).
     */
    public static final String SELF_STACKS_PREFIX = "self_stacks:";

    /**
     * The counter of the unit that CAUSED the event (cone 23061's "我方任意角色在自身同一回合内累计消耗 >= 4 点战技点").
     *
     * <p>The third subject, and it was missing: {@code self_stacks:} reads the rule's OWNER and {@code target_stacks:}
     * the event's target, but "any of our characters spends" puts the counter on the SPENDER -- a unit that is neither.
     * With {@code SKILL_POINT_SPENT} naming its spender as the actor ({@code onSpent(user, amount)}), a rule owned by
     * the light cone's wearer can finally read it.
     */
    public static final String ACTOR_STACKS_PREFIX = "actor_stacks:";

    public static final String TARGET_STACKS_PREFIX = "target_stacks:";

    /**
     * The two variables an identity comparison can ask about: who caused the event, and what it happened to.
     */
    private static final Set<String> IDENTITY_VARIABLES = Set.of("actor", "target");

    /**
     * The two parties an identity comparison can compare against: the rule's own character, and the summons
     * that character owns.
     *
     * <p>Deliberately not the whole target-selector vocabulary. The other selectors are either not a single
     * unit ({@code all_allies} / {@code party}) or would make the comparison vacuous ({@code attacker} can only
     * be the actor, and {@code target == target} is a tautology because the event already says who it happened
     * to) - and a spelling that can never mean anything is exactly what this DSL refuses at load time rather
     * than letting an author write it and wonder.
     */
    private static final Set<String> IDENTITY_TERMS = Set.of("self", "summon", "countdown");

    /**
     * Companion-group membership, by character id (never by name: Himeko (姬子) is a prefix of 姬子-启行).
     */
    static boolean isCompanion(CanHit unit) {
        return unit instanceof Character c && TRAILBLAZE_COMPANIONS.contains(c.getCid());
    }

    /**
     * The Path a {@code has_path} condition names, checked against the closed vocabulary of the nine.
     *
     * <p>Unlike {@code Path.fromName} - which degrades an unknown name to {@link
     * com.laosun.aluminium.enums.Path#OTHER} because data may legitimately carry a Path this build does not know -
     * a <b>rule file</b> may not: {@code target has_path 同谐} with a typo would silently become "the target is on
     * some other Path", and for Sunday (星期日)'s sentence that means the exception fires on exactly the units it was
     * written to exclude. So the name is refused while the file is read.
     */
    static com.laosun.aluminium.enums.Path requirePath(String name, String raw, TriggerSpec spec) {
        com.laosun.aluminium.enums.Path path = com.laosun.aluminium.enums.Path.fromNameOrNull(name);
        if (path == null) {
            throw new IllegalArgumentException(
                    "Condition '" + raw + "' has unknown \"has_path\" name '" + name + "'; the Paths are "
                            + String.join(", ", com.laosun.aluminium.enums.Path.names().stream().sorted().toList())
                            + " (source: " + spec.getSource() + ")");
        }
        return path;
    }

    /**
     * The {@code is_party_first} keyword: "<b>that unit is the FIRST CHARACTER of the party</b>".
     *
     * <p>Read from {@code battle.characters} -- the roster of characters, in party order -- and NOT from
     * {@code allies}, which has memosprites and servants appended as they are summoned (light cone 21025 and
     * relic 31 both mean the party as formed). Its reader is relic 31: "若装备者不是队伍第一名".
     */
    /**
     * The Trailblaze Companions group: the twenty character ids the documentation's glossary lists as
     * "Trailblaze Companions" -- the Trailblazer (8001-8010), Himeko (姬子) (1003), Himeko - Departure (姬子-启行) (1510), March 7th (三月七) (1001, 1224), Evernight (长夜月) (1413), Dan Heng (丹恒) (1002),
     * Dan Heng - Imbibitor Lunae (丹恒-饮月) (1213), Dan Heng - Permansor Terrae (丹恒-腾荒) (1414), Welt (瓦尔特) (1004), Sunday (星期日) (1313).
     *
     * <p>Note: PROVENANCE: membership is NOT marked by any data field -- AvatarConfig, AvatarCamp and SpecialAvatar were
     * all checked, and a co-occurrence scan over 2253 tables hit 355 generic ones. The ids themselves come from
     * character_data.json; only the GROUPING comes from the documentation's glossary (1510_姬子-启行.md:408 /
     * export_glossary.py:0). If a data marker is ever found, read it and delete this set. Its reader is relic 32.
     */
    static final java.util.Set<Integer> TRAILBLAZE_COMPANIONS = java.util.Set.of(
            8001, 8002, 8003, 8004, 8005, 8006, 8007, 8008, 8009, 8010,
            1001, 1002, 1003, 1004, 1213, 1224, 1313, 1413, 1414, 1510);
}



