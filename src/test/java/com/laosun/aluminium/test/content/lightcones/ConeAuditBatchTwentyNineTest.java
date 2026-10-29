package com.laosun.aluminium.test.content.lightcones;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The final audit batch of light cones (24002-24006).
 *
 * <p>Defects: 24003's DoT share said 0.48 (that is rank 5) instead of 0.24; 24004 stated its attack share twice; 24002's shield
 * had no per-3-turn limit. 24002's shielded half and 24004's per-hit stack are registered.
 */
public class ConeAuditBatchTwentyNineTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1204;

    /**
     * 24002 记忆的质料: an unshielded wearer that is hit gains a shield of 16% of her max HP, at most every three turns.
     */
    @Test
    public void coneTwoFourZeroTwoShieldOnHitWithCooldown() {
        Fixture f = new Fixture(24002);
        Fixture bare = new Fixture(null);
        double resist = f.wearer.getAttribute(AttributeType.EFFECT_RESISTANCE).get()
                - bare.wearer.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        System.out.println("[24002] 效果抵抗增量 " + resist);
        Assertions.assertEquals(0.08, resist, 1e-9, "「效果抵抗提高 8%」—— 属性给一次");

        JsonObject rule = rule(24002, "cone24002_shield_when_unshielded");
        JsonObject shield = rule.getAsJsonArray("do").get(0).getAsJsonObject();
        System.out.println("[24002] 护盾: scale=" + shield.get("scale") + " percent=" + shield.get("percent")
                + " turns=" + shield.get("turns") + " cooldown=" + rule.get("cooldown"));
        Assertions.assertEquals("owner_max_hp", shield.get("scale").getAsString(), "「等同于装备者生命上限」");
        Assertions.assertEquals(0.16, shield.get("percent").getAsDouble(), 1e-12, "16%");
        Assertions.assertEquals(2, shield.get("turns").getAsInt(), "「持续 2 回合」");
        Assertions.assertEquals(3, rule.get("cooldown").getAsInt(), "「每 3 回合只能触发 1 次」（2026-10-19 补的）");
    }

    /**
     * 24003 孤独的疗愈: the DoT share is 24% at rank 1 (it said 48%, the rank-5 value).
     */
    @Test
    public void coneTwoFourZeroThreeDotShareIsTwentyFour() {
        JsonObject effect = effect(24003, "cone24003_ult_dot", 0);
        System.out.println("[24003] 持续伤害: percent=" + effect.get("percent") + " turns=" + effect.get("turns")
                + " attribute=" + effect.get("attribute"));
        Assertions.assertEquals(0.24, effect.get("percent").getAsDouble(), 1e-12,
                "「持续伤害提高 24%」（叠影 1；原写 0.48 是叠影 5 的值 ✗）");
        Assertions.assertEquals("DOT_DAMAGE_BOOST", effect.get("attribute").getAsString(), "「造成的**持续伤害**」");
        Assertions.assertEquals(2, effect.get("turns").getAsInt(), "「持续 2 回合」");
    }

    /**
     * 24004 不息的演算: the attack share is the property; three or more targets give speed for a turn.
     */
    @Test
    public void coneTwoFourZeroFourSpeedOnThreeTargets() {
        Fixture f = new Fixture(24004);
        Fixture bare = new Fixture(null);
        System.out.println("[24004] 攻击增量 " + (f.wearer.getAttribute(AttributeType.ATTACK).get()
                - bare.wearer.getAttribute(AttributeType.ATTACK).get()) + " ; 规则数 " + rules(24004));
        Assertions.assertTrue(f.wearer.getAttribute(AttributeType.ATTACK).get()
                > bare.wearer.getAttribute(AttributeType.ATTACK).get(), "「攻击力提高 8%」—— 属性给一次");
        Assertions.assertEquals(1, rules(24004), "只剩速度那一条（重复的攻击规则已删）");

        JsonObject speed = effect(24004, "cone24004_speed_on_wide_hit", 0);
        System.out.println("[24004] 速度: percent=" + speed.get("percent") + " turns=" + speed.get("turns"));
        Assertions.assertEquals(0.08, speed.get("percent").getAsDouble(), 1e-12, "「速度提高 8%」");
        Assertions.assertEquals(1, speed.get("turns").getAsInt(), "「持续 1 回合」");
        Assertions.assertTrue(rule(24004, "cone24004_speed_on_wide_hit").getAsJsonArray("when").toString()
                .contains("hit_count >= 3"), "「击中 ≥ 3 名敌方目标」");
    }

    /**
     * 24005 记忆永不落幕: the speed share is the property, and a skill gives the party damage for three turns.
     */
    @Test
    public void coneTwoFourZeroFivePartyDamageOnSkill() {
        Fixture f = new Fixture(24005);
        Fixture bare = new Fixture(null);
        System.out.println("[24005] 速度增量 " + (f.wearer.getAttribute(AttributeType.SPEED).get()
                - bare.wearer.getAttribute(AttributeType.SPEED).get()));
        Assertions.assertTrue(f.wearer.getAttribute(AttributeType.SPEED).get()
                > bare.wearer.getAttribute(AttributeType.SPEED).get(), "「速度提高 6%」—— 属性给一次");

        double allyBefore = f.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        f.battle.castImmediate(f.wearer.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), f.wearer,
                List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        double gained = f.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - allyBefore;
        System.out.println("[24005] 战技后队友增伤 " + gained);
        Assertions.assertEquals(0.08, gained, 1e-9, "「我方全体造成的伤害提高 8%」，3 回合");
    }

    /**
     * 24006 欢愉满溢祝福: her skill OR ultimate on an ally raises THAT ally's elation damage.
     */
    @Test
    public void coneTwoFourZeroSixElationOnTheAlly() {
        JsonObject skill = effect(24006, "cone24006_ally_elation_on_skill", 0);
        System.out.println("[24006] 目标欢愉度: percent=" + skill.get("percent") + " turns=" + skill.get("turns")
                + " target=" + skill.get("target"));
        Assertions.assertEquals("ELATION_DAMAGE_BOOST", skill.get("attribute").getAsString(), "「目标**欢愉度**提高」");
        Assertions.assertEquals(0.12, skill.get("percent").getAsDouble(), 1e-12, "12%（叠影 1）");
        Assertions.assertEquals("target", skill.get("target").getAsString(), "「**目标**」—— 不是她自己");
        Assertions.assertEquals(2, skill.get("turns").getAsInt(), "「持续 2 回合」");
        Assertions.assertTrue(rules(24006) == 2, "战技与终结技两条（都对我方单体）");
    }

    // ==================================================================

    private static JsonObject rule(int cone, String id) {
        try (java.io.InputStream stream = ConeAuditBatchTwentyNineTest.class
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
        try (java.io.InputStream stream = ConeAuditBatchTwentyNineTest.class
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
