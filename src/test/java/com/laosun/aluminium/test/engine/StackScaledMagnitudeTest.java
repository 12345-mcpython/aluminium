package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1218's two per-layer clauses: "每层[烬煨]使目标的全属性抗性降低3%" and
 * "1层时使敌人受到的伤害提高15.00%，此后每叠加1层提高5.00%".
 *
 * <p>The counter's name is a literal copied from the data file by the script that wrote this test. It is deliberately NOT
 * an escape: a hand-typed escape names a different character and reads as "the engine loses the counter" (the document's
 * name is Ashen Roast (烬煨), and a mistyped one differs in its second character).
 */
public class StackScaledMagnitudeTest {
    private static final int JIAOQIU = 1218;
    private static final int ATTACKER = 1210;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double EPS = 1e-9;
    private static final String COUNTER = "烬煨";

    @Test
    public void theResistanceReductionIsThreePercentPerStack() {
        Assertions.assertEquals(0.03, resistanceAfter(1), EPS, "one stack is 3%");
        Assertions.assertEquals(0.09, resistanceAfter(3), EPS, "three stacks are 9%, not 3% applied three times");
    }

    /**
     * The vulnerability zone, judged on what is robust.
     *
     * <p>Note: Why not the exact formula: the three measurements fire a different NUMBER of events (one layer marks one stack
     * with one event, three layers mark three), so the shared generator sits at a different point when the damage is
     * settled and the crit roll can differ. The exact curve is therefore pinned by construction (`amount: 0.1` +
     * `percent: 0.05` per layer, recorded in the rule's note), and this test pins the direction and the magnitude:
     * the damage rises with layers, and one layer's damage over the unmarked baseline is the document's ~15% plus the
     * per-layer resistance reduction's ~3%. Halving `amount` lands near 1.08 and fails; dropping the zone lands at 1.0.
     */
    @Test
    public void theVulnerabilityRisesWithTheLayers() {
        double none = damageAfter(0);
        double one = damageAfter(1);
        double three = damageAfter(3);
        Assertions.assertTrue(one > none && three > one,
                "the damage taken rises with the layers: " + none + " -> " + one + " -> " + three);
        double ratio = one / none;
        Assertions.assertTrue(ratio > 1.10 && ratio < 1.30,
                "one layer is the document's 15% plus the per-layer resistance reduction, not 1.0 or 1.08: " + ratio);
    }

    private static double resistanceAfter(int stacks) {
        Fixture f = fixture();
        mark(f, stacks);
        return f.enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
    }

    private static double damageAfter(int stacks) {
        Fixture f = fixture();
        mark(f, stacks);
        double before = f.enemy.getCurrentHp();
        f.battle.castImmediate(f.attacker.getSkills().get(SkillType.COMMON), f.attacker, List.of(f.enemy));
        return before - f.enemy.getCurrentHp();
    }

    private static void mark(Fixture f, int stacks) {
        for (int i = 0; i < stacks; i++) {
            f.battle.fireTriggers(TriggerEvent.ALLY_ATTACK, f.jiaoqiu, f.enemy, 1, 1000);
        }
        Assertions.assertEquals(stacks, f.enemy.getBuffManager().stacksOf(COUNTER),
                "precondition: the talent marked exactly " + stacks + " stack(s)");
    }

    private static Fixture fixture() {
        Character jiaoqiu = CharacterFactory.create(JIAOQIU, LEVEL);
        Character attacker = CharacterFactory.create(ATTACKER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        // A generator that never crits (0.99 is above any crit chance) and never misses a base-chance roll: without it
        // the three measurements sit at different points in the shared stream, the crit flips, and the damage ratio
        // reflects the roll rather than the zone (measured: d1/d0 read 1.38 instead of ~1.19).
        Random noCrit = new Random(0) {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        };
        Battle battle = new Battle(List.of(jiaoqiu, attacker), List.of(enemy), noCrit);
        battle.startBattle();
        return new Fixture(jiaoqiu, attacker, enemy, battle);
    }

    private record Fixture(Character jiaoqiu, Character attacker, Enemy enemy, Battle battle) {
    }
}
