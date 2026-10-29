package com.laosun.aluminium.test.content.lightcones;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The twenty-fourth audit batch of light cones (23037-23041).
 *
 * <p>One defect: 23038's follow-up rule had no `actor == self`, so ANY ally's follow-up paid the wearer energy and gave the
 * party 【谕示】. 23039 was pure duplication of its two properties and its whole payment clause is registered; 23037's
 * energy-spent skill point and 23040/23041's remaining halves are registered too.
 */
public class ConeAuditBatchTwentyFourTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1204;

    /**
     * 23037 向着不可追问处: an ultimate raises both channels for three turns.
     */
    @Test
    public void coneTwoThreeZeroSevenSkillAndUltForThreeTurns() {
        Fixture f = new Fixture(23037);
        double skillBefore = f.wearer.getAttribute(AttributeType.SKILL_DAMAGE_BOOST).get();
        double ultBefore = f.wearer.getAttribute(AttributeType.ULTIMATE_DAMAGE_BOOST).get();
        f.wearer.setCurrentEnergy(f.wearer.getMaxEnergy());
        f.battle.castUltra(f.wearer, List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        System.out.println("[23037] 战技档 +" + (f.wearer.getAttribute(AttributeType.SKILL_DAMAGE_BOOST).get() - skillBefore)
                + " ; 终结技档 +" + (f.wearer.getAttribute(AttributeType.ULTIMATE_DAMAGE_BOOST).get() - ultBefore));
        Assertions.assertEquals(0.6, f.wearer.getAttribute(AttributeType.SKILL_DAMAGE_BOOST).get() - skillBefore, 1e-9,
                "「战技伤害提高 60%」（叠影 1）");
        Assertions.assertEquals(0.6, f.wearer.getAttribute(AttributeType.ULTIMATE_DAMAGE_BOOST).get() - ultBefore, 1e-9,
                "「终结技伤害提高 60%」");
        Assertions.assertEquals(3, effect(23037, "cone23037_skill_and_ult_damage_up", 0).get("turns").getAsInt(),
                "「持续 3 回合」");
    }

    /**
     * 23038 如果时间是一朵花: HER follow-up pays energy, a teammate's does not.
     */
    @Test
    public void coneTwoThreeZeroEightOnlyHerFollowUpPays() {
        Fixture f = new Fixture(23038);
        f.wearer.setCurrentEnergy(0);
        f.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.FOLLOW_UP, f.ally, f.battle.enemyUnits().getFirst(), 0, 0));
        System.out.println("[23038] 队友追加攻击后她的能量 " + f.wearer.getCurrentEnergy() + "（应为 0）");
        Assertions.assertEquals(0.0, f.wearer.getCurrentEnergy(), 1e-9, "「**装备者**施放追加攻击后」—— 队友的不算");

        f.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.FOLLOW_UP, f.wearer, f.battle.enemyUnits().getFirst(), 0, 0));
        System.out.println("[23038] 她自己追加攻击后 " + f.wearer.getCurrentEnergy());
        Assertions.assertEquals(12.0, f.wearer.getCurrentEnergy(), 1e-9, "「额外恢复 12 点能量」");
        double allyCrit = f.ally.getAttribute(AttributeType.CRIT_ATTACK).get()
                - new Fixture(null).ally.getAttribute(AttributeType.CRIT_ATTACK).get();
        System.out.println("[23038] 队友的暴伤增量 " + allyCrit);
        Assertions.assertEquals(0.48, allyCrit, 1e-9, "「我方全体暴击伤害提高 48%」（开战也有【谕示】）");
    }

    /**
     * 23039 血火啊: the health and healing shares are the properties; the payment clause is registered.
     */
    @Test
    public void coneTwoThreeZeroNineHasOnlyItsTwoProperties() {
        Fixture f = new Fixture(23039);
        Fixture bare = new Fixture(null);
        Assertions.assertTrue(f.wearer.getAttribute(AttributeType.HEALTH).get()
                > bare.wearer.getAttribute(AttributeType.HEALTH).get(), "「生命上限提高 18%」");
        Assertions.assertTrue(f.wearer.getAttribute(AttributeType.HEAL_TAKEN_RATIO).get()
                > bare.wearer.getAttribute(AttributeType.HEAL_TAKEN_RATIO).get(), "「受到的治疗量提高 20%」");
        System.out.println("[23039] 规则数 " + rules(23039) + "（支付生命那一句没有拼法）");
        Assertions.assertEquals(0, rules(23039), "缺口见证：没有规则（见状态表）");
    }

    /**
     * 23040 让告别，更美一些: losing health (hers or the memosprite's) gives the defence ignore for two turns.
     */
    @Test
    public void coneTwoThreeFourZeroFlowerFromHpLoss() {
        Fixture f = new Fixture(23040);
        double before = f.wearer.getAttribute(AttributeType.DEFENCE_IGNORE).get();
        f.battle.applyDamage(f.wearer, new Damage(f.battle.enemyUnits().getFirst(), f.wearer,
                DamageElement.PHYSICAL, DamageType.NORMAL, 10));
        f.battle.processRequests();
        System.out.println("[23040] 损失生命后的无视防御增量 " + (f.wearer.getAttribute(AttributeType.DEFENCE_IGNORE).get() - before));
        Assertions.assertEquals(0.3, f.wearer.getAttribute(AttributeType.DEFENCE_IGNORE).get() - before, 1e-9,
                "「无视 30% 防御力」");

        JsonArray when = rule(23040, "cone23040_flower_from_summon").getAsJsonArray("when");
        Assertions.assertTrue(when.toString().contains("summon"), "「装备者**或忆灵**损失生命值时」");
        Assertions.assertEquals(2, effect(23040, "cone23040_flower_from_summon", 0).get("turns").getAsInt(), "「持续 2 回合」");
    }

    /**
     * 23041 生命当付之一炬: the turn's energy and the defence-down on her target.
     */
    @Test
    public void coneTwoThreeFourOneTurnEnergyAndDefenceDown() {
        Fixture f = new Fixture(23041);
        f.wearer.setCurrentEnergy(0);
        f.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, f.wearer, f.wearer, 0, 0));
        System.out.println("[23041] 回合开始能量 " + f.wearer.getCurrentEnergy());
        Assertions.assertTrue(f.wearer.getCurrentEnergy() >= 10, "「回合开始时恢复 10 点能量」");

        JsonObject down = effect(23041, "cone23041_defence_down", 0);
        System.out.println("[23041] 降防: percent=" + down.get("percent") + " turns=" + down.get("turns")
                + " target=" + down.get("target"));
        Assertions.assertEquals(-0.24, down.get("percent").getAsDouble(), 1e-12, "「防御力降低 24%」（叠影 1）");
        Assertions.assertEquals(2, down.get("turns").getAsInt(), "「持续 2 回合」");
        Assertions.assertEquals("target", down.get("target").getAsString(), "落在被打的目标身上");
    }

    // ==================================================================

    private static JsonObject rule(int cone, String id) {
        try (java.io.InputStream stream = ConeAuditBatchTwentyFourTest.class
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
        try (java.io.InputStream stream = ConeAuditBatchTwentyFourTest.class
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
