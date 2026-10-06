package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1404: [血仇] - - entering it, and the lethal blow that ends it.
 *
 * <p>ONE VARIABLE: whether [血仇] is on when the lethal blow lands. His ultimate grants 20 charge, so five of them reach the
 * hundred the entry clause needs -- no test-only shortcut into his resource.
 */
public class BloodfeudTest {
    private static final int MYDEI = 1404;
    private static final int MONSTER = 1002011;
    private static final String STATE = "血仇";
    private static final String CHARGE = "天赋充能";

    /** A hundred charge enters [血仇], and a lethal blow then leaves it -- at half his Max HP. */
    @Test
    public void aLethalBlowEndsItAndHeSurvives() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        chargeToAHundred(battle, him);
        Assertions.assertTrue(him.getBuffManager().hasState(STATE),
                "\"when charge reaches 100, consume 100 points of charge to enter the [血仇] state\"");

        battle.applyTrueDamage(battle.enemies.getFirst(), him, DamageElement.ICE, him.getCurrentHp() * 2.0);
        battle.processRequests();

        Assertions.assertFalse(him.isDeath(), "\"will not enter the unable-to-fight state\"");
        Assertions.assertEquals(him.getMaxHp() * 0.50, him.getCurrentHp(), him.getMaxHp() * 0.01,
                "\"restore HP equal to 50% of one's own Max HP\"");
        Assertions.assertFalse(him.getBuffManager().hasState(STATE), "\"leave the [血仇] state\"");
        Assertions.assertEquals(0.0, him.getResources().value(CHARGE), 1e-9, "\"clear the charge\"");
    }

    /** Note: Without [血仇] the same blow kills him -- the clause is the state's, not his. */
    @Test
    public void withoutBloodfeudTheBlowKills() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Assertions.assertFalse(him.getBuffManager().hasState(STATE), "precondition: no [血仇] yet");

        battle.applyTrueDamage(battle.enemies.getFirst(), him, DamageElement.ICE, him.getCurrentHp() * 2.0);
        battle.processRequests();
        Assertions.assertTrue(him.isDeath(), "only while in the [血仇] state does this clause exist");
    }

    // ==================================================================

    /** his ultimate grants 20 charge, so five casts reach a hundred. */
    private static void chargeToAHundred(Battle battle, Character him) {
        Skill ult = him.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: he has an ultimate");
        for (int i = 0; i < 5; i++) {
            SkillExecutor.execute(battle, ult, him, List.of(battle.enemies.getFirst()));
            battle.processRequests();
        }
    }
}
