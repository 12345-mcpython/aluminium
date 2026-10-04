"""The reading for 1217's 【禳命】 duration (round 2 of the goal).

Measured before writing:
  * the sentence is 「施放战技后藿藿获得【禳命】，持续 2 回合，**藿藿每回合开始时**持续回合数减 1」, and the file already expresses it with
    `APPLY_BUFF { turns: 2, ticks_on: "self" }` -- so the §3 row's premise ("needs a shorten-duration spelling") is stale: the
    clause is about WHOSE CLOCK spends the duration, which `ticks_on` already says;
  * ⚠ and my own "the corpus has no such sentence" conclusion was WRONG: I had hand-typed U+79ED, while both the corpus and the
    file use U+79B3. Read codepoints from files; never type them from memory. (138,128 files scanned, the right codepoint is there.)
  * the drive and the counting convention are copied from a green sibling, `ArlanEidolonFourTest`: a timed buff ticks on its
    wearer's turns in TWO halves, `beforeMove()` then `afterMove()`, and expiry is announced on the late half.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/HuohuoTalismanDurationTest.java"

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1217 \u85ff\u85ff\uff1a\u300c\u65bd\u653e\u6218\u6280\u540e\u85ff\u85ff\u83b7\u5f97\u3010\u79b3\u547d\u3011\uff0c\u6301\u7eed 2 \u56de\u5408\uff0c**\u85ff\u85ff\u6bcf\u56de\u5408\u5f00\u59cb\u65f6**\u6301\u7eed\u56de\u5408\u6570\u51cf 1\u300d (2026-10-02).
 *
 * <p>The point is WHOSE clock spends it: the sentence names \u85ff\u85ff, not the party. \u26a0 The drive is the one a green sibling uses
 * (`ArlanEidolonFourTest`): a timed buff ticks in two halves per turn, and expiry is announced on the late one.
 */
public class HuohuoTalismanDurationTest {
    private static final int HUOHUO = 1217;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String STATE = "\\u79b3\\u547d";

    /** A teammate's turn does not spend it; hers does, and the count is the one the sentence states. */
    @Test
    public void theTalismanRunsOnHerOwnClock() {
        Character her = CharacterFactory.create(HUOHUO, 80, false, null, null, 0);
        Character ally = CharacterFactory.create(ALLY, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(her, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        SkillExecutor.execute(battle, her.getSkills().get(SkillType.SKILL), her, List.of(her));
        battle.processRequests();
        Assertions.assertTrue(her.getBuffManager().hasState(STATE), "precondition: her skill grants \u3010\u79b3\u547d\u3011");

        spendTurnOf(battle, ally);
        boolean afterAllyTurn = her.getBuffManager().hasState(STATE);
        spendTurnOf(battle, her);
        boolean afterHerFirstTurn = her.getBuffManager().hasState(STATE);
        spendTurnOf(battle, her);
        boolean afterHerSecondTurn = her.getBuffManager().hasState(STATE);
        System.out.println("[huohuo-clock] after the ally's turn=" + afterAllyTurn
                + " ; after her 1st=" + afterHerFirstTurn + " ; after her 2nd=" + afterHerSecondTurn);

        Assertions.assertTrue(afterAllyTurn,
                "\u300c\u85ff\u85ff\u6bcf\u56de\u5408\u5f00\u59cb\u65f6\u300d-- the clock is HERS, so a teammate's turn costs it nothing");
        Assertions.assertTrue(afterHerFirstTurn, "\u300c\u6301\u7eed 2 \u56de\u5408\u300d-- one of her turns is not two");
        Assertions.assertFalse(afterHerSecondTurn, "\u300c\u6301\u7eed\u56de\u5408\u6570\u51cf 1\u300d-- two of her turns spend it");
    }

    /** \u26a0 Half a turn is `beforeMove()` alone; a full one is both halves, and expiry lands on the late half. */
    private static void spendTurnOf(Battle battle, Character unit) {
        Signal signal = battle.queue.snapshot().stream()
                .filter(candidate -> candidate.getCanHit() == unit).findFirst()
                .orElseThrow(() -> new AssertionError("precondition: the unit is in the queue"));
        battle.currentMove = signal;
        battle.beforeMove();
        battle.afterMove();
        battle.processRequests();
    }
}
''')
print("ok   the reading is written")
