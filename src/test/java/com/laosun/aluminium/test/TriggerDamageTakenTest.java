package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.ReductionBuff;
import com.laosun.aluminium.models.buff.VulnerabilityBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The {@code MODIFY_DAMAGE_TAKEN} op: 「受到的伤害提高 X%」（易伤）and 「受到的伤害降低 X%」（减伤）as data.
 *
 * <p><b>Why a separate op was needed.</b> Both are damage <i>zones</i>, not attributes: the engine has had
 * {@link VulnerabilityBuff} / {@link ReductionBuff} since P1, and they work by injecting into the settlement
 * of each hit — but no rule could create one, because {@code MODIFY_ATTR} changes an {@code AttributeType}
 * and nothing named "incoming damage ×0.92" is an attribute. Relic set 106's 2-piece ("Reduces DMG taken by
 * 8%") is registered as unmodelled for exactly that reason and is the first user.
 *
 * <p><b>The sign picks the zone</b>, mirroring {@code MODIFY_ATTR}'s sign convention. Each case checks all
 * three of: the number moved in the right direction, by the right amount, and through the right buff class —
 * because a mixed-up sign would still move the number, just the other way.
 *
 * <p><b>Why the measurements are taken on the 1,000,000-HP dummy</b> and not on a character: a 1000-point hit
 * would nearly empty a level-80 character's HP bar, and {@code takeDamage} clamps at zero, so the measured
 * "HP lost" would be the bar's remainder rather than the hit (the first version of this test read 351 where
 * it expected 779). Both numbers below are therefore exact, and the ratio between them is what is asserted —
 * the vulnerability/reduction zones are multiplicative, so an unrelated additive boost (Himeko's own fire DMG
 * traces) cancels out of the ratio instead of having to be neutralised.
 */
public class TriggerDamageTakenTest {
    /** Himeko: no shipped rule file, so the table under test is the only one. */
    private static final int OWNER = 1003;
    private static final int ALLY = 1202;
    private static final int LEVEL = 80;
    private static final double BASE = 1000;
    private static final double EPS = 1e-9;

    // ==================================================================
    // 1. The two zones, picked by the sign
    // ==================================================================

    @Test
    public void aPositivePercentRaisesTheDamageTaken() {
        Battle battle = battleWith(takenRule(0.12, 2, "target"));
        double plain = dummyDamageTaken(battle);

        fireOnDummy(battle);
        Assertions.assertTrue(dummy(battle).getBuffManager().hasBuff(VulnerabilityBuff.class),
                "positive = 受到的伤害提高 = vulnerability, a DEBUFF on the defender");
        Assertions.assertEquals(plain * 1.12, dummyDamageTaken(battle), EPS);
    }

    @Test
    public void aNegativePercentLowersTheDamageTaken() {
        Battle battle = battleWith(takenRule(-0.08, 2, "target"));
        double plain = dummyDamageTaken(battle);

        fireOnDummy(battle);
        Assertions.assertTrue(dummy(battle).getBuffManager().hasBuff(ReductionBuff.class),
                "negative = 受到的伤害降低 = reduction, a BUFF on the defender");
        Assertions.assertEquals(plain * 0.92, dummyDamageTaken(battle), EPS);
    }

    /**
     * The C-1 boundary: a vulnerability on the defender must not boost the damage that defender <b>deals</b>.
     *
     * <p>This is the trap both buffs guard with {@code damage.isOnDefenderSide(owner)} — without it a 易伤
     * sitting on the enemy would also be an output bonus for the enemy. Pinned at the content level, because
     * this op is the first way for <b>data</b> to attach one.
     */
    @Test
    public void aVulnerabilityOnTheDefenderDoesNotBoostItsOwnDamage() {
        Battle battle = battleWithTwoDummies(takenRule(0.5, 2, "target"));
        Enemy vulnerable = dummy(battle);
        Enemy other = secondDummy(battle);

        double dealtBefore = dummyDamageDealtTo(battle, vulnerable, other);
        double takenBefore = dummyDamageTaken(battle);
        fireOnDummy(battle);
        Assertions.assertTrue(vulnerable.getBuffManager().hasBuff(VulnerabilityBuff.class));

        Assertions.assertEquals(dealtBefore, dummyDamageDealtTo(battle, vulnerable, other), EPS,
                "the buff is on the one being hit -- it must not raise the damage that unit deals");
        Assertions.assertEquals(takenBefore * 1.5, dummyDamageTaken(battle), EPS,
                "while the damage it takes is exactly 50% higher");
    }

    // ==================================================================
    // 2. Duration
    // ==================================================================

    @Test
    public void itExpiresAfterItsTurns() {
        Battle battle = battleWith(takenRule(0.12, 1, "target"));
        double plain = dummyDamageTaken(battle);

        fireOnDummy(battle);
        Assertions.assertEquals(plain * 1.12, dummyDamageTaken(battle), EPS);

        takeTurn(battle, dummy(battle));
        Assertions.assertFalse(dummy(battle).getBuffManager().hasBuff(VulnerabilityBuff.class));
        Assertions.assertEquals(plain, dummyDamageTaken(battle), EPS);
    }

    @Test
    public void aPermanentOneLastsForTheRestOfTheBattle() {
        Battle battle = battleWith(permanentTakenRule(-0.08));
        double plain = dummyDamageTaken(battle);

        fireOnDummy(battle);
        for (int turn = 0; turn < 3; turn++) {
            takeTurn(battle, dummy(battle));
        }
        Assertions.assertEquals(plain * 0.92, dummyDamageTaken(battle), EPS,
                "a relic passive has no turn count, which is what permanent is for");
    }

    // ==================================================================
    // 3. Who it lands on
    // ==================================================================

    @Test
    public void theTargetSelectorDecidesWhoGetsIt() {
        Battle battle = battleWith(takenRule(0.12, 2, "target"));
        Character owner = battle.characters.getFirst();

        fireOnDummy(battle);
        Assertions.assertTrue(dummy(battle).getBuffManager().hasBuff(VulnerabilityBuff.class),
                "the event's subject is the dummy, so the dummy is the one taking more damage");
        Assertions.assertFalse(owner.getBuffManager().hasBuff(VulnerabilityBuff.class),
                "and the rule's owner is untouched");
    }

    @Test
    public void allAlliesReachesTheWholeParty() {
        Battle battle = withAlly(takenRule(-0.08, 2, "all_allies"));
        Character owner = battle.characters.getFirst();
        Character ally = battle.characters.get(1);

        fire(battle, owner);
        Assertions.assertTrue(owner.getBuffManager().hasBuff(ReductionBuff.class));
        Assertions.assertTrue(ally.getBuffManager().hasBuff(ReductionBuff.class));
        Assertions.assertFalse(dummy(battle).getBuffManager().hasBuff(ReductionBuff.class),
                "the enemy is not one of ours");
    }

    // ==================================================================
    // 4. Fail fast at load time
    // ==================================================================

    @Test
    public void aZeroPercentIsRejectedAtLoadTime() {
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(takenRule(0, 2, null))));
        Assertions.assertTrue(e.getMessage().contains("percent"), e.getMessage());
        Assertions.assertTrue(e.getMessage().contains("0"), e.getMessage());
    }

    @Test
    public void aDurationIsRequiredAndBothAtOnceIsRejected() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(takenRule(0.12, null, null))), "no duration");
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(permanentTakenRule(0.12, 2))), "both durations");

        EffectSpec noPercent = new EffectSpec();
        TriggerSpecs.set(noPercent, "op", "MODIFY_DAMAGE_TAKEN");
        TriggerSpecs.set(noPercent, "turns", 2);
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(TriggerSpecs.rule("ALLY_ATTACK", null, noPercent))));
    }

    @Test
    public void aDamageTakenEffectCannotClaimAStackCap() {
        EffectSpec effect = takenOp(0.12, 2, null);
        TriggerSpecs.set(effect, "maxStacks", 3);
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(TriggerSpecs.rule("ALLY_ATTACK", null, effect))));
        Assertions.assertTrue(e.getMessage().contains("max_stacks"), e.getMessage());
    }

    /** A buff class that only ever means one zone cannot be built with the other zone's sign. */
    @Test
    public void theBuffClassesThemselvesRefuseTheWrongSign() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new VulnerabilityBuff(2, -0.5));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ReductionBuff(2, 0));
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** Settles one plain hit on the dummy and returns the HP it lost. */
    private static double dummyDamageTaken(Battle battle) {
        Character attacker = battle.characters.getFirst();
        Enemy victim = dummy(battle);
        double before = victim.getCurrentHp();
        battle.applyDamage(victim, new Damage(attacker, victim, DamageElement.FIRE, DamageType.NORMAL, BASE));
        return before - victim.getCurrentHp();
    }

    /** Settles one plain hit by {@code attacker} on {@code victim} and returns the HP lost. */
    private static double dummyDamageDealtTo(Battle battle, Enemy attacker, Enemy victim) {
        double before = victim.getCurrentHp();
        battle.applyDamage(victim, new Damage(attacker, victim, DamageElement.FIRE, DamageType.NORMAL, BASE));
        return before - victim.getCurrentHp();
    }

    /** Fires the rule with the dummy as the event's subject, which is what {@code target} selects. */
    private static void fireOnDummy(Battle battle) {
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, battle.characters.getFirst(), dummy(battle), 1, 0);
    }

    private static Enemy dummy(Battle battle) {
        return battle.enemyUnits().getFirst();
    }

    private static Enemy secondDummy(Battle battle) {
        return battle.enemyUnits().get(1);
    }

    private static TriggerSpec takenRule(double percent, Integer turns, String target) {
        return TriggerSpecs.rule("ALLY_ATTACK", null, takenOp(percent, turns, target));
    }

    /** A rule for a passive with no turn count ("for the rest of the battle"). */
    private static TriggerSpec permanentTakenRule(double percent) {
        return permanentTakenRule(percent, null);
    }

    /**
     * The same, carrying both a turn count and {@code permanent} — the combination that must be refused.
     *
     * <p>It targets the event's subject (the dummy), like the measurable cases: a rule with no
     * {@code target} lands on its own owner, which made the first version of the permanence test assert
     * against a character that had never received the buff.
     */
    private static TriggerSpec permanentTakenRule(double percent, Integer turns) {
        EffectSpec effect = takenOp(percent, turns, "target");
        TriggerSpecs.set(effect, "permanent", true);
        return TriggerSpecs.rule("ALLY_ATTACK", null, effect);
    }

    private static EffectSpec takenOp(double percent, Integer turns, String target) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_DAMAGE_TAKEN");
        TriggerSpecs.set(effect, "percent", percent);
        TriggerSpecs.set(effect, "turns", turns);
        TriggerSpecs.set(effect, "target", target);
        return effect;
    }

    private static Battle battleWith(TriggerSpec... specs) {
        return newBattle(List.of(ownerWith(specs)), 1);
    }

    private static Battle battleWithTwoDummies(TriggerSpec... specs) {
        return newBattle(List.of(ownerWith(specs)), 2);
    }

    private static Battle withAlly(TriggerSpec... specs) {
        return newBattle(List.of(ownerWith(specs), CharacterFactory.create(ALLY, LEVEL)), 1);
    }

    private static Battle newBattle(List<Character> team, int enemyCount) {
        List<Enemy> enemies = new java.util.ArrayList<>();
        for (int i = 0; i < enemyCount; i++) {
            enemies.add(Enemy.fromAttributes("dummy" + i, 1_000_000, 0, 100, 100));
        }
        Battle battle = new Battle(team, enemies, new Random(0));
        battle.startBattle();
        return battle;
    }

    private static Character ownerWith(TriggerSpec... specs) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        // No crits: the ratio assertions must hold to the last bit, and assemble rolls crit from the sheet.
        owner.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0));
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(specs)));
        return owner;
    }

    private static void fire(Battle battle, Character actor) {
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, actor, null, 1, 0);
    }

    /** Runs turns until {@code who} acts, settles the start and finishes the turn. */
    private static void takeTurn(Battle battle, CanHit who) {
        for (int guard = 0; guard < 40; guard++) {
            battle.stepForward();
            if (battle.isOver()) {
                throw new AssertionError("the battle ended before the requested unit acted");
            }
            boolean mine = battle.queue.getCurrentActor().getCanHit() == who;
            if (mine) {
                battle.beforeMove();
            }
            battle.afterMove();
            if (mine) {
                return;
            }
        }
        throw new AssertionError("no turn for the requested unit within 40 steps");
    }
}
