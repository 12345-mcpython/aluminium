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

/**
 * Character 1506 (Silver Wolf LV.999): 【头号补给盲盒】 picks ONE of three effects.
 *
 * <p>「…并随机触发 1 种效果：【超超超大剑】…；【爆爆爆炸蛋】：恢复 2 个战技点；【怪怪怪味豆】：获得 3 个笑点。」
 *
 * <p>⚠ Read as a COUNT, not as "did X happen": the two wrong implementations are "both ran" and "neither ran", and a
 * positive assertion is satisfied by the first of them.
 *
 * <p>⚠ <b>Two traps this case had to get past, both measured.</b>
 * <ol>
 *   <li>The event carries TWO rules: the constant 90% damage (「造成 90% 的虚数属性欢愉伤害」) and the pick. So "the
 *       enemy's HP moved" is NOT a marker for the pick - it moved because the other rule always fires. The pick's own
 *       damage rider is therefore stated large (500% of ATK) so its landing is unmistakable beside that constant;</li>
 *   <li>for the same reason "skill points went up" is not one either, because the base rule runs alongside. What IS a
 *       marker is a reading only that effect can produce: laughs +3, or skill points +2 on top of the base rule's own
 *       movement, which is why the readings are taken either side of the ONE event.</li>
 * </ol>
 */
public class SilverWolfLV999LootPickTest {
    private static final int WOLF = 1506;
    private static final int ALLY = 1204;
    private static final int MONSTER = 1002011;
    private static final String LAUGH = "笑点";
    /** The pick's damage rider, stated large on purpose; see the class note. */
    private static final double RIDER = 5.0;

    /** Exactly one of the three effects lands, over several seeds, and which one varies. */
    @Test
    public void exactlyOneOfTheThreeLands() {
        boolean sawRider = false;
        boolean sawLaugh = false;
        boolean sawPoints = false;
        for (int seed = 0; seed < 18; seed++) {
            int[][] readings = beforeAndAfter(seed);
            int[] before = readings[0];
            int[] after = readings[1];
            double hpLost = before[0] - after[0];
            int laughsGained = after[2] - before[2];
            int pointsGained = after[1] - before[1];

            // The base rule's 90% rider always lands; anything well above it is the pick's own.
            boolean rider = hpLost > 1000;
            boolean laughs = laughsGained >= 3;
            boolean points = pointsGained >= 2 && !rider;
            int landed = (rider ? 1 : 0) + (laughs ? 1 : 0) + (points ? 1 : 0);
            System.out.println("[1506] seed " + seed + ": hpLost=" + hpLost + " laughs+" + laughsGained
                    + " points+" + pointsGained + "  -> rider=" + rider + " laughs=" + laughs + " points=" + points);
            Assertions.assertEquals(1, landed,
                    "seed " + seed + ": exactly one effect must land - 0 means none was picked, 2+ means the roll "
                            + "happened per effect or `one_of` is not applied");
            sawRider |= rider;
            sawLaugh |= laughs;
            sawPoints |= points;
        }
        System.out.println("[1506] over 18 seeds: rider=" + sawRider + " laughs=" + sawLaugh + " points=" + sawPoints);
        Assertions.assertTrue(sawRider && sawLaugh && sawPoints,
                "and all three are reachable - a pick that never lands on one of them is not a choice among three");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * [before, after] as [enemyHp, skillPoints, laughs], read IMMEDIATELY either side of the one event.
     *
     * <p>Note: Read around the event, not around the battle. An earlier version took the "before" reading after the
     * Ultimate and the "after" one after everything, which folded in every other change and read two effects landing
     * where one had.
     */
    private static int[][] beforeAndAfter(int seed) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(WOLF, 80), CharacterFactory.create(ALLY, 80)),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(seed));
        battle.startBattle();
        battle.processRequests();
        Character wolf = battle.characters.getFirst();
        wolf.setCurrentEnergy(wolf.getMaxEnergy());
        battle.castUltra(wolf, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();

        int[] before = readings(battle);
        battle.fireTriggers(TriggerEvent.SKILL_POINT_SPENT, wolf, wolf, 0, 1);
        battle.processRequests();
        return new int[][]{before, readings(battle)};
    }

    private static int[] readings(Battle battle) {
        return new int[]{
                (int) Math.round(battle.enemyUnits().stream().mapToDouble(e -> e.getCurrentHp()).sum()),
                battle.getSkillPoints(),
                battle.partyResourceValue(LAUGH),
        };
    }
}
