package com.laosun.aluminium.test.engine;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** The parameterised variable `allies_with_path:<PATH>`: how many of my team share a Path. */
public class AlliesWithPathTest {
    private static final int PEARL = 1503;      // Elation
    private static final int YAO_GUANG = 1502;  // Elation, per her panel entry
    private static final int HUNTER = 1204;     // Hunt

    @Test
    public void theCountFollowsHowManyTeammatesShareThePath() {
        double one = boostWith(false);
        double two = boostWith(true);
        System.out.println("[path_count] with one Elation teammate the ATK boost is " + one
                + " ; with two it is " + two);
        Assertions.assertEquals(0.0, one, 1e-9, "the gate needs 2, and only Pearl is Elation");
        Assertions.assertTrue(two > 0.0,
                "with Yao Guang alongside her the gate holds and the ATK modifier lands (measured " + two + ")");
    }

    /** Runs a battle and returns the ATK boost from a rule gated on the team's Elation count. */
    private static double boostWith(boolean withYaoGuang) {
        List<Character> team = new ArrayList<>();
        team.add(CharacterFactory.create(PEARL, 80));
        if (withYaoGuang) {
            team.add(CharacterFactory.create(YAO_GUANG, 80));
        }
        team.add(CharacterFactory.create(HUNTER, 80));
        Battle battle = new Battle(team, List.of(EnemyFactory.create(1002011, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character pearl = battle.characters.stream().filter(c -> c.getCid() == PEARL).findFirst().orElseThrow();
        pearl.setTriggerTable(new TriggerTable(PEARL, List.of(
                TriggerSpecs.rule("TURN_START", List.of("allies_with_path:欢愉 >= 2"),
                        TriggerSpecs.modifyAttr("ATTACK", 0.5, 1)))));
        double before = pearl.getAttribute(AttributeType.ATTACK).get();
        battle.fireTriggers(TriggerEvent.TURN_START, pearl, pearl, 0, 0);
        return pearl.getAttribute(AttributeType.ATTACK).get() - before;
    }
}
