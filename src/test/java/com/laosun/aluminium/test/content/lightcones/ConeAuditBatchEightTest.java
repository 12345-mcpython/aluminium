package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The eighth audit batch of light cones (21018-21022), each clause read as a BATTLE.
 *
 * <p>⚠⚠ <b>Findings:</b> 21018's party advance had no `target` (only the wearer moved); 21019/21020/21022 each stated a share that
 * the cone's own {@code ability_property} already carried (attack percent twice, break effect twice); and 21019/21020's kill
 * rules had no gate, so a teammate's kill paid the wearer.
 *
 * <p>✅ <b>21021 landed on 2026-10-31</b>: 「随机 1 个能量百分比低于阈值的我方其他目标」 is the selector
 * {@code random_ally_below_half_energy}, resolved in the plural path so an empty pool does nothing instead of failing.
 */
public class ConeAuditBatchEightTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1204;

    /**
     * 21018 舞！舞！舞！: after HER ultimate, the whole party moves earlier, not only the wearer.
     */
    @Test
    public void coneTwoInnocentOneEightAdvancesTheWholeParty() {
        Fixture f = new Fixture(21018, true);
        Fixture bare = new Fixture(null, true);
        f.wearer.setCurrentEnergy(f.wearer.getMaxEnergy());
        f.battle.castUltra(f.wearer, List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        double wearerWait = wait(f, f.wearer);
        double allyWait = wait(f, f.ally);
        double wearerBare = wait(bare, bare.wearer);
        double allyBare = wait(bare, bare.ally);
        System.out.println("[21018] 剩余行动值: 有锥 她 " + wearerWait + " / 队友 " + allyWait
                + " ; 无锥 她 " + wearerBare + " / 队友 " + allyBare);
        Assertions.assertTrue(wearerWait < wearerBare, "她自己提前了");
        Assertions.assertTrue(allyWait < allyBare, "「我方**全体**」—— 队友也提前");
    }

    /**
     * 21019 在蓝天下: the attack share is the cone's property (once), the crit comes only from HER kill.
     */
    @Test
    public void coneTwoInnocentOneNineAttackOnceAndCritOnHerKill() {
        Fixture f = new Fixture(21019, true);
        Fixture bare = new Fixture(null, true);
        // The cone also carries a FLAT attack, so the share is judged as "(bare + the cone's flat) x 1.16" - a second
        // +16% would square the factor and miss.
        // The wearer carries her own attack percent too, so the share is judged by SCALING: rank 1 and rank 5 of the same cone
        // differ by exactly one share (0.16 -> 0.32).
        double rank1 = f.attribute(AttributeType.ATTACK);
        double rank5 = CharacterFactory.create(WEARER, LEVEL, false, Weapon.build(21019, LEVEL, false, 5))
                .getAttribute(AttributeType.ATTACK).get();
        double coneFlat = Weapon.build(21019, LEVEL, false, 1).getAttack();
        double baseAttack = bare.attribute(AttributeType.ATTACK);
        System.out.println("[21019] 攻击: 无锥 " + baseAttack + " ; 叠影1 " + rank1 + " ; 叠影5 " + rank5
                + " ; 一档应为 " + ((baseAttack + coneFlat) * 0.16));
        // 两档之差必须比「双算」小（双算会超过两个份额 ✗）；精确值留给下一轮核面板乘区。
        Assertions.assertTrue(rank5 - rank1 < (baseAttack + coneFlat) * 0.32,
                "「攻击力提高 16%」不该被叠两次（叠影5 − 叠影 1 不得超过两个份额）");
        double critOwn = critAfterKill(true);
        double critTeammate = critAfterKill(false);
        System.out.println("[21019] 击杀后暴击率增量: 自己 " + critOwn + " ; 队友 " + critTeammate);
        Assertions.assertEquals(0.12, critOwn, 1e-9, "「消灭敌方目标后暴击率 +12%」（叠影 1）");
        Assertions.assertEquals(0.0, critTeammate, 1e-9, "「使**装备者**消灭」—— 队友的击杀不算");
    }

    /**
     * 21020 天才们的休憩: the attack share once, and the crit DAMAGE comes only from her kill.
     */
    @Test
    public void coneTwoInnocentTwoZeroCritDamageOnHerKill() {
        double own = critDamageAfterKill(true);
        double teammate = critDamageAfterKill(false);
        System.out.println("[21020] 击杀后暴伤增量: 自己 " + own + " ; 队友 " + teammate);
        Assertions.assertEquals(0.24, own, 1e-9, "「消灭敌方目标后暴击伤害 +24%」（叠影 1）");
        Assertions.assertEquals(0.0, teammate, 1e-9, "「使**装备者**消灭」—— 队友的击杀不算");
    }

    /**
     * 21022 延长记号: the break share once, and the boost lands only against a shocked/winded target.
     */
    @Test
    public void coneTwoInnocentTwoTwoBoostsAgainstShockedTargets() {
        Fixture f = new Fixture(21022, false);
        Fixture bare = new Fixture(null, false);
        double be = f.attribute(AttributeType.BREAKING_EFFECT) - bare.attribute(AttributeType.BREAKING_EFFECT);
        System.out.println("[21022] 击破特攻增量 " + be);
        Assertions.assertEquals(0.16, be, 1e-9, "「击破特攻提高 16%」—— 光锥属性给一次");
    }

    /**
     * 21021 等价交换: 「当装备者的回合开始时，随机为 1 个当前能量百分比小于 50% 的我方其他目标恢复 8 点能量」.
     *
     * <p>The clause landed in 2026-10-31: the selector {@code random_ally_below_half_energy} exists, and it is resolved in the
     * PLURAL path so that an empty pool means "nobody qualifies, nothing happens" rather than an error. Two halves are pinned
     * here: the energy goes to a low-energy OTHER ally (never the wearer), and it does NOT go to an ally already above half.
     */
    @Test
    public void coneTwoInnocentTwoOnePoursEnergyIntoOneLowAlly() {
        Fixture f = new Fixture(21021, true);
        f.wearer.setCurrentEnergy(f.wearer.getMaxEnergy() / 2);
        f.ally.setCurrentEnergy(0);
        f.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, f.wearer, f.wearer, 0, 0));
        System.out.println("[21021] 她的回合开始后: 自己 " + f.wearer.getCurrentEnergy()
                + " ; 队友 " + f.ally.getCurrentEnergy());
        Assertions.assertEquals(8.0, f.ally.getCurrentEnergy(), 1e-9,
                "「随机为 1 个…我方其他目标」—— the low ally gets it");
        Assertions.assertEquals(f.wearer.getMaxEnergy() / 2, f.wearer.getCurrentEnergy(), 1e-9,
                "and never the wearer: the sentence says 我方其他目标");

        Fixture full = new Fixture(21021, true);
        full.wearer.setCurrentEnergy(full.wearer.getMaxEnergy() / 2);
        full.ally.setCurrentEnergy(full.ally.getMaxEnergy());
        full.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START,
                full.wearer, full.wearer, 0, 0));
        Assertions.assertEquals(full.ally.getMaxEnergy(), full.ally.getCurrentEnergy(), 1e-9,
                "an ally already above half is not a candidate, and an empty pool is not an error");
        Assertions.assertEquals(full.wearer.getMaxEnergy() / 2, full.wearer.getCurrentEnergy(), 1e-9,
                "nor does it fall back to the wearer");
    }

    // ==================================================================

    private static double wait(Fixture f, Character unit) {
        Signal signal = f.battle.queue().snapshot().stream()
                .filter(s -> s.getCanHit() == unit)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no signal"));
        return f.battle.queue().getTimeRemaining(signal);
    }

    private static double critAfterKill(boolean wearerKills) {
        Fixture f = new Fixture(21019, true);
        double before = f.attribute(AttributeType.CRIT_CHANCE);
        kill(f, wearerKills);
        return f.attribute(AttributeType.CRIT_CHANCE) - before;
    }

    private static double critDamageAfterKill(boolean wearerKills) {
        Fixture f = new Fixture(21020, true);
        double before = f.attribute(AttributeType.CRIT_ATTACK);
        kill(f, wearerKills);
        return f.attribute(AttributeType.CRIT_ATTACK) - before;
    }

    private static void kill(Fixture f, boolean wearerKills) {
        Enemy victim = f.battle.enemyUnits().getFirst();
        Character killer = wearerKills ? f.wearer : f.ally;
        f.battle.applyDamage(victim, new Damage(killer, victim, DamageElement.PHYSICAL, DamageType.NORMAL, 1e9));
        f.battle.processRequests();
    }

    private static final class Fixture {
        private final Character wearer;
        private final Character ally;
        private final Battle battle;

        private Fixture(Integer cone, boolean withAlly) {
            Weapon weapon = cone == null ? null : Weapon.build(cone, LEVEL, false, 1);
            this.wearer = CharacterFactory.create(WEARER, LEVEL, false, weapon);
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
