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
 * 1404："[血仇]状态下生命上限提高，数值等同于当前生命上限的 50%".
 *
 * <p>ONE VARIABLE, in one scene: the state goes ON (a hundred charge, five ultimates) and then OFF (the lethal blow the paragraph
 * names as its only exit). Max HP must follow it both ways.
 */
public class BloodfeudMaxHpTest {
    private static final int MYDEI = 1404;
    private static final int MONSTER = 1002011;
    private static final String STATE = "血仇";
    private static final String CHARGE = "天赋充能";

    /** +50% Max HP while [血仇] is on, and back to the base when it leaves. */
    @Test
    public void theRaiseFollowsTheState() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Skill ult = him.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: he has an ultimate");
        // Note: FOUR ultimates, then the reading, then the fifth: the entry fires at a hundred, so this compares "immediately before
        // the state" with "immediately after" it. Measuring the baseline earlier (right after startBattle) let an unrelated modifier
        // expire in between -- that is exactly the ~5% gap the first version of this judge reported (24.6 expected, 260.9 seen).
        for (int i = 0; i < 4; i++) {
            SkillExecutor.execute(battle, ult, him, List.of(battle.enemies.getFirst()));
            battle.processRequests();
        }
        Assertions.assertFalse(him.getBuffManager().hasState(STATE), "precondition: not a hundred yet");
        double base = him.getMaxHp();
        SkillExecutor.execute(battle, ult, him, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        Assertions.assertTrue(him.getBuffManager().hasState(STATE), "precondition: 【血仇】 (Vendetta) is on");
        Assertions.assertEquals(base * 1.5, him.getMaxHp(), base * 0.01,
                "「【血仇】状态下生命上限提高，数值等同于当前生命上限的 50%」 (while in the Vendetta (【血仇】) state, Max HP is raised by an amount equal to 50% of the current Max HP)");

        // The paragraph's ONLY exit: the lethal blow. The raise must go with the state.
        battle.applyTrueDamage(battle.enemies.getFirst(), him, DamageElement.ICE, him.getCurrentHp() * 2.0);
        battle.processRequests();
        Assertions.assertFalse(him.getBuffManager().hasState(STATE), "precondition: the lethal blow ended it");
        Assertions.assertEquals(base, him.getMaxHp(), base * 0.01,
                "「退出【血仇】状态」 (leaves the Vendetta state)-- the extra Max HP leaves with it");
    }
}
