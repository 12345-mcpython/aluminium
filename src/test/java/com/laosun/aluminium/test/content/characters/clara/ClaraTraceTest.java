package com.laosun.aluminium.test.content.characters.clara;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DebuffClass;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.buff.ClassResistBuff;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TestTurns;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 110 Clara (克拉拉): her Family (家人) trace, the first shipped rule that combines a <b>probability</b> with a
 * <b>dispel</b>.
 *
 * <p>"受到攻击时有 35% 的固定概率解除自身 1 个负面效果" is three things at once - an event that fires on every hit
 * she takes, a probability, and the removal of one negative effect - and all three had to exist before the rule
 * could be written. Her file already held her counter talent, so this also keeps pinning "one character, several
 * mechanics, one table".
 *
 * <p>The generator is fixed in every case below, so the 35% is asserted rather than gambled: with a generator
 * that answers {@code 0.1} the roll passes, with {@code 0.9} it fails. That is the same injected-generator rule
 * the engine follows everywhere.
 *
 * <p><b>L3:</b> the clauses that share one fixture are grouped into the scenarios below, each assertion inside
 * {@link Assertions#assertAll} keeping its message that names the clause. The {@code 0.9} board is read once - the
 * failed roll leaves the burn, and the same hit still lets the counter talent fire - and the 守护 trace's value is read
 * before and after two of her own turns on the one board. Nothing was dropped: every expected value, tolerance and
 * message the separate cases carried is still asserted.
 */
public class ClaraTraceTest {
    private static final int CLARA = 1107;
    private static final int LEVEL = 80;

    @Test
    public void herFamilyTraceDispelsADebuffWhenTheRollPasses() {
        Battle battle = battleWith(fixed(0.1));
        Character clara = battle.characters().getFirst();
        clara.getBuffManager().addBuff(new DotBuff(clara, DamageElement.FIRE, 100, 3));
        clara.getBuffManager().addBuff(new StateBuff("char:1309/concerto", 3));

        hit(battle, clara);

        Assertions.assertAll("「受到攻击时有 35% 的固定概率解除自身 1 个负面效果」 - the roll passes",
                () -> Assertions.assertFalse(clara.getBuffManager().hasBuff(DotBuff.class),
                        "the burn is a negative effect, so dispelling (解除) takes it"),
                () -> Assertions.assertTrue(clara.getBuffManager().hasBuff(StateBuff.class),
                        "and the named state stays: it is not classified as negative"));
    }

    /**
     * The same hit with the roll failing (0.9): the trace takes nothing off, and her counter talent - a different rule
     * in the same file - still answers the attack.
     */
    @Test
    public void aFailedRollLeavesTheDebuffAndTheTalentStillCounters() {
        Battle battle = battleWith(fixed(0.9));
        Character clara = battle.characters().getFirst();
        Enemy enemy = battle.enemyUnits().getFirst();
        clara.getBuffManager().addBuff(new DotBuff(clara, DamageElement.FIRE, 100, 3));
        double before = enemy.getCurrentHp();

        hit(battle, clara);

        boolean debuffKept = clara.getBuffManager().hasBuff(DotBuff.class);
        boolean counterFired = enemy.getCurrentHp() < before;

        Assertions.assertAll("a failed Family roll on a hit that still counters",
                () -> Assertions.assertTrue(debuffKept,
                        "0.9 is not below 0.35, so nothing is dispelled -- and the same hit still happened"),
                () -> Assertions.assertTrue(counterFired,
                        "「受到攻击后反击」 (counterattacks after being attacked) is a different rule in the same file, and it must survive the addition"));
    }

    /**
     * Her Guardian (守护) trace: "抵抗控制类负面状态的概率提高35%" - a resistance to a whole <b>class</b>, from the real file.
     *
     * <p>The number is read back off the character the loader built, which is the point: the op installs a permanent
     * 35% control resistance at BATTLE_START, and nothing else in the file changed. Asserting it here rather than
     * only in {@code DebuffResistTest} is what keeps the <b>content</b> honest - the engine op can be perfect and the
     * file still say the wrong percentage or the wrong class.
     *
     * <p>...and it is permanent, which is what a trace (行迹) is: two of her own turns do not spend it, and the same
     * value is read again off the same board after them.
     */
    @Test
    public void herGuardianTraceResistsControlAndIsNotSpentByTurns() {
        Battle battle = battleWith(fixed(0.9));
        Character clara = battle.characters().getFirst();

        double control = clara.getBuffManager().debuffResistOf(DebuffClass.CONTROL);
        int contributions = clara.getBuffManager().countBuffs(ClassResistBuff.class);
        double dot = clara.getBuffManager().debuffResistOf(DebuffClass.DOT);
        int debuffs = clara.getBuffManager().debuffCount();

        TestTurns.take(battle, clara);
        TestTurns.take(battle, clara);
        double afterTurns = clara.getBuffManager().debuffResistOf(DebuffClass.CONTROL);

        Assertions.assertAll("the 守护 trace: a permanent 35% control resistance",
                () -> Assertions.assertEquals(0.35, control, 1e-9,
                        "「抵抗控制类负面状态的概率提高35%」 (raises RES to Crowd Control debuffs by 35%)"),
                () -> Assertions.assertEquals(1, contributions, "one contribution"),
                () -> Assertions.assertEquals(0, dot,
                        "Guardian (守护) says control class (控制类) and nothing about the DOT class (持续伤害类)"),
                () -> Assertions.assertEquals(0, debuffs,
                        "being harder to control is good for her: a dispel-negative (解除负面) must not take it off"),
                () -> Assertions.assertEquals(0.35, afterTurns, 1e-9,
                        "a trace states no duration, so 'permanent': true — the buff is never ticked"));
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static void hit(Battle battle, Character victim) {
        battle.applyDamage(victim, new Damage(battle.enemyUnits().getFirst(), victim,
                DamageElement.ICE, 100));
    }

    private static Battle battleWith(Random rng) {
        Character clara = CharacterFactory.create(CLARA, LEVEL);
        Battle battle = new Battle(List.of(clara), List.of(dummy()), rng);
        battle.startBattle();
        return battle;
    }

    /**
     * A generator that always answers the same value (see {@code TriggerChanceTest.fixed}).
     */
    private static Random fixed(double value) {
        return BattleFixture.alwaysRoll(value);
    }

    private static Enemy dummy() {
        return Enemy.fromAttributes("dummy", 1_000_000, 0, 100, 100);
    }
}
