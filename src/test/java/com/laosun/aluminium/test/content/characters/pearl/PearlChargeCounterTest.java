package com.laosun.aluminium.test.content.characters.pearl;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Character 1503 (Pearl): 充能 - a counter that IS a state's duration, not a number of turns.
 *
 * <p>「【深度学习】具有 3 点充能。真珠施放强化普攻时消耗 1 点充能，行动结束后若充能不足则【深度学习】结束。」
 *
 * <p>⚠ Replaces a registered approximation. The three charges used to be written as {@code turns: 3} on the state, because
 * the engine had no way to say "this lasts N USES" - and turns and uses are not the same thing: she may take three turns
 * without ever using the enhanced basic, or use it three times in one. The counter is the document's own words.
 *
 * <p>Four readings, and each fails separately: the Ultimate fills it to three; ONE enhanced basic removes exactly one;
 * the state is still there while charges remain; and it is gone once they do not. The reading that would pass for a
 * `turns` counter is the last one, so the middle two are what pin the model.
 */
public class PearlChargeCounterTest {
    private static final int PEARL = 1503;
    private static final int ELATION = 1502;
    private static final int HUNTER = 1204;
    private static final String CHARGE = "充能";
    private static final String DEEP = "深度学习";

    /**
     * The Ultimate fills the counter to its declared three.
     */
    @Test
    public void theUltimateFillsTheCounterToThree() {
        Battle battle = newBattle(HUNTER);
        Character pearl = battle.characters().get(0);
        castUltimate(battle, pearl);
        System.out.println("[pearl_charge] after the Ultimate: charges=" + pearl.getResources().value(CHARGE)
                + " deep=" + pearl.getBuffManager().hasState(DEEP));
        Assertions.assertEquals(3, pearl.getResources().value(CHARGE), "「具有 3 点充能」");
        Assertions.assertTrue(pearl.getBuffManager().hasState(DEEP), "and the state it pays for is on");
    }

    /**
     * One enhanced basic spends exactly one charge - not all three, and not none.
     */
    @Test
    public void oneEnhancedBasicSpendsExactlyOneCharge() {
        Battle battle = newBattle(HUNTER);
        Character pearl = battle.characters().get(0);
        castUltimate(battle, pearl);
        attack(battle, pearl);
        int afterOne = pearl.getResources().value(CHARGE);
        attack(battle, pearl);
        int afterTwo = pearl.getResources().value(CHARGE);
        System.out.println("[pearl_charge] charges after 1 / 2 enhanced basics: " + afterOne + " / " + afterTwo);
        Assertions.assertEquals(2, afterOne, "one use, one charge");
        Assertions.assertEquals(1, afterTwo, "and another use spends another");
    }

    /**
     * While a charge is left the state stands, and when the last one is spent the state is gone.
     */
    @Test
    public void theStateEndsWithTheLastCharge() {
        Battle battle = newBattle(HUNTER);
        Character pearl = battle.characters().get(0);
        castUltimate(battle, pearl);

        attack(battle, pearl);
        boolean afterOne = pearl.getBuffManager().hasState(DEEP);
        attack(battle, pearl);
        boolean afterTwo = pearl.getBuffManager().hasState(DEEP);
        attack(battle, pearl);
        int charges = pearl.getResources().value(CHARGE);
        boolean afterThree = pearl.getBuffManager().hasState(DEEP);
        System.out.println("[pearl_charge] 深度学习 after 1 / 2 / 3 enhanced basics: " + afterOne + " / " + afterTwo
                + " / " + afterThree + " (charges " + charges + ")");

        Assertions.assertTrue(afterOne, "two charges left, so it lasts");
        Assertions.assertTrue(afterTwo, "one left, so it still lasts");
        Assertions.assertEquals(0, charges, "and the third spends the last one");
        Assertions.assertFalse(afterThree, "「行动结束后若充能不足则【深度学习】结束」");
    }

    /**
     * Re-entering the state RESTORES the counter to three rather than adding three to what is left.
     *
     * <p>This is the reading that makes `to_max` the right spelling, and it has to reach the Ultimate with charges
     * ALREADY SPENT: from empty, "restore to three" and "add three" give the same answer, so a case that re-entered
     * first would pin nothing. Measured - the first version of this case did exactly that and the mutation that removes
     * `to_max` survived it.
     */
    @Test
    public void reEnteringRestoresToThreeAndDoesNotAddThree() {
        Battle battle = newBattle(HUNTER);
        Character pearl = battle.characters().get(0);
        castUltimate(battle, pearl);
        attack(battle, pearl);
        attack(battle, pearl);
        int spentTwoLeft = pearl.getResources().value(CHARGE);
        // The state is still up with one charge, so this must be the "already there" case the field exists for.
        boolean stillDeep = pearl.getBuffManager().hasState(DEEP);
        pearl.setCurrentEnergy(pearl.getMaxEnergy());
        castUltimate(battle, pearl);
        int afterReEntry = pearl.getResources().value(CHARGE);
        System.out.println("[pearl_charge] after spending two: " + spentTwoLeft + " (state up: " + stillDeep
                + ") ; after re-entering the state: " + afterReEntry);

        Assertions.assertEquals(1, spentTwoLeft, "precondition: two charges were spent");
        Assertions.assertTrue(stillDeep, "precondition: the state is still up, so its counter is not empty");
        Assertions.assertEquals(3, afterReEntry,
                "re-entering restores the FULL three (1 + 3 = 4 would mean it merely added three)");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * Casts the Ultimate at the given ally, which is what installs the enhanced basic and fills the counter.
     *
     * <p>Note: Through {@code castUltra}, the engine's own entry point - {@code requestSkill} only QUEUES a cast, and
     * {@code fireTriggers(ULT_CAST)} is not a cast at all. Measured: both of those left the counter at 0, which read as
     * "the rule does not fire" when in fact no Ultimate had been cast. The energy is raised first because {@code
     * castUltra} rightly refuses one she cannot afford.
     */
    private static void castUltimate(Battle battle, Character pearl) {
        pearl.setCurrentEnergy(pearl.getMaxEnergy());
        battle.castUltra(pearl, List.of(battle.characters().get(1)));
        battle.processRequests();
    }

    /**
     * Drives one enhanced basic through the engine's own skill request path, so the cast events fire.
     */
    private static void attack(Battle battle, Character pearl) {
        Enemy victim = battle.enemyUnits().getFirst();
        battle.requestSkill(pearl.getSkills().get(SkillType.COMMON), pearl, List.of(victim));
        battle.processRequests();
        // Note: ATTACK_FINISHED is fired HERE because the fixture drives the attack directly rather than through a turn,
        // so the move that would normally fire it never runs. The rule that ends the state hangs on this event, and a
        // fixture that skipped it would read 'the state never ends' for a reason that is about the fixture.
        battle.fireTriggers(TriggerEvent.ATTACK_FINISHED, pearl, victim, 1, 0);
        battle.processRequests();
    }

    private static Battle newBattle(int archetype) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(archetype, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
