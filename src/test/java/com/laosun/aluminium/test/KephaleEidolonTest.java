package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1412's eidolon 1: "a character holding [军功] ignores 16% of the target's defence when dealing damage".
 *
 * <p>The clause mirrors its own gate: same event, same target, same length. Two ways -- with the gating skill cast the
 * marked unit ignores the stated share, and without it nothing is stated.
 */
public class KephaleEidolonTest {
    private static final int OWNER = 1412;   // the marked ally is the Skill's target
    private static final int ALLY = 1210;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double EPS = 1e-9;
    private static final int RANK = 1;

    @Test
    public void theMarkedUnitIgnoresItsShareOfDefence() {
        Assertions.assertEquals(0.16, marked(true), EPS, "a character holding [军功] ignores 16% of the target's defence when dealing damage");
        Assertions.assertEquals(0.0, marked(false), EPS, "without the gate nothing is stated");
    }

    /** The candidate's DEFENCE_IGNORE with and without the gating cast. */
    private static double marked(boolean gated) {
        Character owner = CharacterFactory.create(OWNER, LEVEL, true, null, null, RANK);
        Character partner = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(owner, partner), List.of(enemy), new Random(0));
        battle.startBattle();
        Character candidate = false ? owner : partner;
        if (gated) {
            battle.castImmediate(owner.getSkills().get(SkillType.SKILL), owner, List.of(partner));
        }
        return candidate.getAttribute(AttributeType.DEFENCE_IGNORE).get()
                - candidate.getAttribute(AttributeType.DEFENCE_IGNORE).baseValue();
    }
}
