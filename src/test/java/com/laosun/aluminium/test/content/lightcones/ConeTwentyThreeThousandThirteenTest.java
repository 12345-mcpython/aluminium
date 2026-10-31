package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 时节不居 (23013): 「当装备者对我方目标提供治疗时，<b>记录治疗量</b>」.
 *
 * <p>What landed: the recording half, as one declared resource fed from `HEALED` with `amountFromEvent` - the same spelling
 * 1409's tally uses. What did NOT: the payment half, because 「该伤害不受加成影响」 has no expression (every DAMAGE instance goes
 * through the boost zone), and writing it anyway would report a number that is too high.
 */
public class ConeTwentyThreeThousandThirteenTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1204;

    @Test
    public void healingIsRecordedAtItsActualAmount() {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(23013, LEVEL, false, 1));
        Character ally = CharacterFactory.create(1001, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(wearer, ally)), new ArrayList<>(List.of(enemy)),
                BattleFixture.seeded());
        battle.startBattle();

        Assertions.assertTrue(wearer.getResources().has("记录的治疗量"), "the cone declares the counter");
        Assertions.assertEquals(0, wearer.getResources().value("记录的治疗量"), "and it starts empty");

        ally.takeDamage(ally.getMaxHp() * 0.5);
        battle.heal(wearer, ally, ally.getMaxHp() * 0.3);
        battle.processRequests();
        int recorded = wearer.getResources().value("记录的治疗量");
        System.out.println("[23013] recorded after one 30%-MaxHP heal: " + recorded);
        Assertions.assertTrue(recorded > 0, "「记录治疗量」 - the heal landed in the counter");
    }
}
