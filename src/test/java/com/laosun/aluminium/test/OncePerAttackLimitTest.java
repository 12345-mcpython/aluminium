package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * `once_per_attack` -- the firing limit scoped to ONE attack.
 *
 * <p>Readers: relic set 115's 4-piece, cone 23008 ("每次攻击最多通过该方式恢复 3 次能量") and cone 21031
 * ("该效果每次攻击只可触发 1 次"). The point of the field is that an attack can settle MANY instances, so a
 * per-turn count cannot express it -- and two attacks in one turn must both be allowed.
 */
public class OncePerAttackLimitTest {
    private static final int OWNER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    // \u26a0 Kept as fields: Battle exposes no "give me my party" accessor (measured), and every reading here needs
    // the same two units.
    private Character owner;
    private Enemy enemy;

    private Battle battleWith(TriggerSpec rule) {
        owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(rule)));
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private int fire(Battle battle) {
        return battle.fireTriggers(TriggerEvent.DEALING_DAMAGE, owner, enemy, 0, 0);
    }

    private void endAttack(Battle battle) {
        battle.fireAfterAttack(owner, enemy, List.of(enemy), 1000);
    }

    @Test
    public void oneFiringPerAttackAndTheNextAttackFiresAgain() {
        TriggerSpec rule = TriggerSpecs.rule("DEALING_DAMAGE", null, TriggerSpecs.gainEnergy(1));
        TriggerSpecs.set(rule, "oncePerAttack", true);
        Battle battle = battleWith(rule);
        int first = fire(battle);
        int second = fire(battle);
        endAttack(battle);
        int third = fire(battle);
        System.out.println("[limit] once_per_attack: first=" + first + " second(same attack)=" + second
                + " third(next attack, same turn)=" + third);
        Assertions.assertEquals(1, first, "the attack's first instance fires");
        Assertions.assertEquals(0, second, "a later instance of the SAME attack does not");
        Assertions.assertEquals(1, third, "the next attack fires again -- still the same turn");
    }

    @Test
    public void withoutTheFieldEveryInstanceFires() {
        TriggerSpec rule = TriggerSpecs.rule("DEALING_DAMAGE", null, TriggerSpecs.gainEnergy(1));
        Battle battle = battleWith(rule);
        int first = fire(battle);
        int second = fire(battle);
        endAttack(battle);
        int third = fire(battle);
        System.out.println("[limit] no limit: " + first + " / " + second + " / " + third);
        Assertions.assertEquals(1, first, "no limit: every instance is its own firing");
        Assertions.assertEquals(1, second, "no limit: the second instance too");
        Assertions.assertEquals(1, third, "no limit: and after the attack boundary");
    }

    /** The contrast that proves the field is not `per_turn`: two attacks in ONE turn. */
    @Test
    public void perTurnIsNotTheSameThing() {
        TriggerSpec rule = TriggerSpecs.rule("DEALING_DAMAGE", null, TriggerSpecs.gainEnergy(1));
        TriggerSpecs.set(rule, "perTurn", 1);
        Battle battle = battleWith(rule);
        int first = fire(battle);
        endAttack(battle);
        int second = fire(battle);
        System.out.println("[limit] per_turn=1: first=" + first + " secondAttackSameTurn=" + second);
        Assertions.assertEquals(1, first, "per_turn lets the first firing through");
        Assertions.assertEquals(0, second,
                "but a SECOND attack in the same turn is blocked -- which once_per_attack allows");
    }
}
