package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The {@code allies_of_path_except:<PATH>:<WHO>,<WHO>} selector: the allies of a Path, minus named units.
 *
 * <p>Reader: 1503 Pearl (真珠)'s Eidolon 2 -- 「使除真珠和【美学底本】外的其他我方「欢愉」命途角色也触发行动提前效果」. The
 * vocabulary already had {@code allies_of_path:} (a selector) and {@code allies_with_path:} (a counting condition),
 * but nothing that could say "that Path, other than these two" - and the exclusion is about WHICH UNITS an effect
 * reaches, which is what a target selector is for.
 *
 * <p>Read as the selector's contract rather than through Pearl's clause: the rule is built in-test and grants a marked
 * stack to whatever it selects, so the case is about the selector. Four units are present so the two halves can fail
 * separately - a Path mate is reached, and the excluded units and the off-Path unit are not. A selector that ignored
 * its exclusions would reach the owner; one that ignored the Path would reach the off-Path ally; one that returned
 * everybody would fail on both counts, and each mistake has its own assertion.
 */
public class AlliesOfPathExceptSelectorTest {
    private static final int LEVEL = 80;
    private static final int PEARL = 1503;      // Elation - the rule's owner and one of the two exclusions
    private static final int ELATION_MATE = 1502;   // Elation - must be reached
    private static final int HUNTER = 1204;     // NOT Elation - must not be reached
    private static final int MONSTER = 1002011;
    private static final String MARK = "测试标记";

    /** The Path mate is reached; the owner (excluded as `self`), the aimed ally (as `target`) and the off-Path ally are not. */
    @Test
    public void itReachesThePathMateAndNeitherExclusionNorTheOffPathAlly() {
        Fixture f = new Fixture();
        f.owner.setTriggerTable(new TriggerTable(PEARL, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(),
                        marker("allies_of_path_except:欢愉:self,target")))));
        f.battle.fireTriggers(TriggerEvent.BATTLE_START, f.owner, f.aimed, 0, 0);
        f.battle.processRequests();

        System.out.println("[path_except] mate " + f.stacks(f.mate) + " ; owner " + f.stacks(f.owner)
                + " ; aimed " + f.stacks(f.aimed) + " ; off-path " + f.stacks(f.hunter));
        Assertions.assertEquals(1, f.stacks(f.mate), "the Elation mate is reached");
        Assertions.assertEquals(0, f.stacks(f.owner), "the owner is excluded by `self`");
        Assertions.assertEquals(0, f.stacks(f.aimed), "the aimed ally is excluded by `target`");
        Assertions.assertEquals(0, f.stacks(f.hunter), "and an ally OFF the Path is not reached");
    }

    /**
     * The exclusion list is what makes it different from the plain path selector, and an empty one is refused.
     *
     * <p>A selector that excludes nobody is a different clause, so the vocabulary asks for the plain spelling instead of
     * quietly behaving like it - the same treatment a misspelled path gets.
     */
    @Test
    public void anExclusionListThatNamesNobodyIsRefused() {
        Fixture f = new Fixture();
        f.owner.setTriggerTable(new TriggerTable(PEARL, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(),
                        marker("allies_of_path_except:欢愉:actor")))));
        // `actor` on BATTLE_START is nobody (the event has no actor), so the list names no existing unit.
        IllegalStateException refused = Assertions.assertThrows(IllegalStateException.class,
                () -> f.battle.fireTriggers(TriggerEvent.BATTLE_START, null, null, 0, 0));
        System.out.println("[path_except] refused: " + refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains("excludes nothing"),
                "the message says what is wrong: " + refused.getMessage());
    }

    /** An unknown Path is a refused spelling, not an empty answer. */
    @Test
    public void anUnknownPathIsRefused() {
        Fixture f = new Fixture();
        f.owner.setTriggerTable(new TriggerTable(PEARL, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(),
                        marker("allies_of_path_except:不存在的命途:self")))));
        IllegalStateException refused = Assertions.assertThrows(IllegalStateException.class,
                () -> f.battle.fireTriggers(TriggerEvent.BATTLE_START, f.owner, f.aimed, 0, 0));
        System.out.println("[path_except] refused: " + refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains("not a path"),
                "the message names the problem: " + refused.getMessage());
    }

    /** A selector with no exclusion list at all is a spelling mistake too. */
    @Test
    public void aSelectorWithNoExclusionListIsRefused() {
        Fixture f = new Fixture();
        f.owner.setTriggerTable(new TriggerTable(PEARL, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(),
                        marker("allies_of_path_except:欢愉")))));
        IllegalStateException refused = Assertions.assertThrows(IllegalStateException.class,
                () -> f.battle.fireTriggers(TriggerEvent.BATTLE_START, f.owner, f.aimed, 0, 0));
        System.out.println("[path_except] refused: " + refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains("no exclusion list"),
                "the message asks for one: " + refused.getMessage());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** A rule that puts one marked stack on whatever the selector names. */
    private static EffectSpec marker(String selector) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "ADD_STACK");
        TriggerSpecs.set(effect, "buff", MARK);
        TriggerSpecs.set(effect, "amount", 1.0);
        TriggerSpecs.set(effect, "permanent", Boolean.TRUE);
        TriggerSpecs.set(effect, "target", selector);
        return effect;
    }

    /** Pearl, an Elation mate, a Hunt ally (the AIMED one, so the archetype is off-Path), and an enemy. */
    private static final class Fixture {
        final Battle battle;
        final Character owner;
        final Character mate;
        final Character hunter;
        final Character aimed;

        Fixture() {
            battle = new Battle(
                    List.of(CharacterFactory.create(PEARL, LEVEL), CharacterFactory.create(ELATION_MATE, LEVEL),
                            CharacterFactory.create(HUNTER, LEVEL)),
                    List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
            battle.startBattle();
            battle.processRequests();
            owner = battle.characters.get(0);
            mate = battle.characters.get(1);
            hunter = battle.characters.get(2);
            aimed = hunter;
        }

        int stacks(Character unit) {
            return unit.getBuffManager().stacksOf(MARK);
        }
    }
}
