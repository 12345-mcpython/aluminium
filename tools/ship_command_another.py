"""Commanding ANOTHER unit to cast -- judged, and the register corrected (round 14 of the goal).

The §3 row for 1415's 「使其自动施放1次不消耗充能的【弑神登神】」 says the prerequisite is "a way to command another unit to cast".
Measured: that already ships. `castSkill` resolves its caster as `resolveTarget(effect, ctx)` (NOT the rule owner), and 1412's shipped
`coup_de_main` writes exactly `CAST_SKILL{skill: SKILL, target: "attacker"}`. `holder_of:<state>` also exists, which is what a sentence
like 「对万敌施放时…」 needs to name its caster.

The reading is ATTRIBUTABLE: the commanded unit carries its own rule that marks a resource when it casts, so "who actually cast" is
observable rather than inferred from damage.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/CastSkillCommandsAnotherUnitTest.java"
MUTATOR = "tools/mut_command_another.py"

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.ResourceSpec;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 「使万敌自动施放【弑神登神】」所需的“命令**另一个单位**施放” (2026-10-02).
 *
 * <p>The point is attribution: the commanded unit carries its OWN rule that marks a resource when it casts, so the reading says who
 * cast rather than inferring it from damage. The commander names it with `holder_of:<state>` -- the selector a sentence like
 * 「对万敌施放时」 needs -- and a mutation that points `target` at `self` moves the mark to the commander.
 */
public class CastSkillCommandsAnotherUnitTest {
    private static final int COMMANDER = 1002;
    private static final int COMMANDED = 1003;
    private static final int MONSTER = 1002011;
    private static final String MARK = "probeCastBy";

    /** The mark lands on the COMMANDED unit, not on the commander. */
    @Test
    public void theOtherUnitsCastIsTheOneThatHappens() {
        int[] marks = run();
        System.out.println("[command] mark on commander=" + marks[0] + " commanded=" + marks[1]);
        Assertions.assertEquals(0, marks[0], "命令**他人**施放时，发话的那一位自己并没有施放");
        Assertions.assertEquals(1, marks[1], "被命令的那一位施放了一次");
    }

    /** { mark on the commander, mark on the commanded }. */
    private static int[] run() {
        Character commander = CharacterFactory.create(COMMANDER, 80, false, null, null, 0);
        Character commanded = CharacterFactory.create(COMMANDED, 80, false, null, null, 0);

        // the commander: on its own turn start, command the holder of the marker to cast
        EffectSpec command = new EffectSpec();
        TriggerSpecs.set(command, "op", "CAST_SKILL");
        TriggerSpecs.set(command, "skill", "COMMON");
        TriggerSpecs.set(command, "target", "holder_of:probeMark");
        commander.setTriggerTable(new TriggerTable(COMMANDER, List.of(
                TriggerSpecs.rule("TURN_START", List.of("actor == self"), command)), List.of()));

        // the commanded: its own rule marks the resource whenever IT casts
        EffectSpec mark = new EffectSpec();
        TriggerSpecs.set(mark, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(mark, "resource", MARK);
        TriggerSpecs.set(mark, "amount", 1.0);
        TriggerSpecs.set(mark, "target", "self");
        commanded.setTriggerTable(new TriggerTable(COMMANDED, List.of(
                TriggerSpecs.rule("BASIC_ATTACK", List.of("actor == self"), mark)), List.of()));

        Battle battle = new Battle(List.of(commander, commanded),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        commanded.getBuffManager().addBuff(new StateBuff("probeMark", 99, true));
        battle.processRequests();

        Signal signal = battle.queue.snapshot().stream()
                .filter(candidate -> candidate.getCanHit() == commander).findFirst().orElseThrow();
        battle.currentMove = signal;
        battle.beforeMove();
        battle.afterMove();
        battle.processRequests();

        return new int[] {value(commander), value(commanded)};
    }

    private static int value(Character unit) {
        return unit.getResources().has(MARK) ? unit.getResources().value(MARK) : 0;
    }
}
''')
print("ok   the attribution judge is written")

io.open(MUTATOR, "w", encoding="utf-8", newline="\n").write('''"""Mutant: the commanded cast points at the commander itself (round 14)."""
import io
import sys

PATH = "src/test/java/com/laosun/aluminium/test/CastSkillCommandsAnotherUnitTest.java"
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
if mode not in ("on", "off"):
    sys.exit("usage: mut_command_another.py on|off")

LIVE = 'TriggerSpecs.set(command, "target", "holder_of:probeMark");'
MUTATED = 'TriggerSpecs.set(command, "target", "self");'
text = io.open(PATH, encoding="utf-8").read()
if mode == "off":
    if text.count(LIVE) != 1:
        sys.exit("REFUSING: %d occurrences" % text.count(LIVE))
    io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(LIVE, MUTATED, 1))
    print("MUTATION: the cast is aimed at the commander")
else:
    if text.count(MUTATED) != 1:
        sys.exit("REFUSING: the mutant is not in place")
    io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(MUTATED, LIVE, 1))
    print("restored: the cast names the marker holder")
''')
print("ok   the mutator is written")
