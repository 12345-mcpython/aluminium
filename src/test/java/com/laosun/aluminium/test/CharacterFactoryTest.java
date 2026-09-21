package com.laosun.aluminium.test;

import com.laosun.aluminium.beans.CharacterData;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.Path;
import com.laosun.aluminium.exceptions.CharacterException;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.SkillPoint;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.LevelPromotionCalc;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * P8-1 验收：{@code CharacterFactory} + 角色身份字段补全。
 *
 * <p>本项**不做技能装配**（那是 P8-2），所以这里断言的是：元素 / 命途 / 仇恨 / 能量上限 /
 * 等级 / 面板缩放 —— 也就是"一个角色的身份与数值"，不含机制。
 *
 * <p>选人原则：5 人是 P8-5 的目标队伍 + 存护，覆盖 8 种元素中的 5 种、5 种命途、
 * 4 档仇恨、4 档能量；另有 3 个**数据边界**角色（null / 12 / 9 能量）只做能量断言。
 */
public class CharacterFactoryTest {

    // ==================================================================
    // 主验收：景元
    // ==================================================================

    @Test
    public void createBuildsARealCharacter() {
        Character jingYuan = CharacterFactory.create(1204, 80);

        Assertions.assertEquals("Jing Yuan", jingYuan.getName());
        Assertions.assertEquals(80, jingYuan.getLevel());
        Assertions.assertEquals(DamageElement.THUNDER, jingYuan.getElement());
        Assertions.assertEquals(Path.ERUDITION, jingYuan.getPath());
        Assertions.assertEquals(130, jingYuan.getMaxEnergy(), 1e-9);
        Assertions.assertEquals(75, jingYuan.getAggro());
    }

    /**
     * 面板缩放核对：{@code 最终面板 = 数据基础值 × calcCharacterRate(80, true)}，
     * **再叠加 {@code point.json} 的行迹加成**（后者在 {@code build()} 里无条件应用）。
     *
     * <pre>
     *   景元的行迹：攻击 4+4+6+6+8 = 28%、防御 5+7.5 = 12.5%，没有生命行迹
     *   攻击 95.04  × 7.35 × 1.28   = 894.13632
     *   防御 66     × 7.35 × 1.125  = 545.7375
     *   生命 158.4  × 7.35          = 1164.24
     * </pre>
     *
     * <p>⚠ 我第一版把攻击/防御直接断言成 {@code 数据 × 倍率}、**漏了行迹**，失败值
     * 894.136 / 545.7375 看起来像"属性数组索引错位"，害我误诊了一轮。
     * 这里改成从 {@code SkillPoint.sumAttributes} 显式把行迹算进去 —— 测试自己会解释那 28%/12.5%。
     */
    @Test
    public void panelIsScaledFromTheData() {
        CharacterData data = CharacterFactory.data(1204);
        Character jingYuan = CharacterFactory.create(1204, 80);
        double rate = LevelPromotionCalc.calcCharacterRate(80, true);
        Map<AttributeType, Double> traces = SkillPoint.sumAttributes(SkillPoint.init(1204));

        double traceAttack = traces.getOrDefault(AttributeType.ATTACK_PERCENT, 0.0);
        double traceDefence = traces.getOrDefault(AttributeType.DEFENCE_PERCENT, 0.0);
        Assertions.assertEquals(0.28, traceAttack, 1e-9, "景元的攻击行迹合计 28%");
        Assertions.assertEquals(0.125, traceDefence, 1e-9, "景元的防御行迹合计 12.5%");

        Assertions.assertEquals(data.health() * rate,
                jingYuan.getAttribute(AttributeType.HEALTH).get(), 1e-3,
                "他没有生命行迹");
        Assertions.assertEquals(data.attack() * rate * (1 + traceAttack),
                jingYuan.getAttribute(AttributeType.ATTACK).get(), 1e-3);
        Assertions.assertEquals(data.defence() * rate * (1 + traceDefence),
                jingYuan.getAttribute(AttributeType.DEFENCE).get(), 1e-3);
        // 速度不吃等级缩放，也没有速度行迹
        Assertions.assertEquals(data.speed(),
                jingYuan.getAttribute(AttributeType.SPEED).get(), 1e-9);
        Assertions.assertEquals(1164.24, jingYuan.getAttribute(AttributeType.HEALTH).get(), 1e-3,
                "景元 Lv80 满晋阶生命（游戏内值）");
    }

    /**
     * 晋阶倍率只保证 **Lv1 与 Lv80 两个锚点**，中间档位是**线性近似**，不是游戏值。
     *
     * <p>实测 {@code calcCharacterRate}：Lv20 → 2.35、Lv40 → 4.15、Lv70 → **6.85**；
     * 而 docs 的「面板成长」表里晋阶 2/40 是 {@code 285.12 / 158.4 = 1.80}、
     * 晋阶 5/70 是 {@code 475.2 / 158.4 = 3.00}（即 1 + 等级档 × 0.4）。
     * 所以**不要**拿中间档位去对拍游戏面板 —— 公式只是首尾对得上（这与 ROADMAP P1-4
     * 记的"模拟器倍率公式"一致：那本来就是近似）。
     *
     * <p>这条把"哪些锚点是可信的"写进测试，免得下次又有人（包括我）拿 Lv70 去断言 475.2。
     */
    @Test
    public void onlyTheLevel1AndLevel80AnchorsMatchTheGameTable() {
        // 可信锚点 1：Lv1 未晋阶 = 基础值
        Assertions.assertEquals(1.0, LevelPromotionCalc.calcCharacterRate(1, false), 1e-9);
        Assertions.assertEquals(158.4,
                158.4 * LevelPromotionCalc.calcCharacterRate(1, false), 1e-9);

        // 可信锚点 2：Lv80 已晋阶 = ×7.35（景元满级 1164.24 / 三月七 1058.4）
        Assertions.assertEquals(7.35, LevelPromotionCalc.calcCharacterRate(80, true), 1e-9);
        Assertions.assertEquals(1164.24,
                158.4 * LevelPromotionCalc.calcCharacterRate(80, true), 1e-3);
        Assertions.assertEquals(1058.4,
                144.0 * LevelPromotionCalc.calcCharacterRate(80, true), 1e-3);

        // 中间档位：记录当前公式值，**并显式记录它与游戏表的差异**
        Assertions.assertEquals(6.85, LevelPromotionCalc.calcCharacterRate(70, true), 1e-9);
        Assertions.assertNotEquals(475.2 / 158.4,
                LevelPromotionCalc.calcCharacterRate(70, true), 1e-6,
                "Lv70 是线性近似的偏差档位，不是游戏值（游戏表是 3.00 倍）");
    }

    /**
     * P8-1 修掉的一个潜伏 bug：低等级配"已晋阶"曾算出**负晋阶**。
     *
     * <p>原来 Lv1 + {@code promotion=true} 得到 {@code promoteCount = 1/10 - 1 = -1}，
     * 倍率 0.6 —— 于是"已晋阶"反而把 1 级面板压到 6 折（景元生命 158.4 → 95.04）。
     * 而 95.04 恰好是他**攻击**的数值，所以这个 bug 看起来像"属性数组索引错位"，
     * 极容易误诊（我一开始就误诊了）。
     *
     * <p>1 级角色不可能有负晋阶，所以下界是 0，Lv1 倍率必须恰好是 1.0。
     */
    @Test
    public void lowLevelsNeverGetNegativePromotion() {
        Assertions.assertEquals(1.0, LevelPromotionCalc.calcCharacterRate(1, true), 1e-9);
        Assertions.assertEquals(1.0, LevelPromotionCalc.calcCharacterRate(1, false), 1e-9);
        for (int level = 1; level <= 80; level++) {
            Assertions.assertTrue(LevelPromotionCalc.calcCharacterRate(level, true) >= 1.0,
                    "Lv" + level + " 已晋阶的倍率不该小于 1");
            Assertions.assertTrue(LevelPromotionCalc.calcCharacterRate(level, true)
                            >= LevelPromotionCalc.calcCharacterRate(level, false),
                    "Lv" + level + "：已晋阶的面板不该低于未晋阶");
        }
    }

    /**
     * 未晋阶的面板**不高于**已晋阶（Lv70 下确实更低 —— Lv80 两者相同，见上一条）。
     */
    @Test
    public void unpromotedPanelIsNeverHigher() {
        Character promoted = CharacterFactory.create(1204, 70, true);
        Character unpromoted = CharacterFactory.create(1204, 70, false);
        double promotedRate = LevelPromotionCalc.calcCharacterRate(70, true);
        double unpromotedRate = LevelPromotionCalc.calcCharacterRate(70, false);

        Assertions.assertTrue(promoted.getAttribute(AttributeType.HEALTH).get()
                        > unpromoted.getAttribute(AttributeType.HEALTH).get(),
                "Lv70 已晋阶的面板应当更高");
        Assertions.assertEquals(CharacterFactory.data(1204).health() * promotedRate,
                promoted.getAttribute(AttributeType.HEALTH).get(), 1e-3);
        Assertions.assertEquals(CharacterFactory.data(1204).health() * unpromotedRate,
                unpromoted.getAttribute(AttributeType.HEALTH).get(), 1e-3);
    }

    // ==================================================================
    // 字段覆盖：元素 / 命途 / 仇恨 / 能量
    // ==================================================================

    /**
     * 5 个真实角色的身份字段全表核对（值直接取自 {@code character_data.json}）。
     *
     * <p>故意让这 5 人的元素、命途、仇恨、能量**两两不同**，一个测试覆盖多档。
     */
    @Test
    public void identityFieldsMatchTheDataForTheTargetTeam() {
        assertIdentity(1204, "Jing Yuan", DamageElement.THUNDER, Path.ERUDITION, 130, 75);
        assertIdentity(1102, "Seele", DamageElement.QUANTUM, Path.HUNT, 120, 75);
        assertIdentity(1107, "Clara", DamageElement.PHYSICAL, Path.DESTRUCTION, 110, 125);
        assertIdentity(1105, "Natasha", DamageElement.PHYSICAL, Path.ABUNDANCE, 90, 100);
        assertIdentity(1001, "March 7th", DamageElement.ICE, Path.PRESERVATION, 120, 150);
    }

    /**
     * 元素解析必须**大小写不敏感**：{@code character_data.attribute} 是全小写
     * （{@code "thunder"}），而 {@code skills.json} 的 {@code element} 是首字母大写
     * （{@code "Thunder"}）。同一份数据里两种写法都存在。
     *
     * <p>修之前 {@code fromString} 是精确匹配，全小写输入会**静默返回 null** ——
     * 元素字段就成了 null，而不是报错。
     */
    @Test
    public void elementParsingIsCaseInsensitive() {
        Assertions.assertEquals(DamageElement.THUNDER, DamageElement.fromString("Thunder"));
        Assertions.assertEquals(DamageElement.THUNDER, DamageElement.fromString("thunder"));
        Assertions.assertEquals(DamageElement.THUNDER, DamageElement.fromString("THUNDER"));
        Assertions.assertEquals(DamageElement.THUNDER, DamageElement.fromString("  thunder  "),
                "首尾空白应当被忽略");
        Assertions.assertEquals(DamageElement.QUANTUM, DamageElement.fromString("Quantum"));
        Assertions.assertEquals(DamageElement.QUANTUM, DamageElement.fromString("quantum"));

        // 非伤害技能在数据里写 "Unknown" → 必须仍然是 null，不能变成某个元素
        Assertions.assertNull(DamageElement.fromString("Unknown"));
        Assertions.assertNull(DamageElement.fromString("unknown"));
        Assertions.assertNull(DamageElement.fromString(""));
        Assertions.assertNull(DamageElement.fromString(null));
    }

    @Test
    public void identityFieldsAreCaseInsensitivelyParsedForEveryCharacter() {
        // 全部 93 个角色的 attribute 都应当能解析出元素（数据里只有 7 种元素，全都存在）
        com.laosun.aluminium.Constant.CHARACTERS.forEach((cid, data) -> {
            DamageElement element = DamageElement.fromString(data.attribute());
            Assertions.assertNotNull(element,
                    "角色 " + cid + " 的 attribute=" + data.attribute() + " 解析不出元素");
        });
    }

    // ==================================================================
    // 能量边界（P3-0 A 表点名的三个）
    // ==================================================================

    /**
     * 能量上限的三个数据边界：
     * <ul>
     *   <li>1407 遐蝶 —— 全数据里唯一的 {@code null}。**必须保持 0（无能量条）**，
     *       兜底成 100 会凭空给她造出一条能量条；</li>
     *   <li>1220 飞霄 —— 12（终结技只耗 6）；</li>
     *   <li>1308 黄泉 —— 9（实际走"层数替代能量条"，见 P8-8）。</li>
     * </ul>
     */
    @Test
    public void energyEdgeCasesAreKeptFaithfully() {
        Assertions.assertNull(CharacterFactory.data(1407).maxEnergy(), "遐蝶的能量确实是 null");
        Assertions.assertEquals(0, CharacterFactory.create(1407, 80).getMaxEnergy(), 1e-9,
                "null 必须落成 0 = 没有能量条，不能兜底成 100");
        Assertions.assertFalse(CharacterFactory.create(1407, 80).hasEnergyBar(),
                "遐蝶没有能量条");

        Assertions.assertEquals(12, CharacterFactory.create(1220, 80).getMaxEnergy(), 1e-9);
        Assertions.assertEquals(9, CharacterFactory.create(1308, 80).getMaxEnergy(), 1e-9);
    }

    /**
     * 有能量条的角色：{@code hasEnergyBar()} 为真、初始能量为 0。
     */
    @Test
    public void charactersWithEnergyHaveAnEnergyBar() {
        Character jingYuan = CharacterFactory.create(1204, 80);

        Assertions.assertTrue(jingYuan.hasEnergyBar());
        Assertions.assertEquals(0, jingYuan.getCurrentEnergy(), 1e-9);
        Assertions.assertFalse(jingYuan.isEnergyFull(), "开局不该是满能量");
    }

    // ==================================================================
    // 中间入场 / 老入口
    // ==================================================================

    /**
     * 等级不同 → 面板不同（1 级与 80 级）。
     */
    @Test
    public void levelChangesThePanel() {
        Character low = CharacterFactory.create(1204, 1);
        Character high = CharacterFactory.create(1204, 80);

        Assertions.assertEquals(1, low.getLevel());
        Assertions.assertTrue(high.getAttribute(AttributeType.HEALTH).get()
                > low.getAttribute(AttributeType.HEALTH).get());
        Assertions.assertEquals(CharacterFactory.data(1204).health(),
                low.getAttribute(AttributeType.HEALTH).get(), 1e-3,
                "Lv1 就是数据基础值（倍率 1.0）");
    }

    /**
     * 老的占位入口仍然可用，但**没有**元素（如实反映"这不是角色"）。
     *
     * <p>这条同时说明为什么 P8 之后新代码不该再用它。
     */
    @Test
    public void placeholderEntryStillWorksButHasNoIdentity() {
        Character placeholder = Character.fromAttributes("hero", 10_000, 100, 100, 100);

        Assertions.assertEquals("hero", placeholder.getName());
        Assertions.assertNull(placeholder.getElement(), "占位角色没有元素");
        Assertions.assertEquals(Path.OTHER, placeholder.getPath(), "占位角色命途是兜底值");
        Assertions.assertEquals(0, placeholder.getMaxEnergy(), 1e-9, "占位角色没有能量条");
    }

    /**
     * 未知 cid → 自解释的异常；{@code exists} 可以提前问。
     */
    @Test
    public void unknownCharacterIsRejected() {
        Assertions.assertThrows(CharacterException.class, () -> CharacterFactory.create(99999, 80));
        Assertions.assertThrows(CharacterException.class, () -> CharacterFactory.data(99999));

        Assertions.assertTrue(CharacterFactory.exists(1204));
        Assertions.assertFalse(CharacterFactory.exists(99999));
    }

    /**
     * 工厂造出来的角色能直接进战斗（面板 / 元素 / 命途都齐了）。
     */
    @Test
    public void factoryCharactersCanJoinABattle() {
        Character jingYuan = CharacterFactory.create(1204, 80);
        Character seele = CharacterFactory.create(1102, 80);
        com.laosun.aluminium.models.Enemy enemy =
                com.laosun.aluminium.models.EnemyFactory.create(1002011, 90, 1);

        com.laosun.aluminium.Battle battle = new com.laosun.aluminium.Battle(
                java.util.List.of(jingYuan, seele), java.util.List.of(enemy), new java.util.Random(0));
        battle.startBattle();

        Assertions.assertEquals(3, battle.queue.size(), "2 名角色 + 1 只怪");
        Assertions.assertEquals(com.laosun.aluminium.Battle.Status.RUNNING, battle.getStatus());
        // 仇恨来自数据（景元 75 / 希儿 75），所以受击概率对半
        Assertions.assertEquals(75, battle.aggroOf(jingYuan));
        Assertions.assertEquals(75, battle.aggroOf(seele));
    }

    // ==================================================================

    private static void assertIdentity(int cid, String name, DamageElement element,
                                       Path path, double energy, int aggro) {
        Character character = CharacterFactory.create(cid, 80);

        Assertions.assertEquals(name, character.getName(), "cid " + cid);
        Assertions.assertEquals(element, character.getElement(), "cid " + cid);
        Assertions.assertEquals(path, character.getPath(), "cid " + cid);
        Assertions.assertEquals(energy, character.getMaxEnergy(), 1e-9, "cid " + cid);
        Assertions.assertEquals(aggro, character.getAggro(), "cid " + cid);
    }
}
