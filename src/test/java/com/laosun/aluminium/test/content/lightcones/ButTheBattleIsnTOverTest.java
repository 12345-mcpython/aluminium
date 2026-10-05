package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
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
 * Light cone 23003: an Ultimate aimed at an ALLY restores one skill point -- but only every second such Ultimate.
 *
 * <p>A two-step counter, and the threshold is 2 rather than 1 on purpose: the rules of one event are evaluated IN ORDER and
 * see each other's effects, so a threshold of 1 paid on EVERY cast (measured: points 1/2/3 after the first three). With the
 * threshold at 2 the first cast's increment is invisible to the payout, and the same-pass evaluation is harmless.
 */
public class ButTheBattleIsnTOverTest {
    private static final int CONE = 23003;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String COUNT = "终结计数";

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
        while (battle.spendSkillPoint()) {
            // drain, so every point granted is visible
        }
        return battle;
    }

    private void ultOnAlly(Battle battle, CanHit ally) {
        battle.fireTriggers(TriggerEvent.ULT_CAST, wearer, ally, 0, 0);
    }

    private int rulesMatching(Battle battle, CanHit ally) {
        return (int) wearer.getTriggerTable().matching(TriggerEvent.ULT_CAST,
                        new TriggerTable.TriggerContext(wearer, wearer, ally, 0, 0, null, battle, null))
                .stream().filter(rule -> rule.id().startsWith("cone23003_")).count();
    }

    @Test
    public void everySecondUltimateOnAnAllyPays() {
        Battle battle = battle(true);
        CanHit ally = battle.allies.get(1);
        ultOnAlly(battle, ally);
        int afterFirst = battle.getSkillPoints();
        int counted = wearer.getBuffManager().stacksOf(COUNT);
        ultOnAlly(battle, ally);
        int afterSecond = battle.getSkillPoints();
        int left = wearer.getBuffManager().stacksOf(COUNT);
        ultOnAlly(battle, ally);
        int afterThird = battle.getSkillPoints();
        System.out.println("[23003] points after 1st/2nd/3rd ultimate = " + afterFirst + "/" + afterSecond + "/" + afterThird
                + " ; counter after 1st=" + counted + " after 2nd=" + left);
        Assertions.assertEquals(0, afterFirst, "the FIRST one only counts");
        Assertions.assertEquals(1, counted, "and the counter holds 1");
        Assertions.assertEquals(1, afterSecond, "the second pays one skill point");
        Assertions.assertEquals(0, left, "and clears the counter");
        Assertions.assertEquals(1, afterThird, "so the third only counts again");
    }

    @Test
    public void theCounterItselfIsObservable() {
        Battle battle = battle(true);
        CanHit ally = battle.allies.get(1);
        int atZero = rulesMatching(battle, ally);
        ultOnAlly(battle, ally);
        int atOne = rulesMatching(battle, ally);
        System.out.println("[23003] rules matching at 0/1 casts = " + atZero + "/" + atOne);
        Assertions.assertEquals(1, atZero, "at zero the counting rule is live");
        Assertions.assertEquals(1, atOne, "at one the paying rule is live instead (they are mutually exclusive)");
    }

    @Test
    public void anUltimateOnAnEnemyNeverCounts() {
        Battle battle = battle(true);
        battle.fireTriggers(TriggerEvent.ULT_CAST, wearer, enemy, 0, 0);
        int counted = wearer.getBuffManager().stacksOf(COUNT);
        System.out.println("[23003] after an ultimate aimed at an ENEMY: counter=" + counted
                + " points=" + battle.getSkillPoints());
        Assertions.assertEquals(0, counted, "对我方目标 (false case)");
        Assertions.assertEquals(0, battle.getSkillPoints(), "and nothing is paid");
    }

    @Test
    public void somebodyElsesUltimateDoesNotCount() {
        Battle battle = battle(true);
        CanHit ally = battle.allies.get(1);
        battle.fireTriggers(TriggerEvent.ULT_CAST, ally, wearer, 0, 0);
        System.out.println("[23003] after the ALLY's ultimate: counter=" + wearer.getBuffManager().stacksOf(COUNT));
        Assertions.assertEquals(0, wearer.getBuffManager().stacksOf(COUNT), "the wearer's own casts (false case)");
    }

    @Test
    public void withoutTheConeNoRuleExists() {
        Battle battle = battle(false);
        CanHit ally = battle.allies.get(1);
        int rules = rulesMatching(battle, ally);
        System.out.println("[23003] without the cone, rules matching: " + rules);
        Assertions.assertEquals(0, rules, "no cone, no rule (false case)");
    }
}
