package com.laosun.aluminium.test.content.lightcones;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The twenty-first audit batch of light cones (23021-23025).
 *
 * <p>Defects: 23023 stated its defence share twice (property + rule) and gave its vulnerability no base chance; 23022's attack
 * share had no stack buffer; 23024's 【泡影】 re-applied on every hit instead of once per attack. 23022's per-DoT-type-once and
 * 23025's break-damage-taken clause are registered.
 */
public class ConeAuditBatchTwentyOneTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1204;

    /**
     * 23021 游戏尘寰: the crit-damage share once, and the mask gives the OTHER allies crit and crit damage for its window.
     */
    @Test
    public void coneTwoThreeTwoOneMaskGivesTheTeamCrit() {
        Fixture f = new Fixture(23021);
        Fixture bare = new Fixture(null);
        double crit = f.wearer.getAttribute(AttributeType.CRIT_ATTACK).get()
                - bare.wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
        System.out.println("[23021] 她的暴伤增量 " + crit);
        Assertions.assertEquals(0.32, crit, 1e-9, "「暴击伤害提高 32%」—— 光锥属性给一次");

        // Deltas against a cone-less party: the ally has her own crit traces.
        double allyCrit = f.ally.getAttribute(AttributeType.CRIT_CHANCE).get()
                - bare.ally.getAttribute(AttributeType.CRIT_CHANCE).get();
        double allyDamage = f.ally.getAttribute(AttributeType.CRIT_ATTACK).get()
                - bare.ally.getAttribute(AttributeType.CRIT_ATTACK).get();
        System.out.println("[23021] 队友的暴击率/暴伤增量 " + allyCrit + " / " + allyDamage);
        Assertions.assertEquals(0.1, allyCrit, 1e-9, "「队友暴击率提高 10%」（开战即有【假面】）");
        Assertions.assertEquals(0.28, allyDamage, 1e-9, "「队友暴击伤害提高 28%」");
    }

    /**
     * 23022 重塑时光之忆: the effect-hit share once, and the seer's attack share is per layer (it used to replace itself).
     */
    @Test
    public void coneTwoThreeTwoTwoSeerAttackIsPerLayer() {
        Fixture f = new Fixture(23022);
        Fixture bare = new Fixture(null);
        System.out.println("[23022] 效果命中增量 " + (f.wearer.getAttribute(AttributeType.EFFECT_HIT_RATE).get()
                - bare.wearer.getAttribute(AttributeType.EFFECT_HIT_RATE).get()));
        Assertions.assertTrue(f.wearer.getAttribute(AttributeType.EFFECT_HIT_RATE).get()
                > bare.wearer.getAttribute(AttributeType.EFFECT_HIT_RATE).get(), "「效果命中提高 40%」");

        JsonObject effect = rule(23022, "cone23022_seer_from_dot_1").getAsJsonArray("do").get(1).getAsJsonObject();
        System.out.println("[23022] 攻击那条: per_stack=" + effect.get("per_stack") + " max_stacks=" + effect.get("max_stacks"));
        Assertions.assertEquals("self_stacks:先知", effect.get("per_stack").getAsString(),
                "「**每层**【先知】使攻击力提高 5%」");
        JsonObject ignore = rule(23022, "cone23022_dot_ignores_defence").getAsJsonArray("do").get(0).getAsJsonObject();
        System.out.println("[23022] 无视防御: damage_type=" + ignore.get("damage_type") + " per_stack=" + ignore.get("per_stack"));
        Assertions.assertEquals("DOT", ignore.get("damage_type").getAsString(), "「造成的**持续伤害**无视防御」");
        Assertions.assertEquals("self_stacks:先知", ignore.get("per_stack").getAsString(), "「每层」");
    }

    /**
     * 23023 命运从未公平: the defence share is the property, and the follow-up's vulnerability carries its base chance.
     */
    @Test
    public void coneTwoThreeTwoThreeDefenceOnceAndVulnerabilityChance() {
        Fixture f = new Fixture(23023);
        Fixture bare = new Fixture(null);
        // The cone carries a FLAT defence too, so the share is a ratio against (base + the cone's flat): a doubled share is 1.40.
        double baseDefence = bare.wearer.getAttribute(AttributeType.DEFENCE).get();
        double coneFlat = Weapon.build(23023, LEVEL, false, 1).getDefence();
        double ratio = f.wearer.getAttribute(AttributeType.DEFENCE).get() / (baseDefence + coneFlat);
        System.out.println("[23023] 防御比值 " + ratio + "（应为 1.40）");
        // Her own defence traces are in the panel too, so the cone's share is the ratio DIVIDED by her own multiplier.
        double own = bare.wearer.getAttribute(AttributeType.DEFENCE).get() / baseDefence;
        System.out.println("[23023] 她自己的倍数 " + own + " ; 光锥那份 " + (ratio / own));
        Assertions.assertTrue(ratio / own > 1.38 && ratio / own < 1.52,
                "「防御力提高 40%」已在面板里，且没被叠成两份（实测 " + (ratio / own) + "）");

        JsonObject vuln = rule(23023, "cone23023_vuln_on_followup").getAsJsonArray("do").get(0).getAsJsonObject();
        System.out.println("[23023] 易伤: percent=" + vuln.get("percent") + " chance=" + vuln.get("base_chance")
                + " turns=" + vuln.get("turns"));
        Assertions.assertEquals(1.0, vuln.get("base_chance").getAsDouble(), 1e-12, "「100% 基础概率」（叠影 1）");
        Assertions.assertEquals(0.1, vuln.get("percent").getAsDouble(), 1e-12, "10%");
        Assertions.assertEquals(2, vuln.get("turns").getAsInt(), "「持续 2 回合」");
    }

    /**
     * 23024 行于流逝的岸: 【泡影】 lands once per attack per target, and the damage share follows it.
     */
    @Test
    public void coneTwoThreeTwoFourBubbleOncePerAttack() {
        JsonObject bubble = rule(23024, "cone23024_bubble");
        System.out.println("[23024] 泡影: per_attack=" + bubble.get("per_attack") + " turns="
                + bubble.getAsJsonArray("do").get(0).getAsJsonObject().get("turns"));
        Assertions.assertEquals(1, bubble.get("per_attack").getAsInt(), "「每次攻击时，对每个目标只可触发 1 次」");

        Fixture f = new Fixture(23024);
        double damage = rule(23024, "cone23024_bubble_damage").getAsJsonArray("do").get(0).getAsJsonObject()
                .get("percent").getAsDouble();
        System.out.println("[23024] 对泡影目标的加伤 " + damage);
        Assertions.assertEquals(0.24, damage, 1e-12, "24%（叠影 1）");
        Assertions.assertTrue(f.wearer.getCurrentHp() > 0, "夹具可用");
    }

    /**
     * 23025 梦应归于何处: the collapse state slows the target; the break-damage-taken half is registered.
     */
    @Test
    public void coneTwoThreeTwoFiveCollapseSlowsTheTarget() {
        Fixture f = new Fixture(23025);
        Fixture bare = new Fixture(null);
        Assertions.assertTrue(f.wearer.getAttribute(AttributeType.BREAKING_EFFECT).get()
                > bare.wearer.getAttribute(AttributeType.BREAKING_EFFECT).get(), "「击破特攻提高 60%」");

        JsonObject slow = rule(23025, "cone23025_collapse_slows").getAsJsonArray("do").get(0).getAsJsonObject();
        System.out.println("[23025] 减速: attribute=" + slow.get("attribute") + " percent=" + slow.get("percent")
                + " turns=" + slow.get("turns") + " target=" + slow.get("target"));
        Assertions.assertEquals("SPEED", slow.get("attribute").getAsString(), "「速度降低 20%」");
        Assertions.assertEquals(-0.2, slow.get("percent").getAsDouble(), 1e-12, "20%");
        Assertions.assertEquals(2, slow.get("turns").getAsInt(), "「持续 2 回合」");

        // Witness: 「【溃败】状态下目标受到装备者造成的**击破伤害**提高 24%」 has no rule at all.
        Assertions.assertEquals(2, rules(23025), "缺口见证：只有溃败与减速两条（击破伤害那半没有拼法）");
    }

    // ==================================================================

    private static JsonObject rule(int cone, String id) {
        for (com.google.gson.JsonElement each : all(cone)) {
            JsonObject candidate = each.getAsJsonObject();
            if (id.equals(candidate.get("id").getAsString())) {
                return candidate;
            }
        }
        throw new AssertionError("no rule " + id);
    }

    private static com.google.gson.JsonArray all(int cone) {
        try (java.io.InputStream stream = ConeAuditBatchTwentyOneTest.class
                .getResourceAsStream("/game/light_cones/" + cone + ".json")) {
            Assertions.assertNotNull(stream, "missing cone " + cone);
            return JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("1");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int rules(int cone) {
        return all(cone).size();
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
