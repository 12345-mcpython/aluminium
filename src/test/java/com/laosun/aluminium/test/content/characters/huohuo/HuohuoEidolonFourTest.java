package com.laosun.aluminium.test.content.characters.huohuo;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1217 藿藿's Eidolon 4: 「目标的当前生命值越低治疗量越高，最多使藿藿提供的治疗量提高 80%」 - a <b>continuous</b> bonus, not a
 * threshold, so the check is the healing amount at three HP shares: +0% at full, +40% at half, +80% at empty.
 *
 * <p>The target is a rule-less unit brought to the share with true damage, so the only difference between two runs is the
 * healer's Eidolon rank (a character with shields or reductions would land the damage for a different amount).
 */
public class HuohuoEidolonFourTest {

    private static final int LEVEL = 80;
    private static final int HUOHUO = 1217;

    @Test
    public void theBonusGrowsAsTheTargetIsMoreHurt() {
        Assertions.assertEquals(1.0, ratio(4, 1.0), 1e-6, "满血: no bonus (「越低…越高」 has nothing to give)");
        Assertions.assertEquals(1.4, ratio(4, 0.5), 1e-6, "half HP: 0.8 x 0.5 = 40%");
        // HP floors at 1, so the near-empty case is 1% (the full 80% is the limit, not a reachable point).
        Assertions.assertEquals(1.792, ratio(4, 0.01), 1e-6, "1% HP: 0.8 x 0.99 = 79.2%");

        Assertions.assertEquals(1.0, ratio(0, 0.5), 1e-6, "without the Eidolon the heal does not follow the HP");
        Assertions.assertEquals(1.0, ratio(0, 0.01), 1e-6);
    }

    private static double ratio(int eidolon, double hpShare) {
        return healed(eidolon, hpShare) / healed(0, hpShare);
    }

    private static double healed(int eidolon, double hpShare) {
        Character huohuo = CharacterFactory.create(HUOHUO, LEVEL, true, null, null, eidolon);
        Character ally = Character.fromAttributes("Poor Soul", 100000, 500, 100, 100);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(huohuo, ally)), new ArrayList<>(List.of(enemy)),
                BattleFixture.seeded());
        battle.startBattle();

        double missing = ally.getMaxHp() * (1 - hpShare);
        if (missing > 0) {
            ally.consumeHp(missing);   // exact HP loss: no damage zones to reduce it
        }
        Assertions.assertEquals(hpShare, ally.getCurrentHp() / ally.getMaxHp(), 1e-6,
                "precondition: the target is at the intended HP share");
        return battle.calculateHeal(huohuo, ally, 1000.0);
    }
}

