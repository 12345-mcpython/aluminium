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

/** Character 1503 (Pearl): the art barrier only lands on an Elation archetype, and the talent's reduction tracks the HP threshold. */
public class PearlArtBarrierAndTalentTest {
    private static final int PEARL = 1503;
    private static final int ELATION = 1502;   // Yao Guang, Elation
    private static final int HUNTER = 1204;
    private static final String BARRIER = "\u827a\u672f\u58c1\u5792";
    private static final String GRIT = "\u541e\u7eb3\u6c99\u783e";

    @Test
    public void theBarrierLandsOnlyOnAnElationArchetypeAndRefundsNinety() {
        Battle battle = newBattle();
        Character pearl = battle.characters.get(0);
        Character elation = battle.characters.get(1);
        Character hunter = battle.characters.get(2);

        battle.fireTriggers(TriggerEvent.ULT_CAST, pearl, elation, 0, 0);
        battle.fireTriggers(TriggerEvent.ULT_CAST, pearl, hunter, 0, 0);
        boolean onElation = elation.getBuffManager().hasState(BARRIER);
        boolean onHunter = hunter.getBuffManager().hasState(BARRIER);

        double pearlBefore = pearl.getCurrentEnergy();
        battle.fireTriggers(TriggerEvent.ULT_CAST, elation, elation, 0, 0);
        double refund = pearl.getCurrentEnergy() - pearlBefore;
        System.out.println("[pearl_barrier] on the Elation ally = " + onElation + " ; on the Hunt ally = " + onHunter
                + " ; refund when the holder casts = " + refund);

        Assertions.assertTrue(onElation, "an Elation-Path archetype gets the barrier");
        Assertions.assertFalse(onHunter, "a Hunt ally must NOT get it (the rule gates on target has_path)");
        Assertions.assertEquals(90.0, refund, 1e-9, "and its holder's ultimate refunds Pearl 90 energy");
    }

    @Test
    public void theTalentReductionFollowsTheHalfHpThreshold() {
        Battle battle = newBattle();
        Character ally = battle.characters.get(2);

        ally.takeDamage(ally.getMaxHp() * 0.8);
        battle.fireTriggers(TriggerEvent.HP_LOST, ally, ally, 0, 0);
        boolean below = ally.getBuffManager().hasState(GRIT);

        ally.heal(ally.getMaxHp());
        battle.fireTriggers(TriggerEvent.HEALED, ally, ally, 0, 0);
        boolean above = ally.getBuffManager().hasState(GRIT);
        System.out.println("[pearl_talent] reduction below half = " + below + " ; after healing above half = " + above);

        Assertions.assertTrue(below, "below 50% HP the ally carries the damage reduction");
        Assertions.assertFalse(above, "and healing back above 50% removes it");
    }

    private static Battle newBattle() {
        Battle battle = new Battle(List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ELATION, 80),
                CharacterFactory.create(HUNTER, 80)), List.of(EnemyFactory.create(1002011, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
