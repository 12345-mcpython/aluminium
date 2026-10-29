package com.laosun.aluminium.test.content.lightcones;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The twenty-second audit batch of light cones (23026-23030).
 *
 * <p>Defects: 23028 stated its crit share twice and both of its other clauses were missing; 23029's 【卸甲】 was a flat
 * unconditional vulnerability with no state and no chance; 23027 was missing its speed clause entirely.
 */
public class ConeAuditBatchTwentyTwoTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1204;

    /**
     * 23026 夜色流光溢彩: every ally attack stacks 【歌咏】 (five at most) and an ultimate trades them for 【华彩】.
     */
    @Test
    public void coneTwoThreeTwoSixSongStacksAndFlourish() {
        Fixture f = new Fixture(23026);
        double start = f.attribute(AttributeType.ENERGY_REGENERATION_RATE);
        for (int i = 0; i < 6; i++) {
            f.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, f.ally, f.battle.enemyUnits().getFirst(), 0, 0));
        }
        double stacked = f.attribute(AttributeType.ENERGY_REGENERATION_RATE) - start;
        System.out.println("[23026] 六次队友攻击后的能量恢复增量 " + stacked + "（5 层 × 3% = 0.15）");
        Assertions.assertEquals(0.15, stacked, 1e-9, "「每层 +3%，最多 5 层」");

        double partyBefore = f.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        double attackBefore = f.attribute(AttributeType.ATTACK);
        f.wearer.setCurrentEnergy(f.wearer.getMaxEnergy());
        f.battle.castUltra(f.wearer, List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        System.out.println("[23026] 终结技后: 队友增伤 " + (f.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - partyBefore)
                + " ; 她的攻击增量 " + (f.attribute(AttributeType.ATTACK) - attackBefore));
        Assertions.assertEquals(0.24, f.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - partyBefore, 1e-9,
                "【华彩】「使我方全体造成的伤害提高 24%」");
    }

    /**
     * 23027 驶向第二次生命: the break-damage defence ignore, and the speed gate at 150% break effect.
     */
    @Test
    public void coneTwoThreeTwoSevenSpeedAtHighBreak() {
        JsonObject ignore = rule(23027, "cone23027_break_ignores_defence").getAsJsonArray("do").get(0).getAsJsonObject();
        System.out.println("[23027] 击破无视防御: damage_type=" + ignore.get("damage_type") + " percent=" + ignore.get("percent"));
        Assertions.assertEquals("BREAK", ignore.get("damage_type").getAsString(), "「造成的**击破伤害**无视防御」");
        Assertions.assertEquals(0.2, ignore.get("percent").getAsDouble(), 1e-12, "20%（叠影 1）");

        JsonObject speed = rule(23027, "cone23027_speed_at_high_break");
        System.out.println("[23027] 速度那条: when=" + speed.getAsJsonArray("when") + " percent="
                + speed.getAsJsonArray("do").get(0).getAsJsonObject().get("percent"));
        Assertions.assertTrue(speed.getAsJsonArray("when").toString().contains("BREAKING_EFFECT >= 1.5"),
                "「击破特攻 ≥ 150% 时」");
        Assertions.assertEquals(0.12, speed.getAsJsonArray("do").get(0).getAsJsonObject().get("percent").getAsDouble(),
                1e-12, "12%（叠影 1）");
    }

    /**
     * 23028 偏偏希望无价: only the crit share is spelled - both of its other clauses are registered.
     */
    @Test
    public void coneTwoThreeTwoEightHasOnlyTheCritShare() {
        Fixture f = new Fixture(23028);
        Fixture bare = new Fixture(null);
        double crit = f.attribute(AttributeType.CRIT_CHANCE) - bare.attribute(AttributeType.CRIT_CHANCE);
        System.out.println("[23028] 暴击率增量 " + crit + " ; 规则数 " + rules(23028));
        Assertions.assertTrue(crit > 0, "「暴击率提高 16%」—— 光锥属性给一次");
        Assertions.assertEquals(0, rules(23028), "缺口见证：没有规则（两句都没有拼法，见状态表）");
    }

    /**
     * 23029 那无数个春天: the armour-break state lands with its 60% base chance and its own share.
     */
    @Test
    public void coneTwoThreeTwoNineArmorBreakWithItsChance() {
        JsonObject rule = rule(23029, "cone23029_armor_break_normal");
        System.out.println("[23029] 卸甲: when=" + rule.getAsJsonArray("when") + " on_any=" + rule.get("on_any")
                + " chance=" + rule.getAsJsonArray("do").get(0).getAsJsonObject().get("base_chance")
                + " percent=" + rule.getAsJsonArray("do").get(1).getAsJsonObject().get("percent"));
        Assertions.assertEquals("0.6", rule.getAsJsonArray("do").get(0).getAsJsonObject().get("base_chance").toString(),
                "「60% 基础概率」");
        Assertions.assertEquals(0.1, rule.getAsJsonArray("do").get(1).getAsJsonObject().get("percent").getAsDouble(),
                1e-12, "「受到的伤害提高 10%」（叠影 1，用 #3 而不是 #6）");
        Assertions.assertEquals(2, rule.getAsJsonArray("do").get(1).getAsJsonObject().get("turns").getAsInt(),
                "「持续 2 回合」");
        Assertions.assertTrue(rule.get("on_any").toString().contains("SKILL_CAST"), "「普攻、战技、终结技」");
    }

    /**
     * 23030 落日时起舞: 【火舞】 stacks twice on each ultimate, two turns each, and the follow-up share is per layer.
     */
    @Test
    public void coneTwoThreeThreeZeroFireDanceStacks() {
        JsonObject stack = rule(23030, "cone23030_fire_dance").getAsJsonArray("do").get(0).getAsJsonObject();
        JsonObject share = rule(23030, "cone23030_fire_dance").getAsJsonArray("do").get(1).getAsJsonObject();
        System.out.println("[23030] 火舞: max=" + stack.get("max_stacks") + " turns=" + stack.get("turns")
                + " ; 每层追加攻击 +" + share.get("percent") + " per_stack=" + share.get("per_stack"));
        Assertions.assertEquals(2, stack.get("max_stacks").getAsInt(), "「最多叠加 2 层」");
        Assertions.assertEquals(2, stack.get("turns").getAsInt(), "「持续 2 回合」");
        Assertions.assertEquals("火舞", share.get("per_stack").getAsString(), "「**每层**使追加攻击伤害提高 36%」");

        Fixture f = new Fixture(23030);
        Assertions.assertTrue(f.wearer.getAttribute(AttributeType.AGGRO_ADDED_RATIO).get() > 0, "「受到攻击的概率大幅提高」");
    }

    // ==================================================================

    private static JsonObject rule(int cone, String id) {
        try (java.io.InputStream stream = ConeAuditBatchTwentyTwoTest.class
                .getResourceAsStream("/game/light_cones/" + cone + ".json")) {
            Assertions.assertNotNull(stream, "missing cone " + cone);
            JsonObject root = JsonParser.parseReader(
                    new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (com.google.gson.JsonElement each : root.getAsJsonArray("1")) {
                JsonObject candidate = each.getAsJsonObject();
                if (id.equals(candidate.get("id").getAsString())) {
                    return candidate;
                }
            }
            throw new AssertionError("no rule " + id);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int rules(int cone) {
        try (java.io.InputStream stream = ConeAuditBatchTwentyTwoTest.class
                .getResourceAsStream("/game/light_cones/" + cone + ".json")) {
            Assertions.assertNotNull(stream, "missing cone " + cone);
            return JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("1").size();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class Fixture {
        private final Character wearer;
        private final Character ally;
        private final Battle battle;

        private Fixture(Integer cone) {
            Weapon weapon = cone == null ? null : Weapon.build(cone, LEVEL, false, 1);
            this.wearer = CharacterFactory.create(WEARER, LEVEL, false, weapon);
            this.ally = CharacterFactory.create(ALLY, LEVEL);
            this.battle = new Battle(List.of(wearer, ally), List.of(BattleFixture.dummy()), BattleFixture.alwaysRoll(0.9999));
            battle.startBattle();
            battle.processRequests();
        }

        private double attribute(AttributeType type) {
            return wearer.getAttribute(type).get();
        }
    }
}
