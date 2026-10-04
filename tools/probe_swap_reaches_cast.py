"""Probe: does the swapped row actually reach the CAST? (round 5 of the goal)

Measured so far: the swap installs slot 9 into the map (`getSkillSlot() == 9`), and the turn-start rule pays the 35% cost -- but
the damage the enemy took (767.59) is IDENTICAL to the run before the swap existed, which smells like the cast still using the
original row 2. This compares, in the same scene, (a) the damage from the content's turn-start cast against (b) the damage from
casting slot 9 directly.

Probe only: deleted once it has answered.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/SwapReachesCastProbeTest.java"
io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** Probe only: which row does the content's cast actually execute? */
public class SwapReachesCastProbeTest {
    private static final int MONSTER = 1002011;
    private static final String BLOODFEUD = "\\u8840\\u4ec7";

    @Test
    public void compareTheTwoDamages() {
        // (a) the content path: gate + swap + cost + cast, driven by his own turn start
        Character him = CharacterFactory.create(1404, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        him.getBuffManager().addBuff(new StateBuff(BLOODFEUD, 9, true));
        battle.processRequests();
        double enemyBefore = battle.enemies.getFirst().getCurrentHp();
        Signal signal = battle.queue.snapshot().stream()
                .filter(candidate -> candidate.getCanHit() == him).findFirst().orElseThrow();
        battle.currentMove = signal;
        battle.beforeMove();
        battle.afterMove();
        battle.processRequests();
        double contentDamage = enemyBefore - battle.enemies.getFirst().getCurrentHp();

        // (b) row 9 cast directly, no content rule involved
        Character him2 = CharacterFactory.create(1404, 80, false, null, null, 0);
        EffectSpec swap = new EffectSpec();
        TriggerSpecs.set(swap, "op", "REPLACE_SKILL");
        TriggerSpecs.set(swap, "skill", "SKILL");
        TriggerSpecs.set(swap, "skillId", 9);
        TriggerSpecs.set(swap, "permanent", Boolean.TRUE);
        TriggerSpecs.set(swap, "target", "self");
        him2.setTriggerTable(new TriggerTable(1404, List.of(TriggerSpecs.rule("BATTLE_START", List.of(), swap)), List.of()));
        Battle battle2 = new Battle(List.of(him2),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle2.startBattle();
        battle2.processRequests();
        double enemyBefore2 = battle2.enemies.getFirst().getCurrentHp();
        SkillExecutor.execute(battle2, him2.getSkills().get(SkillType.SKILL), him2,
                List.of(battle2.enemies.getFirst()));
        battle2.processRequests();
        double slotNineDamage = enemyBefore2 - battle2.enemies.getFirst().getCurrentHp();

        System.out.println("[swap-probe] content cast=" + contentDamage + " ; slot-9 cast=" + slotNineDamage
                + " ; character max hp=" + him.getMaxHp() + " ; attack=" + him.getAttack());
    }
}
''')
print("ok   the probe is written")
