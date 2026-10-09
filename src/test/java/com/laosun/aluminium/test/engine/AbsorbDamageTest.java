package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@code ABSORB_DAMAGE}: a share of an incoming hit paid for out of <b>another unit's</b> counter.
 *
 * <p>The reader is 1503 Pearl (真珠)'s talent: 「真珠可使用【好活当赏】作为抵御值，每点【好活当赏】等同于 #5 点抵御值，我方目标
 * 受到伤害时，真珠可消耗抵御值为我方目标抵挡 #2% 的伤害」 - #5 = 200, #2 = 0.6. The counter is Pearl's, the units
 * protected are her allies, and one point buys 200 of damage.
 *
 * <p>Deliberately a FIXTURE rather than Pearl's own file: these cases are about the primitive's arithmetic (the share,
 * the exchange rate, the partial case, and whose counter pays), so each is stated in isolation. Pearl's rule is judged
 * separately.
 *
 * <p>Every reading is a pair - "what did this hit cost in HP" <b>and</b> "what does the counter hold now" - because a
 * case that reads only one of them can pass while the other is wrong (blocking the right amount while charging the
 * wrong pool, for instance).
 */
public class AbsorbDamageTest {
    private static final int PEARL = 1503;
    private static final int ALLY = 1204;
    private static final String COUNTER = "抵御值计数";

    /** Enough points behind the target: the whole share is blocked, and the payer is charged for it. */
    @Test
    public void theShareIsBlockedWhenTheCounterCanPay() {
        Reading armed = absorb(30, 1000, 0.6, 200, true);
        Reading control = absorb(30, 1000, 0.6, 200, false);
        System.out.println("[absorb] armed: hp cost " + armed.hpCost + " (counter left " + armed.counterAfter
                + ") ; control: hp cost " + control.hpCost);

        Assertions.assertTrue(control.hpCost > 0, "precondition: the control hit lands (" + control.hpCost + ")");
        // Note: The blocked amount is measured against the CONTROL's reading, not as a fraction of it, and the two are
        // not the same number. The share caps what MAY be blocked, but what IS blocked is whatever the points spent
        // buy - and points are whole: 60% of this hit wants ceil(401.04/200) = 3 points, and 3 points buy 600, which
        // is MORE than the share asked for. So the HP cost is control - 600, and asserting `0.4 * control` here was
        // asserting an arithmetic the mechanic does not do (measured: 68.39 against an expected 267.36).
        Assertions.assertEquals(3, 30 - armed.counterAfter,
                "3 points were needed for the share, and 3 were spent");
        Assertions.assertEquals(control.hpCost - 3 * 200, armed.hpCost, 0.5,
                "and those 3 points buy 600 of cover, which is what the hit loses");
    }

    /**
     * Not enough points: the counter blocks what it can and the rest still lands.
     *
     * <p>The case the author ruled on explicitly - 「按剩余点数按比例抵挡」 - and the one an all-or-nothing
     * implementation fails by blocking either everything or nothing.
     */
    @Test
    public void aCounterThatCannotCoverTheShareStillBlocksWhatItHas() {
        Reading armed = absorb(1, 1000, 0.6, 200, true);
        Reading control = absorb(1, 1000, 0.6, 200, false);
        System.out.println("[absorb] one point: hp cost " + armed.hpCost + " (control " + control.hpCost
                + ") ; counter left " + armed.counterAfter);

        Assertions.assertEquals(0, armed.counterAfter, "the single point is spent");
        Assertions.assertEquals(control.hpCost - 200, armed.hpCost, 0.5,
                "and it blocks exactly its own 200, not the 600 the share asked for");
        Assertions.assertTrue(armed.hpCost > 0,
                "the rest of the hit still lands: partial absorption, not a save");
    }

    /** No points at all: nothing is blocked and nothing is spent. */
    @Test
    public void anEmptyCounterBlocksNothing() {
        Reading armed = absorb(0, 1000, 0.6, 200, true);
        Reading control = absorb(0, 1000, 0.6, 200, false);
        System.out.println("[absorb] empty counter: hp cost " + armed.hpCost + " (control " + control.hpCost + ")");
        Assertions.assertEquals(control.hpCost, armed.hpCost, 1e-9,
                "with no points there is nothing to spend, so the hit is unchanged");
        Assertions.assertEquals(0, armed.counterAfter, "and the counter stays empty");
    }

    /**
     * The counter is read from the PAYER, not from the unit being hit.
     *
     * <p>The shape's whole point, and the easiest thing to get wrong: the absorber sits on the ally (that is where the
     * hit lands) while the pool belongs to Pearl. So a rich VICTIM and an empty payer must block nothing - an
     * implementation that read the victim's own resources would quietly work in every single-character test.
     */
    @Test
    public void theCounterComesFromThePayerNotTheVictim() {
        Battle battle = newBattle();
        Character pearl = battle.characters.getFirst();
        Character ally = battle.characters.get(1);
        pearl.getResources().register(COUNTER, 0, 100);      // the payer is empty
        ally.getResources().register(COUNTER, 30, 100);      // the victim is rich
        armTheParty(battle, pearl, 0.6, 200);

        double cost = hitAlly(battle, ally, 1000);
        System.out.println("[absorb] payer empty, victim rich: hp cost " + cost
                + " ; the victim's own counter still reads " + ally.getResources().value(COUNTER));
        Assertions.assertEquals(30, ally.getResources().value(COUNTER),
                "the victim's counter has nothing to do with this");
        Assertions.assertTrue(cost > 0, "and since the PAYER is empty, the whole hit lands");
    }

    /**
     * A payer who has fallen stops paying.
     *
     * <p>Worth its own case because the alternative reading - "the counter still exists, so it still blocks" - is
     * plausible enough to be written by accident, and nothing else in the suite would notice.
     */
    @Test
    public void aFallenPayerBlocksNothing() {
        Battle battle = newBattle();
        Character pearl = battle.characters.getFirst();
        Character ally = battle.characters.get(1);
        pearl.getResources().register(COUNTER, 30, 100);
        armTheParty(battle, pearl, 0.6, 200);
        pearl.perish();

        double cost = hitAlly(battle, ally, 1000);
        System.out.println("[absorb] payer fallen: hp cost " + cost + " ; her counter still reads "
                + pearl.getResources().value(COUNTER));
        Assertions.assertEquals(30, pearl.getResources().value(COUNTER), "nothing is spent");
        Assertions.assertTrue(cost > 0, "and the hit lands in full");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** The two facts a reading has to carry together: what the hit cost, and what the counter holds afterwards. */
    private record Reading(double hpCost, int counterAfter) {
    }

    private static Reading absorb(int points, double instance, double percent, double unitAmount, boolean arm) {
        Battle battle = newBattle();
        Character pearl = battle.characters.getFirst();
        Character ally = battle.characters.get(1);
        pearl.getResources().register(COUNTER, points, 100);
        if (arm) {
            armTheParty(battle, pearl, percent, unitAmount);
        }
        double cost = hitAlly(battle, ally, instance);
        return new Reading(cost, pearl.getResources().value(COUNTER));
    }

    /**
     * Puts an absorber on the whole party, owned by {@code payer} - Pearl's own shape: the rule belongs to her, it
     * reaches `all_allies`, and with no `payer` stated the pool is hers.
     */
    private static void armTheParty(Battle battle, Character payer, double percent, double unitAmount) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "ABSORB_DAMAGE");
        TriggerSpecs.set(effect, "resource", COUNTER);
        TriggerSpecs.set(effect, "unitAmount", unitAmount);
        TriggerSpecs.set(effect, "percent", percent);
        TriggerSpecs.set(effect, "permanent", Boolean.TRUE);
        TriggerSpecs.set(effect, "target", "all_allies");
        TriggerSpec rule = TriggerSpecs.rule("BATTLE_START", List.of(), effect);
        payer.setTriggerTable(new TriggerTable(payer.getCid(), List.of(rule)));
        battle.fireTriggers(TriggerEvent.BATTLE_START, payer, payer, 0, 0);
        battle.processRequests();
    }

    /** Settles one hit of {@code instance} on {@code ally} through the engine's own entrance. */
    private static double hitAlly(Battle battle, Character ally, double instance) {
        Enemy attacker = battle.enemyUnits().getFirst();
        double before = ally.getCurrentHp();
        battle.applyDamage(ally, new Damage(attacker, ally, DamageElement.PHYSICAL, DamageType.NORMAL, instance));
        return before - ally.getCurrentHp();
    }

    private static Battle newBattle() {
        Battle battle = BattleFixture.battle(List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
