package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
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
 * acceptance: break DOT (first applied first settled, settled at the start of the turn, goes
 * through the zones but cannot crit).
 *
 * <p>Anchor: a target with 100 defence, an Lv80 attacker (defence zone = 1000/1100), no resistance
 * and no DMG boost to one DOT with base=500 settles 500  x  1000/1100 ~= 454.55.
 *
 * <p>Since the DOT became an ordinary buff, settling and counting down are <b>two</b> steps in two
 * different objects: {@code Battle.tickDots} deals the damage, {@code BuffManager.beforeMove()} burns
 * the turn off. Tests that assert expiry therefore drive both, in the order
 * {@code Battle.beforeMove()} drives them - the split is the point of the design, so it is visible in
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
        battle.castImmediate(new DefaultSkill(1003, 1, 1), himeko, List.of(iceEdge));   // drains it empty to break

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
        Character hero = character("hero", 0.0);                        // speed 100 to period 100
        Enemy fast = dummy(100_000, 100, 200);                          // speed 200 to period 50, acts first
        Battle battle = new Battle(List.of(hero), List.of(fast), new Random(0));
        fast.getBuffManager().addBuff(new DotBuff(hero, DamageElement.FIRE, 500, 2));

        battle.stepForward();
        Assertions.assertSame(fast, battle.queue.getCurrentActor().getCanHit());
        battle.beforeMove();

        Assertions.assertEquals(100_000 - 500 * 1000.0 / 1100.0, fast.getCurrentHp(), 1e-6,
                "the DOT settles automatically at the start of the enemy's turn");
    }

    /**
     * A one-turn DOT must still <b>settle once</b> - and this is the test that pins the <b>order</b> of
     * the two lines in {@code Battle.beforeMove()}.
     *
     * <p>Settlement runs first, the buff countdown second. Swap them and a 1-turn DOT is counted down
     * to zero and removed <i>before</i> it ever deals damage: it silently burns for nothing. No other
     * test can see that, because for a DOT with 2+ turns one settlement survives either way - only the
     * boundary case distinguishes the two orders.
     */
    @Test
    public void aOneTurnDotStillSettlesBeforeItExpires() {
        Character hero = character("hero", 0.0);
        Enemy fast = dummy(100_000, 100, 200);                 // speed 200 to acts first
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
     * A DOT can land on <b>us</b>, and the engine settles it - this is the capability the migration
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
        Enemy boss = dummy(100_000, 100, 50);                  // speed 50 to the hero (100) acts first
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

    // ==================================================================
    // A DOT attached by a RULE: op APPLY_DOT
    // ==================================================================

    /**
     * "使目标陷入灼烧状态，每回合造成 500 点伤害": the rule attaches a real DOT, and the {@code has_state}
     * name follows from the element (Fire to burn (灼烧)) with no second field.
     *
     * <p>Before this op only a weakness break could attach a DOT ({@code attachBreakDot}), so the whole
     * "使目标陷入灼烧/触电/裂伤/风化状态" family - 11 of the 9documents - had no spelling.
     */
    @Test
    public void aRuleCanAttachABurn() {
        Fixture f = new Fixture(TriggerSpecs.dot("Fire", 500.0, null, null, 2, null));

        Assertions.assertEquals(1, f.fire(), "the rule fires");
        Assertions.assertTrue(f.enemy.getBuffManager().hasState("灼烧"),
                "Fire + DotBuff IS Burn (灼烧): the engine's one translation is BuffManager.DOT_STATES");
        Assertions.assertEquals(500, f.dot().getBaseDamage(), EPS, "the flat amount the rule stated");

        double expected = 500 * 1000.0 / (100 + 1000.0);
        Assertions.assertEquals(expected, f.battle.tickDots(f.enemy), 1e-6, "and it settles like any DOT");
    }

    /**
     * "每回合造成等同于三月七60%攻击力的冰属性伤害": the magnitude is a share of the <b>rule owner's</b>
     * attribute, read once when the DOT lands and frozen into it.
     *
     * <p>Note: Frozen, not live: raising her attack afterwards must not change damage that is already burning. That is
     * the same snapshot rule the other derived values follow, and it is asserted here because the alternative
     * (holding a reference to her panel) would look identical in a one-turn test.
     */
    @Test
    public void theDotMagnitudeCanBeDerivedFromTheAppliersAttribute() {
        Fixture f = new Fixture(TriggerSpecs.dot("Ice", null, "self_attr:ATTACK", 0.6, 2, null));
        f.hero.setAttribute(AttributeType.ATTACK, new DoubleValue(2000));

        f.fire();

        Assertions.assertEquals(1200, f.dot().getBaseDamage(), EPS,
                "60% of the applier's 2000 ATTACK, computed when it landed");

        f.hero.setAttribute(AttributeType.ATTACK, new DoubleValue(4000));
        Assertions.assertEquals(1200, f.dot().getBaseDamage(), EPS,
                "and frozen: a later change to her panel cannot change a DOT that is already burning");
    }

    /** A flat constant may sit on top of the derived share - "等同于 60% 攻击力 + 50". */
    @Test
    public void aDerivedMagnitudeMayCarryAConstant() {
        Fixture f = new Fixture(TriggerSpecs.dot("Ice", 50.0, "self_attr:ATTACK", 0.6, 2, null));
        f.hero.setAttribute(AttributeType.ATTACK, new DoubleValue(2000));

        f.fire();

        Assertions.assertEquals(1250, f.dot().getBaseDamage(), EPS, "0.6 × 2000 + 50");
    }

    /**
     * A DOT may state a <b>base chance</b>, and it is the same per-target pipeline a control uses.
     *
     * <p>"有一定基础概率使目标陷入灼烧状态" is common in the documents, so the field is read here too - 
     * and when the roll fails, nothing is attached (not a DOT with 0 turns, which would still settle nothing but
     * would show up in "有几个负面效果").
     */
    @Test
    public void aDotWithABaseChanceIsRolledThroughTheEffectHitPipeline() {
        Fixture resisted = new Fixture(TriggerSpecs.dot("Fire", 500.0, null, null, 2, 1.0));
        resisted.enemy.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(1.0));
        resisted.fire();
        Assertions.assertEquals(0, resisted.enemy.getBuffManager().countBuffs(DotBuff.class),
                "100% effect resistance: 「陷入灼烧状态」 (falls into the Burn state) did not happen, so there is no DOT at all");

        Fixture lands = new Fixture(TriggerSpecs.dot("Fire", 500.0, null, null, 2, 1.0));
        lands.enemy.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(0));
        lands.fire();
        Assertions.assertEquals(1, lands.enemy.getBuffManager().countBuffs(DotBuff.class),
                "and with no resistance, a base chance of 1 always lands");
    }

    // ==================================================================
    // Fail fast: what a DOT rule may not say
    // ==================================================================

    @Test
    public void aDotWithoutAnElementOrATurnsCountIsRejected() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.dot(null, 500.0, null, null, 2, null)), "no element");
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.dot("Fire", 500.0, null, null, null, null)), "no turns");
    }

    /** An unknown element would attach nothing at all, so it is refused where the file is read. */
    @Test
    public void anUnknownElementIsRejectedAtLoadTime() {
        IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.dot("Burning", 500.0, null, null, 2, null)));

        Assertions.assertTrue(rejected.getMessage().contains("Burning"), rejected.getMessage());
    }

    /** A DOT has to know how much each turn takes: neither an amount nor a scale+percent means 0 damage forever. */
    @Test
    public void aDotWithoutAMagnitudeIsRejected() {
        IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.dot("Fire", null, null, null, 2, null)));

        Assertions.assertTrue(rejected.getMessage().contains("magnitude"), rejected.getMessage());

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableOf(TriggerSpecs.dot("Fire", null, null, 0.6, 2, null)),
                "a percent with no scale is a share of nothing");
    }

    /** One battle with a rule under test, one enemy, and a way to read the DOT it attached. */
    private static final class Fixture {
        private final Character hero;
        private final Enemy enemy;
        private final Battle battle;

        private Fixture(com.laosun.aluminium.beans.EffectSpec effect) {
            this.hero = character("applier", 0.0);
            this.enemy = dummy(100_000, 100, 100);
            this.hero.setTriggerTable(new com.laosun.aluminium.models.TriggerTable(0,
                    List.of(TriggerSpecs.rule("ALLY_ATTACK", null, effect))));
            this.battle = new Battle(List.of(hero), List.of(enemy), new Random(0));
            battle.startBattle();
        }

        private int fire() {
            return battle.fireTriggers(TriggerEvent.ALLY_ATTACK, hero, enemy, 1, 0);
        }

        /** The DOT the rule attached, failing loudly when there is none. */
        private DotBuff dot() {
            DotBuff found = enemy.getBuffManager().findBuff(DotBuff.class);
            Assertions.assertNotNull(found, "the rule was expected to attach a DOT");
            return found;
        }
    }

    /** Compiles one effect into a table, which is where its validation runs. */
    private static com.laosun.aluminium.models.TriggerTable tableOf(
            com.laosun.aluminium.beans.EffectSpec effect) {
        return new com.laosun.aluminium.models.TriggerTable(0,
                List.of(TriggerSpecs.rule("ALLY_ATTACK", null, effect)));
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
