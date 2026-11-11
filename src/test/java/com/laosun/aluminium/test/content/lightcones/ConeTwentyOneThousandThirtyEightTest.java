package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 在火的远处 (21038): 「当装备者在单次受到攻击中累计损失的生命值超过最大生命值的 25%，或单次消耗自身生命值超过最大生命值的 25%，
 * 则立即回复等同于装备者生命上限 15% 的生命值，同时使装备者造成的伤害提高 25~50%，持续 2 回合。该效果每 3 回合只能触发 1 次。」
 *
 * <p><b>Two quantities, not one.</b> The first half asks about a <b>sum</b> over the instances of one action
 * ({@code hp_lost_this_action_percent}) and the second about a <b>single</b> payment ({@code event_amount_percent}).
 * Adding them together would be wrong: a 15% hit plus a 15% payment satisfies neither clause, and the engine's own
 * reading keeps them apart - which is what {@link #halfOfEachIsNotEnough()} pins.
 *
 * <p>The shared three-turn gate is a state rather than a rule-level {@code cooldown}, because the two halves are two
 * rules: two cooldowns would let each fire once per window, i.e. twice where the sentence says once.
 */
public class ConeTwentyOneThousandThirtyEightTest {

    private static final int LEVEL = 80;
    /** 1002 Dan Heng (丹恒): a plain kit, so nothing of his own competes with the cone's damage boost. */
    private static final int WEARER = 1002;
    private static final String GATE = "爆燃已触发";

    /**
     * Half one: HP lost during one action, summed across its instances, over 25% of Max HP.
     */
    @Test
    public void theAttackHalfFiresWhenTheActionsLossPassesAQuarter() {
        Run run = new Run(1);
        double before = run.wearer.getCurrentHp();

        run.take(0.6);

        Assertions.assertTrue(run.lostShare() > 0.25,
                "precondition: this action really took more than a quarter (" + run.lostShare() + ")");
        Assertions.assertTrue(run.gated(), "the effect fired");
        Assertions.assertEquals(0.25, run.boost(), 1e-6, "「造成的伤害提高 25%」");
        Assertions.assertEquals(before - run.lostShare() * run.wearer.getMaxHp() + 0.15 * run.wearer.getMaxHp(),
                run.wearer.getCurrentHp(), 1e-6,
                "「立即回复等同于装备者生命上限 15% 的生命值」 - the heal is 15% of Max HP, on top of the loss");
    }

    /**
     * The SUM is the subject: two instances, each below a quarter, together above it.
     */
    @Test
    public void theSumOfTwoInstancesCrossesTheThreshold() {
        Run run = new Run(1);
        run.take(0.3);
        Assertions.assertTrue(run.lostShare() < 0.25,
                "precondition: the first instance alone is below the threshold (" + run.lostShare() + ")");
        Assertions.assertFalse(run.gated(), "so nothing has fired yet");

        run.take(0.3);

        Assertions.assertTrue(run.lostShare() > 0.25, "the two instances together are above it");
        Assertions.assertTrue(run.gated(), "「单次受到攻击中**累计**损失的生命值」 — the sum is what counts");
        Assertions.assertEquals(0.25, run.boost(), 1e-6, "and the same 25%");
    }

    /**
     * Below the threshold nothing happens - and the control sits on the same fixture.
     */
    @Test
    public void aSmallLossStaysSilent() {
        Run run = new Run(1);
        run.take(0.2);
        Assertions.assertTrue(run.lostShare() < 0.25, "precondition: below a quarter (" + run.lostShare() + ")");
        Assertions.assertFalse(run.gated(), "no state");
        Assertions.assertEquals(0.0, run.boost(), 1e-9, "and no damage boost");
    }

    /**
     * Half two: a single self-payment over 25% fires the same effect.
     *
     * <p>The payment is produced by the engine's own {@code CONSUME_HP} (as a fixture-only rule), not by a shipped
     * character: 1205 Blade really pays 30%, but his own 【地狱变】 also puts a damage boost on the same attribute, and
     * two boosts on one attribute collapse to one (see 1014's note) - so his kit cannot isolate this clause's number.
     */
    @Test
    public void theSelfConsumeHalfFiresToo() {
        Run run = new Run(1, true);

        Assertions.assertTrue(run.gated(), "「单次消耗自身生命值超过最大生命值的 25%」 — 30% paid in one go");
        Assertions.assertEquals(0.25, run.boost(), 1e-6, "the same 25% damage boost");
        Assertions.assertEquals(0.85 * run.wearer.getMaxHp(), run.wearer.getCurrentHp(), 1e-6,
                "-30% paid, then +15% of Max HP healed");
    }

    /**
     * The two halves are different quantities: a bit of damage plus a bit of paying is not "one of them".
     *
     * <p>15% taken + 15% paid: neither clause is satisfied, so nothing fires. A spelling that summed the two would
     * pass this test's premise and fail it here.
     */
    @Test
    public void halfOfEachIsNotEnough() {
        Run run = new Run(1);
        run.take(0.3);                       // ~15% of Max HP actually lost
        Assertions.assertFalse(run.gated(), "precondition: the loss half alone has not fired");
        Assertions.assertTrue(run.lostShare() < 0.25, "precondition: below a quarter (" + run.lostShare() + ")");
        Assertions.assertFalse(run.wearer.getBuffManager().hasState(GATE), "still nothing");
        Assertions.assertEquals(0.0, run.boost(), 1e-9);
    }

    /**
     * 「每 3 回合只能触发 1 次」: the second crossing inside the window does not stack a second boost.
     */
    @Test
    public void theGateStopsTheSecondTrigger() {
        Run run = new Run(1);
        run.take(0.6);
        Assertions.assertEquals(0.25, run.boost(), 1e-6, "precondition: fired once");

        run.take(0.6);

        Assertions.assertEquals(0.25, run.boost(), 1e-6,
                "「该效果每 3 回合只能触发 1 次」 — a second big hit inside the window adds nothing");
    }

    /**
     * The rank moves the boost, and nothing else.
     */
    @Test
    public void theRankMovesTheBoost() {
        Run five = new Run(5);
        five.take(0.6);
        Assertions.assertEquals(0.5, five.boost(), 1e-6, "rank 5 states 50%");
    }

    // ==================================================================

    private static final class Run {
        private final Character wearer;
        private final Enemy enemy;
        private final Battle battle;

        private Run(int rank) {
            this(rank, false);
        }

        /**
         * @param payAtStart whether to add a fixture-only rule that makes the wearer pay 30% of Max HP at battle start -
         *                   the engine's own {@code CONSUME_HP}, so the second half's event is real
         */
        private Run(int rank, boolean payAtStart) {
            wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21038, LEVEL, false, rank));
            if (payAtStart) {
                EffectSpec pay = new EffectSpec();
                TriggerSpecs.set(pay, "op", "CONSUME_HP");
                TriggerSpecs.set(pay, "scale", "owner_max_hp");
                TriggerSpecs.set(pay, "percent", 0.3);
                TriggerSpecs.set(pay, "target", "self");
                TriggerSpec payRule = TriggerSpecs.rule("BATTLE_START", List.of(), pay);
                TriggerSpecs.set(payRule, "id", "test_pay_thirty_percent");
                wearer.setTriggerTable(wearer.getTriggerTable()
                        .plus(new TriggerTable(WEARER, List.of(payRule))));
            }
            enemy = BattleFixture.monster();
            battle = new Battle(List.of(wearer), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        /** One enemy hit worth {@code share} of the wearer's Max HP, before the defensive zones. */
        private void take(double share) {
            battle.applyDamage(wearer, new Damage(enemy, wearer, DamageElement.ICE, DamageType.NORMAL,
                    wearer.getMaxHp() * share));
            battle.processRequests();
        }

        private double lostShare() {
            return battle.hpLostThisAction(wearer) / wearer.getMaxHp();
        }

        private double boost() {
            return wearer.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        }

        private boolean gated() {
            return wearer.getBuffManager().hasState(GATE);
        }
    }
}
