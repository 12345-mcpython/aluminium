package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 23040: losing health -- by the wearer OR by its memosprite -- grants [冥花], which makes the wearer's damage
 * ignore 30% of the target's defence for two turns.
 *
 * <p>"获得[冥花]" is a STATE, not a stack counter, and a written modifier without `max_stacks` is
 * idempotent (measured): a second loss from either side leaves the value at 0.3. This judge pins exactly that, which is what the
 * sentence says -- and my first attempt asserted 0.6, which the engine refused with `expected: &lt;0.6&gt; but was: &lt;0.3&gt;`.
 */
public class MakeFarewellsMoreBeautifulTest {
    private static final int CONE = 23040;
    private static final int WEARER = 1402;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer;
    private Enemy enemy;
    private Battle battle;

    private void build(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(List.of(wearer, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy), new Random(0));
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
        battle.fireTriggers(TriggerEvent.HP_LOST, sprite, sprite, 0, 0);
        double afterSprite = ignore();
        battle.fireTriggers(TriggerEvent.HP_LOST, wearer, wearer, 0, 0);
        double afterWearer = ignore();
        battle.fireTriggers(TriggerEvent.HP_LOST, wearer, wearer, 0, 0);
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
        battle.fireTriggers(TriggerEvent.TURN_START, wearer, null, 0, 0);
        battle.fireTriggers(TriggerEvent.SKILL_CAST, wearer, enemy, 0, 0);
        System.out.println("[23040] after a turn start and a skill: ignore=" + ignore());
        Assertions.assertEquals(0.0, ignore(), 1e-9, "the trigger is an HP loss (false case)");
    }

    @Test
    public void withoutTheConeNothingIsGranted() {
        build(false);
        battle.fireTriggers(TriggerEvent.HP_LOST, wearer, wearer, 0, 0);
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
        battle.fireTriggers(TriggerEvent.HP_LOST, wearer, wearer, 0, 0);
        double after = ignore();
        System.out.println("[23040] with no memosprite at all: before=" + before + " after the wearer's loss=" + after);
        Assertions.assertEquals(0.0, before, 1e-9, "nothing before a loss");
        Assertions.assertEquals(0.3, after, 1e-9, "the wearer's own rule is enough (no memosprite in play)");
    }
}
