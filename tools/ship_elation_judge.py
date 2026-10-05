"""Judge for the ElationDamage row reading (item 57). Judge-only, per the one-script-one-kind discipline.

What it measures, and why this instrument: the sentence is about the NUMBER of instances
(「造成 #1 次伤害，每次对敌方随机单体造成 #2%…。最后造成 #3%…由敌方全体均分」), so the judge counts instances rather than
damage -- damage would drag in the crit zone and the Elation boost, and a count does not. The counter is a test-only rule on
the caster: `DEALING_DAMAGE` + `actor == self` -> `ADD_STACK` on a named counter, read back with `stacksOf`.

With ONE enemy on the field the expected count is exact: 8 random-single hits (the draw has only one candidate) plus the one
final split instance = 9. Before the branch, this row took the AOE path and read 8 as the multiplier, i.e. ONE instance.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/ElationRowTest.java"

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpecs;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 欢愉技的行首列是**次数**：「造成 **#1 次伤害，每次对敌方随机单体造成 #2%…。最后造成 #3%…
 * 由**敌方全体均分**」 (2026-10-02; readers 8009/8010 slot 20, data row `[8, 0.25, 0.75]` at L15).
 *
 * <p>⭐ THE INSTRUMENT COUNTS INSTANCES, not damage: the sentence is about a NUMBER of hits, and damage would drag in the crit
 * zone and the Elation boost. A test-only rule on the caster adds one counter stack per damage instance it deals.
 */
public class ElationRowTest {
    private static final int AVENTURINE = 8009;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String COUNTER = "\\u547d\\u4e2d\\u6b21\\u6570";

    /** ⭐ Eight hits plus the final split instance, with a single enemy that every random draw must pick. */
    @Test
    public void theRowSettlesEightHitsAndTheSplit() {
        Assertions.assertEquals(9, instancesFromTheElationRow(), 0,
                "8 \\u6b21\\u4f24\\u5bb3 + \\u6700\\u540e\\u4e00\\u6b21\\u5747\\u5206");
    }

    /** ⚠ And the same reading must NOT be a single 8x instance, which is what the AOE path did before the branch. */
    @Test
    public void itIsNotOneInstanceOfEightTimesTheShare() {
        Assertions.assertNotEquals(1, instancesFromTheElationRow(),
                "行首列是次数，不是倍率");
    }

    // ==================================================================

    private static int instancesFromTheElationRow() {
        Character him = CharacterFactory.create(AVENTURINE, LEVEL, false, null, null, 0);
        EffectSpec counter = new EffectSpec();
        TriggerSpecs.set(counter, "op", "ADD_STACK");
        TriggerSpecs.set(counter, "buff", COUNTER);
        TriggerSpecs.set(counter, "amount", 1);
        TriggerSpecs.set(counter, "max_stacks", 99);
        TriggerSpecs.set(counter, "permanent", true);
        TriggerSpecs.set(counter, "target", "self");
        him.setTriggerTable(new TriggerTable(AVENTURINE, List.of(
                TriggerSpecs.rule("DEALING_DAMAGE", List.of("actor == self"), counter))));

        Battle battle = new Battle(List.of(him), List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        battle.castImmediate(him.getSkills().get(SkillType.ELATION_SKILL), him,
                List.of(battle.enemies.getFirst()));
        int instances = him.getBuffManager().stacksOf(COUNTER);
        System.out.println("[" + AVENTURINE + "] damage instances settled by the Elation row: " + instances);
        return instances;
    }
}
''')
print("ok   judge written: it counts damage instances, not damage")
