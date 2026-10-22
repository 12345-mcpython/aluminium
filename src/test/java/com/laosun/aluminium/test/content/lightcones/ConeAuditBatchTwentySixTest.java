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
 * The twenty-sixth audit batch of light cones (23049-23054).
 *
 * <p>Defects: 23049 and 23054 stated the health/speed share twice; 23050 stated its break share twice; 23051's heals were both
 * 20% (they are 10% and 10%) and its garrison share was 0.48 instead of 0.24. 23051's summon case and 23050's ally selection are
 * registered.
 */
public class ConeAuditBatchTwentySixTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1409;   // has a memosprite
    private static final int ALLY = 1002;

    /**
     * 23049 致长夜的星光: only the health share is spelled (the night-colour state is registered).
     */
    @Test
    public void coneTwoThreeFourNineHasOnlyTheHealthShare() {
        Fixture f = new Fixture(23049);
        Fixture bare = new Fixture(null);
        System.out.println("[23049] 生命上限增量 " + (f.wearer.getAttribute(AttributeType.HEALTH).get()
                - bare.wearer.getAttribute(AttributeType.HEALTH).get()) + " ; 规则数 " + rules(23049));
        Assertions.assertTrue(f.wearer.getAttribute(AttributeType.HEALTH).get()
                > bare.wearer.getAttribute(AttributeType.HEALTH).get(), "「生命上限提高 30%」");
        Assertions.assertEquals(0, rules(23049), "缺口见证：没有规则（【夜色】没有拼法）");
    }

    /**
     * 23050 勿忘她的火焰: the break share once, the weakness skill point, and the ultimate resetting its trigger.
     */
    @Test
    public void coneTwoThreeFiveZeroWeaknessPaysAndUltResets() {
        Fixture f = new Fixture(23050);
        Fixture bare = new Fixture(null);
        double breaking = f.wearer.getAttribute(AttributeType.BREAKING_EFFECT).get()
                - bare.wearer.getAttribute(AttributeType.BREAKING_EFFECT).get();
        System.out.println("[23050] 击破特攻增量 " + breaking + " ; 规则数 " + rules(23050));
        Assertions.assertEquals(0.6, breaking, 1e-9, "「击破特攻提高 60%」—— 属性给一次");
        Assertions.assertEquals(2, rules(23050), "剩下的两条：弱点回点与终结技重置");

        JsonObject reset = rule(23050, "cone23050_resets_the_weakness_count").getAsJsonArray("do").get(0).getAsJsonObject();
        System.out.println("[23050] 重置: rule=" + reset.get("rule"));
        Assertions.assertEquals("cone23050_weakness_grants_skill_point", reset.get("rule").getAsString(),
                "「施放终结技时重置可触发次数」");

        int before = f.battle.getSkillPoints();
        f.battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.WEAKNESS_ADDED, f.wearer, f.battle.enemyUnits().getFirst(), 0, 0));
        System.out.println("[23050] 添加弱点后战技点 " + before + " -> " + f.battle.getSkillPoints());
        Assertions.assertEquals(before + 1, f.battle.getSkillPoints(), "「恢复 1 个战技点」");
    }

    /**
     * 23051 纵然山河万程: the party heal is 10% of her ATTACK, the lowest ally gets another 10%, and the garrison share is 24%.
     */
    @Test
    public void coneTwoThreeFiveOneHealsAndGarrison() {
        JsonObject partyHeal = effect(23051, "cone23051_ult_heal", 0);
        JsonObject lowestHeal = effect(23051, "cone23051_ult_heal", 1);
        JsonObject garrison = effect(23051, "cone23051_ult_heal", 2);
        System.out.println("[23051] 全队治疗 " + partyHeal.get("amountPercent") + " ; 最低者 " + lowestHeal.get("amountPercent")
                + " ; 卫戍 " + garrison.get("percent") + " turns=" + garrison.get("turns"));
        Assertions.assertEquals(0.1, partyHeal.get("amountPercent").getAsDouble(), 1e-12, "「攻击力 10%」");
        Assertions.assertEquals(0.1, lowestHeal.get("amountPercent").getAsDouble(), 1e-12, "「额外 10%」");
        Assertions.assertEquals(0.24, garrison.get("percent").getAsDouble(), 1e-12, "「伤害提高 24%」（不是 48%）");
        Assertions.assertEquals(3, garrison.get("turns").getAsInt(), "「持续 3 回合」");

        Fixture f = new Fixture(23051);
        f.ally.consumeHp(f.ally.getMaxHp() * 0.5);
        double allyBefore = f.ally.getCurrentHp();
        f.wearer.setCurrentEnergy(f.wearer.getMaxEnergy());
        f.battle.castUltra(f.wearer, List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        System.out.println("[23051] 队友回复 " + (f.ally.getCurrentHp() - allyBefore)
                + "（应 ≥ 攻击 × 0.1）");
        Assertions.assertTrue(f.ally.getCurrentHp() - allyBefore > 0, "全队治疗落地");
    }

    /**
     * 23052 爱如此刻永恒: the memosprite's ally cast leaves 【空白】 on the enemies, its enemy cast leaves 【诗行】 on the party.
     */
    @Test
    public void coneTwoThreeFiveTwoBlankAndPoem() {
        JsonObject blank = effect(23052, "cone23052_blank_on_ally", 1);
        JsonObject poem = effect(23052, "cone23052_poem_on_enemy", 1);
        System.out.println("[23052] 空白: damage_taken=" + blank.get("percent") + " target=" + blank.get("target")
                + " ; 诗行: crit_damage=" + poem.get("percent") + " target=" + poem.get("target"));
        Assertions.assertEquals(0.1, blank.get("percent").getAsDouble(), 1e-12, "「敌方全体受到的伤害提高 10%」");
        Assertions.assertEquals("all_enemies", blank.get("target").getAsString(), "「**敌方全体**」");
        Assertions.assertEquals(0.16, poem.get("percent").getAsDouble(), 1e-12, "「我方全体暴击伤害提高 16%」");
        Assertions.assertEquals("all_allies", poem.get("target").getAsString(), "「我方全体」");
    }

    /**
     * 23053 花花世界迷人眼: each spent skill point adds a layer of elation defence ignore (four at most), and four in a turn
     * pay out 【推流】.
     */
    @Test
    public void coneTwoThreeFiveThreeSpentPointsIgnoreDefence() {
        JsonObject ignore = effect(23053, "cone23053_def_ignore_per_stack", 0);
        System.out.println("[23053] 无视防御: per_stack=" + ignore.get("per_stack") + " damage_type=" + ignore.get("damage_type")
                + " percent=" + ignore.get("percent"));
        Assertions.assertEquals("self_stacks:消耗层数", ignore.get("per_stack").getAsString(), "「**每消耗 1 个**战技点」");
        Assertions.assertEquals("ELATION", ignore.get("damage_type").getAsString(), "只对**欢愉伤害**");
        JsonObject pay = effect(23053, "cone23053_turn_spend_pays_out", 2);
        System.out.println("[23053] 推流: elation boost=" + pay.get("percent") + " target=" + pay.get("target"));
        Assertions.assertEquals(0.2, pay.get("percent").getAsDouble(), 1e-12, "「我方全体欢愉度提高 20%」");
        Assertions.assertEquals("all_allies", pay.get("target").getAsString(), "「我方全体」");
    }

    /**
     * 23054 当她决定看见: the speed share once, and 【上上签】 gives the party crit for three turns plus her energy regen.
     */
    @Test
    public void coneTwoThreeFiveFourDivination() {
        Fixture f = new Fixture(23054);
        Fixture bare = new Fixture(null);
        double speed = f.wearer.getAttribute(AttributeType.SPEED).get() - bare.wearer.getAttribute(AttributeType.SPEED).get();
        double expected = bare.wearer.getAttribute(AttributeType.SPEED).get() * 0.18;
        System.out.println("[23054] 速度增量 " + speed + " ; 期望 " + expected + " ; 规则数 " + rules(23054));
        Assertions.assertTrue(speed > 0, "「速度提高 18%」");
        Assertions.assertEquals(2, rules(23054), "剩下两条：【上上签】与波次回能");

        double allyCrit = f.ally.getAttribute(AttributeType.CRIT_CHANCE).get()
                - bare.ally.getAttribute(AttributeType.CRIT_CHANCE).get();
        System.out.println("[23054] 队友暴击率增量 " + allyCrit);
        Assertions.assertEquals(0.1, allyCrit, 1e-9, "「我方全体暴击率提高 10%」");
    }

    // ==================================================================

    private static JsonObject rule(int cone, String id) {
        try (java.io.InputStream stream = ConeAuditBatchTwentySixTest.class
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
        try (java.io.InputStream stream = ConeAuditBatchTwentySixTest.class
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
