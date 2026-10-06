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

/** Character 1503 (Pearl): the Ultimate marks an ally as the archetype and Pearl as its deep learner. */
public class PearlDeepStudyStateTest {
    private static final int PEARL = 1503;
    private static final int ELATION = 1502;
    private static final int HUNTER = 1204;
    private static final String ARCHETYPE = "\u7f8e\u5b66\u5e95\u672c";
    private static final String DEEP = "\u6df1\u5ea6\u5b66\u4e60";
    private static final String COUNTER = "\u597d\u6d3b\u5f53\u8d4f";

    @Test
    public void theUltimateMarksTheArchetypeAndTheDeepLearner() {
        Battle battle = new Battle(List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ELATION, 80),
                CharacterFactory.create(HUNTER, 80)), List.of(EnemyFactory.create(1002011, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character pearl = battle.characters.get(0);
        Character elation = battle.characters.get(1);
        Character hunter = battle.characters.get(2);

        int before = (int) pearl.getResources().get(COUNTER).getValue();
        battle.fireTriggers(TriggerEvent.ULT_CAST, pearl, elation, 0, 0);
        boolean archetype = elation.getBuffManager().hasState(ARCHETYPE);
        boolean deep = pearl.getBuffManager().hasState(DEEP);
        boolean hunterUntouched = hunter.getBuffManager().hasState(ARCHETYPE);
        int after = (int) pearl.getResources().get(COUNTER).getValue();

        // a second ultimate on the other ally must mark THAT one
        battle.fireTriggers(TriggerEvent.ULT_CAST, pearl, hunter, 0, 0);
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
