package com.laosun.aluminium.test.content.characters.hook;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1109 虎克's technique: 「进入战斗后对敌方随机单体造成 50% 攻击力的火属性伤害，同时有 100% 基础概率使敌方每个单体目标陷入
 * 灼烧状态，持续 3 回合（每回合 50% 攻击力火伤）」.
 *
 * <p>Two halves, two different targets: the <b>damage</b> is one random enemy ({@code random_enemy}), the <b>burn</b> is
 * every enemy ({@code all_enemies}). The technique gate is the engine's own {@code 秘技} state, laid down before any
 * {@code BATTLE_START} rule runs.
 */
public class HookTechniqueTest {

    private static final int LEVEL = 80;
    private static final int HOOK = 1109;

    /**
     * With the technique: one random enemy takes the hit, every enemy burns; without it, neither happens.
     */
    @Test
    public void theTechniqueHitsOneAndBurnsAll() {
        Fixture f = new Fixture(true);
        List<Enemy> hit = f.enemies.stream().filter(e -> e.getCurrentHp() < e.getMaxHp()).toList();
        Assertions.assertEquals(1, hit.size(), "「对敌方随机单体造成…火属性伤害」 - exactly one enemy is hit");
        for (Enemy enemy : f.enemies) {
            Assertions.assertEquals(1, enemy.getBuffManager().countBuffs(DotBuff.class),
                    enemy.getName() + ": 「使敌方每个单体目标陷入灼烧状态」");
        }

        Fixture bare = new Fixture(false);
        for (Enemy enemy : bare.enemies) {
            Assertions.assertEquals(enemy.getMaxHp(), enemy.getCurrentHp(), 1e-9, "no technique, no damage");
            Assertions.assertEquals(0, enemy.getBuffManager().countBuffs(DotBuff.class), "no technique, no burn");
        }
    }

    // ==================================================================

    private static final class Fixture {
        private final Character hook;
        private final List<Enemy> enemies = new ArrayList<>();
        private final Battle battle;

        private Fixture(boolean technique) {
            hook = CharacterFactory.create(HOOK, LEVEL);
            enemies.add(BattleFixture.monster());
            enemies.add(BattleFixture.monster());
            battle = new Battle(new ArrayList<>(List.of(hook)), new ArrayList<>(enemies), BattleFixture.seeded());
            if (technique) {
                battle.markTechniqueUsed(hook);
            }
            // The stated 100% base chance is rolled against effect resistance; a zero-resistance target makes the
            // fixture measure the clause, not the roll.
            for (Enemy enemy : enemies) {
                DoubleValue resistance = enemy.getAttribute(AttributeType.EFFECT_RESISTANCE);
                if (resistance != null && resistance.get() != 0) {
                    resistance.addModifier(DoubleValue.Modifier.pure(-resistance.get()));
                }
            }
            battle.startBattle();
        }
    }
}
