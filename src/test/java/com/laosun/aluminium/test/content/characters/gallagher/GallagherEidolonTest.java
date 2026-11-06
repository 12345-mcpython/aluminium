package com.laosun.aluminium.test.content.characters.gallagher;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1301 加拉赫's Eidolons 2 and 6, written 2026-11-06 during the section-by-section audit.
 *
 * <p>星魂 2 was one of the four "not read, not written" entries; 星魂 6's second half (weakness-break efficiency) is
 * registered instead, because the engine has no such quantity.
 */
public class GallagherEidolonTest {
    private static final int GALLAGHER = 1301;
    private static final int ALLY = 1204;
    private static final int LEVEL = 80;

    /** 星魂 2「施放战技时，解除指定我方单体的 1 个负面效果，同时使其效果抵抗提高 30%，持续 2 回合」. */
    @Test
    public void theSecondEidolonCleansesAndHardensTheTarget() {
        Character gallagher = CharacterFactory.create(GALLAGHER, LEVEL, false, null, null, 2);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(gallagher, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        ally.getBuffManager().addBuff(com.laosun.aluminium.buff.StatModifierBuff.percentDebuff(
                AttributeType.SPEED, -0.2, 3));
        int debuffs = ally.getBuffManager().debuffCount();
        double before = ally.getAttribute(AttributeType.EFFECT_RESISTANCE).get();

        battle.castImmediate(gallagher.getSkills().get(SkillType.SKILL), gallagher, List.of(ally));
        battle.processRequests();

        Assertions.assertEquals(debuffs - 1, ally.getBuffManager().debuffCount(), "「解除…1 个负面效果」");
        Assertions.assertEquals(before + 0.3, ally.getAttribute(AttributeType.EFFECT_RESISTANCE).get(), 1e-6,
                "「使其效果抵抗提高 30%」");
    }

    /** 星魂 6 前半「加拉赫的击破特攻提高 20%」. */
    @Test
    public void theSixthEidolonRaisesHisBreakEffect() {
        double atFive = breakEffect(5);
        double atSix = breakEffect(6);
        Assertions.assertEquals(0.2, atSix - atFive, 1e-6,
                "「击破特攻提高 20%」 — and nothing before 星魂 6");
    }

    private static double breakEffect(int eidolon) {
        Character gallagher = CharacterFactory.create(GALLAGHER, LEVEL, false, null, null, eidolon);
        Battle battle = new Battle(List.of(gallagher), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        return gallagher.getAttribute(AttributeType.BREAKING_EFFECT).get();
    }
}
