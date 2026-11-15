package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.AttributeBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.laosun.aluminium.damage.Damage;

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
 *
 * <p><b>L3:</b> the single-clause cases are grouped into the seven positive scenarios and the one
 * load-time validation scenario below, one {@code assertAll} per scenario so a failing clause still
 * names itself. The exception paths stay out of the positive scenarios: an {@code assertThrows} is
 * not a positive clause and must not hide behind one.
 */
public class DotTest {
    private static final double EPS = 1e-6;

    // ==================================================================
    // 1. Manual DOTs on a dummy: settlement, order, stacking, boost
    // ==================================================================

    /**
     * One fixture recipe - a source at a given DMG boost against the 100000-HP / 100-DEF dummy - for
     * the whole manual-DOT contract: it settles at the turn start and only the manager's tick burns
     * a turn off, it is removed once the last settlement is done, a second copy of the same element
     * is a second instance that settles too, and the settlement takes the DMG boost while never
     * critting.
     */
    @Test
    public void manualDotsSettleStackExpireAndNeverCrit() {
        // One DOT of 2 turns: settlement first, the countdown second - exactly as Battle.beforeMove does it.
        Character source = character("source", 0.0);
        Enemy dummy = dummy(100_000, 100, 100);
        Battle battle = new Battle(List.of(source), List.of(dummy), BattleFixture.seeded());
        dummy.getBuffManager().addBuff(new DotBuff(source, DamageElement.FIRE, 500, 2));

        double expected = 500 * 1000.0 / (100 + 1000.0);
        double first = battle.tickDots(dummy);
        int afterFirstSettlement = dummy.getBuffManager().countBuffs(DotBuff.class);

        dummy.getBuffManager().beforeMove();                    // the other half, exactly as Battle.beforeMove does it
        int afterFirstCountdown = dummy.getBuffManager().countBuffs(DotBuff.class);

        double second = battle.tickDots(dummy);
        dummy.getBuffManager().beforeMove();
        boolean expired = dummy.getBuffManager().allBuffsOf(DotBuff.class).isEmpty();
        double afterExpiry = battle.tickDots(dummy);
        double hpAfterTwoSettlements = dummy.getCurrentHp();

        // A second burn of the same element is a second instance, and both settle.
        Character stackingSource = character("source", 0.0);
        Enemy stackingDummy = dummy(100_000, 100, 100);
        Battle stackingBattle = new Battle(List.of(stackingSource), List.of(stackingDummy), BattleFixture.seeded());
        stackingDummy.getBuffManager().addBuff(new DotBuff(stackingSource, DamageElement.FIRE, 500, 3));
        stackingDummy.getBuffManager().addBuff(new DotBuff(stackingSource, DamageElement.FIRE, 500, 3));
        int stackedInstances = stackingDummy.getBuffManager().countBuffs(DotBuff.class);
        double stackedSettlement = stackingBattle.tickDots(stackingDummy);

        // A boosted source: the DOT takes the boost, and a DOT is never a crit.
        Character boostedSource = character("source", 1.0);      // DMG boost +100%
        Enemy boostedDummy = dummy(100_000, 100, 100);
        Battle boostedBattle = new Battle(List.of(boostedSource), List.of(boostedDummy), BattleFixture.seeded());
        boostedDummy.getBuffManager().addBuff(new DotBuff(boostedSource, DamageElement.FIRE, 500, 1));
        double settled = boostedBattle.tickDots(boostedDummy);

        Assertions.assertAll("the manual DOT contract on a 100-DEF dummy",
                () -> Assertions.assertEquals(expected, first, 1e-6,
                        "500 x 1000/1100: the first settlement"),
                () -> Assertions.assertEquals(1, afterFirstSettlement,
                        "settling alone does not consume a turn -- the manager's tick does that"),
                () -> Assertions.assertEquals(1, afterFirstCountdown, "1 settlement left"),
                () -> Assertions.assertEquals(expected, second, 1e-6,
                        "the second settlement is the same 500 x 1000/1100"),
                () -> Assertions.assertTrue(expired,
                        "it is removed once the last settlement is done"),
                () -> Assertions.assertEquals(0, afterExpiry, EPS, "no DOTs left → 0"),
                () -> Assertions.assertEquals(dummy.getMaxHp() - 2 * expected, hpAfterTwoSettlements, 1e-6,
                        "exactly two settlements came off the 100000 HP"),
                () -> Assertions.assertEquals(2, stackedInstances,
                        "a second burn is a second instance, not a refresh of the first"),
                () -> Assertions.assertEquals(2 * expected, stackedSettlement, 1e-6,
                        "both settle, and neither was silently dropped in favour of the other"),
                () -> Assertions.assertEquals(500 * 2 * 1000.0 / (100 + 1000.0), settled, 1e-6,
                        "DOT takes DMG boost"),
                () -> Assertions.assertFalse(DamageType.DOT.isCrittable(), "a DOT never crits"),
                () -> Assertions.assertTrue(DamageType.DOT.isBoostable(), "but it is boostable"));
    }

    /**
     * "first applied, first settled" - its own scenario because the observable is not the dummy's HP
     * but the order in which the elements arrive at {@code onDamage}, which needs the recording enemy
     * below.
     */
    @Test
    public void dotsSettleInApplicationOrder() {
        Character source = character("source", 0.0);
        List<DamageElement> seen = new ArrayList<>();
        Enemy recorder = recorder(100_000, 100, 100, seen);
        Battle battle = new Battle(List.of(source), List.of(recorder), BattleFixture.seeded());
        recorder.getBuffManager().addBuff(new DotBuff(source, DamageElement.THUNDER, 100, 1));
        recorder.getBuffManager().addBuff(new DotBuff(source, DamageElement.FIRE, 100, 1));

        battle.tickDots(recorder);

        Assertions.assertEquals(List.of(DamageElement.THUNDER, DamageElement.FIRE), seen, "first applied, first settled");
    }

    // ==================================================================
    // 2. Attachment: a weakness break
    // ==================================================================

    /**
     * Breaking with Fire attaches a real burn: 1 DOT instance of the breaking element, owned by the
     * breaker, for {@code Constant.DOT_TURNS} turns, at the break's own base damage.
     */
    @Test
    public void breakingWithFireAttachesABurn() {
        Character himeko = character("himeko", 0.0);
        Enemy iceEdge = EnemyFactory.create(1002011, 90, 1);   // weak to Fire, toughness 60
        Battle battle = new Battle(List.of(himeko), List.of(iceEdge), BattleFixture.seeded());

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

    /**
     * Ice breaks to freeze, not to a DOT: the break happens, the broken element is Ice, and no
     * {@link DotBuff} is attached at all.
     */
    @Test
    public void breakingWithAFrozenElementAttachesNoDot() {
        Character mar7th = character("mar7th", 0.0);
        Enemy iceEdge = EnemyFactory.create(1002011, 90, 1);
        iceEdge.setStanceWeak(Set.of(DamageElement.ICE));      // temporarily made Ice-weak, so that the Ice element can break it
        Battle battle = new Battle(List.of(mar7th), List.of(iceEdge), BattleFixture.seeded());

        for (int i = 0; i < 2; i++) {
            battle.castImmediate(new DefaultSkill(1001, 1, 1), mar7th, List.of(iceEdge));
        }

        Assertions.assertTrue(iceEdge.isBroken());
        Assertions.assertEquals(DamageElement.ICE, iceEdge.getBrokenElement());
        Assertions.assertTrue(iceEdge.getBuffManager().allBuffsOf(DotBuff.class).isEmpty(), "Ice = freeze, not a DOT");
    }

    // ==================================================================
    // 3. Settlement driven by the real battle driver
    // ==================================================================

    /**
     * Through {@code Battle.beforeMove()}: the DOT settles automatically at the start of its
     * carrier's turn.
     *
     * <p>One scenario because both clauses are the same fixture (a hero at speed 100 against the
     * speed-200 dummy) and the same observable (the dummy's HP at the moment its turn starts).
     *
     * <p>The 1-turn half is also the test that pins the <b>order</b> of the two lines in
     * {@code Battle.beforeMove()}: settlement runs first, the buff countdown second. Swap them and a
     * 1-turn DOT is counted down to zero and removed <i>before</i> it ever deals damage: it silently
     * burns for nothing. No other test can see that, because for a DOT with 2+ turns one settlement
     * survives either way - only the boundary case distinguishes the two orders.
     */
    @Test
    public void dotsSettleAutomaticallyWhenTheirCarrierTurnStarts() {
        Character hero = character("hero", 0.0);                        // speed 100 to period 100
        Enemy fast = dummy(100_000, 100, 200);                          // speed 200 to period 50, acts first
        Battle battle = new Battle(List.of(hero), List.of(fast), BattleFixture.seeded());
        fast.getBuffManager().addBuff(new DotBuff(hero, DamageElement.FIRE, 500, 2));

        battle.stepForward();
        var acting = battle.queue().getCurrentActor().getCanHit();
        battle.beforeMove();
        double hpAfterTwoTurnDot = fast.getCurrentHp();

        // The boundary case: a 1-turn DOT still settles once, before it is counted down and removed.
        Character boundaryHero = character("hero", 0.0);
        Enemy boundaryFast = dummy(100_000, 100, 200);                  // speed 200 to acts first
        Battle boundaryBattle = new Battle(List.of(boundaryHero), List.of(boundaryFast), BattleFixture.seeded());
        boundaryFast.getBuffManager().addBuff(new DotBuff(boundaryHero, DamageElement.FIRE, 500, 1));

        boundaryBattle.stepForward();
        boundaryBattle.beforeMove();
        double hpAfterOneTurnDot = boundaryFast.getCurrentHp();
        boolean boundaryExpired = boundaryFast.getBuffManager().allBuffsOf(DotBuff.class).isEmpty();

        Assertions.assertAll("the driver settles the DOT at the start of the carrier's turn",
                () -> Assertions.assertSame(fast, acting, "the speed-200 enemy acts first"),
                () -> Assertions.assertEquals(100_000 - 500 * 1000.0 / 1100.0, hpAfterTwoTurnDot, 1e-6,
                        "the DOT settles automatically at the start of the enemy's turn"),
                () -> Assertions.assertEquals(100_000 - 500 * 1000.0 / 1100.0, hpAfterOneTurnDot, 1e-6,
                        "the last settlement must happen before the DOT expires"),
                () -> Assertions.assertTrue(boundaryExpired, "and then it is gone"));
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
        Battle battle = new Battle(List.of(hero), List.of(boss), BattleFixture.seeded());
        hero.getBuffManager().addBuff(new DotBuff(boss, DamageElement.FIRE, 500, 2));

        battle.stepForward();
        Assertions.assertSame(hero, battle.queue().getCurrentActor().getCanHit(), "the hero acts first");
        battle.beforeMove();

        Assertions.assertEquals(10_000 - 500 * 1000.0 / 1100.0, hero.getCurrentHp(), 1e-6,
                "a DOT on a character settles at the start of that character's turn");
        Assertions.assertEquals(1, hero.getBuffManager().countBuffs(DotBuff.class), "1 settlement left");
    }

    // ==================================================================
    // 4. A DOT attached by a RULE: op APPLY_DOT
    // ==================================================================

    /**
     * A rule-attached DOT, in one fixture (an {@code EffectSpec} compiled onto the applier): the
     * element, the magnitude and the chance the spec states, and the {@code has_state} name that
     * follows from the element.
     *
     * <p>"使目标陷入dot:burn状态，每回合造成 500 点伤害": the rule attaches a real DOT, and the
     * {@code has_state} name follows from the element (Fire to burn (dot:burn)) with no second field.
     * Before this op only a weakness break could attach a DOT ({@code attachBreakDot}), so the whole
     * "使目标陷入dot:burn/dot:shock/dot:bleed/dot:wind_shear状态" family - 11 of the 9documents - had
     * no spelling.
     *
     * <p>"每回合造成等同于三月七60%攻击力的冰属性伤害": the magnitude is a share of the <b>rule
     * owner's</b> attribute, read once when the DOT lands and frozen into it - raising her attack
     * afterwards must not change damage that is already burning. A flat constant may sit on top of the
     * share ("等同于 60% 攻击力 + 50"), and a stated <b>base chance</b> is the same per-target pipeline
     * a control uses: when the roll fails nothing is attached (not a DOT with 0 turns, which would
     * still settle nothing but would show up in "有几个负面效果").
     */
    @Test
    public void aRuleAttachesTheDotItsEffectSpecStates() {
        // A flat amount.
        Fixture flat = new Fixture(TriggerSpecs.dot("Fire", 500.0, null, null, 2, null));
        int flatFired = flat.fire();
        boolean flatBurned = flat.enemy.getBuffManager().hasState("dot:burn");
        double flatBase = flat.dot().getBaseDamage();
        double flatSettlement = flat.battle.tickDots(flat.enemy);

        // The magnitude derived from the applier's ATTACK, read once when the DOT lands and frozen.
        Fixture derived = new Fixture(TriggerSpecs.dot("Ice", null, "self_attr:ATTACK", 0.6, 2, null));
        derived.hero.setAttribute(AttributeType.ATTACK, new DoubleValue(2000));
        derived.fire();
        double derivedBase = derived.dot().getBaseDamage();
        derived.hero.setAttribute(AttributeType.ATTACK, new DoubleValue(4000));
        double derivedBaseAfterRaise = derived.dot().getBaseDamage();

        // A flat constant on top of the derived share.
        Fixture withConstant = new Fixture(TriggerSpecs.dot("Ice", 50.0, "self_attr:ATTACK", 0.6, 2, null));
        withConstant.hero.setAttribute(AttributeType.ATTACK, new DoubleValue(2000));
        withConstant.fire();
        double baseWithConstant = withConstant.dot().getBaseDamage();

        // The base chance, through the effect-hit pipeline: resisted, and landing.
        Fixture resisted = new Fixture(TriggerSpecs.dot("Fire", 500.0, null, null, 2, 1.0));
        resisted.enemy.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(1.0));
        resisted.fire();
        int resistedInstances = resisted.enemy.getBuffManager().countBuffs(DotBuff.class);

        Fixture lands = new Fixture(TriggerSpecs.dot("Fire", 500.0, null, null, 2, 1.0));
        lands.enemy.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(0));
        lands.fire();
        int landedInstances = lands.enemy.getBuffManager().countBuffs(DotBuff.class);

        Assertions.assertAll("a rule attaches the DOT its effect spec describes",
                () -> Assertions.assertEquals(1, flatFired, "the rule fires"),
                () -> Assertions.assertTrue(flatBurned,
                        "Fire + DotBuff IS Burn (dot:burn): the engine's one translation is BuffManager.DOT_STATES"),
                () -> Assertions.assertEquals(500, flatBase, EPS, "the flat amount the rule stated"),
                () -> Assertions.assertEquals(500 * 1000.0 / (100 + 1000.0), flatSettlement, 1e-6,
                        "and it settles like any DOT"),
                () -> Assertions.assertEquals(1200, derivedBase, EPS,
                        "60% of the applier's 2000 ATTACK, computed when it landed"),
                () -> Assertions.assertEquals(1200, derivedBaseAfterRaise, EPS,
                        "and frozen: a later change to her panel cannot change a DOT that is already burning"),
                () -> Assertions.assertEquals(1250, baseWithConstant, EPS, "0.6 × 2000 + 50"),
                () -> Assertions.assertEquals(0, resistedInstances,
                        "100% effect resistance: 「陷入dot:burn状态」 (falls into the Burn state) did not happen, so there is no DOT at all"),
                () -> Assertions.assertEquals(1, landedInstances,
                        "and with no resistance, a base chance of 1 always lands"));
    }

    // ==================================================================
    // 5. Fail fast: what a DOT rule may not say
    // ==================================================================

    /**
     * What a DOT rule may not say, refused where the file is read: no element, no turns, an unknown
     * element (it would attach nothing at all), and no magnitude (neither an amount nor a
     * scale+percent means 0 damage forever).
     *
     * <p>One validation scenario for the one fixture (an effect spec compiled into a
     * {@code TriggerTable}, which is where validation runs) and the one observable (the load-time
     * refusal and the field its message names) - and it stays apart from every positive scenario
     * above, because an {@code assertThrows} is not a positive clause.
     */
    @Test
    public void aDotRuleThatCannotWorkIsRejectedAtLoadTime() {
        Assertions.assertAll("a DOT rule that cannot state a settlement is rejected at load time",
                () -> Assertions.assertThrows(IllegalArgumentException.class,
                        () -> tableOf(TriggerSpecs.dot(null, 500.0, null, null, 2, null)), "no element"),
                () -> Assertions.assertThrows(IllegalArgumentException.class,
                        () -> tableOf(TriggerSpecs.dot("Fire", 500.0, null, null, null, null)), "no turns"),
                () -> {
                    IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> tableOf(TriggerSpecs.dot("Burning", 500.0, null, null, 2, null)),
                            "an unknown element would attach nothing at all");
                    Assertions.assertTrue(rejected.getMessage().contains("Burning"), rejected.getMessage());
                },
                () -> {
                    IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> tableOf(TriggerSpecs.dot("Fire", null, null, null, 2, null)),
                            "neither an amount nor a scale+percent is 0 damage forever");
                    Assertions.assertTrue(rejected.getMessage().contains("magnitude"), rejected.getMessage());
                },
                () -> Assertions.assertThrows(IllegalArgumentException.class,
                        () -> tableOf(TriggerSpecs.dot("Fire", null, null, 0.6, 2, null)),
                        "a percent with no scale is a share of nothing"));
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /**
     * One battle with a rule under test, one enemy, and a way to read the DOT it attached.
     */
    private static final class Fixture {
        private final Character hero;
        private final Enemy enemy;
        private final Battle battle;

        private Fixture(com.laosun.aluminium.beans.EffectSpec effect) {
            this.hero = character("applier", 0.0);
            this.enemy = dummy(100_000, 100, 100);
            this.hero.setTriggerTable(new TriggerTable(0,
                    List.of(TriggerSpecs.rule("ALLY_ATTACK", null, effect))));
            this.battle = new Battle(List.of(hero), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
        }

        private int fire() {
            return battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, hero, enemy, 1, 0));
        }

        /**
         * The DOT the rule attached, failing loudly when there is none.
         */
        private DotBuff dot() {
            DotBuff found = enemy.getBuffManager().findBuff(DotBuff.class);
            Assertions.assertNotNull(found, "the rule was expected to attach a DOT");
            return found;
        }
    }

    /**
     * Compiles one effect into a table, which is where its validation runs.
     */
    private static TriggerTable tableOf(
            com.laosun.aluminium.beans.EffectSpec effect) {
        return new TriggerTable(0,
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

    /**
     * Records the order of elements received by onDamage, used to assert "first applied, first settled".
     */
    private static Enemy recorder(double hp, double defence, double speed, List<DamageElement> seen) {
        AttributeBuilder builder = new AttributeBuilder();
        builder.setBase(AttributeType.HEALTH, hp)
                .setBase(AttributeType.DEFENCE, defence)
                .setBase(AttributeType.ATTACK, 100)
                .setBase(AttributeType.SPEED, speed);
        return new Enemy("recorder", builder.build()) {
            @Override
            public void onDamage(Battle battle, com.laosun.aluminium.damage.Damage damage) {
                seen.add(damage.getElement());
                super.onDamage(battle, damage);
            }
        };
    }
}
