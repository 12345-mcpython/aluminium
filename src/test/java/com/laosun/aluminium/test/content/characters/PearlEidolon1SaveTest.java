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
 * Character 1503 (Pearl): Eidolon 1 keeps an ally from falling - twice per battle, at half its Max HP.
 *
 * <p>「若我方目标受到致命伤害，不会陷入无法战斗状态，并立即回复等同于其生命上限 50% 的生命值。该效果单场战斗中可以触发 2 次。」
 *
 * <p>Written because a coverage sweep found this rule UNGUARDED: removing it left every judge of hers green. It is not
 * a threshold or a coefficient that another case happens to cover - it is an entire BEHAVIOUR (an ally that would have
 * died does not), so nothing else could have been watching it by accident.
 *
 * <p>The three readings are the whole sentence, and each fails separately: the ally survives; it comes back at half its
 * Max HP rather than at 1 or at full; and the third lethal blow in the same battle is NOT saved, because the allowance
 * is two. A case that only checked "it survived" would pass for an unlimited allowance.
 */
public class PearlEidolon1SaveTest {
    private static final int PEARL = 1503;
    private static final int ALLY = 1204;
    private static final int MONSTER = 1002011;
    private static final double EPS = 1.0;

    /** E1 saves the ally; E0 does not. */
    @Test
    public void theSaveNeedsTheEidolon() {
        double savedAtOne = hpFractionAfterLethalBlow(1);
        double savedAtZero = hpFractionAfterLethalBlow(0);
        System.out.println("[pearl_e1] after a lethal blow the ally is at " + savedAtOne + " of Max HP at E1, and "
                + savedAtZero + " at E0");
        Assertions.assertTrue(savedAtOne > 0.4,
                "at E1 the ally is restored to about half its Max HP (" + savedAtOne + ")");
        Assertions.assertTrue(savedAtZero <= 0,
                "and at E0 it simply falls (" + savedAtZero + ")");
    }

    /**
     * It heals HALF the Max HP, not full and not one point.
     *
     * <p>Note: Measured at 0.51 rather than exactly 0.50, and that is her own outgoing-healing boost riding along -
     * the 20% of her Elation that her trace grants (which is 0.02 at the Elation this fixture has) multiplies every
     * heal she makes, this one included. The tolerance states that rather than hiding it: the claim is "half", not
     * "half to the last decimal".
     */
    @Test
    public void theSaveRestoresHalfMaxHp() {
        double fraction = hpFractionAfterLethalBlow(1);
        System.out.println("[pearl_e1] the restored fraction is " + fraction
                + " (half, times her outgoing-healing boost)");
        Assertions.assertEquals(0.5, fraction, 0.03,
                "「回复等同于其生命上限 50% 的生命值」 - half, plus the heal boost her own trace grants");
    }

    /** 「单场战斗中可以触发 2 次」: the third lethal blow in one battle is not saved. */
    @Test
    public void theAllowanceIsTwoSavesInOneBattle() {
        Battle battle = newBattle(1);
        Character ally = battle.characters.get(1);
        Enemy attacker = battle.enemyUnits().getFirst();

        double first = lethalBlow(battle, attacker, ally);
        double second = lethalBlow(battle, attacker, ally);
        double third = lethalBlow(battle, attacker, ally);
        System.out.println("[pearl_e1] after 1/2/3 lethal blows the ally is at " + first + " / " + second + " / "
                + third + " of Max HP");
        Assertions.assertTrue(first > 0.4, "the first save happens");
        Assertions.assertTrue(second > 0.4, "the second save happens too");
        Assertions.assertTrue(third <= 0, "and the third does not: the allowance is two, and it is per battle");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** The ally's HP as a fraction of its Max HP after one lethal blow, for a Pearl of the given rank. */
    private static double hpFractionAfterLethalBlow(int eidolonRank) {
        Battle battle = newBattle(eidolonRank);
        Character ally = battle.characters.get(1);
        return lethalBlow(battle, battle.enemyUnits().getFirst(), ally);
    }

    /** Settles an overwhelming hit and returns the ally's HP afterwards, as a fraction of its maximum. */
    private static double lethalBlow(Battle battle, Enemy attacker, Character ally) {
        battle.applyDamage(ally, new Damage(attacker, ally, DamageElement.PHYSICAL, DamageType.NORMAL,
                ally.getMaxHp() * 4));
        battle.processRequests();
        return ally.getMaxHp() <= 0 ? 0 : ally.getCurrentHp() / ally.getMaxHp();
    }

    private static Battle newBattle(int eidolonRank) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PEARL, 80, false, null, null, eidolonRank),
                        CharacterFactory.create(ALLY, 80)),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
