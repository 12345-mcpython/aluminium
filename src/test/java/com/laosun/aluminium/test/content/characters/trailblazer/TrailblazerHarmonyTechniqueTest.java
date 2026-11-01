package com.laosun.aluminium.test.content.characters.trailblazer;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 8006 开拓者•同谐's technique: 「使用秘技后，下一次战斗开始时使我方全体的击破特攻提高 30%，持续 2 回合」 (the same clause its
 * sibling 8005 already carried).
 */
public class TrailblazerHarmonyTechniqueTest {

    private static final int TRAILBLAZER = 8006;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;

    @Test
    public void theTechniqueRaisesEveryonesBreakEffect() {
        Assertions.assertEquals(0.3, boost(true, true) - boost(false, true), 1e-9,
                "「我方全体的击破特攻提高 30%」 - measured on an ALLY, with and without the technique");
        Assertions.assertEquals(0.3, boost(true, false) - boost(false, false), 1e-9,
                "「我方全体」 includes the trailblazer herself");
    }

    /**
     * @param withTechnique whether the trailblazer entered with the technique
     * @param onAlly       measure the ally (true) or the trailblazer (false)
     */
    private static double boost(boolean withTechnique, boolean onAlly) {
        Character trailblazer = CharacterFactory.create(TRAILBLAZER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(trailblazer, ally)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        if (withTechnique) {
            battle.markTechniqueUsed(trailblazer);
        }
        battle.startBattle();
        Character measured = onAlly ? ally : trailblazer;
        return measured.getAttribute(AttributeType.BREAKING_EFFECT).get();
    }
}
