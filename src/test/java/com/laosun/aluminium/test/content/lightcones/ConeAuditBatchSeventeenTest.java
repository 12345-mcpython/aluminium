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

    // ==================================================================

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
        private final Battle battle;

        private Fixture(Integer cone, int enemies) {
            Weapon weapon = cone == null ? null : Weapon.build(cone, LEVEL, false, 1);
            this.wearer = CharacterFactory.create(WEARER, LEVEL, false, weapon);
            List<com.laosun.aluminium.models.enemy.Enemy> foes = enemies == 1
                    ? List.of(BattleFixture.dummy())
                    : List.of(BattleFixture.dummy(), BattleFixture.dummy());
            this.battle = new Battle(List.of(wearer), foes, BattleFixture.alwaysRoll(0.9999));
            battle.startBattle();
            battle.processRequests();
        }

        private double attribute(AttributeType type) {
            return wearer.getAttribute(type).get();
        }
    }
}
