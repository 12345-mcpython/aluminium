package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skillpoint.StandardSkillPointPolicy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The battle's lifecycle and its two mutable entry points: the rosters and the observer lists.
 *
 * <p>These are the defects a caller could previously hit without any error: a second
 * {@code startBattle()} re-ran every opening hook, a caller's own party list stayed the battle's
 * roster, a policy installed by field assignment lost its event wiring, and an observer could never
 * be taken off again. Each case pins the observable consequence, not the field.
 */
public class BattleLifecycleTest {

    /**
     * Missing character data is a test-environment problem, not a behaviour under test.
     */
    private static Character hero() {
        return CharacterFactory.create(1001, 80);
    }

    /**
     * Starting twice is a mistake, not a no-op: the second call would double every opening effect.
     */
    @Test
    public void startBattleTwiceIsRefused() {
        Battle battle = new Battle(List.of(hero()), List.of(BattleFixture.monster()), new Random(0));
        battle.startBattle();

        Assertions.assertThrows(IllegalStateException.class, battle::startBattle,
                "a second start would re-run BATTLE_START, party resources and technique states");
    }

    /**
     * The constructor owns its rosters: mutating the caller's list afterwards must not change the battle.
     */
    @Test
    public void theConstructorOwnsItsCharacterRoster() {
        Character hero = hero();
        List<Character> party = new ArrayList<>(List.of(hero));
        Battle battle = new Battle(party, List.of(BattleFixture.monster()), new Random(0));

        party.clear();

        Assertions.assertEquals(1, battle.characters().size(),
                "the battle copied the party; the caller's later clear() is not its business");
        Assertions.assertSame(hero, battle.characters().getFirst());
    }

    /**
     * A unit admitted after construction joins its camp and the pending entry list, then the action bar.
     */
    @Test
    public void aCombatantJoinsItsCampAndTheActionBar() {
        Battle battle = new Battle(List.of(hero()), List.of(BattleFixture.monster()), new Random(0));
        battle.startBattle();
        Enemy late = EnemyFactory.create(8002040, 90, 1);

        battle.addCombatant(late);

        Assertions.assertEquals(2, battle.enemies().size(), "it is on the enemy camp");
        Assertions.assertEquals(List.of(late), battle.pendingCombatants(), "and waiting to enter");
        battle.processRequests();
        Assertions.assertTrue(battle.pendingCombatants().isEmpty(), "the queue was drained");
        Assertions.assertTrue(battle.getQueueSnapshot().stream().anyMatch(s -> s.getCanHit() == late),
                "it is now in the action bar");
    }

    /**
     * Replacing the policy through the setter keeps the battle's readouts and cap in sync.
     */
    @Test
    public void theSkillPointPolicySetterReplacesThePool() {
        Battle battle = new Battle(List.of(hero()), List.of(BattleFixture.monster()), new Random(0));
        StandardSkillPointPolicy policy = new StandardSkillPointPolicy(7, 4);

        battle.setSkillPointPolicy(policy);

        Assertions.assertSame(policy, battle.getSkillPointPolicy());
        Assertions.assertEquals(4, battle.getSkillPoints(), "the new starting value");
        Assertions.assertEquals(7, battle.getSkillPointMax(), "the new cap");
        battle.gainSkillPoint(100);
        Assertions.assertEquals(7, battle.getSkillPoints(), "gain is capped by the new policy");
    }

    /**
     * A removed observer is not called; a sibling that stayed is. This is what a long-lived front end
     * needs in order not to accumulate listeners across battles.
     */
    @Test
    public void anObserverCanBeRemoved() {
        Character hero = hero();
        Enemy foe = BattleFixture.monster();
        Battle battle = new Battle(List.of(hero), List.of(foe), new Random(0));
        battle.startBattle();

        AtomicInteger removedCalls = new AtomicInteger();
        AtomicInteger keptCalls = new AtomicInteger();
        Battle.DamageObserver removed = (damage, applied, hpLoss, shieldAbsorbed, died) ->
                removedCalls.incrementAndGet();
        battle.addDamageObserver(removed);
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) ->
                keptCalls.incrementAndGet());
        battle.removeDamageObserver(removed);

        battle.castImmediate(hero.getSkills().get(SkillType.COMMON), hero, List.of(foe));

        Assertions.assertEquals(0, removedCalls.get(), "the removed observer is silent");
        Assertions.assertEquals(1, keptCalls.get(), "the remaining observer still hears the settlement");
    }
}
