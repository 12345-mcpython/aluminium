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
 * 1505 Evanescia (绯英)'s energy sync (2026-09-30): "when Evanescia gains energy, she gains an equal amount of [好活当赏] at the same time".
 *
 * <p>The energy comes from the ENGINE's own credit (casting a basic attack), not from a hand-written rule, and the old table is
 * never replaced -- an earlier version of this test did that and silently removed the very rule under test (measured: it read 20,
 * the technique's gift alone). The expectation is the MEASURED energy delta, so the judge states the rule rather than a number
 * borrowed from somewhere else.
 */
public class Character1505EnergySyncTest {
    private static final int WEARER = 1505;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String GIFTS = "好活当赏";

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
                "every point of energy is mirrored one-for-one into [好活当赏]");
    }

    @Test
    public void withNoEnergyNothingIsMirrored() {
        Character elation = CharacterFactory.create(WEARER, LEVEL);
        Battle battle = new Battle(List.of(elation, CharacterFactory.create(ALLY, LEVEL)),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        int gifts = elation.getResources().value(GIFTS);
        System.out.println("[1505-sync] before any energy is credited the gifts read " + gifts);
        Assertions.assertEquals(20, gifts, "only the technique’s 20 so far (the false side of the sync)");
    }
}
