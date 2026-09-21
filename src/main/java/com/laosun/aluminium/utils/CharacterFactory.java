package com.laosun.aluminium.utils;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.CharacterData;
import com.laosun.aluminium.exceptions.CharacterException;
import com.laosun.aluminium.models.Character;

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
 * <p>⚠ <b>技能仍是占位</b>：P8-1 不做技能装配，所以 {@code create()} 造出来的角色
 * 技能是 {@code DefaultSkill}（槽位 1）。真实倍率是 P8-2。
 * 本类留了 {@link #create(int, int, boolean)} 的扩展点，但**不要**在这里填技能 ——
 * 装配点应该在 P8-2 的 {@code RealSkillSet} 里。
 */
public final class CharacterFactory {
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
        return builder.build();
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
