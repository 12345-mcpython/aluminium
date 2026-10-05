package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
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
 * 1305 Dr. Ratio, from his own file (2026-09-29, round 204): the Wiseman's Folly reaction and the technique's slow.
 *
 * <p>The reaction is measured by FIRING the event (round 190's lesson: a real ally attack would mix its own damage into the reading). The slow is compared with a
 * hand-built -30% reference in the same pipeline, so the ratio 0.5 is the claim.
 */
public class DrRatioTest {
    private static final int RATIO = 1305;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: The reaction needs the MARK: without it a teammate's attack does nothing. */
    @Test
    public void theReactionNeedsTheMark() {
        double beforeMark = reactionLoss(false);
        double afterMark = reactionLoss(true);

        Assertions.assertEquals(0.0, beforeMark, 1e-9,
                "「持有【智者的短见】的目标」 -- no mark, no reaction");
        Assertions.assertTrue(afterMark > 0,
                "「理真医生对该目标发动1次天赋的追加攻击」: " + afterMark);
    }

    /** Note: The technique's 15% slow, against a hand-built -30% reference in the same pipeline. */
    @Test
    public void theTechniqueSlowsTheEnemiesByFifteenPercent() {
        double content = techniqueSlow(0);
        double reference = techniqueSlow(1);

        Assertions.assertTrue(reference > 0, "the reference must lower speed at all");
        Assertions.assertEquals(0.5, content / reference, 0.05,
                "content " + content + " vs reference " + reference);
    }

    /** Fires a TEAMMATE's attack at the enemy and returns its HP loss; optionally marks the enemy first. */
    private static double reactionLoss(boolean mark) {
        Character ratio = CharacterFactory.create(RATIO, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(ratio, ally), List.of(enemy), fixed());
        battle.startBattle();
        if (mark) {
            battle.fireTriggers(TriggerEvent.ULT_CAST, ratio, enemy, 0, 0);
        }
        double before = enemy.getCurrentHp();
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0);
        return before - enemy.getCurrentHp();
    }

    /** mode 0 = the shipped file, 1 = a hand-built -30% reference. Returns the enemy's speed drop at battle start. */
    private static double techniqueSlow(int mode) {
        Character ratio = CharacterFactory.create(RATIO, LEVEL);
        if (mode == 1) {
            EffectSpec effect = new EffectSpec();
            TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
            TriggerSpecs.set(effect, "attribute", "SPEED");
            TriggerSpecs.set(effect, "percent", -0.30);
            TriggerSpecs.set(effect, "turns", 2);
            TriggerSpecs.set(effect, "target", "all_enemies");
            ratio.setTriggerTable(new TriggerTable(RATIO, List.of(TriggerSpecs.rule(
                    TriggerEvent.BATTLE_START.name(), List.of(), effect))));
        }
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(ratio), List.of(enemy), fixed());
        if (mode == 0) {
            battle.markTechniqueUsed(ratio);
        }
        double before = enemy.getAttribute(AttributeType.SPEED).get();
        battle.startBattle();
        return before - enemy.getAttribute(AttributeType.SPEED).get();
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }
}
