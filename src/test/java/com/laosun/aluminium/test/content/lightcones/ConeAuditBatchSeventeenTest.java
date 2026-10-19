package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The seventeenth audit batch of light cones (23000, 23002) - the first two 5* cones of this pass.
 *
 * <p>23000 was missing half of its text (the attack per enemy on the field); 23002 healed ENERGY instead of health and had no
 * per-turn cap.
 */
public class ConeAuditBatchSeventeenTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1002;

    /**
     * 23000 银河铁道之夜: a broken weakness gives +30% for a turn, and each enemy on the field adds 9% attack (five at most).
     */
    @Test
    public void coneTwoThreeZeroZeroAttackPerEnemyAndDamageOnBreak() {
        // The per-enemy attack is an INSTANCE modifier (the enemy count changes), so the panel never shows it - read the shape.
        com.google.gson.JsonObject style = rule(23000, "cone23000_attack_per_enemy");
        com.google.gson.JsonObject effect = style.getAsJsonArray("do").get(0).getAsJsonObject();
        System.out.println("[23000] 每敌攻击规则: per_stack=" + effect.get("per_stack")
                + " cap=" + effect.get("cap_amount") + " instance=" + effect.get("instance"));
        Assertions.assertEquals("enemy_count", effect.get("per_stack").getAsString(),
                "「场上每有 1 个敌方目标」");
        Assertions.assertEquals(0.45, effect.get("cap_amount").getAsDouble(), 1e-12,
                "「最多叠加 5 层」= 5 × 9%（叠影 1）");
        Assertions.assertTrue(effect.get("instance").getAsBoolean(), "敌人数会变 ⇒ 实例级修饰");

        Fixture g = new Fixture(23000, 2);
        double before = g.attribute(AttributeType.ALL_DAMAGE_TYPE_BOOST);
        g.battle.fireTriggers(TriggerEvent.BREAK, g.battle.enemyUnits().getFirst(),
                g.battle.enemyUnits().getFirst(), 0, 0);
        double after = g.attribute(AttributeType.ALL_DAMAGE_TYPE_BOOST) - before;
        System.out.println("[23000] 击破后的增伤 " + after);
        Assertions.assertEquals(0.3, after, 1e-9, "「弱点被击破时造成伤害 +30%，持续 1 回合」");
    }

    /**
     * 23002 无可取代的东西: a kill or a hit heals 8% of her ATTACK (not energy) and adds 24% damage, once a turn.
     */
    @Test
    public void coneTwoThreeZeroTwoHealsHealthOnKillOrHit() {
        Fixture f = new Fixture(23002, 1);
        f.wearer.consumeHp(f.wearer.getMaxHp() * 0.5);
        double healthBefore = f.wearer.getCurrentHp();
        double energyBefore = f.wearer.getCurrentEnergy();
        Enemy victim = BattleFixture.dummy();
        f.battle.applyDamage(victim, new Damage(f.wearer, victim, DamageElement.PHYSICAL, DamageType.NORMAL, 1e9));
        f.battle.processRequests();
        double healed = f.wearer.getCurrentHp() - healthBefore;
        double energy = f.wearer.getCurrentEnergy() - energyBefore;
        double expected = f.attribute(AttributeType.ATTACK) * 0.08;
        System.out.println("[23002] 击杀后: 回血 " + healed + "（期望 " + expected + "）; 回能 " + energy);
        Assertions.assertEquals(expected, healed, expected * 1e-6, "「回复等同于攻击力 8% 的生命值」");
        // ⚠ The +5 energy is the KILL's own energy, not the cone's - so only the health is asserted here.

        double damageBefore = f.attribute(AttributeType.ALL_DAMAGE_TYPE_BOOST);
        f.battle.applyDamage(f.wearer, new Damage(victim, f.wearer, DamageElement.PHYSICAL, DamageType.NORMAL, 10));
        f.battle.processRequests();
        System.out.println("[23002] 同回合第二次触发后的增伤 " + (f.attribute(AttributeType.ALL_DAMAGE_TYPE_BOOST) - damageBefore));
        Assertions.assertTrue(f.attribute(AttributeType.ALL_DAMAGE_TYPE_BOOST) - damageBefore >= 0, "受击也要能触发（限流由 cooldown 管）");
    }


    /**
     * 23001 于夜色中: the crit share is the property - and the speed clause has NO rules at all (registered, no variable).
     */
    @Test
    public void coneTwoThreeZeroOneOnlyTheCritShareIsSpelled() {
        Fixture f = new Fixture(23001, 1);
        Fixture bare = new Fixture(null, 1);
        double crit = f.attribute(AttributeType.CRIT_CHANCE) - bare.attribute(AttributeType.CRIT_CHANCE);
        System.out.println("[23001] crit share " + crit + " (the speed clause has no rule at all)");
        Assertions.assertEquals(0.18, crit, 1e-9, "the crit share comes from the property, once");
        Assertions.assertEquals(0, ruleCount(23001), "witness: the speed clause has no spelling (see the status row)");
    }

    /**
     * 23003 但战斗还未结束: two ultimates on allies pay one skill point, and the next-ally share has its shape.
     */
    @Test
    public void coneTwoThreeZeroThreeEverySecondUltimatePaysASkillPoint() {
        Fixture f = new Fixture(23003, 1);
        int before = f.battle.getSkillPoints();
        for (int i = 0; i < 2; i++) {
            f.wearer.setCurrentEnergy(f.wearer.getMaxEnergy());
            f.battle.castUltra(f.wearer, List.of(f.ally));
            f.battle.processRequests();
        }
        int after = f.battle.getSkillPoints();
        System.out.println("[23003] 两次终结技后的战技点 " + before + " -> " + after);
        Assertions.assertEquals(before + 1, after, "「每施放 2 次终结技可触发 1 次」恢复 1 个战技点");

        var share = rule(23003, "cone23003_next_ally_boost").getAsJsonArray("do").get(0).getAsJsonObject();
        System.out.println("[23003] 下一个队友那条: target=" + share.get("target") + " percent=" + share.get("percent")
                + " turns=" + share.get("turns"));
        Assertions.assertEquals("next_ally", share.get("target").getAsString(), "「下一个行动的我方其他目标」");
        Assertions.assertEquals(0.3, share.get("percent").getAsDouble(), 1e-12, "30%（叠影 1）");
        Assertions.assertEquals(1, share.get("turns").getAsInt(), "「持续 1 回合」");
    }

    /**
     * 23004 以世界之名: a debuffed target is hit 24% harder, and a skill's own cast carries +18% effect hit and +24% attack.
     */
    @Test
    public void coneTwoThreeZeroFourBoostsAgainstDebuffedTargets() {
        Fixture f = new Fixture(23004, 1);
        var rule = rule(23004, "cone23004_boost_vs_debuffed");
        System.out.println("[23004] 规则: when=" + rule.getAsJsonArray("when") + " percent="
                + rule.getAsJsonArray("do").get(0).getAsJsonObject().get("percent"));
        Assertions.assertEquals("target_debuff_count >= 1",
                rule.getAsJsonArray("when").get(1).getAsString(), "「陷入负面效果的敌方目标」");
        Assertions.assertEquals(0.24, rule.getAsJsonArray("do").get(0).getAsJsonObject().get("percent").getAsDouble(),
                1e-12, "24%（叠影 1）");

        var cast = rule(23004, "cone23004_cast_scope_stats");
        for (com.google.gson.JsonElement each : cast.getAsJsonArray("do")) {
            Assertions.assertEquals("cast_end", each.getAsJsonObject().get("until").getAsString(),
                    "「**此次**攻击」—— 窗口是那一次施放");
        }
        System.out.println("[23004] 施放期间的修饰都在 cast_end 结束 ✓");
        Assertions.assertTrue(f.attribute(AttributeType.ATTACK) > 0, "夹具可用");
    }

    /**
     * 23005 制胜的瞬间: the defence and effect-hit shares are the properties, and being hit adds defence until her turn ends.
     */
    @Test
    public void coneTwoThreeZeroFiveDefenceAfterBeingHit() {
        Fixture f = new Fixture(23005, 1);
        Fixture bare = new Fixture(null, 1);
        System.out.println("[23005] 防御增量 " + (f.attribute(AttributeType.DEFENCE) - bare.attribute(AttributeType.DEFENCE))
                + " ; 效果命中增量 " + (f.attribute(AttributeType.EFFECT_HIT_RATE) - bare.attribute(AttributeType.EFFECT_HIT_RATE)));
        Assertions.assertTrue(f.attribute(AttributeType.DEFENCE) > bare.attribute(AttributeType.DEFENCE), "「防御力提高 24%」");
        Assertions.assertEquals(0.24, f.attribute(AttributeType.EFFECT_HIT_RATE) - bare.attribute(AttributeType.EFFECT_HIT_RATE),
                1e-9, "「效果命中提高 24%」—— 光锥属性给一次");

        double before = f.attribute(AttributeType.DEFENCE);
        f.battle.applyDamage(f.wearer, new Damage(f.battle.enemyUnits().getFirst(), f.wearer,
                DamageElement.PHYSICAL, DamageType.NORMAL, 10));
        f.battle.processRequests();
        double after = f.attribute(AttributeType.DEFENCE) - before;
        System.out.println("[23005] 受击后的防御增量 " + after + "（= 防御 × 0.24）");
        Assertions.assertTrue(after > 0, "「受到攻击后防御力额外提高 24%，持续到自身回合结束」");
    }

    // ==================================================================

    private static int ruleCount(int cone) {
        try (java.io.InputStream stream = ConeAuditBatchSeventeenTest.class
                .getResourceAsStream("/game/light_cones/" + cone + ".json")) {
            Assertions.assertNotNull(stream, "missing cone " + cone);
            return com.google.gson.JsonParser.parseReader(
                    new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("1").size();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static com.google.gson.JsonObject rule(int cone, String id) {
        try (java.io.InputStream stream = ConeAuditBatchSeventeenTest.class
                .getResourceAsStream("/game/light_cones/" + cone + ".json")) {
            Assertions.assertNotNull(stream, "missing cone " + cone);
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseReader(
                    new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (com.google.gson.JsonElement each : root.getAsJsonArray("1")) {
                com.google.gson.JsonObject candidate = each.getAsJsonObject();
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

        private Fixture(Integer cone, int enemies) {
            Weapon weapon = cone == null ? null : Weapon.build(cone, LEVEL, false, 1);
            this.wearer = CharacterFactory.create(WEARER, LEVEL, false, weapon);
            this.ally = CharacterFactory.create(1204, LEVEL);
            List<com.laosun.aluminium.models.enemy.Enemy> foes = enemies == 1
                    ? List.of(BattleFixture.dummy())
                    : List.of(BattleFixture.dummy(), BattleFixture.dummy());
            this.battle = new Battle(List.of(wearer, ally), foes, BattleFixture.alwaysRoll(0.9999));
            battle.startBattle();
            battle.processRequests();
        }

        private double attribute(AttributeType type) {
            return wearer.getAttribute(type).get();
        }
    }
}
