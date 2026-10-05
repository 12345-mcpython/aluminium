package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 21, the fire-seed CRIT clause (2026-10-02): "变身时若[火种]大于 #5 点，每超出 1 点，卡厄斯兰那的暴击伤害提高 #6%，最多可提高 #%".
 *
 * The cap is the step count (`#/ #6 = 6`), so the six gates are the sentence. This judge sets [火种] to a chosen value and reads the CRIT DMG the transformation grants.
 */
public class WorldOdeFireSeedCritTest {
    private static final int LEVEL = 80;
    private static final int PHAINON = 1408;
    private static final int MONSTER = 1002011;
    private static final String FIRE_SEED = "火种";

    @Test
    public void theTransformationTurnsExcessSeedsIntoCritDamage() {
        double[] baseline = run(0);
        double[] thirteen = run(13);
        double[] twelve = run(12);
        double[] fifteen = run(15);
        System.out.println("[fire_crit] his own transformation grants +" + baseline[0] + " ; with seeds the total is +" + twelve[0]
                + " (12), +" + thirteen[0] + " (13), +" + fifteen[0] + " (15)");
        // Differences cancel whatever his OWN transformation grants, so each difference is purely the sentence's steps.
        // ANCHORED to `baseline` rather than compared with each other (2026-10-02): a difference is blind to a uniform shift, which is exactly what a missing step looks
        // like -- measured, the mutation that disabled step 1 moved all three readings together and the difference-only version of this judge still passed.
        Assertions.assertEquals(steps(twelve[1]) * 0.06, twelve[0] - baseline[0], 1e-6, "at the threshold, nothing is over it");
        Assertions.assertEquals(steps(thirteen[1]) * 0.06, thirteen[0] - baseline[0], 1e-6, "one seed past the threshold is one step");
        Assertions.assertEquals(steps(fifteen[1]) * 0.06, fifteen[0] - baseline[0], 1e-6, "and four seeds past it, four steps");
    }

    /** How many steps a seed count is past the threshold. */
    private static int steps(double seeds) {
        return (int) Math.max(0, Math.min(seeds - 12, 6));
    }

    /** [the CRIT DMG the transformation granted, the seeds it ended with] */
    private static double[] run(int seeds) {
        Character him = CharacterFactory.create(PHAINON, LEVEL);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        him = battle.characters.getFirst();
        // Give him the seeds DIRECTLY (2026-10-02). A hand-built table REPLACES his loaded one -- the trap this project has hit six times -- and that would delete
        // the very rules under test, along with his own transformation.
        him.getResources().gain(FIRE_SEED, seeds);
        battle.processRequests();
        // His own kit already grants a seed at battle start (`trace_worlds_end_one_seed_at_battle_start`), so the judge reads the TOTAL and derives the step count from
        // it -- the engine's own number, never a hard-coded expectation.
        int total = him.getResources().value(FIRE_SEED);
        Assertions.assertTrue(total >= seeds, "precondition: at least the seeds under test are in place, got " + total);

        double before = him.getAttribute(AttributeType.CRIT_ATTACK).get();
        var ult = him.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: he has an ultimate");
        SkillExecutor.execute(battle, ult, him, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        double after = battle.characters.getFirst().getAttribute(AttributeType.CRIT_ATTACK).get();
        return new double[]{after - before, total};
    }
}
