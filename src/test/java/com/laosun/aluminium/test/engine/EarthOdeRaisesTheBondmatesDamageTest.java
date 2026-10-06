package com.laosun.aluminium.test.engine;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/** "当他持有[献予'大地'之诗]时，[同袍]造成的伤害提高 0.12%". */
public class EarthOdeRaisesTheBondmatesDamageTest {
    @Test
    public void theBondmateGainsTheSmallBoost() {
        Character cyrene = CharacterFactory.create(1415, 80);
        Character him = CharacterFactory.create(1414, 80);
        Character ally = CharacterFactory.create(1405, 80);
        Battle battle = new Battle(List.of(cyrene, him, ally),
                List.of(EnemyFactory.create(1002011, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        // his own skill hands [同袍] to the one it is aimed at
        him = battle.characters.get(1);
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, him.getSkills().get(SkillType.SKILL), him,
                List.of(battle.characters.get(2)));
        battle.processRequests();
        Assertions.assertTrue(battle.characters.get(2).getBuffManager().hasState("同袍"),
                "precondition: the ally holds 【同袍】");
        double before = battle.characters.get(2).getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        // the ode, cast at him
        var sprite = battle.summonServant(battle.characters.get(0));
        battle.processRequests();
        var ode = sprite.skillAt(25);
        Assertions.assertNotNull(ode, "precondition: slot 25");
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, ode, sprite,
                List.of(battle.characters.get(1)));
        battle.processRequests();
        double after = battle.characters.get(2).getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        System.out.println("[earth_boost] the bondmate's damage boost reads " + before + " -> " + after);
        Assertions.assertEquals(0.0012, after - before, 1e-9, "0.12% lands on the 【同袍】 holder");
    }
}
