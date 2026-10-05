package com.laosun.aluminium.test;

import com.google.gson.Gson;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Firing limits on a trigger rule: {@code cooldown} (in the owner's own turns), {@code per_turn} (a count
 * within one of them) and {@code once_per_battle}.
 *
 * <p><b>Why this vocabulary exists.</b> The game's rule text is full of "该效果每回合只能触发1次" /
 * "该效果有1回合的触发冷却" / "单场战斗中只能触发1次" / "该效果每回合可触发<b>2</b>次" - a per-rule limit is the
 * difference between "Misha's counter counts attacks" and "Misha's counter counts attacks once per turn".
 * Without it a data author has to choose between over-triggering and not modelling the mechanic at all, and the
 * over-triggering version is a wrong number with nothing to see. Note: The last form is why {@code per_turn} is a
 * <b>count</b> and not a flag: 三月七's 天赋 counter is "每回合可触发2次", and {@code cooldown: 1} can only ever
 * say "once".
 *
 * <p><b>Where the state lives, and why that is the whole design.</b> A trigger table is compiled once
 * and <b>cached per cid</b>, and relic rules are merged into the same table for every character wearing
 * them - so a counter stored on a rule would be shared by every wearer in every battle inside one JVM.
 * The counters therefore live on the combatant ({@code CanHit}), and a battle start clears them. Both
 * halves of that are pinned below ({@link #twoRulesWithTheSameSourceHaveIndependentLimits} and
 * {@link #startingABattleMakesEveryRuleReadyAgain}).
 *
 * <p><b>How a firing is counted.</b> {@code Battle.fireTriggers} returns how many rules really ran, and
 * the rules used here grant 1 skill point each, so the pool is a second, independent count (the pool
 * caps at 5, so no test below lets the fingerprint exceed it).
 */
public class TriggerLimitTest {
    /** 姬子: her own table is replaced by the rule under test, so nothing else is ever in play. */
    private static final int OWNER = 1003;
    /** Tingyun: likewise, and used as the "somebody else acted" actor in the two-character cases. */
    private static final int ALLY = 1202;
    private static final int LEVEL = 80;

    // ==================================================================
    // 1. Once per battle
    // ==================================================================

    @Test
    public void oncePerBattleFiresExactlyOnce() {
        Battle battle = battleWith(limited(null, true));
        Character owner = battle.characters.getFirst();

        Assertions.assertEquals(1, fire(battle, owner), "the first event fires the rule");
        Assertions.assertEquals(0, fire(battle, owner), "the second is refused: 单场战斗中只能触发1次");
        Assertions.assertEquals(0, fire(battle, owner), "and it never comes back");
        Assertions.assertEquals(1, battle.getSkillPoints(), "exactly one of the three firings paid out");
    }

    // ==================================================================
    // 2. Cooldown, counted in the owner's turns
    // ==================================================================

    @Test
    public void cooldownOneMeansOncePerOwnTurn() {
        Battle battle = battleWith(limited(1, null));
        Character owner = battle.characters.getFirst();

        Assertions.assertEquals(1, fire(battle, owner));
        Assertions.assertEquals(0, fire(battle, owner), "two firings in one turn: the cooldown refuses the second");

        takeTurn(battle, owner);
        Assertions.assertEquals(1, fire(battle, owner), "one of the owner's turns later it is ready again");
    }

    @Test
    public void cooldownTwoWaitsForTwoOfTheOwnersTurns() {
        Battle battle = battleWith(limited(2, null));
        Character owner = battle.characters.getFirst();

        Assertions.assertEquals(1, fire(battle, owner));
        takeTurn(battle, owner);
        Assertions.assertEquals(0, fire(battle, owner), "one turn is not enough for a 2-turn cooldown");

        takeTurn(battle, owner);
        Assertions.assertEquals(1, fire(battle, owner), "the second turn clears it");
    }

    /**
     * The subtle half of "the owner's turns": a rule that reacts to <b>other people's</b> actions still
     * counts <b>its owner's</b> turns.
     *
     * <p>This is what makes "每回合只能触发1次" mean "once on my turn" for a talent like Tingyun's
     * "when the buffed ally kills someone" - the limit belongs to the character whose table it is, not
     * to whoever happened to set the event off.
     */
    @Test
    public void otherPeoplesTurnsDoNotCountTheCooldownDown() {
        Battle battle = withAlly(limited(1, null));
        Character owner = battle.characters.getFirst();
        Character ally = battle.characters.get(1);

        Assertions.assertEquals(1, fire(battle, ally), "the ally's action fires it");
        takeTurn(battle, ally);
        Assertions.assertEquals(0, fire(battle, ally),
                "the ally having a turn must not clear the OWNER's cooldown");

        takeTurn(battle, owner);
        Assertions.assertEquals(1, fire(battle, ally), "the owner's own turn clears it");
    }

    // ==================================================================
    // 3. Per-turn count, counted in the owner's turns like the cooldown
    // ==================================================================

    /** "该效果每回合可触发2次": the third firing in one turn is refused, the first two are not. */
    @Test
    public void perTurnTwoLetsTheRuleFireTwiceInOneTurn() {
        Battle battle = battleWith(perTurn(2));
        Character owner = battle.characters.getFirst();

        Assertions.assertEquals(1, fire(battle, owner), "the first firing is allowed");
        Assertions.assertEquals(1, fire(battle, owner), "and the second");
        Assertions.assertEquals(0, fire(battle, owner), "the third is refused: 每回合可触发2次");
        Assertions.assertEquals(2, battle.getSkillPoints(), "exactly two firings paid out");
    }

    /** The count comes back on the owner's own turn - the same moment a cooldown is decremented. */
    @Test
    public void theTurnLimitComesBackOnTheOwnersOwnTurn() {
        Battle battle = battleWith(perTurn(2));
        Character owner = battle.characters.getFirst();
        Assertions.assertEquals(1, fire(battle, owner));
        Assertions.assertEquals(1, fire(battle, owner));
        Assertions.assertEquals(0, fire(battle, owner), "precondition: the two uses are spent");

        takeTurn(battle, owner);

        Assertions.assertEquals(1, fire(battle, owner), "a fresh turn, a fresh count");
        Assertions.assertEquals(1, fire(battle, owner));
        Assertions.assertEquals(0, fire(battle, owner), "and again exactly two");
    }

    /**
     * Somebody else's turn does not hand the count back.
     *
     * <p>The mirror of {@link #otherPeoplesTurnsDoNotCountTheCooldownDown}, and the reason both limits are
     * counted on the <b>owner</b>: "每回合" belongs to the character whose rule it is, not to whoever happened to
     * set the event off.
     */
    @Test
    public void otherPeoplesTurnsDoNotClearTheTurnLimit() {
        Battle battle = withAlly(perTurn(1));
        Character owner = battle.characters.getFirst();
        Character ally = battle.characters.get(1);

        Assertions.assertEquals(1, fire(battle, ally), "the ally's action fires it once");
        takeTurn(battle, ally);
        Assertions.assertEquals(0, fire(battle, ally),
                "the ally having a turn must not hand back the owner's per-turn count");

        takeTurn(battle, owner);
        Assertions.assertEquals(1, fire(battle, ally), "the owner's own turn does");
    }

    /**
     * A per-turn cap and a once-per-battle cap are <b>cumulative</b> - unlike {@code cooldown}, which is
     * refused next to {@code per_turn} because the two would each have to start counting at a different firing.
     */
    @Test
    public void aTurnLimitAndAOncePerBattleLimitCombine() {
        TriggerSpec spec = perTurn(3);
        TriggerSpecs.set(spec, "oncePerBattle", true);
        Battle battle = battleWith(spec);
        Character owner = battle.characters.getFirst();

        Assertions.assertEquals(1, fire(battle, owner));
        Assertions.assertEquals(0, fire(battle, owner), "the battle limit is the tighter one here");
        takeTurn(battle, owner);
        Assertions.assertEquals(0, fire(battle, owner), "and no turn brings it back");
    }

    // ==================================================================
    // 4. Limits are per rule, and only where stated
    // ==================================================================

    /** A limit must not leak onto the other rules of the same table. */
    @Test
    public void anUnlimitedRuleNextToALimitedOneKeepsFiring() {
        Battle battle = battleWith(limited(1, null), unlimited(2));
        Character owner = battle.characters.getFirst();

        Assertions.assertEquals(2, fire(battle, owner), "both rules fire on the first event");
        Assertions.assertEquals(1, fire(battle, owner),
                "only the unlimited rule fires again -- the cooldown is per rule, not per table");
        Assertions.assertEquals(5, battle.getSkillPoints(),
                "1 + 2 on the first event, then the unlimited rule's 2 again (the pool caps at 5, which "
                        + "this sum reaches exactly)");
    }

    /**
     * Two rules may share one provenance string - {@code characters/1403.json} ships exactly that shape,
     * because one trace states two effects. Their limits must stay independent.
     */
    @Test
    public void twoRulesWithTheSameSourceHaveIndependentLimits() {
        String shared = "1403 缇宝 trace 1403103";
        TriggerSpec limited = TriggerSpecs.rule("ALLY_ATTACK", null, shared, gain(1));
        TriggerSpecs.set(limited, "cooldown", 1);
        TriggerSpec unlimited = TriggerSpecs.rule("ALLY_ATTACK", null, shared, gain(2));
        Battle battle = battleWith(limited, unlimited);
        Character owner = battle.characters.getFirst();

        Assertions.assertEquals(2, fire(battle, owner), "both fire the first time");
        Assertions.assertEquals(1, fire(battle, owner),
                "the sibling rule with the same `source` must not be blocked by the other one's cooldown");
    }

    // ==================================================================
    // 5. The counters belong to a combatant, not to the table
    // ==================================================================

    @Test
    public void startingABattleMakesEveryRuleReadyAgain() {
        Battle battle = battleWith(limited(null, true));
        Character owner = battle.characters.getFirst();

        Assertions.assertEquals(1, fire(battle, owner));
        Assertions.assertEquals(0, fire(battle, owner), "precondition: it is spent");

        owner.onBattleStart(battle);        // what Battle.startBattle() calls for every combatant
        Assertions.assertEquals(1, fire(battle, owner),
                "a battle starts with every rule ready; a stale counter would make the mechanic vanish "
                        + "for the rest of the JVM's life");
    }

    // ==================================================================
    // 6. Fail fast: a limit that cannot mean anything is rejected at load
    // ==================================================================

    @Test
    public void aCooldownBelowOneIsRejectedAtLoadTime() {
        IllegalArgumentException zero = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(limited(0, null))));
        Assertions.assertTrue(zero.getMessage().contains("cooldown"), zero.getMessage());

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(limited(-3, null))),
                "0 would read like 'no cooldown', which is already spelled by omitting the field");
    }

    @Test
    public void statingBothLimitsAtOnceIsRejectedAtLoadTime() {
        IllegalArgumentException both = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(limited(2, true))));
        Assertions.assertTrue(both.getMessage().contains("once_per_battle"), both.getMessage());
        Assertions.assertTrue(both.getMessage().contains(TriggerSpecs.TEST_SOURCE),
                "the message must name the rule's source: " + both.getMessage());
    }

    /** {@code per_turn: 0} reads like "no per-turn limit", which is spelled by omitting the field. */
    @Test
    public void aTurnLimitBelowOneIsRejectedAtLoadTime() {
        IllegalArgumentException zero = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(perTurn(0))));
        Assertions.assertTrue(zero.getMessage().contains("per_turn"), zero.getMessage());

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(perTurn(-2))), "and a negative count is not a count");
    }

    /**
     * A per-turn cap next to a cooldown is refused: once {@code per_turn > 1} the two limits would each have to
     * start counting at a different firing, and {@code cooldown: 1} is already the {@code per_turn: 1} case of
     * the same limit - so the pair is never the only way to say something.
     */
    @Test
    public void aTurnLimitNextToACooldownIsRejectedAtLoadTime() {
        TriggerSpec spec = perTurn(2);
        TriggerSpecs.set(spec, "cooldown", 1);

        IllegalArgumentException both = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(spec)));
        Assertions.assertTrue(both.getMessage().contains("per_turn"), both.getMessage());
        Assertions.assertTrue(both.getMessage().contains("cooldown"), both.getMessage());
    }

    // ==================================================================
    // 6. The JSON spelling (a snake_case mistake would bind to null silently)
    // ==================================================================

    @Test
    public void theJsonSpellingBinds() {
        TriggerSpec cooldown = new Gson().fromJson(
                "{\"on\":\"ALLY_ATTACK\",\"cooldown\":2,\"do\":[{\"op\":\"GAIN_ENERGY\",\"amount\":1}]}",
                TriggerSpec.class);
        Assertions.assertEquals(2, cooldown.getCooldown(), "the field is spelled `cooldown`");
        Assertions.assertNull(cooldown.getOncePerBattle());

        TriggerSpec once = new Gson().fromJson(
                "{\"on\":\"ALLY_ATTACK\",\"once_per_battle\":true,\"do\":[{\"op\":\"GAIN_ENERGY\",\"amount\":1}]}",
                TriggerSpec.class);
        Assertions.assertEquals(Boolean.TRUE, once.getOncePerBattle(), "the field is spelled `once_per_battle`");
        Assertions.assertNull(once.getCooldown());

        TriggerSpec perTurn = new Gson().fromJson(
                "{\"on\":\"ALLY_ATTACK\",\"per_turn\":2,\"do\":[{\"op\":\"GAIN_ENERGY\",\"amount\":1}]}",
                TriggerSpec.class);
        Assertions.assertEquals(2, perTurn.getPerTurn(), "the field is spelled `per_turn`");
        Assertions.assertNull(perTurn.getCooldown());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * The rule under test: on any ally's attack, pay 1 skill point.
     *
     * @param cooldown      the {@code cooldown} field, or {@code null} for none
     * @param oncePerBattle the {@code once_per_battle} field, or {@code null} for none
     */
    private static TriggerSpec limited(Integer cooldown, Boolean oncePerBattle) {
        TriggerSpec spec = TriggerSpecs.rule("ALLY_ATTACK", null, gain(1));
        TriggerSpecs.set(spec, "cooldown", cooldown);
        TriggerSpecs.set(spec, "oncePerBattle", oncePerBattle);
        return spec;
    }

    /**
     * The same rule with a per-turn count: "该效果每回合可触发 N 次".
     *
     * @param perTurn how many firings one of the owner's turns allows
     */
    private static TriggerSpec perTurn(Integer perTurn) {
        TriggerSpec spec = TriggerSpecs.rule("ALLY_ATTACK", null, gain(1));
        TriggerSpecs.set(spec, "perTurn", perTurn);
        return spec;
    }

    /** An unlimited rule with a different fingerprint, to prove limits do not leak. */
    private static TriggerSpec unlimited(double points) {
        return TriggerSpecs.rule("ALLY_ATTACK", null, "TriggerLimitTest (unlimited)", gain(points));
    }

    private static EffectSpec gain(double amount) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "GAIN_SKILL_POINT");
        TriggerSpecs.set(effect, "amount", amount);
        return effect;
    }

    private static Battle battleWith(TriggerSpec... specs) {
        Character owner = ownerWith(specs);
        Battle battle = new Battle(List.of(owner), List.of(dummy()), new Random(0));
        battle.startBattle();
        drainSkillPoints(battle);
        return battle;
    }

    private static Battle withAlly(TriggerSpec... specs) {
        Character owner = ownerWith(specs);
        Battle battle = new Battle(List.of(owner, CharacterFactory.create(ALLY, LEVEL)),
                List.of(dummy()), new Random(0));
        battle.startBattle();
        drainSkillPoints(battle);
        return battle;
    }

    private static Character ownerWith(TriggerSpec... specs) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(specs)));
        return owner;
    }

    /**
     * Fires the event and returns how many rules really ran.
     *
     * <p>{@code actor} is who caused it: the owner itself in most cases, an ally in the ones that pin
     * "the limit counts the owner's turns, not whoever triggered the event".
     */
    private static int fire(Battle battle, Character actor) {
        return battle.fireTriggers(TriggerEvent.ALLY_ATTACK, actor, null, 1, 0);
    }

    /**
     * Runs turns until {@code who} is the actor, then settles that turn's start - the moment a cooldown is
     * counted down and a per-turn counter is cleared - and finishes the turn.
     *
     * <p>Delegates to {@link TestTurns} so the shield suite measures the same moment the same way.
     */
    private static void takeTurn(Battle battle, Character who) {
        TestTurns.take(battle, who);
    }

    private static void drainSkillPoints(Battle battle) {
        while (battle.spendSkillPoint()) {
            // drain to zero
        }
        Assertions.assertEquals(0, battle.getSkillPoints(), "precondition: no skill points left");
    }

    private static Enemy dummy() {
        return Enemy.fromAttributes("dummy", 100_000, 100, 100, 100);
    }
}
