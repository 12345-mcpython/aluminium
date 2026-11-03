package com.laosun.aluminium.test.content.characters.ruanmei;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
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

    /**
     * 战技: 「当阮•梅拥有【弦外音】时，我方全体伤害提高 32%」 — the cross-unit gate.
     *
     * <p>This was registered as unwritable for months. {@code while_state} checks the RULE OWNER's state, not the modifier
     * holder's, so "she holds it ⇒ the party gets the modifier" is exactly what it expresses — and the modifier stops when her
     * state does.
     */
    @Test
    public void theOvertoneBoostsTheWholeSideWhileSheHoldsIt() {
        Character ruanMei = CharacterFactory.create(RUAN_MEI, 80);
        Character ally = CharacterFactory.create(1002, 80);
        Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        Battle battle = new Battle(List.of(ruanMei, ally), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        double before = ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        battle.castImmediate(ruanMei.getSkills().get(SkillType.SKILL), ruanMei, List.of(ally));
        battle.processRequests();

        Assertions.assertTrue(ruanMei.getBuffManager().hasState("弦外音"), "precondition: the Skill grants 弦外音");
        Assertions.assertEquals(before + 0.32, ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "「当阮•梅拥有【弦外音】时，我方全体伤害提高 32%」 — the ALLY carries it");
    }
}
