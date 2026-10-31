package com.laosun.aluminium.test.content.lightcones;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The twenty-seventh audit batch of light cones (23056-23061).
 *
 * <p>Defects: 23056 and 23059 stated their crit/health share twice; 23060's defence ignore had no `actor == self` (any ally's
 * damage would ignore 32%). 23059's 【炼狱】 crit-damage effects, 23058's energy-cap clause and 23060's 启航 half are registered.
 */
public class ConeAuditBatchTwentySevenTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1204;

    /**
     * 23056 一场谎言的终幕: the crit share is the property; 【影噬】 comes at battle start and every four follow-ups.
     */
    @Test
    public void coneTwoThreeFiveSixShadowEveryFourFollowUps() {
        Fixture f = new Fixture(23056);
        Fixture bare = new Fixture(null);
        double crit = f.wearer.getAttribute(AttributeType.CRIT_CHANCE).get()
                - bare.wearer.getAttribute(AttributeType.CRIT_CHANCE).get();
        System.out.println("[23056] 暴击率增量 " + crit + " ; 规则数 " + rules(23056));
        Assertions.assertEquals(0.18, crit, 1e-9, "「暴击率提高 18%」—— 属性给一次");
        Assertions.assertEquals(3, rules(23056), "开战【影噬】、累计计数、每 4 次付清");

        JsonObject counter = effect(23056, "cone23056_shadow_every4_count", 0);
        System.out.println("[23056] 计数上限 " + counter.get("max_stacks"));
        Assertions.assertEquals(4, counter.get("max_stacks").getAsInt(), "「每累计 4 次」");
    }

    /**
     * 23057 欢迎来到银河城: the elation defence ignore, and the ultimate pays 20 【笑点】 once, reset by three basics.
     */
    @Test
    public void coneTwoThreeFiveSevenLaughsOnSelfUlt() {
        JsonObject ignore = effect(23057, "cone23057_elation_def_ignore", 0);
        System.out.println("[23057] 无视防御: damage_type=" + ignore.get("damage_type") + " percent=" + ignore.get("percent"));
        Assertions.assertEquals("ELATION", ignore.get("damage_type").getAsString(), "「造成的**欢愉伤害**无视防御」");

        JsonObject pay = effect(23057, "cone23057_laughter_on_self_ult", 0);
        System.out.println("[23057] 笑点: op=" + pay.get("op") + " resource=" + pay.get("resource") + " amount=" + pay.get("amount"));
        Assertions.assertEquals("GAIN_RESOURCE", pay.get("op").getAsString(), "「获得 20 点【笑点】」");
        Assertions.assertEquals(20.0, pay.get("amount").getAsDouble(), 1e-9, "20 点（叠影 1）");
    }

    /**
     * 23058 邂逅于下一个花季: the crit-damage share is the property; the elation cast leaves 15% vulnerability for two turns.
     */
    @Test
    public void coneTwoThreeFiveEightVulnerabilityOnElationCast() {
        Fixture f = new Fixture(23058);
        Fixture bare = new Fixture(null);
        double crit = f.wearer.getAttribute(AttributeType.CRIT_ATTACK).get()
                - bare.wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
        System.out.println("[23058] 暴伤增量 " + crit);
        Assertions.assertEquals(0.6, crit, 1e-9, "「暴击伤害提高 60%」—— 属性给一次");

        JsonObject vuln = effect(23058, "cone23058_elation_cast_damage_taken", 0);
        System.out.println("[23058] 易伤: percent=" + vuln.get("percent") + " turns=" + vuln.get("turns")
                + " target=" + vuln.get("target"));
        Assertions.assertEquals(0.15, vuln.get("percent").getAsDouble(), 1e-12, "15%（叠影 1）");
        Assertions.assertEquals(2, vuln.get("turns").getAsInt(), "「持续 2 回合」");
        Assertions.assertEquals("all_enemies", vuln.get("target").getAsString(), "「敌方全体」");
    }

    /**
     * 23059 灼尽炼狱的新骸: the health share is the property; the turn energy fires once a wave; the skill sticks 【炼狱】; the\n * two crit-damage shares are spelled now (judged in ConeTwentyThreeThousandFiftyNineTest).
     */
    @Test
    public void coneTwoThreeFiveNinePurgatoryAndWaveEnergy() {
        Fixture f = new Fixture(23059);
        Fixture bare = new Fixture(null);
        double health = f.wearer.getAttribute(AttributeType.HEALTH).get() - bare.wearer.getAttribute(AttributeType.HEALTH).get();
        System.out.println("[23059] 生命上限增量 " + health + " ; 规则数 " + rules(23059));
        Assertions.assertTrue(health > 0, "「生命上限提高 30%」");
        Assertions.assertEquals(5, rules(23059), "回合回能、波次重置、【炼狱】、两条暴伤份额");

        f.wearer.setCurrentEnergy(0);
        f.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(com.laosun.aluminium.enums.TriggerEvent.TURN_START, f.wearer, f.wearer, 0, 0));
        f.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(com.laosun.aluminium.enums.TriggerEvent.TURN_START, f.wearer, f.wearer, 0, 0));
        System.out.println("[23059] 两次回合开始后的能量 " + f.wearer.getCurrentEnergy() + "（每波次只 1 次）");
        Assertions.assertEquals(20.0, f.wearer.getCurrentEnergy(), 1e-9, "「每个波次可触发 1 次」");

        JsonObject purgatory = effect(23059, "cone23059_purgatory", 0);
        Assertions.assertEquals(2, purgatory.get("turns").getAsInt(), "【炼狱】持续 2 回合");
    }

    /**
     * 23060 当一颗星照亮夜空: the defence ignore is HERS (the gate that was missing).
     */
    @Test
    public void coneTwoThreeSixZeroDefenceIgnoreIsHers() {
        JsonObject rule = rule(23060, "cone23060_def_ignore");
        System.out.println("[23060] 无视防御: when=" + rule.getAsJsonArray("when") + " percent="
                + rule.getAsJsonArray("do").get(0).getAsJsonObject().get("percent"));
        Assertions.assertEquals("actor == self", rule.getAsJsonArray("when").get(0).getAsString(),
                "「**装备者**造成伤害时」—— 队友的伤害不算（2026-10-19 补的门）");
        Assertions.assertEquals(0.32, rule.getAsJsonArray("do").get(0).getAsJsonObject().get("percent").getAsDouble(),
                1e-9, "32%（叠影 1）");
    }

    /**
     * 23061 星火悄然闪耀: four skill points in one turn pay out the crown, and her skill damage rises 72% while she has it.
     */
    @Test
    public void coneTwoThreeSixOneCrownAndSkillDamage() {
        Fixture f = new Fixture(23061);
        Fixture bare = new Fixture(null);
        System.out.println("[23061] 暴击率增量 " + (f.wearer.getAttribute(AttributeType.CRIT_CHANCE).get()
                - bare.wearer.getAttribute(AttributeType.CRIT_CHANCE).get()));
        Assertions.assertEquals(0.18, f.wearer.getAttribute(AttributeType.CRIT_CHANCE).get()
                - bare.wearer.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-9, "「暴击率提高 18%」—— 属性给一次");

        JsonObject skill = effect(23061, "cone23061_skill_damage_boost", 0);
        System.out.println("[23061] 战技加伤: percent=" + skill.get("percent"));
        Assertions.assertEquals(0.72, skill.get("percent").getAsDouble(), 1e-12, "「战技伤害提高 72%」");

        JsonObject crown = effect(23061, "cone23061_crown_pays_out", 2);
        System.out.println("[23061] 王冠: 全队无视防御 " + crown.get("percent") + " target=" + crown.get("target"));
        Assertions.assertEquals(0.2, crown.get("percent").getAsDouble(), 1e-12, "「我方全体无视 20% 防御」");
        Assertions.assertEquals("all_allies", crown.get("target").getAsString(), "「我方全体」");
    }

    // ==================================================================

    private static JsonObject rule(int cone, String id) {
        try (java.io.InputStream stream = ConeAuditBatchTwentySevenTest.class
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

    private static JsonObject effect(int cone, String id, int index) {
        return rule(cone, id).getAsJsonArray("do").get(index).getAsJsonObject();
    }

    private static int rules(int cone) {
        try (java.io.InputStream stream = ConeAuditBatchTwentySevenTest.class
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
    }
}
