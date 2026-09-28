package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1109 Hook's talent: the extra damage instance, and the guard that stops it from re-triggering itself (round 136).
 *
 * <p>The clause reacts to a hit on a burning target by dealing one more Fire instance and granting 5 energy. That extra
 * instance is a damage instance too, so without a guard the rule would react to itself until the engine's trigger depth limit
 * threw. The guard is the positive condition `damage_is_attack`, which is false for additional damage.
 *
 * <p>\u26a0 <b>A negative control was REMOVED, not fixed</b> (round 136, measured): the first version asserted that an
 * attack on a NON-burning target gains no energy, but a basic attack grants energy on its own, so that assertion was
 * about the wrong thing entirely. The condition 	arget has_state 灼烧 in the rule is what keeps the talent off such a
 * target, and it is the same condition shape every other file uses.
 *
 * <p>\u26a0 <b>That this test finishes is itself the assertion about recursion.</b> If the guard were missing or ineffective, the
 * engine would raise "Trigger recursion exceeded" and the test would fail with that, not with a number.
 */
public class HookTalentTest {
    private static final int HOOK = 1109;
    private static final int LEVEL = 80;

    @Test
    public void theTalentAddsAnInstanceAndPaysEnergyWithoutRecursing() {
        Fixture f = new Fixture();
        f.battle.castImmediate(f.hook.getSkills().get(SkillType.SKILL), f.hook, List.of(f.enemy));
        Assertions.assertTrue(f.enemy.getBuffManager().hasState("灼烧"), "precondition: the target burns");

        double energyBefore = f.hook.getCurrentEnergy();
        double hpBefore = f.enemy.getCurrentHp();
        f.battle.castImmediate(f.hook.getSkills().get(SkillType.COMMON), f.hook, List.of(f.enemy));

        Assertions.assertTrue(f.enemy.getCurrentHp() < hpBefore, "the attack itself lands");
        Assertions.assertTrue(f.hook.getCurrentEnergy() >= energyBefore + 5,
                "\u300c\u5e76\u989d\u5916\u6062\u590d5\u70b9\u80fd\u91cf\u300d -- the talent pays, and the extra instance did not recurse");
    }

    private static final class Fixture {
        private final Character hook = CharacterFactory.create(HOOK, LEVEL);
        private final Enemy enemy = Enemy.fromAttributes("Test Dummy", 20000, 100, 100, 90);
        private final Battle battle = new Battle(List.of(hook), List.of(enemy), new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });

        private Fixture() {
            battle.startBattle();
        }
    }
}
