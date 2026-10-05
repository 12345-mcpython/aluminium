package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.DamageElement;
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
 * <b>Per-target conditions</b>: an effect's {@code target_when}.
 *
 * <p><b>The sentences that needed it.</b> "deals additional damage to all enemies <b>in the shocked state</b>" (1103's talent), "makes enemies <b>not in the shocked state</b>
 * fall into shock" (her Eidolon (星魂) 4) and "attaches continuous healing to our targets <b>whose HP percentage is <= 30%</b>" (1105's Eidolon (星魂) 2). A rule's own conditions
 * filter the <b>rule</b>, so "all shocked enemies" can only be spelled as "the enemy I hit was shocked" - 
 * which then also hit the unshocked ones. The selector says which units; this filter says which of them qualify.
 *
 * <p><b>What is pinned here.</b> That the filter is applied <b>per candidate</b> (the subject really is the candidate,
 * not the event's target), in both polarities, on a numeric condition as well as a state one, and that a misspelled
 * condition inside a filter is refused at load.
 */
public class TargetFilterTest {
    private static final int CID = 1103;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** The positive form: only the candidate carrying the state is reached. */
    @Test
    public void onlyTheQualifyingCandidateIsReached() {
        Fixture f = new Fixture("target has_state 触电");
        f.markSecond();

        f.fire();

        Assertions.assertFalse(f.first.getBuffManager().hasState("印记"),
                "the unmarked enemy was not reached: 「对所有触电状态下的敌方目标」");
        Assertions.assertTrue(f.second.getBuffManager().hasState("印记"), "…and the marked one was");
    }

    /** The negated form: the filter excludes exactly the ones that carry it. */
    @Test
    public void theNegatedFormExcludesTheQualifyingOne() {
        Fixture f = new Fixture("!target has_state 触电");
        f.markSecond();

        f.fire();

        Assertions.assertTrue(f.first.getBuffManager().hasState("印记"), "the unmarked enemy was reached");
        Assertions.assertFalse(f.second.getBuffManager().hasState("印记"), "…and the marked one was excluded");
    }

    /** A numeric filter reads the candidate's own panel ({@code target_hp_percent} of the candidate). */
    @Test
    public void aNumericFilterReadsTheCandidate() {
        Fixture f = new Fixture("target_hp_percent <= 0.5");
        f.first.takeDamage(f.first.getMaxHp() * 0.8);      // below the line; the second stays at full HP

        f.fire();

        Assertions.assertTrue(f.first.getBuffManager().hasState("印记"), "the hurt candidate qualified");
        Assertions.assertFalse(f.second.getBuffManager().hasState("印记"),
                "…and the healthy one did not: the condition tested the CANDIDATE, not the event's target");
    }

    /** Note: A misspelled condition inside a filter is refused when the file loads, not ignored. */
    /** Note: A misspelled condition inside a filter is refused when the file loads, not ignored. */
    @Test
    public void aBadFilterIsRefusedAtLoad() {
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(CID, List.of(rule(List.of("target has_state")))));
        Assertions.assertTrue(refused.getMessage().contains("has_state"), refused.getMessage());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static final class Fixture {
        private final Character hero = CharacterFactory.create(CID, LEVEL);
        private final Enemy first = EnemyFactory.create(MONSTER, 90, 1);
        private final Enemy second = EnemyFactory.create(MONSTER, 91, 1);
        private final Battle battle;
        private final TriggerEvent event = TriggerEvent.KILL;

        private double firstHpBefore;
        private double secondHpBefore;
        private final List<String> filter;

        private Fixture(String filter) {
            this.filter = List.of(filter);
            hero.setTriggerTable(new TriggerTable(CID, List.of(rule(this.filter))));
            battle = new Battle(List.of(hero), List.of(first, second), fixed());
            battle.startBattle();
        }

        /** Puts shock (触电) on the second enemy through the engine's own DOT path. */
        private void markSecond() {
            EffectSpec dot = new EffectSpec();
            TriggerSpecs.set(dot, "op", "APPLY_DOT");
            TriggerSpecs.set(dot, "element", "Thunder");
            TriggerSpecs.set(dot, "amount", 1.0);
            TriggerSpecs.set(dot, "turns", 3);
            TriggerSpecs.set(dot, "baseChance", 1.0);
            TriggerSpecs.set(dot, "target", "target");
            hero.setTriggerTable(new TriggerTable(CID, List.of(TriggerSpecs.rule("SKILL_CAST", null, dot))));
            battle.fireTriggers(TriggerEvent.SKILL_CAST, hero, second, 1, 0);
            Assertions.assertTrue(second.getBuffManager().hasState("触电"), "precondition: the second enemy is shocked");
            Assertions.assertFalse(first.getBuffManager().hasState("触电"), "precondition: the first is not");
            hero.setTriggerTable(new TriggerTable(CID, List.of(rule(filter))));
        }

        private void fire() {
            firstHpBefore = first.getCurrentHp();
            secondHpBefore = second.getCurrentHp();
            // the event's own subject is the FIRST enemy, so a filter that tested the event's target instead of the
            // candidate would hit the first and spare the second -- the opposite of what the sentence says
            battle.fireTriggers(event, hero, first, 1, 0);
        }
    }

    /**
     * One rule that marks <b>every</b> enemy, filtered per candidate.
     *
     * <p>Note: A marker state rather than damage: the filter is what is under test, and {@code APPLY_BUFF} observes it in one
     * line (`hasState`) without dragging in the DAMAGE op's own requirements (a skill slot and a parameter index).
     */
    private static TriggerSpec rule(List<String> filter) {
        EffectSpec mark = new EffectSpec();
        TriggerSpecs.set(mark, "op", "APPLY_BUFF");
        TriggerSpecs.set(mark, "buff", "印记");
        TriggerSpecs.set(mark, "permanent", true);
        TriggerSpecs.set(mark, "target", "all_enemies");
        TriggerSpecs.set(mark, "targetWhen", filter);
        return TriggerSpecs.rule(TriggerEvent.KILL.value(), null, mark);
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
