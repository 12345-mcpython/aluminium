package com.laosun.aluminium.test.content.memosprites;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * "'长夜'被攻击的概率提高" - the memosprite's own aggro weight, which is a different unit from its master.
 *
 * <p>The number rides in the parameter her prose never names: memosprite skill (忆灵技能) 2's table is {@code [0.25 … 0., 3]}, the first value
 * being the "damage increased by 50%" the text does quote (Lv6 = 0.5) and the second a constant 3 it never mentions. The servant
 * config's property set is exactly {@code {AggroAddedRatio, AllDamageTypeAddedRatio, SpeedAddedRatio}} and its passive
 * writes {@code AggroAddedRatio}, so 3 reads as weight x (1 + 3) on top of the spec's own {@code "aggro": 125}.
 *
 * <p>Note: The other half of that sentence, "immune to control-class negative states", has been shipped since the memosprite work as
 * {@code RESIST_DEBUFF kind control percent 1.0} on the same event -- which is why nothing about it appears here.
 */
public class EvernightMemospriteAggroTest {
    private static final int EVERNIGHT = 1413;
    private static final int ALLY = 1210;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double EPS = 1e-6;

    @Test
    public void herMemospriteCarriesTheAggroRatioItsProseNeverNames() {
        Character her = CharacterFactory.create(EVERNIGHT, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(her, ally), List.of(dummy()), new Random(0));

        battle.startBattle();

        Summon evey = battle.memospriteOf(her);
        Assertions.assertNotNull(evey, "precondition: 长夜月's talent summons 长夜 at battle start");
        Assertions.assertEquals(125 * 4.0, battle.aggroOf(evey), EPS,
                "「「长夜」被攻击的概率提高」 "
                        + "-- the spec's aggro 125 x (1 + 3)");
    }

    private static Enemy dummy() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }
}
