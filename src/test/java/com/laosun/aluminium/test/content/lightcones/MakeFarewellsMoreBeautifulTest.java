package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Light cone 23040: losing health -- by the wearer OR by its memosprite -- grants [冥花], which makes the wearer's damage
 * ignore 30% of the target's defence for two turns.
 *
 * <p>"获得[冥花]" is a STATE, not a stack counter, and a written modifier without `max_stacks` is
 * idempotent (measured): a second loss from either side leaves the value at 0.3. This judge pins exactly that, which is what the
 * sentence says -- and my first attempt asserted 0.6, which the engine refused with `expected: &lt;0.6&gt; but was: &lt;0.3&gt;`.
 *
 * <p>让告别，更美一些 (23040): 「当装备者的忆灵消失时，使装备者行动提前 <b>12%</b>。该效果最多触发 <b>1</b> 次，装备者每次施放终结技时
 * <b>重置</b>触发次数」.
 *
 * <p><b>The 「可复位旗标」 that was called missing.</b> It is two rules: a one-stack marker gates the advance, and an ultimate takes
 * that marker off again - "reset the count" is "remove the flag".
 */
public class MakeFarewellsMoreBeautifulTest {
    private static final int CONE = 23040;
    private static final int WEARER = 1402;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    // From the merged MakeFarewellsMoreBeautifulConeTest: a wearer WITH a memosprite, because the clause is 「装备者的忆灵消失时」.
    private static final int MEMOSPRITE_WEARER = 1415;
    private Character wearer;
    private Enemy enemy;
    private Battle battle;

    private void build(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = BattleFixture.monster();
        battle = new Battle(List.of(wearer, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
    }

    private double ignore() {
        return wearer.getAttribute(AttributeType.DEFENCE_IGNORE).get();
    }

    @Test
    public void eitherSourceGrantsItOnce() {
        build(true);
        double before = ignore();
        // The MEMOSPRITE loses health FIRST, and that order is the point: the flower is a state, so whichever source fires
        // first is the only one that can be observed. Firing the wearer's loss first made an `actor == summon` mutation inert
        // (discipline 225) -- measured, the value was already 0.3 by then.
        Summon sprite = battle.summonMemosprite(wearer);
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.HP_LOST, sprite, sprite, 0, 0));
        double afterSprite = ignore();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.HP_LOST, wearer, wearer, 0, 0));
        double afterWearer = ignore();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.HP_LOST, wearer, wearer, 0, 0));
        double afterAgain = ignore();
        System.out.println("[23040] defence ignore: before=" + before + " the memosprite's loss=" + afterSprite
                + " wearer loss=" + afterWearer + " second loss=" + afterAgain);
        Assertions.assertEquals(0.0, before, 1e-9, "nothing before a loss");
        Assertions.assertEquals(0.3, afterSprite, 1e-9, "the MEMOSPRITE’s loss alone grants Death Flower (冥花) (actor == summon)");
        Assertions.assertEquals(0.3, afterWearer, 1e-9, "the wearer’s loss leaves the same state alone");
        Assertions.assertEquals(0.3, afterAgain, 1e-9, "and it is a state: a second loss adds nothing");
    }

    @Test
    public void nothingElseGrantsIt() {
        build(true);
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, wearer, null, 0, 0));
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, wearer, enemy, 0, 0));
        System.out.println("[23040] after a turn start and a skill: ignore=" + ignore());
        Assertions.assertEquals(0.0, ignore(), 1e-9, "the trigger is an HP loss (false case)");
    }

    @Test
    public void withoutTheConeNothingIsGranted() {
        build(false);
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.HP_LOST, wearer, wearer, 0, 0));
        System.out.println("[23040] without the cone: ignore=" + ignore());
        Assertions.assertEquals(0.0, ignore(), 1e-9, "no cone, no flower (false case)");
    }

    @Test
    public void theWearerAloneGrantsIt() {
        // The two rules grant the SAME state, so a mutation of one is invisible while the other can fire -- measured:
        // moving the wearer's rule to ULT_CAST left every earlier reading at 0.3, because the memosprite's rule covered it.
        // With NO memosprite on the field the wearer's rule is the only one that can match.
        build(true);
        double before = ignore();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.HP_LOST, wearer, wearer, 0, 0));
        double after = ignore();
        System.out.println("[23040] with no memosprite at all: before=" + before + " after the wearer's loss=" + after);
        Assertions.assertEquals(0.0, before, 1e-9, "nothing before a loss");
        Assertions.assertEquals(0.3, after, 1e-9, "the wearer's own rule is enough (no memosprite in play)");
    }

    // ==================================================================
    // Merged from MakeFarewellsMoreBeautifulConeTest.

    private static final class Run {
        private final Character wearer;
        private final Enemy enemy;
        private final Battle battle;

        Run() {
            wearer = CharacterFactory.create(MEMOSPRITE_WEARER, LEVEL, true, Weapon.build(23040, LEVEL, false, 1));
            enemy = BattleFixture.monster();
            battle = new Battle(new ArrayList<>(List.of(wearer)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.seeded());
            battle.startBattle();
        }

        void summonLeaves() {
            // `actor is_summon` is the condition; a plain fire with the wearer's own memosprite is the shape the rule reads.
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                    TriggerEvent.SUMMON_LEFT, battle.summonMemosprite(wearer), wearer, 0, 0));
            battle.processRequests();
        }

        boolean armed() {
            return wearer.getBuffManager().stacksOf("送别的花") >= 1;
        }
    }

    /**
     * One departure arms the advance; a second one does not, until an ultimate resets it.
     */
    @Test
    public void oneDeparturePaysAndAnUltimateRearmsIt() {
        Run run = new Run();
        Assertions.assertFalse(run.armed(), "nothing spent yet");
        run.summonLeaves();
        Assertions.assertTrue(run.armed(), "the first departure spends the one allowed trigger");

        run.summonLeaves();
        Assertions.assertTrue(run.armed(), "a second departure does not spend another - 「最多触发 1 次」");

        run.wearer.setCurrentEnergy(run.wearer.getMaxEnergy());
        run.battle.castImmediate(run.wearer.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA),
                run.wearer, List.of(run.enemy));
        run.battle.processRequests();
        Assertions.assertFalse(run.armed(), "「每次施放终结技时重置触发次数」 - the flag is off again");
    }
}
