package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.SkillType;
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
 * "while in the [协奏] state, Robin (知更鸟) is immune to control-class negative states" - the immunity, asserted BOTH ways.
 *
 * <p>The clause has shipped together with clause 3's party ATK boost, in one ULT_CAST rule that carries
 * the name concerto (协奏); this class pins what nothing else did: the same control that lands after the countdown cannot land while
 * the state lasts, because the immunity ends with the state rather than with a turn count. It also records that ULT_CAST
 * arrives at the tables exactly once per cast.
 *
 * <p>Note: <b>How this class was born.</b> A second copy of the clauses measured a party ATK gain of
 * 45.081848 where 0.228 x the pre-cast ATK + 200 is 386.844908 -- which reads like an engine bug. The duplicate
 * was the cause: the first rule computes 386.844908 (her ATK then reads 1206.499) and the second recomputes
 * 45.081848 from that boosted value, replacing the first because both carry the same name. The instrument was too
 * coarse to see it (the counter counts EVENTS, not rules), and the probe that would have shown the existing rule printed
 * into a report that was read truncated. See GAPS and HANDOFF's discipline 31.
 */
public class RobinConcertoTest {
    private static final int ROBIN = 1309;
    private static final int ALLY = 1210;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void herConcertoMakesHerControlImmuneUntilTheCountdown() {
        Character robin = CharacterFactory.create(ROBIN, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Character injector = Character.fromAttributes("injector", 10_000, 100, 100, 100);
        injector.setTriggerTable(new TriggerTable(9999, List.of(TriggerSpecs.rule(
                TriggerEvent.TURN_START.name(), List.of(), control()))));
        Character counter = Character.fromAttributes("counter", 10_000, 100, 100, 100);
        counter.setTriggerTable(new TriggerTable(9998, List.of(TriggerSpecs.rule(
                TriggerEvent.ULT_CAST.name(), List.of(), stack()))));
        Battle battle = new Battle(List.of(robin, ally, injector, counter), List.of(dummy()), fixed());

        battle.startBattle();
        battle.castImmediate(robin.getSkills().get(SkillType.ULTRA), robin, List.of(dummy()));

        Assertions.assertEquals(1, counter.getBuffManager().stacksOf("计数"),
                "ULT_CAST arrives at the tables once per cast (an event count -- it does NOT count the rules that match)");
        battle.fireTriggers(TriggerEvent.TURN_START, injector, robin, 0, 0);
        Assertions.assertFalse(robin.getBuffManager().hasState("冻结"),
                "「处于【协奏】状态时，知更鸟免疫控制类负面状态」");

        battle.fireTriggers(TriggerEvent.COUNTDOWN_TURN, battle.countdownsOf(robin).getFirst(), robin, 0, 0);

        battle.fireTriggers(TriggerEvent.TURN_START, injector, robin, 0, 0);
        Assertions.assertTrue(robin.getBuffManager().hasState("冻结"),
                "the immunity is named after the state, so the countdown takes it off -- the same control now lands");
    }

    /** A 100% base chance control aimed at the event's target (the immunity under test is on the target). */
    private static EffectSpec control() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "APPLY_CONTROL");
        TriggerSpecs.set(effect, "control", "冻结");
        TriggerSpecs.set(effect, "turns", 1);
        TriggerSpecs.set(effect, "baseChance", 1.0);
        TriggerSpecs.set(effect, "target", "target");
        return effect;
    }

    /** Counts how many times the event arrives. */
    private static EffectSpec stack() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "ADD_STACK");
        TriggerSpecs.set(effect, "buff", "计数");
        TriggerSpecs.set(effect, "maxStacks", 99);
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpecs.set(effect, "target", "self");
        return effect;
    }

    private static Enemy dummy() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }

    /** Rolls low enough that a control that is not resisted always lands. */
    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }
}
