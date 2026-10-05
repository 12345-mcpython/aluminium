package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * An {@code EXTRA_TURN} that cannot be granted must SAY SO (2026-10-02).
 *
 * <p>Found while writing 1415's ode of genesis: `Queue.grantExtraTurn` returns false for a unit that is not in the action order, and a memosprite
 * at Speed 0 is skipped by `Queue.addCombatant` (a fix shipped earlier this session). The op dropped that answer, so a rule granting 德谬歌 an
 * extra turn looked like it had worked -- the judge read `extra turn actor = none` and nothing anywhere said why.
 *
 * <p>⭐ Two cases on purpose: the refusal must be loud, AND an ordinary character that CAN take an extra turn must still get one. A guard that
 * fires on everything is no better than one that fires on nothing.
 */
public class ExtraTurnLoudnessTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;

    private static EffectSpec extraTurn(String target) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "EXTRA_TURN");
        TriggerSpecs.set(effect, "target", target);
        return effect;
    }

    /** A memosprite at Speed 0 is not in the action order, so the grant is refused -- loudly. */
    @Test
    public void aMemospriteOutsideTheActionOrderIsRefusedLoudly() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(cyrene, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        battle.summonServant(cyrene);
        battle.processRequests();

        cyrene.setTriggerTable(new TriggerTable(CYRENE, List.of(
                TriggerSpecs.rule("TURN_START", List.of(), extraTurn("summon")))));

        Throwable thrown = Assertions.assertThrows(RuntimeException.class,
                () -> battle.fireTriggers(TriggerEvent.TURN_START),
                "「德谬歌立即获得 1 个额外回合」-- and when it cannot be granted, say so");
        String message = String.valueOf(thrown.getMessage());
        System.out.println("[loud_turn] memosprite -> " + thrown.getClass().getSimpleName() + ": "
                + message.substring(0, Math.min(150, message.length())));
        Assertions.assertTrue(message.contains("action order"), "the message must name the reason");
    }

    /** A character who IS in the action order still gets one -- the guard is not a blanket refusal. */
    @Test
    public void anOrdinaryCharacterStillGetsOne() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(cyrene, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        ally.setTriggerTable(new TriggerTable(ALLY, List.of(
                TriggerSpecs.rule("TURN_START", List.of(), extraTurn("self")))));

        battle.fireTriggers(TriggerEvent.TURN_START);
        System.out.println("[loud_turn] a character in the queue -> extra turn actor = "
                + (battle.getExtraTurnActor() == null ? "none" : battle.getExtraTurnActor().getName()));
        Assertions.assertSame(ally, battle.getExtraTurnActor(), "an ordinary character can still take an extra turn");
    }
}
