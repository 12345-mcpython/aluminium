package com.laosun.aluminium.test.content.characters.jiaoqiu;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StackBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1218 椒丘's Eidolon 1, first clause: 「我方目标对处于【烬煨】状态的敌方目标造成的伤害提高 40.00%」.
 *
 * <p>Note: the attacker is a plain ally, not Jiaoqiu - the sentence says 「我方目标」 (any of our units), and a plain ally's
 * boost zone starts at 1.0, so the +0.4 reads as exactly x1.4 instead of being diluted by Jiaoqiu's own buff.
 */
public class JiaoqiuEidolonOneTest {

    private static final int JIAOQIU = 1218;
    private static final int ATTACKER = 1001;
    private static final int LEVEL = 80;
    private static final String ASHEN = "烬煨";

    @Test
    public void anyAllyHitsARoastedEnemyHarder() {
        double e0 = settled(0);
        double e1 = settled(1);
        System.out.println("[1218] e0=" + e0 + " e1=" + e1 + " ratio=" + (e1 / e0));
        Assertions.assertEquals(1.4, e1 / e0, 1e-6, "「对处于【烬煨】状态的敌方目标造成的伤害提高 40%」");
    }

    private static double settled(int eidolon) {
        Character jiaoqiu = CharacterFactory.create(JIAOQIU, LEVEL, true, null, null, eidolon);
        Character attacker = CharacterFactory.create(ATTACKER, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(jiaoqiu, attacker), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        enemy.getBuffManager().addBuff(new StackBuff(ASHEN, 1, true, 5));
        return battle.applyDamage(enemy, new Damage(attacker, enemy, DamageElement.PHYSICAL, DamageType.NORMAL, 100));
    }
}