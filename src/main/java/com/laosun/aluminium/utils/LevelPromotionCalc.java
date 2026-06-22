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

        return baseRate + promoteCount * 0.4;
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
