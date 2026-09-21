package com.laosun.aluminium.utils;

/**
 * Character and weapon level/promotion scaling calculator.
 *
 * <p>Computes the multiplier applied to base stats based on character/weapon level
 * and promotion status. The formulas follow the game's internal scaling rules.
 *
 * <p><b>Character scaling:</b> {@code baseRate = 1 + (level - 1) * 0.05}
 * plus a promotion bonus of {@code promoteCount * 0.4}.
 *
 * <p><b>Weapon scaling:</b> {@code baseRate = 1 + (level - 1) * 0.15}
 * plus {@code 1.6 * promoteCount} and a first-promotion bonus of 1.2.
 */
public final class LevelPromotionCalc {
    /**
     * Calculates the character stat multiplier for the given level and promotion status.
     *
     * <p>公式：{@code 1 + (等级-1)×0.05 + 晋阶次数×0.4}。晋阶次数由等级档位推出
     * （每 10 级一档、每档 0.4），也就是游戏 {@code AvatarPromotionConfig} 的 7 行：
     * 晋阶 0（≤20）/ 1（≤30）/ 2（≤40）/ 3（≤50）/ 4（≤60）/ 5（≤70）/ 6（≤80）。
     *
     * <p>⚠ <b>晋阶次数要 clamp 到 ≥ 0</b>（P8-1 修正）：原来低等级配 {@code promotion=true}
     * 会算出**负数**（Lv1 → {@code 1/10 - 1 = -1}），于是"已晋阶"反而把 Lv1 面板压到 0.6 倍
     * （景元基础生命 158.4 → 95.04，正好等于他的**攻击** 95.04，看串了非常容易误判成索引错位）。
     * 一个 1 级角色不可能"负晋阶"，所以下界必须是 0。
     *
     * @param level     character level (1-80)
     * @param promotion whether the character is promoted at the current ascension threshold
     * @return the stat multiplier
     */
    public static double calcCharacterRate(int level, boolean promotion) {
        double baseRate = 1 + (level - 1) * 0.05;
        int promoteCount = level / 10 - (promotion ? 1 : 2);

        if (promotion && level == 80) {
            promoteCount -= 1;
        }
        if (level <= 20 && !promotion) {
            promoteCount = 0;
        }

        return baseRate + Math.max(0, promoteCount) * 0.4;   // 负晋阶不存在
    }

    /**
     * Calculates the weapon stat multiplier for the given level and promotion status.
     *
     * @param level     weapon level (1-80)
     * @param promotion whether the weapon is promoted at the current ascension threshold
     * @return the stat multiplier
     */
    public static double calcWeaponRate(int level, boolean promotion) {
        double baseRate = 1.00 + (level - 1) * 0.15;

        boolean firstPromote = level > 20 || (level == 20 && promotion);

        int promoteCount = 0;
        if (level > 20) {
            promoteCount = level / 10 - (promotion ? 2 : 3);
            if (promotion && level == 80) {
                promoteCount--;
            }
        }

        return baseRate + 1.6 * promoteCount + (firstPromote ? 1.2 : 0);
    }

    /**
     * Calculates the character stat multiplier without promotion.
     *
     * @param level character level (1-80)
     * @return the stat multiplier
     */
    public static double calcCharacterRate(int level) {
        return calcCharacterRate(level, false);
    }

    /**
     * Calculates the weapon stat multiplier without promotion.
     *
     * @param level weapon level (1-80)
     * @return the stat multiplier
     */
    public static double calcWeaponRate(int level) {
        return calcWeaponRate(level, false);
    }
}
