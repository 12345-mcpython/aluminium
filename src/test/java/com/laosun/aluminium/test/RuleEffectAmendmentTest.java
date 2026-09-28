package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;

/**
 * {@code MODIFY_RULE}'s <b>third form</b> (2026-09-28): raise another rule's effect <b>value</b> or <b>duration</b>,
 * rather than how often it runs.
 *
 * <p><b>The sentences that need it.</b> 1215 寒鸦 星魂 4 「终结技的持续时间额外增加1回合」 and 星魂 6 「天赋的伤害提高效果额外提高
 * 10%」 (30% → 40%). ⚠ Neither may be written as <i>a second rule with a bigger number</i>: same-kind modifiers
 * <b>refresh instead of stacking</b>, so the extra rule would replace the first — 0.1 instead of 0.4, or a duration that
 * stays at 2 — and nothing would look broken.
 *
 * <p><b>What is pinned here.</b> That the raised value is what the ops actually read, that a duration amendment is
 * installed on the named rule, that the hand-written copy carries <b>every</b> field, and that an amendment naming a
 * rule with no such field is refused at load.
 */
public class RuleEffectAmendmentTest {
    private static final int CID = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double EPS = 1e-6;

    /** The value amendment is what the effect reads: 30% becomes 40%. */
    @Test
    public void theRaisedValueIsWhatTheOpUses() {
        double base = attackOf(new TriggerTable(CID, List.of(boostRule())), 0);
        // ⚠ The amendment rule comes FIRST: rules of one event run in file order, so an amendment filed after the rule
        // it amends would arrive too late to change anything (silently -- the value would simply stay at 30%).
        double withAmendment = attackOf(new TriggerTable(CID, List.of(percentAmendment(), boostRule())), 0);
        double expectedDelta = 0.1 * CharacterFactory.create(CID, LEVEL).getAttribute(AttributeType.ATTACK).get();

        Assertions.assertEquals(expectedDelta, withAmendment - base, 1.0,
                "「…额外提高10%」: the named rule's 30% is raised to 40%, i.e. a tenth of the base attack more than "
                        + "the unamended rule gives");
    }

    /**
     * The duration amendment is <b>what the effect gets</b>: a 2-turn state lasts 3 turns with 「额外增加1回合」.
     *
     * <p>⚠ The first version of this case only asserted that the amendment was filed
     * ({@code ruleEffectTurnsBonus == 1}), and removing the code that <i>applies</i> it left the test green (measured
     * mutation m2, 0 red) -- a pin on the bookkeeping instead of on the behaviour.
     */
    @Test
    public void theDurationAmendmentLengthensTheNamedRulesEffect() {
        Assertions.assertTrue(afterTicks(2, false), "precondition: an unamended 2-turn state is gone after 2 turns");
        Assertions.assertTrue(afterTicks(2, true),
                "「…持续时间额外增加1回合」 keeps it standing after the turn it used to expire on");
    }

    /** Applies the state, runs {@code ticks} of the owner's turns, and reports whether it is still there. */
    private static boolean afterTicks(int ticks, boolean amended) {
        Character hero = CharacterFactory.create(CID, LEVEL);
        TriggerTable table = amended
                ? new TriggerTable(CID, List.of(turnsAmendment(), stateRule()))
                : new TriggerTable(CID, List.of(stateRule()));
        hero.setTriggerTable(table);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(hero), List.of(enemy), fixed());
        battle.startBattle();
        Assertions.assertTrue(hero.getBuffManager().hasState("测试状态"), "precondition: the state landed");
        for (int i = 0; i < ticks; i++) {
            battle.currentMove = battle.queue.snapshot().stream()
                    .filter(signal -> signal.getCanHit() == hero)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no signal for the owner"));
            battle.beforeMove();
        }
        return hero.getBuffManager().hasState("测试状态");
    }

    /** A rule with an id that applies a 2-turn named state to its owner. */
    private static TriggerSpec stateRule() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "APPLY_BUFF");
        TriggerSpecs.set(effect, "buff", "测试状态");
        TriggerSpecs.set(effect, "turns", 2);
        TriggerSpecs.set(effect, "target", "self");
        TriggerSpec rule = TriggerSpecs.rule("BATTLE_START", null, effect);
        TriggerSpecs.set(rule, "id", "boost");
        return rule;
    }

    /**
     * ⚠ The copy that applies an amendment must carry <b>every</b> field: a field forgotten there would be silently
     * dropped for exactly the firings that amend something (a wrong number with no symptom).
     */
    @Test
    public void theCopyCarriesEveryField() throws Exception {
        // ⚠ The bean has getters but no setters (Gson fills it by reflection), so the fields are filled through the
        // same reflection helper the other data tests use -- and the comparison below is field-by-field through the
        // getters, which is what catches a field that `copy()` forgot.
        EffectSpec original = new EffectSpec();
        int filled = 0;
        for (java.lang.reflect.Field field : EffectSpec.class.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            Object value = switch (field.getType().getName()) {
                case "java.lang.String" -> "x";
                case "java.lang.Double" -> 0.25;
                case "java.lang.Integer" -> 2;
                case "java.lang.Boolean" -> Boolean.TRUE;
                case "java.util.List" -> List.of("y");
                default -> null;
            };
            if (value != null) {
                TriggerSpecs.set(original, field.getName(), value);
                filled++;
            }
        }
        Assertions.assertTrue(filled >= 25, "expected at least 25 fillable fields, filled " + filled);

        EffectSpec copy = original.copy();
        for (Method getter : EffectSpec.class.getDeclaredMethods()) {
            if (!getter.getName().startsWith("get") || getter.getParameterCount() != 0
                    || getter.getName().equals("getClass")) {
                continue;
            }
            Assertions.assertEquals(getter.invoke(original), getter.invoke(copy),
                    getter.getName() + " must survive the copy -- add it to EffectSpec.copy()");
        }
    }

    /** ⚠ Naming a rule that states no such field is refused at load: there would be no number to raise. */
    @Test
    public void anAmendmentWithNothingToRaiseIsRefused() {
        EffectSpec instant = new EffectSpec();
        TriggerSpecs.set(instant, "op", "GAIN_ENERGY");
        TriggerSpecs.set(instant, "amount", 5.0);
        TriggerSpecs.set(instant, "target", "self");
        TriggerSpec named = TriggerSpecs.rule("BATTLE_START", null, instant);
        TriggerSpecs.set(named, "id", "instant");

        EffectSpec amend = new EffectSpec();
        TriggerSpecs.set(amend, "op", "MODIFY_RULE");
        TriggerSpecs.set(amend, "rule", "instant");
        TriggerSpecs.set(amend, "effectTurns", 1);
        TriggerSpec amender = TriggerSpecs.rule("BATTLE_START", null, amend);

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(CID, List.of(named, amender)));
        Assertions.assertTrue(refused.getMessage().contains("duration"), refused.getMessage());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** A rule with an id and one 30% ATTACK boost lasting 2 turns. */
    private static TriggerSpec boostRule() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "ATTACK");
        TriggerSpecs.set(effect, "percent", 0.3);
        TriggerSpecs.set(effect, "turns", 2);
        TriggerSpecs.set(effect, "target", "self");
        TriggerSpec rule = TriggerSpecs.rule("BATTLE_START", null, effect);
        TriggerSpecs.set(rule, "id", "boost");
        return rule;
    }

    private static TriggerSpec percentAmendment() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_RULE");
        TriggerSpecs.set(effect, "rule", "boost");
        TriggerSpecs.set(effect, "effectPercent", 0.1);
        return TriggerSpecs.rule("BATTLE_START", null, effect);
    }

    private static TriggerSpec turnsAmendment() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_RULE");
        TriggerSpecs.set(effect, "rule", "boost");
        TriggerSpecs.set(effect, "effectTurns", 1);
        return TriggerSpecs.rule("BATTLE_START", null, effect);
    }

    private static double attackOf(TriggerTable table, int unused) {
        return hero(table).getAttribute(AttributeType.ATTACK).get();
    }

    private static Character hero(TriggerTable table) {
        Character hero = CharacterFactory.create(CID, LEVEL);
        hero.setTriggerTable(table);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(hero), List.of(enemy), fixed());
        battle.startBattle();
        return hero;
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }
}
