package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.AbstractBuff;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DefaultSkill;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.Skill;
import com.laosun.aluminium.models.event.AttackEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * P1-9 acceptance: 附加伤害 (additional damage) and 真实伤害 (true damage), reproduced from the
 * real kits of 1309 知更鸟 and 1403 缇宝 (see {@code E:\code\blog\hsr\1309_知更鸟.md} /
 * {@code 1403_缇宝.md}).
 *
 * <p><b>附加伤害</b> official definition: "使受击者额外受到 1 次伤害，本次伤害不视为造成了 1 次攻击"
 * → it goes through the full zones (its base is a panel value: 攻击力 / 生命上限 × 倍率) and is
 * flagged {@code notCountsAsAttack()}.
 *
 * <p><b>真伤</b> (缇宝 E1): base is derived from 本次攻击总伤害值, so it skips every zone.
 *
 * <p>Attackers are plain Lv80 characters with ATK 100 / MaxHP 1000 and no boost or crit panel
 * attributes; defenders below use DEFENCE 0 unless stated otherwise.
 */
public class ExtraTrueDamageTest {
    private static final double EPS = 1e-6;

    private static Character character() {
        return Character.fromAttributes("ally", 1000, 100, 100, 100);   // ATK 100 / MaxHP 1000
    }

    private static Enemy enemy(String name) {
        return enemy(name, 1_000_000, 0);
    }

    private static Enemy enemy(String name, double hp, double defence) {
        return Enemy.fromAttributes(name, hp, defence, 100, 100);
    }

    private static Battle battle(List<Character> allies, List<Enemy> enemies) {
        return new Battle(allies, enemies, new Random(0));
    }

    private static double damageTaken(Enemy enemy) {
        return enemy.getMaxHp() - enemy.getCurrentHp();
    }

    private static Skill singleAttack() {
        return new DefaultSkill(1001, 1, 1);       // 真实数据：SingleAttack ×0.5
    }

    private static Skill aoeAttack() {
        return new DefaultSkill(1001, 3, 1);       // 真实数据：AoEAttack ×0.9
    }

    @Test
    public void concertoStyleAdditionalDamageHitsTheMainTargetExactlyOnce() {
        Character robin = character();
        Character mainC = character();
        Enemy first = enemy("e1");
        Enemy main = enemy("e2");
        Enemy third = enemy("e3");
        Battle battle = battle(List.of(robin, mainC), List.of(first, main, third));
        ConcertoBuff concerto = new ConcertoBuff(2, robin);
        robin.getBuffManager().addBuff(concerto);

        battle.castImmediate(aoeAttack(), mainC, List.of(main));    // 主目标 = e2

        // 主C 的 AOE：每敌 90；附加伤害只触发 1 次、只落在主目标身上
        // 120（120% × 知更鸟攻击力 100）× 固定暴击 2.5（100% 暴击率 / 150% 暴伤）= 300
        Assertions.assertEquals(90, damageTaken(first), EPS);
        Assertions.assertEquals(90 + 300, damageTaken(main), EPS);
        Assertions.assertEquals(90, damageTaken(third), EPS);
        Assertions.assertEquals(1, concerto.triggerCount, "每次施放攻击后只触发 1 次");
    }

    @Test
    public void fixedCritIsNotOverwrittenByThePanel() {
        Character robin = character();
        Character mainC = character();
        Enemy boss = enemy("boss");
        Battle battle = battle(List.of(robin, mainC), List.of(boss));
        robin.getBuffManager().addBuff(new ConcertoBuff(2, robin));

        // 攻击者面板暴击率 = 0：若 assemble 仍按面板骰，附加伤害就只有 120 而不是 300
        battle.castImmediate(singleAttack(), mainC, List.of(boss));

        Assertions.assertEquals(50 + 300, damageTaken(boss), EPS);
    }

    @Test
    public void additionalDamageDoesNotRetriggerTheAttackEvent() {
        Character robin = character();
        Character mainC = character();
        Enemy boss = enemy("boss");
        Battle battle = battle(List.of(robin, mainC), List.of(boss));
        ConcertoBuff concerto = new ConcertoBuff(2, robin);
        robin.getBuffManager().addBuff(concerto);

        battle.castImmediate(singleAttack(), mainC, List.of(boss));

        // 附加伤害段"不视为造成了 1 次攻击" → 不会递归触发
        Assertions.assertEquals(1, concerto.triggerCount);
        Assertions.assertEquals(50 + 300, damageTaken(boss), EPS);
    }

    @Test
    public void zoneStyleZoneHitsTheHighestHpTargetOncePerHitTarget() {
        Character tribbie = character();
        Character mainC = character();
        Enemy high = enemy("e1", 300_000, 0);
        Enemy mid = enemy("e2", 200_000, 0);
        Enemy low = enemy("e3", 100_000, 0);
        Battle battle = battle(List.of(tribbie, mainC), List.of(high, mid, low));
        tribbie.getBuffManager().addBuff(new TribbieZoneBuff(2, tribbie));

        battle.castImmediate(aoeAttack(), mainC, List.of(mid));

        // AOE ×0.9 → 各 90；结界"每有 1 名目标受到攻击" → 3 次 × (12% × 生命上限 1000 = 120)
        // 每次都挑"被击目标中当前 HP 最高"者 → 全部落在 e1
        Assertions.assertEquals(90 + 360, damageTaken(high), EPS);
        Assertions.assertEquals(90, damageTaken(mid), EPS);
        Assertions.assertEquals(90, damageTaken(low), EPS);
    }

    @Test
    public void zoneE1StyleTrueDamageUsesTheAttackTotalAndIgnoresEveryZone() {
        Character tribbie = character();
        Character mainC = character();
        Enemy boss = enemy("boss", 1_000_000, 10_000);                    // 防御 10000
        boss.setDamageResist(Map.of(DamageElement.QUANTUM, 0.9));         // 量子抗性 0.9
        Battle battle = battle(List.of(tribbie, mainC), List.of(boss));
        tribbie.getBuffManager().addBuff(new TribbieE1Buff(2, tribbie));

        // 主C 普攻（冰，×0.5）：100 × 0.5 = 50 → 防御区 1000 / (10000 + 1000)
        double mainDamage = 50.0 * 1000.0 / 11_000.0;

        battle.castImmediate(singleAttack(), mainC, List.of(boss));

        // 真伤 = 本次攻击总伤害 × 24%（D2 未定：这里不发生溢出，两种口径一致）
        Assertions.assertEquals(mainDamage * 1.24, damageTaken(boss), 1e-9);
    }

    @Test
    public void additionalDamageGoesThroughZonesWhileTrueDamageSkipsThem() {
        Character attacker = character();
        Enemy armoured = enemy("boss", 1_000_000, 1150);                  // 防御 1150
        Battle battle = battle(List.of(attacker), List.of(armoured));

        double additional = battle.applyAdditionalDamage(attacker, armoured, DamageElement.PHYSICAL, 1000);
        double trueDamage = battle.applyTrueDamage(attacker, armoured, DamageElement.PHYSICAL, 1000);

        // 附加伤害的 base 是面板值 → 吃防御区；真伤 → 原样
        Assertions.assertEquals(1000.0 * 1000.0 / 2150.0, additional, EPS);
        Assertions.assertEquals(1000, trueDamage, EPS);
    }

    // ==================================================================
    // 测试内嵌的"角色效果"：真实实现留给 P8-3，这里按文档数值复刻
    // ==================================================================

    /** 无实际效果的伤害反应型 buff 骨架（applyEffect / removeBuff 留空，只减时长）。 */
    private abstract static class DamageReactor extends AbstractBuff {
        protected final CanHit owner;

        private DamageReactor(int duration, CanHit owner) {
            super(duration, false);
            this.owner = owner;
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

        /** 被击目标中"当前生命值最高"的存活者；全死返回 null。 */
        protected static CanHit highestHpAlive(List<? extends CanHit> candidates) {
            CanHit best = null;
            for (CanHit candidate : candidates) {
                if (candidate.isDeath()) {
                    continue;
                }
                if (best == null || candidate.getCurrentHp() > best.getCurrentHp()) {
                    best = candidate;
                }
            }
            return best;
        }
    }

    /**
     * 1309 知更鸟【协奏】：我方目标每次施放攻击后，造成 1 次等于自身 120% 攻击力的物理附加伤害，
     * 固定 100% 暴击率 / 150% 暴伤。目标取**主目标**（D1(i)），主目标已死则本次跳过。
     */
    private static class ConcertoBuff extends DamageReactor implements AttackEvent {
        private int triggerCount;

        private ConcertoBuff(int duration, CanHit owner) {
            super(duration, owner);
        }

        @Override
        public void afterAttack(Battle battle, CanHit attacker, CanHit mainTarget,
                                List<? extends CanHit> hitTargets, double totalDamage) {
            triggerCount++;
            if (mainTarget == null || mainTarget.isDeath()) {
                return;                                   // D1(i)：主目标已死 → 本次不产生伤害
            }
            double base = owner.getAttribute(AttributeType.ATTACK).get() * 1.2;
            Damage extra = new Damage(owner, mainTarget, DamageElement.PHYSICAL, DamageType.ADDITIONAL, base);
            extra.fixedCrit(true, 1.5).notCountsAsAttack();   // 固定 100% / 150%
            battle.applyDamage(mainTarget, extra);
        }
    }

    /**
     * 1403 缇宝结界：我方攻击后"每有 1 名目标受到攻击"，对被击目标中当前生命值最高者造成
     * 1 次等于缇宝 12% 生命上限的量子附加伤害。
     */
    private static class TribbieZoneBuff extends DamageReactor implements AttackEvent {
        private TribbieZoneBuff(int duration, CanHit owner) {
            super(duration, owner);
        }

        @Override
        public void afterAttack(Battle battle, CanHit attacker, CanHit mainTarget,
                                List<? extends CanHit> hitTargets, double totalDamage) {
            double base = owner.getMaxHp() * 0.12;
            for (int i = 0; i < hitTargets.size(); i++) {
                CanHit target = highestHpAlive(hitTargets);
                if (target == null) {
                    return;                               // 全死 → 剩余次数作废
                }
                battle.applyAdditionalDamage(owner, target, DamageElement.QUANTUM, base);
            }
        }
    }

    /**
     * 1403 缇宝 E1：对（造成附加伤害的）目标额外造成等同于本次攻击总伤害值 24% 的真实伤害。
     * 这里用主目标近似"造成附加伤害的目标"（TODO P8-3 按 E1 完整链路接线）。
     */
    private static class TribbieE1Buff extends DamageReactor implements AttackEvent {
        private TribbieE1Buff(int duration, CanHit owner) {
            super(duration, owner);
        }

        @Override
        public void afterAttack(Battle battle, CanHit attacker, CanHit mainTarget,
                                List<? extends CanHit> hitTargets, double totalDamage) {
            if (mainTarget == null || mainTarget.isDeath()) {
                return;
            }
            battle.applyTrueDamage(owner, mainTarget, DamageElement.QUANTUM, totalDamage * 0.24);
        }
    }
}
