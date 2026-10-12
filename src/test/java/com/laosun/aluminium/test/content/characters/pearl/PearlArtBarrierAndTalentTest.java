package com.laosun.aluminium.test.content.characters.pearl;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Character 1503 (Pearl): the art barrier only lands on an Elation archetype, and the talent's reduction tracks the HP threshold.
 */
public class PearlArtBarrierAndTalentTest {
    private static final int PEARL = 1503;
    private static final int ELATION = 1502;   // Yao Guang, Elation
    private static final int HUNTER = 1204;
    private static final String BARRIER = "\u827a\u672f\u58c1\u5792";
    private static final String GRIT = "\u541e\u7eb3\u6c99\u783e";

    /**
     * The art barrier lands on an Elation archetype and on nobody else.
     *
     * <p>Note: This case used to assert the 90-energy refund as well. That rule has been WITHDRAWN: the corpus says
     * "使其【下一次】施放终结技时" - the NEXT time - and the rule fired on every ultimate the holder cast, because
     * "next" needs the engine to take a state off the unit that CAUSED the event. The author rejected the
     * approximation, so the rule is gone rather than sitting in the tree looking finished. The barrier's own half is
     * exact and stays.
     */
    @Test
    public void theBarrierLandsOnlyOnAnElationArchetype() {
        Battle battle = newBattle();
        Character pearl = battle.characters.get(0);
        Character elation = battle.characters.get(1);
        Character hunter = battle.characters.get(2);

        battle.fireTriggers(TriggerEvent.ULT_CAST, pearl, elation, 0, 0);
        battle.fireTriggers(TriggerEvent.ULT_CAST, pearl, hunter, 0, 0);
        boolean onElation = elation.getBuffManager().hasState(BARRIER);
        boolean onHunter = hunter.getBuffManager().hasState(BARRIER);
        System.out.println("[pearl_barrier] on the Elation ally = " + onElation + " ; on the Hunt ally = " + onHunter);

        Assertions.assertTrue(onElation, "an Elation-Path archetype gets the barrier");
        Assertions.assertFalse(onHunter, "a Hunt ally must NOT get it (the rule gates on target has_path)");
    }

    @Test
    public void theTalentReductionFollowsTheHalfHpThreshold() {
        Battle battle = newBattle();
        Character ally = battle.characters.get(2);

        ally.takeDamage(ally.getMaxHp() * 0.8);
        battle.fireTriggers(TriggerEvent.HP_LOST, ally, ally, 0, 0);
        boolean below = ally.getBuffManager().hasState(GRIT);

        ally.heal(ally.getMaxHp());
        battle.fireTriggers(TriggerEvent.HEALED, ally, ally, 0, 0);
        boolean above = ally.getBuffManager().hasState(GRIT);
        System.out.println("[pearl_talent] reduction below half = " + below + " ; after healing above half = " + above);

        Assertions.assertTrue(below, "below 50% HP the ally carries the damage reduction");
        Assertions.assertFalse(above, "and healing back above 50% removes it");
    }

    /**
     * The talent's NUMBER, not just its state: below half HP the ally takes 30% less.
     *
     * <p>Note: Why this case exists. The state assertion above passes for a buff carrying the right NAME and any
     * number at all, and a mutation of the shipped `-0.15` to `-0.5` was measured to leave the whole class GREEN. So
     * the magnitude is pinned here, on the engine's own settlement entrance ({@code Battle.applyDamage}, the single
     * outlet - a hand-fired {@code HP_LOST} never reaches a rule), as a RATIO of two readings taken on the same ally
     * in one battle: before the talent is up, and after.
     *
     * <p>Note: The figure is 30%, not the corpus's headline 15% - that one is the Lv1 row, and the author settled the
     * level question in favour of Lv10 (#4 is 0.15 at Lv1 and 0.375 at Lv15, so 0.30 at Lv10), which is the
     * convention every other number in the file already uses.
     */
    @Test
    public void theTalentsReductionIsThirtyPercent() {
        Battle battle = newBattle();
        Character ally = battle.characters.get(2);
        Enemy attacker = battle.enemyUnits().getFirst();

        // The talent's own gate is "at or below half HP", and its event is HP_LOST - so the ally has to really be
        // brought below half by a SETTLED hit before the rule can fire.
        // Note: The setup goes through `Battle.applyDamage` too, because that is the only entrance that broadcasts
        // HP_LOST. Driving it with `takeDamage` was measured to leave the rule silent even with the ally at 20% HP
        // (read: `reduction up = false`), which is the same trap HANDOFF discipline 6 records - that call changes HP
        // and nothing else. The instance is stated as a multiple of the reading's so the zones are the same for both.
        // Measured: the setup lands the ally at 31.3% of Max HP, below the half line and with room for the reading.
        double readingInstance = ally.getMaxHp() * 0.1;
        battle.applyDamage(ally, new Damage(attacker, ally, DamageElement.PHYSICAL, DamageType.NORMAL,
                readingInstance * 10));
        Assertions.assertTrue(ally.getBuffManager().hasState(GRIT),
                "precondition: the ally was brought below half HP, so the talent is up");
        Assertions.assertTrue(ally.getCurrentHp() / ally.getMaxHp() <= 0.5,
                "precondition: the ally really is below half (" + ally.getCurrentHp() / ally.getMaxHp() + ")");

        double withTalent = hurtBy(battle, attacker, ally, readingInstance);
        ally.getBuffManager().removeState(GRIT);
        battle.fireTriggers(TriggerEvent.HEALED, ally, ally, 0, 0);
        Assertions.assertFalse(ally.getBuffManager().hasState(GRIT),
                "precondition: the reduction is off for the control reading");
        double without = hurtBy(battle, attacker, ally, readingInstance);

        System.out.println("[pearl_talent] one hit takes " + withTalent + " with the talent and " + without
                + " without it ; ratio = " + (withTalent / without));

        Assertions.assertTrue(without > 0, "the control hit must land (" + without + ")");
        // Note: The zone is MULTIPLICATIVE, not a division - `ReductionArea.add(r)` calls `multiplyPercent(-r)`, so the
        // hit is scaled by (1 - 0.30) = 0.70. Measured, and read off the source after the numbers disagreed with the
        // first expectation twice: the earlier `-0.15` version asserted 1/1.15 = 0.8696 and passed only because the
        // tolerance was 0.02 against a measured 0.85 (a 1.9% error, just inside it). This case now states the engine's
        // own relationship, at a magnitude whose error cannot hide.
        Assertions.assertEquals(1 - 0.30, withTalent / without, 1e-6,
                "「受到的伤害降低 30%」（Lv10）: the hit is multiplied by 1 - 0.30");
    }

    /**
     * One settled hit of the given instance value by {@code attacker} on {@code victim}, returned as the HP lost.
     *
     * <p>Both readings pass the SAME instance value on purpose: the outcome is then a pure ratio, with the defence,
     * resistance and level zones cancelling - which is what makes "the reduction is 15%" assertable without
     * re-deriving the engine's whole damage formula in the test.
     */
    private static double hurtBy(Battle battle, Enemy attacker, Character victim, double instance) {
        double before = victim.getCurrentHp();
        battle.applyDamage(victim, new Damage(attacker, victim, DamageElement.PHYSICAL, DamageType.NORMAL, instance));
        return before - victim.getCurrentHp();
    }

    private static Battle newBattle() {
        Battle battle = new Battle(List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ELATION, 80),
                CharacterFactory.create(HUNTER, 80)), List.of(EnemyFactory.create(1002011, 100, 1)), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
