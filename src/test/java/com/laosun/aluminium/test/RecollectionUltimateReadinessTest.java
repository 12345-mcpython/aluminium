package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/** "[追忆]达到 24 点时可激活终结技" and "处于[往昔的涟漪]时 12 点" (2026-10-02). */
public class RecollectionUltimateReadinessTest {
    private static final String MEMORY = "追忆";

    @Test
    public void theCounterIsWhatMakesHerUltimateCastable() {
        boolean none = ready(0, false);
        boolean twelve = ready(11, false);
        boolean twelveInside = ready(11, true);
        boolean twentyFour = ready(23, false);
        System.out.println("[readiness] one point short of each line: bare 0 = " + none + " ; reaching 12 = " + twelve
                + " ; reaching 12 in the ripple = " + twelveInside + " ; reaching 24 = " + twentyFour);
        Assertions.assertFalse(none, "a single point of recollection activates nothing");
        Assertions.assertFalse(twelve, "and twelve is not enough on its own");
        Assertions.assertTrue(twelveInside, "inside the ripple twelve is");
        Assertions.assertTrue(twentyFour, "and twenty-four always is");
    }

    /** Seeds just short of the line and lets her BASIC carry her over it, which is what raises the event. */
    private static boolean ready(int seeded, boolean insideTheRipple) {
        Character her = CharacterFactory.create(1415, 80);
        Battle battle = new Battle(List.of(her), List.of(EnemyFactory.create(1002011, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        her = battle.characters.getFirst();
        if (insideTheRipple) {
            com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, her.getSkills().get(SkillType.ULTRA),
                    her, List.of(battle.enemies.getFirst()));
            battle.processRequests();
            her = battle.characters.getFirst();
            Assertions.assertTrue(her.getBuffManager().hasState("往昔的涟漪"),
                    "precondition: the ripple came up with her ultimate");
        }
        her.getResources().gain(MEMORY, seeded);
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, her.getSkills().get(SkillType.COMMON), her,
                List.of(battle.enemies.getFirst()));
        battle.processRequests();
        return battle.isUltraReady(battle.characters.getFirst());
    }
}
