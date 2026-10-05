package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerInterpreter;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1306 Sparkle: "当我方目标每消耗 1 点战技点，花火获得 1 层[幻相]" -- PER POINT, not per spending action.
 *
 * <p>Shipped 2026-09-30 by giving the rule `scale: event_amount` + `percent: 1`, which `addStack` already honoured
 * (built for cone 23021). The other 1306 judges all spend one point per call, where per-action and per-point agree, so
 * they cannot see this; this one hands the rule a context whose amount is 2 and expects two stacks.
 */
public class SparklePerPointTest {
    private static final int SPARKLE = 1306;
    private static final int LEVEL = 80;

    private static int phantasmAfter(int pointsSpent) {
        Character c = CharacterFactory.create(SPARKLE, LEVEL, true, null, null);
        Enemy e = EnemyFactory.create(1002011, 90, 1);
        Battle b = new Battle(List.of(c), List.of(e), new Random(0));
        b.startBattle();
        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(c, c, e, 1, pointsSpent, null, b,
                SkillCategory.UNSPECIFIED);
        var rules = TriggerTables.of(SPARKLE).rulesFor(TriggerEvent.SKILL_POINT_SPENT).stream()
                .filter(r -> "talent_phantasm_stack".equals(r.id())).toList();
        Assertions.assertEquals(1, rules.size(), "the stack rule must exist");
        TriggerInterpreter.apply(b, rules.getFirst(), ctx);
        return c.getBuffManager().stacksOf("幻相");
    }

    @Test
    public void twoSpentPointsGrantTwoStacks() {
        Assertions.assertEquals(1, phantasmAfter(1), "one point is one stack");
        Assertions.assertEquals(2, phantasmAfter(2),
                "two points must be two stacks -- that is what per-point means, and a per-action reading gives 1");
        System.out.println("[1306] per-point ok: 1 point -> " + phantasmAfter(1) + ", 2 points -> " + phantasmAfter(2));
    }
}
