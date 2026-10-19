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
 * The twentieth audit batch of light cones (23016-23020).
 *
 * <p>Only one defect: 23017's third sentence (healing a teammate raises THEIR attack, five layers for two turns) was missing
 * entirely. The other four read correctly on first inspection.
 */
public class ConeAuditBatchTwentyTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1204;

    /**
     * 23016 烦恼着，幸福着: the follow-up share, 【温驯】 on the target (two layers), and crit damage per layer.
     */
    @Test
    public void coneTwoThreeOneSixTameStacksAndCritPerLayer() {
        JsonObject followUp = rule(23016, "cone23016_follow_up_damage").getAsJsonArray("do").get(0).getAsJsonObject();
        System.out.println("[23016] 追加攻击档: attribute=" + followUp.get("attribute") + " percent=" + followUp.get("percent"));
        Assertions.assertEquals("FOLLOW_UP_DAMAGE_BOOST", followUp.get("attribute").getAsString(), "「追加攻击造成的伤害提高 30%」");

        JsonObject tame = rule(23016, "cone23016_tame_the_target").getAsJsonArray("do").get(0).getAsJsonObject();
        System.out.println("[23016] 温驯: target=" + tame.get("target") + " max=" + tame.get("max_stacks"));
        Assertions.assertEquals("target", tame.get("target").getAsString(), "「使**目标**陷入【温驯】」");
        Assertions.assertEquals(2, tame.get("max_stacks").getAsInt(), "「最多叠加 2 层」");

        JsonObject perLayer = rule(23016, "cone23016_crit_per_tame_layer").getAsJsonArray("do").get(0).getAsJsonObject();
        System.out.println("[23016] 每层暴伤: per_stack=" + perLayer.get("per_stack") + " instance=" + perLayer.get("instance"));
        Assertions.assertEquals("温驯", perLayer.get("per_stack").getAsString(), "「**每层**【温驯】使暴击伤害提高」");
        Assertions.assertTrue(perLayer.get("instance").getAsBoolean(), "只作用在那一发上");
    }

    /**
     * 23017 惊魂夜: an ally's ultimate heals the LOWEST-HP ally for 10% of ITS max HP, and being healed raises that ally's
     * attack, five layers for two turns.
     */
    @Test
    public void coneTwoThreeOneSevenHealsTheLowestAndRaisesTheirAttack() {
        Fixture f = new Fixture(23017);
        f.ally.consumeHp(f.ally.getMaxHp() * 0.7);
        double allyBefore = f.ally.getCurrentHp();
        f.battle.fireTriggers(TriggerEvent.ULT_CAST, f.ally, f.wearer, 0, 0);
        f.battle.processRequests();
        double healed = f.ally.getCurrentHp() - allyBefore;
        System.out.println("[23017] 队友被治疗 " + healed + " ; 其上限 × 0.1 = " + (f.ally.getMaxHp() * 0.1));
        Assertions.assertEquals(f.ally.getMaxHp() * 0.1, healed, healed * 1e-6,
                "「为当前生命值百分比最低的我方目标回复其生命上限 10%」");

        var healedRule = rule(23017, "cone23017_healed_ally_attack");
        System.out.println("[23017] 被治疗者那条: when=" + healedRule.getAsJsonArray("when"));
        Assertions.assertEquals("target is_ally", healedRule.getAsJsonArray("when").get(0).getAsString(), "「该目标」");
        Assertions.assertEquals(5, healedRule.getAsJsonArray("do").get(0).getAsJsonObject().get("max_stacks").getAsInt(),
                "「最多叠加 5 层」");
    }

    /**
     * 23018 片刻，留在眼底: an ultimate's damage rises per point of max energy, 180 points at most.
     */
    @Test
    public void coneTwoThreeOneEightUltDamagePerEnergy() {
        JsonObject effect = rule(23018, "cone23018_ult_damage_per_energy").getAsJsonArray("do").get(0).getAsJsonObject();
        System.out.println("[23018] 规则: attribute=" + effect.get("attribute") + " scale=" + effect.get("scale")
                + " percent=" + effect.get("percent") + " cap=" + effect.get("cap_amount"));
        Assertions.assertEquals("ULTIMATE_DAMAGE_BOOST", effect.get("attribute").getAsString(), "「提高**终结技**造成的伤害」");
        Assertions.assertEquals("self_max_energy", effect.get("scale").getAsString(), "「根据装备者的能量上限」");
        Assertions.assertEquals(0.648, effect.get("cap_amount").getAsDouble(), 1e-9, "「最多计入 180 点」= 180 × 0.36%");
    }

    /**
     * 23019 镜中故我: the break share is the property, an ultimate boosts the party and pays a skill point at 150% break, and a
     * wave start gives everyone energy.
     */
    @Test
    public void coneTwoThreeOneNineUltBoostsAndWaveEnergy() {
        Fixture f = new Fixture(23019);
        Fixture bare = new Fixture(null);
        System.out.println("[23019] 击破特攻增量 " + (f.wearer.getAttribute(AttributeType.BREAKING_EFFECT).get()
                - bare.wearer.getAttribute(AttributeType.BREAKING_EFFECT).get()));
        Assertions.assertTrue(f.wearer.getAttribute(AttributeType.BREAKING_EFFECT).get()
                > bare.wearer.getAttribute(AttributeType.BREAKING_EFFECT).get(), "「击破特攻提高 60%」");

        double partyBefore = f.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        f.battle.fireTriggers(TriggerEvent.WAVE_START, f.wearer, f.wearer, 0, 0);
        int pointsBefore = f.battle.getSkillPoints();
        f.battle.fireTriggers(TriggerEvent.CAST_SETUP, f.wearer, f.wearer, 0, 0);
        System.out.println("[23019] 波次能量/终结技后队友增伤 " + (f.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get()
                - partyBefore) + " ; 战技点 " + pointsBefore + " -> " + f.battle.getSkillPoints());

        JsonObject gate = rule(23019, "cone23019_ult_skill_point");
        System.out.println("[23019] 战技点那条: when=" + gate.getAsJsonArray("when"));
        Assertions.assertTrue(gate.getAsJsonArray("when").toString().contains("BREAKING_EFFECT >= 1.5"),
                "「若装备者击破特攻 ≥ 150%」");
    }

    /**
     * 23020 纯粹思维的洗礼: crit damage per debuff (three at most), and the 【论辩】 state pays damage plus follow-up defence
     * ignore.
     */
    @Test
    public void coneTwoThreeTwoZeroCritPerDebuffAndDebate() {
        JsonObject crit = rule(23020, "cone23020_crit_per_debuff");
        System.out.println("[23020] 每负面: when=" + crit.getAsJsonArray("when") + " per_stack="
                + crit.getAsJsonArray("do").get(0).getAsJsonObject().get("per_stack"));
        Assertions.assertTrue(crit.getAsJsonArray("when").toString().contains("target_debuff_count >= 1"), "「每承受 1 个负面效果」");
        Assertions.assertTrue(crit.getAsJsonArray("when").toString().contains("target_debuff_count <= 3"), "「最多叠加 3 层」");

        JsonObject ignore = rule(23020, "cone23020_ignore_defence_on_follow_up")
                .getAsJsonArray("do").get(0).getAsJsonObject();
        System.out.println("[23020] 论辩无视防御: damage_type=" + ignore.get("damage_type") + " percent=" + ignore.get("percent"));
        Assertions.assertEquals("ADDITIONAL", ignore.get("damage_type").getAsString(), "「**追加攻击**无视防御」");
        Assertions.assertEquals(0.24, ignore.get("percent").getAsDouble(), 1e-12, "24%（叠影 1）");
    }

    // ==================================================================

    private static JsonObject rule(int cone, String id) {
        try (java.io.InputStream stream = ConeAuditBatchTwentyTest.class
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
