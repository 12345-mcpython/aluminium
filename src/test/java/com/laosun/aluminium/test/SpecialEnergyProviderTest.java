package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DefaultSkill;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.Skill;
import com.laosun.aluminium.models.energy.EnergyProvider;
import com.laosun.aluminium.models.energy.NoConventionalEnergyProvider;
import com.laosun.aluminium.models.energy.StandardEnergyProvider;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 特殊供能角色走**独立 {@link EnergyProvider}**（{@link NoConventionalEnergyProvider}）。
 *
 * <p>背景：飞霄 1220 / 黄泉 1308 / 遐蝶 1407 / 白厄 1408 / 昔涟 1415 / 银狼LV.999 1506
 * 在游戏里攒的不是能量，而是层数 / 【新蕊】/【火种】/【追忆】等资源。
 *
 * <p>为什么必须拦满 5 个钩子：{@code castUltra} 的门槛是 {@code currentEnergy >= maxEnergy}，
 * 而这些角色的上限很低（黄泉 **9**、飞霄/白厄 **12**）。只堵技能那两条的话，
 * 他们挨一两下就能凑满并**放出一个本不该存在的终结技**（槽位 3 确实是 {@code Ultra}）。
 * 这个类就是那条护栏。
 *
 * <p>为什么不放在 {@link StandardEnergyProvider} 里判空：那是**设计归类**而不是单条数据事实，
 * 按 P8-0 的三分法归 provider / 装配点（也是唯一允许出现 {@code cid} 的地方）。
 */
public class SpecialEnergyProviderTest {
    private static final double EPS = 1e-9;

    /** 全项目"走特殊资源"的角色，逐个明确写出来（多一个少一个都要显式改）。 */
    private static final int[] SPECIAL = {1220, 1308, 1407, 1408, 1415, 1506};

    private final EnergyProvider standard = new StandardEnergyProvider();
    private final EnergyProvider none = new NoConventionalEnergyProvider();

    // ==================================================================
    // 一、provider 本身
    // ==================================================================

    /** 5 个钩子全部不入账 —— 这是"任何来源都不该回能"的完整表达。 */
    @Test
    public void specialProviderGrantsNothingFromAnySource() {
        Character any = CharacterFactory.create(1308, 80);      // 黄泉
        Skill skill = realSkill(1308, SkillType.COMMON);
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        var hit = new com.laosun.aluminium.models.Damage(any, enemy,
                com.laosun.aluminium.enums.DamageElement.THUNDER,
                com.laosun.aluminium.enums.DamageType.NORMAL, 100);

        Assertions.assertNull(none.onSkillCast(any, skill, Set.of()), "技能施放");
        Assertions.assertNull(none.onUltCast(any, realSkill(1308, SkillType.ULTRA)), "终结技");
        Assertions.assertNull(none.onTakingHit(any, hit), "受击");
        Assertions.assertNull(none.onKill(any, any), "击杀");
        Assertions.assertNull(none.onBreak(any, any), "击破");
    }

    /** 对照：常规 provider 的同样 5 个钩子都会给（受击 10 / 击杀 5 / 击破 5）。 */
    @Test
    public void standardProviderGrantsFromEverySource() {
        Character regular = CharacterFactory.create(1204, 80);
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        // onTakingHit 自己会判 damage == null（那是"没有伤害事件"），所以这里给一发真伤害
        var hit = new com.laosun.aluminium.models.Damage(regular, enemy,
                com.laosun.aluminium.enums.DamageElement.THUNDER,
                com.laosun.aluminium.enums.DamageType.NORMAL, 100);

        Assertions.assertEquals(20, standard.onSkillCast(regular,
                realSkill(1204, SkillType.COMMON), Set.of()).amount(), EPS);
        Assertions.assertEquals(30, standard.onSkillCast(regular,
                realSkill(1204, SkillType.SKILL), Set.of()).amount(), EPS);
        Assertions.assertEquals(5, standard.onUltCast(regular,
                realSkill(1204, SkillType.ULTRA)).amount(), EPS);
        Assertions.assertEquals(10, standard.onTakingHit(regular, hit).amount(), EPS);
        Assertions.assertEquals(5, standard.onKill(regular, regular).amount(), EPS);
        Assertions.assertEquals(5, standard.onBreak(regular, regular).amount(), EPS);
    }

    /** 常规 provider 仍走常量（20/30/5），与 ROADMAP P3-0 的常规档一致 —— 不依赖技能数据。 */
    @Test
    public void standardProviderUsesTheConventionalConstants() {
        Character yaoGuang = CharacterFactory.create(1502, 80);   // 爻光：普攻数据是 30（离档）
        Assertions.assertEquals(20, standard.onSkillCast(yaoGuang,
                        realSkill(1502, SkillType.COMMON), Set.of()).amount(), EPS,
                "常规 provider 给常量 20（离档值的保真留给 P3-4 的数据化）");
    }

    // ==================================================================
    // 二、装配点
    // ==================================================================

    /** {@code CharacterFactory} 给这 6 个角色换上了特殊 provider。 */
    @Test
    public void factoryInjectsTheSpecialProvider() {
        for (int cid : SPECIAL) {
            Character c = CharacterFactory.create(cid, 80);
            Assertions.assertTrue(c.getEnergyProvider() instanceof NoConventionalEnergyProvider,
                    "cid=" + cid + " 应当是特殊 provider，实际 " + c.getEnergyProvider().getClass().getSimpleName());
            Assertions.assertTrue(CharacterFactory.usesSpecialResource(cid));
        }
        Character jingYuan = CharacterFactory.create(1204, 80);
        Assertions.assertTrue(jingYuan.getEnergyProvider() instanceof StandardEnergyProvider,
                "常规角色仍然是标准 provider");
        Assertions.assertFalse(CharacterFactory.usesSpecialResource(1204));
    }

    /** 占位入口不受影响（它不查角色数据，也就没有 cid 可判）。 */
    @Test
    public void placeholderCharactersKeepTheStandardProvider() {
        Character placeholder = Character.fromAttributes("hero", 10_000, 100, 100, 100);
        Assertions.assertTrue(placeholder.getEnergyProvider() instanceof StandardEnergyProvider);
    }

    // ==================================================================
    // 三、端到端：这才是防回归的部分
    // ==================================================================

    /**
     * 核心：黄泉挨打**一点能量都不涨**。
     *
     * <p>她上限只有 9，修之前挨一下（+10）就满，能直接放出终结技。
     */
    @Test
    public void acheronGainsNothingFromBeingHit() {
        Character acheron = CharacterFactory.create(1308, 80);
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(acheron), List.of(enemy), new Random(0));
        battle.startBattle();

        // 让敌人打她几下：敌人 132 速先动
        for (int i = 0; i < 6 && !battle.isOver(); i++) {
            battle.stepForward();
            if (battle.currentMove == null) {
                break;
            }
            CanHit actor = battle.currentMove.getCanHit();
            battle.beforeMove();
            if (actor == enemy) {
                battle.performAction(enemySkill(enemy), List.of(acheron));
            } else {
                battle.performAction(realSkill(1308, SkillType.COMMON), List.of(enemy));
            }
            battle.afterMove();
        }

        Assertions.assertEquals(0, acheron.getCurrentEnergy(), EPS,
                "黄泉的能量应当恒为 0（受击/技能都不入账），实际 " + acheron.getCurrentEnergy());
        Assertions.assertFalse(acheron.isEnergyFull(),
                "永远不该满能量 —— 满了就能放出不该存在的终结技");
    }

    /**
     * 对照：景元同样挨打，能量会涨（受击回能 10）。
     */
    @Test
    public void regularCharacterStillGainsFromBeingHit() {
        Character jingYuan = CharacterFactory.create(1204, 80);
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(jingYuan), List.of(enemy), new Random(0));
        battle.startBattle();

        for (int i = 0; i < 6 && !battle.isOver(); i++) {
            battle.stepForward();
            if (battle.currentMove == null) {
                break;
            }
            CanHit actor = battle.currentMove.getCanHit();
            battle.beforeMove();
            if (actor == enemy) {
                battle.performAction(enemySkill(enemy), List.of(jingYuan));
            } else {
                battle.performAction(realSkill(1204, SkillType.COMMON), List.of(enemy));
            }
            battle.afterMove();
        }

        Assertions.assertTrue(jingYuan.getCurrentEnergy() > 0,
                "常规角色应当有能量入账，实际 " + jingYuan.getCurrentEnergy());
    }

    /**
     * 6 个特殊角色在真实战斗里**能量恒为 0**（穷举，避免只保一个）。
     */
    @Test
    public void everySpecialResourceCharacterStaysAtZeroEnergy() {
        for (int cid : SPECIAL) {
            Character c = CharacterFactory.create(cid, 80);
            Enemy enemy = EnemyFactory.create(1002011, 90, 1);
            Battle battle = new Battle(List.of(c), List.of(enemy), new Random(1));
            battle.startBattle();

            for (int i = 0; i < 6 && !battle.isOver(); i++) {
                battle.stepForward();
                if (battle.currentMove == null) {
                    break;
                }
                CanHit actor = battle.currentMove.getCanHit();
                battle.beforeMove();
                if (actor == enemy) {
                    battle.performAction(enemySkill(enemy), List.of(c));
                } else {
                    battle.performAction(realSkill(cid, SkillType.COMMON), List.of(enemy));
                }
                battle.afterMove();
            }
            Assertions.assertEquals(0, c.getCurrentEnergy(), EPS,
                    "cid=" + cid + "（" + c.getName() + "）的能量应当恒为 0");
        }
    }

    // ==================================================================

    private static Skill realSkill(int cid, SkillType type) {
        int slot = switch (type) {
            case COMMON -> 1;
            case SKILL -> 2;
            case ULTRA -> 3;
            default -> throw new IllegalArgumentException("本测试只用 1/2/3 槽");
        };
        return new DefaultSkill(cid, slot, 1);
    }

    /** 敌人的普攻（P5-3 装在敌人身上的技能）。 */
    private static Skill enemySkill(Enemy enemy) {
        return enemy.getSkills().values().iterator().next();
    }
}
