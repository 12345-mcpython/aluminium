package com.laosun.aluminium.test.content.relics;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 神的瞬息欢愉 (relic set 329), 2-piece: 「使装备者的速度提高 6%。当装备者的欢愉度大于等于 40% 时，进入战斗时立刻使行动提前 40%。」
 *
 * <p><b>What the set needed.</b> Nothing new: the speed half is the data's own {@code properties}
 * ({@code SpeedAddedRatio 0.06}), and the conditional half is {@code BATTLE_READY} + the attribute gate
 * {@code self_attr:ELATION_DAMAGE_BOOST >= 0.4} + {@code ADVANCE 0.4}. It sat in {@code _unmodelled.json} for one
 * reason - "whether the panel carries 欢愉度 at the moment combat begins was not verified" - and that question was
 * answered when set 330 landed: its {@code self_attr:SPEED >= 145} gate reads 145.1, of which 6.9 is the suit's own
 * share, so relic stats are already in the panel by the opening hook.
 *
 * <p><b>Why {@code BATTLE_READY} and not {@code BATTLE_START}.</b> 欢愉度 is raised by other opening rules, and
 * {@code BATTLE_START} rules run in team order - a gate there would answer differently depending on where the raiser
 * stands. {@code BATTLE_READY} is the engine's second phase for that shape (precedent: cone 23046's skill-point cap).
 *
 * <p><b>How the 40% is measured.</b> As a ratio against the SAME suit-wearing board that does not meet the gate: the
 * suit's own 6% speed also shortens the wait, so a board without the suit would mix two effects into one number. Both
 * readings below wear the set; only 欢愉度 differs.
 */
public class Relic329GodsMomentTest {

    private static final int LEVEL = 80;
    private static final int RELIC_STAR = 5;
    private static final int RELIC_LEVEL = 15;
    private static final int SET = 329;
    private static final int WEARER = 1102;

    /** At exactly the stated 40%, the remaining wait becomes 60% of what it was. */
    @Test
    public void theWearerActsEarlyWhenHerElationReachesFortyPercent() {
        double untouched = remainingWithSuit(0.0);
        double gated = remainingWithSuit(0.4);

        Assertions.assertTrue(untouched > 0, "precondition: she has a wait to shorten");
        Assertions.assertEquals(0.6 * untouched, gated, 1e-6,
                "「行动提前 40%」 - ADVANCE skips 40% of the REMAINING wait: " + untouched + " -> " + gated);
    }

    /** One point below the stated threshold changes nothing. */
    @Test
    public void justBelowTheThresholdNothingHappens() {
        double untouched = remainingWithSuit(0.0);
        double below = remainingWithSuit(0.39);

        Assertions.assertEquals(untouched, below, 1e-9,
                "「大于等于 40%」 - 39% leaves the wait untouched: " + untouched + " vs " + below);
    }

    /** 「使装备者的速度提高 6%」 is the 2-piece {@code properties} entry, not a rule. */
    @Test
    public void theTwoPieceSpeedIsSixPercentOfTheBase() {
        double bare = CharacterFactory.create(WEARER, LEVEL).getAttribute(AttributeType.SPEED).get();
        double suited = CharacterFactory.create(WEARER, LEVEL, true, null,
                RelicFactory.suit(SET, RELIC_STAR, RELIC_LEVEL)).getAttribute(AttributeType.SPEED).get();

        Assertions.assertEquals(0.06 * 115.0, suited - bare, 1e-6,
                "6% of her 115 data speed: " + bare + " -> " + suited);
    }

    // ==================================================================

    /**
     * The wearer's remaining wait at the opening, with the set worn and 欢愉度 set to {@code elation}.
     *
     * <p>The attribute is added AFTER the {@code Battle} is built: assembly recomputes the panel, and the value has to
     * be in place before {@code startBattle} fires {@code BATTLE_READY}.
     */
    private static double remainingWithSuit(double elation) {
        Character hero = CharacterFactory.create(WEARER, LEVEL, true, null,
                RelicFactory.suit(SET, RELIC_STAR, RELIC_LEVEL));
        Battle battle = new Battle(new ArrayList<>(List.of(hero)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        if (elation != 0) {
            hero.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).addModifier(DoubleValue.Modifier.pure(elation));
        }
        battle.startBattle();
        battle.processRequests();
        return battle.queue().snapshot().stream()
                .filter(signal -> signal.getCanHit() == hero)
                .findFirst()
                .map(battle.queue()::getTimeRemaining)
                .orElseThrow(() -> new AssertionError("the wearer is not on the action bar"));
    }
}
