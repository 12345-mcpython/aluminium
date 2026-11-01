package com.laosun.aluminium.test.content.characters.fuxuan;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1208 符玄's Eidolon 2: 「【穷观阵】开启时，若我方目标受到致命伤害，则…不会陷入无法战斗状态，并立即回复等同于其自身生命上限
 * 70% 的生命值。该效果单场战斗中可以触发 1 次」.
 *
 * <p>The rule already existed; this judge pins it (and the once-per-battle limit).
 */
public class FuXuanEidolonTwoTest {

    private static final int FUXUAN = 1208;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;
    private static final String ZONE = "鉴知";

    @Test
    public void theZoneSavesAnAllyOncePerBattle() {
        Character fuxuan = CharacterFactory.create(FUXUAN, LEVEL, true, null, null, 2);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(fuxuan, ally)),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        fuxuan.getBuffManager().addBuff(new StateBuff(ZONE, 3, false));

        battle.applyTrueDamage(enemy, ally, DamageElement.PHYSICAL, ally.getCurrentHp() * 10);
        battle.processRequests();
        Assertions.assertFalse(ally.isDeath(), "the lethal hit did not take the ally down");
        double healed = ally.getCurrentHp();
        System.out.println("[1208] ally hp after the rescue = " + healed + " / max " + ally.getMaxHp());
        Assertions.assertEquals(0.7, healed / ally.getMaxHp(), 1e-6, "「回复等同于其自身生命上限 70%」");

        // Second lethal hit: the once-per-battle limit has been spent.
        CanHit target = ally;
        battle.applyTrueDamage(enemy, target, DamageElement.PHYSICAL, ally.getCurrentHp() * 10);
        battle.processRequests();
        Assertions.assertTrue(ally.isDeath(), "「该效果单场战斗中可以触发 1 次」");
    }
}