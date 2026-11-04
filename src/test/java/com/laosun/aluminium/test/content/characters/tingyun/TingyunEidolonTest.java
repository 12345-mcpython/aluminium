package com.laosun.aluminium.test.content.characters.tingyun;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1202 停云's technique and the three Eidolons the audit found writable: the blessed ally's speed after its Ultimate (E1),
 * the blessed ally's energy on a kill (E2), and the Ultimate's extra 10 energy (E6).
 */
public class TingyunEidolonTest {
    private static final int TINGYUN = 1202;
    private static final int LEVEL = 80;
    private static final String BLESSING = "赐福";

    /** 秘技:「使用秘技后立即为自身恢复 50 点能量」 - and nothing happens when the technique was not declared. */
    @Test
    public void theTechniquePaysFiftyEnergy() {
        Character with = CharacterFactory.create(TINGYUN, LEVEL);
        with.getBuffManager().addBuff(new StateBuff("秘技", 1));
        Character without = CharacterFactory.create(TINGYUN, LEVEL);
        double withEnergy = energyAfterOpening(with);
        double withoutEnergy = energyAfterOpening(without);
        Assertions.assertEquals(50.0, withEnergy - withoutEnergy, 1e-9, "「立即为自身恢复 50 点能量」");
    }

    /** 星魂 1:「得到【赐福】的我方单体施放终结技后速度提高 20%，持续 1 回合」. */
    @Test
    public void eidolonOneSpeedsUpTheBlessedCaster() {
        Assertions.assertEquals(0.2, blessedUltSpeedDelta(true), 1e-9,
                "the blessed caster is 20% faster after its Ultimate");
        Assertions.assertEquals(0.0, blessedUltSpeedDelta(false), 1e-9,
                "and an unblessed caster gets nothing (the condition is the whole clause)");
        Assertions.assertEquals(0.0, unblessedUltSpeedDeltaAt(0), 1e-9, "and nothing at E0");
    }

    /** 星魂 2:「得到【赐福】的我方单体在消灭敌方目标时恢复 5 点能量，该效果每回合只能触发 1 次」. */
    @Test
    public void eidolonTwoPaysOncePerTurn() {
        Character tingyun = CharacterFactory.create(TINGYUN, LEVEL, false, null, null, 2);
        Character ally = CharacterFactory.create(1001, LEVEL);
        ally.getBuffManager().addBuff(new StateBuff(BLESSING, 3));
        Enemy first = BattleFixture.monster();
        Enemy second = BattleFixture.monster();
        Battle battle = new Battle(List.of(tingyun, ally), List.of(first, second), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double before = ally.getCurrentEnergy();

        fireKill(battle, ally, first);
        double afterFirst = ally.getCurrentEnergy();
        fireKill(battle, ally, second);
        double afterSecond = ally.getCurrentEnergy();

        Assertions.assertEquals(5.0, afterFirst - before, 1e-9, "the first kill in the turn pays 5");
        Assertions.assertEquals(afterFirst, afterSecond, 1e-9,
                "「该效果每回合只能触发 1 次」 — the second kill in the same turn pays nothing");
    }

    /** 星魂 6:「终结技为我方目标恢复的能量提高 10 点」 — 60 rather than 50. */
    @Test
    public void eidolonSixPaysSixty() {
        Assertions.assertEquals(60.0, ultEnergy(6), 1e-9, "E6 pays the base 50 plus 10");
        Assertions.assertEquals(50.0, ultEnergy(5), 1e-9, "and E5 pays the base 50");
    }

    /**
     * 战技:「获得【赐福】的目标施放攻击后，会额外造成 1 次等同于**其自身** 40% 攻击力的雷属性附加伤害」.
     *
     * <p>The clause's whole difficulty is <b>whose</b> attack the instance is: it is the blessed ALLY's, so the engine's
     * {@code attacker: "actor"} resolution decides both the attribution (asserted here) and which ATK the share reads
     * (asserted by the ATK ratio between two fixtures that differ only in the caster's ATK).
     */
    @Test
    public void theBlessedAlliesAttackCarriesHerOwnShare() {
        double[] small = extraDamageAtAlliedAttack(1_000);
        double[] large = extraDamageAtAlliedAttack(5_000);

        Assertions.assertEquals(1, (int) small[0], "precondition: exactly one ADDITIONAL instance lands");
        Assertions.assertEquals(1, (int) large[0], "precondition: and one in the stronger fixture too");
        Assertions.assertTrue(small[1] > 0, "precondition: the instance deals damage: " + small[1]);
        Assertions.assertEquals(5.0, large[1] / small[1], 1e-6,
                "「等同于其自身 40% 攻击力」 — the share follows the ALLY's ATK (5000/1000), not Tingyun's: "
                        + small[1] + " vs " + large[1]);

        double unblessed = extraDamageAtAlliedAttack(1_000, false)[1];
        Assertions.assertEquals(0.0, unblessed, 1e-9,
                "control: without 【赐福】 the ally's attack carries nothing extra");
    }

    /**
     * @return {how many ADDITIONAL instances landed, the HP they removed}
     */
    private static double[] extraDamageAtAlliedAttack(double allyAttack) {
        return extraDamageAtAlliedAttack(allyAttack, true);
    }

    private static double[] extraDamageAtAlliedAttack(double allyAttack, boolean blessed) {
        Character tingyun = CharacterFactory.create(TINGYUN, LEVEL);
        Character ally = CharacterFactory.create(1001, LEVEL);
        ally.setAttribute(AttributeType.ATTACK, new com.laosun.aluminium.DoubleValue(allyAttack));
        if (blessed) {
            ally.getBuffManager().addBuff(new StateBuff(BLESSING, 3));
        }
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(tingyun, ally), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        int[] additional = {0};
        double[] removed = {0};
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> {
            if (damage.getType() == com.laosun.aluminium.enums.DamageType.ADDITIONAL) {
                additional[0]++;
                Assertions.assertSame(ally, damage.getAttacker(),
                        "the instance belongs to the blessed ALLY, not to Tingyun");
                removed[0] += applied;
            }
        });
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.ALLY_ATTACK, ally, enemy, 1, 0));
        battle.processRequests();
        return new double[]{additional[0], removed[0]};
    }

    private static double energyAfterOpening(Character tingyun) {
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(tingyun), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return tingyun.getCurrentEnergy();
    }

    /** The blessed ally's SPEED after its own Ultimate, with or without 【赐福】. */
    private static double blessedUltSpeedDelta(boolean blessed) {
        Character tingyun = CharacterFactory.create(TINGYUN, LEVEL, false, null, null, 1);
        Character ally = CharacterFactory.create(1001, LEVEL);
        if (blessed) {
            ally.getBuffManager().addBuff(new StateBuff(BLESSING, 3));
        }
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(tingyun, ally), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double before = ally.getAttribute(AttributeType.SPEED).get();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.ULT_CAST, ally, enemy, 1, 0));
        battle.processRequests();
        return (ally.getAttribute(AttributeType.SPEED).get() - before) / before;
    }

    /** The same measurement at a given Eidolon rank with no blessing at all (the E0 control). */
    private static double unblessedUltSpeedDeltaAt(int eidolon) {        Character tingyun = CharacterFactory.create(TINGYUN, LEVEL, false, null, null, eidolon);
        Character ally = CharacterFactory.create(1001, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(tingyun, ally), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double before = ally.getAttribute(AttributeType.SPEED).get();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.ULT_CAST, ally, enemy, 1, 0));
        battle.processRequests();
        return (ally.getAttribute(AttributeType.SPEED).get() - before) / before;
    }

    /** The energy the Ultimate hands the ally, at a given Eidolon rank. */
    private static double ultEnergy(int eidolon) {
        Character tingyun = CharacterFactory.create(TINGYUN, LEVEL, false, null, null, eidolon);
        Character ally = CharacterFactory.create(1001, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(tingyun, ally), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double before = ally.getCurrentEnergy();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.ULT_CAST, tingyun, ally, 1, 0));
        battle.processRequests();
        return ally.getCurrentEnergy() - before;
    }

    private static void fireKill(Battle battle, Character killer, Enemy victim) {
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.KILL, killer, victim, 0, 0));
        battle.processRequests();
    }
}
