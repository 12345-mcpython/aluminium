package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.buff.AbstractBuff;
import com.laosun.aluminium.buff.ControlBuff;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.buff.StatModifierBuff;
import com.laosun.aluminium.buff.StunBuff;
import com.laosun.aluminium.buff.TauntBuff;
import com.laosun.aluminium.buff.VulnerabilityBuff;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Light cone 21001: "敌方目标每承受 1 个负面效果，装备者对其造成的伤害提高 #1%，最多叠加 #2 层" at superimposition 5 (24% x 3).
 *
 * <p>The expectation is built from the count the ENGINE reports, because that is what the clause counts -- and a fixture cannot assume how many debuffs a given
 * buff class contributes (measured: a `ControlBuff` contributes two).
 *
 * <p>Also covers 21001 晚安与睡颜 at superimposition 1 on the {@code BoostArea} factor: 「敌方目标每承受 1 个负面效果，装备者对其造成的伤害
 * 提高 12%，最多叠加 3 层。该效果对持续伤害也会生效」. The cone's 12% is one additive zone factor, so the judge can pin 0.12 / 0.24 / 0.36
 * exactly even though the final number also carries defence and resistance. The debuffs are non-damage attributes (SPEED / effect
 * resistance / effect hit rate / defence), so only the cone's per-stack term changes between runs.
 */
public class GoodNightAndSleepWellTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final double SHARE = 0.24;
    private static final int CAP = 3;
    // From the merged GoodNightAndSleepWellConeTest: its wearer is a DIFFERENT character id (the old class's WEARER, renamed),
    // and it reads the cone at superimposition 1 instead of 5.
    private static final int CONE_WEARER = 1402;
    private static final int CONE = 21001;
    private static final double EPS = 1e-9;

    @Test
    public void theConeScalesPerDebuffAndCapsAtThree() {
        double clean = settled(0);
        for (int fixture = 1; fixture <= 4; fixture++) {
            double[] pair = settledWithCount(fixture);
            double ratio = pair[0] / clean;
            int count = (int) pair[1];
            double expected = 1 + Math.min(count, CAP) * SHARE;
            System.out.println("[21001] fixture=" + fixture + " engineCount=" + count
                    + " ratio=" + ratio + " expected=" + expected);
            Assertions.assertEquals(expected, ratio, 3e-2,
                    "count " + count + " should scale by min(count, 3) x 0.24");
        }
        Assertions.assertEquals(1 + CAP * SHARE, settledWithCount(3)[0] / clean, 3e-2, "the cap is reached");
    }

    private static double settled(int debuffs) {
        return settledWithCount(debuffs)[0];
    }

    private static double[] settledWithCount(int debuffs) {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21001, LEVEL, false, 5));
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        if (debuffs >= 1) {
            enemy.getBuffManager().addBuff(new DotBuff(unit, DamageElement.FIRE, 100, 3));
        }
        if (debuffs >= 2) {
            enemy.getBuffManager().addBuff(new ControlBuff(Constant.CONTROL_EFFECTS.get("IMPRISONED"), 3));
        }
        if (debuffs >= 3) {
            enemy.getBuffManager().addBuff(new ControlBuff(Constant.CONTROL_EFFECTS.get("FROZEN"), 3));
        }
        int count = enemy.getBuffManager().debuffCount();
        double settled = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.QUANTUM, DamageType.NORMAL, 1000));
        return new double[]{settled, count};
    }

    /**
     * The per-debuff boost, its three-stack cap, and the cap boundary.
     */
    @Test
    public void theBoostIsTwelvePercentPerDebuffUpToThree() {
        double zero = boostWith(0);
        Assertions.assertEquals(0.12, boostWith(1) - zero, EPS, "1 debuff");
        Assertions.assertEquals(0.24, boostWith(2) - zero, EPS, "2 debuffs");
        Assertions.assertEquals(0.36, boostWith(3) - zero, EPS, "3 debuffs");
        Assertions.assertEquals(0.36, boostWith(4) - zero, EPS, "the cap is 3 stacks");
    }

    private static double boostWith(int debuffs) {
        Character wearer = CharacterFactory.create(CONE_WEARER, LEVEL, false, Weapon.build(CONE, LEVEL, false, 1));
        Battle battle = new Battle(List.of(wearer), List.of(BattleFixture.dummy()),
                BattleFixture.alwaysRoll(0.9999));
        battle.startBattle();
        battle.processRequests();

        var enemy = battle.enemies().getFirst();
        AbstractBuff[] sources = {
                new StunBuff(2),
                new TauntBuff(2),
                new VulnerabilityBuff(2, 0.01),
                StatModifierBuff.percentDebuff(com.laosun.aluminium.enums.AttributeType.SPEED, -0.1, 2)
        };
        for (int i = 0; i < debuffs; i++) {
            enemy.getBuffManager().addBuff(sources[i]);
        }

        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        battle.castImmediate(wearer.getSkills().get(SkillType.COMMON), wearer, List.of(enemy));
        battle.processRequests();
        return settled.getFirst().breakdown().get("BoostArea");
    }
}
