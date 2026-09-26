package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Damage-instance conditions: the {@code DEALING_DAMAGE} event, the {@code BOOST_DAMAGE} op, and the DoT
 * state names that make them useful together — i.e. 「对处于灼烧状态的目标造成的伤害提高 20%」.
 *
 * <p><b>Why a new event was needed.</b> {@code ALLY_ATTACK} fires <i>after</i> an attack is fully settled, so
 * at that point the number is already final. A bonus that depends on the <b>target's state at the moment of
 * the hit</b> therefore could not be expressed at all: it is not a timed buff either (it applies to the hits
 * that happen to match, and to nothing else). The engine now hands the pending instance over before the zones
 * are read, and {@code BOOST_DAMAGE} changes <b>that instance</b> — the instance is the state, so there is no
 * buff to attach, nothing to remove, and nothing that can leak into the next hit.
 *
 * <p><b>The other half is the state name.</b> 「灼烧」 is not a {@code StateBuff}; it is a {@code DotBuff} of
 * element Fire, and the engine has modelled it that way since P10-0. {@code has_state} now resolves those four
 * names, which is what lets a rule ask about them without inventing a second fact for "this unit is burning".
 */
public class TriggerDealingDamageTest {
    /** Himeko: no shipped rule file, so the table under test is the only one. */
    private static final int OWNER = 1003;
    private static final int ALLY = 1202;
    private static final int LEVEL = 80;
    private static final double BASE = 1000;
    private static final double BURN_RATIO = 0.2;
    private static final double EPS = 1e-9;

    // ==================================================================
    // 1. The conditional boost, end to end
    // ==================================================================

    @Test
    public void aBoostConditionedOnTheTargetsStateAppliesOnlyToThatInstance() {
        Battle battle = battleWith(burnRule());
        Enemy target = dummy(battle);
        double plain = hit(battle, battle.characters.getFirst(), target);

        burn(target, battle.characters.getFirst());
        Assertions.assertEquals(plain * (1 + BURN_RATIO), hit(battle, battle.characters.getFirst(), target), EPS,
                "the target is burning now, so the rule fires and raises this hit by 20%");
    }

    /**
     * The boost must not leave anything behind: it is not a buff, and the next hit is only boosted if it too
     * matches the condition.
     */
    @Test
    public void theBoostDoesNotLeakIntoTheNextHit() {
        Battle battle = battleWith(burnRule());
        Character owner = battle.characters.getFirst();
        Enemy target = dummy(battle);
        double plain = hit(battle, owner, target);

        burn(target, owner);
        Assertions.assertEquals(plain * (1 + BURN_RATIO), hit(battle, owner, target), EPS);

        Assertions.assertTrue(target.getBuffManager().removeOneBuff(DotBuff.class), "the burn is removed");
        Assertions.assertEquals(plain, hit(battle, owner, target), EPS,
                "a hit against a target that is no longer burning gets nothing -- the state was the instance, "
                        + "not a buff someone attached");
    }

    /** {@code actor == self} scopes it to the rule owner's own damage, which is what a self-buff text means. */
    @Test
    public void theActorConditionScopesItToTheOwnersOwnDamage() {
        Battle battle = withAlly(TriggerSpecs.rule("DEALING_DAMAGE",
                List.of("target has_state 灼烧", "actor == self"), boostOp(BURN_RATIO)));
        Character owner = battle.characters.getFirst();
        Character ally = battle.characters.get(1);
        Enemy target = dummy(battle);

        double ownerPlain = hit(battle, owner, target);
        double allyPlain = hit(battle, ally, target);

        burn(target, owner);
        double byOwner = hit(battle, owner, target);
        double byAlly = hit(battle, ally, target);

        Assertions.assertEquals(ownerPlain * (1 + BURN_RATIO), byOwner, EPS, "my own hit is boosted");
        Assertions.assertEquals(allyPlain, byAlly, EPS,
                "the ally's hit is not mine, so my rule must not touch it -- each side measured against its "
                        + "own baseline, because the two characters do not have the same stats");
    }

    /**
     * The event fires for every instance the engine settles, DOT ticks included — those are damage too, and a
     * rule that means "attacks only" can say so with its own conditions.
     *
     * <p>The comparison is between two battles, because a DOT tick only exists while the target is burning,
     * so there is no "unboosted tick" to measure inside one battle: the same roster, the same level and the
     * same seed, with and without the rule.
     */
    @Test
    public void theEventFiresForDotTicksToo() {
        double withRule = dotTickDamage(battleWith(burnRule()));
        double withoutRule = dotTickDamage(battleWith());

        Assertions.assertTrue(withoutRule > 0, "precondition: the burn settles something");
        Assertions.assertEquals(withoutRule * (1 + BURN_RATIO), withRule, 1e-6,
                "a DOT tick is a damage instance like any other, so the same target-state condition boosts it");
    }

    /** Burns the dummy and returns what one DOT settlement deals to it. */
    private static double dotTickDamage(Battle battle) {
        Character owner = battle.characters.getFirst();
        Enemy target = dummy(battle);
        burn(target, owner);
        return battle.tickDots(target);
    }

    // ==================================================================
    // 2. The DoT state names
    // ==================================================================

    @Test
    public void hasStateKnowsTheFourDotNames() {
        Battle battle = battleWith();
        Character owner = battle.characters.getFirst();
        Enemy target = dummy(battle);

        Assertions.assertFalse(target.getBuffManager().hasState("灼烧"), "nothing attached yet");

        burn(target, owner);
        Assertions.assertTrue(target.getBuffManager().hasState("灼烧"), "a Fire DOT is 灼烧");
        Assertions.assertFalse(target.getBuffManager().hasState("触电"), "and it is not 触电");
        Assertions.assertFalse(target.getBuffManager().hasState("冻结"),
                "control states are deliberately not resolved yet: P10-2 models them as a buff plus a delay, "
                        + "so 'is it frozen' needs its own definition rather than a guess");

        target.getBuffManager().addBuff(new DotBuff(owner, DamageElement.THUNDER, 300, 2));
        Assertions.assertTrue(target.getBuffManager().hasState("触电"), "a Thunder DOT is 触电");
        Assertions.assertTrue(target.getBuffManager().hasState("灼烧"), "and the first one is still there");
    }

    @Test
    public void namedStatesStillWorkAlongsideTheDotNames() {
        Battle battle = battleWith();
        Character owner = battle.characters.getFirst();

        owner.getBuffManager().addBuff(new StateBuff("协奏", 2));
        Assertions.assertTrue(owner.getBuffManager().hasState("协奏"));
        Assertions.assertFalse(owner.getBuffManager().hasState("灼烧"), "a named state is not a DOT");
        Assertions.assertFalse(owner.getBuffManager().hasState("没听说过"), "and an unknown name is nobody");
    }

    // ==================================================================
    // 3. Fail fast at load time
    // ==================================================================

    @Test
    public void boostDamageOnAnotherEventIsRejectedAtLoadTime() {
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER,
                        List.of(TriggerSpecs.rule("ALLY_ATTACK", null, boostOp(BURN_RATIO)))));
        Assertions.assertTrue(e.getMessage().contains("DEALING_DAMAGE"), e.getMessage());
        Assertions.assertTrue(e.getMessage().contains(TriggerSpecs.TEST_SOURCE), e.getMessage());
    }

    @Test
    public void boostDamageRejectsAZeroPercentAndADuration() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(burnRuleWith(0.0, null))), "zero percent");

        IllegalArgumentException turns = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(burnRuleWith(BURN_RATIO, 2))));
        // the op acts on one moment, so a duration would be silently ignored
        Assertions.assertTrue(turns.getMessage().contains("turns"), turns.getMessage());

        EffectSpec stacked = boostOp(BURN_RATIO);
        TriggerSpecs.set(stacked, "maxStacks", 3);
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(TriggerSpecs.rule("DEALING_DAMAGE", null, stacked))));
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** The rule under test: while the target is burning, my damage against it is 20% higher. */
    private static TriggerSpec burnRule() {
        return TriggerSpecs.rule("DEALING_DAMAGE", List.of("target has_state 灼烧"), boostOp(BURN_RATIO));
    }

    private static TriggerSpec burnRuleWith(double percent, Integer turns) {
        EffectSpec effect = boostOp(percent);
        TriggerSpecs.set(effect, "turns", turns);
        return TriggerSpecs.rule("DEALING_DAMAGE", List.of("target has_state 灼烧"), effect);
    }

    private static EffectSpec boostOp(double percent) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "BOOST_DAMAGE");
        TriggerSpecs.set(effect, "percent", percent);
        return effect;
    }

    /** Puts a Fire DoT on {@code target} — what 「陷入灼烧状态」 leaves behind. */
    private static void burn(Enemy target, Character source) {
        target.getBuffManager().addBuff(new DotBuff(source, DamageElement.FIRE, 500, 3));
    }

    /** Settles one plain hit and returns the HP the target lost. */
    private static double hit(Battle battle, Character attacker, Enemy target) {
        double before = target.getCurrentHp();
        battle.applyDamage(target, new Damage(attacker, target, DamageElement.FIRE, DamageType.NORMAL, BASE));
        return before - target.getCurrentHp();
    }

    private static Battle battleWith(TriggerSpec... specs) {
        return newBattle(List.of(ownerWith(specs)), 1);
    }

    private static Battle withAlly(TriggerSpec... specs) {
        return newBattle(List.of(ownerWith(specs), CharacterFactory.create(ALLY, LEVEL)), 1);
    }

    private static Battle newBattle(List<Character> team, int enemyCount) {
        team.forEach(TriggerDealingDamageTest::neutralise);
        List<Enemy> enemies = new java.util.ArrayList<>();
        for (int i = 0; i < enemyCount; i++) {
            // No defence: the settled number is the base value times the zones, so a ratio is exact.
            enemies.add(Enemy.fromAttributes("dummy" + i, 1_000_000, 0, 100, 100));
        }
        Battle battle = new Battle(team, enemies, new Random(0));
        battle.startBattle();
        return battle;
    }

    /**
     * Removes the two things that would make a ratio assertion untrue: crit (rolled from the sheet by
     * {@code assemble}) and the character's own typed DMG boosts (traces), which would otherwise sit inside the
     * same additive zone the boost under test lands in.
     */
    private static void neutralise(Character who) {
        who.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0));
        who.setAttribute(AttributeType.FIRE_DAMAGE_BOOST, new DoubleValue(0));
        who.setAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST, new DoubleValue(0));
    }

    private static Character ownerWith(TriggerSpec... specs) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(specs)));
        return owner;
    }

    private static Enemy dummy(Battle battle) {
        return battle.enemyUnits().getFirst();
    }
}
