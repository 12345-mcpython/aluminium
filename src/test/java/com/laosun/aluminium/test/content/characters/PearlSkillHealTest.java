package com.laosun.aluminium.test.content.characters;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/** Character 1503 (Pearl): her Skill heals the party and the lowest-HP ally a SECOND time. */
public class PearlSkillHealTest {
    private static final int PEARL = 1503;
    private static final int ALLY = 1204;
    private static final String COUNTER = "\u597d\u6d3b\u5f53\u8d4f";

    @Test
    public void theSkillHealsThePartyAndTheLowestAllyAgain() {
        Battle battle = new Battle(List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ALLY, 80)),
                List.of(EnemyFactory.create(1002011, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character pearl = battle.characters.get(0);
        Character ally = battle.characters.get(1);

        pearl.takeDamage(pearl.getMaxHp() * 0.6);
        ally.takeDamage(ally.getMaxHp() * 0.8);

        double pearlBefore = pearl.getCurrentHp();
        double allyBefore = ally.getCurrentHp();
        int counterBefore = (int) pearl.getResources().get(COUNTER).getValue();

        battle.fireTriggers(TriggerEvent.SKILL_CAST, pearl, ally, 0, 0);

        double pearlGain = pearl.getCurrentHp() - pearlBefore;
        double allyGain = ally.getCurrentHp() - allyBefore;
        int counterAfter = (int) pearl.getResources().get(COUNTER).getValue();
        System.out.println("[pearl] pearl gained " + pearlGain + " ; the lowest ally gained " + allyGain
                + " ; counter " + counterBefore + " -> " + counterAfter);

        Assertions.assertTrue(pearlGain > 0, "the party heal reaches her too");
        Assertions.assertTrue(allyGain > pearlGain,
                "the ally standing lowest is healed a SECOND time (" + allyGain + " > " + pearlGain + ")");
        Assertions.assertEquals(counterBefore + 15, counterAfter, "and the Skill grants 15 of her counter");
    }
}
