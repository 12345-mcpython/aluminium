package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.data.TriggerTables;
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
 * 1005's 「触电状态的持续时间增加1回合」: the mechanism, and her file's own rule.
 *
 * <p>The mechanism is measured on a DOT the test applies itself -- identical in both runs except for the `EXTEND_BUFF` -- so
 * "one more turn" is a difference between two runs of the same content. A DOT settles when the victim's turn begins, and a
 * whole turn is `beforeMove()` plus `afterMove()` (the countdown is split into an early and a late pass), so counting the
 * turns that cost HP is the instrument.
 */
public class KafkaShockDurationTest {
    private static final int KAFKA = 1005;
    private static final int ALLY = 1210;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void anExtendedDotBurnsForOneMoreTurn() {
        int plain = burningTurns(false);
        int extended = burningTurns(true);
        Assertions.assertEquals(plain + 1, extended,
                "the extension adds exactly one turn: " + plain + " -> " + extended);
        Assertions.assertTrue(plain >= 2, "precondition: the DOT burns for more than one turn anyway: " + plain);
    }

    @Test
    public void herFileCarriesTheExtension() {
        Assertions.assertEquals(1, TriggerTables.of(KAFKA).ruleCount(TriggerEvent.ULT_CAST),
                "her file has one Ultimate rule for the shock (and the extension lives on the same event)");
        Assertions.assertTrue(TriggerTables.of(KAFKA).ruleCount(TriggerEvent.ULT_CAST) >= 1,
                "census: the extension rule is filed on ULT_CAST, where the shock is applied");
    }

    private static final int DOT_LENGTH = 2;

    /** Applies a 2-turn DOT with a synthetic rule, optionally extended by one, and counts the turns that cost HP. */
    private static int burningTurns(boolean extended) {
        Character applier = CharacterFactory.create(ALLY, LEVEL);
        EffectSpec dot = new EffectSpec();
        TriggerSpecs.set(dot, "op", "APPLY_DOT");
        TriggerSpecs.set(dot, "element", "Thunder");
        TriggerSpecs.set(dot, "amount", 100.0);
        TriggerSpecs.set(dot, "turns", DOT_LENGTH);
        TriggerSpecs.set(dot, "target", "target");
        List<EffectSpec> effects = extended
                ? List.of(dot, extension())
                : List.of(dot);
        applier.setTriggerTable(new TriggerTable(9101, List.of(
                TriggerSpecs.rule(TriggerEvent.ALLY_ATTACK.name(), List.of(), effects.toArray(new EffectSpec[0])))));

        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(applier), List.of(enemy), new Random(0));
        battle.startBattle();
        // ⚠ ALLY_ATTACK rather than BATTLE_START: the start of a battle has no aimed party, so `target: target` is refused
        // there (measured: "this event has no such party"). Firing the attack applies the DOT and deals no damage, because
        // the synthetic rule carries no DAMAGE effect.
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, applier, enemy, 1, 0);

        int burning = 0;
        for (int turn = 0; turn < DOT_LENGTH + 4 && !enemy.isDeath(); turn++) {
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

    /**
     * Her own rule, judged: her Ultimate's shock must burn one turn longer than an identical synthetic shock.
     *
     * <p>⚠ Why this case exists: the mechanism case above proves the op but never runs her rule, so a wrong number in her
     * file would pass unnoticed -- only a census covered it. Both runs use the same monster and the same DOT LENGTH, so the
     * difference is exactly the turn her trace adds, and mutating it changes this number.
     */
    @Test
    public void herOwnShockBurnsOneLongerThanAnIdenticalPlainOne() {
        int synthetic = burningTurns(true) - 1;          // burningTurns(true) is the extended synthetic one
        int hers = herShockTurns();
        Assertions.assertEquals(synthetic + 1, hers,
                "her trace adds exactly one turn to her own shock: synthetic " + synthetic + " -> hers " + hers);
    }

    /** Casts her Ultimate and counts the enemy's turns that cost it HP. */
    private static int herShockTurns() {
        Character kafka = CharacterFactory.create(KAFKA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(kafka, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.castImmediate(kafka.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA), kafka, List.of(enemy));
        return burningTurnsOf(battle, enemy);
    }

    private static int burningTurnsOf(Battle battle, Enemy enemy) {
        int burning = 0;
        for (int turn = 0; turn < DOT_LENGTH + 4 && !enemy.isDeath(); turn++) {
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
}
