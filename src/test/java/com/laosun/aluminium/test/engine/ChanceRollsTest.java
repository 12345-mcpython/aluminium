package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.damage.Damage;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@code chance_rolls: N} - one firing that rolls its {@code chance} N times, each attempt standing on its own.
 *
 * <p>Reader: 1206 Sushang's ultimate, 「施放战技时额外增加 2 次【剑势】的发动判定」. The spelling exists because the
 * obvious data-only form - a second rule with the same {@code chance} - is refused by
 * {@code ContentDuplicateGuardTest} when the two rules are symmetric (measured: it reports "one sentence written
 * twice"). So the count lives on the rule.
 *
 * <p>⚠ What this guard can and cannot see. It counts the settled damage instances of one Skill cast through the
 * engine's own observer, so "two attempts really settled twice" is asserted exactly. What it does NOT assert is
 * that the two attempts are statistically independent: doing that needs a draw sequence whose positions depend on
 * how many draws the crit roll consumes in between, i.e. an assertion about internals rather than about the rule.
 * The count below is what a broken implementation fails: with {@code chance_rolls} ignored, the {@code chance: 1.0}
 * case settles 2 instances instead of 3.
 */
public class ChanceRollsTest {
    private static final int SUSHANG = 1206;
    private static final int LEVEL = 80;
    /** Her own Sword Stance is 33%, so the dice are pinned to make the base one land every time. */
    private static final double[] ALWAYS = {0.0};

    /**
     * Without the ultimate's marker, her Skill settles exactly one additional instance: the base Sword Stance.
     */
    @Test
    public void withoutTheMarkerThereIsOneAdditionalInstance() {
        Fixture f = new Fixture();
        Assertions.assertEquals(0, f.extraStacks(), "precondition: the ultimate has not been cast");

        f.castSkill();

        Assertions.assertEquals(1, f.additionalInstances(),
                "the base 【剑势】 only: " + f.additionalInstances() + " additional instance(s)");
    }

    /**
     * ⭐ With the marker, the two extra judgements settle as well - two more instances at 50% of her ATK.
     *
     * <p>⚠ The extra rules carry {@code chance: 1.0} so that no draw is involved: the assertion is about the
     * NUMBER of attempts, and pinning the dice keeps it from being a statement about luck.
     */
    @Test
    public void theMarkerAddsTwoMoreInstances() {
        Fixture f = new Fixture();
        f.mark();
        Assertions.assertEquals(1, f.extraStacks(), "precondition: the marker is up");

        f.castSkill();

        Assertions.assertEquals(3, f.additionalInstances(),
                "the base 【剑势】 plus the two extra judgements: " + f.additionalInstances()
                        + " additional instance(s)");
    }

    /**
     * And they are worth 50% of the base one, as the same sentence says: 「通过额外判定发动的【剑势】伤害为原伤害的50%」.
     *
     * <p>⚠ A BAND, not an exact 0.5, and the reason is measured: two other instances from the same cast come out
     * 235.07 and 240.81 against a base of 458.68 (0.5125 and 0.525), i.e. the engine applies a spread to damage,
     * so an exact ratio is not an observable of the rule. The band is still a real guard - 50% stated as 60% would
     * land at ~0.61 and fail - and it is asserted on BOTH extra instances so a rule that kept the base multiplier
     * for one of the two rolls cannot pass.
     */
    @Test
    public void theExtraInstancesAreHalfTheBaseOne() {
        Fixture f = new Fixture();
        f.mark();
        f.castSkill();

        List<Double> amounts = f.additionalAmounts();
        Assertions.assertEquals(3, amounts.size(), "precondition: three additional instances");
        double base = amounts.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        List<Double> smaller = amounts.stream().filter(a -> a < base).toList();

        Assertions.assertEquals(2, smaller.size(),
                "exactly two instances are smaller than the base one, and they are the extra judgements: " + amounts);
        for (double instance : smaller) {
            double ratio = instance / base;
            Assertions.assertTrue(ratio > 0.45 && ratio < 0.55,
                    "「原伤害的50%」: instance " + instance + " over base " + base + " = " + ratio
                            + " (a 60% rule would land near 0.61)");
        }
    }

    private static final class Fixture {
        private final Character sushang = CharacterFactory.create(SUSHANG, LEVEL);
        private final Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        private final Battle battle = new Battle(List.of(sushang), List.of(enemy),
                BattleFixture.alwaysRoll(ALWAYS[0]));
        private final java.util.List<Double> settled = new java.util.ArrayList<>();

        private Fixture() {
            battle.addDamageObserver(new Battle.DamageObserver() {
                @Override
                public void onDamageSettled(Damage damage, double applied, double hpLoss, double shieldAbsorbed,
                                            boolean died) {
                    if (damage.getType() == com.laosun.aluminium.enums.DamageType.ADDITIONAL) {
                        settled.add(applied);
                    }
                }
            });
            battle.startBattle();
        }

        /** The ultimate's own marker rule, fired as the ultimate would fire it. */
        private void mark() {
            battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(com.laosun.aluminium.enums.TriggerEvent.ULT_CAST, sushang, enemy, 0, 0));
        }

        private void castSkill() {
            settled.clear();
            battle.castImmediate(sushang.getSkills().get(SkillType.SKILL), sushang, List.of(enemy));
        }

        private int extraStacks() {
            return sushang.getBuffManager().stacksOf("剑势额外判定");
        }

        private int additionalInstances() {
            return settled.size();
        }

        private java.util.List<Double> additionalAmounts() {
            return java.util.List.copyOf(settled);
        }
    }
}
