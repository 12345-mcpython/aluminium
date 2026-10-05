package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.RelicTriggerTables;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerInterpreter;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Effect-level repetition (2026-09-30): the {@code times} field on an effect.
 *
 * <p>The fixture set 99004 holds two rules differing in nothing but {@code times} (3 vs 1), so the observable is how
 * much damage ONE battle produces -- an absolute figure would drag defence and mitigation into the assertion.
 *
 * <p><b>Both sides must be one battle that settles three times.</b> Measured (rounds 631/632): one application of
 * {@code times: 3} against three applications of {@code times: 1} gave 1159.30 against 993.69, because each
 * application in a FRESH battle restarts the RNG at Random(0), while times: 3 advances the battle RNG three times and
 * can crit on different rolls. Three settlements inside one battle consume exactly what one times: 3 consumes.
 *
 * <p>Nine shipped clauses are registered on this shape; 8005 is the one that has shipped.
 */
public class TimesRepeatTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;

    /** Applies the named rule of 99004 applications times INSIDE one battle; returns the enemy HP loss. */
    private static double lossWith(String ruleId, int applications) {
        Character c = CharacterFactory.create(WEARER, LEVEL, true, null, RelicFactory.suit(99004, 5, 15));
        Enemy e = EnemyFactory.create(1002011, 90, 1);
        Battle b = new Battle(List.of(c), List.of(e), new Random(0));
        b.startBattle();
        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(c, c, e, 1, 0, null, b,
                SkillCategory.UNSPECIFIED);
        var rules = RelicTriggerTables.of(99004).at(2)
                .matching(TriggerEvent.BATTLE_START, ctx).stream()
                .filter(r -> ruleId.equals(r.id())).toList();
        Assertions.assertEquals(1, rules.size(), ruleId + " must match");
        double before = e.getCurrentHp();
        for (int i = 0; i < applications; i++) {
            TriggerInterpreter.apply(b, rules.getFirst(), ctx);
        }
        return before - e.getCurrentHp();
    }

    @Test
    public void oneTimesThreeEqualsThreeTimesOneInOneBattle() {
        double three = lossWith("times_three", 1);
        double oneThreeTimes = lossWith("times_one", 3);
        Assertions.assertTrue(three > 0, "times 3 must deal something (got " + three + ")");
        Assertions.assertEquals(oneThreeTimes, three, 1e-6,
                "one settlement repeated 3 times must equal three single settlements in one battle -- that is what "
                        + "times means (times_three=" + three + ", 3x times_one=" + oneThreeTimes + ")");
        System.out.println("[times] repeat ok: times_three=" + three + " 3x times_one=" + oneThreeTimes);
    }
}
