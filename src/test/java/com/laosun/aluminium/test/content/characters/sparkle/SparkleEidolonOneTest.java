package com.laosun.aluminium.test.content.characters.sparkle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1306 花火's Eidolon 1 (「【谜诡】持续时间 +1 回合」 and 「持有【谜诡】的我方目标攻击力提高 40%」) plus the Ultimate's own
 * per-layer rider.
 *
 * <p><b>Why a criterion for the second half especially.</b> The clause is conditional on the HOLDER (「持有【谜诡】的我方
 * target」), not on the rule owner - and the wrong spelling (grant it once at the cast, no gate) looks identical on the cast
 * turn and then keeps buffing an ally after the state has left. {@code while_holder_state} is the gate that asks the
 * carrier; this case walks the state off one ally and checks that only that ally loses the 40%.
 *
 * <p>The rider 「每层额外提高 10%」 is the <b>base kit's</b> clause (it is in the Ultimate's own text), so it must already be
 * there at E0 - that is asserted as its own reading, not as an Eidolon difference.
 */
public class SparkleEidolonOneTest {
    private static final int SPARKLE = 1306;
    /** A character with no rule file of its own, so no other content moves its panel (1210 Guinaifen). */
    private static final int ALLY = 1210;
    private static final int LEVEL = 80;
    private static final String PHANTASM = "谜诡";

    /**
     * 「持有【谜诡】的我方目标攻击力提高 40%」.
     */
    @Test
    public void theFirstEidolonRaisesTheAttackOfWhoeverHoldsThePhantasm() {
        Run e0 = new Run(0);
        Run e1 = new Run(1);
        System.out.println("[1306-E1] ally ATK delta: E0=" + e0.attackDelta() + " ; E1=" + e1.attackDelta());
        Assertions.assertEquals(0.0, e0.attackDelta(), 1e-6, "E0 states nothing of the sort");
        Assertions.assertEquals(0.4 * e1.allyBaseAttack, e1.attackDelta(), 1e-6, "「攻击力提高 40%」");
    }

    /**
     * The gate asks the CARRIER: take 谜诡 off the ally, and only that ally loses the 40%.
     */
    @Test
    public void theBonusLeavesWithTheStateOfItsCarrier() {
        Run run = new Run(1);
        Assertions.assertEquals(0.4 * run.allyBaseAttack, run.attackDelta(), 1e-6);

        run.ally.getBuffManager().removeState(PHANTASM);
        // A `while_holder_state` modifier is pruned at the next turn boundary, like every read-time gate.
        run.battle.setCurrentMove(new com.laosun.aluminium.Signal(run.ally));
        run.battle.beforeMove();
        run.battle.processRequests();

        Assertions.assertEquals(0.0, run.attackDelta(), 1e-6,
                "the gate is `while_holder_state`, so the state going takes the bonus with it");
        Assertions.assertTrue(run.sparkle.getBuffManager().hasState(PHANTASM),
                "her own copy is untouched (the removal was on the ally)");
    }

    /**
     * 「触发花火天赋造成的伤害提高效果每层额外提高 10%」 - the rider is in the Ultimate's own text, so E0 has it too.
     */
    @Test
    public void theUltimateRaisesTheTalentsBoostPerLayer() {
        Run e0 = new Run(0);
        Run e1 = new Run(1);
        double plain = e0.boostAfterThreeSpends();
        double raised = e1.boostAfterThreeSpends();
        System.out.println("[1306] ally all-damage boost after three spends: E0=" + plain + " ; E1=" + raised);
        Assertions.assertEquals(0.06 * 3 + 0.1 * 3, plain, 1e-6,
                "6% per spent point (3 layers) plus 10% per layer of that same boost, for the 谜诡 holder");
        Assertions.assertEquals(plain, raised, 1e-6, "the rider is base kit, not an Eidolon");
    }

    // ==================================================================

    private static final class Run {
        private final Character sparkle;
        private final Character ally;
        private final Battle battle;
        private final double allyBaseAttack;
        private final double allyAttackBefore;

        Run(int eidolon) {
            sparkle = CharacterFactory.create(SPARKLE, LEVEL);
            sparkle.setEidolonRank(eidolon);
            ally = CharacterFactory.create(ALLY, LEVEL);
            allyBaseAttack = ally.getAttribute(AttributeType.ATTACK).baseValue();
            Enemy enemy = BattleFixture.monster();
            battle = new Battle(List.of(sparkle, ally), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            allyAttackBefore = ally.getAttribute(AttributeType.ATTACK).get();
            battle.castImmediate(sparkle.getSkills().get(SkillType.ULTRA), sparkle, List.of(enemy));
            battle.processRequests();
        }

        double attackDelta() {
            return ally.getAttribute(AttributeType.ATTACK).get() - allyAttackBefore;
        }

        /** Three skill points spent: the talent stacks its 6% three times, then the rider reads the same layers. */
        double boostAfterThreeSpends() {
            for (int i = 0; i < 3; i++) {
                battle.fire(TriggerPayload.Simple.of(TriggerEvent.SKILL_POINT_SPENT, ally,
                        battle.enemies().getFirst(), 1, 0));
                battle.processRequests();
            }
            return ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        }
    }
}
