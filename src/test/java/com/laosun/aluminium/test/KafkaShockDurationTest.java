package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * `EXTEND_BUFF` by a DOT's state name: the DOT must burn for one more turn.
 *
 * <p>The instrument is the victim's WHOLE turns -- `beforeMove()` plus `afterMove()`, because the countdown is split into an
 * early and a late pass -- and the two runs differ ONLY by the extension, so the extra burn is attributable to it. ⚠ The
 * horizon is generous on purpose: round 34's version looked six turns ahead while the DOTs last three and two, so a real
 * one-turn extension fell outside the window and the mutation appeared to do nothing.
 */
public class KafkaShockDurationTest {
    private static final int ALLY = 1210;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int DOT_TURNS = 2;
    private static final int HORIZON = 12;

    @Test
    public void anExtendedDotBurnsForOneMoreTurn() {
        int plain = burningTurns(false);
        int extended = burningTurns(true);
        Assertions.assertEquals(plain + 1, extended,
                "the extension adds exactly one turn: " + plain + " -> " + extended);
        Assertions.assertTrue(plain >= 2, "precondition: the DOT burns for more than one turn anyway: " + plain);
    }

    /** Applies a DOT with a synthetic rule, optionally extended by one, and counts the turns that cost HP. */
    private static int burningTurns(boolean extended) {
        Character applier = CharacterFactory.create(ALLY, LEVEL);
        EffectSpec dot = new EffectSpec();
        TriggerSpecs.set(dot, "op", "APPLY_DOT");
        TriggerSpecs.set(dot, "element", "Thunder");
        TriggerSpecs.set(dot, "amount", 100.0);
        TriggerSpecs.set(dot, "turns", DOT_TURNS);
        TriggerSpecs.set(dot, "target", "target");
        List<EffectSpec> effects = extended ? List.of(dot, extension()) : List.of(dot);
        applier.setTriggerTable(new TriggerTable(9101, List.of(TriggerSpecs.rule(
                TriggerEvent.ALLY_ATTACK.name(), List.of(), effects.toArray(new EffectSpec[0])))));

        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(applier), List.of(enemy), new Random(0));
        battle.startBattle();
        // ALLY_ATTACK, not BATTLE_START: the start of a battle has no aimed party, so `target: target` is refused there.
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, applier, enemy, 1, 0);

        int burning = 0;
        for (int turn = 0; turn < HORIZON && !enemy.isDeath(); turn++) {
            double before = enemy.getCurrentHp();
            battle.currentMove = new Signal(enemy);
            battle.beforeMove();
            battle.afterMove();
            if (enemy.getCurrentHp() < before) {
                burning++;
            }
        }
        return burning;
    }

    private static EffectSpec extension() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "EXTEND_BUFF");
        TriggerSpecs.set(effect, "buff", "\u89e6\u7535");
        TriggerSpecs.set(effect, "turns", 1);
        TriggerSpecs.set(effect, "target", "target");
        return effect;
    }
}
