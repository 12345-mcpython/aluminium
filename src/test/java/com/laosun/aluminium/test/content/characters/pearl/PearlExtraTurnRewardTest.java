package com.laosun.aluminium.test.content.characters.pearl;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Character 1503 (Pearl): the extra turn her ultimate grants pays 30 好活当赏 and 60 笑点, and takes them back when that
 * turn ends.
 *
 * <p>「该额外回合开始时，【美学底本】获得 30 点【好活当赏】，并获得 60 点笑点，额外回合结束时移除」. Her ultimate's own row carries
 * both numbers (150303: #8 = 30, #7 = 60, constant at every level).
 *
 * <p><b>Who the 30 is paid TO, and why it is Pearl.</b> The sentence reads as the archetype, and the first version of
 * this case asserted exactly that - and it was measured to add ZERO, because GAIN_RESOURCE onto a unit that does not
 * declare the counter lands nowhere. The encyclopedia settles the model: 「使参演阿哈时刻的角色获得【好活当赏】<b>状态</b>，
 * 并将本次阿哈时刻的笑点计入该状态…多个【好活当赏】计入的笑点数<b>合并计算</b>」 - so 好活当赏 is ONE count (expressed here as a
 * resource, whose declaration and whose spender are both Pearl), and the clause adds to it. The CONDITION still speaks
 * about the archetype (`actor has_state 美学底本`): the question is whose extra turn it is, the payment is into the
 * count. Two different units, each named by the part that is about it.
 *
 * <p>The removal is read on three counter states, because "take back the 30" has three different answers and only one
 * of them is about arithmetic: untouched, partly spent (the author's ruling, 「撤回剩余的」), and already empty.
 */
public class PearlExtraTurnRewardTest {
    private static final int PEARL = 1503;
    private static final int ELATION_ALLY = 1502;
    private static final String COUNTER = "好活当赏";
    private static final String PUNCHLINE = "笑点";

    /**
     * The turn begins: the count gains 30 and the team gains 60 punchline.
     */
    @Test
    public void theExtraTurnPaysThirtyAndSixty() {
        Battle battle = newBattle();
        Character pearl = battle.characters().getFirst();
        Character archetype = battle.characters().get(1);
        int partyBefore = battle.partyResource(PUNCHLINE).value();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.EXTRA_TURN_STARTED, archetype, archetype, 0, 0));
        battle.processRequests();

        int gained = pearl.getResources().value(COUNTER);
        int partyGained = battle.partyResource(PUNCHLINE).value() - partyBefore;
        System.out.println("[pearl_extra_turn] the count gained " + gained + " ; the party gained " + partyGained
                + " punchline");
        Assertions.assertEquals(30, gained, "【好活当赏】获得 30 点");
        Assertions.assertEquals(60, partyGained, "and 60 points of punchline");
    }

    /**
     * The turn ends with the reward untouched: it comes straight back off.
     */
    @Test
    public void theRewardIsTakenBackWhenTheTurnEnds() {
        Battle battle = newBattle();
        Character pearl = battle.characters().getFirst();
        Character archetype = battle.characters().get(1);
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.EXTRA_TURN_STARTED, archetype, archetype, 0, 0));
        battle.processRequests();
        int partyAfterGrant = battle.partyResource(PUNCHLINE).value();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.EXTRA_TURN_ENDED, archetype, archetype, 0, 0));
        battle.processRequests();

        int left = pearl.getResources().value(COUNTER);
        int partyLost = partyAfterGrant - battle.partyResource(PUNCHLINE).value();
        System.out.println("[pearl_extra_turn] after the turn: the count holds " + left + " ; the party lost "
                + partyLost + " punchline");
        Assertions.assertEquals(0, left, "the granted 30 is removed");
        Assertions.assertEquals(60, partyLost, "and so is the granted 60");
    }

    /**
     * The turn ends with part of the reward already spent: what is left is taken and no more.
     *
     * <p>The author's ruling (「花掉部分就撤回剩余的」) and the reason the removal cannot be a strict spend - a strict one
     * would either error or demand points that are not there.
     */
    @Test
    public void aPartlySpentRewardIsTakenBackUpToWhatIsLeft() {
        Battle battle = newBattle();
        Character pearl = battle.characters().getFirst();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.EXTRA_TURN_STARTED, pearl, pearl, 0, 0));
        battle.processRequests();
        pearl.getResources().spend(COUNTER, 12);                // 30 granted, 12 already used

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.EXTRA_TURN_ENDED, pearl, pearl, 0, 0));
        battle.processRequests();

        int left = pearl.getResources().value(COUNTER);
        System.out.println("[pearl_extra_turn] 30 granted, 12 spent, " + left + " left after the removal");
        Assertions.assertEquals(0, left,
                "the removal clamps to what is there (18) rather than failing or leaving a residue");
    }

    /**
     * The turn ends with the counter already empty: nothing happens and nothing errors.
     */
    @Test
    public void anAlreadyEmptyRewardIsNotAnError() {
        Battle battle = newBattle();
        Character pearl = battle.characters().getFirst();
        Character archetype = battle.characters().get(1);
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.EXTRA_TURN_STARTED, archetype, archetype, 0, 0));
        battle.processRequests();
        pearl.getResources().spend(COUNTER, 30);                // all of it gone

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.EXTRA_TURN_ENDED, archetype, archetype, 0, 0));
        battle.processRequests();

        System.out.println("[pearl_extra_turn] removal against an empty count left "
                + pearl.getResources().value(COUNTER));
        Assertions.assertEquals(0, pearl.getResources().value(COUNTER),
                "an empty count stays empty rather than the removal failing");
    }

    /**
     * A unit that is NOT the archetype gets nothing, which is what the clause's own condition says.
     */
    @Test
    public void aUnitWithoutTheArchetypeMarkIsNotPaid() {
        Battle battle = newBattle();
        Character pearl = battle.characters().getFirst();
        Character hunter = battle.characters().get(2);
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.EXTRA_TURN_STARTED, hunter, hunter, 0, 0));
        battle.processRequests();
        System.out.println("[pearl_extra_turn] the Hunt ally's extra turn paid " + pearl.getResources().value(COUNTER));
        Assertions.assertEquals(0, pearl.getResources().value(COUNTER),
                "only 【美学底本】's extra turn pays: the clause gates on the mark");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static Battle newBattle() {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ELATION_ALLY, 80),
                        CharacterFactory.create(1204, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        // The archetype mark is normally applied by her ultimate; this case is about the reward, so it is set directly
        // (the same way HolderAttrScaleTest marks a holder).
        battle.characters().get(1).getBuffManager()
                .addBuff(new com.laosun.aluminium.buff.StateBuff("美学底本", 3, false));
        return battle;
    }
}
