package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * {@code "target": "lowest_hp_ally"} - "当前<b>生命值百分比</b>最低的我方目标".
 *
 * <p><b>Why the vocabulary needed it.</b> "The most hurt ally" is a place an effect <b>reaches</b>, and before this
 * selector the only tool was a <b>condition</b> - which filters <i>rules</i> ("is this event mine?"), not the units
 * an effect lands on. So March 7th's Eidolon 2 ("进入战斗时，为当前生命值百分比最低的我方目标提供…护盾") and Huohuo's (藿藿)
 * [禎命] / Lingsha's (灵砂) [浮元] (heal that unit) had no spelling at all.
 *
 * <p>Note: <b>The one thing this suite exists to pin: percentage, not points.</b> With allies at 100/1000 and 900/10000
 * the answer differs - 10% versus 9% - so a selector that quietly compared absolute HP would look right in every
 * two-ally test and be wrong in a real fight. {@link #theLowestShareWinsRatherThanTheLowestNumberOfPoints} is that
 * case, and it is built so that the number of <i>points</i> points at the other ally.
 */
public class LowestHpAllyTest {
    private static final double EPS = 1e-9;

    private static final int MONSTER = 1002011;

    /** The most-hurt <b>share</b> wins, even when another ally has fewer HP points. */
    @Test
    public void theLowestShareWinsRatherThanTheLowestNumberOfPoints() {
        Character hero = character("hero", 1000);
        Character ally = character("ally", 10_000);
        Fixture f = new Fixture(hero, List.of(hero, ally), TriggerSpecs.heal(100, "lowest_hp_ally"));
        hero.takeDamage(500);                                 // 500/1000   = 50%, and only 500 POINTS
        ally.takeDamage(9100);                                // 900/10000  =  9%, but 900 POINTS

        f.fire();

        Assertions.assertEquals(500, hero.getCurrentHp(), 1.0,
                "an absolute-HP reading would pick the 500-point ally -- it must not");
        Assertions.assertEquals(1000, ally.getCurrentHp(), 1.0,
                "「生命值百分比最低的」 is the 9% one, so the heal lands there");
    }

    /**
     * Ties go to the earliest unit in the party order.
     *
     * <p>Not a corner case: at {@code BATTLE_START} everybody is at 100%, so every Eidolon-2-style rule fires on a tie
     * and the choice has to be stated rather than left to whatever order a map happened to iterate in.
     */
    @Test
    public void aTieGoesToTheEarlierUnitInThePartyOrder() {
        Character hero = character("hero", 1000);
        Character ally = character("ally", 1000);
        Fixture f = new Fixture(hero, List.of(hero, ally), TriggerSpecs.heal(100, "lowest_hp_ally"));
        Assertions.assertSame(hero, f.battle.allies.getFirst(), "precondition: the party order is hero, ally");
        hero.takeDamage(500);
        ally.takeDamage(500);
        Assertions.assertEquals(hero.getCurrentHp() / hero.getMaxHp(), ally.getCurrentHp() / ally.getMaxHp(), EPS,
                "precondition: an exact tie (50% each)");

        f.fire();

        Assertions.assertEquals(600, hero.getCurrentHp(), 1.0, "the FIRST unit in the party order got it");
        Assertions.assertEquals(500, ally.getCurrentHp(), 1.0, "and the later one did not");
    }

    /** A corpse is not "the most hurt ally" - and a heal would not land on it anyway. */
    @Test
    public void theDeadAreSkipped() {
        Character hero = character("hero", 1000);
        Character ally = character("ally", 1000);
        Fixture f = new Fixture(hero, List.of(hero, ally), TriggerSpecs.heal(100, "lowest_hp_ally"));
        ally.takeDamage(ally.getMaxHp());                     // dead, and therefore at 0%
        hero.takeDamage(500);

        f.fire();

        Assertions.assertEquals(600, hero.getCurrentHp(), 1.0,
                "the living ally is the lowest -- 「我方目标」 does not mean 「包括倒下的」");
    }

    /** A camp with nobody alive is <b>no target</b>, not an error: the same reading the group selectors have. */
    @Test
    public void aCampWithNobodyAliveResolvesToNothingRatherThanFailing() {
        Character hero = character("hero", 1000);
        Fixture f = new Fixture(hero, List.of(hero), TriggerSpecs.heal(100, "lowest_hp_ally"));
        hero.takeDamage(hero.getMaxHp());

        Assertions.assertDoesNotThrow(f::fire, "an empty answer is a no-op, like 「我方全体」 with nobody standing");
        Assertions.assertEquals(0, hero.getCurrentHp(), 1.0, "and nothing happened to it");
    }

    /** It needs a battlefield, and a hand-built context has none: that fails loudly rather than guessing. */
    @Test
    public void withoutABattleItFailsLoudly() {
        Character owner = character("owner", 1000);
        EffectSpec heal = TriggerSpecs.heal(100, "lowest_hp_ally");
        owner.setTriggerTable(new TriggerTable(0, List.of(TriggerSpecs.rule("ALLY_ATTACK", null, heal))));

        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(owner, owner, null, 0, 0);
        TriggerTable.CompiledRule rule = owner.getTriggerTable()
                .matching(TriggerEvent.ALLY_ATTACK, ctx).getFirst();

        IllegalStateException rejected = Assertions.assertThrows(IllegalStateException.class,
                () -> com.laosun.aluminium.models.TriggerInterpreter.apply(null, rule, ctx));
        Assertions.assertTrue(rejected.getMessage().contains("no battle"), rejected.getMessage());
    }

    /**
     * The selector is a <b>list</b>, so an op that resolves one unit refuses it.
     *
     * <p>It always resolves to exactly one unit - but that is a property of the party's <i>state</i>, not of the
     * selector, and the list resolver is the one that has a battlefield to answer with. Keeping it on that side is
     * what makes {@code HEAL} / {@code SHIELD} / {@code ADVANCE} work without any of them knowing about it.
     */
    @Test
    public void aSingleTargetOpRefusesTheSelector() {
        EffectSpec extraTurn = new EffectSpec();
        TriggerSpecs.set(extraTurn, "op", "EXTRA_TURN");
        TriggerSpecs.set(extraTurn, "target", "lowest_hp_ally");
        Character hero = character("hero", 1000);
        Fixture f = new Fixture(hero, List.of(hero), extraTurn);

        IllegalStateException rejected = Assertions.assertThrows(IllegalStateException.class, f::fire);
        Assertions.assertTrue(rejected.getMessage().contains("several"), rejected.getMessage());
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /** The first character carries the rule under test; the rest are the party it chooses from. */
    private record Fixture(Character hero, Battle battle) {

        private Fixture(Character hero, List<Character> party, EffectSpec effect) {
            this(hero, battle(hero, party, effect));
        }

        private static Battle battle(Character hero, List<Character> party, EffectSpec effect) {
            hero.setTriggerTable(new TriggerTable(0,
                    List.of(TriggerSpecs.rule("ALLY_ATTACK", null, effect))));
            for (Character other : party) {
                if (other != hero) {
                    other.setTriggerTable(new TriggerTable(0, List.of()));
                }
            }
            Battle battle = new Battle(party, List.of(dummy()), new Random(0));
            battle.startBattle();
            return battle;
        }

        private int fire() {
            return battle.fireTriggers(TriggerEvent.ALLY_ATTACK, hero, null, 1, 0);
        }
    }

    /** A character with an explicit panel: the shares in these cases have to be stated, not inherited. */
    private static Character character(String name, double maxHp) {
        return Character.fromAttributes(name, maxHp, 100, 100, 100);
    }

    private static Enemy dummy() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }
}
