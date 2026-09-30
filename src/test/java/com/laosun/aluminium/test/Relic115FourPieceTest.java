package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.RelicFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Relic set 115 (The Ashblazing Grand Duke), 4-piece: each FOLLOW-UP damage instance raises ATK by 6% for 3 turns, up
 * to 8 times, and the effect is removed when the wearer next uses a Follow-Up ATK.
 *
 * <p>Its registration reason ("no follow-up dimension on a Damage instance") had gone stale: the FOLLOW_UP event
 * already fires per additional-damage instance, and `once_per_attack` is exactly the "first instance of the next
 * follow-up attack" that the removal clause needs.
 */
public class Relic115FourPieceTest {
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int RELIC_LEVEL = 15;
    private static final int MONSTER = 1002011;
    private static final double PER_STACK = 0.06;

    private Character wearer;
    private Enemy enemy;

    private Battle battle(boolean withSet) {
        wearer = withSet
                ? CharacterFactory.create(WEARER, LEVEL, true, null, RelicFactory.suit(115, 4, RELIC_LEVEL))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private void followUps(Battle battle, int count) {
        for (int i = 0; i < count; i++) {
            battle.fireTriggers(TriggerEvent.FOLLOW_UP, wearer, enemy, 0, 0);
        }
    }

    @Test
    public void everyFollowUpInstanceStacksAndTheNextFollowUpStartsWithOne() {
        Battle battle = battle(true);
        double base = wearer.getAttribute(AttributeType.ATTACK).baseValue();
        double before = wearer.getAttribute(AttributeType.ATTACK).get();
        followUps(battle, 3);
        double threeStacks = wearer.getAttribute(AttributeType.ATTACK).get() - before;
        System.out.println("[115/4] three follow-up instances: delta=" + threeStacks
                + " expected=" + (3 * PER_STACK * base));
        Assertions.assertEquals(3 * PER_STACK * base, threeStacks, 1e-6,
                "each instance of the follow-up adds a stack (6% of BASE, three times)");

        // A new attack begins: `once_per_attack` lets the FIRST instance of it clear the old stacks.
        battle.fireAfterAttack(wearer, enemy, List.of(enemy), 1000);
        followUps(battle, 1);
        double afterNext = wearer.getAttribute(AttributeType.ATTACK).get() - before;
        System.out.println("[115/4] after the next follow-up: delta=" + afterNext
                + " expected=" + (PER_STACK * base));
        Assertions.assertEquals(PER_STACK * base, afterNext, 1e-6,
                "the next follow-up removes the old effect and then stacks once");
    }

    @Test
    public void theStacksAreCappedAtEight() {
        Battle battle = battle(true);
        double base = wearer.getAttribute(AttributeType.ATTACK).baseValue();
        double before = wearer.getAttribute(AttributeType.ATTACK).get();
        followUps(battle, 10);
        double delta = wearer.getAttribute(AttributeType.ATTACK).get() - before;
        System.out.println("[115/4] ten instances: delta=" + delta + " cap=" + (8 * PER_STACK * base));
        Assertions.assertEquals(8 * PER_STACK * base, delta, 1e-6, "the text says at most 8 stacks");
    }

    @Test
    public void withoutTheSetNothingMoves() {
        Battle battle = battle(false);
        double before = wearer.getAttribute(AttributeType.ATTACK).get();
        followUps(battle, 3);
        System.out.println("[115/4] without the set: " + before + " -> "
                + wearer.getAttribute(AttributeType.ATTACK).get());
        Assertions.assertEquals(before, wearer.getAttribute(AttributeType.ATTACK).get(), 1e-9,
                "no set, no stacks (false case)");
    }

    @Test
    public void theSpecPinsTheNumbersAndTheOrder() {
        Battle battle = battle(true);
        int removals = 0;
        int additions = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.FOLLOW_UP,
                new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("REMOVE_STACK".equals(effect.getOp())) {
                    removals++;
                    System.out.println("[115/4] spec removal amount=" + effect.getAmount()
                            + " oncePerAttack=" + rule.oncePerAttack());
                    Assertions.assertTrue(rule.oncePerAttack(), "the removal is the FIRST instance of the next attack");
                } else if ("ADD_STACK".equals(effect.getOp())) {
                    additions++;
                    System.out.println("[115/4] spec add maxStacks=" + effect.getMaxStacks()
                            + " turns=" + effect.getTurns());
                    Assertions.assertEquals(1, effect.getAmount(), 1e-9, "one stack per instance");
                    Assertions.assertEquals(3, effect.getTurns(), "for 3 turns");
                } else if ("MODIFY_ATTR".equals(effect.getAttribute())) {
                    Assertions.assertEquals(PER_STACK, effect.getPercent(), 1e-9, "6% per stack");
                }
            }
        }
        Assertions.assertEquals(1, removals, "exactly one removal rule from this set");
        Assertions.assertEquals(1, additions, "and one stacking rule");
    }
}
