package com.laosun.aluminium.test.engine;

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
 * "奇袭结束后，使刻律德菈获得 1 点充能" (2026-10-02).
 *
 * <p>The sentence is 1415's memosprite skill 15. Everything it needs was measured before the rule was written: the moment
 * (INSERTED_CAST_END), the aimed unit (item 66 made the event carry it), and the resource declaration (the loader checks per
 * file, so her file declares the same id 1412 does).
 */
public class OdeToLawChargeTest {
    private static final int CERYDRA = 1412;
    private static final int CYRENE = 1415;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String CHARGE = "充能";
    private static final String PEERAGE = "爵位";

    /** After the coup: the charge she had, minus the six the coup costs, plus the one 1415 pays. */
    @Test
    public void theCoupPaysHerOneCharge() {
        Character cerydra = CharacterFactory.create(CERYDRA, LEVEL, false, null, null, 0);
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL, false, null, null, 0);
        Enemy victim = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(cerydra, cyrene), List.of(victim), new Random(0));
        battle.startBattle();
        battle.processRequests();

        // three ultimates: +2 charge each, which is the six her own rule upgrades on
        for (int i = 0; i < 3; i++) {
            battle.castImmediate(cerydra.getSkills().get(SkillType.ULTRA), cerydra, List.of(victim));
            battle.processRequests();
        }
        Assertions.assertEquals(6, cerydra.getResources().value(CHARGE), "precondition: six charge");
        // Note: Applied directly: measured, the six-charge upgrade's RESOURCE_CHANGED trigger does not fire in this scene, and
        // this reading is about the ode's rule, not about that one.
        cerydra.getBuffManager().addBuff(
                new com.laosun.aluminium.models.buff.StateBuff(PEERAGE, 9, true));
        Assertions.assertTrue(cerydra.getBuffManager().hasState(PEERAGE), "precondition: the peerage is up");

        // the skill cast that triggers the coup
        battle.castImmediate(cerydra.getSkills().get(SkillType.SKILL), cerydra, List.of(victim));
        battle.processRequests();

        int after = cerydra.getResources().value(CHARGE);
        System.out.println("[ode-to-law] charge after the coup=" + after
                + " peerage=" + cerydra.getBuffManager().hasState(PEERAGE));
        Assertions.assertFalse(cerydra.getBuffManager().hasState(PEERAGE), "the coup took the peerage off");
        // The arithmetic the engine produces, and every term of it is a sentence: 6 + 1 (her own skill grants one) +
        // 1 (the COMMANDED COPY casts the same skill again -- "复制一次即将施放的技能并提前施放，随后施放原技能") - 6 (the coup's end
        // spends six) + 1 (1415's ode pays one) = 3.
        Assertions.assertEquals(3, after,
                "6 + 1 + 1 - 6 + 1: her skill twice (the copy is the coup), the coup's six, and the ode's one");
    }
}
