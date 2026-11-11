package com.laosun.aluminium.test.content.characters.moze;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1223 貊泽's 【充能】: 「…并获得 9 点充能」 / 「我方目标攻击【猎物】后…并消耗 1 点充能。每消耗 3 点充能…发动 1 次追加攻击…当充能为 0 时，解除目标【猎物】状态」.
 *
 * <p><b>Why it took until 2026-11-09.</b> The file said 「充能没有上限 ⇒ 不能声明为资源 ⇒ 与他相关的每一句都无从表达」. That conclusion
 * was wrong: {@code ResourceSpec.max} is an {@code Integer}, and this library already spells "no ceiling" as
 * {@code 2147483647} (1408's 【毁伤】 does exactly that). The whole mechanic is expressible, and this judge is what makes the
 * claim checkable: nine charges, one per hit, a follow-up on every third, and the mark leaving at zero.
 */
public class MozeChargeTest {
    private static final int MOZE = 1223;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final String CHARGE = "充能";
    private static final String PREY = "猎物";

    /**
     * The Skill grants nine, and its OWN attack already spends one: 「我方目标攻击【猎物】后…并消耗 1 点充能」 fires on the same
     * cast (the mark is up by the time the hit lands), so the counter starts at 8 with 1 already consumed.
     */
    @Test
    public void theSkillGrantsNineChargesAndEveryHitSpendsOne() {
        Fixture f = new Fixture();
        Assertions.assertEquals(8, f.charge(),
                "「并获得 9 点充能」 minus the one the Skill's own hit spends");
        f.allyHits();
        Assertions.assertEquals(7, f.charge(), "「我方目标攻击【猎物】后…并消耗 1 点充能」");
        f.allyHits();
        Assertions.assertEquals(6, f.charge(), "one per hit, not one per turn");
    }

    /** 「每消耗 3 点充能…发动 1 次追加攻击」 - the third, sixth and ninth spends carry an extra 160% instance. */
    @Test
    public void everyThirdChargeLaunchesTheFollowUp() {
        Fixture f = new Fixture();
        List<Double> hits = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            hits.add(f.allyHits());
        }
        System.out.println("[1223] per hit: " + hits + " charge=" + f.charge());
        double plain = hits.get(0);
        for (int i : new int[]{0, 2, 3, 5, 6}) {
            Assertions.assertEquals(plain, hits.get(i), plain * 1e-9,
                    "hit " + (i + 1) + " is a plain one (the follow-up rides every THIRD spend, and the Skill's own hit was the first)");
        }
        for (int i : new int[]{1, 4}) {
            Assertions.assertTrue(hits.get(i) > plain * 1.5,
                    "hit " + (i + 1) + " also carries the 160% follow-up (" + hits.get(i) + " vs " + plain + ")");
        }
    }

    /** 「当充能为 0 时，解除目标【猎物】状态」 - the ninth spend empties the counter and the mark goes with it. */
    @Test
    public void theMarkLeavesWhenTheChargeReachesZero() {
        Fixture f = new Fixture();
        for (int i = 0; i < 7; i++) {
            f.allyHits();
        }
        Assertions.assertEquals(1, f.charge(), "precondition: one charge left");
        Assertions.assertTrue(f.enemy.getBuffManager().hasState(PREY), "and the mark is still there");

        f.allyHits();
        Assertions.assertEquals(0, f.charge(), "the eighth hit spends the last charge");
        Assertions.assertFalse(f.enemy.getBuffManager().hasState(PREY),
                "「当充能为 0 时，解除目标【猎物】状态」");
    }

    /**
     * 「…并**重置计入发动追加攻击所需的充能点数**」 - the 3-point tally restarts for the NEXT prey.
     *
     * <p>If the reset did nothing, the counter would still hold its old layers and the very first hit on the new prey
     * would already launch the follow-up; this case pins the third-hit timing after a reset.
     */
    @Test
    public void theFollowUpCounterRestartsForTheNextPrey() {
        Fixture f = new Fixture();
        for (int i = 0; i < 8; i++) {
            f.allyHits();
        }
        Assertions.assertEquals(0, f.charge(), "precondition: the first prey was emptied");

        f.remarkThePrey();
        double plain = f.allyHits();
        double second = f.allyHits();
        double third = f.allyHits();
        System.out.println("[1223] after reset: first=" + plain + " second=" + second + " third=" + third);
        // The re-cast is itself the new prey FIRST spend (the Skill own hit), so the follow-up is due on the 2nd ally
        // hit: 1 + the cast's = 3. Measured 213 / 1349 / 213 - the tally really did restart.
        Assertions.assertTrue(second > plain * 1.5, "the follow-up lands on the 2nd ally hit (" + second + ")");
        Assertions.assertEquals(plain, third, 1e-6, "and the 3rd is plain again");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character moze;
        private final Character ally;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private Fixture() {
            moze = CharacterFactory.create(MOZE, LEVEL);
            ally = CharacterFactory.create(ALLY, LEVEL);
            enemy.setMaxStance(100_000);
            enemy.setStance(100_000);
            battle = new Battle(new ArrayList<>(List.of(moze, ally)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            battle.processRequests();
            battle.castImmediate(moze.getSkills().get(SkillType.SKILL), moze, List.of(enemy));
            battle.processRequests();
            Assertions.assertTrue(enemy.getBuffManager().hasState(PREY), "precondition: the Skill marks the prey");
        }

        private int charge() {
            // 【充能】 is Moze's OWN counter (his unit store), not a party pool - the declaration sits on the unit, so reading
            // the party pool answers 0 and would have made this test look like "the gain did nothing".
            return moze.getResources().has(CHARGE) ? moze.getResources().value(CHARGE) : 0;
        }

        /** Casts the Skill again: a fresh prey and nine charges (the 「重置」 case). */
        private void remarkThePrey() {
            battle.castImmediate(moze.getSkills().get(SkillType.SKILL), moze, List.of(enemy));
            battle.processRequests();
        }
        /** The ALLY hits the prey; returns the HP it took. */
        private double allyHits() {
            double before = enemy.getCurrentHp();
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 1, 0));
            battle.processRequests();
            return before - enemy.getCurrentHp();
        }
    }
}
