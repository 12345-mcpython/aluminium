package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DefaultSkill;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.Skill;
import com.laosun.aluminium.models.energy.StandardEnergyProvider;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 技能回能数据化（P3-4 的实质内容）：{@code skills.json.sp_base} → {@code SkillData.spBase}
 * → {@link StandardEnergyProvider}。
 *
 * <p>为什么单独一个类：既有的 {@code EnergyTest} / {@code EnergyBattleTest} 全部用
 * {@code Character.fromAttributes} 造角色，而那条路**不查技能数据**（槽位恒为 1、参数为空）——
 * 所以"技能回能"这条路径此前**完全没有被测到**，引擎里写死的 20/30/5 也就一直没被质疑过。
 */
public class EnergyGainDataTest {
    private static final double EPS = 1e-9;

    private final StandardEnergyProvider provider = new StandardEnergyProvider();

    // ==================================================================
    // 一、数据落库
    // ==================================================================

    /**
     * 常规档：普攻 20 / 战技 30 / 终结技 5 —— 与引擎原先写死的常量一致（78 个角色如此）。
     */
    @Test
    public void regularCharactersUseTheStandardGains() {
        Assertions.assertEquals(20, spBase(1204, 1), EPS, "景元普攻");
        Assertions.assertEquals(30, spBase(1204, 2), EPS, "景元战技");
        Assertions.assertEquals(5, spBase(1204, 3), EPS, "景元终结技");
        Assertions.assertEquals(20, spBase(1102, 1), EPS, "希儿普攻");
        Assertions.assertEquals(30, spBase(1105, 2), EPS, "娜塔莎战技");
    }

    /**
     * 核心：**6 个特殊资源角色一个技能都不回能**（{@code sp_base} 三项全为 null）。
     *
     * <p>修之前引擎照发 20/30/5，等于凭空给她们造出能量 —— 而她们在游戏里走的是
     * "层数/特殊资源"：飞霄与黄泉攒点数、遐蝶攒【新蕊】、白厄攒【火种】、昔涟攒【追忆】、
     * 银狼LV.999 走欢愉体系。
     */
    @Test
    public void specialResourceCharactersGainNoEnergyFromSkills() {
        int[] noEnergy = {1220, 1308, 1407, 1408, 1415, 1506};

        for (int cid : noEnergy) {
            for (int slot : new int[]{1, 2, 3}) {
                Assertions.assertNull(spBaseOrNull(cid, slot),
                        "cid=" + cid + " slot=" + slot + " 的 sp_base 应当是 null（不回能）");
            }
        }
        Assertions.assertEquals(20, spBase(1204, 1), EPS, "对照组：景元普攻仍然回 20");
    }

    /**
     * 离档值：爻光 1502 的普攻回 **30**，不是常规的 20。
     */
    @Test
    public void offScheduleValuesComeFromData() {
        Assertions.assertEquals(30, spBase(1502, 1), EPS, "爻光普攻是 30（离档）");
    }

    /**
     * ⚠ <b>登记表</b>：这些角色的 {@code sp_base} **与引擎原先写死的值不同**。
     *
     * <p>其中"战技 ≠ 30"的 8 个是 **P3-0 记录的多段/弹射技能** —— 那里的 {@code sp_base}
     * 是**每段值**（艾丝妲 6×5 段、瓦尔特 10×3 段，乘完才是常规 30）。
     * 段数乘算需要能力配置的 {@code SPHitRatio}，而**那个字段不在本项目数据里**，
     * 所以引擎当前直接取 {@code sp_base} 原值，这 8 个角色的战技回能会偏低。
     *
     * <p>这条测试的作用是**把差异钉住**，而不是断言它正确 —— 谁看到它红了，
     * 说明数据或口径变了，需要重新核对 P3-0 的结论。
     */
    @Test
    public void documentsTheOffScheduleSet() {
        // 多段/弹射：sp_base 是每段值（需要 SPHitRatio 才能算总量）
        Assertions.assertEquals(6, spBase(1009, 2), EPS, "艾丝妲（1+4 段）");
        Assertions.assertEquals(6, spBase(1108, 2), EPS, "桑博");
        Assertions.assertEquals(6, spBase(1405, 2), EPS, "那刻夏");
        Assertions.assertEquals(6, spBase(8005, 2), EPS, "同谐开拓者");
        Assertions.assertEquals(6, spBase(8006, 2), EPS, "同谐开拓者");
        Assertions.assertEquals(10, spBase(1004, 2), EPS, "瓦尔特（1+2 段）");
        // 非弹射但对不上常规 30 的
        Assertions.assertEquals(20, spBase(1212, 2), EPS, "镜流战技");
        Assertions.assertEquals(20, spBase(1402, 2), EPS, "阿格莱雅战技（Summon）");

        // 穷举一遍：全项目"战技回能 ≠ 30"的角色就是这 8 个（防止悄悄多出人来）
        long offSchedule = Constant.CHARACTERS.keySet().stream()
                .filter(cid -> {
                    Double base = spBaseOrNull(cid, 2);
                    return base != null && base != 30;
                })
                .count();
        Assertions.assertEquals(8, offSchedule,
                "战技回能离档的角色数应为 8（多段/弹射 6 + 镜流 + 阿格莱雅）");
    }

    // ==================================================================
    // 二、接进 provider
    // ==================================================================

    /**
     * 常规角色：普攻走 {@code onSkillCast} 回 20、终结技走 {@code onUltCast} 回 5。
     */
    @Test
    public void providerUsesTheDataForRegularCharacters() {
        Character jingYuan = CharacterFactory.create(1204, 80);
        Skill normal = realSkill(1204, SkillType.COMMON);
        Skill ultra = realSkill(1204, SkillType.ULTRA);

        Assertions.assertEquals(20, provider.onSkillCast(jingYuan, normal, Set.of()).amount(), EPS);
        Assertions.assertEquals(30, provider.onSkillCast(jingYuan, realSkill(1204, SkillType.SKILL), Set.of())
                .amount(), EPS);
        Assertions.assertEquals(5, provider.onUltCast(jingYuan, ultra).amount(), EPS);
    }

    /**
     * 核心：特殊资源角色施放技能**不回能**（provider 返回 null = 没有入账）。
     */
    @Test
    public void providerGivesNothingToSpecialResourceCharacters() {
        Character cyrene = CharacterFactory.create(1415, 80);
        Assertions.assertTrue(cyrene.hasEnergyBar(), "昔涟有能量池（上限 24）");

        Assertions.assertNull(provider.onSkillCast(cyrene, realSkill(1415, SkillType.COMMON), Set.of()),
                "普攻不回能");
        Assertions.assertNull(provider.onSkillCast(cyrene, realSkill(1415, SkillType.SKILL), Set.of()),
                "战技不回能");
        Assertions.assertNull(provider.onUltCast(cyrene, realSkill(1415, SkillType.ULTRA)),
                "终结技也不回能");
    }

    /**
     * 端到端：把她放进战斗里打一次普攻，能量**一点都没涨**。
     *
     * <p>这一条才是真正防回归的 —— 它同时覆盖 {@code SkillData} 取数、{@code Skill} 槽位、
     * provider 分派与 {@code Battle} 的入账口。
     */
    @Test
    public void battleEndToEndGainsNoEnergyForASpecialResourceCharacter() {
        Character cyrene = CharacterFactory.create(1415, 80);
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(cyrene), List.of(enemy), new Random(0));
        battle.startBattle();

        // 打到昔涟行动，然后用她的普攻
        for (int i = 0; i < 20 && !battle.isOver(); i++) {
            battle.stepForward();
            CanHit actor = battle.currentMove == null ? null : battle.currentMove.getCanHit();
            if (actor == cyrene) {
                break;
            }
            battle.beforeMove();
            battle.afterMove();
        }
        Assertions.assertEquals(cyrene, battle.currentMove.getCanHit(), "应当轮到昔涟");

        double before = cyrene.getCurrentEnergy();
        Assertions.assertEquals(0, before, EPS, "开局 0");
        battle.beforeMove();
        battle.performAction(realSkill(1415, SkillType.COMMON), List.of(enemy));
        battle.processRequests();
        battle.afterMove();

        Assertions.assertEquals(0, cyrene.getCurrentEnergy(), EPS,
                "特殊资源角色普攻不回能（修之前会 +20）");
    }

    /**
     * 端到端对照：常规角色（景元）同样打一次普攻，能量 +20。
     */
    @Test
    public void battleEndToEndGainsEnergyForARegularCharacter() {
        Character jingYuan = CharacterFactory.create(1204, 80);
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(jingYuan), List.of(enemy), new Random(0));
        battle.startBattle();

        for (int i = 0; i < 20 && !battle.isOver(); i++) {
            battle.stepForward();
            if (battle.currentMove != null && battle.currentMove.getCanHit() == jingYuan) {
                break;
            }
            battle.beforeMove();
            battle.afterMove();
        }
        Assertions.assertEquals(jingYuan, battle.currentMove.getCanHit());

        battle.beforeMove();
        battle.performAction(realSkill(1204, SkillType.COMMON), List.of(enemy));
        battle.processRequests();
        battle.afterMove();

        // 20（技能回能）＋ 10（受击回能，敌人之前打过他）→ 至少 20
        Assertions.assertTrue(jingYuan.getCurrentEnergy() >= 20,
                "景元普攻应当至少 +20，实际 " + jingYuan.getCurrentEnergy());
    }

    // ==================================================================

    /** 从真实数据取某槽位的 sp_base（不带角色实例，纯数据断言用）。 */
    private static Double spBaseOrNull(int cid, int slot) {
        var data = Constant.SKILLS.get(cid);
        if (data == null || data.get(slot) == null) {
            return null;
        }
        return data.get(slot).spBase();
    }

    private static double spBase(int cid, int slot) {
        Double value = spBaseOrNull(cid, slot);
        Assertions.assertNotNull(value, "cid=" + cid + " slot=" + slot + " 应当有 sp_base");
        return value;
    }

    /** 真实数据的技能（按槽位，用 {@code DefaultSkill} 直接取，不依赖 P8-2 的槽位映射）。 */
    private static Skill realSkill(int cid, SkillType type) {
        int slot = switch (type) {
            case COMMON -> 1;
            case SKILL -> 2;
            case ULTRA -> 3;
            default -> throw new IllegalArgumentException("本测试只用 1/2/3 槽");
        };
        return new DefaultSkill(cid, slot, 1);
    }
}
