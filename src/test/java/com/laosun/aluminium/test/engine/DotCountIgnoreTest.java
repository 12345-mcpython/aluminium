package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** Relic 116, 4 pieces: "每承受 1 个持续伤害效果…无视其 6% 防御力，最多计入 3 个" -- instance-scoped, counted per DoT. */
public class DotCountIgnoreTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int RELIC_LEVEL = 15;
    private static final double SHARE = 0.06;
    private static final int CAP = 3;

    @Test
    public void theFourthPieceIgnoresDefencePerDotUpToThree() {
        for (int dots = 0; dots <= 4; dots++) {
            double[] pair = state(dots);
            double expected = SHARE * Math.min(dots, CAP);
            System.out.println("[116] dots=" + dots + " engineDots=" + pair[1] + " ignore=" + pair[0]
                    + " expected=" + expected);
            Assertions.assertEquals(expected, pair[0], 1e-9,
                    dots + " DoTs should ignore " + expected + " but measured " + pair[0]);
        }
    }

    private static double[] state(int dots) {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, null, RelicFactory.suit(116, 4, RELIC_LEVEL));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        DamageElement[] elements = {DamageElement.FIRE, DamageElement.THUNDER, DamageElement.ICE, DamageElement.WIND};
        for (int index = 0; index < dots; index++) {
            enemy.getBuffManager().addBuff(new DotBuff(unit, elements[index], 100, 3));
        }
        Damage damage = new Damage(unit, enemy, DamageElement.QUANTUM, DamageType.NORMAL, 1000);
        battle.applyDamage(enemy, damage);
        return new double[] {damage.getDefenceIgnore(), enemy.getBuffManager().countBuffs(DotBuff.class)};
    }
}
