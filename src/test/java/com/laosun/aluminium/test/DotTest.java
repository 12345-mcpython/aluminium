package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.utils.AttributeBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * P4-5 acceptance: break DOT (first applied first settled, settled at the start of the turn, goes
 * through the zones but cannot crit).
 *
 * <p>Anchor: a target with 100 defence, an Lv80 attacker (defence zone = 1000/1100), no resistance
 * and no DMG boost → one DOT with base=500 settles 500 × 1000/1100 ≈ 454.55.
 *
 * <p>Since the DOT became an ordinary buff, settling and counting down are <b>two</b> steps in two
 * different objects: {@code Battle.tickDots} deals the damage, {@code BuffManager.beforeMove()} burns
 * the turn off. Tests that assert expiry therefore drive both, in the order
 * {@code Battle.beforeMove()} drives them — the split is the point of the design, so it is visible in
 * the tests rather than hidden behind a helper that would hide a wrong order too.
 */
public class DotTest {
    private static final double EPS = 1e-6;

    @Test
    public void dotTicksAtTurnStartAndExpiresAfterItsTurns() {
        Character source = character("source", 0.0);
        Enemy dummy = dummy(100_000, 100, 100);
        Battle battle = new Battle(List.of(source), List.of(dummy), new Random(0));
        dummy.getBuffManager().addBuff(new DotBuff(source, DamageElement.FIRE, 500, 2));

        double expected = 500 * 1000.0 / (100 + 1000.0);
        double first = battle.tickDots(dummy);
        Assertions.assertEquals(expected, first, 1e-6);
        Assertions.assertEquals(1, dummy.getBuffManager().countBuffs(DotBuff.class),
                "settling alone does not consume a turn -- the manager's tick does that");

        dummy.getBuffManager().beforeMove();                    // the other half, exactly as Battle.beforeMove does it
        Assertions.assertEquals(1, dummy.getBuffManager().countBuffs(DotBuff.class), "1 settlement left");

        double second = battle.tickDots(dummy);
        Assertions.assertEquals(expected, second, 1e-6);
        dummy.getBuffManager().beforeMove();
        Assertions.assertTrue(dummy.getBuffManager().allBuffsOf(DotBuff.class).isEmpty(),
                "it is removed once the last settlement is done");

        Assertions.assertEquals(0, battle.tickDots(dummy), EPS, "no DOTs left → 0");
        Assertions.assertEquals(dummy.getMaxHp() - 2 * expected, dummy.getCurrentHp(), 1e-6);
    }

    @Test
    public void dotsSettleInApplicationOrder() {
        Character source = character("source", 0.0);
        List<DamageElement> seen = new ArrayList<>();
        Enemy recorder = recorder(100_000, 100, 100, seen);
        Battle battle = new Battle(List.of(source), List.of(recorder), new Random(0));
        recorder.getBuffManager().addBuff(new DotBuff(source, DamageElement.THUNDER, 100, 1));
        recorder.getBuffManager().addBuff(new DotBuff(source, DamageElement.FIRE, 100, 1));

        battle.tickDots(recorder);

        Assertions.assertEquals(List.of(DamageElement.THUNDER, DamageElement.FIRE), seen, "first applied, first settled");
    }

    @Test
    public void theSameElementStacksInsteadOfRefreshing() {
        Character source = character("source", 0.0);
        Enemy dummy = dummy(100_000, 100, 100);
        Battle battle = new Battle(List.of(source), List.of(dummy), new Random(0));
        dummy.getBuffManager().addBuff(new DotBuff(source, DamageElement.FIRE, 500, 3));
        dummy.getBuffManager().addBuff(new DotBuff(source, DamageElement.FIRE, 500, 3));

        Assertions.assertEquals(2, dummy.getBuffManager().countBuffs(DotBuff.class),
                "a second burn is a second instance, not a refresh of the first");

        // Both settle, and neither was silently dropped in favour of the other.
        double expected = 2 * 500 * 1000.0 / (100 + 1000.0);
        Assertions.assertEquals(expected, battle.tickDots(dummy), 1e-6);
    }

    @Test
    public void dotIsBoostedButNeverCrits() {
        Character source = character("source", 1.0);          // DMG boost +100%
        Enemy dummy = dummy(100_000, 100, 100);
        Battle battle = new Battle(List.of(source), List.of(dummy), new Random(0));
        dummy.getBuffManager().addBuff(new DotBuff(source, DamageElement.FIRE, 500, 1));

        double settled = battle.tickDots(dummy);

        Assertions.assertEquals(500 * 2 * 1000.0 / (100 + 1000.0), settled, 1e-6, "DOT takes DMG boost");
        Assertions.assertFalse(DamageType.DOT.isCrittable());
        Assertions.assertTrue(DamageType.DOT.isBoostable());
    }

    @Test
    public void breakingWithFireAttachesABurn() {
        Character himeko = character("himeko", 0.0);
        Enemy iceEdge = EnemyFactory.create(1002011, 90, 1);   // weak to Fire, toughness 60
        Battle battle = new Battle(List.of(himeko), List.of(iceEdge), new Random(0));

        battle.castImmediate(new DefaultSkill(1003, 1, 1), himeko, List.of(iceEdge));
        battle.castImmediate(new DefaultSkill(1003, 1, 1), himeko, List.of(iceEdge));   // drains it empty → break

        Assertions.assertTrue(iceEdge.isBroken());
        Assertions.assertEquals(1, iceEdge.getBuffManager().countBuffs(DotBuff.class));
        DotBuff dot = iceEdge.getBuffManager().allBuffsOf(DotBuff.class).getFirst();
        Assertions.assertEquals(DamageElement.FIRE, dot.getElement());
        Assertions.assertEquals(himeko, dot.getSource());
        Assertions.assertEquals(Constant.DOT_TURNS, dot.duration());
        Assertions.assertEquals(376.75535 * Constant.DOT_RATIO, dot.getBaseDamage(), 1e-6);
    }

    @Test
    public void breakingWithAFrozenElementAttachesNoDot() {
        Character mar7th = character("mar7th", 0.0);
        Enemy iceEdge = EnemyFactory.create(1002011, 90, 1);
        iceEdge.setStanceWeak(Set.of(DamageElement.ICE));      // temporarily made Ice-weak, so that the Ice element can break it
        Battle battle = new Battle(List.of(mar7th), List.of(iceEdge), new Random(0));

        for (int i = 0; i < 2; i++) {
            battle.castImmediate(new DefaultSkill(1001, 1, 1), mar7th, List.of(iceEdge));
        }

        Assertions.assertTrue(iceEdge.isBroken());
        Assertions.assertEquals(DamageElement.ICE, iceEdge.getBrokenElement());
        Assertions.assertTrue(iceEdge.getBuffManager().allBuffsOf(DotBuff.class).isEmpty(), "Ice = freeze, not a DOT");
    }

    @Test
    public void dotTicksThroughBattleWhenTheEnemyTurnStarts() {
        Character hero = character("hero", 0.0);                        // speed 100 → period 100
        Enemy fast = dummy(100_000, 100, 200);                          // speed 200 → period 50, acts first
        Battle battle = new Battle(List.of(hero), List.of(fast), new Random(0));
        fast.getBuffManager().addBuff(new DotBuff(hero, DamageElement.FIRE, 500, 2));

        battle.stepForward();
        Assertions.assertSame(fast, battle.queue.getCurrentActor().getCanHit());
        battle.beforeMove();

        Assertions.assertEquals(100_000 - 500 * 1000.0 / 1100.0, fast.getCurrentHp(), 1e-6,
                "the DOT settles automatically at the start of the enemy's turn");
    }

    /**
     * A one-turn DOT must still <b>settle once</b> — and this is the test that pins the <b>order</b> of
     * the two lines in {@code Battle.beforeMove()}.
     *
     * <p>Settlement runs first, the buff countdown second. Swap them and a 1-turn DOT is counted down
     * to zero and removed <i>before</i> it ever deals damage: it silently burns for nothing. No other
     * test can see that, because for a DOT with 2+ turns one settlement survives either way — only the
     * boundary case distinguishes the two orders.
     */
    @Test
    public void aOneTurnDotStillSettlesBeforeItExpires() {
        Character hero = character("hero", 0.0);
        Enemy fast = dummy(100_000, 100, 200);                 // speed 200 → acts first
        Battle battle = new Battle(List.of(hero), List.of(fast), new Random(0));
        fast.getBuffManager().addBuff(new DotBuff(hero, DamageElement.FIRE, 500, 1));

        battle.stepForward();
        battle.beforeMove();

        Assertions.assertEquals(100_000 - 500 * 1000.0 / 1100.0, fast.getCurrentHp(), 1e-6,
                "the last settlement must happen before the DOT expires");
        Assertions.assertTrue(fast.getBuffManager().allBuffsOf(DotBuff.class).isEmpty(),
                "and then it is gone");
    }

    /**
     * A DOT can land on <b>us</b>, and the engine settles it — this is the capability the migration
     * bought, and it was <b>impossible</b> before it: {@code tickDots} took an {@code Enemy} and the
     * list of DOTs lived on {@code Enemy}, so "the boss burns us" could not be expressed at all.
     *
     * <p>Deliberately driven through {@code Battle.beforeMove()} rather than by calling
     * {@code tickDots(hero)} directly: the old shape's other half was the {@code actor instanceof
     * Enemy} guard inside {@code beforeMove}, and calling the method by hand would skip exactly the
     * line that used to make a character-DOT inert. Put that guard back and this assertion fails.
     */
    @Test
    public void aCharacterCanCarryADot() {
        Enemy boss = dummy(100_000, 100, 50);                  // speed 50 → the hero (100) acts first
        Character hero = character("hero", 0.0);
        Battle battle = new Battle(List.of(hero), List.of(boss), new Random(0));
        hero.getBuffManager().addBuff(new DotBuff(boss, DamageElement.FIRE, 500, 2));

        battle.stepForward();
        Assertions.assertSame(hero, battle.queue.getCurrentActor().getCanHit(), "the hero acts first");
        battle.beforeMove();

        Assertions.assertEquals(10_000 - 500 * 1000.0 / 1100.0, hero.getCurrentHp(), 1e-6,
                "a DOT on a character settles at the start of that character's turn");
        Assertions.assertEquals(1, hero.getBuffManager().countBuffs(DotBuff.class), "1 settlement left");
    }

    private static Character character(String name, double boost) {
        Character c = Character.fromAttributes(name, 10_000, 100, 100, 100);
        c.setAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST, new DoubleValue(boost));
        return c;
    }

    private static Enemy dummy(double hp, double defence, double speed) {
        return Enemy.fromAttributes("dummy", hp, defence, 100, speed);
    }

    /** Records the order of elements received by onDamage, used to assert "first applied, first settled". */
    private static Enemy recorder(double hp, double defence, double speed, List<DamageElement> seen) {
        AttributeBuilder builder = new AttributeBuilder();
        builder.setBase(AttributeType.HEALTH, hp)
                .setBase(AttributeType.DEFENCE, defence)
                .setBase(AttributeType.ATTACK, 100)
                .setBase(AttributeType.SPEED, speed);
        return new Enemy("recorder", builder.build()) {
            @Override
            public void onDamage(Battle battle, com.laosun.aluminium.models.Damage damage) {
                seen.add(damage.getElement());
                super.onDamage(battle, damage);
            }
        };
    }
}
