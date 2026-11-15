package com.laosun.aluminium.test.content.characters.bailu;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1211 白露's 行迹 岐黄精义:「白露对我方目标造成**过量治疗**时会提高目标 10% 生命上限，持续 2 回合」.
 *
 * <p>Two things had to exist first, and both were surprises:
 *
 * <ul>
 *   <li>a signal for the OVERFLOW - {@code HEALED} deliberately does not fire for a heal that restores nothing, so the engine
 *       grew {@code OVERHEAL} (see that constant's documentation);</li>
 *   <li>a runtime max-HP spelling - {@code HEALTH_PERCENT} is a builder-only key that the loader refuses, while
 *       {@code AttributeSheet.maxHp()} reads {@code HEALTH}.</li>
 * </ul>
 *
 * <p><b>L3:</b> the two readings of this one clause - an overflowing heal and a fully-used one - are one scenario test
 * over two {@code scene()} boards, each assertion inside {@code Assertions.assertAll}; every expected value, tolerance
 * and message is the one the separate case carried.
 */
public class BailuOverhealTraceTest {
    private static final int BAILU = 1211;
    private static final int ALLY = 1204;
    private static final int LEVEL = 80;

    /**
     * 「造成过量治疗时…提高目标 10% 生命上限」: the overflowing heal lifts the target's max HP, and a heal that is fully
     * used does not.
     *
     * <p>Both readings are the one clause on the same {@code scene()}: the ally at full HP takes a heal that restores
     * nothing, and the ally that was wounded first swallows all 10 points.
     */
    @Test
    public void theOverhealTraceLiftsMaxHpOnlyWhenTheHealOverflows() {
        Battle overflowing = scene();
        Character bailu = overflowing.characters().getFirst();
        Character ally = overflowing.characters().get(1);
        double before = ally.getMaxHp();

        // The ally is at full HP, so every point of this heal is excess and NOTHING is restored - the exact case HEALED is
        // silent about.
        overflowing.heal(bailu, ally, ally.getMaxHp() * 0.5);
        overflowing.processRequests();

        Assertions.assertAll("「造成过量治疗时…提高目标 10% 生命上限」",
                () -> Assertions.assertTrue(ally.getMaxHp() > before,
                        "「造成过量治疗时…提高目标 10% 生命上限」 — max HP rose (" + before + " -> " + ally.getMaxHp() + ")"));

        Battle fullyUsed = scene();
        Character fullyUsedBailu = fullyUsed.characters().getFirst();
        Character fullyUsedAlly = fullyUsed.characters().get(1);
        Enemy enemy = (Enemy) fullyUsed.enemies().getFirst();
        fullyUsed.applyDamage(fullyUsedAlly, new Damage(enemy, fullyUsedAlly, DamageElement.FIRE, DamageType.NORMAL, 200));
        fullyUsed.processRequests();
        double fullyUsedBefore = fullyUsedAlly.getMaxHp();

        fullyUsed.heal(fullyUsedBailu, fullyUsedAlly, 10);
        fullyUsed.processRequests();

        Assertions.assertAll("no overflow, no bonus",
                () -> Assertions.assertEquals(fullyUsedBefore, fullyUsedAlly.getMaxHp(), 1e-9,
                        "no overflow, no bonus: the rule only subscribes to OVERHEAL"));
    }

    private static Battle scene() {
        Character bailu = CharacterFactory.create(BAILU, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(bailu, ally), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
