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
 */
public class BailuOverhealTraceTest {
    private static final int BAILU = 1211;
    private static final int ALLY = 1204;
    private static final int LEVEL = 80;

    @Test
    public void aHealThatOverflowsLiftsTheTargetsMaxHp() {
        Battle battle = scene();
        Character bailu = battle.characters().getFirst();
        Character ally = battle.characters().get(1);
        double before = ally.getMaxHp();

        // The ally is at full HP, so every point of this heal is excess and NOTHING is restored - the exact case HEALED is
        // silent about.
        battle.heal(bailu, ally, ally.getMaxHp() * 0.5);
        battle.processRequests();

        Assertions.assertTrue(ally.getMaxHp() > before,
                "「造成过量治疗时…提高目标 10% 生命上限」 — max HP rose (" + before + " -> " + ally.getMaxHp() + ")");
    }

    @Test
    public void aHealThatIsFullyUsedDoesNot() {
        Battle battle = scene();
        Character bailu = battle.characters().getFirst();
        Character ally = battle.characters().get(1);
        Enemy enemy = (Enemy) battle.enemies().getFirst();
        battle.applyDamage(ally, new Damage(enemy, ally, DamageElement.FIRE, DamageType.NORMAL, 200));
        battle.processRequests();
        double before = ally.getMaxHp();

        battle.heal(bailu, ally, 10);
        battle.processRequests();

        Assertions.assertEquals(before, ally.getMaxHp(), 1e-9,
                "no overflow, no bonus: the rule only subscribes to OVERHEAL");
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
