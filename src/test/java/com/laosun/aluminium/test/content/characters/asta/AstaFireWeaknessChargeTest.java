package com.laosun.aluminium.test.content.characters.asta;

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
 * 1009 艾丝妲's talent: 「若受到攻击的敌方目标弱点为火属性，则额外获得 1 层蓄能」.
 */
public class AstaFireWeaknessChargeTest {

    private static final int ASTA = 1009;
    private static final int LEVEL = 80;
    private static final String CHARGE = "蓄能";

    @Test
    public void onlyAFireWeaknessEnemyPaysTheExtraLayer() {
        Assertions.assertEquals(0, chargeFrom(false), "no Fire weakness: nothing");
        Assertions.assertEquals(1, chargeFrom(true), "「额外获得 1 层」");
    }

    private static int chargeFrom(boolean fireWeakness) {
        Character asta = CharacterFactory.create(ASTA, LEVEL);
        // 803201006 weakness = [Thunder, Wind] (no Fire); the standard fixture already has Fire.
        Enemy enemy = fireWeakness ? BattleFixture.monster() : BattleFixture.enemy(803201006, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(asta, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        int before = asta.getBuffManager().stacksOf(CHARGE);
        battle.applyDamage(enemy, new Damage(asta, enemy, DamageElement.FIRE, DamageType.NORMAL, 100));
        return asta.getBuffManager().stacksOf(CHARGE) - before;
    }
}

