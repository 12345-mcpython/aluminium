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
 * 1415's memosprite skill 17, first clause (2026-10-02): 「对遐蝶施放时，【新蕊】可以溢出至 #3%」 (#3 = 2).
 *
 * ⭐ Two-sided: after the ode lands on her, filling 【新蕊】 far past its cap stops at `max + 2% of max`; without the ode the same fill stops at `max`. Both numbers are the
 * engine's: the cap her file declares, and the overflow the sentence widens.
 */
public class LifeOdeNewBudOverflowTest {
    private static final int LEVEL = 80;
    private static final int CASTORICE = 1407;
    private static final int CYRENE = 1415;
    private static final int MONSTER = 1002011;
    private static final int ODE_SLOT = 17;
    private static final String BUD = "新蕊";
    private static final int MAX = 34000;
    private static final int OVERFLOW = 680;

    @Test
    public void theOdeWidensTheOverflow() {
        int with = filled(true);
        int without = filled(false);
        System.out.println("[newbud] filled past the cap: with the ode " + with + " ; without it " + without);
        Assertions.assertEquals(MAX + OVERFLOW, with, "the ode lets 【新蕊】 overflow to max + 2% of max");
        Assertions.assertEquals(MAX, without, "and without it there is no overflow to use");
    }

    /** Her 【新蕊】 after asking for far more than any cap. */
    private static int filled(boolean castTheOde) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character her = CharacterFactory.create(CASTORICE, LEVEL);
        Battle battle = new Battle(List.of(cyrene, her),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        her = battle.characters.get(1);
        if (castTheOde) {
            var sprite = battle.summonServant(cyrene);
            battle.processRequests();
            var ode = sprite.skillAt(ODE_SLOT);
            Assertions.assertNotNull(ode, "precondition: slot 17");
            SkillExecutor.execute(battle, ode, sprite, List.of(her));
            battle.processRequests();
        }
        // ⭐ 【新蕊】 is declared `scope: PARTY`, so it lives in the BATTLE's store, not the character's (round 77's measured fact).
        battle.partyResource(BUD).gain(999_999);
        return battle.partyResourceValue(BUD);
    }
}
