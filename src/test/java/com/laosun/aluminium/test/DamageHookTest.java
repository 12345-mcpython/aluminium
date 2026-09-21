package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.AbstractBuff;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue.Modifier.ModifierSource;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.buffs.ReductionBuff;
import com.laosun.aluminium.models.buffs.VulnerabilityBuff;
import com.laosun.aluminium.models.event.DamageEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * P1-7 acceptance: buffs inject zones through {@code DamageEvent} during settlement.
 *
 * <p>Every defender here has DEFENCE = 0 and the attacker has no boost / crit attributes,
 * so a base of 1000 isolates the hooked zone.
 */
public class DamageHookTest {
    private static final double EPS = 1e-6;
    private static final double BASE = 1000.0;

    private static Character attacker() {
        return Character.fromAttributes("attacker", 1000, 100, 100, 100);
    }

    private static Enemy enemy() {
        return Enemy.fromAttributes("enemy", 100000, 0, 100, 100);
    }

    private static double settle(Character attacker, Enemy defender) {
        return settle(attacker, defender, DamageType.NORMAL);
    }

    private static double settle(Character attacker, Enemy defender, DamageType type) {
        Damage damage = new Damage(attacker, defender, DamageElement.FIRE, type, BASE);
        return new Battle(List.of(attacker), List.of(defender), new Random(0))
                .applyDamage(defender, damage);
    }

    @Test
    public void vulnerabilityRaisesIncomingDamage() {
        Enemy enemy = enemy();
        enemy.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.5));

        Assertions.assertEquals(1500, settle(attacker(), enemy), EPS);
    }

    @Test
    public void lateBuffDoesNotTickOnBeforeMove() {
        Enemy enemy = enemy();
        enemy.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.5));

        enemy.getBuffManager().beforeMove();     // 后置 buff：beforeMove 不动它

        Assertions.assertEquals(1500, settle(attacker(), enemy), EPS);
    }

    @Test
    public void vulnerabilityExpiresAfterItsDuration() {
        Enemy enemy = enemy();
        enemy.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.5));
        Assertions.assertEquals(1500, settle(attacker(), enemy), EPS);

        enemy.getBuffManager().afterMove();
        enemy.getBuffManager().afterMove();      // duration 2 → 0，到期即摘掉修正

        Assertions.assertEquals(1000, settle(attacker(), enemy), EPS);
    }

    @Test
    public void reductionLowersIncomingDamage() {
        Enemy enemy = enemy();
        enemy.getBuffManager().addBuff(new ReductionBuff(2, 0.3));

        Assertions.assertEquals(700, settle(attacker(), enemy), EPS);
    }

    @Test
    public void sameKindBuffReplacesInsteadOfStacking() {
        Enemy enemy = enemy();
        enemy.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.5));
        enemy.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.9));

        Assertions.assertEquals(1900, settle(attacker(), enemy), EPS);
    }

    @Test
    public void hooksAlsoApplyToBreakDamage() {
        Enemy enemy = enemy();
        enemy.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.5));

        // 击破不吃增伤/双暴（BoostArea / CritArea 的 applies() 挡掉），但吃易伤 —— P4 复用它
        Assertions.assertEquals(1500, settle(attacker(), enemy, DamageType.BREAK), EPS);
    }

    @Test
    public void attackerSideWeaknessFeedsTheWeaknessZone() {
        // 虚弱是"攻击方负面"（HSR.md §2.2）→ 钩子必须也遍历攻击方
        Character attacker = attacker();
        attacker.getBuffManager().addBuff(new WeaknessBuff(2, 0.4));

        Assertions.assertEquals(600, settle(attacker, enemy()), EPS);
    }

    // ==================================================================
    // C-1：注入乘区的 buff 必须只看自己站在哪一侧
    //   Battle.assemble 把 DamageEvent 广播给攻守双方，所以"挂在哪一侧"必须由 buff 自己判，
    //   否则挂在敌人身上的易伤会连它自己的输出一起提高、挂在角色身上的减伤会削自己的输出。
    // ==================================================================

    @Test
    public void vulnerabilityOnTheAttackerDoesNotBoostItsOwnAttacks() {
        Character attacker = attacker();
        attacker.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.5));

        // 受击方身上没有易伤 → 这一击就是干净的 1000（易伤只算"我挨的那一下"）
        Assertions.assertEquals(1000, settle(attacker, enemy()), EPS);
    }

    @Test
    public void vulnerabilityStillAppliesWhenItsOwnerIsTheDefender() {
        // 与上一条配对：挂对了侧就必须照常生效（防止"一刀切不生效"的假修复）
        Character attacker = attacker();
        Enemy enemy = enemy();
        enemy.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.5));

        Assertions.assertEquals(1500, settle(attacker, enemy), EPS);
    }

    @Test
    public void reductionOnTheAttackerDoesNotWeakenItsOwnAttacks() {
        Character attacker = attacker();
        attacker.getBuffManager().addBuff(new ReductionBuff(2, 0.3));

        Assertions.assertEquals(1000, settle(attacker, enemy()), EPS, "减伤只挡「我挨的那一下」");
    }

    @Test
    public void reductionStillAppliesWhenItsOwnerIsTheDefender() {
        Character attacker = attacker();
        Enemy enemy = enemy();
        enemy.getBuffManager().addBuff(new ReductionBuff(2, 0.3));

        Assertions.assertEquals(700, settle(attacker, enemy), EPS);
    }

    /**
     * 测试内嵌的攻击方负面 buff：证明 {@code DamageEvent} 会在攻击方一侧被触发。
     * （生产用的 WeaknessBuff 留给 P10-3 统一做。）
     */
    private static class WeaknessBuff extends AbstractBuff implements DamageEvent {
        private final double ratio;

        private WeaknessBuff(int duration, double ratio) {
            super(duration, false);
            this.ratio = ratio;
        }

        @Override
        public boolean canAct() {
            return true;
        }

        @Override
        public void applyEffect(CanHit target) {
        }

        @Override
        public void removeBuff(CanHit target) {
        }

        @Override
        public void tickEffect(CanHit target) {
            decreaseDuration();
        }

        @Override
        public void onDamage(Battle battle, Damage damage) {
            damage.addWeakness(ratio, ModifierSource.DEBUFF, id);
        }
    }
}
