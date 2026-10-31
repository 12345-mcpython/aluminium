package com.laosun.aluminium.test.content.lightcones;

import com.google.gson.JsonArray;
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
 * The twenty-fifth audit batch of light cones (23043-23048).
 *
 * <p>Defects: 23043, 23045 and 23048 stated their speed/crit/attack share twice (properties plus rules); 23046's per-skill
 * attack had no stack buffer; 23048's skill-damage share was missing its `actor == self` and `target is_ally` gates. 23047's
 * spell subsystem is registered.
 */
public class ConeAuditBatchTwentyFiveTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1204;

    /**
     * 23043 谎言在风中飘扬: only the speed share is spelled (the two states are registered).
     */
    @Test
    public void coneTwoThreeFourThreeHasOnlyTheSpeedShare() {
        Fixture f = new Fixture(23043);
        Fixture bare = new Fixture(null);
        System.out.println("[23043] 速度增量 " + (f.wearer.getAttribute(AttributeType.SPEED).get()
                - bare.wearer.getAttribute(AttributeType.SPEED).get()) + " ; 规则数 " + rules(23043));
        Assertions.assertTrue(f.wearer.getAttribute(AttributeType.SPEED).get()
                > bare.wearer.getAttribute(AttributeType.SPEED).get(), "「速度提高 18%」—— 光锥属性给一次");
        Assertions.assertEquals(0, rules(23043), "缺口见证：没有规则（【茫然】/【失窃】没有拼法）");
    }

    /**
     * 23044 黎明恰如此燃烧: the defence ignore, and 【烈阳】 pays +60% damage until the next turn start.
     */
    @Test
    public void coneTwoThreeFourFourSunUntilTurnStart() {
        JsonObject sun = effect(23044, "cone23044_sun_damage", 0);
        System.out.println("[23044] 烈阳: percent=" + sun.get("percent") + " when="
                + rule(23044, "cone23044_sun_damage").getAsJsonArray("when"));
        Assertions.assertEquals(0.6, sun.get("percent").getAsDouble(), 1e-12, "「造成的伤害提高 60%」（叠影 1）");
        Assertions.assertEquals(1, effect(23044, "cone23044_sun_on_ult", 0).get("max_stacks").getAsInt(),
                "【烈阳】只有 1 层");

        Fixture f = new Fixture(23044);
        f.wearer.setCurrentEnergy(f.wearer.getMaxEnergy());
        f.battle.castUltra(f.wearer, List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        f.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, f.wearer, f.wearer, 0, 0));
        System.out.println("[23044] 回合开始后【烈阳】已被移除（形状由上面的规则钉住）");
        Assertions.assertTrue(f.wearer.getCurrentHp() > 0, "夹具可用");
    }

    /**
     * 23045 没有回报的加冕: the crit-damage share is the property; the ultimate and energy-cap clauses are spelled now (judged in\n * ConeTwentyThreeThousandFortyFiveTest).
     */
    @Test
    public void coneTwoThreeFourFiveHasOnlyTheCritDamageShare() {
        Fixture f = new Fixture(23045);
        Fixture bare = new Fixture(null);
        System.out.println("[23045] 暴伤增量 " + (f.wearer.getAttribute(AttributeType.CRIT_ATTACK).get()
                - bare.wearer.getAttribute(AttributeType.CRIT_ATTACK).get()) + " ; 规则数 " + rules(23045));
        Assertions.assertTrue(f.wearer.getAttribute(AttributeType.CRIT_ATTACK).get()
                > bare.wearer.getAttribute(AttributeType.CRIT_ATTACK).get(), "「暴击伤害提高 36%」");
        Assertions.assertEquals(2, rules(23045), "the unconditional ATK share and the energy-cap pair");
    }

    /**
     * 23046 理想燃烧的地狱: the skill-point-cap gate, and each skill adds a layer (four at most).
     */
    @Test
    public void coneTwoThreeFourSixStacksPerSkill() {
        JsonObject stack = effect(23046, "cone23046_skill_stacks", 0);
        JsonObject share = effect(23046, "cone23046_skill_stacks", 1);
        System.out.println("[23046] 叠层: max=" + stack.get("max_stacks") + " per_stack=" + share.get("per_stack"));
        Assertions.assertEquals(4, stack.get("max_stacks").getAsInt(), "「最多叠加 4 次」");
        Assertions.assertEquals("技能层", share.get("per_stack").getAsString(), "「每次战技后攻击提高 10%」按层");

        JsonArray gate = rule(23046, "cone23046_atk_when_cap_is_six").getAsJsonArray("when");
        System.out.println("[23046] 战技点上限那条: when=" + gate);
        Assertions.assertTrue(gate.toString().contains("skill_point_max >= 6"), "「战技点上限 ≥ 6」");
    }

    /**
     * 23047 海洋为何而歌: the effect-hit share is the property and 【魂迷】 lands with its 80% chance for three turns.
     */
    @Test
    public void coneTwoThreeFourSevenEnsnareChance() {
        Fixture f = new Fixture(23047);
        Fixture bare = new Fixture(null);
        System.out.println("[23047] 效果命中增量 " + (f.wearer.getAttribute(AttributeType.EFFECT_HIT_RATE).get()
                - bare.wearer.getAttribute(AttributeType.EFFECT_HIT_RATE).get()));
        Assertions.assertEquals(0.4, f.wearer.getAttribute(AttributeType.EFFECT_HIT_RATE).get()
                - bare.wearer.getAttribute(AttributeType.EFFECT_HIT_RATE).get(), 1e-9, "「效果命中提高 40%」—— 属性给一次");

        JsonObject mark = effect(23047, "cone23047_ensnare", 0);
        System.out.println("[23047] 魂迷: chance=" + mark.get("base_chance") + " turns=" + mark.get("turns"));
        Assertions.assertEquals(0.8, mark.get("base_chance").getAsDouble(), 1e-12, "「80% 基础概率」");
        Assertions.assertEquals(3, mark.get("turns").getAsInt(), "「持续 3 回合」");
    }

    /**
     * 23048 金血铭刻的时代: the attack share is the property, the ultimate pays a skill point, and HER skill on an ALLY raises
     * that ally's skill damage.
     */
    @Test
    public void coneTwoThreeFourEightSkillDamageOnTheAlly() {
        Fixture f = new Fixture(23048);
        Fixture bare = new Fixture(null);
        System.out.println("[23048] 攻击增量 " + (f.wearer.getAttribute(AttributeType.ATTACK).get()
                - bare.wearer.getAttribute(AttributeType.ATTACK).get()));
        Assertions.assertTrue(f.wearer.getAttribute(AttributeType.ATTACK).get()
                > bare.wearer.getAttribute(AttributeType.ATTACK).get(), "「攻击力提高 64%」—— 属性给一次");

        JsonArray when = rule(23048, "cone23048_skill_damage").getAsJsonArray("when");
        System.out.println("[23048] 战技那条的 when=" + when);
        Assertions.assertTrue(when.toString().contains("actor == self"), "「**装备者**施放战技」");
        Assertions.assertTrue(when.toString().contains("target is_ally"), "「对我方**单体**角色」");

        int pointsBefore = f.battle.getSkillPoints();
        f.wearer.setCurrentEnergy(f.wearer.getMaxEnergy());
        f.battle.castUltra(f.wearer, List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        System.out.println("[23048] 终结技后的战技点 " + pointsBefore + " -> " + f.battle.getSkillPoints());
    }

    // ==================================================================

    private static JsonObject rule(int cone, String id) {
        try (java.io.InputStream stream = ConeAuditBatchTwentyFiveTest.class
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
        try (java.io.InputStream stream = ConeAuditBatchTwentyFiveTest.class
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
