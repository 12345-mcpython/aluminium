package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * {@code SHIELD_GRANTED} - "a shield was granted" as an event (M-43), and 大丽花's trace that needs it.
 *
 * <p><b>Why the engine needed it.</b> Shields were only an <i>op</i> ({@code SHIELD}): the engine could make one but
 * nothing was announced, so "受到队友提供的治疗效果<b>或护盾</b>时" (大丽花's trace, 1321101) could be written only
 * as its healing half - an effect that is too weak exactly when a shield arrives, with nothing to report. The event
 * follows {@code HEALED}'s convention ({@code actor} = who provided it, {@code target} = who received it), so "队友
 * 提供的" needs no new vocabulary: {@code target == self} + {@code actor is_ally} + {@code actor != self}.
 *
 * <p><b>Two paths, one fact.</b> A shield reaches the field as a raw grant ({@code Battle.grantShield}) or through a
 * timed {@code ShieldBuff} (the interpreter's {@code SHIELD} arm, which has no {@code Battle} handle of its own). Both
 * are covered here, because a fix that only announced one of them would leave half the game's shields invisible.
 *
 * <p><b>Note: And the reading that shrank this round's plan.</b> "再次触发<b>该效果</b>" was checked against the trace's
 * own text before anything was built: 该效果 is the sentence above's effect ("使其他角色的击破特攻提高，提高数值等同于
 * 24% 大丽花的击破特攻 + 0.5"), so re-triggering it is that <b>same buff granted again with a longer duration</b> - 
 * which the existing {@code MODIFY_ATTR} already says. A planned {@code RETRIGGER_RULE} op would have had no reader.
 */
public class ShieldGrantedEventTest {
    private static final double EPS = 1e-6;

    private static final int DAHLIA = 1321;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double HEAL = 100;

    /** An ally's shield on her fires it (the raw grant path). */
    @Test
    public void anAlliesShieldFiresIt() {
        Fixture f = new Fixture();
        f.hurt();

        f.battle.grantShield(f.ally, f.dahlia, 500);

        Assertions.assertEquals(HEAL, f.healed(), EPS, "「受到队友提供的…护盾时」");
    }

    /** Note: The timed path goes through a buff, not through {@code grantShield}: it has to be announced separately. */
    @Test
    public void aTimedShieldFiresItToo() {
        Fixture f = new Fixture();
        f.ally.setTriggerTable(new TriggerTable(ALLY, List.of(timedShieldRule())));
        f.hurt();

        f.battle.fireTriggers(TriggerEvent.SKILL_CAST, f.ally, f.dahlia, 0, 0);

        Assertions.assertTrue(f.dahlia.getShield() > 0, "precondition: the timed shield is on her");
        Assertions.assertEquals(HEAL, f.healed(), EPS,
                "a shield with `turns` is installed by a ShieldBuff, which has no Battle handle -- the interpreter "
                        + "announces it, and a rule that answered only the raw path would miss every timed shield");
    }

    /** Note: "队友提供的": a shield she gives herself is not one. */
    @Test
    public void aShieldSheGaveHerselfDoesNotFire() {
        Fixture f = new Fixture();
        f.hurt();

        f.battle.grantShield(f.dahlia, f.dahlia, 500);

        Assertions.assertEquals(0, f.healed(), EPS,
                "`actor is_ally` alone would accept this (she IS on our side) -- `actor != self` is the other half");
    }

    /** Note: A raw grant names no provider, so a question about who provided it must answer no. */
    @Test
    public void aShieldNobodyIsCreditedWithDoesNotFire() {
        Fixture f = new Fixture();
        f.hurt();

        f.battle.grantShield(f.dahlia, 500);

        Assertions.assertEquals(0, f.healed(), EPS, "no provider stated = no one to be a teammate");
    }

    /** Note: A grant of 0 is how this API says "clear the shield": it is not a grant. */
    @Test
    public void clearingAShieldIsNotGrantingOne() {
        Fixture f = new Fixture();
        f.hurt();

        f.battle.grantShield(f.ally, f.dahlia, 0);

        Assertions.assertEquals(0, f.healed(), EPS,
                "the same convention as HEALED, which only fires when HP was really restored");
    }

    /** "单个回合内不可重复触发" is `per_turn: 1` (the engine counts the owner's turns). */
    @Test
    public void itFiresOncePerTurn() {
        Fixture f = new Fixture();
        f.hurt();

        f.battle.grantShield(f.ally, f.dahlia, 500);
        f.battle.grantShield(f.ally, f.dahlia, 500);

        Assertions.assertEquals(HEAL, f.healed(), EPS,
                "two shields in the same turn, one trigger -- without the limit this would read " + (2 * HEAL));
    }

    /** The healing half is the same rule shape: a teammate's heal (and NOT her own). */
    @Test
    public void aTeammatesHealFiresTheHealingHalf() {
        Fixture f = new Fixture();
        f.hurt();
        double baseline = f.dahlia.getCurrentHp();

        // Note: The heal under test IS the heal the rule hangs on, so the direct amount lands in the same delta: a
        // self-heal of 100 moves her HP by 100 and must NOT add the rule's 100; a teammate's moves it by 200.
        f.battle.heal(f.dahlia, f.dahlia, 100);
        Assertions.assertEquals(100, f.dahlia.getCurrentHp() - baseline, EPS,
                "a heal she gave herself is not 「队友提供的」 (actor != self)");

        baseline = f.dahlia.getCurrentHp();
        f.battle.heal(f.ally, f.dahlia, 100);
        Assertions.assertEquals(200, f.dahlia.getCurrentHp() - baseline, EPS,
                "…and a teammate's heal is: the 100 healed plus the rule's 100");
    }

    /**
     * The shipped trace states both halves, each as the trace's own effect with the trace's own durations.
     *
     * <p>Note: Structural pin on purpose: her file's numbers are the document's ({@code [0.24, 1, 0.5, 3]} to 24% of her own
     * Break Effect + 0.5, for 1 turn at battle start and for <b>3</b> turns when it is re-triggered).
     */
    @Test
    public void herFileStatesBothHalves() {
        Character dahlia = CharacterFactory.create(DAHLIA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        // Note: A real battle is required: `actor is_ally` asks the field who is on our side, so a context without one
        // cannot answer it (measured: with battle = null `matching` returned 0 rules and the assertion below was
        // the thing that caught it).
        Battle battle = new Battle(List.of(dahlia, ally), List.of(enemy()), fixed());
        TriggerTable table = TriggerTables.of(DAHLIA);

        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.HEALED),
                "大丽花's trace needs BOTH halves: 「治疗效果**或**护盾」 -- one alone is a trace that stays silent "
                        + "half the time");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.SHIELD_GRANTED), "…the shield half is the other one");

        for (TriggerEvent event : List.of(TriggerEvent.HEALED, TriggerEvent.SHIELD_GRANTED)) {
            // A context where the conditions hold (a teammate acting on her), so `matching` keeps the rule.
            TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(dahlia, ally, dahlia, 0, 0, null, battle,
                    com.laosun.aluminium.enums.SkillCategory.UNSPECIFIED);
            List<TriggerTable.CompiledRule> rules = table.matching(event, ctx);
            Assertions.assertEquals(1, rules.size(), event + ": the trace's rule must match 「大丽花受到队友提供的…」");
            TriggerTable.CompiledRule rule = rules.getFirst();
            Assertions.assertEquals(List.of("target == self", "actor is_ally", "actor != self"),
                    rule.conditions().stream().map(TriggerTable.Condition::source).toList(),
                    event + ": all three conditions are load-bearing");
            Assertions.assertEquals(1, rule.perTurn(), event + ": 「单个回合内不可重复触发」");
            EffectSpec effect = rule.effects().getFirst();
            Assertions.assertEquals("MODIFY_ATTR", effect.getOp());
            Assertions.assertEquals("BREAKING_EFFECT", effect.getAttribute());
            Assertions.assertEquals("self_attr:BREAKING_EFFECT", effect.getScale());
            Assertions.assertEquals(0.24, effect.getPercent(), EPS);
            Assertions.assertEquals(0.5, effect.getAmount(), EPS);
            Assertions.assertEquals(3, effect.getTurns(), event + ": 「持续#4[i]回合」 = 3");
        }
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** 大丽花 with a hand-built copy of her trace's two rules, one ally to shield her, and one enemy. */
    private static final class Fixture {
        private final Character dahlia = CharacterFactory.create(DAHLIA, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Battle battle;

        private Fixture() {
            dahlia.setTriggerTable(new TriggerTable(DAHLIA, List.of(
                    trigger(TriggerEvent.HEALED), trigger(TriggerEvent.SHIELD_GRANTED))));
            ally.setTriggerTable(new TriggerTable(ALLY, List.of()));
            battle = new Battle(List.of(dahlia, ally), List.of(enemy()), fixed());
            battle.startBattle();
        }

        /** Leaves her damaged, so a 100-point heal is visible. The baseline is captured here, not in a field init. */
        private void hurt() {
            dahlia.takeDamage(dahlia.getMaxHp() * 0.5);
            hpAfterHurt = dahlia.getCurrentHp();
        }

        /** How much the rule healed her (0 = it never fired). */
        private double healed() {
            return dahlia.getCurrentHp() - hpAfterHurt;
        }

        private double hpAfterHurt;
    }

    // A hand-written fixture cannot use a field initialiser that runs after `hurt`, so the baseline is captured there.
    private static TriggerSpec trigger(TriggerEvent event) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "HEAL");
        TriggerSpecs.set(effect, "amount", HEAL);
        TriggerSpecs.set(effect, "target", "self");
        TriggerSpec rule = TriggerSpecs.rule(event.value(),
                List.of("target == self", "actor is_ally", "actor != self"), effect);
        TriggerSpecs.set(rule, "perTurn", 1);
        return rule;
    }

    /** A rule that gives the aimed ally a shield that lasts: the interpreter's timed `SHIELD` arm. */
    private static TriggerSpec timedShieldRule() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "SHIELD");
        TriggerSpecs.set(effect, "amount", 500.0);
        TriggerSpecs.set(effect, "turns", 3);
        TriggerSpecs.set(effect, "target", "target");
        return TriggerSpecs.rule("SKILL_CAST", List.of("actor == self"), effect);
    }

    private static Enemy enemy() {
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        enemy.setAttribute(AttributeType.EFFECT_RESISTANCE, new com.laosun.aluminium.models.DoubleValue(0));
        return enemy;
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
