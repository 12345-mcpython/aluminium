package com.laosun.aluminium.test.content.characters.blade;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1205 刃's Talent follow-up and the two halves of his Eidolon 6.
 *
 * <p><b>Why a criterion.</b> The follow-up has no cast of its own: its trigger is 「当充能叠加至上限时」, i.e. a COUNTER
 * reaching its cap, and its four effects (damage / heal / consume-all) hang off that one fact. Nothing else in the suite
 * would notice if the trigger fired one layer early or late, or if the charge was not emptied - the damage number would
 * still look plausible. So the cases below count the layers and the settlements on both sides of the cap, at E5 (cap 5)
 * and E6 (cap 4, 「充能层数上限降至 4 层」).
 *
 * <p>The E6 damage half is measured the same way as Eidolon 1: the follow-up's settled number at E6 minus its settled
 * number at E5 must equal <b>0.5 x Max HP</b> exactly, because the raise is an absolute amount added after every zone.
 */
public class BladeTalentFollowupTest {
    private static final int BLADE = 1205;
    private static final int LEVEL = 80;
    private static final String CHARGE = "充能";

    /**
     * E5: four layers are not the cap; the fifth pays one follow-up and empties the counter.
     */
    @Test
    public void theFifthLayerPaysTheFollowupAndConsumesEveryCharge() {
        Run run = new Run(5);
        run.hit(4);
        Assertions.assertEquals(4, run.blade.getBuffManager().stacksOf(CHARGE), "four layers, cap is five");
        Assertions.assertEquals(0.0, run.enemyLoss(), 1e-9, "「当充能叠加至上限时」 - not yet");

        run.hit(1);
        Assertions.assertTrue(run.enemyLoss() > 0, "the fifth layer is the cap, so the follow-up lands");
        Assertions.assertEquals(0, run.blade.getBuffManager().stacksOf(CHARGE),
                "「施放追加攻击后，消耗所有充能」");
    }

    /**
     * E6: 「充能的层数上限降低至 4 层」 - the cap, and the threshold that follows from it, are four.
     */
    @Test
    public void theSixthEidolonLowersTheChargeCapToFour() {
        Run run = new Run(6);
        run.hit(3);
        Assertions.assertEquals(3, run.blade.getBuffManager().stacksOf(CHARGE), "three layers, cap is four");
        Assertions.assertEquals(0.0, run.enemyLoss(), 1e-9, "not at the cap yet");

        run.hit(1);
        Assertions.assertTrue(run.enemyLoss() > 0, "the fourth layer is the cap at E6");
        Assertions.assertEquals(0, run.blade.getBuffManager().stacksOf(CHARGE),
                "and the follow-up still consumes every charge");

        // And the counter cannot hold five any more.
        run.hit(20);
        Assertions.assertTrue(run.blade.getBuffManager().stacksOf(CHARGE) <= 4,
                "「最多叠加 4 层」 - twenty more hits must never read 5");
    }

    /**
     * E6's damage half: 「天赋触发追加攻击造成的伤害额外提高，提高数值等同于刃 50% 的生命上限」.
     */
    @Test
    public void theSixthEidolonAddsHalfHisMaxHpToTheFollowup() {
        Run e5 = new Run(5);
        e5.hit(5);
        Run e6 = new Run(6);
        e6.hit(4);
        double delta = e6.enemyLoss() - e5.enemyLoss();
        // The follow-up is an AoE: ONE instance per victim, and each instance carries the same absolute raise.
        double expected = 0.5 * e6.blade.getMaxHp() * e6.enemies.size();
        System.out.println("[1205-E6] followup E5=" + e5.enemyLoss() + " E6=" + e6.enemyLoss()
                + " delta=" + delta + " expected=" + expected);
        Assertions.assertEquals(expected, delta, 1e-6,
                "the raise is 50% of Max HP per instance, added after the zones - no crit, no defence, no resistance");
    }

    /**
     * 「并回复等同于刃生命上限 25% 的生命值」.
     */
    @Test
    public void theFollowupHealsAQuarterOfHisMaxHp() {
        Run run = new Run(5);
        run.blade.takeDamage(run.blade.getMaxHp() * 0.6);
        double before = run.blade.getCurrentHp();
        run.hit(5);
        double healed = run.blade.getCurrentHp() - before;
        Assertions.assertEquals(0.25 * run.blade.getMaxHp(), healed, 1e-6,
                "the heal is 25% of his own Max HP (skills.json 120504 #3 = 0.25)");
    }

    // ==================================================================

    /** One battle: Blade at the named eidolon rank, three dummies, and a way to land N hits on him. */
    private static final class Run {
        private final Character blade;
        private final Enemy first;
        private final List<Enemy> enemies;
        private final Battle battle;
        private final double enemyHpBefore;

        Run(int eidolon) {
            blade = CharacterFactory.create(BLADE, LEVEL);
            blade.setEidolonRank(eidolon);
            first = BattleFixture.dummy();
            Enemy second = BattleFixture.dummy();
            Enemy third = BattleFixture.dummy();
            enemies = List.of(first, second, third);
            battle = new Battle(new ArrayList<>(List.of(blade)), new ArrayList<>(enemies),
                    BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            battle.processRequests();
            enemyHpBefore = enemyHp();
        }

        void hit(int times) {
            for (int i = 0; i < times; i++) {
                battle.fire(TriggerPayload.Simple.of(TriggerEvent.TAKING_HIT, enemies.getFirst(), blade, 0, 0));
            }
        }

        double enemyLoss() {
            return enemyHpBefore - enemyHp();
        }

        private double enemyHp() {
            double total = 0;
            for (Enemy enemy : enemies) {
                total += enemy.getCurrentHp();
            }
            return total;
        }
    }
}
