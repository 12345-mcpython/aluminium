package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * GAIN_RESOURCE captures the CAST skill parameter (2026-10-02).
 *
 * Why it matters: a memosprite skill row is unreachable by EVERY share -- Summon keys its skills by int data slot while shares read Character.getSkills(), keyed by SkillType --
 * but the cast moment CAN read it, because castParamValue goes through the event actor. So a number that belongs to an ode is captured into a resource as it is cast.
 *
 * The reader is a content rule on 1412 (the character the law ode is aimed at), because two preconditions were measured to be necessary: the rule must sit on the AIMED character
 * table (a memosprite cast does not reach the memosprite own file), and the resource must be declared BY CONTENT (a table installed before battle start is never registered).
 *
 * Two-sided: with the ode the charge rises by the row value; with no ode it does not move. And the read is the character's OWN store -- measured, that is where the credit lands,
 * while the party store is a separate copy.
 */
public class CaptureCastParamTest {
    private static final int LEVEL = 80;
    private static final String CHARGE = "\u5145\u80fd";
    @Test
    public void theChargeGainsTheOdeRowsValue() {
        int[] with = run(true);
        int[] without = run(false);
        System.out.println("[capture] row value " + with[2] + " ; charge " + with[0] + " -> " + with[1]
                + " ; without it " + without[0] + " -> " + without[1]);
        // \u2b50 The capture stores BASIS POINTS now (percent: 10000), because the row value is a share below 1 while a resource holds an integer -- and her charge is
        // capped at 8, so the reading saturates there. Both halves are the engine's own numbers: the row value read from the memosprite, and her declared cap.
        Assertions.assertEquals(Math.min(10000 * Math.round(with[2]), 8), with[1],
                "the charge rises by the captured basis points, capped at her 8");
        Assertions.assertEquals(without[0], without[1], "and with no ode it does not move");
    }
    /** [charge before, charge after, the row value] -- casting the ode, or not. */
    private static int[] run(boolean castTheOde) {
        Character cyrene = CharacterFactory.create(1415, LEVEL);
        Character cerydra = CharacterFactory.create(1412, LEVEL);
        Battle battle = new Battle(List.of(cyrene, cerydra),
                List.of(EnemyFactory.create(1002011, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        cerydra = battle.characters.get(1);
        var demiurge = battle.summonServant(cyrene);
        battle.processRequests();
        var ode = demiurge.skillAt(23);
        Assertions.assertNotNull(ode, "precondition: slot 23");
        double rowValue = ode.getData().getSkills().get(demiurge.skillLevel(ode) - 1).get(1);
        int before = cerydra.getResources().value(CHARGE);
        if (castTheOde) {
            SkillExecutor.execute(battle, ode, demiurge, List.of(cerydra));
            battle.processRequests();
        }
        return new int[]{before, cerydra.getResources().value(CHARGE), (int) Math.round(rowValue)};
    }
}
