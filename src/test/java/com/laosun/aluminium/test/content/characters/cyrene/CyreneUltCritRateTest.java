package com.laosun.aluminium.test.content.characters.cyrene;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1415 昔涟's ultimate: 「昔涟和德谬歌的暴击率提高 50%」 (141503, params [1, 24, 0.5, 12]).
 *
 * <p>CRIT RATE is a <b>point</b> attribute: the document says "+50%", i.e. +0.5 in the engine's unit, and a `percent`
 * modifier would be read as a share of her base instead (the trap already paid for once on CRIT DMG: 0.6 x 0.12 = 0.072).
 */
public class CyreneUltCritRateTest {

    private static final int CYRENE = 1415;
    private static final int ALLY = 1001;

    @Test
    public void theUltimateGivesHerExactlyFiftyPointsOfCritRate() {
        Character cyrene = CharacterFactory.create(CYRENE, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(cyrene, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        double before = cyrene.getAttribute(AttributeType.CRIT_CHANCE).get();
        SkillExecutor.execute(battle, cyrene.getSkills().get(SkillType.ULTRA), cyrene,
                List.of(battle.enemies().get(0)));
        battle.processRequests();
        double after = cyrene.getAttribute(AttributeType.CRIT_CHANCE).get();
        Assertions.assertEquals(0.5, after - before, 1e-6,
                "「暴击率提高 50%」 (before=" + before + ", after=" + after + ")");
        // 「**昔涟和德谬歌**的暴击率提高 50%」: the memosprite shares the clause.
        var demiurge = battle.memospriteOf(cyrene);
        Assertions.assertNotNull(demiurge, "precondition: 德谬歌 is on the field");
        Assertions.assertEquals(0.5, demiurge.getAttribute(AttributeType.CRIT_CHANCE).get(),
                "and 德谬歌 gets the same +50 points");
    }
}
