package com.laosun.aluminium.test.content.lightcones;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/** F-4: light cone 23034's skill-point clause, "every 2 Skills or Ultimates cast on one ally, restore 1". */
public class Cone23034SkillPointTest {
    private static final int CONE = 23034;
    private static final int WEARER = 1204;   // any wearer will do; the cone is what is under test
    private static final int ALLY = 1205;

    @Test
    public void theSecondAllyTargetedCastRestoresAPoint() {
        int afterOne = pointsAfterAllyCasts(1);
        int afterTwo = pointsAfterAllyCasts(2);
        System.out.println("[cone23034_sp] after one ultimate cast on the ally = " + afterOne
                + " ; after two = " + afterTwo);
        Assertions.assertEquals(3, afterOne, "one cast is not enough yet, and an ultimate spends nothing");
        Assertions.assertEquals(4, afterTwo, "the second cast on the ally restores one point");
    }

    /** Casts the wearer's ultimate at the ally the given number of times and reports the pool. */
    private static int pointsAfterAllyCasts(int casts) {
        Character wearer = CharacterFactory.create(WEARER, 80, true, Weapon.build(CONE, 80, false, 1));
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(wearer, ally), List.of(EnemyFactory.create(1002011, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        wearer = battle.characters.getFirst();
        ally = battle.characters.get(1);
        for (int i = 0; i < casts; i++) {
            wearer.setCurrentEnergy(200);
            Assertions.assertTrue(battle.castUltra(wearer, List.of(ally)), "precondition: the ultimate lands on the ally");
            battle.processRequests();
            wearer = battle.characters.getFirst();
            ally = battle.characters.get(1);
        }
        return battle.getSkillPoints();
    }
}
