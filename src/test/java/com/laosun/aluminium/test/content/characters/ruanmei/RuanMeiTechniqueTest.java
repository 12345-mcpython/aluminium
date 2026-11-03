package com.laosun.aluminium.test.content.characters.ruanmei;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1303 阮•梅: the technique — 「下一次战斗开始时自动触发 1 次战技」.
 *
 * <p><b>Why this needed a data entry first.</b> Her Skill is a Support buff: it deals no damage and heals nothing, so
 * {@code SkillExecutor.canDeliver} refused {@code CAST_SKILL} for it — the guard that stops a rule from commanding a cast that
 * would silently do nothing. 1412's Skill was in exactly that position and got a {@code Rules} entry in {@code skill_effects.json};
 * hers now has one too, which is what makes the command legitimate rather than the guard being loosened.
 */
public class RuanMeiTechniqueTest {

    private static final int RUAN_MEI = 1303;

    @Test
    public void theTechniqueAutoCastsTheSkillAtBattleStart() {
        Assertions.assertTrue(techniqueOpensTheZone(), "「使下一次战斗开始时自动触发 1 次战技」");
        Assertions.assertFalse(plainBattleOpensTheZone(), "control: without the technique nothing is cast");
    }

    private static boolean techniqueOpensTheZone() {
        Battle battle = battleWithTechnique(true);
        return !battle.characters().getFirst().getBuffManager().activeBuffs().isEmpty();
    }

    private static boolean plainBattleOpensTheZone() {
        Battle battle = battleWithTechnique(false);
        return !battle.characters().getFirst().getBuffManager().activeBuffs().isEmpty();
    }

    private static Battle battleWithTechnique(boolean technique) {
        Character ruanMei = CharacterFactory.create(RUAN_MEI, 80);
        Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        Battle battle = new Battle(List.of(ruanMei), List.of(enemy), BattleFixture.seeded());
        if (technique) {
            battle.markTechniqueUsed(ruanMei);
        }
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}

