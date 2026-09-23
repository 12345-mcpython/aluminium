package com.laosun.aluminium.utils;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.CharacterData;
import com.laosun.aluminium.exceptions.CharacterException;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.energy.EnergyProvider;
import com.laosun.aluminium.models.energy.NoConventionalEnergyProvider;

import java.util.Set;

/**
 * 角色工厂（P8-1）：一条命令造出**真实角色**。
 *
 * <pre>{@code
 * Character jingYuan = CharacterFactory.create(1204, 80);
 * jingYuan.getElement();      // THUNDER
 * jingYuan.getPath();         // ERUDITION
 * jingYuan.getMaxEnergy();    // 130
 * jingYuan.getAggro();        // 75
 * }</pre>
 *
 * <p>它其实就是 {@code Character.builder()} 的一层薄封装 —— 面板管线（等级缩放 / 光锥 /
 * 遗器 / 行迹 / 额外加成）在 P2 就完备了，P8-1 补的是**角色身份字段**：
 * 元素、命途、仇恨、能量上限（见 {@code Character.Builder#build()}）。
 *
 * <p>与 {@link Character#fromAttributes} 的分工：那个是测试/占位入口（无元素、无命途、
 * 能量上限 0、技能全占位），这个是真实角色入口。**P8 之后新代码一律用这个。**
 *
 * <p><b>技能装配在 P8-2 已接</b>：{@code create()} 造出来的角色带**真实槽位映射**的
 * {@code DefaultSkill}（普攻 1 / 战技 2 / 终结技 3 / 天赋 4），映射表只有一份
 * （{@code Constant.SKILL_SLOT}），装配点是 {@code Character.Builder#build()}
 * —— 见 {@code engine.md} §7.2。地图普攻（6）/ 秘技（7）不在这里装，
 * 由 {@code Battle.startBattle()} 附加。
 *
 * <p>⚠ 本类**只管角色身份与资源**，不碰技能倍率：追加攻击/召唤物是 P8-3/P9-4。
 */
public final class CharacterFactory {
    /**
     * 走**层数/特殊资源**而不是常规能量的角色（P8-0 三分法里的"引擎还不具备的能力"）。
     *
     * <p>他们在游戏里攒的是【追忆】/【新蕊】/【火种】/点数，常规回能对他们是无意义的；
     * 而 {@code castUltra} 只看 {@code currentEnergy >= maxEnergy}，
     * 所以不拦的话他们能靠"挨打"凑满并放出不该存在的终结技（黄泉上限才 9）。
     *
     * <p>判定放在装配点是 P8-0 明确允许的（provider 注册表 / 装配点是唯一允许出现 cid 的地方）。
     * 等 P8-8 的 {@code Resource} 落地后，这张表演化成"角色 → 资源实现"的注册表。
     */
    private static final Set<Integer> SPECIAL_RESOURCE_CHARACTERS = Set.of(
            1220,   // 飞霄：层数（大招阈值 6，上限 12）
            1308,   // 黄泉：层数（上限 9）
            1407,   // 遐蝶：【新蕊】（max_energy 为 null，本来就没有能量条）
            1408,   // 白厄：【火种】（上限 12）
            1415,   // 昔涟：【追忆】（见 engine.md §9.5）
            1506    // 银狼LV.999：欢愉体系
    );

    /** 上述角色共用的"不入账"provider（无状态，可共享）。 */
    private static final EnergyProvider NO_CONVENTIONAL_ENERGY = new NoConventionalEnergyProvider();

    private CharacterFactory() {
    }

    /**
     * 造一个满晋阶的真实角色。
     *
     * <p>"满晋阶"指晋阶到当前等级的上限（Lv80 → 晋阶 6 次），这正是
     * {@code LevelPromotionCalc.calcCharacterRate(level, true)} 里的 {@code true}。
     * 景元 Lv80 已晋阶的生命正好 = {@code 158.4 × 7.35 = 1164.24}，与游戏内一致。
     *
     * @param cid   角色 id（见 {@code character_data.json}）
     * @param level 等级（1-80）
     * @return 真实角色
     * @throws CharacterException 角色不存在
     */
    public static Character create(int cid, int level) {
        return create(cid, level, true);
    }

    /**
     * 造一个真实角色。
     *
     * @param cid       角色 id
     * @param level     等级（1-80）
     * @param promoted  是否已晋阶（{@code false} = 未晋阶，面板更低；两者差异见
     *                  {@link LevelPromotionCalc#calcCharacterRate(int, boolean)}）
     * @return 真实角色
     * @throws CharacterException 角色不存在
     */
    public static Character create(int cid, int level, boolean promoted) {
        Character.Builder builder = Character.builder().cid(cid).level(level);
        if (promoted) {
            builder = builder.isPromote();
        }
        Character character = builder.build();
        // 层数/特殊资源角色：换掉常规回能（否则靠挨打就能凑满能量、放出不该有的终结技）
        if (SPECIAL_RESOURCE_CHARACTERS.contains(cid)) {
            character.setEnergyProvider(NO_CONVENTIONAL_ENERGY);
        }
        return character;
    }

    /**
     * 这个角色是否走层数/特殊资源（而非常规能量）。
     *
     * <p>给调用方一个"先问再接"的口子，也方便测试与将来的 P8-8 注册表复用同一张表。
     */
    public static boolean usesSpecialResource(int cid) {
        return SPECIAL_RESOURCE_CHARACTERS.contains(cid);
    }

    /**
     * 角色是否存在（数据里有没有这个 id）。
     *
     * <p>给调用方一个"先问再建"的口子，免得靠 catch {@link CharacterException} 探路。
     */
    public static boolean exists(int cid) {
        return Constant.CHARACTERS.containsKey(cid);
    }

    /**
     * 取原始角色数据（不做面板计算）。
     *
     * @param cid 角色 id
     * @return 数据行
     * @throws CharacterException 角色不存在
     */
    public static CharacterData data(int cid) {
        CharacterData data = Constant.CHARACTERS.get(cid);
        if (data == null) {
            throw new CharacterException.CharacterNotFoundException(
                    String.format("Character '%s' not found", cid));
        }
        return data;
    }
}
