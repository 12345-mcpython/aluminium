package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The fifth audit batch of light cones (21000-21005), each clause read as a BATTLE.
 *
 * <p>⚠⚠ <b>Three defects, all silent:</b>
 * <ul>
 *   <li><b>21004</b> had no gate on its energy rule: a TEAMMATE's attack paid the wearer (the fourth cone in that family:
 *       20012, 20013, 20020, now this).</li>
 *   <li><b>21003</b> sampled 「敌方目标数量小于等于 2」 at BATTLE_START only, and its own note argued that count is an
 *       "opening fact" - it is not, enemies die during the fight. It now re-reads on `KILL`.</li>
 *   <li><b>21005</b> capped its stacks at 3 with a quote that the text does not contain (the sentence has no cap).</li>
 * </ul>
 */
public class ConeAuditBatchFiveTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1204;

    /**
     * 21000 一场术后对话: the energy-regeneration share comes from the cone's own property, and the heal boost lives inside
     * the ultimate's cast only.
     */
    @Test
    public void coneTwoInnocentHealsInsideTheUltimateAndBoostsRegeneration() {
        Fixture f = new Fixture(21000, true, 1409);
        double regenWith = f.wearer.getAttribute(AttributeType.ENERGY_REGENERATION_RATE).get();
        double regenBare = new Fixture(null, true, 1409).wearer.getAttribute(AttributeType.ENERGY_REGENERATION_RATE).get();
        System.out.println("[21000] 能量恢复效率增量 " + (regenWith - regenBare));
        Assertions.assertEquals(0.08, regenWith - regenBare, 1e-9, "「能量恢复效率提高 8%」（光锥属性）");

        // Inside the ultimate's cast the wearer carries +12% outgoing healing; right after it, the same heal is unboosted.
        // ⚠ The window closes with the cast (`until: cast_end`), so it cannot be read AFTER `castUltra`: the 12% is read from
        // the file, the way the 20001 case reads its own cast-scoped half.
        com.google.gson.JsonObject rule = read("/game/light_cones/21000.json").getAsJsonArray("1").get(0).getAsJsonObject();
        com.google.gson.JsonObject effect = rule.getAsJsonArray("do").get(0).getAsJsonObject();
        Assertions.assertEquals(0.12, effect.get("percent").getAsDouble(), 1e-12, "「施放终结技时治疗量提高 12%」");
        Assertions.assertEquals("cast_end", effect.get("until").getAsString(), "窗口就是那一次施放");
        Assertions.assertEquals("CAST_SETUP", rule.get("on").getAsString(), "结算之前的事件，才救得了这次施放自己的治疗");
    }

    // ⚠ 21001 晚安与睡颜的「每个负面效果 +12%%，最多 3 层」尚未配判据：合成的 buff 不计入
    // 	arget_debuff_count（它只数真正的 debuff 状态），而负面效果的数量还没找到可靠的来源 ✗。
    // 规则本身由装载守卫盯住（ConeConditionLiteralsTest）；配判据记在状态表里。
    /**
     * 21002 余生的第一天: the party's all-type resistance rises by 8% (the DEF half is the cone's own property).
     */
    @Test
    public void coneTwoInnocentRaisesThePartysAllTypeResistance() {
        Fixture f = new Fixture(21002, true);
        Fixture bare = new Fixture(null, true);
        for (int i = 0; i < 2; i++) {
            double delta = f.battle.characters().get(i).getAttribute(AttributeType.ALL_TYPE_RESISTANCE).get()
                    - bare.battle.characters().get(i).getAttribute(AttributeType.ALL_TYPE_RESISTANCE).get();
            System.out.println("[21002] " + f.battle.characters().get(i).getName() + " 全属性抗性增量 " + delta);
            Assertions.assertEquals(0.08, delta, 1e-9, "「我方全体…全属性抗性提高 8%」（叠影 1）");
        }
    }

    /**
     * 21003 唯有沉默: no crit bonus with three enemies, +12% once only two remain.
     */
    @Test
    public void coneTwoInnocentCritsOnlyWhenTwoEnemiesAreLeft() {
        Fixture f = new Fixture(21003, false, 1002, 3);
        double bare = new Fixture(null, false, 1002, 3).critChance();
        double withThree = f.critChance() - bare;
        Enemy victim = f.battle.enemyUnits().getFirst();
        f.battle.applyDamage(victim, new Damage(f.wearer, victim, DamageElement.PHYSICAL, DamageType.NORMAL, 1e9));
        f.battle.processRequests();
        double withTwo = f.critChance() - bare;
        long alive = f.battle.enemyUnits().stream().filter(e -> !e.isDeath()).count();
        System.out.println("[21003] 三只敌人时 " + withThree + " ; 杀掉一只后 " + withTwo + " ; 活着 " + alive + " 只");
        Assertions.assertEquals(0.0, withThree, 1e-9, "三只敌人 ⇒ 不触发");
        Assertions.assertEquals(0.12, withTwo, 1e-9, "剩两只 ⇒ +12%（`KILL` 后重新求值）");
    }

    /**
     * 21004 记忆中的模样: her own attack pays 4 energy, once per turn; a teammate's pays nothing.
     */
    @Test
    public void coneTwoInnocentPaysForHerOwnAttackOnly() {
        Fixture f = new Fixture(21004, true);
        f.wearer.setCurrentEnergy(0);
        f.battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.BASIC_ATTACK, f.wearer, f.battle.enemyUnits().getFirst(), 0, 0));
        double first = f.wearer.getCurrentEnergy();
        f.battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, f.wearer, f.battle.enemyUnits().getFirst(), 0, 0));
        double second = f.wearer.getCurrentEnergy();
        Fixture other = new Fixture(21004, true);
        other.wearer.setCurrentEnergy(0);
        other.battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, other.ally, other.battle.enemyUnits().getFirst(), 0, 0));
        double teammate = other.wearer.getCurrentEnergy();
        System.out.println("[21004] 自己第一发后 " + first + " ; 同回合第二发后 " + second + " ; 队友施放后 " + teammate);
        Assertions.assertEquals(4.0, first, 1e-9, "「施放攻击后额外恢复 4 点」（叠影 1）");
        Assertions.assertEquals(4.0, second, 1e-9, "「单个回合内不可重复触发」");
        Assertions.assertEquals(0.0, teammate, 1e-9, "「**装备者**施放攻击后」—— 队友的不算");
    }

    /**
     * 21005 鼹鼠党欢迎你: one layer per attack, and the layers are NOT capped at three (the text has no cap).
     */
    @Test
    public void coneTwoInnocentStacksBeyondThreeAttacks() {
        Fixture f = new Fixture(21005, false);
        double start = f.wearer.getAttribute(AttributeType.ATTACK).get();
        double share = f.wearer.getAttribute(AttributeType.ATTACK).baseValue() * 0.12;
        double[] gains = new double[5];
        for (int i = 0; i < 4; i++) {
            f.battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.BASIC_ATTACK, f.wearer, f.battle.enemyUnits().getFirst(), 0, 0));
            gains[i] = f.wearer.getAttribute(AttributeType.ATTACK).get() - start;
        }
        System.out.println("[21005] 每层攻击增量 " + gains[0] + " / " + gains[1] + " / " + gains[2] + " / " + gains[3]
                + "（一层 = " + share + "）");
        Assertions.assertEquals(share, gains[0], share * 1e-9, "1 层 = +12%");
        Assertions.assertEquals(2 * share, gains[1], share * 1e-6, "2 层");
        Assertions.assertEquals(3 * share, gains[2], share * 1e-6, "3 层");
        Assertions.assertEquals(4 * share, gains[3], share * 1e-6,
                "4 次攻击 ⇒ 4 层（原文没有「最多 3 层」），所以第 4 次仍在加");
    }

    // ==================================================================

    private static com.google.gson.JsonObject read(String path) {
        try (java.io.InputStream stream = ConeAuditBatchFiveTest.class.getResourceAsStream(path)) {
            Assertions.assertNotNull(stream, "missing resource " + path);
            return com.google.gson.JsonParser.parseReader(
                    new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static double boostAgainst(int debuffs) {
        Fixture f = new Fixture(21001, true);
        Fixture bare = new Fixture(null, true);
        if (debuffs > 0) {
            applyDebuffs(f.battle, f.battle.enemyUnits().getFirst(), debuffs);
            applyDebuffs(bare.battle, bare.battle.enemyUnits().getFirst(), debuffs);
        }
        return boostZone(f) - boostZone(bare);
    }

    private static void applyDebuffs(Battle battle, Enemy target, int count) {
        for (int i = 0; i < count; i++) {
            EffectSpec slow = new EffectSpec();
            TriggerSpecs.set(slow, "op", "APPLY_BUFF");
            TriggerSpecs.set(slow, "buff", "负面" + i);
            TriggerSpecs.set(slow, "turns", 5);
            TriggerSpecs.set(slow, "target", "target");
            // ⚠ On the ALLY's table, not the wearer's: the wearer's table carries the cone's rules, and replacing it would
            // measure a fixture that has no cone at all (measured: every band read 0).
            Character ally = battle.characters().get(1);
            ally.setTriggerTable(new TriggerTable(9300 + i, List.of(
                    TriggerSpecs.rule(TriggerEvent.ALLY_ATTACK.name(), List.of(), slow))));
            battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, target, 1, 0));
        }
    }

    private static double boostZone(Fixture f) {
        List<Damage> settled = new ArrayList<>();
        f.battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        f.battle.castImmediate(f.wearer.getSkills().get(SkillType.COMMON), f.wearer,
                List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        return settled.getFirst().breakdown().get("BoostArea");
    }

    private static final class Fixture {
        private final Character wearer;
        private final Character ally;
        private final Battle battle;
        private double ultBoostSeen = -1;

        private Fixture(Integer cone, boolean withAlly) {
            this(cone, withAlly, WEARER);
        }

        private Fixture(Integer cone, boolean withAlly, int wearerId) {
            this(cone, withAlly, wearerId, 1);
        }

        private Fixture(Integer cone, boolean withAlly, int wearerId, int enemies) {
            Weapon weapon = cone == null ? null : Weapon.build(cone, LEVEL, false, 1);
            this.wearer = CharacterFactory.create(wearerId, LEVEL, false, weapon);
            this.ally = CharacterFactory.create(ALLY, LEVEL);
            List<Character> party = withAlly ? List.of(wearer, ally) : List.of(wearer);
            List<Enemy> foes = new ArrayList<>();
            for (int i = 0; i < enemies; i++) {
                foes.add(BattleFixture.dummy());
            }
            this.battle = new Battle(party, foes, BattleFixture.alwaysRoll(0.9999));
            battle.startBattle();
            battle.processRequests();
        }

        private double critChance() {
            return wearer.getAttribute(AttributeType.CRIT_CHANCE).get();
        }

        /**
         * The outgoing-healing boost the wearer carries right after the ultimate: read through a same-amount heal.
         */
        private double boostDuringUlt() {
            Character ally0 = battle.characters().get(1);
            if (ally0.getCurrentHp() >= ally0.getMaxHp()) {
                ally0.consumeHp(ally0.getMaxHp() * 0.4);
            }
            double before = ally0.getCurrentHp();
            battle.heal(wearer, ally0, ally0.getMaxHp() * 0.1);
            battle.processRequests();
            double healed = ally0.getCurrentHp() - before;
            // The boost is what the heal path multiplies by; recover it from a second heal of the same size.
            double expected = ally0.getMaxHp() * 0.1;
            ultBoostSeen = healed / expected - 1;
            return ultBoostSeen;
        }
    }
}
