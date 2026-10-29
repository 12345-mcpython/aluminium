package com.laosun.aluminium.test.content.lightcones;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The nineteenth audit batch of light cones (23011-23015).
 *
 * <p>Two defects, both "the stat share stated twice": 23012's crit damage and 23013's health plus healing were the cones' own
 * properties and were also written as rules. Their remaining clauses have no spelling (a crit that did NOT happen, and a stored
 * healing amount), so both files now carry no rules and the clauses are registered.
 */
public class ConeAuditBatchNineteenTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1204;

    /**
     * 23011 她已闭上双眼: the two stat shares are the properties; losing health boosts the WHOLE party for two turns, and every
     * wave heals each unit's own lost health.
     */
    @Test
    public void coneTwoThreeOneOnePartyBoostOnHurtAndWaveHeal() {
        Fixture f = new Fixture(23011);
        Fixture bare = new Fixture(null);
        System.out.println("[23011] 生命上限增量 " + (f.attribute(AttributeType.HEALTH) - bare.attribute(AttributeType.HEALTH))
                + " ; 能量恢复增量 " + (f.attribute(AttributeType.ENERGY_REGENERATION_RATE)
                - bare.attribute(AttributeType.ENERGY_REGENERATION_RATE)));
        Assertions.assertTrue(f.attribute(AttributeType.HEALTH) > bare.attribute(AttributeType.HEALTH), "「生命上限提高 24%」");
        Assertions.assertTrue(f.attribute(AttributeType.ENERGY_REGENERATION_RATE)
                > bare.attribute(AttributeType.ENERGY_REGENERATION_RATE), "「能量恢复效率提高 12%」");

        double allyBefore = f.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        f.battle.applyDamage(f.wearer, new Damage(f.battle.enemyUnits().getFirst(), f.wearer,
                DamageElement.PHYSICAL, DamageType.NORMAL, 10));
        f.battle.processRequests();
        double allyAfter = f.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - allyBefore;
        System.out.println("[23011] 她掉血后队友的增伤 " + allyAfter);
        Assertions.assertEquals(0.09, allyAfter, 1e-9, "「当装备者的生命值降低时，使我方**全体**造成的伤害提高 9%」");

        JsonObject heal = rule(23011, "cone23011_heal_lost_hp_per_wave").getAsJsonArray("do").get(0).getAsJsonObject();
        System.out.println("[23011] 波次治疗: scale=" + heal.get("scale") + " percent=" + heal.get("percent"));
        Assertions.assertEquals("target_lost_hp", heal.get("scale").getAsString(), "「等同于**各自**已损失生命值」");
        Assertions.assertEquals(0.8, heal.get("percent").getAsDouble(), 1e-12, "80%（叠影 1）");
    }

    /**
     * 23012 如泥酣眠: the crit-damage share is the property, and the file carries no rules (registered gap).
     */
    @Test
    public void coneTwoThreeOneTwoHasOnlyTheCritDamageShare() {
        Fixture f = new Fixture(23012);
        Fixture bare = new Fixture(null);
        double critDamage = f.attribute(AttributeType.CRIT_ATTACK) - bare.attribute(AttributeType.CRIT_ATTACK);
        System.out.println("[23012] 暴伤增量 " + critDamage + " ; 规则数 " + rules(23012));
        Assertions.assertTrue(critDamage > 0, "「暴击伤害提高 30%」—— 光锥属性给一次");
        Assertions.assertEquals(0, rules(23012), "缺口见证：没有规则（未暴击那一句没有拼法，见状态表）");
    }

    /**
     * 23013 时节不居: both stat shares are the properties, and the recorded-healing clause has no rules.
     */
    @Test
    public void coneTwoThreeOneThreeHasOnlyItsTwoStatShares() {
        Fixture f = new Fixture(23013);
        Fixture bare = new Fixture(null);
        Assertions.assertTrue(f.attribute(AttributeType.HEALTH) > bare.attribute(AttributeType.HEALTH), "「生命上限提高 18%」");
        Assertions.assertTrue(f.attribute(AttributeType.OUTGOING_HEALING_BOOST)
                > bare.attribute(AttributeType.OUTGOING_HEALING_BOOST), "「治疗量提高 12%」");
        System.out.println("[23013] 规则数 " + rules(23013) + "（记录治疗量那一句没有拼法）");
        Assertions.assertEquals(0, rules(23013), "缺口见证：没有规则（见状态表）");
    }

    /**
     * 23014 此身为剑: a teammate hit stacks 【月蚀】, and each layer sharpens her NEXT attack (three layers also ignore defence).
     */
    @Test
    public void coneTwoThreeOneFourEclipseStacksOnTeammateHurt() {
        Fixture f = new Fixture(23014);
        var stack = rule(23014, "cone23014_eclipse_on_teammate_hit").getAsJsonArray("do").get(0).getAsJsonObject();
        System.out.println("[23014] 叠层: buff=" + stack.get("buff") + " max=" + stack.get("max_stacks"));
        Assertions.assertEquals(3, stack.get("max_stacks").getAsInt(), "「最多叠加 3 层」");

        var ignore = rule(23014, "cone23014_eclipse_full_ignore");
        System.out.println("[23014] 满层无视防御: when=" + ignore.getAsJsonArray("when") + " percent="
                + ignore.getAsJsonArray("do").get(0).getAsJsonObject().get("percent"));
        Assertions.assertTrue(ignore.getAsJsonArray("when").toString().contains(">= 3"), "「叠满 3 层时」");
        Assertions.assertEquals(0.12, ignore.getAsJsonArray("do").get(0).getAsJsonObject().get("percent").getAsDouble(),
                1e-12, "12%（叠影 1）");

        // The team hurt really adds a layer, and her own attack spends them.
        f.battle.applyDamage(f.ally, new Damage(f.battle.enemyUnits().getFirst(), f.ally,
                DamageElement.PHYSICAL, DamageType.NORMAL, 10));
        f.battle.processRequests();
        f.battle.castImmediate(f.wearer.getSkills().get(SkillType.COMMON), f.wearer,
                List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        System.out.println("[23014] 队友受击 + 她出手一轮完成（层数在出手后清除）");
        Assertions.assertTrue(f.wearer.getCurrentHp() > 0, "夹具可用");
    }

    /**
     * 23015 比阳光更明亮的: each basic attack stacks 【龙吟】 (two at most, two turns), and both shares are per layer.
     */
    @Test
    public void coneTwoThreeOneFiveDragonStacksAttackAndEnergyRegen() {
        Fixture f = new Fixture(23015);
        double startAttack = f.attribute(AttributeType.ATTACK);
        for (int i = 0; i < 3; i++) {
            f.battle.castImmediate(f.wearer.getSkills().get(SkillType.COMMON), f.wearer,
                    List.of(f.battle.enemyUnits().getFirst()));
            f.battle.processRequests();
        }
        var stack = rule(23015, "cone23015_dragon_stack").getAsJsonArray("do").get(0).getAsJsonObject();
        System.out.println("[23015] 叠层: max=" + stack.get("max_stacks") + " turns=" + stack.get("turns")
                + " ; 三次普攻后的攻击增量 " + (f.attribute(AttributeType.ATTACK) - startAttack));
        Assertions.assertEquals(2, stack.get("max_stacks").getAsInt(), "「最多叠加 2 层」");
        Assertions.assertEquals(2, stack.get("turns").getAsInt(), "「持续 2 回合」");
        for (com.google.gson.JsonElement each : rule(23015, "cone23015_dragon_stack").getAsJsonArray("do")) {
            if ("MODIFY_ATTR".equals(each.getAsJsonObject().get("op").getAsString())) {
                Assertions.assertEquals("龙吟", each.getAsJsonObject().get("per_stack").getAsString(),
                        "「**每层**使攻击力/能量恢复提高」");
            }
        }
        Assertions.assertTrue(f.attribute(AttributeType.ATTACK) > startAttack, "攻击确实涨了");
    }

    // ==================================================================

    private static JsonObject rule(int cone, String id) {
        try (java.io.InputStream stream = ConeAuditBatchNineteenTest.class
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
        try (java.io.InputStream stream = ConeAuditBatchNineteenTest.class
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
