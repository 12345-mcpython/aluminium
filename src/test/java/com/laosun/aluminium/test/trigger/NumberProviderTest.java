package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.NumberProvider;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The <b>number provider</b> ({@code percent_from} / {@code amount_from}): one object that names where a number comes
 * from, replacing the field-per-source spellings and adding composition.
 *
 * <p><b>What is pinned, and why these four things.</b>
 * <ol>
 *   <li><b>The provider reads the same number the field spelling reads.</b> That is the whole migration contract: a
 *       {@code {"type": "skill_param", "skill": "SKILL", "index": 1}} must equal {@code percent_from_skill_param:
 *       "SKILL:1"} on the same rule, or the two spellings have already drifted.</li>
 *   <li><b>A constant provider is the literal.</b> The trivial case, and the one that proves the resolver is reached at
 *       all (a provider that silently fell back to 0 would fail here, and pass case 1 if both sides broke).</li>
 *   <li><b>Arithmetic composes.</b> The reason the feature exists: {@code skill_param + 0.05} is a number no single
 *       field can state.</li>
 *   <li><b>The loader refuses the mistakes Gson would swallow</b> - an unknown type, and the provider written beside the
 *       field spelling it replaces. Both would otherwise be a number that "looks" right and is not.</li>
 * </ol>
 *
 * <p>⚠ <b>Why a {@code MODIFY_ATTR} rather than a heal.</b> The number has to be <b>readable</b> after the rule fires,
 * and a heal needs a damaged unit (whose HP arithmetic then sits between the rule and the assertion). An attribute
 * modifier is the rule's own number, read back from the sheet.
 */
public class NumberProviderTest {

    private static final int OWNER = 1001;              // 三月七: has a SKILL row, so SKILL:1 exists
    private static final int LEVEL = 80;

    /**
     * {@code const} is the literal: {@code 10% ATTACK} raises the sheet by exactly 10% of the fresh value.
     */
    @Test
    public void aConstantProviderIsTheLiteralItStates() {
        double base = freshAttack();
        double raised = attackAfter(rule(modifyAttack(constant(0.1))));

        Assertions.assertEquals(base * 1.1, raised, 1e-9,
                "{\"type\": \"const\", \"value\": 0.1} must raise ATTACK by 10%");
    }

    /**
     * The migration contract: provider and field spelling are the same number, read from the same row.
     */
    @Test
    public void aSkillParamProviderIsTheLegacyFieldSpelling() {
        double viaProvider = attackAfter(rule(modifyAttack(skillParam("SKILL", 1))));
        double viaField = attackAfter(rule(modifyAttackLegacy("SKILL:1")));

        Assertions.assertEquals(viaField, viaProvider, 1e-9,
                "{\"type\": \"skill_param\", \"skill\": \"SKILL\", \"index\": 1} must equal percent_from_skill_param: \"SKILL:1\"");
        Assertions.assertNotEquals(freshAttack(), viaProvider,
                "and the row's share is not zero (otherwise the case would be vacuous)");
    }

    /**
     * Composition: the row share plus a constant, which no single field can spell.
     */
    @Test
    public void arithmeticComposesTwoSources() {
        double base = freshAttack();
        double viaRow = attackAfter(rule(modifyAttack(skillParam("SKILL", 1))));
        double rowShare = viaRow / base - 1;

        double composed = attackAfter(rule(modifyAttack(add(skillParam("SKILL", 1), constant(0.05)))));

        Assertions.assertEquals(base * (1 + rowShare + 0.05), composed, 1e-9,
                "\"the row's #1 plus 5%\" is a number no single field can state");
    }

    /**
     * An unknown type is refused when the table loads, not when the rule first fires.
     */
    @Test
    public void anUnknownTypeIsRefusedAtLoad() {
        NumberProvider bogus = new NumberProvider();
        bogus.setType("bogus");

        IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class,
                () -> table(rule(modifyAttack(bogus))));
        Assertions.assertTrue(error.getMessage().contains("bogus"), error.getMessage());
        Assertions.assertTrue(error.getMessage().contains("const"), "the message lists the known types: " + error.getMessage());
    }

    /**
     * The provider and the field spelling it replaces cannot both be stated: two answers to one question.
     */
    @Test
    public void theProviderAndTheFieldSpellingCannotBothBeStated() {
        EffectSpec both = modifyAttack(constant(0.1));
        TriggerSpecs.set(both, "percentFromSkillParam", "SKILL:1");

        IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class,
                () -> table(rule(both)));
        Assertions.assertTrue(error.getMessage().contains("percent_from"), error.getMessage());
    }

    // --- fixtures ---------------------------------------------------------------------------------------------------

    private static double freshAttack() {
        return CharacterFactory.create(OWNER, LEVEL).getAttribute(AttributeType.ATTACK).get();
    }

    /**
     * Runs one {@code BATTLE_START} rule on a fresh battle and returns the owner's ATTACK afterwards.
     */
    private static double attackAfter(TriggerSpec rule) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Battle battle = new Battle(List.of(owner), List.of(BattleFixture.monster()), BattleFixture.seeded());
        owner.setTriggerTable(table(rule));
        battle.startBattle();
        return owner.getAttribute(AttributeType.ATTACK).get();
    }

    private static TriggerTable table(TriggerSpec rule) {
        return new TriggerTable(OWNER, List.of(rule));
    }

    private static TriggerSpec rule(EffectSpec effect) {
        return TriggerSpecs.rule("BATTLE_START", null, effect);
    }

    private static EffectSpec modifyAttack(NumberProvider share) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "ATTACK");
        TriggerSpecs.set(effect, "percentFrom", share);
        TriggerSpecs.set(effect, "permanent", true);
        return effect;
    }

    /**
     * The same effect, in the field spelling the provider replaces.
     */
    private static EffectSpec modifyAttackLegacy(String spelled) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "ATTACK");
        TriggerSpecs.set(effect, "percentFromSkillParam", spelled);
        TriggerSpecs.set(effect, "permanent", true);
        return effect;
    }

    private static NumberProvider constant(double value) {
        NumberProvider provider = new NumberProvider();
        provider.setType("const");
        provider.setValue(value);
        return provider;
    }

    private static NumberProvider skillParam(String skill, int index) {
        NumberProvider provider = new NumberProvider();
        provider.setType("skill_param");
        provider.setSkill(skill);
        provider.setIndex(index);
        return provider;
    }

    private static NumberProvider add(NumberProvider left, NumberProvider right) {
        NumberProvider provider = new NumberProvider();
        provider.setType("add");
        provider.setInputs(List.of(left, right));
        return provider;
    }
}
