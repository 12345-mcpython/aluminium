package com.laosun.aluminium.test.data;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * "raise the number of times the talent's counter effect can trigger per turn by <b>1</b>" / "raise the <b>base chance</b> to freeze the enemy target by 15%" - a rule that raises a number on
 * <b>another rule</b>.
 *
 * <p><b>Why the vocabulary needed it.</b> The two shapes that look like they work are both wrong, and both are wrong by
 * a number nobody would see: writing a <i>second</i> rule with the raised value <b>adds</b> (a {@code per_turn: 3} rule
 * next to the {@code per_turn: 2} one fires five times a turn rather than raising the cap to three), and a second chance
 * rule <b>rolls twice</b> (1 − 0.5  x  0.35 = 82.5% instead of 65%). So the target rule is named ({@code "id"}) and the
 * raise is a fact about the combatant in this battle ({@code MODIFY_RULE} to {@code CanHit}), never an edit of the rule
 * itself - a table is compiled once per cid and a relic's rules are shared by every wearer.
 *
 * <p><b>What this file pins.</b> That the raise really reaches the two consumers (the per-turn cap and the roll), that
 * it is the <b>stated</b> number plus the raise rather than a replacement, that all the ambiguous or meaningless shapes
 * are refused at load time, and that March 7th (三月七)'s own file ships both sentences.
 */
public class RuleAmendmentTest {
    private static final double EPS = 1e-9;

    private static final int MARCH_7TH = 1001;
    private static final int LEVEL = 80;
    /** 1003010 states no {@code STAT_CTRL_*} resistances (Ice Edge (冰锋) cannot be frozen at all). */
    private static final int MONSTER = 1003010;

    /**
     * The per-turn raise, measured where it is observable: a counter that may fire twice now fires three times.
     *
     * <p>Three separate hits in the same turn, so the difference is exactly the raised cap - and the third firing is
     * the thing a second rule could not have produced (a second {@code per_turn} rule counts on its <b>own</b> key, so
     * it fires three more times, i.e. six).
     */
    @Test
    public void thePerTurnRaiseLetsTheRuleFireOnceMorePerTurn() {
        int withoutAmendment = counterFirings(false);
        int withAmendment = counterFirings(true);

        Assertions.assertEquals(2, withoutAmendment, "\"can trigger 2 times per turn\" is the rule's own number");
        Assertions.assertEquals(3, withAmendment, "…and \"raise by 1\" raises it to three, not to five");
    }

    /**
     * The base-chance raise, measured on the boundary: 0.5 raised by 0.15 is 0.65, so a roll of 0.6 lands and 0.does
     * not.
     *
     * <p>Note: This is the assertion a "write a second rule with 0.65" implementation cannot satisfy in the right
     * direction: it would land on 0.6 as well, but it would also land on <b>0.4</b> when the first rule's 0.5 already
     * admitted it - the two rolls compound, so the pair's behaviour is 82.5%, not 65%. The generator is pinned, so the
     * boundary is an assertion instead of a coin flip.
     */
    @Test
    public void theChanceRaiseMovesTheBoundaryByExactlyThatMuch() {
        Assertions.assertTrue(freezeLands(0.15, 0.6), "0.65 > 0.6, so 0.6 lands once the trace is in");
        Assertions.assertFalse(freezeLands(0.15, 0.7), "…and 0.7 is still above it");
        Assertions.assertFalse(freezeLands(0.0, 0.6), "the rule's own 0.5 does not reach 0.6");
        Assertions.assertTrue(freezeLands(0.0, 0.4), "…and it does reach 0.4 (the control case)");
    }

    /** Note: An unnamed target is refused: a reference that points at nothing must not load. */
    @Test
    public void anUnknownRuleIdIsRefusedAtLoad() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(MARCH_7TH, List.of(
                        counterRule("counter", 2),
                        amendPerTurn("no_such_rule"))));

        Assertions.assertTrue(refused.getMessage().contains("no_such_rule"), refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains("counter"), "the message lists the ids it knows");
    }

    /** Two rules sharing one id are refused, because a reference to that id would be ambiguous. */
    @Test
    public void aDuplicateRuleIdIsRefusedAtLoad() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(MARCH_7TH, List.of(
                        counterRule("counter", 2),
                        counterRule("counter", 1))));

        Assertions.assertTrue(refused.getMessage().contains("share the id"), refused.getMessage());
    }

    /** Note: Raising a limit the target does not state is refused: 0 + 1 would silently impose a cap of one. */
    @Test
    public void raisingAPerTurnLimitThatDoesNotExistIsRefused() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(MARCH_7TH, List.of(
                        counterRule("counter", null),
                        amendPerTurn("counter"))));

        Assertions.assertTrue(refused.getMessage().contains("per_turn"), refused.getMessage());
    }

    /** Note: Raising the chance of a rule that states none is refused: an unstated chance is 100%, with no number to raise. */
    @Test
    public void raisingAChanceThatIsNotStatedIsRefused() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(MARCH_7TH, List.of(
                        named("freeze", TriggerSpecs.rule("ULT_CAST", null,
                                TriggerSpecs.applyControl("冻结", 1, null, "all_enemies"))),
                        amendChance("freeze", 0.15))));

        Assertions.assertTrue(refused.getMessage().contains("base_chance"), refused.getMessage());
    }

    /** Both spellings at once (or neither) leave the reader guessing which number on the target moves. */
    @Test
    public void theTwoSpellingsAreMutuallyExclusive() {
        EffectSpec both = TriggerSpecs.rule("BATTLE_START", null, amend("counter", 1.0, 0.15))
                .getDoEffects().getFirst();
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.rule("BATTLE_START", null, both)));

        Assertions.assertTrue(refused.getMessage().contains("amount"), refused.getMessage());
    }

    /** A delta that is not a positive whole count (or not a fraction of 1) is refused where it is stated. */
    @Test
    public void anOutOfRangeDeltaIsRefused() {
        IllegalArgumentException fractional = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.rule("BATTLE_START", null, amend("counter", 0.5, null))));
        Assertions.assertTrue(fractional.getMessage().contains("whole number"), fractional.getMessage());

        IllegalArgumentException tooBig = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.rule("BATTLE_START", null, amend("freeze", null, 1.5))));
        Assertions.assertTrue(tooBig.getMessage().contains("fraction of 1"), tooBig.getMessage());
    }

    /** Note: A raise that is not made at battle start is refused: nothing would ever take it back. */
    @Test
    public void anAmendmentOutsideBattleStartIsRefused() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.rule("TURN_START", null, amend("counter", 1.0, null))));

        Assertions.assertTrue(refused.getMessage().contains("BATTLE_START"), refused.getMessage());
    }

    /** A field the op does not read is refused rather than ignored. */
    @Test
    public void anUnreadFieldIsRefused() {
        EffectSpec effect = amend("counter", 1.0, null);
        TriggerSpecs.set(effect, "turns", 2);

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.rule("BATTLE_START", null, effect)));

        Assertions.assertTrue(refused.getMessage().contains("no other field"), refused.getMessage());
    }

    // ==================================================================
    // The shipped content: 1001 Eidolon 4 and trace "冰咒"
    // ==================================================================

    /** Her file ships both raises: the counter's cap and the ultimate's chance. */
    @Test
    public void theShippedFileCarriesBothRaises() {
        Assertions.assertEquals(3, counterFiringsWithHerFile(4),
                "Eidolon 4's \"raise the number of times it can trigger per turn by 1\" raises the talent's 2 to 3");
        Assertions.assertEquals(2, counterFiringsWithHerFile(3),
                "…and below Eidolon 4 the talent still fires twice");
        Assertions.assertTrue(freezeLandsWithHerFile(0.6),
                "trace \"冰咒\" raises the ultimate's 0.5 to 0.65, so a 0.6 roll freezes");
        Assertions.assertFalse(freezeLandsWithHerFile(0.7), "…and 0.7 does not");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** How many times the counter rule fires when one turn is given three hits, with or without a raise. */
    private static int counterFirings(boolean amended) {
        Character hero = CharacterFactory.create(MARCH_7TH, LEVEL);
        List<TriggerSpec> specs = new ArrayList<>();
        specs.add(counterRule("counter", 2));
        if (amended) {
            specs.add(amendPerTurn("counter"));
        }
        hero.setTriggerTable(new TriggerTable(MARCH_7TH, specs));
        Battle battle = new Battle(List.of(hero), List.of(monster()), fixed(0.0));
        battle.startBattle();
        hero.getBuffManager().addBuff(new com.laosun.aluminium.models.buff.ShieldBuff(hero, 100, 3));

        int fired = 0;
        for (int hit = 0; hit < 3; hit++) {
            fired += battle.fireTriggers(TriggerEvent.TAKING_HIT, monster(), hero, 1, 0);
        }
        return fired;
    }

    /** The same measurement through her shipped file, at the given Eidolon rank. */
    private static int counterFiringsWithHerFile(int rank) {
        Character hero = CharacterFactory.create(MARCH_7TH, LEVEL, true, null, null, rank);
        Battle battle = new Battle(List.of(hero), List.of(monster()), fixed(0.0));
        battle.startBattle();
        hero.getBuffManager().addBuff(new com.laosun.aluminium.models.buff.ShieldBuff(hero, 100, 3));

        int fired = 0;
        for (int hit = 0; hit < 3; hit++) {
            fired += battle.fireTriggers(TriggerEvent.TAKING_HIT, monster(), hero, 1, 0);
        }
        return fired;
    }

    /**
     * Whether her shipped ultimate freezes an enemy, with the generator pinned at {@code roll}.
     *
     * <p>Her file states {@code base_chance: 0.5} and trace "冰咒" raises it by 0.15, so the boundary is 0.65.
     */
    private static boolean freezeLandsWithHerFile(double roll) {
        Character hero = CharacterFactory.create(MARCH_7TH, LEVEL);
        Battle battle = new Battle(List.of(hero), List.of(monster()), fixed(roll));
        battle.startBattle();

        battle.fireTriggers(TriggerEvent.ULT_CAST, hero, null, 0, 0);

        return battle.enemyUnits().getFirst().getBuffManager().hasState("冻结");
    }

    /** One freeze rule (with or without a stated chance) plus a raise, driven with a pinned generator. */
    private static boolean freezeLands(double raise, double roll) {
        Character hero = CharacterFactory.create(MARCH_7TH, LEVEL);
        List<TriggerSpec> specs = new ArrayList<>();
        specs.add(named("freeze", TriggerSpecs.rule("ULT_CAST", null,
                TriggerSpecs.applyControl("冻结", 1, 0.5, "all_enemies"))));
        if (raise > 0) {
            specs.add(amendChance("freeze", raise));
        }
        hero.setTriggerTable(new TriggerTable(MARCH_7TH, specs));
        Battle battle = new Battle(List.of(hero), List.of(monster()), fixed(roll));
        battle.startBattle();

        battle.fireTriggers(TriggerEvent.ULT_CAST, hero, null, 0, 0);

        return battle.enemyUnits().getFirst().getBuffManager().hasState("冻结");
    }

    /** Her talent's counter, in the shape the tests above need (a damage instance aimed at the attacker). */
    private static EffectSpec damageCounter() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "skill", "TALENT");
        TriggerSpecs.set(effect, "damageParam", 0);
        TriggerSpecs.set(effect, "target", "attacker");
        return effect;
    }

    private static TriggerSpec named(String id, TriggerSpec rule) {
        TriggerSpecs.set(rule, "id", id);
        return rule;
    }

    /** A counter rule with the given id (and, optionally, the per-turn limit the documents state). */
    private static TriggerSpec counterRule(String id, Integer perTurn) {
        TriggerSpec spec = named(id, TriggerSpecs.rule("TAKING_HIT", null, damageCounter()));
        if (perTurn != null) {
            TriggerSpecs.set(spec, "perTurn", perTurn);
        }
        return spec;
    }

    /** {@code {"op":"MODIFY_RULE","rule":"…","amount":1}} - one more firing per turn. */
    private static TriggerSpec amendPerTurn(String rule) {
        return TriggerSpecs.rule("BATTLE_START", null, amend(rule, 1.0, null));
    }

    /** {@code {"op":"MODIFY_RULE","rule":"…","percent":0.15}} - a higher base chance. */
    private static TriggerSpec amendChance(String rule, double percent) {
        return TriggerSpecs.rule("BATTLE_START", null, amend(rule, null, percent));
    }

    private static EffectSpec amend(String rule, Double amount, Double percent) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_RULE");
        TriggerSpecs.set(effect, "rule", rule);
        TriggerSpecs.set(effect, "amount", amount);
        TriggerSpecs.set(effect, "percent", percent);
        return effect;
    }

    /** Compiles one rule into a table, which is where load-time validation runs. */
    private static TriggerTable tableOf(TriggerSpec rule) {
        return new TriggerTable(MARCH_7TH, List.of(rule));
    }

    private static Enemy monster() {
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        enemy.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(0));
        return enemy;
    }

    /** A generator that always answers the same value, so a probability becomes an assertion. */
    private static Random fixed(double value) {
        return new Random() {
            @Override
            public double nextDouble() {
                return value;
            }
        };
    }
}
