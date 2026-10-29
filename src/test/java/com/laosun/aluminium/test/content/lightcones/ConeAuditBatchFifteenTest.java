package com.laosun.aluminium.test.content.lightcones;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The fifteenth audit batch of light cones (21064-21066), the three Elation cones. All three read correctly on first inspection:
 * 21064 scopes its vulnerability to ELATION damage, 21065 stacks through a buffer, 21066 ignores defence for the cast only.
 */
public class ConeAuditBatchFifteenTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1506;   // has an Elation skill (data slot 20)
    private static final int ALLY = 1204;

    /**
     * 21064 菇菇嘎嘎历险记: the elation share once, and an Elation cast makes every enemy take 6% more ELATION damage.
     */
    @Test
    public void coneTwoInnocentSixFourVulnerabilityAgainstElationDamage() {
        Fixture f = new Fixture(21064);
        Fixture bare = new Fixture(null);
        double elation = f.attribute(AttributeType.ELATION_DAMAGE_BOOST)
                - bare.attribute(AttributeType.ELATION_DAMAGE_BOOST);
        System.out.println("[21064] 欢愉度增量 " + elation);
        Assertions.assertEquals(0.12, elation, 1e-9, "「欢愉度提高 12%」—— 光锥属性给一次");

        f.battle.castImmediate(f.wearer.getSkills().get(SkillType.ELATION_SKILL), f.wearer,
                List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        Rule rule = rule(21064);
        System.out.println("[21064] 规则: damage_type=" + rule.damageType + " percent=" + rule.percent
                + " turns=" + rule.turns + " target=" + rule.target);
        Assertions.assertEquals("ELATION", rule.damageType, "「受到的**欢愉伤害**提高」—— 只对欢愉伤害");
        Assertions.assertEquals(0.06, rule.percent, 1e-12, "6%（叠影 1）");
        Assertions.assertEquals(2, rule.turns, "「持续 2 回合」");
        Assertions.assertEquals("all_enemies", rule.target, "「使敌方**全体**」");
    }

    /**
     * 21065 今日好手气: the crit share once, and Elation casts stack the elation share, two at most.
     */
    @Test
    public void coneTwoInnocentSixFiveElationStacksTwice() {
        Fixture f = new Fixture(21065);
        Fixture bare = new Fixture(null);
        double crit = f.attribute(AttributeType.CRIT_CHANCE) - bare.attribute(AttributeType.CRIT_CHANCE);
        System.out.println("[21065] 暴击率增量 " + crit);
        Assertions.assertEquals(0.12, crit, 1e-9, "「暴击率提高 12%」—— 光锥属性给一次");

        double base = f.attribute(AttributeType.ELATION_DAMAGE_BOOST);
        for (int i = 0; i < 3; i++) {
            f.battle.castImmediate(f.wearer.getSkills().get(SkillType.ELATION_SKILL), f.wearer,
                    List.of(f.battle.enemyUnits().getFirst()));
            f.battle.processRequests();
        }
        double stacked = f.attribute(AttributeType.ELATION_DAMAGE_BOOST) - base;
        System.out.println("[21065] 三次欢愉技后的欢愉度增量 " + stacked + "（2 层 × 0.12 = 0.24）");
        Assertions.assertEquals(0.24, stacked, 1e-9, "「施放欢愉技时欢愉度 +12%，最多叠加 2 次」");
    }

    /**
     * 21066 放个短假: the elation share once, and the defence ignore lives for that cast only.
     */
    @Test
    public void coneTwoInnocentSixSixDefenceIgnoreDuringTheCast() {
        Fixture f = new Fixture(21066);
        Fixture bare = new Fixture(null);
        double elation = f.attribute(AttributeType.ELATION_DAMAGE_BOOST)
                - bare.attribute(AttributeType.ELATION_DAMAGE_BOOST);
        System.out.println("[21066] 欢愉度增量 " + elation);
        Assertions.assertEquals(0.2, elation, 1e-9, "「欢愉度提高 20%」—— 光锥属性给一次");

        Rule rule = rule(21066);
        System.out.println("[21066] 规则: attribute=" + rule.attribute + " percent=" + rule.percent + " until=" + rule.until);
        Assertions.assertEquals("DEFENCE_IGNORE", rule.attribute, "「无视目标…的防御力」");
        Assertions.assertEquals(0.08, rule.percent, 1e-12, "8%（叠影 1）");
        Assertions.assertEquals("cast_end", rule.until, "「施放欢愉技**期间**」—— 窗口是那一次施放");
        Assertions.assertTrue(f.wearer.getCurrentHp() > 0, "夹具可用");
    }

    // ==================================================================

    private record Rule(String attribute, String damageType, double percent, int turns, String target, String until) {
    }

    private static Rule rule(int cone) {
        try (java.io.InputStream stream = ConeAuditBatchFifteenTest.class
                .getResourceAsStream("/game/light_cones/" + cone + ".json")) {
            Assertions.assertNotNull(stream, "missing cone " + cone);
            JsonObject root = JsonParser.parseReader(
                    new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject effect = root.getAsJsonArray("1").get(0).getAsJsonObject()
                    .getAsJsonArray("do").get(0).getAsJsonObject();
            return new Rule(
                    effect.has("attribute") ? effect.get("attribute").getAsString() : null,
                    effect.has("damage_type") ? effect.get("damage_type").getAsString() : null,
                    effect.has("percent") ? effect.get("percent").getAsDouble() : 0,
                    effect.has("turns") ? effect.get("turns").getAsInt() : 0,
                    effect.has("target") ? effect.get("target").getAsString() : null,
                    effect.has("until") ? effect.get("until").getAsString() : null);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static final class Fixture {
        private final Character wearer;
        private final Battle battle;

        private Fixture(Integer cone) {
            Weapon weapon = cone == null ? null : Weapon.build(cone, LEVEL, false, 1);
            this.wearer = CharacterFactory.create(WEARER, LEVEL, false, weapon);
            this.battle = new Battle(List.of(wearer, CharacterFactory.create(ALLY, LEVEL)),
                    List.of(BattleFixture.dummy()), BattleFixture.alwaysRoll(0.9999));
            battle.startBattle();
            battle.processRequests();
        }

        private double attribute(AttributeType type) {
            return wearer.getAttribute(type).get();
        }
    }
}
