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
 * The twenty-eighth audit batch of light cones (23062-23064, 24000-24001).
 *
 * <p>Defects: 23062 and 24001 stated their stat shares twice; 23063 used 0.4 for two clauses that say 30%; 24000's per-attack
 * attack had no stack buffer. 23064's 【潮流】 is registered (the engine cannot remember the previous Elation skill).
 */
public class ConeAuditBatchTwentyEightTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1204;

    /**
     * 23062 所见即我: the attack and energy-regen shares are the properties; the ultimate's rider is per energy SPENT.
     */
    @Test
    public void coneTwoThreeSixTwoUltScalesOffEnergySpent() {
        Fixture f = new Fixture(23062);
        Fixture bare = new Fixture(null);
        System.out.println("[23062] 攻击增量 " + (f.wearer.getAttribute(AttributeType.ATTACK).get()
                - bare.wearer.getAttribute(AttributeType.ATTACK).get()) + " ; 能量恢复增量 "
                + (f.wearer.getAttribute(AttributeType.ENERGY_REGENERATION_RATE).get()
                - bare.wearer.getAttribute(AttributeType.ENERGY_REGENERATION_RATE).get()) + " ; 规则数 " + rules(23062));
        Assertions.assertTrue(f.wearer.getAttribute(AttributeType.ATTACK).get()
                > bare.wearer.getAttribute(AttributeType.ATTACK).get(), "「攻击力提高 18%」—— 属性给一次");
        Assertions.assertTrue(f.wearer.getAttribute(AttributeType.ENERGY_REGENERATION_RATE).get()
                > bare.wearer.getAttribute(AttributeType.ENERGY_REGENERATION_RATE).get(), "「能量恢复效率提高 10%」");
        Assertions.assertEquals(3, rules(23062), "【王之娱乐】两条 + 能量缩放一条");

        JsonObject rider = effect(23062, "cone23062_energy_scale", 0);
        System.out.println("[23062] 能量缩放: scale=" + rider.get("scale") + " percent=" + rider.get("percent")
                + " cap=" + rider.get("cap_amount"));
        Assertions.assertEquals("cast_energy_spent", rider.get("scale").getAsString(), "「**每消耗 1 点能量**」");
        Assertions.assertEquals(0.72, rider.get("cap_amount").getAsDouble(), 1e-9, "「最多提高 72%」");
    }

    /**
     * 23063 你将起身歌唱: the advance and the party speed are both 30% (they used to say 40%).
     */
    @Test
    public void coneTwoThreeSixThreeAdvanceAndSpeedAreThirty() {
        JsonObject advance = effect(23063, "cone23063_battle_advance", 0);
        JsonObject speed = effect(23063, "cone23063_battle_advance", 2);
        System.out.println("[23063] 行动提前 " + advance.get("percent") + " ; 全队速度 " + speed.get("percent"));
        Assertions.assertEquals(0.3, advance.get("percent").getAsDouble(), 1e-12, "「行动提前 30%」（原写 0.4 ✗）");
        Assertions.assertEquals(0.3, speed.get("percent").getAsDouble(), 1e-12, "「我方全体速度提高 30%」（原写 0.4 ✗）");
        Assertions.assertEquals("all_allies", speed.get("target").getAsString(), "「我方全体」");

        Fixture f = new Fixture(23063);
        int before = f.battle.getSkillPoints();
        f.wearer.setCurrentEnergy(f.wearer.getMaxEnergy());
        f.battle.castUltra(f.wearer, List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        System.out.println("[23063] 终结技后的战技点 " + before + " -> " + f.battle.getSkillPoints());
        Assertions.assertEquals(before + 1, f.battle.getSkillPoints(), "「施放终结技后为我方恢复 1 点战技点」");
    }

    /**
     * 23064 向浪花掷下盛夏: the crit share is the property, and every third Elation cast pays a skill point.
     */
    @Test
    public void coneTwoThreeSixFourElationCastsPaySkillPoints() {
        Fixture f = new Fixture(23064);
        Fixture bare = new Fixture(null);
        System.out.println("[23064] 暴击率增量 " + (f.wearer.getAttribute(AttributeType.CRIT_CHANCE).get()
                - bare.wearer.getAttribute(AttributeType.CRIT_CHANCE).get()));
        Assertions.assertEquals(0.18, f.wearer.getAttribute(AttributeType.CRIT_CHANCE).get()
                - bare.wearer.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-9, "「暴击率提高 18%」—— 属性给一次");

        JsonObject wind = effect(23064, "cone23064_wind_gust", 1);
        System.out.println("[23064] 风口: speed=" + wind.get("percent"));
        Assertions.assertEquals(0.24, wind.get("percent").getAsDouble(), 1e-12, "「速度提高 24%」");

        JsonObject pay = effect(23064, "cone23064_skill_point_per_three_casts", 0);
        System.out.println("[23064] 三次施放后: " + pay.get("op"));
        Assertions.assertEquals("GAIN_SKILL_POINT", pay.get("op").getAsString(), "「每施放 3 次欢愉技后恢复 1 个战技点」");
    }

    /**
     * 24000 记一位星神的陨落: each attack adds a layer of attack (four at most), and a break adds damage for two turns.
     */
    @Test
    public void coneTwoFourZeroZeroStacksPerAttack() {
        JsonObject stack = effect(24000, "cone24000_attack_per_attack", 0);
        JsonObject share = effect(24000, "cone24000_attack_per_attack", 1);
        System.out.println("[24000] 叠层: max=" + stack.get("max_stacks") + " per_stack=" + share.get("per_stack"));
        Assertions.assertEquals(4, stack.get("max_stacks").getAsInt(), "「最多叠加 4 层」");
        Assertions.assertEquals("星神层数", share.get("per_stack").getAsString(), "「攻击力提高 8%」按层");

        JsonObject onBreak = effect(24000, "cone24000_damage_after_break", 0);
        System.out.println("[24000] 击破后: percent=" + onBreak.get("percent") + " turns=" + onBreak.get("turns"));
        Assertions.assertEquals(0.12, onBreak.get("percent").getAsDouble(), 1e-12, "「造成的伤害提高 12%」");
        Assertions.assertEquals(2, onBreak.get("turns").getAsInt(), "「持续 2 回合」");
    }

    /**
     * 24001 星海巡航: the crit share once, crit against low-HP targets, and HER kills raise attack.
     */
    @Test
    public void coneTwoFourZeroOneCritAndKillAttack() {
        Fixture f = new Fixture(24001);
        Fixture bare = new Fixture(null);
        double crit = f.wearer.getAttribute(AttributeType.CRIT_CHANCE).get()
                - bare.wearer.getAttribute(AttributeType.CRIT_CHANCE).get();
        System.out.println("[24001] 暴击率增量 " + crit);
        Assertions.assertEquals(0.08, crit, 1e-9, "「暴击率提高 8%」—— 属性给一次");

        JsonObject kill = rule(24001, "cone24001_attack_after_kill");
        System.out.println("[24001] 击杀那条: when=" + kill.getAsJsonArray("when"));
        Assertions.assertEquals("actor == self", kill.getAsJsonArray("when").get(0).getAsString(),
                "「**装备者**消灭敌方目标后」（2026-10-19 补的门）");
        Assertions.assertEquals(2, effect(24001, "cone24001_attack_after_kill", 0).get("turns").getAsInt(), "「持续 2 回合」");
    }

    // ==================================================================

    private static JsonObject rule(int cone, String id) {
        try (java.io.InputStream stream = ConeAuditBatchTwentyEightTest.class
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
        try (java.io.InputStream stream = ConeAuditBatchTwentyEightTest.class
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
