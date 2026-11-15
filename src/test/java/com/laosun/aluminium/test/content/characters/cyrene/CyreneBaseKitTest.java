package com.laosun.aluminium.test.content.characters.cyrene;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415 昔涟's base kit and the resource/stat layer under it: the two 【追忆】 writers and their cap, the unconditional
 * party boost and the aura that carries it to a teammate, the talent's own 20% as the attribute actually reports it, the
 * talent-plus-trace total past the speed threshold, and 【未来】 - handed to the other allies at battle start and spent
 * when one of them acts.
 *
 * <p>Nothing here settles a damage instance: every claim is read off an attribute, a resource counter or a buff, so a
 * change in a damage formula cannot make these red for the wrong reason.
 *
 * <p>Merged from the former {@code CyreneTest}, {@code CyreneAuraTest}, {@code CyreneTalentBoostReadingTest},
 * {@code CyreneSpeedThresholdTest}, {@code CyreneFutureTest} and {@code CyreneFutureSpendTest}; every test, assertion,
 * helper and class-level note is preserved.
 *
 * <p><b>L3:</b> the three cases that read one battle-start board (1415 + 1405) are folded into one scenario test
 * ({@code Assertions.assertAll}), one message per clause reading: the talent's boost as the attribute reports it, 【未来】
 * to the other ally only, and the point of 【追忆】 that ally's first action is worth. Every assertion, expected value,
 * tolerance and message is unchanged. The aura's difference against a teammate without her and the two-writer dial keep
 * their own tests - different fixtures, and an {@code assertAll} is not where either belongs.
 */
public class CyreneBaseKitTest {

    private static final int CYRENE = 1415;
    private static final int LEVEL = 80;
    // Two different teammates in the two absorbed files: the aura's TEAMMATE (1001) and the party boost's ALLY (1002).
    private static final int TEAMMATE = 1001;
    private static final int ALLY = 1002;
    private static final int ANAXA = 1405;
    private static final int MONSTER = 1002011;
    private static final String FUTURE = "char:1415/future";
    private static final String MEMORY = "char:1415/remembrance";

    // ---------------------------------------------------------------------------------------------------------------
    // Former CyreneTest: 1415 Cyrene, from her own file -- the [追忆] resource and the party boost that needs no condition.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Note: Both writers feed [追忆], stopped at the document's twenty-four.
     */
    @Test
    public void bothWritersFeedRecollectionUpToItsStatedOverflow() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(cyrene), List.of(enemy), fixed());
        battle.startBattle();

        Assertions.assertEquals(0, recollectionOf(cyrene), "the document states no initial value, so it starts at 0");
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.BASIC_ATTACK, cyrene, enemy, 0, 0));
        Assertions.assertEquals(1, recollectionOf(cyrene), "「获得1点【char:1415/remembrance】」 (gains 1 point of Recollection (【char:1415/remembrance】))");
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, cyrene, enemy, 0, 0));
        Assertions.assertEquals(4, recollectionOf(cyrene), "「获得3点【char:1415/remembrance】」 (gains 3 points of Recollection) -- the other writer");

        for (int i = 0; i < 8; i++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, cyrene, enemy, 0, 0));
        }
        Assertions.assertEquals(27, recollectionOf(cyrene),
                "「【char:1415/remembrance】达到24点时可激活终结技」 (at 24 points of Recollection the Ultimate can be activated) -- thirty-two casts must still read twenty-four");
    }

    /**
     * Note: The party boost, which the document conditions on nothing at all: an ally gains it, an enemy does not.
     */
    @Test
    public void thePartyBoostNeedsNoCondition() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(cyrene, ally), List.of(enemy), fixed());
        double allyBefore = ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        double enemyBefore = enemy.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();

        battle.startBattle();

        Assertions.assertEquals(0.2, ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - allyBefore, 1e-9,
                "「昔涟在场时，我方全体目标造成的伤害提高20.00%」 (while Cyrene is on the field, all allies deal 20.00% more DMG)");
        Assertions.assertEquals(0.0, enemy.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - enemyBefore, 1e-9,
                "the selector is `all_allies`: the enemy side must be untouched");
    }

    /**
     * The declared resource's value, read through the combatant's own manager.
     */
    private static int recollectionOf(Character unit) {
        return unit.getResources().get("char:1415/remembrance").getValue();
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Former CyreneAuraTest: the aura, judged on a TEAMMATE and as a difference against that teammate without her.
    // ---------------------------------------------------------------------------------------------------------------
    /*
     * 1415 Cyrene (昔涟)'s aura: "昔涟在场时，我方全体目标造成的伤害提高20.00%" (while Cyrene is on the field, all of our targets deal 20.00% more damage) -- carried by the rule the file ALREADY had,
     * `talent_party_damage` (round 1 added a byte-identical duplicate, which round 3 removed after the mutation failed to move
     * anything: the original kept supplying 0.2 whatever the duplicate said).
     *
     * <p>Note: Mutating THAT rule is what must turn this red -- a mutation of a duplicate cannot, which is exactly how the
     * duplicate was found.
     *
     * <p>Judged on a TEAMMATE (the point of "我方全体") and as a DIFFERENCE against the same teammate without her -- so the assertion is
     * about the scope reaching someone else, not about an absolute number a baseline could satisfy anyway.
     */

    @Test
    public void theAuraReachesATeammate() {
        double withHer = boost(true);
        double withoutHer = boost(false);
        Assertions.assertEquals(0.20, withHer - withoutHer, 1e-9,
                "the aura adds 20% to a teammate and nothing when she is absent: " + withoutHer + " -> " + withHer);
    }

    /**
     * A teammate's ALL_DAMAGE_TYPE_BOOST with Cyrene on the team or not.
     */
    private static double boost(boolean withCyrene) {
        Character teammate = CharacterFactory.create(TEAMMATE, LEVEL);
        Enemy enemy = BattleFixture.monster();
        List<Character> team = withCyrene
                ? List.of(teammate, CharacterFactory.create(CYRENE, LEVEL))
                : List.of(teammate);
        Battle battle = new Battle(team, List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        return teammate.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Former CyreneSpeedThresholdTest: her talent and her trace both write +20%, and both must be counted.
    // ---------------------------------------------------------------------------------------------------------------
    /*
     * Her talent and her trace both write +20% to the party; both must be counted (1415,).
     *
     * <p>FILE-DRIVEN, and the TOTAL is the claim: 0.2 below the threshold (talent alone), 0.4 past it. The measured switch is
     * `max_stacks > 1` -- `StatModifierBuff.isStackable()` is exactly that, and `stackGroupKey()` is the attribute, the
     * modifier type and the source role -- so both writers state 2.
     */

    /**
     * Talent alone below 180; talent plus trace past it.
     */
    @Test
    public void theTwoWritersAdd() {
        Assertions.assertEquals(0.2, total(0), 1e-6, "below the threshold, her talent alone");
        Assertions.assertEquals(0.4, total(100), 1e-6, "past 180, both writers must be counted");
    }

    // ==================================================================

    private static double total(double extraSpeed) {
        Character owner = CharacterFactory.create(CYRENE, 80, false, null, null, 0);
        if (extraSpeed > 0) {
            EffectSpec raise = new EffectSpec();
            TriggerSpecs.set(raise, "op", "MODIFY_ATTR");
            TriggerSpecs.set(raise, "attribute", "SPEED");
            TriggerSpecs.set(raise, "amount", extraSpeed);
            TriggerSpecs.set(raise, "permanent", true);
            TriggerSpecs.set(raise, "target", "self");
            owner.setTriggerTable(owner.getTriggerTable()
                    .plus(new TriggerTable(CYRENE, List.of(TriggerSpecs.rule("BATTLE_START", List.of(), raise)))));
        }
        Battle battle = new Battle(List.of(owner),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, owner, owner, 0, 0));
        battle.processRequests();
        return owner.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Former CyreneTalentBoostReadingTest, CyreneFutureTest and CyreneFutureSpendTest: one battle-start board, read for
    // the talent's boost and the 【未来】 it hands the other ally, then for what that ally's action pays her.
    // ---------------------------------------------------------------------------------------------------------------
    /*
     * What Cyrene (昔涟)'s talent actually leaves on a teammate: "at battle start ... raise the damage dealt by all of our targets".
     * <p>
     * Her file states it as `ALL_DAMAGE_TYPE_BOOST, percent: 0.2, permanent, max_stacks: 2`, i.e. two layers. Reading the attribute directly reports 0.2, while a reverse-solved baseline comes
     * out at 0.4184 -- this settles which number `get()` answers with, so the two readings can be reconciled rather than guessed at.
     *
     * <p>At battle start... other characters gain [未来]. Two-sided: the OTHER ally gets it, Cyrene (昔涟) herself does not (the text says 其他, "other").
     *
     * <p>"持有[未来]的我方目标行动时消耗[未来]使昔涟获得 1 点[追忆]".
     */

    /**
     * One battle start on the 昔涟 + 阿纳克萨 (1415 + 1405) board: the talent's boost as the attribute reports it, 【未来】
     * handed to the other ally and not to her, then the ally's first action, which spends that 【未来】 and credits her one
     * point of 【追忆】.
     */
    @Test
    public void theBattleStartGivesTheBoostAndTheFutureThenTheAllySpendsIt() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character him = CharacterFactory.create(ANAXA, LEVEL);
        Battle battle = new Battle(List.of(cyrene, him),
                List.of(EnemyFactory.create(MONSTER, 100, 1)), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        double boost = battle.characters().get(1).getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        boolean allyHasFuture = battle.characters().get(1).getBuffManager().hasState(FUTURE);
        boolean herHasFuture = battle.characters().get(0).getBuffManager().hasState(FUTURE);
        System.out.println("[cyrene_boost] a teammate's ALL_DAMAGE_TYPE_BOOST reads " + boost
                + " (the file states percent 0.2 with max_stacks 2)");
        System.out.println("[future] at battle start: the ally has it = " + allyHasFuture
                + " ; Cyrene (昔涟) herself = " + herHasFuture);
        Assertions.assertAll("battle start on the 1415 + 1405 board",
                () -> Assertions.assertEquals(0.2, boost, 1e-9,
                        "exactly the talent's stated 20.00% -- measured, NOT two layers, so `max_stacks: 2` merely permits more"),
                () -> Assertions.assertTrue(allyHasFuture, "another ally gains 【char:1415/future】 (the Future)"),
                () -> Assertions.assertFalse(herHasFuture,
                        "and Cyrene (昔涟) does not, because the text says other allies (其他)"));

        Character ally = battle.characters().get(1);
        Character her = battle.characters().get(0);
        double before = her.getResources().has(MEMORY) ? her.getResources().value(MEMORY) : 0;
        boolean heldIt = ally.getBuffManager().hasState(FUTURE);
        battle.setCurrentMove(new Signal(ally));
        battle.beforeMove();
        battle.processRequests();
        boolean still = battle.characters().get(1).getBuffManager().hasState(FUTURE);
        double after = battle.characters().get(0).getResources().has(MEMORY)
                ? battle.characters().get(0).getResources().value(MEMORY) : 0;
        System.out.println("[future_spend] after the ally acts: 【char:1415/future】 (the Future) still on it = " + still
                + " ; Cyrene (昔涟)'s Recollection (【char:1415/remembrance】) " + before + " -> " + after);
        Assertions.assertAll("the ally's first action spends 【char:1415/future】",
                () -> Assertions.assertTrue(heldIt, "precondition: the ally holds it"),
                () -> Assertions.assertFalse(still, "acting consumes it"),
                () -> Assertions.assertEquals(before + 1, after, 1e-9, "and she is credited one point"));
    }
}
