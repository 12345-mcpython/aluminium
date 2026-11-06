package com.laosun.aluminium.test.content.characters.xueyi;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1214 雪衣's Eidolon 2, the healing half: 「…同时为自身恢复等同于雪衣 5% 生命上限的生命值」 - at both stack caps.
 *
 * <p>Written 2026-11-06 with the fix it guards: the heal used to require 「恶报 ≥ 7」 (the 8-stack cap), so from 星魂 6 on -
 * where the cap drops to <b>6</b> - the condition could never hold and the healing vanished silently. Two enemies are not
 * needed; what is needed is the cap <b>in force at that Eidolon rank</b>.
 */
public class XueyiEidolonTwoHealTest {

    private static final int XUEYI = 1214;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;

    @Test
    public void theHealLandsAtTheSixStackCapToo() {
        Assertions.assertTrue(healedAtSix(2) > 0, "E2 (cap 8): the follow-up heals her");
        Assertions.assertTrue(healedAtSix(6) > 0, "E6 (cap 6): …and it still heals her — the condition follows the cap");
        Assertions.assertEquals(healedAtSix(0), 0.0, 1e-9, "below E2 there is no heal to begin with");
    }

    /** How much HP the sixth toughness reduction restores at the given Eidolon rank. */
    private static double healedAtSix(int eidolon) {
        Character xueyi = CharacterFactory.create(XUEYI, LEVEL, true, null, null, eidolon);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.enemy(803201006, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(xueyi, ally)),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        // A wound, so a heal has room to show: 40% of her bar.
        battle.applyDamage(xueyi, new Damage(enemy, xueyi, DamageElement.THUNDER, DamageType.NORMAL,
                xueyi.getMaxHp() * 0.4));
        battle.processRequests();

        // The cap IN FORCE at this rank: 8 below 星魂 6, 6 from it on (「上限降低至 6 层」). The reading is the
        // DIFFERENCE the capping reduction makes, so the wound's exact size and any mitigation cannot leak into it.
        int cap = eidolon >= 6 ? 6 : 8;
        for (int i = 0; i < cap - 1; i++) {
            battle.reduceToughness(ally, enemy, DamageElement.THUNDER, 1, null);
            enemy.setStance(300);
        }
        battle.processRequests();
        double before = xueyi.getCurrentHp();
        int karma = xueyi.getBuffManager().stacksOf("恶报");
        battle.reduceToughness(ally, enemy, DamageElement.THUNDER, 1, null);
        battle.processRequests();
        System.out.println("[1214] e" + eidolon + " cap=" + cap + " karma=" + karma + " hp " + before + " -> "
                + xueyi.getCurrentHp());
        return xueyi.getCurrentHp() - before;
    }
}
