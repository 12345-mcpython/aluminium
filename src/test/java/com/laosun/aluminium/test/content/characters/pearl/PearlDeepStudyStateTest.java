package com.laosun.aluminium.test.content.characters.pearl;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Character 1503 (Pearl): the Ultimate marks an ally as the archetype and Pearl as its deep learner.
 */
public class PearlDeepStudyStateTest {
    private static final int PEARL = 1503;
    private static final int ELATION = 1502;
    private static final int HUNTER = 1204;
    private static final String ARCHETYPE = "美学底本";
    private static final String DEEP = "深度学习";
    private static final String COUNTER = "好活当赏";

    @Test
    public void theUltimateMarksTheArchetypeAndTheDeepLearner() {
        Battle battle = new Battle(List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ELATION, 80),
                CharacterFactory.create(HUNTER, 80)), List.of(EnemyFactory.create(1002011, 100, 1)), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character pearl = battle.characters().get(0);
        Character elation = battle.characters().get(1);
        Character hunter = battle.characters().get(2);

        int before = (int) pearl.getResources().get(COUNTER).getValue();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, pearl, elation, 0, 0));
        boolean archetype = elation.getBuffManager().hasState(ARCHETYPE);
        boolean deep = pearl.getBuffManager().hasState(DEEP);
        boolean hunterUntouched = hunter.getBuffManager().hasState(ARCHETYPE);
        int after = (int) pearl.getResources().get(COUNTER).getValue();

        // a second ultimate on the other ally must mark THAT one
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, pearl, hunter, 0, 0));
        boolean hunterMarked = hunter.getBuffManager().hasState(ARCHETYPE);
        System.out.println("[pearl_states] archetype on the target = " + archetype + " ; deep study on Pearl = "
                + deep + " ; the other ally marked? " + hunterUntouched + " then " + hunterMarked
                + " ; counter " + before + " -> " + after);

        Assertions.assertTrue(archetype, "the designated ally becomes the archetype");
        Assertions.assertTrue(deep, "and Pearl carries deep study");
        Assertions.assertFalse(hunterUntouched, "a bystander is not marked");
        Assertions.assertTrue(hunterMarked, "but the next ultimate marks its own target");
        Assertions.assertEquals(before + 20, after, "and the ultimate grants 20 of her counter");
    }
}
