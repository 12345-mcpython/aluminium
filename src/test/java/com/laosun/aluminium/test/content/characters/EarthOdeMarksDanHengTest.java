package com.laosun.aluminium.test.content.characters;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * Slot 25 "献予'大地'之诗" ("Ode to 'Earth'"): the audit found this whole skill missing, and these are the two clauses it can carry exactly.
 *
 * Two-sided: with the ode cast at Dan Heng - Permansor Terrae (丹恒-腾荒) he gains the state; with it cast at Cyrene (昔涟) he does not.
 */
public class EarthOdeMarksDanHengTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int DAN_HENG = 1414;
    private static final int MONSTER = 1002011;
    private static final int ODE_SLOT = 25;
    private static final String STATE = "献予「大地」之诗";

    @Test
    public void theEarthOdeMarksTheOneItIsCastAt() {
        Assertions.assertEquals(1.0, state(true), 1e-9, "cast at him, he gains the poem offered to Earth (献予「大地」之诗)");
        Assertions.assertEquals(0.0, state(false), 1e-9, "cast at someone else, he does not");
    }

    private static double state(boolean aimedAtHim) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character him = CharacterFactory.create(DAN_HENG, LEVEL);
        Battle battle = new Battle(List.of(cyrene, him),
                List.of(EnemyFactory.create(MONSTER, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        var sprite = battle.summonServant(battle.characters.get(0));
        battle.processRequests();
        var ode = sprite.skillAt(ODE_SLOT);
        Assertions.assertNotNull(ode, "precondition: slot 25");
        Character aimed = battle.characters.get(aimedAtHim ? 1 : 0);
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, ode, sprite, List.of(aimed));
        battle.processRequests();
        return battle.characters.get(1).getBuffManager().hasState(STATE) ? 1 : 0;
    }
}
