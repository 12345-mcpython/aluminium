package com.laosun.aluminium.test;

import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.TriggerTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Guards the JSON <-> bean binding of the trigger files (P8-7).
 *
 * <p>Why this is separate from {@link TriggerTableTest}: that class proves the trigger
 * <b>behaves</b> correctly, but a Gson field-name mismatch is the failure mode this project has hit
 * repeatedly (see {@code ROADMAP} §4.2) -- the file loads, the field silently stays {@code null},
 * and the rule quietly does the wrong thing. So the raw parse is pinned here.
 */
public class TriggerDataBindingTest {

    private static final int TRIBBIE = 1403;
    private static final int ROBIN = 1309;

    /** Both validation characters must actually have a file, and it must produce rules. */
    @Test
    public void bothCharactersHaveLoadableTables() {
        Assertions.assertTrue(TriggerTables.exists(TRIBBIE), "1403 must have a trigger file");
        Assertions.assertTrue(TriggerTables.exists(ROBIN), "1309 must have a trigger file");

        TriggerTable tribbie = TriggerTables.of(TRIBBIE);
        Assertions.assertFalse(tribbie.isEmpty(), "1403's table must not be empty");
        Assertions.assertEquals(1, tribbie.ruleCount(TriggerEvent.BATTLE_START));
        Assertions.assertEquals(1, tribbie.ruleCount(TriggerEvent.ALLY_ATTACK));

        TriggerTable robin = TriggerTables.of(ROBIN);
        Assertions.assertFalse(robin.isEmpty(), "1309's table must not be empty");
        // Robin is the first character whose 行迹 extra abilities are data too, so her table now holds
        // three rules: the talent (ALLY_ATTACK) and two traces (BATTLE_START, SKILL_CAST). The counts
        // are asserted per event rather than as a total, so a rule that lands on the wrong event is
        // still caught.
        Assertions.assertEquals(1, robin.ruleCount(TriggerEvent.BATTLE_START),
                "华彩花腔: 战斗开始时自身行动提前25%");
        Assertions.assertEquals(1, robin.ruleCount(TriggerEvent.SKILL_CAST),
                "模进乐段: 施放战技时额外恢复5点能量");
        Assertions.assertEquals(1, robin.ruleCount(TriggerEvent.ALLY_ATTACK));
        Assertions.assertEquals(0, robin.ruleCount(TriggerEvent.BASIC_ATTACK),
                "nothing in her file listens to 普攻 -- 施放战技时 is the Skill slot");
    }

    /**
     * The {@code per_target} flag must survive the JSON round trip.
     *
     * <p>This is the specific field whose silent loss would turn Tribbie's "1.5 per target" into
     * "1.5 per attack" -- a factor-of-N error that no exception would report.
     */
    @Test
    public void perTargetSurvivesJsonBinding() {
        TriggerTable tribbie = TriggerTables.of(TRIBBIE);
        var rule = tribbie.matching(TriggerEvent.ALLY_ATTACK,
                new TriggerTable.TriggerContext(
                        com.laosun.aluminium.utils.CharacterFactory.create(TRIBBIE, 80),
                        com.laosun.aluminium.utils.CharacterFactory.create(1003, 80), null, 3, 0)).getFirst();

        Assertions.assertEquals(1, rule.effects().size());
        Assertions.assertEquals(Boolean.TRUE, rule.effects().getFirst().getPerTarget(),
                "per_target must deserialize to TRUE; if this is null the rule silently becomes flat");
    }

    /** Robin's rule is deliberately flat, so {@code per_target} must stay unset. */
    @Test
    public void robinRuleIsFlatNotPerTarget() {
        TriggerTable robin = TriggerTables.of(ROBIN);
        var rule = robin.matching(TriggerEvent.ALLY_ATTACK,
                new TriggerTable.TriggerContext(
                        com.laosun.aluminium.utils.CharacterFactory.create(ROBIN, 80),
                        com.laosun.aluminium.utils.CharacterFactory.create(1003, 80), null, 3, 0)).getFirst();

        Assertions.assertNotEquals(Boolean.TRUE, rule.effects().getFirst().getPerTarget(),
                "Robin's talent is a flat 2 per attack, not per target");
        Assertions.assertEquals(2.0, rule.effects().getFirst().getAmount(), 1e-9);
    }

    /** Conditions must survive too: both characters rely on {@code actor != self}. */
    @Test
    public void conditionsSurviveJsonBinding() {
        TriggerTable robin = TriggerTables.of(ROBIN);
        var rule = robin.matching(TriggerEvent.ALLY_ATTACK,
                new TriggerTable.TriggerContext(
                        com.laosun.aluminium.utils.CharacterFactory.create(ROBIN, 80),
                        com.laosun.aluminium.utils.CharacterFactory.create(1003, 80), null, 1, 0)).getFirst();

        Assertions.assertEquals(2, rule.conditions().size(),
                "the file declares two conditions (actor != self, hit_count > 0)");
    }

    /** The provenance fields are present, so a number can be traced back to its document. */
    @Test
    public void rulesCarryTheirSource() {
        TriggerTable tribbie = TriggerTables.of(TRIBBIE);
        var rule = tribbie.matching(TriggerEvent.BATTLE_START,
                new TriggerTable.TriggerContext(
                        com.laosun.aluminium.utils.CharacterFactory.create(TRIBBIE, 80), null, null, 0, 0))
                .getFirst();
        Assertions.assertNotNull(rule.source(), "every rule must state its source");
        Assertions.assertTrue(rule.source().contains("1403"), rule.source());
    }
}
