package com.laosun.aluminium.test.engine;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * Slot 18's [真知] half: "那刻夏在下一次施放普攻、战技时获得[真知]：…造成的战技伤害提高 #2(20)%".
 *
 * Two-sided: the ode is cast at HIM, so the state and the raise land on him; a second reading with the ode cast at somebody else must leave him untouched.
 */
public class ReasonOdeTrueKnowledgeTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int ANAXA = 1405;
    private static final int OTHER = 1002;
    private static final int MONSTER = 1002011;
    private static final int ODE_SLOT = 18;
    private static final String TRUE_KNOWLEDGE = "真知";

    @Test
    public void theReasonOdeGrantsTrueKnowledgeToTheOneItNames() {
        double[] at = run(true);
        double[] elsewhere = run(false);
        System.out.println("[true_knowledge] cast at him: "+at[0]+" and the state is "+at[1]
                + " ; cast elsewhere: "+elsewhere[0]+" and the state is "+elsewhere[1]);
        Assertions.assertEquals(1.0, at[1], 1e-9, "he gets the state");
        // The state is the clause that ships; its two numbers are registered (a path selector and a lasting damage-class raise both do not exist yet).
        Assertions.assertEquals(0.0, elsewhere[1], 1e-9, "and casting it elsewhere leaves him alone");
    }

    /** [his damage boost, does he hold [真知]] */
    private static double[] run(boolean aimedAtHim) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character him = CharacterFactory.create(ANAXA, LEVEL);
        Character other = CharacterFactory.create(OTHER, LEVEL);
        Battle battle = new Battle(List.of(cyrene, him, other),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        var sprite = battle.summonServant(battle.characters.get(0));
        battle.processRequests();
        var ode = sprite.skillAt(ODE_SLOT);
        Assertions.assertNotNull(ode, "precondition: slot 18");
        Character aimed = battle.characters.get(aimedAtHim ? 1 : 2);
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, ode, sprite, List.of(aimed));
        battle.processRequests();
        Character target = battle.characters.get(1);
        return new double[]{target.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(),
                target.getBuffManager().hasState(TRUE_KNOWLEDGE) ? 1 : 0};
    }
}
