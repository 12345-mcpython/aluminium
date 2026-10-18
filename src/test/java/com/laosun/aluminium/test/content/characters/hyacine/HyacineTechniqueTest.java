package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1409 风堇's technique 【天气正好，万物可爱！】(140907): 「下一次战斗开始时，为我方全体回复等同于风堇 30% 生命上限 + 600
 * 的生命值并使生命上限提高 20%，持续 2 回合」.
 *
 * <p>The technique happens before the battle, so the engine is TOLD about it ({@code Battle.markTechniqueUsed} sets the
 * {@code 秘技} state); the rule then hangs on {@code BATTLE_START} behind {@code self has_state 秘技}.
 */
public class HyacineTechniqueTest {

    private static final int HYACINE = 1409;
    private static final int ALLY = 1001;
    /** 1409's raw HEALTH base at Lv80 (the traces' +10% sits on top). */
    private static final double BASE_HP = 1086.624;

    @Test
    public void theTechniqueRaisesThePartysMaxHpAndHealsTheWounded() {
        Character hyacine = CharacterFactory.create(HYACINE, 80, true);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.markTechniqueUsed(hyacine);
        double maxBefore = hyacine.getAttribute(AttributeType.HEALTH).get();
        ally.consumeHp(ally.getAttribute(AttributeType.HEALTH).get() * 0.5);
        double wounded = ally.getCurrentHp();

        battle.startBattle();
        battle.processRequests();

        Assertions.assertEquals(maxBefore + BASE_HP * 0.20, hyacine.getAttribute(AttributeType.HEALTH).get(), 1e-6,
                "「生命上限提高 20%」，持续 2 回合");
        Assertions.assertTrue(ally.getCurrentHp() > wounded,
                "「为我方全体回复等同于风堇 30% 生命上限 + 600 的生命值」（受伤前 " + wounded + "，之后 " + ally.getCurrentHp() + "）");
    }

    @Test
    public void withoutTheTechniqueNothingIsAdded() {
        Character hyacine = CharacterFactory.create(HYACINE, 80, true);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        double maxBefore = hyacine.getAttribute(AttributeType.HEALTH).get();
        battle.startBattle();
        battle.processRequests();
        Assertions.assertEquals(maxBefore, hyacine.getAttribute(AttributeType.HEALTH).get(), 1e-6,
                "no technique used: no 秘技 state, so the rule does not fire");
    }
}
