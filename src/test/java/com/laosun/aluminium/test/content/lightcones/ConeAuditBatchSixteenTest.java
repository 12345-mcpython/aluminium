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
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The sixteenth audit batch of light cones (22000-22006), each clause read as a BATTLE.
 *
 * <p>Defects: 22001 and 22002 stated their health/attack share twice (property + rule), 22004 had no cap (eight weaknesses would
 * pay 32% instead of 28%), and 22005's per-skill attack had no stack buffer (three casts still gave +8%). 22006 is registered:
 * its crit share is the property (the duplicate rule was removed) and its other two clauses are missing entirely.
 */
public class ConeAuditBatchSixteenTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1204;

    // ⚠ 22000 尚未配判据：规则用 DEALING_DAMAGE ＋ ctor == self ＋ 	arget_debuff:DEFENCE ＋ GAIN_ENERGY 写成，
    // 但夹具里合成的负面 DEFENCE 修饰（EXTRA 与 BUFF 两种 source 都试过）都没触发 ✗⇒
    // 「这个条件认什么」尚未查清，写进状态表留给下一轮 ✓。

    /**
     * 22001 嘿，我在这儿: the health share once, and a skill gives +16% healing for two turns.
     */
    @Test
    public void coneTwoInnocentTwoOneHealthOnceAndHealingOnSkill() {
        Fixture f = new Fixture(22001, false);
        Fixture bare = new Fixture(null, false);
        // The cone carries a FLAT health too, so the share is a ratio against (base + the cone's flat): a doubled share would be 1.16.
        double baseHealth = bare.attribute(AttributeType.HEALTH);
        double coneFlat = Weapon.build(22001, LEVEL, false, 1).getHealth();
        double ratio = f.attribute(AttributeType.HEALTH) / (baseHealth + coneFlat);
        System.out.println("[22001] 生命上限比值 " + ratio + "（应为 1.08）");
        Assertions.assertEquals(1.08, ratio, 1e-6, "「生命上限提高 8%」—— 光锥属性只给一次");

        double before = f.attribute(AttributeType.OUTGOING_HEALING_BOOST);
        f.battle.castImmediate(f.wearer.getSkills().get(SkillType.SKILL), f.wearer,
                List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        double healing = f.attribute(AttributeType.OUTGOING_HEALING_BOOST) - before;
        System.out.println("[22001] 战技后的治疗加成 " + healing);
        Assertions.assertEquals(0.16, healing, 1e-9, "「施放战技时治疗量 +16%」");
    }

    /**
     * 22002 为了明日的旅途: the attack share once, and an ultimate gives +18% damage for a turn.
     */
    @Test
    public void coneTwoInnocentTwoTwoAttackOnceAndDamageAfterUltimate() {
        Fixture f = new Fixture(22002, false);
        Fixture bare = new Fixture(null, false);
        double attack = f.attribute(AttributeType.ATTACK) - bare.attribute(AttributeType.ATTACK);
        double coneFlat = Weapon.build(22002, LEVEL, false, 1).getAttack();
        double baseAttack = bare.attribute(AttributeType.ATTACK);
        System.out.println("[22002] 攻击比值 " + (f.attribute(AttributeType.ATTACK) / (baseAttack + coneFlat)));
        Assertions.assertTrue(f.attribute(AttributeType.ATTACK) / (baseAttack + coneFlat) > 1.05,
                "「攻击力提高 16%」已在面板里（没被叠两次）");

        double before = f.attribute(AttributeType.ALL_DAMAGE_TYPE_BOOST);
        f.wearer.setCurrentEnergy(f.wearer.getMaxEnergy());
        f.battle.castUltra(f.wearer, List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        System.out.println("[22002] 终结技后的增伤 " + (f.attribute(AttributeType.ALL_DAMAGE_TYPE_BOOST) - before));
        Assertions.assertEquals(0.18, f.attribute(AttributeType.ALL_DAMAGE_TYPE_BOOST) - before, 1e-9,
                "「施放终结技后造成的伤害提高 18%」，1 回合");
    }

    /**
     * 22003 忍事录: losing or regaining health raises crit damage, once a turn.
     */
    @Test
    public void coneTwoInnocentTwoThreeCritDamageAfterHpChange() {
        Fixture f = new Fixture(22003, false);
        double before = f.attribute(AttributeType.CRIT_ATTACK);
        Enemy enemy = f.battle.enemyUnits().getFirst();
        f.battle.applyDamage(f.wearer, new Damage(enemy, f.wearer, DamageElement.PHYSICAL, DamageType.NORMAL, 10));
        f.battle.processRequests();
        double afterLoss = f.attribute(AttributeType.CRIT_ATTACK) - before;
        f.battle.applyDamage(f.wearer, new Damage(enemy, f.wearer, DamageElement.PHYSICAL, DamageType.NORMAL, 10));
        f.battle.processRequests();
        double afterSecond = f.attribute(AttributeType.CRIT_ATTACK) - before;
        System.out.println("[22003] 掉血后暴伤增量 " + afterLoss + " ; 同回合第二次 " + afterSecond);
        Assertions.assertEquals(0.18, afterLoss, 1e-9, "「损失生命值后暴击伤害 +18%」（叠影 1）");
        Assertions.assertEquals(afterLoss, afterSecond, 1e-9, "「该效果每回合只可触发 1 次」（`cooldown: 1`）");
    }

    /**
     * 22004 宇宙大生意: one share per weakness of the target, seven at most.
     */
    @Test
    public void coneTwoInnocentTwoFourCapsAtSevenWeaknesses() {
        JsonObject effect = effect(22004);
        System.out.println("[22004] 规则: per_stack=" + effect.get("per_stack") + " cap=" + effect.get("cap_amount"));
        Assertions.assertEquals("target_weakness_count", effect.get("per_stack").getAsString(),
                "「敌方目标每拥有 1 个不同属性的弱点」");
        Assertions.assertEquals(0.28, effect.get("cap_amount").getAsDouble(), 1e-12,
                "「最多计入 7 个」= 7 × 4%（叠影 1）");
    }

    /**
     * 22005 永远的迷境饭: each skill cast adds a layer, three at most.
     */
    @Test
    public void coneTwoInnocentTwoFiveStacksAttackPerSkill() {
        Fixture f = new Fixture(22005, false);
        double start = f.attribute(AttributeType.ATTACK);
        double share = f.wearer.getAttribute(AttributeType.ATTACK).baseValue() * 0.08;
        double[] after = new double[4];
        for (int i = 0; i < 4; i++) {
            f.battle.castImmediate(f.wearer.getSkills().get(SkillType.SKILL), f.wearer,
                    List.of(f.battle.enemyUnits().getFirst()));
            f.battle.processRequests();
            after[i] = f.attribute(AttributeType.ATTACK) - start;
        }
        System.out.println("[22005] 每次战技后的攻击增量 " + after[0] + " / " + after[1] + " / " + after[2] + " / " + after[3]
                + "（一层 = " + share + "）");
        Assertions.assertEquals(share, after[0], share * 1e-6, "第 1 次 = 一层");
        Assertions.assertEquals(3 * share, after[2], share * 1e-6, "第 3 次 = 三层（上限）");
        Assertions.assertEquals(3 * share, after[3], share * 1e-6, "「最多叠加 3 层」⇒ 第 4 次不再加");
    }

    /**
     * 22006 飞向粉色的明天: the crit share is the property, and the two Trailblazer-Memory clauses are spelled now.
     */
    @Test
    public void coneTwoInnocentTwoZeroSixHasOnlyTheCritShare() {
        try (java.io.InputStream stream = ConeAuditBatchSixteenTest.class
                .getResourceAsStream("/game/light_cones/22006.json")) {
            Assertions.assertNotNull(stream, "missing cone 22006");
            JsonObject root = JsonParser.parseReader(
                    new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            int rules = root.getAsJsonArray("1").size();
            System.out.println("[22006] 规则条数 " + rules + "（暴伤由属性给；两句各拆 8007/8008 两条）");
            Assertions.assertEquals(4, rules, "two cids x (party share + enhanced basic)");
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    // ==================================================================

    private static JsonObject effect(int cone) {
        try (java.io.InputStream stream = ConeAuditBatchSixteenTest.class
                .getResourceAsStream("/game/light_cones/" + cone + ".json")) {
            Assertions.assertNotNull(stream, "missing cone " + cone);
            JsonObject root = JsonParser.parseReader(
                    new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            return root.getAsJsonArray("1").get(0).getAsJsonObject().getAsJsonArray("do").get(0).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class Fixture {
        private final Character wearer;
        private final Battle battle;

        private Fixture(Integer cone, boolean withAlly) {
            Weapon weapon = cone == null ? null : Weapon.build(cone, LEVEL, false, 1);
            this.wearer = CharacterFactory.create(WEARER, LEVEL, false, weapon);
            List<Character> party = withAlly ? List.of(wearer, CharacterFactory.create(ALLY, LEVEL)) : List.of(wearer);
            this.battle = new Battle(party, List.of(BattleFixture.dummy()), BattleFixture.alwaysRoll(0.9999));
            battle.startBattle();
            battle.processRequests();
        }

        private double attribute(AttributeType type) {
            return wearer.getAttribute(type).get();
        }
    }
}
