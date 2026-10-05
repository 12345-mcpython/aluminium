package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DebuffClass;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.buff.ClassResistBuff;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 110克拉拉 (Clara): her 家人 trace, the first shipped rule that combines a <b>probability</b> with a
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
 */
public class ClaraTraceTest {
    private static final int CLARA = 1107;
    private static final int LEVEL = 80;

    @Test
    public void herFamilyTraceDispelsADebuffWhenTheRollPasses() {
        Battle battle = battleWith(fixed(0.1));
        Character clara = battle.characters.getFirst();
        clara.getBuffManager().addBuff(new DotBuff(clara, DamageElement.FIRE, 100, 3));
        clara.getBuffManager().addBuff(new StateBuff("协奏", 3));

        hit(battle, clara);

        Assertions.assertFalse(clara.getBuffManager().hasBuff(DotBuff.class),
                "the burn is a negative effect, so 解除 takes it");
        Assertions.assertTrue(clara.getBuffManager().hasBuff(StateBuff.class),
                "and the named state stays: it is not classified as negative");
    }

    @Test
    public void aFailedRollLeavesTheDebuffInPlace() {
        Battle battle = battleWith(fixed(0.9));
        Character clara = battle.characters.getFirst();
        clara.getBuffManager().addBuff(new DotBuff(clara, DamageElement.FIRE, 100, 3));

        hit(battle, clara);

        Assertions.assertTrue(clara.getBuffManager().hasBuff(DotBuff.class),
                "0.9 is not below 0.35, so nothing is dispelled -- and the same hit still happened");
    }

    /** Her counter talent (already in the file) still works next to the new trace. */
    @Test
    public void herCounterTalentStillFires() {
        Battle battle = battleWith(fixed(0.9));
        Character clara = battle.characters.getFirst();
        Enemy enemy = battle.enemyUnits().getFirst();
        double before = enemy.getCurrentHp();

        hit(battle, clara);

        Assertions.assertTrue(enemy.getCurrentHp() < before,
                "「受到攻击后反击」 is a different rule in the same file, and it must survive the addition");
    }

    /**
     * Her 守护 trace: "抵抗控制类负面状态的概率提高35%" - a resistance to a whole <b>class</b>, from the real file.
     *
     * <p>The number is read back off the character the loader built, which is the point: the op installs a permanent
     * 35% control resistance at BATTLE_START, and nothing else in the file changed. Asserting it here rather than
     * only in {@code DebuffResistTest} is what keeps the <b>content</b> honest - the engine op can be perfect and the
     * file still say the wrong percentage or the wrong class.
     */
    @Test
    public void herGuardianTraceResistsTheControlClass() {
        Battle battle = battleWith(fixed(0.9));
        Character clara = battle.characters.getFirst();

        Assertions.assertEquals(0.35, clara.getBuffManager().debuffResistOf(DebuffClass.CONTROL), 1e-9,
                "「抵抗控制类负面状态的概率提高35%」");
        Assertions.assertEquals(1, clara.getBuffManager().countBuffs(ClassResistBuff.class), "one contribution");
        Assertions.assertEquals(0, clara.getBuffManager().debuffResistOf(DebuffClass.DOT),
                "守护 says 控制类 and nothing about 持续伤害类");
        Assertions.assertEquals(0, clara.getBuffManager().debuffCount(),
                "being harder to control is good for her: a 解除负面 must not take it off");
    }

    /** …and it is permanent, which is what a 行迹 is: two of her own turns do not spend it. */
    @Test
    public void herGuardianTraceIsNotSpentByTurns() {
        Battle battle = battleWith(fixed(0.9));
        Character clara = battle.characters.getFirst();

        TestTurns.take(battle, clara);
        TestTurns.take(battle, clara);

        Assertions.assertEquals(0.35, clara.getBuffManager().debuffResistOf(DebuffClass.CONTROL), 1e-9,
                "a trace states no duration, so 'permanent': true — the buff is never ticked");
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

    /** A generator that always answers the same value (see {@code TriggerChanceTest.fixed}). */
    private static Random fixed(double value) {
        return new Random() {
            @Override
            public double nextDouble() {
                return value;
            }
        };
    }

    private static Enemy dummy() {
        return Enemy.fromAttributes("dummy", 1_000_000, 0, 100, 100);
    }
}
