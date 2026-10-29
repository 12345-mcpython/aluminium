package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.damage.Damage;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The toughness damage reduction: 「敌对物种最常见的减伤是**韧性条不为0时10%的减伤**。而该减伤**当且仅当**敌人处于弱点击破状态时被移除。
 * 注意**击破伤害一定会受该减伤影响**。」 (the author's ruling, 2026-10-07).
 *
 * <p>It is a <b>member of the reduction zone</b>, not a zone of its own - 「减伤区是各乘区中唯一一个叠加乘算的，即 Π(1-减伤)，
 * 目前游戏中的减伤的叠加上限为 0.99」 - which is why {@code Battle.assemble} adds it with {@code addReduction} beside the
 * victim's own reduction buffs, and why it multiplies with them instead of summing.
 *
 * <p>What this file pins, each measured on the SAME instance so the other zones cancel:
 * <ul>
 *   <li>an unbroken enemy with a toughness bar takes exactly 10% more damage once broken (ratio 1/0.9);</li>
 *   <li>an enemy with NO toughness bar never takes it (「韧性条不为0」 is the premise, and a bar-less enemy is not "not 0");</li>
 *   <li>the BREAK instance takes it even though the bar is already empty by then - 「击破伤害一定会受该减伤影响」 - so a break
 *       instance settles at 0.9 of the same instance settled as ordinary damage;</li>
 *   <li>and it MULTIPLIES: two reductions (this one and a 10% buff) come out 0.81, not 0.80, with the 0.01 floor still on top.</li>
 * </ul>
 */
public class ToughnessDamageReductionTest {
    private static final int ICE_EDGE = 1002011;      // a stage monster: it HAS a toughness bar
    private static final double BASE = 1000.0;
    private static final double EPS = 1e-9;

    /**
     * Breaking the bar removes the reduction: the same instance settles 1/0.9 higher.
     */
    @Test
    public void breakingTheBarRemovesIt() {
        Enemy unbroken = enemy();
        Enemy broken = enemy();
        broken.breakEnemy(DamageElement.ICE);
        Assertions.assertTrue(broken.isBroken(), "precondition: the second enemy's bar is empty");

        double withReduction = settle(unbroken, DamageType.NORMAL);
        double without = settle(broken, DamageType.NORMAL);
        System.out.println("[toughness_reduction] unbroken = " + withReduction + " ; broken = " + without
                + " ; ratio = " + (without / withReduction));
        Assertions.assertEquals(1 / (1 - Constant.TOUGHNESS_DAMAGE_REDUCTION), without / withReduction, 1e-9,
                "「该减伤当且仅当敌人处于弱点击破状态时被移除」: breaking the bar gives back exactly the 10%");
    }

    /**
     * An enemy with no toughness bar at all never takes it - the sentence's premise is 「韧性条不为0」.
     */
    @Test
    public void anEnemyWithoutAToughnessBarNeverTakesIt() {
        Enemy dummy = Enemy.fromAttributes("dummy", 1_000_000, 0, 100, 100);
        Assertions.assertFalse(dummy.hasToughnessBar(), "precondition: this dummy has no toughness bar");
        Battle battle = battleWith(dummy);

        double settled = battle.applyDamage(dummy, new Damage(battle.characters().getFirst(), dummy,
                DamageElement.ICE, DamageType.NORMAL, BASE));
        double defenceZone = 1000.0 / (dummy.getAttribute(AttributeType.DEFENCE).get() + 1000.0);
        System.out.println("[toughness_reduction] bar-less dummy = " + settled + " ; expected without it = "
                + (BASE * defenceZone));
        Assertions.assertEquals(BASE * defenceZone, settled, EPS,
                "a bar-less enemy keeps the whole instance: the reduction is about a bar that is not empty");
    }

    /**
     * 「击破伤害一定会受该减伤影响」: the BREAK instance keeps it even though the bar emptied first.
     */
    @Test
    public void theBreakInstanceAlwaysTakesIt() {
        Enemy enemy = enemy();
        Battle battle = battleWith(enemy);
        enemy.breakEnemy(DamageElement.ICE);

        double asBreak = battle.applyDamage(enemy, new Damage(battle.characters().getFirst(), enemy,
                DamageElement.ICE, DamageType.BREAK, BASE));
        // A fresh enemy for the ordinary instance: `applyDamage` is the one place that reads the state, so the same
        // enemy can serve both readings - but its HP would move, so a second one keeps the comparison clean.
        Enemy twin = enemy();
        twin.breakEnemy(DamageElement.ICE);
        Battle twinBattle = battleWith(twin);
        double asNormal = twinBattle.applyDamage(twin, new Damage(twinBattle.characters().getFirst(), twin,
                DamageElement.ICE, DamageType.NORMAL, BASE));

        System.out.println("[toughness_reduction] break instance on a broken enemy = " + asBreak
                + " ; ordinary = " + asNormal + " ; ratio = " + (asBreak / asNormal));
        Assertions.assertEquals(1 - Constant.TOUGHNESS_DAMAGE_REDUCTION, asBreak / asNormal, 1e-9,
                "break damage 「一定会受该减伤影响」, so it settles 0.9 against the ordinary instance beside it");
    }

    /**
     * And it MULTIPLIES with the victim's other reductions - Π(1-r), with the 0.01 floor on top.
     */
    @Test
    public void itMultipliesWithOtherReductions() {
        Enemy unbroken = enemy();
        Battle battle = battleWith(unbroken);
        Damage plain = new Damage(battle.characters().getFirst(), unbroken, DamageElement.ICE, DamageType.NORMAL, BASE);
        Damage withBuff = new Damage(battle.characters().getFirst(), unbroken, DamageElement.ICE, DamageType.NORMAL, BASE);
        withBuff.addReduction(0.1);         // a 10% "受到伤害降低" buff, the sentence's own example

        double one = battle.applyDamage(unbroken, plain);
        Enemy twin = enemy();
        Battle twinBattle = battleWith(twin);
        Damage twinPlain = new Damage(twinBattle.characters().getFirst(), twin, DamageElement.ICE, DamageType.NORMAL, BASE);
        twinPlain.addReduction(0.1);
        double both = twinBattle.applyDamage(twin, twinPlain);
        System.out.println("[toughness_reduction] toughness only = " + one + " ; plus a 10% buff = " + both
                + " ; ratio = " + (both / one));
        Assertions.assertEquals(1 - 0.1, both / one, 1e-9,
                "the two reductions multiply (0.9 x 0.9 = 0.81), they do not add up to 0.8");

        // The floor belongs to the zone, not to this member: 「目前游戏中的减伤的叠加上限为 0.99」.
        Damage heavy = new Damage(battle.characters().getFirst(), unbroken, DamageElement.ICE, DamageType.NORMAL, BASE);
        heavy.addReduction(0.9999);
        Enemy third = enemy();
        Battle thirdBattle = battleWith(third);
        Damage thirdPlain = new Damage(thirdBattle.characters().getFirst(), third, DamageElement.ICE,
                DamageType.NORMAL, BASE);
        thirdPlain.addReduction(0.9999);
        double floored = thirdBattle.applyDamage(third, thirdPlain);
        System.out.println("[toughness_reduction] with a 99.99% buff on top = " + floored + " ; ratio to the toughness-only"
                + " reading = " + (floored / one));
        // ⚠ The floor is the ZONE's, so the toughness member sits inside it: the reading is `one` (which is already 0.9 of
        // the raw instance) times 0.01/0.9, i.e. the whole reduction zone pinned at 0.01.
        Assertions.assertEquals(Constant.REDUCTION_MIN / (1 - Constant.TOUGHNESS_DAMAGE_REDUCTION), floored / one, EPS,
                "a reduction over the cap pins the whole reduction ZONE at its 0.01 floor (the toughness member inside it)");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * One enemy of the fixed kind, on its own battlefield, plus the attacker those readings share.
     */
    private static Enemy enemy() {
        return EnemyFactory.create(ICE_EDGE, 90, 1);
    }

    private static Battle battleWith(Enemy enemy) {
        // A plain attacker at Lv80 (ATK 100 / DEF 100, like the other zone cases) and a source that never crits: the
        // instance under test is the BASE only, so every reading differs in the reduction and nothing else.
        Character attacker = Character.fromAttributes("attacker", 1000, 100, 100, 100);
        Battle battle = new Battle(List.of(attacker), List.of(enemy), new NoCrit());
        return battle;
    }

    /**
     * The settled value of one instance of {@link #BASE} against {@code enemy}, of the given type.
     */
    private static double settle(Enemy enemy, DamageType type) {
        Battle battle = battleWith(enemy);
        return battle.applyDamage(enemy, new Damage(battle.characters().getFirst(), enemy, DamageElement.ICE, type, BASE));
    }

    /**
     * A source that never crits. The instances here are tiny, but a crit would move one reading and not another - and
     * every case in this file is a RATIO between two readings, so a crit anywhere would break the comparison.
     */
    private static final class NoCrit extends Random {
        @Override
        public double nextDouble() {
            return 0.9999;
        }

        @Override
        public int nextInt(int bound) {
            return super.nextInt(bound);
        }
    }
}
