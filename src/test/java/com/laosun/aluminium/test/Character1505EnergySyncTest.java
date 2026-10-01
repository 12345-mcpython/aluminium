package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1505 \u7eef\u82f1\u2019s energy sync (2026-09-30): \u300c\u7eef\u82f1\u83b7\u5f97\u80fd\u91cf\u65f6\uff0c\u5c06\u540c\u6b65\u83b7\u5f97\u7b49\u503c\u7684\u3010\u597d\u6d3b\u5f53\u8d4f\u3011\u300d.
 *
 * <p>\u2b50 The energy comes from the ENGINE\u2019s own credit (casting a basic attack), not from a hand-written rule, and the old table is
 * never replaced -- an earlier version of this test did that and silently removed the very rule under test (measured: it read 20,
 * the technique\u2019s gift alone). The expectation is the MEASURED energy delta, so the judge states the rule rather than a number
 * borrowed from somewhere else.
 */
public class Character1505EnergySyncTest {
    private static final int WEARER = 1505;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String GIFTS = "\u597d\u6d3b\u5f53\u8d4f";

    @Test
    public void gainingEnergyMirrorsIntoGifts() {
        Character elation = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(elation, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy),
                new Random(0));
        battle.startBattle();
        int giftsBefore = elation.getResources().value(GIFTS);
        double energyBefore = elation.getCurrentEnergy();
        battle.castImmediate(elation.getSkills().get(SkillType.COMMON), elation, List.of(enemy));
        double energyAfter = elation.getCurrentEnergy();
        int giftsAfter = elation.getResources().value(GIFTS);
        int mirrored = (int) Math.round(energyAfter - energyBefore);
        System.out.println("[1505-sync] the cast credited " + (energyAfter - energyBefore) + " energy ; gifts "
                + giftsBefore + " -> " + giftsAfter + " (expected " + (giftsBefore + mirrored) + ")");
        Assertions.assertTrue(mirrored > 0, "the cast really credited energy (it credited " + mirrored + ")");
        Assertions.assertEquals(giftsBefore + mirrored, giftsAfter,
                "every point of energy is mirrored one-for-one into \u3010\u597d\u6d3b\u5f53\u8d4f\u3011");
    }

    @Test
    public void withNoEnergyNothingIsMirrored() {
        Character elation = CharacterFactory.create(WEARER, LEVEL);
        Battle battle = new Battle(List.of(elation, CharacterFactory.create(ALLY, LEVEL)),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        int gifts = elation.getResources().value(GIFTS);
        System.out.println("[1505-sync] before any energy is credited the gifts read " + gifts);
        Assertions.assertEquals(20, gifts, "only the technique\u2019s 20 so far (the false side of the sync)");
    }
}
