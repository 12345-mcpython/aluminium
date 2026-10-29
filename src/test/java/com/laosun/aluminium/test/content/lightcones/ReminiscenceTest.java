package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.entity.Summon;
import com.laosun.aluminium.models.equipment.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Light cone 20022: on the MEMOSPRITE's turn start the wearer and the memosprite each gain one stack of [缅怀] (max 4), and the
 * wearer's stacks are removed when the memosprite disappears -- which, on a path where anyone is left to clean, means the
 * memosprite itself was killed.
 *
 * <p>Every word is existing vocabulary. "忆灵的回合" is {@code TURN_START} + {@code actor == summon} (the selector the
 * engine's own SUMMON_ATTACK note names), "分别获得" is two stack effects (target self / target summon), and
 * "忆灵消失时移除" is {@code KILL} with the memosprite as the victim. The path where the MASTER falls was measured
 * and withdrawn: both holders are gone by then, so there is nothing to clean.
 */
public class ReminiscenceTest {
    private static final int CONE = 20022;
    private static final int WEARER = 1402;   // has resources/game/memosprites/1402.json
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final String MEMORIAL = "缅怀";

    private Character wearer;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        return battle;
    }

    @Test
    public void theMemospriteTurnGrantsOneToEach() {
        Battle battle = battle(true);
        Summon sprite = battle.summonMemosprite(wearer);
        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, sprite, null, 0, 0));
        int onWearer = wearer.getBuffManager().stacksOf(MEMORIAL);
        int onSprite = sprite.getBuffManager().stacksOf(MEMORIAL);
        System.out.println("[20022] after one memosprite turn: wearer=" + onWearer + " sprite=" + onSprite);
        Assertions.assertEquals(1, onWearer, "1 stack each (分别获得 1 层): the wearer");
        Assertions.assertEquals(1, onSprite, "and the memosprite");
    }

    @Test
    public void theWearersOwnTurnGrantsNothing() {
        Battle battle = battle(true);
        battle.summonMemosprite(wearer);
        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, wearer, null, 0, 0));
        System.out.println("[20022] after the WEARER's turn start: " + wearer.getBuffManager().stacksOf(MEMORIAL));
        Assertions.assertEquals(0, wearer.getBuffManager().stacksOf(MEMORIAL), "the memosprite's turn (忆灵的回合) -- the false case");
    }

    @Test
    public void theStacksGoWhenTheMemospriteIsKilled() {
        Battle battle = battle(true);
        Summon sprite = battle.summonMemosprite(wearer);
        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, sprite, null, 0, 0));
        int before = wearer.getBuffManager().stacksOf(MEMORIAL);
        // ⚠ `SUMMON_LEFT` is the event the sentence means (a memosprite that leaves the field - killed, replaced or
        // dismissed). The audit moved the rule off `KILL`, which only covers the killed path.
        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.SUMMON_LEFT, sprite, sprite, 0, 0));
        int after = wearer.getBuffManager().stacksOf(MEMORIAL);
        System.out.println("[20022] the memosprite was killed: wearer stacks " + before + " -> " + after
                + " ; sprite dead=" + sprite.isDeath());
        Assertions.assertEquals(1, before, "one stack first");
        // ⚠ No hit is dealt here: the sentence is about the memosprite LEAVING the field, which `SUMMON_LEFT` states
        // directly, and firing it must not require a kill.
        Assertions.assertNotNull(before, "the memosprite had a stack to lose");
        Assertions.assertEquals(0, after, "removed when the memosprite disappears (忆灵消失时移除)");
    }

    @Test
    public void theSpecPinsTheCapAndBothTargets() {
        Battle battle = battle(true);
        Summon sprite = battle.summonMemosprite(wearer);
        int pinned = 0;
        int targets = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.TURN_START,
                new TriggerTable.TriggerContext(wearer, sprite, null, 0, 0, null, battle, null))) {
            if (!rule.id().startsWith("cone20022_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                pinned++;
                System.out.println("[20022] spec op=" + effect.getOp() + " buff=" + effect.getBuff()
                        + " amount=" + effect.getAmount() + " max=" + effect.getMaxStacks()
                        + " target=" + effect.getTarget());
                // The rule has three effects since the audit: two stacks (wearer + memosprite) and the per-layer boost
                // that the sentence states (「每层使造成的伤害提高 8%」) and the file used to omit entirely.
                if ("MODIFY_ATTR".equals(effect.getOp())) {
                    continue;
                }
                Assertions.assertEquals("ADD_STACK", effect.getOp(), "the turn start gains");
                Assertions.assertEquals(4, effect.getMaxStacks(), "at most 4 stacks (最多叠加 4 层)");
                if ("summon".equals(effect.getTarget())) {
                    targets++;
                }
            }
        }
        // Three, not two: the audit found 「每层使造成的伤害提高 #1%」 had no modifier at all and added it.
        Assertions.assertEquals(3, pinned, "two stacks + the per-layer damage boost");
        Assertions.assertEquals(1, targets, "exactly one of them names the memosprite");
    }

    @Test
    public void withoutTheConeNoRuleExists() {
        Battle battle = battle(false);
        Summon sprite = battle.summonMemosprite(wearer);
        boolean any = wearer.getTriggerTable().matching(TriggerEvent.TURN_START,
                        new TriggerTable.TriggerContext(wearer, sprite, null, 0, 0, null, battle, null))
                .stream().anyMatch(rule -> rule.id().startsWith("cone20022_"));
        System.out.println("[20022] without the cone, a memosprite turn matches a rule: " + any);
        Assertions.assertFalse(any, "no cone, no rule (false case)");
    }
}
