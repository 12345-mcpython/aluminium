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
 * 1415's memosprite skill 19 「献予「天空」之诗」: 「对风堇施放时，为风堇恢复 #2 点能量」 (2026-10-02).
 *
 * <p>⭐ Two scenes that differ by exactly one thing: whether the ode was cast at her. #2 runs with the level (12 -> 33.6), so the expected number comes out of the CAST
 * skill's row. ⚠ Her energy is zeroed BEFORE the cast -- reading an increment needs its baseline set before the action.
 */
public class SkyOdeEnergyTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int HYACINE = 1409;
    private static final int MONSTER = 1002011;

    @Test
    public void theOdeRestoresHerEnergy() {
        double withoutOde = energyAfter(false);
        double withOde = energyAfter(true);

        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character hyacine = CharacterFactory.create(HYACINE, LEVEL);
        Battle probe = new Battle(List.of(cyrene, hyacine),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        probe.startBattle();
        probe.processRequests();
        var demiurge = probe.summonServant(probe.characters.get(0));
        var ode = demiurge.skillAt(19);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries slot 19");
        // ⚠ Energy is granted as a WHOLE number -- `gainEnergyFor` rounds the derived amount -- so the expectation is the rounded row value, not the row value.
        double expected = Math.round(ode.getData().getSkills().get(demiurge.skillLevel(ode) - 1).get(1));

        System.out.println("[sky] her energy without the ode = " + withoutOde + " ; with it = " + withOde
                + " (the cast row says " + expected + ")");

        Assertions.assertEquals(0.0, withoutOde, 1e-9, "precondition: the ode is what moves it");
        Assertions.assertEquals(expected, withOde, Math.abs(expected) * 1e-6,
                "「为风堇恢复 #2 点能量」-- and #2 runs with level");
    }

    private static double energyAfter(boolean castTheOde) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character hyacine = CharacterFactory.create(HYACINE, LEVEL);
        Battle battle = new Battle(List.of(cyrene, hyacine),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        hyacine = battle.characters.get(1);

        hyacine.setCurrentEnergy(0);
        if (castTheOde) {
            var demiurge = battle.summonServant(cyrene);
            var ode = demiurge.skillAt(19);
            Assertions.assertNotNull(ode, "precondition: slot 19");
            SkillExecutor.execute(battle, ode, demiurge, List.of(hyacine));
            battle.processRequests();
        }
        return hyacine.getCurrentEnergy();
    }
}
