package com.laosun.aluminium.test.content.characters.luocha;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1203 罗刹's ultimate: 「解除敌方全体 1 个增益效果」.
 *
 * <p>Direction matters: this strips a <b>positive</b> effect off the <b>enemies</b>, so it is {@code REMOVE_BUFF}
 * ({@code removeBuffs}) and not {@code DISPEL} ({@code removeDebuffs}, which is the other direction and the one his trace
 * uses). Permanent loadout bonuses are skipped by the engine on purpose - nobody's ultimate dispels a relic.
 */
public class LuochaUltDispelTest {

    private static final int LEVEL = 80;
    private static final int LUOCHA = 1203;
    private static final int ALLY = 1001;
    private static final String TEMPORARY = "敌方增益";
    private static final String PERMANENT = "永久增益";

    /**
     * Every enemy loses exactly its one timed buff; the permanent one stays.
     */
    @Test
    public void theUltimateStripsOneBuffFromEveryEnemy() {
        Fixture f = new Fixture(2);
        for (Enemy enemy : f.enemies) {
            enemy.getBuffManager().addBuff(new StateBuff(TEMPORARY, 2));
            enemy.getBuffManager().addBuff(new StateBuff(PERMANENT, 100, true));
        }
        f.castUltimate();
        for (Enemy enemy : f.enemies) {
            Assertions.assertFalse(enemy.getBuffManager().hasState(TEMPORARY),
                    enemy.getName() + ": the timed buff is dispelled");
            Assertions.assertTrue(enemy.getBuffManager().hasState(PERMANENT),
                    enemy.getName() + ": a permanent loadout buff is not a 增益效果");
        }
    }

    /**
     * It is one per enemy, not one overall: with two timed buffs each, one remains.
     */
    @Test
    public void onlyOneIsRemovedPerEnemy() {
        Fixture f = new Fixture(1);
        Enemy enemy = f.enemies.getFirst();
        enemy.getBuffManager().addBuff(new StateBuff(TEMPORARY, 2));
        enemy.getBuffManager().addBuff(new StateBuff("第二个增益", 2));
        f.castUltimate();
        boolean first = enemy.getBuffManager().hasState(TEMPORARY);
        boolean second = enemy.getBuffManager().hasState("第二个增益");
        System.out.println("[1203] after the ultimate: [" + TEMPORARY + "]=" + first + " [第二个增益]=" + second);
        Assertions.assertNotEquals(first, second, "「1 个」 - exactly one of the two timed buffs is gone");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character luocha;
        private final List<Enemy> enemies = new ArrayList<>();
        private final Battle battle;

        private Fixture(int enemyCount) {
            luocha = CharacterFactory.create(LUOCHA, LEVEL);
            for (int i = 0; i < enemyCount; i++) {
                enemies.add(BattleFixture.monster());
            }
            battle = new Battle(new ArrayList<>(List.of(luocha, CharacterFactory.create(ALLY, LEVEL))),
                    new ArrayList<>(enemies), BattleFixture.seeded());
            battle.startBattle();
        }

        private void castUltimate() {
            battle.castImmediate(luocha.getSkills().get(SkillType.ULTRA), luocha, List.copyOf(enemies));
        }
    }
}


