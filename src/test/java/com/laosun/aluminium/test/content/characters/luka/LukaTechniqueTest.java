package com.laosun.aluminium.test.content.characters.luka;

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
 * 1111 卢卡's technique: 「进入战斗后对敌方随机单体造成 50% 攻击力物理伤害，并有 100% 基础概率使该目标陷入与战技相同的裂伤状态。
 * 随后卢卡额外获得 1 层【斗志】」.
 *
 * <p>The two halves must land on <b>the same</b> randomly chosen enemy - that is what the rule-level pick is for (a
 * per-effect {@code random_enemy} would roll twice). The bleed's numbers are the skill's own (`skill_lacerate`).
 */
public class LukaTechniqueTest {

    private static final int LEVEL = 80;
    private static final int LUKA = 1111;
    private static final String FIGHTING_SPIRIT = "斗志";

    /**
     * Exactly one enemy is hit, and that same enemy is the one bleeding; 斗志 gains a layer.
     */
    @Test
    public void theTechniqueHitsAndBleedsTheSameEnemy() {
        Fixture f = new Fixture(true);
        List<Enemy> damaged = f.enemies.stream().filter(e -> e.getCurrentHp() < e.getMaxHp()).toList();
        Assertions.assertEquals(1, damaged.size(), "a single random enemy takes the hit");
        Enemy victim = damaged.getFirst();
        Assertions.assertEquals(1, victim.getBuffManager().countBuffs(DotBuff.class),
                "「使该目标陷入…裂伤」 - the SAME enemy, not a second roll");
        for (Enemy enemy : f.enemies) {
            if (enemy != victim) {
                Assertions.assertEquals(0, enemy.getBuffManager().countBuffs(DotBuff.class),
                        "the other enemy must not be bleeding");
            }
        }
        // The talent already grants a layer at battle start, so the technique is measured as one more.
        int bare = new Fixture(false).luka.getBuffManager().stacksOf(FIGHTING_SPIRIT);
        Assertions.assertEquals(bare + 1, f.luka.getBuffManager().stacksOf(FIGHTING_SPIRIT),
                "「随后卢卡额外获得 1 层【斗志】」");
    }

    /**
     * Without the technique neither the hit, the bleed nor the layer happens.
     */
    @Test
    public void withoutTheTechniqueNothingHappens() {
        Fixture f = new Fixture(false);
        for (Enemy enemy : f.enemies) {
            Assertions.assertEquals(enemy.getMaxHp(), enemy.getCurrentHp(), 1e-9, "no hit");
            Assertions.assertEquals(0, enemy.getBuffManager().countBuffs(DotBuff.class), "no bleed");
        }
        int baseline = new Fixture(false).luka.getBuffManager().stacksOf(FIGHTING_SPIRIT);
        Assertions.assertEquals(baseline, f.luka.getBuffManager().stacksOf(FIGHTING_SPIRIT), "no extra layer");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character luka;
        private final List<Enemy> enemies = new ArrayList<>();

        private Fixture(boolean technique) {
            luka = CharacterFactory.create(LUKA, LEVEL);
            enemies.add(BattleFixture.monster());
            enemies.add(BattleFixture.monster());
            Battle battle = new Battle(new ArrayList<>(List.of(luka)), new ArrayList<>(enemies),
                    BattleFixture.seeded());
            if (technique) {
                battle.markTechniqueUsed(luka);
            }
            // The stated 100% base chance is rolled against effect resistance; zero it so the fixture measures the clause.
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

