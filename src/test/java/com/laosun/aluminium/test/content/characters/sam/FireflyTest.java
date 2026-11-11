package com.laosun.aluminium.test.content.characters.sam;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1310 Firefly (流萤): the two Action Advances, the talent's energy floor, and the full-energy dispel guard.
 *
 * <p>These were three small files in the same package; one class keeps the character together without changing any
 * assertion.
 */
public class FireflyTest {
    private static final int FIREFLY = 1310;
    private static final int LEVEL = 80;

    /**
     * Note: 25% of the REMAINING wait is skipped: what is left is 0.5 of what it was, and nothing moves without firing.
     */
    @Test
    public void theSkillLeavesThreeQuartersOfTheWait() {
        Character firefly = CharacterFactory.create(FIREFLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(firefly), List.of(enemy), fixed());
        battle.startBattle();
        double before = remaining(battle, firefly);
        Assertions.assertTrue(before > 0, "the fixture must have a wait to skip: " + before);

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, firefly, enemy, 0, 0));
        double after = remaining(battle, firefly);

        Assertions.assertEquals(0.75, after / before, 1e-6,
                "「使自身下一次行动提前25%」 (advances her own next action by 25%): " + before + " -> " + after);
    }

    /**
     * Note: 100% means the wait is gone.
     */
    @Test
    public void theUltimateRemovesTheWaitEntirely() {
        Character firefly = CharacterFactory.create(FIREFLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(firefly), List.of(enemy), fixed());
        battle.startBattle();
        double before = remaining(battle, firefly);
        Assertions.assertTrue(before > 0, "the fixture must have a wait to skip: " + before);

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, firefly, enemy, 0, 0));

        Assertions.assertEquals(0.0, remaining(battle, firefly), 1e-6,
                "「自身行动提前100%」 (advances her own action by 100%): " + before + " -> 0");
    }

    /**
     * F-9: Firefly's talent raises energy TO 50% at battle start -- a floor, not a gain.
     */
    @Test
    public void theFloorRaisesLowEnergyAndLeavesHighEnergyAlone() {
        Character firefly = CharacterFactory.create(FIREFLY, LEVEL);
        Battle battle = new Battle(List.of(firefly), List.of(EnemyFactory.create(1002011, 100, 1)), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        firefly = battle.characters().getFirst();
        double max = firefly.getMaxEnergy();
        Assertions.assertTrue(max > 0, "precondition: she has an energy bar");

        double low = energyAfterTrigger(battle, firefly, 0.1 * max);
        double high = energyAfterTrigger(battle, firefly, 0.8 * max);
        System.out.println("[firefly_floor] max = " + max + " ; from 10% -> " + low + " ; from 80% -> " + high);

        Assertions.assertEquals(0.5 * max, low, 1e-6, "from below the floor, energy is raised TO 50%");
        Assertions.assertEquals(0.8 * max, high, 1e-6, "from above the floor, energy is left exactly as it was");
    }

    /**
     * Firefly (流萤) (1310) TALENT: "when energy is restored to the maximum, remove all of its own negative effects".
     *
     * <p>The first shipped reader of the {@code self_energy_percent} condition variable. matching() evaluates conditions,
     * so both directions are pinned: at full energy the rule matches, below full it must not. The engine mutation (flip
     * the ratio) is caught by the SECOND assertion -- with the ratio inverted a zero-energy unit reads Infinity.
     */
    @Test
    public void theRuleStatesItsGuardAndItsEffect() {
        var rules = new Battle[1];
        Character c = wearer(rules);
        // rulesFor does NOT evaluate conditions -- that is what makes it the shape test. matching() below is the
        // one that evaluates them, and it is why this test would have failed if it had used matching() at zero energy.
        var mine = c.getTriggerTable().rulesFor(TriggerEvent.ENERGY_GAINED).stream()
                .filter(r -> "talent_dispel_when_energy_full".equals(r.id())).toList();
        Assertions.assertEquals(1, mine.size(), "the rule is installed");
        Assertions.assertEquals(List.of("actor == self", "self_energy_percent >= 1.0"),
                mine.getFirst().conditions().stream().map(x -> x.source()).toList(), "its guard");
        Assertions.assertEquals("DISPEL", mine.getFirst().effects().getFirst().getOp(), "its effect");
        System.out.println("[1310] shape ok");
    }

    @Test
    public void onlyAFullEnergyBarMatches() {
        var rules = new Battle[1];
        Character c = wearer(rules);
        Battle b = rules[0];
        Assertions.assertTrue(c.getCurrentEnergy() < c.getMaxEnergy(),
                "a fresh unit starts below its maximum (got " + c.getCurrentEnergy() + "/" + c.getMaxEnergy() + ")");
        Assertions.assertEquals(0, matched(c, b),
                "below full energy the talent must NOT fire");
        c.gainEnergy(c.getMaxEnergy() * 2.0);
        Assertions.assertEquals(c.getMaxEnergy(), c.getCurrentEnergy(), 1e-9, "the grant clamps to the cap");
        Assertions.assertEquals(1, matched(c, b),
                "at full energy the talent must fire -- this is the assertion the engine mutation trips");
        System.out.println("[1310] energy guard ok: " + c.getCurrentEnergy() + "/" + c.getMaxEnergy());
    }

    /**
     * The owner's remaining wait, read off the queue's public snapshot.
     */
    private static double remaining(Battle battle, Character unit) {
        for (Signal signal : battle.queue().snapshot()) {
            if (signal.getCanHit() == unit) {
                return battle.queue().getTimeRemaining(signal);
            }
        }
        throw new IllegalStateException("the unit is not in the action queue");
    }

    /**
     * Sets energy, fires the battle-start trigger again, and reports where it ended up.
     */
    private static double energyAfterTrigger(Battle battle, Character firefly, double start) {
        firefly.setCurrentEnergy(start);
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.BATTLE_START, firefly, null, 0, 0));
        return firefly.getCurrentEnergy();
    }

    private static Character wearer(Battle[] out) {
        Character c = CharacterFactory.create(FIREFLY, LEVEL);
        Battle b = new Battle(List.of(c), List.of(EnemyFactory.create(1002011, 90, 1)), BattleFixture.seeded());
        b.startBattle();
        out[0] = b;
        return c;
    }

    private static int matched(Character owner, Battle battle) {
        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(owner, owner, owner, 0, 0, null, battle,
                SkillCategory.UNSPECIFIED);
        return owner.getTriggerTable().matching(TriggerEvent.ENERGY_GAINED, ctx).stream()
                .filter(r -> "talent_dispel_when_energy_full".equals(r.id())).toList().size();
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }
}
