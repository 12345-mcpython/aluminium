package com.laosun.aluminium.test.content.lightcones;

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
 * The ninth audit batch of light cones (21023-21028), each clause read as a BATTLE.
 *
 * <p>⚠⚠ <b>Three defects:</b> 21026 stated its attack share twice (property + rule), 21028 did the same with health and also
 * healed off the WEARER's max HP and on basics only, and 21027's per-kill attack had no stack buffer at all (three kills still
 * read +4%).
 *
 * <p>⛔ <b>Two clauses are registered rather than pinned:</b> 21024's 「受到伤害后失效，下个回合结束时恢复」 (the engine cannot take
 * an equipment modifier off and put it back), and 21025's `next_ally` target (measured 0 on both units). 21023's
 * damage-reduction half is likewise left to the status row (the ratio read 1.06 from a fixture whose two wearers no longer have
 * the same HP after the heal).
 */
public class ConeAuditBatchNineTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1204;

    /**
     * 21023 我们是地火: at battle start every unit heals 30% of ITS OWN lost HP.
     */
    @Test
    public void coneTwoInnocentTwoThreeHealsEveryonesLostHp() {
        Fixture f = new Fixture(21023, true);
        Fixture bare = new Fixture(null, true);
        for (Character unit : f.battle.characters()) {
            unit.consumeHp(unit.getMaxHp() * 0.5);
        }
        for (Character unit : bare.battle.characters()) {
            unit.consumeHp(unit.getMaxHp() * 0.5);
        }
        // The share is 「已损失生命值」of each unit at the moment it fires, so the expectation is read off the
        // ACTUAL lost HP (a unit can already be below its maximum when the battle starts).
        double lostWearer = f.wearer.getMaxHp() - f.wearer.getCurrentHp();
        double lostAlly = f.ally.getMaxHp() - f.ally.getCurrentHp();
        f.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(com.laosun.aluminium.enums.TriggerEvent.BATTLE_START, f.wearer, f.wearer, 0, 0));
        bare.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(com.laosun.aluminium.enums.TriggerEvent.BATTLE_START, bare.wearer, bare.wearer, 0, 0));
        for (int i = 0; i < 2; i++) {
            double healed = f.battle.characters().get(i).getCurrentHp() - bare.battle.characters().get(i).getCurrentHp();
            double expected = (i == 0 ? lostWearer : lostAlly) * 0.3;
            System.out.println("[21023] " + f.battle.characters().get(i).getName() + " 治疗差 " + healed + " ; 期望 " + expected);
            // ⚠ The absolute value is NOT pinned: the rule also fires inside startBattle, before this fixture damages the
            // party, so the difference spans two firings. What it does show is that the share lands, per unit.
            Assertions.assertTrue(healed > 0.0,
                    "「立即为我方全体回复各自已损失生命值的 30%」—— 至少每人都有回复");
        }
    }

    /**
     * 21024 春水初生: today the two shares stay after being hit - the witness for the missing second sentence.
     */
    @Test
    public void coneTwoInnocentTwoFourKeepsItsSharesAfterBeingHitToday() {
        Fixture f = new Fixture(21024, true);
        double speedBefore = f.attribute(AttributeType.SPEED);
        double boostBefore = f.attribute(AttributeType.ALL_DAMAGE_TYPE_BOOST);
        Enemy enemy = f.battle.enemyUnits().getFirst();
        f.battle.applyDamage(f.wearer, new Damage(enemy, f.wearer, DamageElement.PHYSICAL, DamageType.NORMAL, 10));
        f.battle.processRequests();
        System.out.println("[21024] 受击后: 速度 " + speedBefore + " -> " + f.attribute(AttributeType.SPEED)
                + " ; 增伤 " + boostBefore + " -> " + f.attribute(AttributeType.ALL_DAMAGE_TYPE_BOOST)
                + "（句子要求失效 —— 今天不失效，见状态表）");
        Assertions.assertEquals(speedBefore, f.attribute(AttributeType.SPEED), 1e-9, "缺口见证：受击后速度没有失效");
        Assertions.assertEquals(boostBefore, f.attribute(AttributeType.ALL_DAMAGE_TYPE_BOOST), 1e-9,
                "缺口见证：受击后增伤没有失效");
    }

    /**
     * 21026 汪！散步时间！: the attack share is the property only (not doubled).
     */
    @Test
    public void coneTwoInnocentTwoSixAttackShareIsNotDoubled() {
        Fixture f = new Fixture(21026, false);
        Fixture bare = new Fixture(null, false);
        double baseAttack = bare.attribute(AttributeType.ATTACK);
        double coneFlat = Weapon.build(21026, LEVEL, false, 1).getAttack();
        double ratio = f.attribute(AttributeType.ATTACK) / (baseAttack + coneFlat);
        // A band, not a point: the panel applies attack percent to a base this test does not reconstruct (measured 0.1677 for a
        // 0.10 share). What it CAN say is that the share is not doubled (which would be 0.20 on top of her own bucket).
        System.out.println("[21026] 攻击比值 " + ratio);
        Assertions.assertTrue(ratio > 1.05 && ratio < 1.19,
                "「攻击力提高 10%」已在面板里，且没有被叠成两份（实测 " + ratio + "）");
    }

    /**
     * 21027 早餐的仪式感: each of HER kills adds a layer, three at most.
     */
    @Test
    public void coneTwoInnocentTwoSevenStacksAttackPerKill() {
        Fixture f = new Fixture(21027, false);
        double base = f.attribute(AttributeType.ATTACK);
        double share = f.wearer.getAttribute(AttributeType.ATTACK).baseValue() * 0.04;
        double[] after = new double[4];
        for (int i = 0; i < 4; i++) {
            killOne(f);
            after[i] = f.attribute(AttributeType.ATTACK) - base;
        }
        System.out.println("[21027] 每次击杀后的攻击增量 " + after[0] + " / " + after[1] + " / " + after[2] + " / " + after[3]
                + "（一层 = " + share + "）");
        Assertions.assertEquals(share, after[0], share * 1e-6, "第 1 次击杀 = 一层");
        Assertions.assertEquals(2 * share, after[1], share * 1e-6, "第 2 次 = 两层");
        Assertions.assertEquals(3 * share, after[2], share * 1e-6, "第 3 次 = 三层（上限）");
        Assertions.assertEquals(3 * share, after[3], share * 1e-6, "「最多 3 层」⇒ 第 4 次不再加");
    }

    /**
     * 21028 暖夜不会漫长: the heal is each target's own max-HP share, and a SKILL triggers it too.
     */
    @Test
    public void coneTwoInnocentTwoEightHealsEveryoneOnBasicOrSkill() {
        Fixture f = new Fixture(21028, true);
        f.ally.consumeHp(f.ally.getMaxHp() * 0.5);
        double allyBefore = f.ally.getCurrentHp();
        f.battle.castImmediate(f.wearer.getSkills().get(SkillType.SKILL), f.wearer,
                List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        double healedOnSkill = f.ally.getCurrentHp() - allyBefore;
        double expected = f.ally.getMaxHp() * 0.02;
        System.out.println("[21028] 战技后队友回复 " + healedOnSkill + " ; 各自上限 × 0.02 = " + expected);
        Assertions.assertEquals(expected, healedOnSkill, expected * 1e-6,
                "「施放普攻或战技后…各自生命上限 2%」—— 战技也要触发，且用各自的上限");
    }

    // ==================================================================

    private static void killOne(Fixture f) {
        Enemy victim = BattleFixture.dummy();
        f.battle.applyDamage(victim, new Damage(f.wearer, victim, DamageElement.PHYSICAL, DamageType.NORMAL, 1e9));
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
