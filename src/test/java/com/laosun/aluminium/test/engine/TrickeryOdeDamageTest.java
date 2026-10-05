package com.laosun.aluminium.test.engine;

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
 * 1415's memosprite skill 20 "献予'诡计'之诗": "使赛飞儿造成的伤害提高 #1%" (2026-10-02).
 *
 * <p>Two scenes that differ by exactly one thing: whether the ode was cast at her. Note: Nothing is ever replaced -- the table trap has already deleted a rule under
 * test once in this project.
 */
public class TrickeryOdeDamageTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int CIPHER = 1406;
    private static final int MONSTER = 1002011;

    @Test
    public void theOdeRaisesHerDamage() {
        Boost withoutOde = damageBoost(false);
        Boost withOde = damageBoost(true);
        System.out.println("[trickery] without the ode = " + withoutOde + " ; with it = " + withOde.gain
                + " (the cast row says " + withOde.expected + ")");

        // Her OWN kit already carries an `ALL_DAMAGE_TYPE_BOOST` (measured 0.2), and the ode's modifier REPLACES it rather than adding to it --
        // same attribute, same target, and that is the engine's own rule. So the control is a FRESH character, not zero.
        double hers = CharacterFactory.create(CIPHER, LEVEL).getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        Assertions.assertNotEquals(withoutOde.gain, withOde.gain, 1e-9,
                "casting the ode CHANGES her boost -- and the change is the cast row’s own #1");
        Assertions.assertEquals(withOde.expected, withOde.gain, Math.abs(withOde.expected) * 1e-6,
                "「使赛飞儿造成的伤害提高 #1%」-- and #1 runs with level");
    }

    private record Boost(double gain, double expected) {
    }

    private static Boost damageBoost(boolean castTheOde) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character cipher = CharacterFactory.create(CIPHER, LEVEL);
        Battle battle = new Battle(List.of(cyrene, cipher),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        cipher = battle.characters.get(1);

        double expected = 0.0;
        if (castTheOde) {
            var demiurge = battle.summonServant(cyrene);
            var ode = demiurge.skillAt(20);
            Assertions.assertNotNull(ode, "precondition: the memosprite carries slot 20");
            expected = ode.getData().getSkills().get(demiurge.skillLevel(ode) - 1).get(0);
            SkillExecutor.execute(battle, ode, demiurge, List.of(cipher));
            battle.processRequests();
        }
        return new Boost(cipher.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), expected);
    }
}
