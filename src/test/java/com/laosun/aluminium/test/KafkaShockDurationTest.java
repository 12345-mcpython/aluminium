package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.SkillType;
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
 * 1005's shock, and the trace that lengthens it by one turn.
 *
 * <p>Two cases, because they answer different questions: the synthetic one proves the OP (identical runs, one extension), and
 * the second runs HER rule -- her Ultimate against a synthetic DOT of the same length -- so a wrong number in her file is
 * caught rather than merely counted.
 *
 * <p>⚠ Instruments that matter here: a whole turn is `beforeMove()` plus `afterMove()` (the countdown is split into an early
 * and a late pass), and the horizon is wide (HER_DOT_TURNS + 6) because round 34's six-turn window saturated, making a real
 * extension invisible and the rule look like a no-op.
 */
public class KafkaShockDurationTest {
    private static final int KAFKA = 1005;
    private static final int ALLY = 1210;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int HER_DOT_TURNS = 2;
    private static final int HORIZON = HER_DOT_TURNS + 6;

    @Test
    public void anExtendedDotBurnsForOneMoreTurn() {
        int plain = syntheticBurningTurns(HER_DOT_TURNS, false);
        int extended = syntheticBurningTurns(HER_DOT_TURNS, true);
        Assertions.assertEquals(plain + 1, extended,
                "the extension adds exactly one turn: " + plain + " -> " + extended);
    }

    @Test
    public void herOwnUltimateBurnsOneLongerThanAnIdenticalDot() {
        int control = syntheticBurningTurns(HER_DOT_TURNS, false);
        int hers = herBurningTurns();
        Assertions.assertEquals(control + 1, hers,
                "her trace lengthens her own shock by one turn: control " + control + " -> hers " + hers);
    }

    /** Her Ultimate, then the enemy's whole turns, counting those that cost it HP. */
    private static int herBurningTurns() {
        Character kafka = CharacterFactory.create(KAFKA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(kafka, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.castImmediate(kafka.getSkills().get(SkillType.ULTRA), kafka, List.of(enemy));
        return burningTurns(battle, enemy);
    }

    /** A synthetic DOT of the given length, optionally extended by one, then the same count. */
    private static int syntheticBurningTurns(int dotTurns, boolean extended) {
        Character applier = CharacterFactory.create(ALLY, LEVEL);
        EffectSpec dot = new EffectSpec();
        TriggerSpecs.set(dot, "op", "APPLY_DOT");
        TriggerSpecs.set(dot, "element", "Thunder");
        TriggerSpecs.set(dot, "amount", 100.0);
        TriggerSpecs.set(dot, "turns", dotTurns);
        TriggerSpecs.set(dot, "target", "target");
        List<EffectSpec> effects = extended ? List.of(dot, extension()) : List.of(dot);
        applier.setTriggerTable(new TriggerTable(9101, List.of(TriggerSpecs.rule(
                TriggerEvent.ALLY_ATTACK.name(), List.of(), effects.toArray(new EffectSpec[0])))));

        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(applier), List.of(enemy), new Random(0));
        battle.startBattle();
        // ALLY_ATTACK, not BATTLE_START: a battle start has no aimed party, so `target: target` is refused there.
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, applier, enemy, 1, 0);
        return burningTurns(battle, enemy);
    }

    private static int burningTurns(Battle battle, Enemy enemy) {
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
