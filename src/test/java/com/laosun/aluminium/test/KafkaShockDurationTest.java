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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1005's shock and the trace that lengthens it by one turn.
 *
 * <p>Note: The monster is chosen to SURVIVE the whole window, and that is the instrument's real requirement: with a 3-HP
 * monster two DOTs kill it on turn three whatever their durations are, so a real one-turn extension cannot move the count --
 * which is what made this clause look unverifiable for several rounds. The highest-HP monster in the probe range has ~990k.
 *
 * <p>A whole turn is `beforeMove()` plus `afterMove()` (the countdown is split into an early and a late pass), and the count
 * is the number of the victim's turns that cost it HP, which for two DOTs of different lengths is the LONGER one.
 */
public class KafkaShockDurationTest {
    private static final int KAFKA = 1005;
    private static final int ALLY = 1210;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002064;
    private static final int HER_DOT_TURNS = 2;
    private static final int HORIZON = 10;

    @Test
    public void anExtendedDotBurnsForOneMoreTurn() {
        Assertions.assertEquals(HER_DOT_TURNS, syntheticBurningTurns(HER_DOT_TURNS, 0),
                "a plain synthetic DOT burns for its own length");
        Assertions.assertEquals(HER_DOT_TURNS + 1, syntheticBurningTurns(HER_DOT_TURNS, 1),
                "and one turn of extension adds exactly one");
    }

    @Test
    public void herOwnUltimateBurnsOneLongerThanAnUnlengthenedDot() {
        Assertions.assertEquals(syntheticBurningTurns(HER_DOT_TURNS, 1), herBurningTurns(),
                "触电状态的持续时间增加1回合 -- two turns plus the trace's one");
    }

    private static int herBurningTurns() {
        Character kafka = CharacterFactory.create(KAFKA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(kafka, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.castImmediate(kafka.getSkills().get(SkillType.ULTRA), kafka, List.of(enemy));
        return burningTurns(battle, enemy);
    }

    private static int syntheticBurningTurns(int dotTurns, int extension) {
        Character applier = CharacterFactory.create(ALLY, LEVEL);
        EffectSpec dot = new EffectSpec();
        TriggerSpecs.set(dot, "op", "APPLY_DOT");
        TriggerSpecs.set(dot, "element", "Thunder");
        TriggerSpecs.set(dot, "amount", 100.0);
        TriggerSpecs.set(dot, "turns", dotTurns);
        TriggerSpecs.set(dot, "target", "target");
        List<EffectSpec> effects = new ArrayList<>();
        effects.add(dot);
        if (extension > 0) {
            effects.add(extension(extension));
        }
        applier.setTriggerTable(new TriggerTable(9105, List.of(TriggerSpecs.rule(
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

    private static EffectSpec extension(int turns) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "EXTEND_BUFF");
        TriggerSpecs.set(effect, "buff", "触电");
        TriggerSpecs.set(effect, "turns", turns);
        TriggerSpecs.set(effect, "target", "target");
        return effect;
    }
}
