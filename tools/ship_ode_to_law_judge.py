"""Judge for 1415's ode-to-law rule (objective ①-b's fifth reader).

End-to-end reading with both characters in one battle:
  * 1412's ultimate gives 2 charge, so three of them reach 6 -- the threshold where her own rule turns 【军功】/charge into
    【爵位】;
  * her SKILL cast then triggers 奇袭: `coup_de_main` commands a copy of the skill, aimed at the unit that triggered it;
  * when that copy ends, her own rule spends 6 charge and drops 【爵位】, and 1415's new rule pays 1 charge to the event's
    target -- the unit the copy was aimed at, which is her.

So the reading is 6 - 6 + 1 = 1, and 【爵位】 is gone. Without 1415's rule the same battle ends at 0.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/OdeToLawChargeTest.java"

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

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
 * 「奇袭结束后，使刻律德菈获得 1 点充能」 (2026-10-02).
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
    private static final String CHARGE = "\\u5145\\u80fd";
    private static final String PEERAGE = "\\u7235\\u4f4d";

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
        Assertions.assertTrue(cerydra.getBuffManager().hasState(PEERAGE), "precondition: the peerage is up");

        // the skill cast that triggers the coup
        battle.castImmediate(cerydra.getSkills().get(SkillType.SKILL), cerydra, List.of(victim));
        battle.processRequests();

        int after = cerydra.getResources().value(CHARGE);
        System.out.println("[ode-to-law] charge after the coup=" + after
                + " peerage=" + cerydra.getBuffManager().hasState(PEERAGE));
        Assertions.assertFalse(cerydra.getBuffManager().hasState(PEERAGE), "the coup took the peerage off");
        Assertions.assertEquals(1, after, "six spent by the coup, one paid by 1415's ode -- 6 - 6 + 1");
    }
}
''')
print("ok   judge written")
