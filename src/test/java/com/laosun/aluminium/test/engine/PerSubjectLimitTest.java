package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
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
 * "该效果每个角色最多触发 1 次" (1403) -- a firing count that belongs to the TRIGGERER, not the owner.
 *
 * <p>The probe stack must be allowed past one (addStack caps at 1 by default), otherwise the reading
 * saturates and a working scope looks like a frozen failure -- the six rounds lost in 34-4.
 */
public class PerSubjectLimitTest {
    private static final int OWNER = 1001;
    private static final int ALLY_A = 1002;
    private static final int ALLY_B = 1004;
    private static final int LEVEL = 80;

    private static int stacksAfterTwoDifferentTriggerers(String perSubject) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Character a = CharacterFactory.create(ALLY_A, LEVEL);
        Character b = CharacterFactory.create(ALLY_B, LEVEL);
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(owner, a, b), List.of(enemy), new Random(0));
        battle.startBattle();

        EffectSpec stack = new EffectSpec();
        TriggerSpecs.set(stack, "op", "ADD_STACK");
        TriggerSpecs.set(stack, "buff", "探针");
        TriggerSpecs.set(stack, "amount", 1.0d);
        TriggerSpecs.set(stack, "target", "self");
        TriggerSpecs.set(stack, "permanent", Boolean.TRUE);
        TriggerSpecs.set(stack, "maxStacks", 5);
        TriggerSpec rule = TriggerSpecs.rule(TriggerEvent.SKILL_CAST.name(), List.of(), stack);
        TriggerSpecs.set(rule, "id", "subject_probe");
        TriggerSpecs.set(rule, "perTurn", 1);
        if (perSubject != null) {
            TriggerSpecs.set(rule, "perSubject", perSubject);
        }
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(rule)));

        battle.fireTriggers(TriggerEvent.SKILL_CAST, a, enemy, 0, 0);
        battle.fireTriggers(TriggerEvent.SKILL_CAST, b, enemy, 0, 0);
        return owner.getBuffManager().stacksOf("探针");
    }

    @Test
    public void theCountFollowsTheTriggerer() {
        Assertions.assertEquals(1, stacksAfterTwoDifferentTriggerers(null),
                "without the scope the second firer is blocked by the owner-wide count");
        int scoped = stacksAfterTwoDifferentTriggerers("actor");
        Assertions.assertEquals(2, scoped,
                "with per_subject actor each triggerer gets its own count, got " + scoped);
        System.out.println("[subject] ok: owner-wide=" + stacksAfterTwoDifferentTriggerers(null)
                + " per-actor=" + scoped);
    }
}
