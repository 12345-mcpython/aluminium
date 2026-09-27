package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * {@code SUMMONED} — 「被召唤时」, and the reason its timing is part of the contract.
 *
 * <p><b>What the event is for.</b> 长夜月's 忆灵技能3 says 「被召唤时，使自身立即行动」. "Act immediately" was already
 * expressible — {@code ADVANCE percent: 1.0} skips all of a unit's remaining time to act, which is the engine's
 * own reading of 立即行动 — but nothing could fire <em>on arrival</em>, because a summon entering the field was not
 * an event. That is the whole gap this closes.
 *
 * <p><b>Why the timing is the interesting part.</b> A summon enters the action bar through
 * {@code addRequestItems}, which {@code processRequests} drains, so during the summon call itself the unit is on
 * the roster but <b>not yet scheduled</b> — and the one thing a 「被召唤时」 rule almost always wants to do is touch
 * its action value. Firing the event at the call site would therefore hand every such rule a unit whose
 * {@code ADVANCE} is silently dropped: the rule runs, nothing happens, and the log says nothing. So the event is
 * fired at the settle point instead, and the case that pins it is a memosprite so slow that "acts immediately" is
 * the difference between acting first and acting last.
 *
 * <p><b>What else is pinned.</b> That it fires once (and not again on a later settle), that it does not fire for a
 * <em>teammate's</em> summon entering ({@code actor == summon}), and that summoning while one is already out
 * fires nothing — which is what 「若已在场，则…」 clauses depend on.
 */
public class SummonEntryTest {
    private static final double EPS = 1e-6;

    /** 长夜月 — her 忆灵技能3 is the shipped user of this event. */
    private static final int OWNER = 1413;
    /** 阿格莱雅 — the second memosprite owner, for "a teammate's summon does not count". */
    private static final int TEAMMATE = 1402;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    // ==================================================================
    // 1. It fires once, for the summon, at the right moment
    // ==================================================================

    /** Once per arrival, with the summoned unit as the actor. */
    @Test
    public void itFiresOnceWithTheSummonAsActor() {
        Character owner = characterWith(rule("SUMMONED", List.of("actor == summon"),
                gainResource("charge")));
        owner.getResources().register("charge", 10, 0);
        Battle battle = new Battle(List.of(owner), List.of(dummy()), new Random(0));

        battle.summonMemosprite(owner);
        battle.processRequests();

        Assertions.assertEquals(1, owner.getResources().value("charge"),
                "one arrival, one event -- and the actor was the owner's summon");
        battle.processRequests();
        Assertions.assertEquals(1, owner.getResources().value("charge"),
                "a later settle does not re-announce an arrival that already happened");
    }

    /**
     * The unit is already in the action bar when the event fires — otherwise this rule is silently dropped.
     *
     * <p>The summon is made deliberately slow (speed 1) before it is scheduled, so it would act <b>last</b> on its
     * own: acting first is only possible if the {@code ADVANCE} found it in the queue. That is the difference
     * between firing the event at the summon call and firing it at the settle.
     */
    @Test
    public void theSummonIsScheduledBeforeTheEventFires() {
        Character owner = characterWith(rule("SUMMONED", List.of("actor == summon"), advanceSummon()));
        Battle battle = new Battle(List.of(owner), List.of(dummy()), new Random(0));

        Summon minion = battle.summon(owner, MONSTER, 1);
        minion.setAttribute(AttributeType.SPEED, new DoubleValue(1));   // would act last on its own
        battle.processRequests();

        Assertions.assertSame(minion, battle.getQueueSnapshot().getFirst().getCanHit(),
                "「立即行动」 put it at the head of the action bar, so the advance did find it");
    }

    /** A summon that is not the rule owner's does not fire the rule. */
    @Test
    public void aTeammatesSummonDoesNotCount() {
        Character owner = characterWith(rule("SUMMONED", List.of("actor == summon"),
                gainResource("charge")));
        owner.getResources().register("charge", 10, 0);
        Character teammate = CharacterFactory.create(TEAMMATE, LEVEL);
        Battle battle = new Battle(List.of(owner, teammate), List.of(dummy()), new Random(0));

        battle.summonMemosprite(teammate);
        battle.processRequests();

        Assertions.assertEquals(0, owner.getResources().value("charge"),
                "a teammate's memosprite entering is not mine entering");
    }

    /** Summoning while one is already out is a no-op, so there is no arrival to announce. */
    @Test
    public void summoningAgainWhileOneIsOutAnnouncesNothing() {
        Character owner = characterWith(rule("SUMMONED", List.of("actor == summon"),
                gainResource("charge")));
        owner.getResources().register("charge", 10, 0);
        Battle battle = new Battle(List.of(owner), List.of(dummy()), new Random(0));

        battle.summonMemosprite(owner);
        battle.processRequests();
        battle.summonMemosprite(owner);                   // idempotent per summoner: the same one stays
        battle.processRequests();

        Assertions.assertEquals(1, battle.summonCountOf(owner));
        Assertions.assertEquals(1, owner.getResources().value("charge"),
                "「若已在场，则…」 clauses depend on this: no arrival, no event");
    }

    // ==================================================================
    // 2. The shipped content: 忆灵技能3
    // ==================================================================

    /**
     * The authored rule is an arrival rule for the owner's own summon, advancing it all the way.
     *
     * <p>⚠ Her file answers 被召唤时 <b>twice</b> now, and both rules are 「被召唤时」 sentences from the document:
     * 忆灵技能3 「被召唤时，使自身立即行动」 (the ADVANCE pinned here) and 忆灵技能2 「「长夜」免疫控制类负面状态」
     * (granted to the memosprite as it arrives — see {@code MemospriteTest}). So the shape assertions select the
     * ADVANCE rule by its op instead of taking the first one, which is what keeps this case about <b>this</b>
     * sentence rather than about file order.
     */
    @Test
    public void theAuthoredRuleStatesItsShape() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Battle battle = new Battle(List.of(owner), List.of(dummy()), new Random(0));

        List<TriggerTable.CompiledRule> rules = TriggerTables.of(OWNER).matching(TriggerEvent.SUMMONED,
                new TriggerTable.TriggerContext(owner, battle.summonMemosprite(owner), null, 0, 0, null, battle));

        Assertions.assertEquals(2, rules.size(), "忆灵技能3 and 忆灵技能2 both answer 被召唤时");
        TriggerTable.CompiledRule arrival = rules.stream()
                .filter(rule -> "ADVANCE".equals(rule.effects().getFirst().getOp()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("忆灵技能3's 立即行动 rule is gone: " + rules));
        Assertions.assertEquals(List.of("actor == summon"), arrival.conditions().stream()
                .map(TriggerTable.Condition::source).toList());
        EffectSpec effect = arrival.effects().getFirst();
        Assertions.assertEquals("ADVANCE", effect.getOp());
        Assertions.assertEquals(1.0, effect.getPercent(), EPS, "all of its remaining time: 立即行动");
        Assertions.assertEquals("summon", effect.getTarget(), "「使自身」 is the memosprite");
    }

    /**
     * End to end: 长夜月 starts the battle, her talent summons 「长夜」, and it is the next unit to act.
     *
     * <p>Measured as position in the action bar rather than as a remaining-time number: 160 speed would put it
     * near the front anyway, so what the assertion has to show is that nothing is ahead of it.
     */
    @Test
    public void theAuthoredRuleMakesTheMemospriteActFirst() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Battle battle = new Battle(List.of(owner), List.of(dummy()), new Random(0));

        battle.startBattle();

        Summon evey = battle.memospriteOf(owner);
        Assertions.assertNotNull(evey, "precondition: her talent summoned it at battle start");
        Assertions.assertEquals(0, timeRemaining(battle, evey), EPS,
                "its action value was pushed to zero, so it acts immediately");
        Assertions.assertSame(evey, battle.getQueueSnapshot().getFirst().getCanHit());
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    private static Character characterWith(TriggerSpec... rules) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(rules)));
        return owner;
    }

    private static TriggerSpec rule(String event, List<String> when, EffectSpec... effects) {
        return TriggerSpecs.rule(event, when, effects);
    }

    private static EffectSpec gainResource(String id) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(effect, "resource", id);
        TriggerSpecs.set(effect, "amount", 1.0);
        return effect;
    }

    /** 「使自身立即行动」: skip all of the summon's remaining time to act. */
    private static EffectSpec advanceSummon() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "ADVANCE");
        TriggerSpecs.set(effect, "percent", 1.0);
        TriggerSpecs.set(effect, "target", "summon");
        return effect;
    }

    /** How much action value the unit still has — zero means "acts now". */
    private static double timeRemaining(Battle battle, CanHit target) {
        for (Signal signal : battle.queue.snapshot()) {
            if (signal.getCanHit() == target) {
                return battle.queue.getTimeRemaining(signal);
            }
        }
        return Double.NaN;
    }

    private static Enemy dummy() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }
}
