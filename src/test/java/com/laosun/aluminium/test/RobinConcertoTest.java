package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Countdown;
import com.laosun.aluminium.models.buff.ClassResistBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 知更鸟's ultimate 【协奏】, all seven clauses, from her <b>own file</b> (2026-09-28).
 *
 * <p><b>What it took.</b> Each clause waited for a named capability: the state and its timeline for the
 * <b>countdown unit</b> ({@code START_COUNTDOWN} / {@code COUNTDOWN_TURN}, M-49), 「不会进入自己的回合」 for
 * {@code suspends_turns}, the ATK boost and the immunity for <b>named modifiers</b> (so they can end with the state),
 * and the additional damage's 「暴击率固定为100%」 for the {@code crit_rate}/{@code crit_damage} spelling.
 *
 * <p><b>What is pinned here.</b> That one cast really opens the whole thing — state, countdown at #2's speed, party
 * boost, immunity — and that the countdown's turn closes <b>all of it at once</b> (state, boost and immunity come off
 * together, which is the point of naming them after the state).
 */
public class RobinConcertoTest {
    private static final int ROBIN = 1309;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** One cast opens the state, the countdown, the party boost and the immunity. */
    @Test
    public void herUltimateOpensTheWholeConcerto() {
        Fixture f = new Fixture();
        double allyAttackBefore = f.attack(f.ally);

        f.castUltimate();

        Assertions.assertTrue(f.robin.getBuffManager().hasState("协奏"), "① 进入【协奏】状态");
        Assertions.assertTrue(f.robin.getBuffManager().suspendsTurns(), "⑦ 不会进入自己的回合（挂在状态上）");
        Assertions.assertEquals(1, f.battle.countdowns().size(), "⑥ 行动序列上出现倒计时");
        Countdown countdown = f.battle.countdowns().getFirst();
        Assertions.assertEquals(90, countdown.getAttribute(AttributeType.SPEED).get(), 1e-6,
                "…固定拥有 90 点速度（技能自己的 #2）");
        Assertions.assertTrue(f.attack(f.ally) > allyAttackBefore,
                "③ 我方全体攻击力提高（22.8% 她的攻击力 + 200）：队友也吃到了");
        Assertions.assertFalse(f.robin.getBuffManager().allBuffsOf(ClassResistBuff.class).isEmpty(),
                "⑤ 处于【协奏】状态时免疫控制类负面状态");
    }

    /** ④ an ally's attack draws her extra hit — and nothing happens once the state is gone. */
    @Test
    public void anAllysAttackDrawsTheExtraDamage() {
        Fixture f = new Fixture();
        f.castUltimate();
        double before = f.enemy.getCurrentHp();

        f.allyAttacks();

        Assertions.assertTrue(f.enemy.getCurrentHp() < before,
                "④ 我方目标每次施放攻击后，她额外造成 1 次附加伤害（120% 攻击力，固定暴击）");

        double after = f.enemy.getCurrentHp();
        f.endConcerto();
        f.allyAttacks();
        Assertions.assertEquals(after, f.enemy.getCurrentHp(), 1e-6,
                "…and 「处于【协奏】状态时」 is load-bearing: without the state there is no rider");
    }

    /** ⑥ the countdown's turn ends the state <b>and</b> everything that was named after it, at once. */
    @Test
    public void theCountdownTurnEndsEverythingAtOnce() {
        Fixture f = new Fixture();
        f.castUltimate();
        double boosted = f.attack(f.ally);

        f.battle.fireTriggers(TriggerEvent.COUNTDOWN_TURN, f.battle.countdowns().getFirst(),
                f.battle.countdowns().getFirst(), 0, 0);

        Assertions.assertFalse(f.robin.getBuffManager().hasState("协奏"), "知更鸟退出【协奏】状态");
        Assertions.assertFalse(f.robin.getBuffManager().suspendsTurns(), "…so her turns come back");
        Assertions.assertTrue(f.attack(f.ally) < boosted,
                "…and the party ATK boost came off with it, because it carried the state's own name (before that "
                        + "spelling existed it could only have been permanent)");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static final class Fixture {
        private final Character robin = CharacterFactory.create(ROBIN, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        private final Battle battle;

        private Fixture() {
            // ⚠ No `setTriggerTable` here on purpose: `CharacterFactory.create` already attaches the table her FILE
            // declares, and overwriting it with an empty one is what made the first version of this test fail with
            // "the state is not there" (the rules under test were never loaded).
            battle = new Battle(List.of(robin, ally), List.of(enemy), fixed());
            battle.startBattle();
        }

        private void castUltimate() {
            battle.castImmediate(robin.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA), robin,
                    List.of(ally));
        }

        private void allyAttacks() {
            battle.fireTriggers(TriggerEvent.ALLY_ATTACK, ally, enemy, 1, 0);
        }

        /** Takes the state off without going through the countdown (the "no state, no rider" control). */
        private void endConcerto() {
            robin.getBuffManager().removeState("协奏");
        }

        private double attack(Character unit) {
            return unit.getAttribute(AttributeType.ATTACK).get();
        }
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
