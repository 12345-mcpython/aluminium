package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DefaultSkill;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.Skill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 技能**槽位映射**（P8-2 的核心）：{@code SkillType} → {@code skills.json} 的槽位号。
 *
 * <p>修之前 {@code Character.Builder.build()} 把每个槽位都写成
 * {@code new DefaultSkill(cid, 1, level)} —— 于是普攻/战技/终结技/天赋**全部**解析到槽位 1，
 * 六个槽位的倍率、削韧、元素、{@code sp_need} 全是普攻的。
 *
 * <p>为什么这个 bug 一直没被发现：既有的 `SkillExecutorTest` / `SuperBreakTest` 都**自己
 * 构造** `new DefaultSkill(cid, 槽位, ...)`，从不检查 builder 装出来的东西；
 * 而 `EnergyTest` 验的又是 provider 的分派逻辑。所以"角色实际拿到什么技能"这条路没人走过。
 * 这个类就补那一段。
 */
public class SkillSlotMappingTest {
    private static final double EPS = 1e-9;

    /** 槽位表本身：1 普攻 / 2 战技 / 3 终结技 / 4 天赋，且只有这 4 项。 */
    @Test
    public void slotTableIsTheSingleSourceOfTruth() {
        Assertions.assertEquals(Map.of(
                        SkillType.COMMON, 1,
                        SkillType.SKILL, 2,
                        SkillType.ULTRA, 3,
                        SkillType.TALENT, 4),
                Constant.SKILL_SLOT);

        // SkillType 里没有地图普攻/秘技，所以槽位 6/7 刻意不映射
        Assertions.assertFalse(Constant.SKILL_SLOT.containsKey(SkillType.SUMMON_SKILL));
        Assertions.assertFalse(Constant.SKILL_SLOT.containsKey(SkillType.SUMMON_TALENT));
    }

    /**
     * 真实角色：每个槽位拿到**自己的**数据，不再是普攻的。
     *
     * <p>用景元（1204）：普攻单体 0.5 / 战技全体 / 终结技全体 / 天赋弹射。
     */
    @Test
    public void eachSlotResolvesItsOwnData() {
        Character jingYuan = CharacterFactory.create(1204, 80);

        Skill common = jingYuan.getSkills().get(SkillType.COMMON);
        Skill skill = jingYuan.getSkills().get(SkillType.SKILL);
        Skill ultra = jingYuan.getSkills().get(SkillType.ULTRA);
        Skill talent = jingYuan.getSkills().get(SkillType.TALENT);

        // 攻击类型各不相同（修之前四个都是 "Normal"）
        Assertions.assertEquals("Normal", common.getData().getSkillType());
        Assertions.assertEquals("BPSkill", skill.getData().getSkillType());
        Assertions.assertEquals("Ultra", ultra.getData().getSkillType());
        Assertions.assertNull(talent.getData().getSkillType(), "天赋槽的攻击类型数据里是 null");
    }

    /**
     * 与 {@code Constant.SKILLS} 的原始数据逐字段对齐（P8-2 的验收式）。
     */
    @Test
    public void builderDataMatchesTheRawSkillData() {
        int cid = 1204;
        Character jingYuan = CharacterFactory.create(cid, 80);

        for (Map.Entry<SkillType, Integer> entry : Constant.SKILL_SLOT.entrySet()) {
            int slot = entry.getValue();
            var raw = Constant.SKILLS.get(cid).get(slot);
            var data = jingYuan.getSkills().get(entry.getKey()).getData();

            Assertions.assertEquals(raw.attackType(), data.getSkillType(),
                    entry.getKey() + " 的攻击类型");
            Assertions.assertEquals(raw.maxLevel(), data.getMaxLevel(),
                    entry.getKey() + " 的等级上限");
            Assertions.assertEquals(raw.paramList(), data.getSkills(),
                    entry.getKey() + " 的参数表");
            Assertions.assertEquals(raw.stanceList().single(), data.getStanceList().single(),
                    entry.getKey() + " 的单体削韧");
        }
    }

    /**
     * 削韧值也按槽位走：景元普攻单体 30 / 战技全体 30 / 终结技全体 60 / 天赋单体 15。
     */
    @Test
    public void stanceValuesFollowTheSlot() {
        Character jingYuan = CharacterFactory.create(1204, 80);

        var common = jingYuan.getSkills().get(SkillType.COMMON).getData().getStanceList();
        var skill = jingYuan.getSkills().get(SkillType.SKILL).getData().getStanceList();
        var ultra = jingYuan.getSkills().get(SkillType.ULTRA).getData().getStanceList();
        var talent = jingYuan.getSkills().get(SkillType.TALENT).getData().getStanceList();

        Assertions.assertEquals(30, common.single(), EPS);
        Assertions.assertEquals(30, skill.all(), EPS);
        Assertions.assertEquals(60, ultra.all(), EPS);
        Assertions.assertEquals(15, talent.single(), EPS);
    }

    /**
     * 元素也按槽位走：娜塔莎的战技/终结技是**治疗**（数据里 `element = Unknown` → null），
     * 而她的普攻是物理伤害。
     */
    @Test
    public void nonDamagingSlotsHaveNoElement() {
        Character natasha = CharacterFactory.create(1105, 80);

        Assertions.assertNotNull(natasha.getSkills().get(SkillType.COMMON).getData().getElement(),
                "普攻是物理伤害");
        Assertions.assertNull(natasha.getSkills().get(SkillType.SKILL).getData().getElement(),
                "战技是治疗 → 无元素");
        Assertions.assertNull(natasha.getSkills().get(SkillType.ULTRA).getData().getElement(),
                "终结技是治疗 → 无元素");
    }

    /**
     * 等级上限按槽位不同（普攻 10 / 战技·终结技·天赋 15）——证明"读的是自己槽位的 max_level"。
     */
    @Test
    public void maxLevelComesFromTheSlot() {
        Character jingYuan = CharacterFactory.create(1204, 80);

        Assertions.assertEquals(10, jingYuan.getSkills().get(SkillType.COMMON).getData().getMaxLevel());
        Assertions.assertEquals(15, jingYuan.getSkills().get(SkillType.SKILL).getData().getMaxLevel());
        Assertions.assertEquals(15, jingYuan.getSkills().get(SkillType.ULTRA).getData().getMaxLevel());
    }

    /**
     * 全部 93 个角色 × 4 个槽位都能装配（数据齐全，没有 EMPTY 兜底）。
     *
     * <p>用一个"非空参数表"作判据：`SkillData.EMPTY` 的参数表是空的。
     */
    @Test
    public void everyCharacterHasAllFourSlots() {
        Constant.CHARACTERS.keySet().forEach(cid -> {
            Character c = CharacterFactory.create(cid, 80);
            for (Map.Entry<SkillType, Integer> entry : Constant.SKILL_SLOT.entrySet()) {
                Skill skill = c.getSkills().get(entry.getKey());
                Assertions.assertNotNull(skill, "cid=" + cid + " 缺 " + entry.getKey());
                Assertions.assertFalse(skill.getData().getSkills().isEmpty(),
                        "cid=" + cid + " 的 " + entry.getKey() + " 参数表为空（数据没取到？）");
            }
        });
    }

    /**
     * 技能**等级确实接进了伤害**：{@code SkillExecutor} 用 {@code skill.getLevel() - 1} 取参数行。
     *
     * <p>⚠ 我先前在这里写过"等级还没接进伤害"——**那是错的**，已更正。
     * 起因：我构造了一个 8 级技能、却断言 {@code getData().getSkills().getFirst()} 是 1.2，
     * 而 {@code getSkills()} 返回的是**整张**逐级表，取 {@code getFirst()} 当然还是第 1 档。
     * 我把自己取错行当成了引擎没取行。
     *
     * <p>真实调用链：{@code skill.getLevel()} → {@code index = level - 1} → {@code levels.get(index)}。
     * 端到端见 {@link #skillLevelScalesActualDamage}。
     */
    @Test
    public void skillLevelSelectsTheParameterRow() {
        Character hero = CharacterFactory.create(1204, 80);
        List<List<Double>> table = hero.getSkills().get(SkillType.COMMON).getData().getSkills();

        Assertions.assertEquals(0.5, table.getFirst().getFirst(), EPS, "第 1 档 = 0.5");
        Assertions.assertEquals(1.2, table.get(7).getFirst(), EPS, "第 8 档 = 1.2");
        Assertions.assertEquals(table.get(7), table.get(8 - 1), "level 8 → index 7");
    }

    /**
     * 端到端：同一技能 1 级与 8 级打出的伤害之比 = 倍率之比（1.2 / 0.5 = 2.4）。
     *
     * <p>这条把"等级接入"钉死 —— 同时覆盖 {@code SkillExecutor} 的取行、倍率取值与伤害管线。
     */
    @Test
    public void skillLevelScalesActualDamage() {
        double lv1 = damageOfBasicAttack(1);
        double lv8 = damageOfBasicAttack(8);

        Assertions.assertEquals(1.2 / 0.5, lv8 / lv1, 1e-6,
                "8 级 / 1 级的伤害比应等于倍率比 2.4，实际 " + (lv8 / lv1));
        Assertions.assertTrue(lv1 > 0 && lv8 > lv1, "等级越高伤害越高");
    }

    /**
     * 但**装配出来的角色默认是 1 级技能**：{@code Builder} 的 {@code skillLevel} 初值就是 1，
     * 要升级得调 {@code skillLevel(type)}（+1）或 {@code setSkillLevel(type, level)}。
     *
     * <p>这不是缺陷：技能等级属于 P8 的成长系统（行迹/星魂会加等级），
     * 本项只负责"槽位对、数据对、等级能生效"。
     */
    @Test
    public void factoryCharactersStartAtSkillLevelOne() {
        Character hero = CharacterFactory.create(1204, 80);
        for (SkillType type : Constant.SKILL_SLOT.keySet()) {
            Assertions.assertEquals(1, hero.getSkills().get(type).getLevel(),
                    type + " 的初始技能等级是 1");
        }

        // 提升两级后，实际打出的是第 3 档倍率
        Character leveled = Character.builder().cid(1204).level(80).isPromote()
                .skillLevel(SkillType.COMMON).skillLevel(SkillType.COMMON).build();
        Assertions.assertEquals(3, leveled.getSkills().get(SkillType.COMMON).getLevel());
        Assertions.assertEquals(0.7, leveled.getSkills().get(SkillType.COMMON)
                .getData().getSkills().get(2).getFirst(), EPS, "3 级 → 第 3 档 = 0.7");
    }

    /** 用指定技能等级打一发普攻，返回对敌人造成的伤害。 */
    private static double damageOfBasicAttack(int level) {
        Character hero = CharacterFactory.create(1204, 80);
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(1_000_000));
        enemy.heal(1_000_000);

        Battle battle = new Battle(List.of(hero), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.castImmediate(new DefaultSkill(1204, 1, level), hero, List.of(enemy));
        return 1_000_000 - enemy.getCurrentHp();
    }
}
