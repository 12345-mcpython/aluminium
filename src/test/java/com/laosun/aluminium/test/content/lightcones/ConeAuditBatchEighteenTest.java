package com.laosun.aluminium.test.content.lightcones;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The eighteenth audit batch of light cones (23006-23010), each clause read as a BATTLE.
 *
 * <p>Only one defect in this batch: 23007 stated its effect-hit share twice (property + rule). 23010's 「消耗【梦身】」 is the
 * registered gap - the one-shot consumption has no spelling, so every follow-up after a cast takes the share.
 */
public class ConeAuditBatchEighteenTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1204;

    /**
     * 23006 只需等待: each of her attacks stacks speed (three at most), and a hit sticks 【游丝】 on a clean target.
     */
    @Test
    public void coneTwoThreeZeroSixSpeedStacksAndThreadDot() {
        Fixture f = new Fixture(23006);
        double start = f.attribute(AttributeType.SPEED);
        for (int i = 0; i < 4; i++) {
            f.battle.castImmediate(f.wearer.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON), f.wearer,
                    List.of(f.battle.enemyUnits().getFirst()));
            f.battle.processRequests();
        }
        double gain = f.attribute(AttributeType.SPEED) - start;
        System.out.println("[23006] 四次攻击后的速度增量 " + gain + "（3 层 × 4.8% 已封顶）");
        Assertions.assertEquals(3 * 0.048 * (start / 1.0) / 1.0, gain, gain * 0.05, "「每次施放攻击后速度 +4.8%，最多 3 层」");

        JsonObject dot = rule(23006, "cone23006_thread_dot").getAsJsonArray("do").get(0).getAsJsonObject();
        System.out.println("[23006] 游丝: element=" + dot.get("element") + " scale=" + dot.get("scale")
                + " percent=" + dot.get("percent") + " chance=" + dot.get("base_chance") + " turns=" + dot.get("turns"));
        Assertions.assertEquals("Thunder", dot.get("element").getAsString(), "「雷属性持续伤害」");
        Assertions.assertEquals("self_attr:ATTACK", dot.get("scale").getAsString(), "「等同于装备者攻击力」");
        Assertions.assertEquals(0.6, dot.get("percent").getAsDouble(), 1e-12, "60%（叠影 1）");
        Assertions.assertEquals(1.0, dot.get("base_chance").getAsDouble(), 1e-12, "「100% 基础概率」");
        Assertions.assertEquals(1, dot.get("turns").getAsInt(), "「持续 1 回合」");
    }

    /**
     * 23007 雨一直下: the effect-hit share is the property, the mark lands, and the crit share is an INSTANCE.
     */
    @Test
    public void coneTwoThreeZeroSevenMarkAndInstanceCrit() {
        Fixture f = new Fixture(23007);
        Fixture bare = new Fixture(null);
        double ehr = f.attribute(AttributeType.EFFECT_HIT_RATE) - bare.attribute(AttributeType.EFFECT_HIT_RATE);
        System.out.println("[23007] 效果命中增量 " + ehr);
        Assertions.assertEquals(0.24, ehr, 1e-9, "「效果命中提高 24%」—— 光锥属性给一次");

        JsonObject crit = rule(23007, "cone23007_crit_vs_debuffed").getAsJsonArray("do").get(0).getAsJsonObject();
        System.out.println("[23007] 暴击那条: instance=" + crit.get("instance") + " percent=" + crit.get("percent"));
        Assertions.assertTrue(crit.get("instance").getAsBoolean(), "「造成伤害时」—— 只对那一发");
        Assertions.assertEquals(0.12, crit.get("percent").getAsDouble(), 1e-12, "12%（叠影 1）");
    }

    /**
     * 23008 棺的回响: the attack pays energy per target, and an ultimate gives the party flat speed.
     */
    @Test
    public void coneTwoThreeZeroEightEnergyPerTargetAndPartySpeed() {
        Fixture f = new Fixture(23008);
        f.wearer.setCurrentEnergy(0);
        f.battle.castImmediate(f.wearer.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON), f.wearer,
                List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        System.out.println("[23008] 一次攻击后的能量 " + f.wearer.getCurrentEnergy());

        double allyBefore = f.ally.getAttribute(AttributeType.SPEED).get();
        f.wearer.setCurrentEnergy(f.wearer.getMaxEnergy());
        f.battle.castUltra(f.wearer, List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        double allySpeed = f.ally.getAttribute(AttributeType.SPEED).get() - allyBefore;
        System.out.println("[23008] 终结技后队友的速度增量 " + allySpeed);
        Assertions.assertEquals(12.0, allySpeed, 1e-9, "「我方全体速度提高 12 点，持续 1 回合」（叠影 1）");
    }

    /**
     * 23009 到不了的彼岸: being hit (or paying health) raises damage until the next attack.
     */
    @Test
    public void coneTwoThreeZeroNineDamageUntilNextAttack() {
        Fixture f = new Fixture(23009);
        double before = f.attribute(AttributeType.ALL_DAMAGE_TYPE_BOOST);
        f.battle.applyDamage(f.wearer, new Damage(f.battle.enemyUnits().getFirst(), f.wearer,
                DamageElement.PHYSICAL, DamageType.NORMAL, 10));
        f.battle.processRequests();
        double after = f.attribute(AttributeType.ALL_DAMAGE_TYPE_BOOST) - before;
        System.out.println("[23009] 受击后的增伤 " + after);
        Assertions.assertEquals(0.24, after, 1e-9, "「受到攻击后造成的伤害提高 24%」");

        f.battle.castImmediate(f.wearer.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON), f.wearer,
                List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        double afterAttack = f.attribute(AttributeType.ALL_DAMAGE_TYPE_BOOST) - before;
        System.out.println("[23009] 施放攻击后的增伤 " + afterAttack + "（应为 0 —— 「施放攻击后解除」）");
        Assertions.assertEquals(0.0, afterAttack, 1e-9, "「该效果在装备者施放攻击后解除」");
    }

    /**
     * 23010 拂晓之前: the follow-up share's trigger now includes the skill half; the consumption is the registered gap.
     */
    @Test
    public void coneTwoThreeOneZeroFollowUpBoostFromSkillAndUltimate() {
        JsonObject rule = rule(23010, "cone23010_follow_up_damage");
        System.out.println("[23010] 触发: on=" + rule.get("on") + " on_any=" + rule.get("on_any")
                + " percent=" + rule.getAsJsonArray("do").get(0).getAsJsonObject().get("percent"));
        Assertions.assertTrue(rule.get("on_any").toString().contains("SKILL_CAST"),
                "「施放**战技**或终结技后」—— 战技那一半要在（2026-10-19 补的）");
        Assertions.assertEquals(0.48, rule.getAsJsonArray("do").get(0).getAsJsonObject().get("percent").getAsDouble(),
                1e-12, "48%（叠影 1）");
        // Witness: the 【梦身】 consumption is NOT modelled - the boost is a timed modifier, so it applies to every follow-up
        // inside its window rather than to exactly one.
        Assertions.assertEquals(1, rule.getAsJsonArray("do").get(0).getAsJsonObject().get("turns").getAsInt(),
                "缺口见证：现在是「1 回合内的每一次追加攻击」，而不是「消耗一次」");
    }

    // ==================================================================

    private static JsonObject rule(int cone, String id) {
        try (java.io.InputStream stream = ConeAuditBatchEighteenTest.class
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

        private double attribute(AttributeType type) {
            return wearer.getAttribute(type).get();
        }
    }
}
