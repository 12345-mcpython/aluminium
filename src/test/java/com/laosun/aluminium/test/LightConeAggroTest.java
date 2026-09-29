package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.buff.ReductionBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 「朗道的选择 / Landau's Choice」: 「使装备者受到攻击的概率提高，同时受到的伤害降低16/18/20/22/24%」.
 *
 * <p>The prose states no number for the aggro half; `weapons.json` states the factor 2 (a doubling) and the reduction per rank.
 * The aggro half is judged numerically -- a ratio of `Battle.aggroOf`, the recipe `SoftAggroWeightTest` established -- and the
 * reduction by its presence, with its magnitude registered as needing a damage measurement.
 */
public class LightConeAggroTest {
    private static final int WEAPON_ID = 21009;
    private static final int WEARER = 1210;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theAggroIsDoubled() {
        double without = aggro(false);
        double with = aggro(true);
        Assertions.assertEquals(2.0, with / without, 1e-9,
                "\u4f7f\u88c5\u5907\u8005\u53d7\u5230\u653b\u51fb\u7684\u6982\u7387\u63d0\u9ad8 -- and the data says the factor is 2");
    }

    @Test
    public void theDamageReductionIsOnTheWearer() {
        Fixture f = fixture(true);
        Assertions.assertEquals(1, f.wearer.getBuffManager().countBuffs(ReductionBuff.class),
                "the same sentence's second half is a reduction on the wearer");
        Fixture none = fixture(false);
        Assertions.assertEquals(0, none.wearer.getBuffManager().countBuffs(ReductionBuff.class),
                "and an unequipped character carries none");
    }

    private static double aggro(boolean equipped) {
        Fixture f = fixture(equipped);
        return f.battle.aggroOf(f.wearer);
    }

    private static Fixture fixture(boolean equipped) {
        Weapon weapon = equipped ? Weapon.build(WEAPON_ID, LEVEL) : null;
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, weapon);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        return new Fixture(wearer, battle);
    }

    private record Fixture(Character wearer, Battle battle) {
    }
}
