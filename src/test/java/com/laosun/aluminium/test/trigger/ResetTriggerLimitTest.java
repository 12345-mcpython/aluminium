package com.laosun.aluminium.test.trigger;


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
 * RESET_TRIGGER_LIMIT: "施放终结技后重置该效果触发次数".
 *
 * <p>One rule is capped at one firing per turn; a second rule on ULT_CAST clears exactly that rule limit, so
 * the capped rule fires again. Note: resetTriggerLimits() would clear EVERY rule of the unit instead.
 */
public class ResetTriggerLimitTest {
    private static final int OWNER = 1001;
    private static final int LEVEL = 80;
    private static final String CAPPED = "limit_probe";

    private static int probeStacksAfterTwoFiresThenReset() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));
        battle.startBattle();

        EffectSpec stack = new EffectSpec();
        TriggerSpecs.set(stack, "op", "ADD_STACK");
        TriggerSpecs.set(stack, "buff", "探针");
        TriggerSpecs.set(stack, "amount", 1.0d);
        TriggerSpecs.set(stack, "target", "self");
        // ADD_STACK has no default lifetime: the loader names turns / permanent / until (measured in round 33).
        TriggerSpecs.set(stack, "permanent", Boolean.TRUE);
        // Note: addStack defaults the cap to 1, so a probe without this saturates at one and looks like a
        // frozen failure (measured in round 4after six rounds of chasing the wrong thing).
        TriggerSpecs.set(stack, "maxStacks", 3);
        TriggerSpec capped = TriggerSpecs.rule(TriggerEvent.SKILL_CAST.name(), List.of(), stack);
        TriggerSpecs.set(capped, "id", CAPPED);
        TriggerSpecs.set(capped, "perTurn", 1);

        EffectSpec reset = new EffectSpec();
        TriggerSpecs.set(reset, "op", "RESET_TRIGGER_LIMIT");
        TriggerSpecs.set(reset, "rule", CAPPED);
        TriggerSpecs.set(reset, "target", "self");
        TriggerSpec clear = TriggerSpecs.rule(TriggerEvent.ULT_CAST.name(), List.of(), reset);

        owner.setTriggerTable(new TriggerTable(OWNER, List.of(capped, clear)));
        battle.fireTriggers(TriggerEvent.SKILL_CAST, owner, enemy, 0, 0);
        battle.fireTriggers(TriggerEvent.SKILL_CAST, owner, enemy, 0, 0);
        System.out.println("[reset] keyOf=" + owner.getTriggerTable().keyOf(CAPPED)
                + " key=" + owner.getTriggerTable().rulesFor(TriggerEvent.SKILL_CAST)
                        .stream().filter(r -> CAPPED.equals(r.id())).findFirst()
                        .map(r -> r.key()).orElse("<none>"));
        battle.fireTriggers(TriggerEvent.ULT_CAST, owner, enemy, 0, 0);
        battle.fireTriggers(TriggerEvent.SKILL_CAST, owner, enemy, 0, 0);
        return owner.getBuffManager().stacksOf("探针");
    }

    @Test
    public void theResetLetsTheCappedRuleFireAgain() {
        int stacks = probeStacksAfterTwoFiresThenReset();
        Assertions.assertEquals(2, stacks,
                "two fires before the reset (the second is capped) plus one after must be 2, got " + stacks);
        System.out.println("[reset] ok: stacks=" + stacks);
    }
}
