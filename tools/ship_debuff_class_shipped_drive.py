"""The class filter, judged on the shipped judge's own shape (round 15 of the goal).

Copied verbatim from `ControlImmunityTest` (a green shipped judge that applies a control BY RULE):
    TriggerSpecs.set(e, "op", "APPLY_CONTROL"); set(e, "control", FREEZE); set(e, "turns", 3); set(e, "target", "other_allies");
Its comment records the trap that cost this session three rounds: with `all_enemies` the control goes to the MONSTER, which is not
what a judge about the rule owner wants to read. My probes aimed at the monster and read 0 -- the roll, the resistance, or the aim.

Readings are TRUE deltas across startBattle (an earlier attempt compared get() with baseValue() and read a constant as a gain), and
they are two-way: the rule asking for 控制类 must move ATTACK, the one asking for 持续伤害类 must leave DEFENCE alone.
"""
import io
import subprocess
import sys

out = subprocess.run([sys.executable, "tools/ship_debuff_class.py"], capture_output=True, text=True)
print(out.stdout.strip()[-160:])

TABLE = "src/main/java/com/laosun/aluminium/models/TriggerTable.java"
t = io.open(TABLE, encoding="utf-8").read()
t = t.replace("com.laosun.aluminium.enums.DebuffClass.from(wanted)",
              "com.laosun.aluminium.enums.DebuffClass.fromString(wanted)", 1)
io.open(TABLE, "w", encoding="utf-8", newline="\n").write(t)
print("ok   fromString")

JUDGE = "src/test/java/com/laosun/aluminium/test/DebuffClassConditionTest.java"
io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 「敌方对我方施加了**控制类**负面状态」可以被问了 (2026-10-02).
 *
 * <p>One landed control, two watchers; both readings are deltas across startBattle. The landing is copied from the green
 * `ControlImmunityTest` — including its measured warning that `all_enemies` aims at the MONSTER — so that a failure here is about the
 * filter and not about the scene.
 */
public class DebuffClassConditionTest {
    private static final int OWNER = 1002;
    private static final int MONSTER = 1002011;
    private static final String FREEZE = "\\u51bb\\u7ed3";
    private static final double EPS = 1e-9;

    /** The control rule fires; the dot rule does not. */
    @Test
    public void theFilterTellsTheTwoFamiliesApart() {
        double[] gains = run();
        System.out.println("[debuff-class] control=" + gains[0] + " dot=" + gains[1]);
        Assertions.assertTrue(gains[0] > EPS,
                "「控制类」-- the rule that asked for it fired, which also proves the event happened");
        Assertions.assertEquals(0.0, gains[1], EPS,
                "「持续伤害类」-- the same event does NOT fire the rule that asked for the other family");
    }

    /** { the control-watch's ATTACK gain, the dot-watch's DEFENCE gain }. */
    private static double[] run() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);

        EffectSpec land = new EffectSpec();
        TriggerSpecs.set(land, "op", "APPLY_CONTROL");
        TriggerSpecs.set(land, "control", FREEZE);
        TriggerSpecs.set(land, "turns", 3);
        // ⚠ Verbatim from the green judge: from the applier's side the "enemies" are the monster, and a judge about the rule owner
        // wants the control ON the owner's own camp.
        TriggerSpecs.set(land, "target", "other_allies");

        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), land),
                TriggerSpecs.rule("DEBUFF_APPLIED", List.of("debuff_class:control"), boost("ATTACK")),
                TriggerSpecs.rule("DEBUFF_APPLIED", List.of("debuff_class:dot"), boost("DEFENCE"))),
                List.of()));

        Character other = CharacterFactory.create(1003, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner, other),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        double attackBefore = owner.getAttribute(AttributeType.ATTACK).get();
        double defenceBefore = owner.getAttribute(AttributeType.DEFENCE).get();
        battle.startBattle();
        battle.processRequests();
        return new double[] {
                owner.getAttribute(AttributeType.ATTACK).get() - attackBefore,
                owner.getAttribute(AttributeType.DEFENCE).get() - defenceBefore};
    }

    /** A rule effect that leaves a mark on a panel, so the reading needs no declared resource. */
    private static EffectSpec boost(String attribute) {
        EffectSpec spec = new EffectSpec();
        TriggerSpecs.set(spec, "op", "MODIFY_ATTR");
        TriggerSpecs.set(spec, "attribute", attribute);
        TriggerSpecs.set(spec, "percent", 0.5);
        TriggerSpecs.set(spec, "permanent", Boolean.TRUE);
        TriggerSpecs.set(spec, "target", "self");
        return spec;
    }
}
''')
print("ok   the judge copies the shipped drive")
