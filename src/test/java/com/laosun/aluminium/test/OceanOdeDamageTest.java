package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 22 "献予'海洋'之诗": "本场战斗中，海瑟音造成的伤害提高 #1%" (2026-10-02).
 *
 * <p>TWO-SIDED without a magic number: the ode takes her boost to exactly the CAST row's own #1 (which runs with level), and a scene without the ode reads a different value.
 * Note: Nothing is replaced -- her table holds the rule under test.
 */
public class OceanOdeDamageTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int HYSILENS = 1410;
    private static final int MONSTER = 1002011;

    @Test
    public void theOdeRaisesHerDamageToTheRowsOwnShare() {
        double without = boost(false);
        double[] with = boostWithExpected();
        System.out.println("[ocean_damage] without the ode = " + without + " ; with it = " + with[0]
                + " (the cast row says " + with[1] + ")");

        Assertions.assertEquals(with[1], with[0], Math.abs(with[1]) * 1e-6,
                "「本场战斗中，海瑟音造成的伤害提高 #1%」-- and #1 runs with level");
        Assertions.assertNotEquals(without, with[0], 1e-9, "precondition + reading: the ode changes her boost");
    }

    private static double[] boostWithExpected() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character hysilens = CharacterFactory.create(HYSILENS, LEVEL);
        Battle battle = new Battle(List.of(cyrene, hysilens),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        hysilens = battle.characters.get(1);

        var demiurge = battle.summonServant(cyrene);
        battle.processRequests();
        var ode = demiurge.skillAt(22);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries slot 22");
        double expected = ode.getData().getSkills().get(demiurge.skillLevel(ode) - 1).get(0);
        SkillExecutor.execute(battle, ode, demiurge, List.of(hysilens));
        battle.processRequests();
        return new double[]{hysilens.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), expected};
    }

    /** Her boost with the ode never cast. */
    private static double boost(boolean ignored) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character hysilens = CharacterFactory.create(HYSILENS, LEVEL);
        Battle battle = new Battle(List.of(cyrene, hysilens),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        battle.summonServant(battle.characters.get(0));
        battle.processRequests();
        return battle.characters.get(1).getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
    }
}
