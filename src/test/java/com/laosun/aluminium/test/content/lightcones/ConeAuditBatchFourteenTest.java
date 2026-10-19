package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The fourteenth audit batch of light cones (21056-21062), each clause read as a BATTLE.
 *
 * <p>One defect: 21061 stated its damage share twice (the property plus a rule worth 0.32, i.e. +48% in total) and gave the
 * vulnerability the wrong share for four of the five ranks, with no base chance.
 */
public class ConeAuditBatchFourteenTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1204;
    private static final int HYACINE = 1409;

    /**
     * 21056 追逐风的时候: the party's break damage rises 16%.
     */
    @Test
    public void coneTwoInnocentFiveSixRaisesThePartysBreakDamage() {
        Fixture f = new Fixture(21056, true, WEARER);
        Fixture bare = new Fixture(null, true, WEARER);
        for (int i = 0; i < 2; i++) {
            double delta = f.battle.characters.get(i).getAttribute(AttributeType.BREAK_DAMAGE_BOOST).get()
                    - bare.battle.characters.get(i).getAttribute(AttributeType.BREAK_DAMAGE_BOOST).get();
            System.out.println("[21056] " + f.battle.characters.get(i).getName() + " 击破伤害增量 " + delta);
            Assertions.assertEquals(0.16, delta, 1e-9, "「我方全体造成的击破伤害提高 16%」");
        }
    }

    /**
     * 21057 花儿不会忘记: the crit-damage share once, and the memosprite's crits get it as an instance.
     */
    @Test
    public void coneTwoInnocentFiveSevenSummonCritDamage() {
        Fixture f = new Fixture(21057, false, HYACINE);
        Fixture bare = new Fixture(null, false, HYACINE);
        double crit = f.wearer.getAttribute(AttributeType.CRIT_ATTACK).get()
                - bare.wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
        System.out.println("[21057] 她的暴伤增量 " + crit);
        Assertions.assertEquals(0.24, crit, 1e-9, "「暴击伤害提高 24%」—— 光锥属性给一次");

        Summon sprite = f.battle.summonServant(f.wearer);
        // The memosprite's own crit-damage share is an INSTANCE modifier: it rides on its damage, not on the panel.
        Rule rule = rule(21057);
        System.out.println("[21057] 忆灵那条规则: actor=" + rule.actor + " instance=" + rule.instance
                + " percent=" + rule.percent);
        Assertions.assertEquals("summon", rule.actor, "「装备者**忆灵**造成的暴击伤害额外提高」");
        Assertions.assertTrue(rule.instance, "它必须是实例修饰（只作用在忆灵那一发上）");
        Assertions.assertEquals(0.24, rule.percent, 1e-12, "额外 +24%（叠影 1）");
        Assertions.assertNotNull(sprite, "夹具里忆灵在场上");
    }

    /**
     * 21058 一行往日的血: the skill and ultimate channels each get 24%.
     */
    @Test
    public void coneTwoInnocentFiveEightSkillAndUltimateChannels() {
        Fixture f = new Fixture(21058, false, WEARER);
        Fixture bare = new Fixture(null, false, WEARER);
        double skill = f.attribute(AttributeType.SKILL_DAMAGE_BOOST) - bare.attribute(AttributeType.SKILL_DAMAGE_BOOST);
        double ultra = f.attribute(AttributeType.ULTIMATE_DAMAGE_BOOST) - bare.attribute(AttributeType.ULTIMATE_DAMAGE_BOOST);
        double basic = f.attribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST) - bare.attribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST);
        System.out.println("[21058] 战技 " + skill + " ; 终结技 " + ultra + " ; 普攻 " + basic);
        Assertions.assertEquals(0.24, skill, 1e-9, "「战技…伤害提高 24%」");
        Assertions.assertEquals(0.24, ultra, 1e-9, "「…和终结技…」");
        Assertions.assertEquals(0.0, basic, 1e-9, "普攻不在这一句里");
    }

    /**
     * 21061 假日浴场大冒险: the damage share is the property only, and a hit leaves 10% vulnerability on the target for two turns.
     */
    @Test
    public void coneTwoInnocentSixOneVulnerabilityTenPercent() {
        Fixture f = new Fixture(21061, false, WEARER);
        Fixture bare = new Fixture(null, false, WEARER);
        double boost = f.attribute(AttributeType.ALL_DAMAGE_TYPE_BOOST) - bare.attribute(AttributeType.ALL_DAMAGE_TYPE_BOOST);
        System.out.println("[21061] 增伤增量 " + boost);
        Assertions.assertEquals(0.16, boost, 1e-9, "「造成的伤害提高 16%」—— 光锥属性给一次（重复那条已删）");

        Enemy enemy = f.battle.enemyUnits().getFirst();
        f.battle.castImmediate(f.wearer.getSkills().get(SkillType.COMMON), f.wearer, List.of(enemy));
        f.battle.processRequests();
        List<Damage> settled = new ArrayList<>();
        f.battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        f.battle.castImmediate(f.wearer.getSkills().get(SkillType.COMMON), f.wearer, List.of(enemy));
        f.battle.processRequests();
        double vulnerable = settled.stream().mapToDouble(d -> d.breakdown().getOrDefault("VulnerableArea", 1.0)).findFirst()
                .orElseThrow();
        System.out.println("[21061] 打中之后敌方易伤区 ×" + vulnerable);
        Assertions.assertEquals(1.10, vulnerable, 1e-9, "「受到的伤害提高 10%，持续 2 回合」（叠影 1）");
    }

    /**
     * 21062 于那终点再见: the skill and follow-up channels each get 24%.
     */
    @Test
    public void coneTwoInnocentSixTwoSkillAndFollowUpChannels() {
        Fixture f = new Fixture(21062, false, WEARER);
        Fixture bare = new Fixture(null, false, WEARER);
        double skill = f.attribute(AttributeType.SKILL_DAMAGE_BOOST) - bare.attribute(AttributeType.SKILL_DAMAGE_BOOST);
        double followUp = f.attribute(AttributeType.FOLLOW_UP_DAMAGE_BOOST) - bare.attribute(AttributeType.FOLLOW_UP_DAMAGE_BOOST);
        System.out.println("[21062] 战技 " + skill + " ; 追加攻击 " + followUp);
        Assertions.assertEquals(0.24, skill, 1e-9, "「战技…伤害提高 24%」");
        Assertions.assertEquals(0.24, followUp, 1e-9, "「…和追加攻击…」");
    }

    // ==================================================================

    private record Rule(String actor, boolean instance, double percent) {
    }

    private static Rule rule(int cone) {
        try (java.io.InputStream stream = ConeAuditBatchFourteenTest.class
                .getResourceAsStream("/game/light_cones/" + cone + ".json")) {
            Assertions.assertNotNull(stream, "missing cone " + cone);
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseReader(
                    new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            com.google.gson.JsonObject rule = root.getAsJsonArray("1").get(0).getAsJsonObject();
            com.google.gson.JsonObject effect = rule.getAsJsonArray("do").get(0).getAsJsonObject();
            String actor = null;
            for (com.google.gson.JsonElement condition : rule.getAsJsonArray("when")) {
                String text = condition.getAsString();
                if (text.startsWith("actor == ")) {
                    actor = text.substring("actor == ".length());
                }
            }
            return new Rule(actor, effect.has("instance") && effect.get("instance").getAsBoolean(),
                    effect.get("percent").getAsDouble());
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static final class Fixture {
        private final Character wearer;
        private final Character ally;
        private final Battle battle;

        private Fixture(Integer cone, boolean withAlly, int wearerId) {
            Weapon weapon = cone == null ? null : Weapon.build(cone, LEVEL, false, 1);
            this.wearer = CharacterFactory.create(wearerId, LEVEL, false, weapon);
            this.ally = CharacterFactory.create(ALLY, LEVEL);
            List<Character> party = withAlly ? List.of(wearer, ally) : List.of(wearer);
            this.battle = new Battle(party, List.of(BattleFixture.dummy()), BattleFixture.alwaysRoll(0.9999));
            battle.startBattle();
            battle.processRequests();
        }

        private double attribute(AttributeType type) {
            return wearer.getAttribute(type).get();
        }
    }
}
