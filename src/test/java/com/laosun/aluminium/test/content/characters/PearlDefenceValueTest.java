package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Character 1503 (Pearl): her 好活当赏 pays for the party's damage reduction - the talent's second half.
 *
 * <p>「真珠可使用【好活当赏】作为抵御值，每点【好活当赏】等同于 200 点抵御值，我方目标受到伤害时，真珠可消耗抵御值为我方目标
 * 抵挡 60% 的伤害」. Both figures come off her own talent row (150304): #5 = 200 at every level, #2 = 0.6.
 *
 * <p>What this adds over {@code AbsorbDamageTest} (which judges the primitive on a fixture) is that the pieces are
 * wired to the RIGHT units in HER file: the absorber reaches the allies, and the pool it charges is HERS. A mistake
 * there is silent in play - the counter simply reads 0 from the wrong unit and nothing is ever blocked.
 *
 * <p>Note: Each reading comes from its OWN battle, and the instance is small next to the ally's HP. Both matter: an
 * earlier version read the two numbers by hitting the same ally twice in one battle, and the second hit was truncated
 * by the HP the first had already taken (measured 467.88 where the control should have been 668.39), which would have
 * compared two different situations and called it a comparison.
 */
public class PearlDefenceValueTest {
    private static final int PEARL = 1503;
    private static final int ALLY = 1204;
    private static final int MONSTER = 1002011;
    private static final String COUNTER = "好活当赏";
    private static final double INSTANCE = 100;

    /** A full counter blocks the share of an ally's hit, and the points come off HER pool. */
    @Test
    public void herCounterBlocksTheAlliesDamage() {
        Battle rich = newBattle(30);
        Character pearl = rich.characters.getFirst();
        Character ally = rich.characters.get(1);
        double cost = hit(rich, ally, INSTANCE);
        int left = pearl.getResources().value(COUNTER);
        double control = hit(newBattle(0), null, INSTANCE);

        System.out.println("[pearl_defence] the hit cost " + cost + " HP, leaving " + left + " points (control "
                + control + ")");
        Assertions.assertTrue(control > 0, "precondition: the hit lands when nothing pays for it");
        Assertions.assertTrue(cost < control,
                "her counter lowered what the ally paid (" + cost + " < " + control + ")");
        Assertions.assertTrue(left < 30, "and it was HER counter that paid (30 -> " + left + ")");
        // The pool is Pearl's, not the ally's: that split is what the clause is about.
        Assertions.assertEquals(0, ally.getResources().value(COUNTER),
                "the ally has no counter of its own");
    }

    /**
     * With an empty counter the protection is simply absent.
     *
     * <p>Read together with the case above because the failure modes look identical from one reading: a rule whose
     * resource NAME does not match the declaration also blocks nothing, and it does so even when the counter is full.
     */
    @Test
    public void anEmptyCounterMeansNoProtection() {
        Battle battle = newBattle(0);
        Character ally = battle.characters.get(1);
        double cost = hit(battle, ally, INSTANCE);
        double control = hit(newBattle(0), null, INSTANCE);
        System.out.println("[pearl_defence] empty counter: the hit cost " + cost + " (control " + control + ")");
        Assertions.assertEquals(control, cost, 1e-9, "an empty pool blocks nothing");
    }

    /**
     * One point buys exactly 200, so a counter that cannot cover the share blocks what it has.
     *
     * <p>Pearl's own version of the primitive's partial case - and the reason it is worth stating on her file: it is
     * what proves her 200 is the number in play rather than, say, 1 point per point.
     */
    @Test
    public void onePointBuysTwoHundred() {
        Battle battle = newBattle(1);
        Character pearl = battle.characters.getFirst();
        Character ally = battle.characters.get(1);
        double cost = hit(battle, ally, INSTANCE * 10);          // a share far larger than one point covers
        double control = hit(newBattle(0), null, INSTANCE * 10);
        System.out.println("[pearl_defence] one point: the hit cost " + cost + " (control " + control
                + ") ; points left " + pearl.getResources().value(COUNTER));
        Assertions.assertEquals(0, pearl.getResources().value(COUNTER), "the single point is spent");
        Assertions.assertEquals(control - 200, cost, 0.5,
                "and it bought exactly 200 of cover, which is the #5 the talent states");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** One settled hit on {@code ally} (the party's second slot when null), returning what it cost in HP. */
    private static double hit(Battle battle, Character ally, double instance) {
        Character victim = ally == null ? battle.characters.get(1) : ally;
        Enemy attacker = battle.enemyUnits().getFirst();
        double before = victim.getCurrentHp();
        battle.applyDamage(victim, new Damage(attacker, victim, DamageElement.PHYSICAL, DamageType.NORMAL, instance));
        return before - victim.getCurrentHp();
    }

    /** A fresh battle with Pearl holding {@code points} of 好活当赏. */
    private static Battle newBattle(int points) {
        Battle battle = new Battle(List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ALLY, 80)),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        if (points > 0) {
            battle.characters.getFirst().getResources().gain(COUNTER, points);
        }
        return battle;
    }
}
