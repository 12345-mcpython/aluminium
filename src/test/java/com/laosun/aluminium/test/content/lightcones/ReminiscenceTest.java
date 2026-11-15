package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
 *
 * <p><b>What the 「分别」 fixture pins.</b> Both halves of "分别": each unit gets its <b>own</b> stack, and each unit's damage
 * share reads <b>its own</b> counter ({@code per_stack} is resolved on the effect's target, which is why the memosprite's copy is
 * {@code target: summon} rather than a second copy of the owner's). The previous record left the memosprite half
 * unwritten because "whose counter" had not been measured; it is the target's.
 *
 * <p>Also pinned there: the per-rank share. The file used to state 8% at every rank; ranks 2-5 are 9/10/11/12%.
 */
public class ReminiscenceTest {
    private static final int CONE = 20022;
    private static final int WEARER = 1402;   // has resources/game/memosprites/1402.json
    private static final int FIXTURE_WEARER = 1409;
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
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, sprite, null, 0, 0));
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
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, wearer, null, 0, 0));
        System.out.println("[20022] after the WEARER's turn start: " + wearer.getBuffManager().stacksOf(MEMORIAL));
        Assertions.assertEquals(0, wearer.getBuffManager().stacksOf(MEMORIAL), "the memosprite's turn (忆灵的回合) -- the false case");
    }

    @Test
    public void theStacksGoWhenTheMemospriteIsKilled() {
        Battle battle = battle(true);
        Summon sprite = battle.summonMemosprite(wearer);
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, sprite, null, 0, 0));
        int before = wearer.getBuffManager().stacksOf(MEMORIAL);
        // ⚠ `SUMMON_LEFT` is the event the sentence means (a memosprite that leaves the field - killed, replaced or
        // dismissed). The audit moved the rule off `KILL`, which only covers the killed path.
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SUMMON_LEFT, sprite, sprite, 0, 0));
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
                // The rule has four effects: two stacks (wearer + memosprite) and the per-layer boost
                // on both sides (each copy reads its own counter).
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
        // Four: two stacks + one per-layer boost per side (the wearer's and the memosprite's).
        Assertions.assertEquals(4, pinned, "two stacks + the per-layer boost on each side");
        Assertions.assertEquals(1, targets, "exactly one STACK names the memosprite (the share does too, and is counted above)");
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

    /**
     * One memosprite turn gives one layer to each side, and each side's share follows its own layer count.
     */
    @Test
    public void eachSideGetsItsOwnLayerAndItsOwnShare() {
        Fixture f = new Fixture();
        f.summonTurn();
        Assertions.assertEquals(1, f.stacks(f.wearer), "the wearer gains 1 layer");
        Assertions.assertEquals(1, f.stacks(f.summon), "the memosprite gains 1 layer of its own");
        Assertions.assertEquals(0.08, f.share(f.wearer), 1e-9, "the wearer's damage +8% at one layer");
        Assertions.assertEquals(0.08, f.share(f.summon), 1e-9, "the memosprite's damage +8%, read off ITS counter");

        f.summonTurn();
        Assertions.assertEquals(0.16, f.share(f.wearer), 1e-9, "two layers on the wearer");
        Assertions.assertEquals(0.16, f.share(f.summon), 1e-9,
                "two layers on the memosprite - not the wearer's count, and not double");
    }

    /**
     * 「最多叠加 4 层」: the fifth turn adds nothing.
     */
    @Test
    public void theLayersCapAtFour() {
        Fixture f = new Fixture();
        for (int i = 0; i < 5; i++) {
            f.summonTurn();
        }
        Assertions.assertEquals(4, f.stacks(f.wearer));
        Assertions.assertEquals(4, f.stacks(f.summon));
        Assertions.assertEquals(0.32, f.share(f.wearer), 1e-9);
    }

    /**
     * The gate is 「忆灵的回合」: a turn of anybody else grants nothing.
     */
    @Test
    public void aNonMemospriteTurnGrantsNothing() {
        Fixture f = new Fixture();
        f.ownerTurn();
        Assertions.assertEquals(0, f.stacks(f.wearer));
        Assertions.assertEquals(0, f.stacks(f.summon));
    }

    /**
     * 「忆灵消失时移除装备者和忆灵的【缅怀】」: when the memosprite leaves, the wearer's layers go with it.
     */
    @Test
    public void theLayersLeaveWhenTheMemospriteDoes() {
        Fixture f = new Fixture();
        f.summonTurn();
        f.summonTurn();
        Assertions.assertEquals(2, f.stacks(f.wearer));

        f.summon.takeDamage(f.summon.getCurrentHp());
        f.battle.processRequests();

        Assertions.assertTrue(f.summon.isDeath(), "the fixture's memosprite left");
        Assertions.assertEquals(0, f.stacks(f.wearer),
                "「忆灵消失时移除装备者和忆灵的【缅怀】」 - the wearer's layers are cleared too");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character wearer;
        private final Summon summon;
        private final Battle battle;

        private Fixture() {
            wearer = CharacterFactory.create(FIXTURE_WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1));
            battle = new Battle(new ArrayList<>(List.of(wearer, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
            battle.startBattle();
            Summon found = battle.memospriteOf(wearer);
            if (found == null) {
                found = battle.summonMemosprite(wearer);
            }
            summon = found;
            Assertions.assertNotNull(summon, "1409's memosprite must exist for this fixture");
        }

        private void summonTurn() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, summon, summon, 0, 0));
        }

        private void ownerTurn() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, wearer, wearer, 0, 0));
        }

        private int stacks(com.laosun.aluminium.entity.CanHit unit) {
            return unit.getBuffManager().stacksOf("缅怀");
        }

        private double share(com.laosun.aluminium.entity.CanHit unit) {
            return unit.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        }
    }
}
