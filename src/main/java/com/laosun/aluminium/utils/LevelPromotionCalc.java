package com.laosun.aluminium.utils;

public final class LevelPromotionCalc {
    public static double calcCharacterRate(int level, boolean promotion) {
        double baseRate = 1 + (level - 1) * 0.05;
        int promoteCount = level / 10 - (promotion ? 1 : 2);

        if (promotion && level == 80) {
            promoteCount -= 1;
        }
        if (level <= 20) {
            promoteCount = 0;
        }

        return baseRate + promoteCount * 0.4;
    }

    public static double calcAttackerRate(int level, boolean promotion) {
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

    public static double calcCharacterRate(int level) {
        return calcCharacterRate(level, false);
    }

    public static double calcAttackerRate(int level) {
        return calcAttackerRate(level, false);
    }
}
