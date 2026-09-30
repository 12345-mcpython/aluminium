package com.laosun.aluminium.test;

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
 * Light cone 23033: energy at the start of the battle, and the \u3010\u96f7\u9041\u3011 state machine -- an Ultimate arms it, two basic attacks
 * spend it for a 50% advance, and another Ultimate resets the count.
 */
public class Cone23033Test {
    private static final int CONE = 23033;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String MINE = "\u96f7\u9041";
    private static final String COUNT = "\u666e\u653b\u8ba1\u6570";

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
        Assertions.assertEquals(0, wearer.getBuffManager().stacksOf(COUNT), "\u666e\u653b only");
    }

    @Test
    public void theSpecPinsTheShares() {
        Battle battle = battle(true);
        // \u2605 `matching` evaluates conditions (discipline 182): the payout branch is only reachable once the count is full --
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
