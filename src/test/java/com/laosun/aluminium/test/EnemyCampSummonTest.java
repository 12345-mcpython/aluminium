package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DefaultSkill;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.utils.AttributeBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * L-8: the enemy camp accepts any {@code CanHit}, so a {@link Summon} can fight alongside the monsters.
 *
 * <p><b>What this is really testing.</b> Widening the roster's type is only worth anything if the rest of
 * the engine stops assuming "enemy camp == monsters". Four things could each have quietly kept the old
 * assumption, and each is asserted here:
 * <ol>
 *   <li>the camp holds it ({@code enemies}) while the monster view ({@code enemyUnits()}) does not —
 *       the two questions "who is on that side" and "which of them are monsters" are now separate;</li>
 *   <li>our side can reach it: the engine's own target expansion ({@code targetableEnemies}) includes it,
 *       which is what an AOE skill iterates;</li>
 *   <li><b>the outcome waits for it</b>: killing every monster while a summon lives must not be a win —
 *       the failure mode this guards against is a summon that is simply ignored by the victory check;</li>
 *   <li>it takes a turn: it is on the action bar like anything else.</li>
 * </ol>
 *
 * <p>A {@code Summon} is used rather than a fake subclass on purpose — it is the real type P9-4 needs, and
 * it is the type that could not be placed at all before this change.
 */
public class EnemyCampSummonTest {
    private static final double EPS = 1e-6;

    /** The camp holds it; the monster view does not. */
    @Test
    public void theEnemyCampHoldsANonMonsterAndTheMonsterViewDoesNot() {
        Fixture f = fixture();

        Assertions.assertEquals(2, f.battle.enemies.size(), "the camp holds the monster and the summon");
        Assertions.assertSame(f.summon, f.battle.enemies.get(1));
        Assertions.assertEquals(1, f.battle.enemyUnits().size(),
                "enemyUnits() is the monsters only -- 'on that side' and 'is a monster' are now two questions");
        Assertions.assertSame(f.monster, f.battle.enemyUnits().getFirst());
    }

    /** Our side's target list includes it, so an AOE reaches it without the caller naming it. */
    @Test
    public void theEngineTargetListIncludesTheSummon() {
        Fixture f = fixture();

        Assertions.assertEquals(2, f.battle.targetableEnemies().size(),
                "an enemy-side summon is a legitimate target and must be in the engine's own target list");

        double monsterBefore = f.monster.getCurrentHp();
        double summonBefore = f.summon.getCurrentHp();
        f.battle.castImmediate(new DefaultSkill(1001, 3, 1), f.hero, List.of(f.monster));   // AOE

        Assertions.assertTrue(f.monster.getCurrentHp() < monsterBefore, "the named target took the hit");
        Assertions.assertTrue(f.summon.getCurrentHp() < summonBefore,
                "and so did the summon: the AOE branch iterates battle.targetableEnemies(), so this only "
                        + "holds if the widening reached the target list too");
    }

    /**
     * The victory check counts the whole camp, not just the monsters.
     *
     * <p>This is the assertion that would catch the quiet regression: if {@code checkResult} iterated
     * {@code enemyUnits()} (or was left filtering by class), the summon would be ignored and the battle
     * would be declared won while something was still standing.
     */
    @Test
    public void killingAllTheMonstersIsNotAWinWhileASummonStands() {
        Fixture f = fixture();
        f.battle.startBattle();

        f.monster.takeDamage(9_999_999);
        f.battle.processRequests();

        Assertions.assertTrue(f.monster.isDeath(), "precondition: the monster is down");
        Assertions.assertEquals(Battle.Status.RUNNING, f.battle.getStatus(),
                "the summon is still standing, so the enemy side is not wiped out and this must NOT be a win");

        f.summon.takeDamage(9_999_999);
        f.battle.processRequests();

        Assertions.assertEquals(Battle.Status.WIN, f.battle.getStatus(),
                "now the whole camp is down");
    }

    /** It really is a combatant: on the action bar, with its own signal. */
    @Test
    public void theSummonTakesItsTurnOnTheActionBar() {
        Fixture f = fixture();

        Assertions.assertEquals(3, f.battle.queue.size(), "hero + monster + summon are all combatants");

        List<Signal> signals = f.battle.queue.snapshot();
        Assertions.assertTrue(signals.stream().anyMatch(s -> s.getCanHit() == f.summon),
                "the summon has a signal of its own, so it will be handed turns like any other unit");
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    private record Fixture(Battle battle, Character hero, Enemy monster, Summon summon) {
    }

    /** A hero, one real monster, and one enemy-side summon. */
    private static Fixture fixture() {
        Character hero = Character.fromAttributes("hero", 10_000, 100, 100, 100);
        Enemy monster = EnemyFactory.create(1002011, 90, 1);        // 冰锋, toughness 60
        Summon summon = new Summon("minion", Camp.ENEMY,
                new AttributeBuilder()
                        .setBase(AttributeType.HEALTH, 2_000)
                        .setBase(AttributeType.DEFENCE, 100)
                        .setBase(AttributeType.ATTACK, 100)
                        .setBase(AttributeType.SPEED, 100)
                        .build());
        Battle battle = new Battle(List.of(hero), List.of(monster, summon), new Random(0));
        return new Fixture(battle, hero, monster, summon);
    }
}
