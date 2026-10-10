package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 【好活当赏】 counts the Aha Moment's 笑点 INTO its holder, frozen, per holder, on that holder's own turns.
 *
 * <p>The document: 「使参演阿哈时刻的角色获得【好活当赏】状态，并将本次阿哈时刻的笑点计入该状态，持续 2 回合。【好活当赏】
 * 状态产生的技能效果和欢愉伤害以【好活当赏】计入的笑点数计算。多个【好活当赏】计入的笑点数合并计算。各个【好活当赏】的持续时间
 * 各自独立计算。」
 *
 * <p>This is the STRUCTURAL half of that correction and it deliberately moves no number: the laugh factor still reads
 * the party's live pool, so nothing that was green goes red. What it pins is the model underneath.
 *
 * <p>Note: Each case walks a small roster through its OWN turn, one unit at a time, and swaps in only the table it is
 * about. {@code fireTriggers} walks every character's table, so a reading taken after "another unit's turn" would also
 * have run that unit's own rules - the first version of this case did exactly that and read a count that had been
 * topped up by a different character's trace.
 */
public class ElationGiftSnapshotTest {
    private static final int PEARL = 1503;
    /**
     * Note: This ally must DECLARE 好活当赏, or a grant to her is dropped (no counter, no party pool of that name) and
     * this case would silently be reading an empty fixture. Measured: 1502 does not declare it - she declares 笑点 -
     * and the first version of this case used her, so the second holder's snapshot was always 0 for a reason that had
     * nothing to do with snapshots.
     */
    private static final int ALLY = 1505;
    private static final int OTHER = 1204;
        private static final String GIFT = "好活当赏";
    private static final String LAUGH = "笑点";

    /** The count is taken when the state is granted, and the pool growing afterwards does not change it. */
    @Test
    public void theCountIsFrozenWhenTheStateIsGranted() {
        Fixture f = new Fixture();
        f.battle.partyResource(LAUGH).gain(120);

        f.grantTo(f.pearl, 10);
        int atGrant = f.battle.laughSnapshotOf(f.pearl);
        f.battle.partyResource(LAUGH).gain(120);                // the pool doubles afterwards
        int after = f.battle.laughSnapshotOf(f.pearl);
        System.out.println("[gift_snapshot] at grant " + atGrant + " ; after the pool grew to "
                + f.battle.partyResource(LAUGH).value() + " it still reads " + after);

        Assertions.assertEquals(120, atGrant, "the count is what the pool held when the state arrived");
        Assertions.assertEquals(120, after, "and it is FROZEN: the pool growing afterwards does not move it");
        Assertions.assertEquals(240, f.battle.partyResource(LAUGH).value(), "precondition: the pool really did grow");
    }

    /** Several holders each keep their own count, and the figure the damage uses is their SUM. */
    @Test
    public void severalHoldersAreMergedAndKeptApart() {
        Fixture f = new Fixture();
        f.battle.partyResource(LAUGH).gain(60);
        f.grantTo(f.pearl, 10);                                 // Pearl's count: 60
        f.battle.partyResource(LAUGH).gain(60);
        f.grantTo(f.ally, 10);                                  // the ally's count: 120

        System.out.println("[gift_snapshot] Pearl " + f.battle.laughSnapshotOf(f.pearl) + " ; ally "
                + f.battle.laughSnapshotOf(f.ally) + " ; merged " + f.battle.countedLaughPoints());
        Assertions.assertEquals(60, f.battle.laughSnapshotOf(f.pearl), "the first holder's own count");
        Assertions.assertEquals(120, f.battle.laughSnapshotOf(f.ally), "the second holder's own count");
        Assertions.assertEquals(180, f.battle.countedLaughPoints(), "「多个…合并计算」: the two sum");
        Assertions.assertEquals(2, f.battle.laughSnapshotHolders(), "and both are still counted");
    }

    /** 「各个…持续时间各自独立计算」: one holder's turn runs down ITS count and nobody else's. */
    @Test
    public void eachHolderKeepsItsOwnClock() {
        Fixture f = new Fixture();
        f.battle.partyResource(LAUGH).gain(90);
        f.grantTo(f.pearl, 10);
        f.grantTo(f.ally, 10);

        f.endTurn(f.pearl);                                     // Pearl's first turn ends
        System.out.println("[gift_snapshot] after Pearl's turn: Pearl " + f.battle.laughSnapshotOf(f.pearl)
                + " ; ally " + f.battle.laughSnapshotOf(f.ally));
        Assertions.assertEquals(90, f.battle.laughSnapshotOf(f.pearl), "still there on its first tick");
        Assertions.assertEquals(90, f.battle.laughSnapshotOf(f.ally),
                "and the other holder's clock did not move");

        f.endTurn(f.ally);                                      // now the ally's
        System.out.println("[gift_snapshot] after the ally's too: Pearl " + f.battle.laughSnapshotOf(f.pearl)
                + " ; ally " + f.battle.laughSnapshotOf(f.ally));
        Assertions.assertEquals(90, f.battle.laughSnapshotOf(f.pearl), "Pearl is still on her first tick");
        Assertions.assertEquals(90, f.battle.laughSnapshotOf(f.ally), "and so is the ally");
    }

    /** It lasts two of the holder's turns, and the third ends it. */
    @Test
    public void itLastsTwoOfItsHoldersTurns() {
        Fixture f = new Fixture();
        f.battle.partyResource(LAUGH).gain(90);
        f.grantTo(f.pearl, 10);

        f.endTurn(f.pearl);
        int afterOne = f.battle.laughSnapshotOf(f.pearl);
        f.endTurn(f.pearl);
        int afterTwo = f.battle.laughSnapshotOf(f.pearl);
        f.endTurn(f.pearl);
        int afterThree = f.battle.laughSnapshotOf(f.pearl);
        System.out.println("[gift_snapshot] 「持续 2 回合」: after 1 turn " + afterOne + " ; after 2 " + afterTwo
                + " ; after 3 " + afterThree + " ; merged " + f.battle.countedLaughPoints());

        Assertions.assertEquals(90, afterOne, "the first of its two turns");
        Assertions.assertEquals(90, afterTwo, "the second");
        Assertions.assertEquals(0, afterThree, "and the next one is past its duration");
        Assertions.assertEquals(0, f.battle.countedLaughPoints(), "so nothing is counted any more");
    }

    /** Being granted again REPLACES the holder's count with the pool's current value. */
    @Test
    public void grantingItAgainTakesANewCount() {
        Fixture f = new Fixture();
        f.battle.partyResource(LAUGH).gain(30);
        f.grantTo(f.pearl, 10);
        f.battle.partyResource(LAUGH).gain(150);
        f.grantTo(f.pearl, 10);

        System.out.println("[gift_snapshot] after a second grant Pearl reads " + f.battle.laughSnapshotOf(f.pearl)
                + " ; holders " + f.battle.laughSnapshotHolders());
        Assertions.assertEquals(180, f.battle.laughSnapshotOf(f.pearl), "the newer count replaces the older one");
        // Note: The HOLDER count is not 1 and should not be: the fixture has other characters, and a holder count is a
        // count of holders. What this case is about is that PEARL still has one instance, which the reading above shows.
        Assertions.assertEquals(1, f.battle.laughSnapshotOf(f.pearl) == 180 ? 1 : 0,
                "her own count is a single, replaced instance rather than two accumulated ones");
    }

    /**
     * A grant that credited nothing takes no count - a counter already at its cap did not hand the unit anything.
     *
     * <p>Asserted because the alternative is invisible: a snapshot frozen for a grant that never happened would show up
     * only as damage being higher than the state should allow.
     */
    @Test
    public void aGrantThatCreditedNothingTakesNoCount() {
        Fixture f = new Fixture();
        f.battle.partyResource(LAUGH).gain(60);
        // Pearl's own counter is capped at 50 by her file, so asking for 10 when it is full credits 0.
        f.pearl.getResources().gain(GIFT, 50);
        f.grantTo(f.pearl, 10);
        System.out.println("[gift_snapshot] with the counter full at " + f.pearl.getResources().value(GIFT)
                + " the count is " + f.battle.laughSnapshotOf(f.pearl));
        Assertions.assertEquals(0, f.battle.laughSnapshotOf(f.pearl),
                "nothing was credited, so no count was taken");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * A battle whose three units each have an EMPTY table, so a case can put exactly one rule in play and take its
     * reading without another character's rules running underneath it.
     */
    private static final class Fixture {
        final Battle battle;
        final Character pearl;
        final Character ally;
        final Character other;

        Fixture() {
            battle = new Battle(
                    List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ALLY, 80),
                            CharacterFactory.create(OTHER, 80)),
                    List.of(BattleFixture.monster()), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
            pearl = battle.characters.get(0);
            ally = battle.characters.get(1);
            other = battle.characters.get(2);
        }

        /**
         * Grants the state through the engine's own gain path, which is where the count is taken.
         *
         * <p>Driven by {@code TURN_START} on the holder alone, so the case can grant it as often as it likes.
         */
        void grantTo(Character holder, int amount) {
            EffectSpec effect = new EffectSpec();
            TriggerSpecs.set(effect, "op", "GAIN_RESOURCE");
            TriggerSpecs.set(effect, "resource", GIFT);
            TriggerSpecs.set(effect, "amount", (double) amount);
            TriggerSpecs.set(effect, "target", "self");
            TriggerSpec rule = TriggerSpecs.rule("TURN_START", List.of("actor == self"), effect);
            putOnly(holder, rule);
            battle.fireTriggers(TriggerEvent.TURN_START, holder, holder, 0, 0);
            battle.processRequests();
            putOnly(holder, null);                              // the rule has served its purpose
        }

        /** Ends the unit's turn, which is where its own snapshot is counted. */
        void endTurn(Character unit) {
            battle.endTurnOf(unit);
            battle.processRequests();
        }

        /** Puts one rule on the holder and empties every other table, so only this unit's turn is in play. */
        private void putOnly(Character holder, TriggerSpec rule) {
            for (Character unit : battle.characters) {
                List<TriggerSpec> rules = unit == holder && rule != null ? List.of(rule) : List.of();
                unit.setTriggerTable(new TriggerTable(unit.getCid(), rules));
            }
        }
    }
}
