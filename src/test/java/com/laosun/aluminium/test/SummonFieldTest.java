package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.RelicTriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.enemy.SummonFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Questions about summons <b>on the field</b>: {@code self_summon_count}, and the {@code "summon"} target
 * selector (P9-4's 忆灵 family).
 *
 * <p><b>Why these two exist and why they need the battle.</b> 「忆灵在场时」 and 「装备者及其忆灵」 are not facts
 * about the event — nothing just happened, or one of the two units named did not act. They are facts about the
 * battlefield, so {@code TriggerContext} now carries the {@link Battle} for exactly these questions (see its
 * javadoc), and a context built without one makes them <b>fail</b> rather than guess.
 *
 * <p><b>Why the condition is spelled {@code self_summon_count}.</b> It counts the owner's own summons, and
 * {@code hp_percent} shows that the owner is normally the unmarked subject. Here the mark stays on, because a
 * bare {@code summon_count} reads like "how many summons are on the battlefield" — a different question with a
 * different answer that nobody has asked for yet.
 *
 * <p><b>The acceptance content is relic set 318</b> (奇想蕉乐园): its 2-piece grants the wearer an extra 32%
 * CRIT DMG 「当存在装备者召唤的目标时」. Its file is authored with the "while" technique — re-evaluated at each
 * of the wearer's turn starts for one turn — because a {@code permanent} buff would outlive the summon.
 */
public class SummonFieldTest {
    private static final double EPS = 1e-6;

    /** 阿格莱雅 — has a memosprite spec (1402), and no other test uses her id. */
    private static final int AGLAEA = 1402;
    /** 姬子 — our plain character, no memosprite. */
    private static final int PLAIN = 1003;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    /** 银鬃近卫, the monster id used for an enemy-side summon. */
    private static final int MINION = 1002040;

    /** 奇想蕉乐园 — the set whose 2-piece is authored with {@code self_summon_count}. */
    private static final int BANANA_PARADISE = 318;

    // ==================================================================
    // 1. Counting the owner's own summons
    // ==================================================================

    /** The count is per owner: my summon is mine, a teammate's is not. */
    @Test
    public void theCountIsOfTheOwnersOwnSummons() {
        Character owner = CharacterFactory.create(AGLAEA, LEVEL);
        Character teammate = CharacterFactory.create(PLAIN, LEVEL);
        Battle battle = new Battle(List.of(owner, teammate), List.of(monster()), new Random(0));

        Assertions.assertEquals(0, battle.summonCountOf(owner), "nothing is out yet");
        battle.summonMemosprite(owner);

        Assertions.assertEquals(1, battle.summonCountOf(owner));
        Assertions.assertEquals(0, battle.summonCountOf(teammate),
                "a teammate's memosprite is not mine -- 「装备者的忆灵」 is possessive");
    }

    /** A dead summon stops counting: the roster keeps corpses, the count does not. */
    @Test
    public void aDeadSummonIsNotCounted() {
        Fixture f = fixture();

        Assertions.assertEquals(1, f.battle.summonCountOf(f.owner), "precondition: one is out");
        f.memosprite.takeDamage(9_999_999);

        Assertions.assertTrue(f.memosprite.isDeath());
        Assertions.assertEquals(0, f.battle.summonCountOf(f.owner), "a corpse is not 'on the field'");
        Assertions.assertNull(f.battle.summonOf(f.owner));
    }

    /** It counts all of them, not just the first — 知更鸟·晴歌's 晴空乐手 is a trio. */
    @Test
    public void severalSummonsAreAllCounted() {
        Fixture f = fixture();
        Summon second = SummonFactory.memosprite(f.owner);
        second.setMaster(f.owner);
        f.battle.allies.add(second);

        Assertions.assertEquals(2, f.battle.summonCountOf(f.owner),
                "a query that stopped at the first match would silently cap this at one");
        Assertions.assertSame(f.memosprite, f.battle.summonOf(f.owner), "the first one, in roster order");
    }

    /** An enemy's minion counts for the enemy, through the same query. */
    @Test
    public void anEnemySummonCountsForTheEnemy() {
        Character hero = CharacterFactory.create(PLAIN, LEVEL);
        Enemy master = EnemyFactory.create(1003010, 90, 1);
        Battle battle = new Battle(List.of(hero), List.of(master), new Random(0));

        Summon minion = battle.summon(master, MINION, 1);

        Assertions.assertEquals(1, battle.summonCountOf(master), "the count is not a player-side thing");
        Assertions.assertSame(minion, battle.summonOf(master));
        Assertions.assertEquals(0, battle.summonCountOf(hero));
    }

    // ==================================================================
    // 2. The condition gates a rule
    // ==================================================================

    /** A rule gated on the count fires with a summon out and stays quiet without one. */
    @Test
    public void theConditionGatesTheRule() {
        Assertions.assertEquals(0, fireWithSummon(false), "no summon, no fire");
        Assertions.assertEquals(1, fireWithSummon(true), "one out, and the rule fires");
    }

    /**
     * A context with no battlefield makes the condition <b>fail</b>, never "0 summons".
     *
     * <p>The distinction is the whole point, and it needs a <b>negative</b> question to be visible: for
     * {@code >= 1} the two readings agree by accident (0 is not >= 1 either), so a "cannot tell" that
     * degraded to 0 would pass that gate silently. Asking {@code == 0} is what tells them apart — it must not
     * match either, because the honest answer to "how many summons do I have" without a battlefield is
     * "unknown", and a rule gated on 「忆灵在场时」 that read unknown as "none out" would be silently disabled.
     */
    @Test
    public void aContextWithoutABattleFailsTheCondition() {
        Character owner = CharacterFactory.create(AGLAEA, LEVEL);
        TriggerTable positive = new TriggerTable(AGLAEA, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of("self_summon_count >= 1"),
                        TriggerSpecs.modifyAttr("ATTACK", 0.5, 1))));
        TriggerTable negative = new TriggerTable(AGLAEA, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of("self_summon_count == 0"),
                        TriggerSpecs.modifyAttr("ATTACK", 0.5, 1))));

        // A hand-built context, exactly as the other condition tests build them: no battle.
        TriggerTable.TriggerContext noBattle =
                new TriggerTable.TriggerContext(owner, owner, null, 0, 0);

        Assertions.assertTrue(positive.matching(TriggerEvent.BATTLE_START, noBattle).isEmpty(),
                "without a battlefield the answer is 'cannot tell', which fails the rule");
        Assertions.assertTrue(negative.matching(TriggerEvent.BATTLE_START, noBattle).isEmpty(),
                "and it must not read as ZERO either -- 'unknown' and 'none' are different answers, and "
                        + "conflating them would silently disable every 「忆灵在场时」 rule");
    }

    // ==================================================================
    // 3. Addressing the summon as a target
    // ==================================================================

    /** {@code target: "summon"} applies the effect to the memosprite, not to the wearer. */
    @Test
    public void theSummonSelectorAddressesTheMemosprite() {
        Fixture f = fixture();
        double memospriteBefore = f.memosprite.getAttribute(AttributeType.ATTACK).get();
        double ownerBefore = f.owner.getAttribute(AttributeType.ATTACK).get();

        f.owner.setTriggerTable(new TriggerTable(AGLAEA, List.of(summonAttackRule())));
        f.battle.fireTriggers(TriggerEvent.BATTLE_START, f.owner, null, 0, 0);

        Assertions.assertEquals(memospriteBefore * 1.5,
                f.memosprite.getAttribute(AttributeType.ATTACK).get(), 1e-6,
                "「装备者及其忆灵」 names two units, and this is the second one");
        Assertions.assertEquals(ownerBefore, f.owner.getAttribute(AttributeType.ATTACK).get(), EPS,
                "and the wearer is untouched by this rule");
    }

    /**
     * Naming a summon that is not there is a <b>loud</b> failure that says how to fix the rule.
     *
     * <p>An author who writes 「装备者及其忆灵」 without a {@code self_summon_count >= 1} gate has a rule that fires
     * exactly when the unit it names is absent. Silence would leave a wrong state; the message names the
     * condition to add.
     */
    @Test
    public void theSummonSelectorFailsLoudlyWhenNothingIsOut() {
        Character owner = CharacterFactory.create(AGLAEA, LEVEL);
        Battle battle = new Battle(List.of(owner), List.of(monster()), new Random(0));
        battle.startBattle();                       // start with the table empty, so nothing fires inside it
        owner.setTriggerTable(new TriggerTable(AGLAEA, List.of(summonAttackRule())));

        IllegalStateException refused = Assertions.assertThrows(IllegalStateException.class,
                () -> battle.fireTriggers(TriggerEvent.BATTLE_START, owner, null, 0, 0));

        Assertions.assertTrue(refused.getMessage().contains("self_summon_count >= 1"),
                "the message has to say what to add: " + refused.getMessage());
    }

    // ==================================================================
    // 4. The shipped content: relic set 318
    // ==================================================================

    /**
     * The authored 2-piece really grants its extra CRIT DMG only while the wearer has a summon out.
     *
     * <p>End-to-end through the relic loader and the real turn boundary: the wearer's own turn start is what
     * re-evaluates the condition, so the buff is up for the turn in which her own attacks land.
     *
     * <p>⚠ Measured as a <b>delta against the same character without the set</b>, never as an absolute: her
     * sheet already carries a CRIT DMG of its own, and asserting {@code 0.48} would be asserting that base to
     * be zero. The two deltas are the two clauses — 0.16 from the set's {@code properties} plus 0.32 from the
     * rule when a summon is out, and 0.16 alone when it is not.
     */
    @Test
    public void theBananaParadiseExtraNeedsASummonOnTheField() {
        double bare = critDamageAtOwnTurnStart(false, false);
        double withSummon = critDamageAtOwnTurnStart(true, true);
        double withoutSummon = critDamageAtOwnTurnStart(true, false);

        Assertions.assertEquals(0.48, withSummon - bare, EPS,
                "0.16 (properties) + 0.32 (the rule's conditional half)");
        Assertions.assertEquals(0.16, withoutSummon - bare, EPS,
                "with no summon only the unconditional stat is left");
        Assertions.assertTrue(withSummon > withoutSummon,
                "so the extra really depends on the summon: " + withSummon + " vs " + withoutSummon);
    }

    /** The 2-piece is filed at the 2-piece tier and states the parameters from the text. */
    @Test
    public void theBananaParadiseRuleStatesItsOwnNumbers() {
        Fixture f = fixture();
        f.battle.startBattle();
        // The context has to be a real one: `matching` evaluates the conditions, and this rule's conditions
        // include "does the owner have a summon out" -- a hand-built context would match nothing and the
        // assertions below would be about an empty list.
        List<TriggerTable.CompiledRule> rules = RelicTriggerTables.of(BANANA_PARADISE).at(2)
                .matching(TriggerEvent.TURN_START,
                        new TriggerTable.TriggerContext(f.owner, f.owner, null, 0, 0, null, f.battle));

        Assertions.assertEquals(1, rules.size());
        Assertions.assertEquals(2, rules.getFirst().conditions().size(),
                "「装备者」 and 「存在召唤的目标」 are two separate facts");
        EffectSpec effect = rules.getFirst().effects().getFirst();
        Assertions.assertEquals("CRIT_ATTACK", effect.getAttribute());
        Assertions.assertEquals(0.32, effect.getPercent(), EPS, "param #2 is 0.32, not 0.16");
        Assertions.assertEquals(1, effect.getTurns(),
                "re-evaluated each turn rather than `permanent`, so it cannot outlive the summon");
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    private record Fixture(Battle battle, Character owner, Summon memosprite) {
    }

    /** A battle with one memosprite already on the field (before the battle starts). */
    private static Fixture fixture() {
        Character owner = CharacterFactory.create(AGLAEA, LEVEL);
        Battle battle = new Battle(List.of(owner), List.of(monster()), new Random(0));
        Summon memosprite = battle.summonMemosprite(owner);
        battle.processRequests();
        return new Fixture(battle, owner, memosprite);
    }

    /** How many rules fire for an owner that does or does not have a memosprite out. */
    private static int fireWithSummon(boolean withSummon) {
        Character owner = CharacterFactory.create(AGLAEA, LEVEL);
        Battle battle = new Battle(List.of(owner), List.of(monster()), new Random(0));
        if (withSummon) {
            battle.summonMemosprite(owner);
        }
        owner.setTriggerTable(new TriggerTable(AGLAEA, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of("self_summon_count >= 1"),
                        TriggerSpecs.modifyAttr("ATTACK", 0.1, 1)))));
        return battle.fireTriggers(TriggerEvent.BATTLE_START, owner, null, 0, 0);
    }

    /** A rule that buffs 「我的忆灵」 — the {@code target: "summon"} selector. */
    private static TriggerSpec summonAttackRule() {
        EffectSpec effect = TriggerSpecs.modifyAttr("ATTACK", 0.5, 2);
        TriggerSpecs.set(effect, "target", "summon");
        return TriggerSpecs.rule("BATTLE_START", null, effect);
    }

    /**
     * The wearer's total CRIT DMG after her own turn has begun.
     *
     * <p>Turn start is where the authored rule re-evaluates, so the case drives the real path rather than
     * firing the event by hand: {@code stepForward()} opens the turn and {@code beforeMove()} is what emits
     * {@code TURN_START} (and it does nothing at all when no turn is in progress, which is why both are
     * needed). {@code withSet} false measures the same character bare — the baseline the deltas are taken
     * against.
     */
    private static double critDamageAtOwnTurnStart(boolean withSet, boolean withSummon) {
        Character wearer = withSet
                ? CharacterFactory.create(AGLAEA, LEVEL, true, null,
                        RelicFactory.suit(BANANA_PARADISE, 5, 15))
                : CharacterFactory.create(AGLAEA, LEVEL);
        // Make the wearer unambiguously first, so the turn this case opens is hers.
        wearer.setAttribute(AttributeType.SPEED, new DoubleValue(10_000));
        Battle battle = new Battle(List.of(wearer), List.of(monster()), new Random(0));
        if (withSummon) {
            battle.summonMemosprite(wearer);
        }
        battle.startBattle();
        battle.stepForward();                       // opens the turn; beforeMove() is what emits TURN_START
        Assertions.assertSame(wearer, battle.currentMove.getCanHit(),
                "precondition: the first turn is the wearer's");
        battle.beforeMove();
        return wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
    }

    private static Enemy monster() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }
}
