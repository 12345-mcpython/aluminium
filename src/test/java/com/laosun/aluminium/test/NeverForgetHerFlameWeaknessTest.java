package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 23050 At Will (随心): "when the wearer adds a weakness to an enemy target, restores 1 skill point; this effect can trigger at most 1 time, and casting the Ultimate resets the number of triggerable times".
 *
 * <p>Note: This case builds no fixture: it lets two real pieces of content interact - character `1315`'s own rule adds a Physical weakness to the target on the Ultimate
 * (the reader of this section's twelfth `ADD_ELEMENTAL_WEAKNESS`), and the `23050` rule reacts to "a weakness was added".
 * Note: Stuffing in a probe with `setTriggerTable` would replace the light cone's own table, so the case would really test
 * "23050 installed but without 23050's rules" - measured `before=2 after=2`, while the weakness really is added.
 *
 * <p>Note: Both preconditions must be asserted, or the case stays green while testing nothing: (1) full energy (`castUltra` returns false outright when it is not full);
 * (2) skill points below the cap (the policy is start 3 / max 5, and at the cap `gainSkillPoint(1)` would be silently truncated).
 */
public class NeverForgetHerFlameWeaknessTest {
    private static final int WEARER = 1315;
    private static final int MONSTER = 1002011;
    private static final int LEVEL = 80;

    /** 1315 at rank 1 wearing 23050, facing one enemy; returns {battle, wearer, enemy}. */
    private static Object[] scene() {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(23050, LEVEL, false, 1));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        return new Object[]{battle, wearer, enemy};
    }

    /** Casts the Ultimate once (filling energy first), returns {skill points before, skill points after}. */
    private static int[] castOnce(Battle battle, Character wearer, Enemy enemy) {
        wearer.setCurrentEnergy(wearer.getMaxEnergy());
        battle.spendSkillPoint();                     // Note: make room: at the cap nothing could be measured
        int before = battle.getSkillPoints();
        Assertions.assertTrue(before < battle.getSkillPointMax(),
                "the judge needs room below the cap: " + before + "/" + battle.getSkillPointMax());
        boolean cast = battle.castUltra(wearer, List.of(enemy));
        Assertions.assertTrue(cast, "the ultimate must be cast at full energy");
        return new int[]{before, battle.getSkillPoints()};
    }

    @Test
    public void theUltimateInsertsAWeaknessAndTheConePaysOnePoint() {
        Object[] s = scene();
        Battle battle = (Battle) s[0];
        Character wearer = (Character) s[1];
        Enemy enemy = (Enemy) s[2];
        Assertions.assertFalse(enemy.isWeakTo(DamageElement.PHYSICAL),
                "the enemy must not already have Physical, or 1315 inserts nothing");

        int[] p = castOnce(battle, wearer, enemy);
        System.out.println("[23050] first ult: points " + p[0] + " -> " + p[1]
                + " ; physical=" + enemy.isWeakTo(DamageElement.PHYSICAL));
        Assertions.assertTrue(enemy.isWeakTo(DamageElement.PHYSICAL), "1315's own rule must have inserted it");
        Assertions.assertEquals(1, p[1] - p[0], "a real insertion grants exactly one skill point");
    }

    @Test
    public void aSecondUltimateOnTheSameWeaknessPaysNothing() {
        Object[] s = scene();
        Battle battle = (Battle) s[0];
        Character wearer = (Character) s[1];
        Enemy enemy = (Enemy) s[2];

        int[] first = castOnce(battle, wearer, enemy);
        int[] second = castOnce(battle, wearer, enemy);       // Note: the same element, which the enemy already has
        System.out.println("[23050] second ult: first=" + (first[1] - first[0])
                + " second=" + (second[1] - second[0]) + " ; physical=" + enemy.isWeakTo(DamageElement.PHYSICAL));
        Assertions.assertEquals(1, first[1] - first[0], "the first insertion pays");
        Assertions.assertEquals(0, second[1] - second[0],
                "WEAKNESS_ADDED fires only when an insertion actually inserts, so the second pays nothing");
    }
}
