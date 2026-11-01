package com.laosun.aluminium.test.content.characters.hanya;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1215 寒鸦's technique: 「进入战斗后对敌方随机单体附上等同于战技效果的【承负】」.
 *
 * <p>A random pick bound to one effect: exactly one enemy carries 【承负】 after the battle opens, and the marker the
 * skill's rule would have cleared is cleared first (the state only ever sits on one unit - 「仅对最新被施加的目标生效」).
 */
public class HanyaTechniqueTest {

    private static final int LEVEL = 80;
    private static final int HANYA = 1215;
    private static final String BURDEN = "承负";

    /**
     * With the technique exactly one random enemy is marked; without it, none.
     */
    @Test
    public void theTechniqueMarksOneRandomEnemy() {
        Fixture f = new Fixture(true);
        long marked = f.enemies.stream().filter(e -> e.getBuffManager().hasState(BURDEN)).count();
        Assertions.assertEquals(1, marked, "「对敌方随机单体附上…【承负】」 - exactly one enemy");
        Assertions.assertTrue(f.enemies.stream().anyMatch(e -> e.getBuffManager().hasState(BURDEN)),
                "and the marker is a state, so `has_state` answers it");

        Fixture bare = new Fixture(false);
        Assertions.assertEquals(0, bare.enemies.stream().filter(e -> e.getBuffManager().hasState(BURDEN)).count(),
                "no technique, no marker");
    }

    // ==================================================================

    private static final class Fixture {
        private final List<Enemy> enemies = new ArrayList<>();

        private Fixture(boolean technique) {
            Character hanya = CharacterFactory.create(HANYA, LEVEL);
            enemies.add(BattleFixture.monster());
            enemies.add(BattleFixture.monster());
            Battle battle = new Battle(new ArrayList<>(List.of(hanya)), new ArrayList<>(enemies),
                    BattleFixture.seeded());
            if (technique) {
                battle.markTechniqueUsed(hanya);
            }
            battle.startBattle();
        }
    }
}
