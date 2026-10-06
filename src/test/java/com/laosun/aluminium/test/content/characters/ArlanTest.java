package com.laosun.aluminium.test.content.characters;

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
 * 1008 Arlan, from his own file: his traces, and the blast shape his ultimate exposed.
 *
 * <p>The blast case asserts the DOCUMENT'S 2:1 RATIO between the centre and its neighbours. It is what found the engine bug: the branch
 * applied the centre's multiplier to the neighbours, and the skill data's second parameter (`[3.2, 1.6]`) was never read.
 */
public class ArlanTest {
    private static final int ARLAN = 1008;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: "320%…and 160% to enemies adjacent to it": the neighbour takes HALF of what the centre takes. */
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
        Assertions.assertTrue(centreLoss > 0, "「对指定敌方单体造成…雷属性伤害」");
        Assertions.assertEquals(0.5, neighbourLoss / centreLoss, 0.02,
                "「同时对其相邻目标造成等同于阿兰 160% 攻击力的雷属性伤害」 — the data says 320%/160%: centre "
                        + centreLoss + " vs neighbours " + neighbourLoss);
    }

    /** Note: "消灭敌方目标时，若当前生命值百分比小于等于30%，则立即回复等同于自身生命上限20%的生命值". */
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
                "「立即回复等同于自身生命上限20%的生命值」: " + before + " -> " + arlan.getCurrentHp());
    }

    /** Note: "抵抗持续伤害类负面状态的概率提高50%": the per-class resistance, and only that class. */
    @Test
    public void hisEnduranceTraceResistsDotOnly() {
        Character arlan = CharacterFactory.create(ARLAN, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(arlan), List.of(enemy), fixed());
        battle.startBattle();

        Assertions.assertTrue(arlan.getBuffManager().debuffResistOf(DebuffClass.DOT) > 0,
                "「抵抗持续伤害类负面状态的概率提高50%」");
        Assertions.assertEquals(0.0, arlan.getBuffManager().debuffResistOf(DebuffClass.CONTROL), 1e-9,
                "it must not become a general resistance to every debuff class");
    }

    /** Census: the traces that carry rules plus the level convention. */
    @Test
    public void hisFileCarriesTheClauses() {
        var table = TriggerTables.of(ARLAN);
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.KILL), "the survival heal");
        //: three BATTLE_START rules now, not two -- the eidolon-four trace is applied there (its sentence starts
        // with "进入战斗后"), and that trace is what the lethal blow is gated on.
        Assertions.assertEquals(3, table.ruleCount(TriggerEvent.BATTLE_START),
                "the DoT resistance, the level convention, and the eidolon-four trace");
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
