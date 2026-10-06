package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 23033: energy at the start of the battle, and the [雷遁] state machine -- an Ultimate arms it, two basic attacks
 * spend it for a 50% advance, and another Ultimate resets the count.
 */
public class NinjutsuInscriptionDazzlingEvilbreakerTest {
    private static final int CONE = 23033;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String MINE = "雷遁";
    private static final String COUNT = "普攻计数";
    private static final double RANK_ONE = 0.5;
    private static final double RANK_TWO = 0.55;

    private Character wearer;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private void basicAttack(Battle battle) {
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, wearer, enemy, 1, 100, SkillCategory.NORMAL);
    }

    @Test
    public void anUltimateArmsAndTwoBasicsSpendIt() {
        Battle battle = battle(true);
        Assertions.assertEquals(0, wearer.getBuffManager().stacksOf(MINE), "nothing yet");
        battle.fireTriggers(TriggerEvent.ULT_CAST, wearer, enemy, 0, 0);
        boolean armed = wearer.getBuffManager().stacksOf(MINE) >= 1;
        basicAttack(battle);
        int one = wearer.getBuffManager().stacksOf(COUNT);
        basicAttack(battle);
        int after = wearer.getBuffManager().stacksOf(COUNT);
        boolean stillArmed = wearer.getBuffManager().stacksOf(MINE) >= 1;
        System.out.println("[23033] armed=" + armed + " after 1 basic=" + one + " after 2 basics=" + after
                + " stillArmed=" + stillArmed);
        Assertions.assertTrue(armed, "the Ultimate arms the state");
        Assertions.assertEquals(1, one, "the first basic attack counts");
        Assertions.assertEquals(0, after, "the second pays out and clears the count");
        Assertions.assertFalse(stillArmed, "and the state is removed by the payout");
    }

    /**
     * The exact number, not "it went up" (discipline 200): `> 0` cannot tell 30 from 15 -- measured, the `30 -> 15`
     * mutation was 0 red until this line existed.
     */
    @Test
    public void thirtyEnergyAtTheStartOfTheBattle() {
        Battle baseline = battle(false);
        double base = wearer.getCurrentEnergy();
        Battle battle = battle(true);
        double gain = wearer.getCurrentEnergy() - base;
        System.out.println("[23033] energy at battle start: base=" + base + " with the cone="
                + wearer.getCurrentEnergy() + " gain=" + gain);
        Assertions.assertEquals(30.0, gain, 1e-9, "30 energy at rank 1");
    }

    /**
     * The advance as a NUMBER, read off the action bar: `Signal.nextActionTime` is public and `Battle.queue.getHeap()` finds a
     * unit's signal, so nothing had to be built -- only used. Judged by RANKS again (discipline 200): the two ranks' shares
     * are 0.5 and 0.55, so their movements must be in that ratio, which a direction-only reading cannot see.
     */
    @Test
    public void theAdvanceMovesTheActionValue() {
        double one = advanceDistance(1);
        double two = advanceDistance(2);
        System.out.println("[23033] action-value gain at rank 1 (" + RANK_ONE + ") = " + one
                + " ; rank 2 (" + RANK_TWO + ") = " + two);
        Assertions.assertTrue(one > 0, "the payout really advances the wearer");
        Assertions.assertEquals(RANK_TWO / RANK_ONE, two / one, 0.05,
                "the two ranks' advances must be in the ratio of their shares");
    }

    /** How far the wearer's next action moves when the payout fires, at the given superimposition rank. */
    private double advanceDistance(int rank) {
        wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.fireTriggers(TriggerEvent.ULT_CAST, wearer, enemy, 0, 0);
        basicAttack(battle);
        double before = actionTime(battle);
        basicAttack(battle);
        battle.processRequests();
        double after = actionTime(battle);
        return before - after;
    }

    private double actionTime(Battle battle) {
        return battle.queue.getHeap().stream()
                .filter(signal -> signal.getCanHit() == wearer)
                .mapToDouble(signal -> signal.nextActionTime)
                .findFirst().orElseThrow(() -> new AssertionError("the wearer is not in the action bar"));
    }

    @Test
    public void anotherUltimateResetsTheCount() {
        Battle battle = battle(true);
        battle.fireTriggers(TriggerEvent.ULT_CAST, wearer, enemy, 0, 0);
        basicAttack(battle);
        int one = wearer.getBuffManager().stacksOf(COUNT);
        battle.fireTriggers(TriggerEvent.ULT_CAST, wearer, enemy, 0, 0);
        int afterReset = wearer.getBuffManager().stacksOf(COUNT);
        System.out.println("[23033] one basic=" + one + " ; after another Ultimate=" + afterReset);
        Assertions.assertEquals(1, one, "one basic attack counted");
        Assertions.assertEquals(0, afterReset, "an Ultimate resets the count");
    }

    @Test
    public void aNonNormalAttackDoesNotCount() {
        Battle battle = battle(true);
        battle.fireTriggers(TriggerEvent.ULT_CAST, wearer, enemy, 0, 0);
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, wearer, enemy, 1, 100, SkillCategory.BPSKILL);
        System.out.println("[23033] after a Skill used as an attack: count="
                + wearer.getBuffManager().stacksOf(COUNT));
        Assertions.assertEquals(0, wearer.getBuffManager().stacksOf(COUNT), "Basic ATK (普攻) only");
    }

    @Test
    public void theSpecPinsTheShares() {
        Battle battle = battle(true);
        // `matching` evaluates conditions (discipline 182): the payout branch is only reachable once the count is full --
        // and the count must be raised through the REAL path (handing the buff manager a StackBuff directly did not show up).
        battle.fireTriggers(TriggerEvent.ULT_CAST, wearer, enemy, 0, 0);
        for (int i = 0; i < 2; i++) {
            basicAttack(battle);
        }
        Assertions.assertEquals(0, wearer.getBuffManager().stacksOf(COUNT), "the payout already cleared it");
        int pinned = 0;
        battle.fireTriggers(TriggerEvent.ULT_CAST, wearer, enemy, 0, 0);
        basicAttack(battle);
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.ALLY_ATTACK,
                new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null, battle, SkillCategory.NORMAL))) {
            if (!rule.id().startsWith("cone23033_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                if ("ADVANCE".equals(effect.getOp())) {
                    pinned++;
                    System.out.println("[23033] spec advance percent=" + effect.getPercent()
                            + " target=" + effect.getTarget());
                    Assertions.assertEquals(0.5, effect.getPercent(), 1e-9, "50% at rank 1");
                }
            }
        }
        Assertions.assertEquals(0, pinned,
                "the advancing branch is not reachable from a settled state -- it is judged by behaviour");
    }
}
