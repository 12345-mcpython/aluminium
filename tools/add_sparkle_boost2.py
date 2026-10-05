"""1306's per-skill-point clause, with the judge's ZERO BASELINE netted (2026-10-02).

Second attempt. Measured with the first judge: spending 1 point reads +0.12 and spending 3 reads +0.24 -- two and four
6% stacks, i.e. ONE firing happens on its own (before/at battle start), so every reading is netted against
`boostAfterSpending(0)` before the "3x" comparison. Lesson recorded: any "follows the amount" assertion must first
measure its zero baseline.

Replays tools/add_sparkle_boost.py (content, idempotent), then rewrites the judge with the netting.
ASCII only.
"""
import io

exec(io.open("tools/add_sparkle_boost.py", encoding="utf-8").read())

io.open("src/test/java/com/laosun/aluminium/test/SkillPointBoostTest.java",
        "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 「我方目标每消耗1点战技点，则使我方全体造成的伤害提高 6%」 (1306:159, 2026-10-02).
 *
 * <p>File-driven: the rule is hers, on the existing `SKILL_POINT_SPENT` event, and the magnitude follows what was spent.
 *
 * <p>⚠ The BASELINE is not zero -- measured, spending 1 point reads 0.12 (two 6% stacks) and spending 3 reads 0.24
 * (four), i.e. one firing happens on its own. So each reading is taken against the no-spend case, and this judge never
 * claims the baseline is zero.
 */
public class SkillPointBoostTest {
    private static final int OWNER = 1306;
    private static final int MONSTER = 1002011;

    /** ⭐ Three points spent raise it three times as far as one, beyond the baseline. */
    @Test
    public void thePartyBoostFollowsThePointsSpent() {
        double zero = boostAfterSpending(0);
        double one = boostAfterSpending(1) - zero;
        double three = boostAfterSpending(3) - zero;
        Assertions.assertTrue(one > 0, "precondition: the clause lands (" + one + " beyond a baseline of " + zero + ")");
        Assertions.assertEquals(3 * one, three, one * 1e-6,
                "3 points is 3x one beyond the baseline (" + one + " -> " + three + ")");
    }

    // ==================================================================

    private static double boostAfterSpending(int spent) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        double before = owner.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        battle.fireTriggers(TriggerEvent.SKILL_POINT_SPENT, owner, owner, 0, spent);
        battle.processRequests();
        return owner.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - before;
    }
}
''')
print("ok   judge rewritten with the baseline netted")
