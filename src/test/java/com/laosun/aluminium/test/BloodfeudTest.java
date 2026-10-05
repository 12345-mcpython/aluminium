package com.laosun.aluminium.test;

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
 * 1404：[血仇] -  - 进入它，以及结束它的致命一击 (2026-10-02).
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
                "「充能达到 100 时消耗 100 点充能进入【血仇】状态」");

        battle.applyTrueDamage(battle.enemies.getFirst(), him, DamageElement.ICE, him.getCurrentHp() * 2.0);
        battle.processRequests();

        Assertions.assertFalse(him.isDeath(), "「不会陷入无法战斗状态」");
        Assertions.assertEquals(him.getMaxHp() * 0.50, him.getCurrentHp(), him.getMaxHp() * 0.01,
                "「回复等同于自身 50% 生命上限的生命值」");
        Assertions.assertFalse(him.getBuffManager().hasState(STATE), "「退出【血仇】状态」");
        Assertions.assertEquals(0.0, him.getResources().value(CHARGE), 1e-9, "「清空充能」");
    }

    /** Note: Without [血仇] the same blow kills him -- the clause is the state's, not his. */
    @Test
    public void withoutBloodfeudTheBlowKills() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Assertions.assertFalse(him.getBuffManager().hasState(STATE), "precondition: no 【血仇】 yet");

        battle.applyTrueDamage(battle.enemies.getFirst(), him, DamageElement.ICE, him.getCurrentHp() * 2.0);
        battle.processRequests();
        Assertions.assertTrue(him.isDeath(), "【血仇】状态期间才有这一条");
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
