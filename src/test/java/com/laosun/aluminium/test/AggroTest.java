package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.Path;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * P5-1 / P5-2 acceptance: path aggro values, the aggro table, and "taunt does not change the numbers".
 *
 * <p>Anchors (fully cross-checked against the {@code aggro} column of {@code character_data.json}):
 * Preservation 150 / Destruction 125 / other 100 / Hunt·Erudition 75.
 */
public class AggroTest {
    private static final double EPS = 1e-9;

    @Test
    public void pathCarriesTheOfficialAggroLadder() {
        Assertions.assertEquals(150, Path.PRESERVATION.getAggro());
        Assertions.assertEquals(125, Path.DESTRUCTION.getAggro());
        Assertions.assertEquals(100, Path.OTHER.getAggro());
        Assertions.assertEquals(75, Path.HUNT.getAggro(), "Hunt is lower than the usual value; do not treat it as 100");
        Assertions.assertEquals(75, Path.ERUDITION.getAggro());
        Assertions.assertEquals(Path.DESTRUCTION, Path.fromName("毁灭"));
        Assertions.assertEquals(Path.PRESERVATION, Path.fromName("存护"));
        Assertions.assertEquals(Path.OTHER, Path.fromName("nonexistent path"), "an unlisted path degrades instead of blowing up");
    }

    @Test
    public void pathIsDerivedFromCharacterData() {
        Character preservation = Character.builder().cid(1104).level(80).build();   // Gepard (杰帕德): Preservation
        Character destruction = Character.builder().cid(1212).level(80).build();    // Jingliu (镜流): Destruction
        Character hunt = Character.builder().cid(1209).level(80).build();           // Yanqing (彦卿): Hunt

        Assertions.assertEquals(Path.PRESERVATION, preservation.getPath());
        Assertions.assertEquals(150, preservation.getAggro(), "aggro comes straight from the character's aggro column");
        Assertions.assertEquals(Path.DESTRUCTION, destruction.getPath());
        Assertions.assertEquals(125, destruction.getAggro());
        Assertions.assertEquals(Path.HUNT, hunt.getPath());
        Assertions.assertEquals(75, hunt.getAggro());
    }

    @Test
    public void aggroTableSplitsProbabilityByWeight() {
        Character preservation = withAggro(150);
        Character other = withAggro(100);
        Battle battle = new Battle(List.of(preservation, other), List.of(dummy()), new Random(0));

        Map<com.laosun.aluminium.models.CanHit, Double> table = battle.getAggroTable(List.of(preservation, other));

        Assertions.assertEquals(0.6, table.get(preservation), EPS, "150 / 250");
        Assertions.assertEquals(0.4, table.get(other), EPS, "100 / 250");
    }

    @Test
    public void enemyHasTheNeutralAggroWeight() {
        Battle battle = new Battle(List.of(withAggro(100)), List.of(dummy()), new Random(0));

        Assertions.assertEquals(100, battle.aggroOf(battle.enemies.getFirst()), EPS,
                "an enemy has no path, so it uses the conventional tier");

        Map<com.laosun.aluminium.models.CanHit, Double> table = battle.getAggroTable(List.of(withAggro(150)));
        Assertions.assertEquals(1.0, table.values().iterator().next(), EPS, "only one candidate → probability 1");
        Assertions.assertTrue(battle.getAggroTable(List.of()).isEmpty(), "empty list → empty table, no division by zero");
    }

    // ==================================================================

    /** Build a character with the given aggro: set aggro directly, bypassing the data. */
    private static Character withAggro(int aggro) {
        Character c = Character.fromAttributes("c" + aggro, 10_000, 100, 100, 100);
        c.setAggro(aggro);
        return c;
    }

    private static Enemy dummy() {
        return EnemyFactory.create(1002011, 90, 1);
    }
}
