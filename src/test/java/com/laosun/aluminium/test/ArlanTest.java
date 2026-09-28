package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.DebuffClass;
import com.laosun.aluminium.enums.SkillType;
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
 * 1008 Arlan, from his own file (2026-09-29, round 169): his traces, and the blast shape his ultimate exposed.
 *
 * <p>The blast case asserts the DOCUMENT'S 2:1 RATIO between the centre and its neighbours. It is what found the engine bug: the branch
 * applied the centre's multiplier to the neighbours, and the skill data's second parameter (`[3.2, 1.6]`) was never read.
 */
public class ArlanTest {
    private static final int ARLAN = 1008;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 「320%…and 160% to enemies adjacent to it」: the neighbour takes HALF of what the centre takes. */
    @Test
    public void hisUltimateHitsNeighboursForHalf() {
        Character arlan = CharacterFactory.create(ARLAN, LEVEL);
        Enemy left = Enemy.fromAttributes("Left Dummy", 40000, 500, 100, 90);
        Enemy centre = Enemy.fromAttributes("Centre Dummy", 40000, 500, 100, 90);
        Enemy right = Enemy.fromAttributes("Right Dummy", 40000, 500, 100, 90);
        Battle battle = new Battle(List.of(arlan), List.of(left, centre, right), fixed());
        battle.startBattle();
        double centreBefore = centre.getCurrentHp();
        double leftBefore = left.getCurrentHp();
        double rightBefore = right.getCurrentHp();

        battle.castImmediate(arlan.getSkills().get(SkillType.ULTRA), arlan, List.of(centre));

        double centreLoss = centreBefore - centre.getCurrentHp();
        double neighbourLoss = ((leftBefore - left.getCurrentHp()) + (rightBefore - right.getCurrentHp())) / 2;
        Assertions.assertTrue(centreLoss > 0, "\u300c\u5bf9\u6307\u5b9a\u654c\u65b9\u5355\u4f53\u9020\u6210\u2026\u96f7\u5c5e\u6027\u4f24\u5bb3\u300d");
        Assertions.assertEquals(0.5, neighbourLoss / centreLoss, 0.02,
                "\u300c\u540c\u65f6\u5bf9\u5176\u76f8\u90bb\u76ee\u6807\u9020\u6210\u7b49\u540c\u4e8e\u963f\u5170 160% \u653b\u51fb\u529b\u7684\u96f7\u5c5e\u6027\u4f24\u5bb3\u300d \u2014 the data says 320%/160%: centre "
                        + centreLoss + " vs neighbours " + neighbourLoss);
    }

    /** \u26a0 「消灭敌方目标时，若当前生命值百分比小于等于30%，则立即回复等同于自身生命上限20%的生命值」. */
    @Test
    public void hisSurvivalTraceHealsOnAKillBelowThirtyPercent() {
        Character arlan = CharacterFactory.create(ARLAN, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(arlan), List.of(enemy), fixed());
        battle.startBattle();
        // The zones eat part of each instance, so damage him until the fraction is where the document says (bounded loop).
        for (int i = 0; i < 20 && arlan.getCurrentHp() / arlan.getMaxHp() > 0.3 && !arlan.isDeath(); i++) {
            battle.applyDamage(arlan, new com.laosun.aluminium.models.Damage(enemy, arlan,
                    com.laosun.aluminium.enums.DamageElement.PHYSICAL,
                    com.laosun.aluminium.enums.DamageType.NORMAL, arlan.getMaxHp() * 0.25));
        }
        double before = arlan.getCurrentHp();
        Assertions.assertFalse(arlan.isDeath(), "the fixture must be alive to be healed");
        Assertions.assertTrue(arlan.getCurrentHp() / arlan.getMaxHp() <= 0.3,
                "the fixture must be below the threshold, was " + (arlan.getCurrentHp() / arlan.getMaxHp()));

        battle.fireTriggers(TriggerEvent.KILL, arlan, enemy, 0, 0);

        Assertions.assertTrue(arlan.getCurrentHp() > before,
                "\u300c\u7acb\u5373\u56de\u590d\u7b49\u540c\u4e8e\u81ea\u8eab\u751f\u547d\u4e0a\u965020%\u7684\u751f\u547d\u503c\u300d: " + before + " -> " + arlan.getCurrentHp());
    }

    /** \u26a0 「抵抗持续伤害类负面状态的概率提高50%」: the per-class resistance, and only that class. */
    @Test
    public void hisEnduranceTraceResistsDotOnly() {
        Character arlan = CharacterFactory.create(ARLAN, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(arlan), List.of(enemy), fixed());
        battle.startBattle();

        Assertions.assertTrue(arlan.getBuffManager().debuffResistOf(DebuffClass.DOT) > 0,
                "\u300c\u62b5\u6297\u6301\u7eed\u4f24\u5bb3\u7c7b\u8d1f\u9762\u72b6\u6001\u7684\u6982\u7387\u63d0\u9ad850%\u300d");
        Assertions.assertEquals(0.0, arlan.getBuffManager().debuffResistOf(DebuffClass.CONTROL), 1e-9,
                "it must not become a general resistance to every debuff class");
    }

    /** Census: the two traces that carry rules plus the level convention. */
    @Test
    public void hisFileCarriesTheClauses() {
        var table = TriggerTables.of(ARLAN);
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.KILL), "the survival heal");
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.BATTLE_START), "the DoT resistance and the level convention");
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
