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
 * "受到致命攻击时不会陷入无法战斗状态，而是回复…" -- one capability, two readers (2026-10-02).
 *
 * <p>The hit is dealt through the battle's own settlement entry point with twice the unit's CURRENT HP, so it is lethal by
 * construction -- and the assertion is about survival, not about how much was healed.
 */
public class LethalHitTest {
    private static final int PHAINON = 1408;
    private static final int JINGLIU = 1104;
    private static final int MONSTER = 1002011;
    private static final String STATE = "变身";

    /** 1408: transformed, a lethal blow leaves her standing at a fifth of her (raised) Max HP. */
    @Test
    public void theTransformedFormSurvivesALethalBlow() {
        Character her = survivor(PHAINON, true);
        Assertions.assertFalse(her.isDeath(), "「卡厄斯兰那受到致命攻击时不会陷入无法战斗状态」");
        Assertions.assertEquals(her.getMaxHp() * 0.20, her.getCurrentHp(), her.getMaxHp() * 0.01,
                "「而是回复等同于自身生命上限 20% 的生命值」");
    }

    /** Note: 1408 untransformed: the same blow kills her. */
    @Test
    public void withoutTheTransformationTheBlowKills() {
        Assertions.assertTrue(survivor(PHAINON, false).isDeath(),
                "「变身期间」-- outside it the clause does not apply");
    }

    /** 1104: her trace saves her once -- at half of Max HP -- and the SECOND lethal blow kills her. */
    @Test
    public void jingliuSurvivesOnce() {
        Character her = survivor(JINGLIU, false);
        Assertions.assertFalse(her.isDeath(), "1104 的行迹救了她一次");
        Assertions.assertEquals(her.getMaxHp() * 0.50, her.getCurrentHp(), her.getMaxHp() * 0.01,
                "「回复等同于自身生命上限 50% 的生命值」");
    }

    /** Note: "该效果单场战斗中只能触发 1 次": the second lethal blow kills her. */
    @Test
    public void jingliuDiesToASecondLethalBlow() {
        Character her = CharacterFactory.create(JINGLIU, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(her),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        battle.applyTrueDamage(battle.enemies.getFirst(), her, DamageElement.ICE, her.getCurrentHp() * 2.0);
        battle.processRequests();
        Assertions.assertFalse(her.isDeath(), "precondition: the first blow is survived");

        battle.applyTrueDamage(battle.enemies.getFirst(), her, DamageElement.ICE, her.getCurrentHp() * 2.0);
        battle.processRequests();
        Assertions.assertTrue(her.isDeath(),
                "「该效果单场战斗中只能触发 1 次」-- the second one is not saved");
    }

    // ==================================================================

    /** builds the scene, optionally transforms her, deals one lethal hit, and returns her. */
    private static Character survivor(int cid, boolean transform) {
        Character her = CharacterFactory.create(cid, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(her),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        if (transform) {
            Skill ult = her.getSkills().get(SkillType.ULTRA);
            Assertions.assertNotNull(ult, "precondition: she has an ultimate");
            SkillExecutor.execute(battle, ult, her, List.of(her));
            battle.processRequests();
            Assertions.assertTrue(her.getBuffManager().hasState(STATE), "precondition: the transformation is on");
        }
        Assertions.assertFalse(her.isDeath(), "precondition: she starts alive");

        battle.applyTrueDamage(battle.enemies.getFirst(), her, DamageElement.ICE, her.getCurrentHp() * 2.0);
        battle.processRequests();
        return her;
    }
}
