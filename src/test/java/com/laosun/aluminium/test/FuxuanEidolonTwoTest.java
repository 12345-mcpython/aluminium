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
 * 1208：「【穷观阵】开启时，若我方目标受到致命伤害…立即回复等同于其自身生命上限 70% 的生命值。
 * 该效果单场战斗中可以触发 1 次」 (2026-10-02).
 *
 * <p>⭐ TWO READINGS IN ONE SCENE: the first lethal blow is answered (the ally stands at 70% of its OWN Max HP), and the second is not --
 * which is exactly 「单场战斗中可以触发 1 次」. The zone is opened by her own skill, so no test-only shortcut is used.
 */
public class FuxuanEidolonTwoTest {
    private static final int FUXUAN = 1208;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String ZONE = "鉴知";

    /** ⭐ The first blow is answered; the second kills, because the sentence allows one. */
    @Test
    public void theFirstBlowIsAnsweredAndTheSecondIsNot() {
        Character her = CharacterFactory.create(FUXUAN, 80, false, null, null, 2);
        Character ally = CharacterFactory.create(ALLY, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(her, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Skill skill = her.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: she has a skill");
        SkillExecutor.execute(battle, skill, her, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        Assertions.assertTrue(her.getBuffManager().hasState(ZONE), "precondition: the zone is open (her state)");

        battle.applyTrueDamage(battle.enemies.getFirst(), ally, DamageElement.ICE, ally.getCurrentHp() * 2.0);
        battle.processRequests();
        Assertions.assertFalse(ally.isDeath(), "「不会陷入无法战斗状态」");
        Assertions.assertEquals(ally.getMaxHp() * 0.70, ally.getCurrentHp(), ally.getMaxHp() * 0.01,
                "「回复等同于**其自身**生命上限 70% 的生命值」");

        battle.applyTrueDamage(battle.enemies.getFirst(), ally, DamageElement.ICE, ally.getCurrentHp() * 2.0);
        battle.processRequests();
        Assertions.assertTrue(ally.isDeath(),
                "「该效果单场战斗中可以触发 1 次」-- the second one is not answered");
    }
}
