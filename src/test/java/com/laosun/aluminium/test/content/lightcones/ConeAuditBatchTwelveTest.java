package com.laosun.aluminium.test.content.lightcones;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.buff.BuffManager;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The twelfth audit batch of light cones (21042-21048), each clause read as a BATTLE.
 *
 * <p>Four more duplicated shares (21042 / 21045 / 21047 / 21048): the break effect and the speed halves are the cones' own
 * {@code ability_property} values, and each was stated again as a rule. Two clauses that also needed a trigger or a cap had it
 * missing - 21047's "after dealing break damage" and its per-turn cap, 21048's any-attack trigger and its "twice a turn".
 *
 * <p>21043 and 21046 are registered: the "number of shielded allies" and "two allies sharing a Path" conditions have no
 * spelling. Their cases read the FILE, because the missing half is a condition that does not exist rather than a wrong number.
 */
public class ConeAuditBatchTwelveTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1204;

    /**
     * 21042: the break share once, and an ultimate gives +15% crit.
     */
    @Test
    public void coneTwoInnocentFourTwoBreakOnceAndCritOnUltimate() {
        Fixture f = new Fixture(21042, true);
        Fixture bare = new Fixture(null, true);
        double breaking = f.attribute(AttributeType.BREAKING_EFFECT) - bare.attribute(AttributeType.BREAKING_EFFECT);
        System.out.println("[21042] break effect gain " + breaking);
        Assertions.assertEquals(0.28, breaking, 1e-9, "the break share comes from the property, once");

        double before = f.attribute(AttributeType.CRIT_CHANCE);
        f.wearer.setCurrentEnergy(f.wearer.getMaxEnergy());
        f.battle.castUltra(f.wearer, List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        double crit = f.attribute(AttributeType.CRIT_CHANCE) - before;
        System.out.println("[21042] crit after the ultimate " + crit);
        Assertions.assertEquals(0.15, crit, 1e-9, "「施放终结技时暴击率 +15%」");
    }

    /**
     * 21045: the break share once, and an ultimate raises speed.
     */
    @Test
    public void coneTwoInnocentFourFiveSpeedOnUltimate() {
        Fixture f = new Fixture(21045, true);
        Fixture bare = new Fixture(null, true);
        double breaking = f.attribute(AttributeType.BREAKING_EFFECT) - bare.attribute(AttributeType.BREAKING_EFFECT);
        System.out.println("[21045] break effect gain " + breaking);
        Assertions.assertEquals(0.28, breaking, 1e-9, "the break share comes from the property, once");
        double before = f.attribute(AttributeType.SPEED);
        f.wearer.setCurrentEnergy(f.wearer.getMaxEnergy());
        f.battle.castUltra(f.wearer, List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        double speed = f.attribute(AttributeType.SPEED) - before;
        System.out.println("[21045] speed after the ultimate " + speed);
        Assertions.assertTrue(speed > 0, "「施放终结技后速度 +8%」");
    }

    /**
     * 21047: speed at battle start, and the break half exists with its per-turn cap.
     *
     * <p>NOTE the break half is INVISIBLE in the panel: it applies the same modifier identity as the battle-start one, so the
     * second application replaces the first and the value does not move. The rule shape is read instead.
     */
    @Test
    public void coneTwoInnocentFourSevenSpeedAtBattleStartAndOnBreak() {
        Fixture f = new Fixture(21047, false);
        Fixture bare = new Fixture(null, false);
        double start = f.attribute(AttributeType.SPEED) - bare.attribute(AttributeType.SPEED);
        System.out.println("[21047] speed at battle start " + start);
        Assertions.assertTrue(start > 0, "「进入战斗时…速度 +8%」");

        JsonObject rule = read(21047, "1", "cone21047_speed");
        Assertions.assertTrue(rule.getAsJsonArray("on_any").toString().contains("BREAK"),
                "the break half must be subscribed (on_any BREAK)");
        Assertions.assertEquals(1, rule.get("per_turn").getAsInt(), "「该效果每回合只可触发 1 次」");
    }

    /**
     * 21048: her own attack on a BROKEN enemy pays energy, twice a turn at most.
     */
    @Test
    public void coneTwoInnocentFourEightPaysTwiceATurnOnABrokenTarget() {
        Fixture f = new Fixture(21048, false);
        f.wearer.setCurrentEnergy(0);
        f.battle.enemyUnits().getFirst().getBuffManager().addBuff(new StateBuff(BuffManager.BROKEN_STATE, 2, false));
        for (int i = 0; i < 3; i++) {
            f.battle.fireTriggers(TriggerEvent.BASIC_ATTACK, f.wearer, f.battle.enemyUnits().getFirst(), 0, 0);
        }
        System.out.println("[21048] energy after three attacks " + f.wearer.getCurrentEnergy() + " (2 x 3 = 6)");
        Assertions.assertEquals(6.0, f.wearer.getCurrentEnergy(), 1e-9, "「每回合最多触发 2 次」");
    }

    /**
     * 21043: the damage share has only `actor == self` - the shielded-ally count does not exist.
     */
    @Test
    public void coneTwoInnocentFourThreeHasNoShieldCountCondition() {
        JsonObject rule = read(21043, "1", "cone21043_damage_per_shielded_ally");
        System.out.println("[21043] conditions: " + rule.get("when"));
        Assertions.assertEquals(1, rule.getAsJsonArray("when").size(),
                "witness: only `actor == self`, there is no shielded-ally count (see the status row)");
    }

    /**
     * 21046: the crit-damage rule carries no condition at all - the Path pairing does not exist.
     */
    @Test
    public void coneTwoInnocentFourSixHasNoPathPairingCondition() {
        JsonObject rule = read(21046, "1", "cone21046_path_twins");
        System.out.println("[21046] conditions: " + rule.get("when"));
        Assertions.assertNull(rule.get("when"), "witness: not even a condition (see the status row)");
    }

    // ==================================================================

    private static JsonObject read(int cone, String level, String id) {
        String path = "/game/light_cones/" + cone + ".json";
        try (InputStream stream = ConeAuditBatchTwelveTest.class.getResourceAsStream(path)) {
            Assertions.assertNotNull(stream, "missing resource " + path);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (com.google.gson.JsonElement each : root.getAsJsonArray(level)) {
                JsonObject rule = each.getAsJsonObject();
                if (id.equals(rule.get("id").getAsString())) {
                    return rule;
                }
            }
            throw new AssertionError("no rule " + id + " in " + path);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static final class Fixture {
        private final Character wearer;
        private final Character ally;
        private final Battle battle;

        private Fixture(Integer cone, boolean withAlly) {
            Weapon weapon = cone == null ? null : Weapon.build(cone, LEVEL, false, 1);
            this.wearer = CharacterFactory.create(WEARER, LEVEL, false, weapon);
            this.ally = CharacterFactory.create(ALLY, LEVEL);
            List<Character> party = withAlly ? List.of(wearer, ally) : List.of(wearer);
            this.battle = new Battle(party, List.of(BattleFixture.dummy()), BattleFixture.alwaysRoll(0.9999));
            battle.startBattle();
            battle.processRequests();
        }

        private double attribute(AttributeType type) {
            return wearer.getAttribute(type).get();
        }
    }
}
