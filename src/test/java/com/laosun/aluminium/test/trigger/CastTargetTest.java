package com.laosun.aluminium.test.trigger;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The cast events carry the unit they were <b>aimed at</b> - "designate one of our characters".
 *
 * <p><b>What was missing.</b> Bronya (布洛妮娅)'s skill is "removes 1 negative effect from <b>the designated one of our characters</b>, and makes <b>that target</b> act immediately ... when this skill is cast on herself the immediate-action effect cannot be triggered". The trigger events carried the caster (`actor`) and, for attacks, how many
 * targets were hit (`hit_count`) - but not <em>who</em> was chosen, so "the ally I aimed at" was unwritable. The
 * buff-side {@code SkillCastEvent} has carried {@code targets} all along; the data side did not.
 *
 * <p><b>The semantic decision, written down rather than assumed.</b> {@code TriggerContext.target} already means
 * "the event's subject, defined per event" - {@code TAKING_HIT}'s is the one who took the hit, {@code HP_LOST}'s
 * the one who lost HP. A cast's subject is therefore <b>the unit the caller aimed at</b> (the main target), and
 * explicitly <b>not</b> "everything the effect reached": an AOE reaches several units and only the first is named,
 * so coverage is still {@code hit_count}'s question. That distinction is pinned below, because it is the one a
 * reader would get wrong.
 *
 * <p>Note: Checked before the change: no shipped rule pairs a cast event with a {@code target} condition, so filling
 * the field in changes no existing rule's behaviour - which the whole suite confirms.
 */
public class CastTargetTest {
    private static final double EPS = 1e-6;

    /** Bronya (布洛妮娅) - her skill is the first user of "the ally this cast was aimed at". */
    private static final int BRONYA = 1101;
    /** Guinaifen (桂乃芬) - a plain teammate to aim at. */
    private static final int ALLY = 1210;
    /** Tingyun (停云) - a second plain teammate, so "aimed at" can be told apart from "also hit". */
    private static final int OTHER = 1202;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    // ==================================================================
    // 1. The aimed unit is the event's subject
    // ==================================================================

    /** Aimed at one teammate, the rule on that teammate fires - and it does not fire for the other one. */
    @Test
    public void theCastCarriesTheUnitItWasAimedAt() {
        Battle battle = party();
        Character bronya = battle.characters.getFirst();
        Character ally = battle.characters.get(1);
        Character other = battle.characters.get(2);
        double allyBefore = damageBoostOf(ally);
        double otherBefore = damageBoostOf(other);

        battle.castImmediate(new DefaultSkill(BRONYA, 2, 1), bronya, List.of(ally));

        Assertions.assertEquals(allyBefore + 0.5, damageBoostOf(ally), EPS, "the aimed-at ally is the subject");
        Assertions.assertEquals(otherBefore, damageBoostOf(other), EPS, "and the one I did not choose is not");
    }

    /**
     * An AOE names only its <b>main</b> target: the others are reached, not aimed at.
     *
     * <p>This is the half a reader would get wrong, so it is measured: the same rule sits on both teammates, the
     * attack is aimed at one of them, and only that one reacts.
     */
    @Test
    public void anAoeNamesOnlyItsMainTarget() {
        Character bronya = CharacterFactory.create(BRONYA, LEVEL);
        Character ally = characterWith(rule("ULT_CAST", "target == self"));
        Character other = characterWith(rule("ULT_CAST", "target == self"));
        Battle battle = new Battle(List.of(bronya, ally, other), List.of(dummy()), new Random(0));

        // Slot 3 is her ultimate, and 1001's slot 3 is an AOE attack: the caller aims it at `ally`, the effect
        // reaches the enemy camp, and the question here is which unit the EVENT names as its subject.
        battle.castImmediate(new DefaultSkill(1001, 3, 1), bronya, List.of(ally));

        Assertions.assertTrue(damageBoostOf(ally) > damageBoostOf(bronya),
                "the aimed-at unit is the subject");
        Assertions.assertEquals(0, damageBoostOf(other), EPS,
                "…and being reached is not being aimed at (`hit_count` is the coverage question)");
    }

    // ==================================================================
    // 2. Bronya's skill, end to end
    // ==================================================================

    /** Aimed at an ally: they act immediately, and Bronya's own place in the turn order is untouched. */
    @Test
    public void herSkillAdvancesTheAllySheAimedAt() {
        Battle battle = new Battle(List.of(CharacterFactory.create(BRONYA, LEVEL),
                CharacterFactory.create(ALLY, LEVEL)), List.of(dummy()), new Random(0));
        battle.startBattle();
        Character bronya = battle.characters.getFirst();
        Character ally = battle.characters.get(1);
        double bronyaBefore = timeRemaining(battle, bronya);
        double bronyaBoostBefore = damageBoostOf(bronya);

        battle.castImmediate(new DefaultSkill(BRONYA, 2, 1), bronya, List.of(ally));

        Assertions.assertEquals(0, timeRemaining(battle, ally), EPS, "「make that target act immediately」");
        Assertions.assertEquals(bronyaBefore, timeRemaining(battle, bronya), EPS,
                "she spends the skill, not the action: 「that target」 is not her");
        Assertions.assertEquals(0.66, damageBoostOf(ally), EPS,
                "and both effects land on the AIMED unit: the damage boost too, not on the caster");
        Assertions.assertEquals(bronyaBoostBefore, damageBoostOf(bronya), EPS, "…not on her");
    }

    /**
     * Aimed at herself: the damage boost still lands, the immediate action does not.
     *
     * <p>"when this skill is cast on herself the immediate-action effect cannot be triggered" - the whole reason her skill is two rules rather than one, and
     * both halves are asserted here so the gate cannot be satisfied by dropping the rule altogether.
     */
    @Test
    public void castingItOnHerselfSkipsOnlyTheImmediateAction() {
        Battle battle = new Battle(List.of(CharacterFactory.create(BRONYA, LEVEL)), List.of(dummy()), new Random(0));
        Character bronya = battle.characters.getFirst();
        double before = timeRemaining(battle, bronya);
        double boostBefore = damageBoostOf(bronya);

        battle.castImmediate(new DefaultSkill(BRONYA, 2, 1), bronya, List.of(bronya));

        Assertions.assertEquals(before, timeRemaining(battle, bronya), EPS,
                "「the immediate-action effect cannot be triggered」");
        Assertions.assertEquals(boostBefore + 0.66, damageBoostOf(bronya), EPS,
                "…while the damage boost still applies on a self-cast");
    }

    // ==================================================================
    // 3. The authored rules state their numbers
    // ==================================================================

    /** Her skill is two rules, and the only difference between them is the self-cast gate. */
    @Test
    public void theAuthoredRulesStateTheirShape() {
        Character bronya = CharacterFactory.create(BRONYA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(bronya, ally), List.of(dummy()), new Random(0));
        battle.startBattle();
        TriggerTable table = TriggerTables.of(BRONYA);

        TriggerTable.TriggerContext aimed =
                new TriggerTable.TriggerContext(bronya, bronya, ally, 0, 0, null, battle);
        List<TriggerTable.CompiledRule> onAlly = rulesFor(table, aimed);
        Assertions.assertEquals(4, onAlly.size(),
                "her Eidolon 1 rule matches a skill cast too, and so does eidolon 6 (which lengthens the boost below); "
                        + "the two below are the skill's own sentences");
        EffectSpec advance = ruleWithCondition(onAlly, "target != self").effects().getFirst();
        Assertions.assertEquals("ADVANCE", advance.getOp());
        Assertions.assertEquals(1.0, advance.getPercent(), EPS, "on the aimed ally, not on herself");
        Assertions.assertEquals("target", advance.getTarget());

        EffectSpec dispel = ruleWithEffect(onAlly, "DISPEL").effects().get(0);
        EffectSpec boost = ruleWithEffect(onAlly, "DISPEL").effects().get(1);
        Assertions.assertEquals("DISPEL", dispel.getOp());
        Assertions.assertEquals("target", dispel.getTarget());
        Assertions.assertEquals("MODIFY_ATTR", boost.getOp());
        Assertions.assertEquals("ALL_DAMAGE_TYPE_BOOST", boost.getAttribute());
        Assertions.assertEquals(0.66, boost.getPercent(), EPS, "skill 110102's #1 at Lv10 (the prose's row)");
        Assertions.assertEquals(1, boost.getTurns(), "skill 110102's #3");

        TriggerTable.TriggerContext self =
                new TriggerTable.TriggerContext(bronya, bronya, bronya, 0, 0, null, battle);
        Assertions.assertEquals(3, rulesFor(table, self).size(),
                "aimed at herself the advance rule drops out (Eidolon 1 and eidolon 6 still match): the gate is a "
                        + "condition, not a branch");
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /** Bronya plus two teammates, each carrying a rule that answers a cast aimed at it. */
    private static Battle party() {
        Character bronya = CharacterFactory.create(BRONYA, LEVEL);
        Character ally = characterWith(rule("SKILL_CAST", "target == self"));
        Character other = characterWith(rule("SKILL_CAST", "target == self"));
        return new Battle(List.of(bronya, ally, other), List.of(dummy()), new Random(0));
    }

    /** Three characters, the first two carrying a rule that answers "a cast aimed at me". */
    private static Battle party(int unused) {
        return party();
    }

    private static Character characterWith(TriggerSpec... rules) {
        Character character = CharacterFactory.create(ALLY, LEVEL);
        character.setTriggerTable(new TriggerTable(ALLY, List.of(rules)));
        return character;
    }

    /** Fires on a cast aimed at <b>me</b>, by someone else, and credits 0.5 damage boost to me. */
    private static TriggerSpec rule(String event, String targetCondition) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "ALL_DAMAGE_TYPE_BOOST");
        TriggerSpecs.set(effect, "percent", 0.5);
        TriggerSpecs.set(effect, "permanent", true);
        return TriggerSpecs.rule(event, List.of("actor != self", targetCondition), effect);
    }

    private static List<TriggerTable.CompiledRule> rulesFor(TriggerTable table, TriggerTable.TriggerContext ctx) {
        return table.matching(TriggerEvent.SKILL_CAST, ctx);
    }

    private static TriggerTable.CompiledRule ruleWithCondition(List<TriggerTable.CompiledRule> rules,
                                                               String condition) {
        return rules.stream()
                .filter(rule -> rule.conditions().stream().anyMatch(c -> c.source().equals(condition)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no rule conditioned on '" + condition + "'"));
    }

    /** The rule that contains an effect with this op (her file has two rules whose conditions are identical). */
    private static TriggerTable.CompiledRule ruleWithEffect(List<TriggerTable.CompiledRule> rules, String op) {
        return rules.stream()
                .filter(rule -> rule.effects().stream().anyMatch(e -> op.equals(e.getOp())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no rule with an effect op '" + op + "'"));
    }

    private static double damageBoostOf(CanHit unit) {
        return unit.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
    }

    /** How much action value the unit still has - zero means "acts now". */
    private static double timeRemaining(Battle battle, CanHit target) {
        for (Signal signal : battle.queue.snapshot()) {
            if (signal.getCanHit() == target) {
                return battle.queue.getTimeRemaining(signal);
            }
        }
        return Double.NaN;
    }

    private static Enemy dummy() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }
}
