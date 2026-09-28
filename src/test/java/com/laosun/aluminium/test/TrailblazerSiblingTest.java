package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 8002, the sibling id of 8001 (2026-09-29, round 182): the same Trailblazer (Destruction) kit, verified number by number.
 *
 * <p>Same three assertions as 8001's file: the break-triggered ATK stacking with its cap, the technique heal gated on the round-178 marker, and the
 * census. Shipping the sibling under the same document is the point — the two ids state the same numbers.
 */
public class TrailblazerSiblingTest {
    private static final int TB2 = 8002;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 「每次击破敌方目标的弱点后，攻击力提高20%\u2026\u6700\u591a\u53e0\u52a02\u5c42」. */
    @Test
    public void hisTalentStacksAttackOnBreaksAndStopsAtTwo() {
        Character tb = CharacterFactory.create(TB2, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(tb), List.of(enemy), fixed());
        battle.startBattle();
        double before = tb.getAttribute(AttributeType.ATTACK).get();

        battle.fireTriggers(TriggerEvent.BREAK, tb, enemy, 0, 0);
        double afterOne = tb.getAttribute(AttributeType.ATTACK).get();
        battle.fireTriggers(TriggerEvent.BREAK, tb, enemy, 0, 0);
        double afterTwo = tb.getAttribute(AttributeType.ATTACK).get();
        battle.fireTriggers(TriggerEvent.BREAK, tb, enemy, 0, 0);
        double afterThree = tb.getAttribute(AttributeType.ATTACK).get();

        Assertions.assertTrue(afterOne > before, "\u300c\u6bcf\u6b21\u51fb\u7834\u654c\u65b9\u76ee\u6807\u7684\u5f31\u70b9\u540e\uff0c\u653b\u51fb\u529b\u63d0\u9ad820%\u300d: " + before + " -> " + afterOne);
        Assertions.assertTrue(afterTwo > afterOne, "the second layer must add again");
        Assertions.assertEquals(afterTwo, afterThree, 1e-9, "\u6700\u591a\u53e0\u52a02\u5c42");
    }

    /** \u26a0 The technique heal, gated on the round-178 marker. */
    @Test
    public void aDeclaredTechniqueHealsTheParty() {
        Character tb = CharacterFactory.create(TB2, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(tb, ally), List.of(enemy), fixed());
        battle.markTechniqueUsed(tb);
        battle.applyDamage(ally, new com.laosun.aluminium.models.Damage(enemy, ally,
                com.laosun.aluminium.enums.DamageElement.PHYSICAL,
                com.laosun.aluminium.enums.DamageType.NORMAL, ally.getMaxHp() * 0.5));
        double hurt = ally.getCurrentHp();

        battle.startBattle();

        double expected = ally.getMaxHp() * 0.15;
        Assertions.assertEquals(expected, ally.getCurrentHp() - hurt, expected * 0.05,
                "\u300c\u56de\u590d\u7b49\u540c\u4e8e\u5404\u81ea\u751f\u547d\u4e0a\u965015%\u7684\u751f\u547d\u503c\u300d");
    }

    /** Census: the talent, the technique heal and the level convention. */
    @Test
    public void hisFileCarriesTheClauses() {
        var table = TriggerTables.of(TB2);
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.BREAK), "the ATK stack");
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.BATTLE_START), "the technique heal and the level convention");
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        };
    }
}
