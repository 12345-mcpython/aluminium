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
 * 1217：「当藿藿拥有【禳命】时，若我方目标受到致命攻击…立即回复等同于其自身生命上限 50% 的生命值」 (2026-10-02).
 *
 * <p>⭐ ONE VARIABLE: the eidolon rank. Same party, same skill (which is what puts 【禳命】 on her), same lethal blow.
 */
public class TalismanSavesAnAllyTest {
    private static final int HUOHUO = 1217;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String STATE = "禳命";

    /** ⭐ At E2 the ally survives, at HALF OF ITS OWN Max HP. */
    @Test
    public void atEidolonTwoTheAllySurvives() {
        double[] result = afterLethalBlow(2);
        Assertions.assertTrue(result[0] > 0, "「不会陷入无法战斗状态」 (hp " + result[0] + ")");
        Assertions.assertEquals(result[1] * 0.50, result[0], result[1] * 0.01,
                "「回复等同于**其自身**生命上限 50%」-- the VICTIM’s own, not the healer’s");
    }

    /** ⚠ Below E2 the ally falls. */
    @Test
    public void belowEidolonTwoTheAllyFalls() {
        Assertions.assertTrue(afterLethalBlow(0)[0] <= 0, "星魂 2 才有这一条");
    }

    // ==================================================================

    /** { the ally's HP after the blow, the ally's Max HP }. */
    private static double[] afterLethalBlow(int eidolon) {
        Character her = CharacterFactory.create(HUOHUO, 80, false, null, null, eidolon);
        Character ally = CharacterFactory.create(ALLY, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(her, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        // Her skill is what puts 【禳命】 on her (2 turns, ticking on her own turns).
        Skill skill = her.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: she has a skill");
        SkillExecutor.execute(battle, skill, her, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        Assertions.assertTrue(her.getBuffManager().hasState(STATE), "precondition: 【禳命】 is on her");

        Battle dummy = battle;
        dummy.applyTrueDamage(battle.enemies.getFirst(), ally, DamageElement.ICE, ally.getCurrentHp() * 2.0);
        battle.processRequests();
        return new double[]{ally.isDeath() ? 0 : ally.getCurrentHp(), ally.getMaxHp()};
    }
}
