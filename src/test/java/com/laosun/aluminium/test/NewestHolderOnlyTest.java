package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The move of the "only the newest target it was applied to is affected" family: `REMOVE_STATE` first takes the name off everybody, then `APPLY_BUFF` gives it to the new target
 *  so Note: order is meaning (the note at `TriggerInterpreter:2515`: *takes the named state off every resolved target - 
 * the only-the-newest-one-holds-it half*; `1215`'s note says it even more plainly: *the state goes off everybody, then onto the
 * new target - there is no only-one-holder flag, the removal IS that clause*).
 *
 * <p>Note: This one first proves the move itself holds, and only then can the 12 readers (1414/1224/1225/1202/1406/1412/1504/1112/1215/1305/1404/1006)
 * all copy it. Mutation: delete the `REMOVE_STATE` line so A does not lose it, so it must go red.
 */
public class NewestHolderOnlyTest {
    private static final int WEARER = 1003;
    private static final int OTHER = 1002;
    private static final int MONSTER = 1002011;
    private static final int LEVEL = 80;
    private static final String MARK = "最新标记";

    /** A rule that "leaves the mark only on the target aimed at this time": take it off everybody first, then attach the new one. */
    private static TriggerSpec newestOnlyRule(boolean removeFirst) {
        EffectSpec apply = new EffectSpec();
        TriggerSpecs.set(apply, "op", "APPLY_BUFF");
        TriggerSpecs.set(apply, "buff", MARK);
        TriggerSpecs.set(apply, "permanent", Boolean.TRUE);
        TriggerSpecs.set(apply, "target", "target");
        if (removeFirst) {
            EffectSpec strip = new EffectSpec();
            TriggerSpecs.set(strip, "op", "REMOVE_STATE");
            TriggerSpecs.set(strip, "buff", MARK);
            TriggerSpecs.set(strip, "target", "all_allies");
            return TriggerSpecs.rule(TriggerEvent.SKILL_CAST.name(), List.of(), strip, apply);
        }
        return TriggerSpecs.rule(TriggerEvent.SKILL_CAST.name(), List.of(), apply);
    }

    private static boolean marked(Battle battle, com.laosun.aluminium.models.CanHit who) {
        // Note: APPLY_BUFF creates a StateBuff, and the query for it is hasState - - not stacksOf (that queries ADD_STACK's layer count).
        // TriggerTable:2899 is exactly `has_state`'s implementation: `who.getBuffManager().hasState(state)`.
        return who.getBuffManager().hasState(MARK);
    }

    /** Cast "Skill" twice, the first aimed at a and the second at b; returns {does a still have the mark, does b still have the mark}. */
    private static boolean[] twoCasts(boolean removeFirst) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL);
        Character a = CharacterFactory.create(OTHER, LEVEL);
        TriggerSpec rule = newestOnlyRule(removeFirst);
        TriggerSpecs.set(rule, "id", "probe_newest_only");
        TriggerSpecs.set(rule, "when", List.of("actor == self"));
        wearer.setTriggerTable(new TriggerTable(WEARER, List.of(rule)));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, a), List.of(enemy), new Random(0));
        battle.startBattle();

        battle.fireTriggers(TriggerEvent.SKILL_CAST, wearer, a, 0, 0);      // Note: a gets the mark
        boolean aHad = marked(battle, a);
        battle.fireTriggers(TriggerEvent.SKILL_CAST, wearer, enemy, 0, 0);  // Note: switch it to the enemy
        return new boolean[]{aHad, marked(battle, a), marked(battle, enemy)};
    }

    @Test
    public void onlyTheNewestTargetKeepsTheMark() {
        boolean[] withStrip = twoCasts(true);
        System.out.println("[newest] with REMOVE_STATE: aHad=" + withStrip[0] + " aStill=" + withStrip[1]
                + " enemy=" + withStrip[2]);
        Assertions.assertTrue(withStrip[0], "the first cast must have marked a");
        Assertions.assertFalse(withStrip[1], "the second cast must have taken it off a -- the removal IS the clause");
        Assertions.assertTrue(withStrip[2], "and the second cast's own target holds it");

        boolean[] without = twoCasts(false);
        System.out.println("[newest] without: aHad=" + without[0] + " aStill=" + without[1]
                + " enemy=" + without[2] + "   <- how the mutation behaves");
        Assertions.assertTrue(without[1],
                "with no REMOVE_STATE the old holder keeps it, which is what the mutation must show");
    }
}
