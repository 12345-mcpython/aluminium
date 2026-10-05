package com.laosun.aluminium.test;

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
 * A <b>state that is rolled</b> (2026-09-28): {@code APPLY_BUFF} with {@code base_chance}.
 *
 * <p>"有 100% 的基础概率使敌方每个单体目标陷入[通解]状态" (1106 佩拉) needed it, and so does a family of 69 sentences across 24
 * files (six of them already-shipped characters). Note: The point is <b>not</b> "100% always works": the state goes through the same
 * {@code tryApplyDebuff} path a DOT or a control uses, so <b>effect resistance still applies</b> - "基础概率" is the chance
 * <i>before</i> resistance, and applying the state unconditionally would be a different mechanic rather than a bigger number.
 *
 * <p><b>What is pinned here.</b> That a stated chance is really <b>rolled</b> (a tiny chance with a 0.5 roll leaves nothing
 * behind, while the same rule with no chance stated always lands), and that a plain state still meets no <i>class</i>
 * resistance - it reports no {@code debuffClass}, which is the honest limit of this spelling.
 */
public class RolledStateTest {
    private static final int CID = 1106;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** A stated chance is rolled: 0.0001 against a 0.5 roll attaches nothing. */
    @Test
    public void aStatedChanceIsRolled() {
        Assertions.assertFalse(lands(0.0001, 0.5), "「基础概率」 is a real roll, not a label");
    }

    /** …while the same rule with no chance stated attaches unconditionally (every existing file's behaviour). */
    @Test
    public void noStatedChanceIsNotARoll() {
        Assertions.assertTrue(lands(null, 0.5), "unstated = applied directly, which is what every earlier file relies on");
    }

    /**
     * Note: And the round's actual claim, pinned: <b>a 100% base chance can still be resisted.</b>
     *
     * <p>A 0.999999 draw against 1.0 must fail, because the chance is the number <i>before</i> effect resistance - if this
     * ever lands, the state is being applied unconditionally and "基础概率" has silently become "always". (The first
     * version of this case asserted the opposite, which is how the difference got measured rather than assumed.)
     */
    @Test
    public void aHundredPercentBaseChanceIsStillResistible() {
        Assertions.assertFalse(lands(1.0, 0.999999),
                "「基础概率」 is the chance BEFORE resistance: 1.0 still meets it, which is why applying unconditionally "
                        + "would be a different mechanic");
        Assertions.assertTrue(lands(1.0, 0.0), "…and an easy draw lands");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static boolean lands(Double baseChance, double roll) {
        Character pela = CharacterFactory.create(CID, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);

        EffectSpec state = new EffectSpec();
        TriggerSpecs.set(state, "op", "APPLY_BUFF");
        TriggerSpecs.set(state, "buff", "通解");
        TriggerSpecs.set(state, "turns", 2);
        TriggerSpecs.set(state, "target", "target");
        if (baseChance != null) {
            TriggerSpecs.set(state, "baseChance", baseChance);
        }
        pela.setTriggerTable(new TriggerTable(CID, List.of(TriggerSpecs.rule("KILL", List.of("actor == self"), state))));

        Battle battle = new Battle(List.of(pela), List.of(enemy), new Random() {
            @Override
            public double nextDouble() {
                return roll;
            }
        });
        battle.startBattle();
        battle.fireTriggers(TriggerEvent.KILL, pela, enemy, 0, 0);
        return enemy.getBuffManager().hasState("通解");
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
