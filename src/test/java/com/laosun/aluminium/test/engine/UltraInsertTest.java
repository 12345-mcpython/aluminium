package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

/**
 * P10-5's acceptance case: <b>an ultimate cuts in</b> - it resolves where it is cast, at any armed moment, without
 * spending the caster's turn and without moving the action bar; and it leaves the energy the game's rule states.
 *
 * <p>The capability already existed ({@code Battle.castUltra} is public and the {@code ../tui} front end has been
 * inserting ultimates with it); what was missing is the engine-side judge the ledger asks for, and this is it. The
 * three numbers below are the ledger's own (§4.2).
 *
 * <p><b>Why "any moment" needs its own assertion.</b> The TUI's own notes record the trap: an earlier version only
 * allowed an insertion on our own turn, which silently closed the two <em>empty</em> moments - right after the battle
 * starts, and after a unit acts but before the next is readied. Those are exactly when a player presses the key, so
 * the restriction looked like "it stopped working". The first case below therefore casts with <b>no current actor at
 * all</b>.
 */
public class UltraInsertTest {

    private static final double EPS = 1e-9;

    /** {@code Constant.ENERGY_GAIN_ULTRA}: the row's own {@code sp_base}, 5 for every shipped character. */
    private static final double ULT_ENERGY = 5;

    /**
     * The insertion happens with nobody on the clock yet - the "empty moment" right after the battle starts.
     */
    @Test
    public void anUltimateCutsInBeforeAnyoneHasActed() {
        Battle battle = battle();
        Character hero = heroOf(battle);
        Assertions.assertEquals(hero.getMaxEnergy(), hero.getCurrentEnergy(), EPS, "precondition: the bar is full");

        Assertions.assertNull(battle.queue().getCurrentActor(), "precondition: nobody is acting yet");
        Assertions.assertTrue(battle.castUltra(hero, List.of(enemyOf(battle))),
                "「满能量时任意时点」: an armed ultimate must resolve even with no current actor");
        Assertions.assertTrue(hero.getCurrentEnergy() < hero.getMaxEnergy(), "the bar was spent, so the cast really happened");
    }

    /**
     * The same insertion in the middle of someone else's action: casting does not end or consume that action.
     */
    @Test
    public void anUltimateCutsInDuringAnotherUnitsTurnAndLeavesItActing() {
        Battle battle = battle();
        Character hero = heroOf(battle);
        battle.stepForward();                                  // somebody else is now the acting unit
        CanHit acting = battle.queue().getCurrentActor().getCanHit();
        Assertions.assertNotSame(hero, acting, "precondition: it is not the hero's turn");

        Assertions.assertTrue(battle.castUltra(hero, List.of(enemyOf(battle))));
        Assertions.assertSame(acting, battle.queue().getCurrentActor().getCanHit(),
                "the unit whose action was in progress still owns it - the ultimate borrowed nothing");
    }

    /**
     * The action bar is <b>untouched</b>: whoever was next is still next, at the same time.
     *
     * <p>Read as name + next-action time rather than object identity, because the claim is about the schedule, not
     * about which heap node represents it.
     */
    @Test
    public void theActionBarIsUnchangedByTheInsertion() {
        Battle battle = battle();
        Character hero = heroOf(battle);
        List<String> before = schedule(battle);

        Assertions.assertTrue(battle.castUltra(hero, List.of(enemyOf(battle))));

        Assertions.assertEquals(before, schedule(battle),
                "an inserted ultimate neither advances nor delays anybody - it is not a turn");
    }

    /**
     * What the cast leaves: the bar is zeroed and the ultimate's own gain is credited, <b>through the energy
     * regeneration rate</b> - {@code 5 x (1 + rate)}.
     */
    @Test
    public void theCastLeavesFiveEnergyScaledByTheRegenerationRate() {
        Battle battle = battle();
        Character hero = heroOf(battle);
        hero.setAttribute(AttributeType.ENERGY_REGENERATION_RATE, new DoubleValue(0.4));

        Assertions.assertTrue(battle.castUltra(hero, List.of(enemyOf(battle))));
        Assertions.assertEquals(ULT_ENERGY * 1.4, hero.getCurrentEnergy(), EPS,
                "5 x (1 + 0.4): the ultimate's gain is a normal grant, not a fixed one");
    }

    /**
     * And the gate itself: an empty bar cannot cast, so "any moment" does not mean "any state".
     */
    @Test
    public void anEmptyBarCannotCast() {
        Battle battle = battle();
        Character hero = heroOf(battle);
        hero.setCurrentEnergy(0);

        Assertions.assertFalse(battle.castUltra(hero, List.of(enemyOf(battle))),
                "the ultimate is gated on energy, insertion or not");
    }

    private static Battle battle() {
        Character hero = CharacterFactory.create(1001, 80);       // 三月七: has an ULTRA slot
        Character ally = CharacterFactory.create(1002, 80);       // 丹恒: a second, differently named unit
        Battle battle = new Battle(List.of(hero, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        hero.setCurrentEnergy(hero.getMaxEnergy());
        Assertions.assertNotNull(hero.getSkills().get(SkillType.ULTRA), "precondition: the hero has an ultimate");
        return battle;
    }

    private static Character heroOf(Battle battle) {
        return battle.characters().getFirst();
    }

    private static CanHit enemyOf(Battle battle) {
        return battle.enemies().getFirst();
    }

    private static List<String> schedule(Battle battle) {
        return battle.getQueueSnapshot().stream()
                .map(signal -> String.format(Locale.ROOT, "%s@%.4f", signal.getCanHit().getName(),
                        signal.getNextActionTime()))
                .toList();
    }


}
