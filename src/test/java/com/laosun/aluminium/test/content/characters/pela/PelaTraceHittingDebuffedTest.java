package com.laosun.aluminium.test.content.characters.pela;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.VulnerabilityBuff;
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
 * 1106 佩拉's trace 痛击: 「对处于负面效果的敌方目标造成的伤害提高 20%」 - measured as the DELTA of the boost zone between an
 * enemy with a real debuff and one without (her own panel already carries a boost).
 */
public class PelaTraceHittingDebuffedTest {

    private static final int PELA = 1106;
    private static final int LEVEL = 80;

    @Test
    public void onlyADebuffedEnemyPaysTheExtraBoost() {
        double bare = boostArea(false);
        double debuffed = boostArea(true);
        System.out.println("[1106] boost zone " + bare + " -> " + debuffed);
        Assertions.assertEquals(0.2, debuffed - bare, 1e-6, "「对处于负面效果的敌方目标造成的伤害提高 20%」");
    }

    private static double boostArea(boolean debuffed) {
        Character pela = CharacterFactory.create(PELA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(pela, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        if (debuffed) {
            enemy.getBuffManager().addBuff(new VulnerabilityBuff(1, 0.01));
        }
        Damage damage = new Damage(pela, enemy, DamageElement.ICE, DamageType.NORMAL, 100);
        battle.applyDamage(enemy, damage);
        return damage.breakdown().getOrDefault("BoostArea", 0.0);
    }
}