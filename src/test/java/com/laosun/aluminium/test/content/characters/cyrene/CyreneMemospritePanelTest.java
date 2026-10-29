package com.laosun.aluminium.test.content.characters.cyrene;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.entity.CanHit;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1415 昔涟's memosprite 德谬歌: 「德谬歌初始拥有等同于昔涟 100% 生命上限的生命上限」 (141503, params [1, 24, 0.5, 12]).
 *
 * <p>The panel lives in {@code memosprites/1415.json} as {@code {"attribute": "HEALTH", "percent": 1.0}}; this case
 * measures the result rather than the declaration, because "100% of the summoner's Max HP" is the kind of share that
 * looks right either way.
 */
public class CyreneMemospritePanelTest {

    private static final int CYRENE = 1415;
    private static final int ALLY = 1001;

    @Test
    public void theMemospriteHasHundredPercentOfHerMaxHp() {
        Character cyrene = CharacterFactory.create(CYRENE, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(cyrene, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        SkillExecutor.execute(battle, cyrene.getSkills().get(SkillType.ULTRA), cyrene,
                List.of(battle.enemies().get(0)));
        battle.processRequests();
        CanHit demiurge = battle.memospriteOf(cyrene);
        Assertions.assertNotNull(demiurge, "precondition: 德谬歌 is on the field");
        double hers = cyrene.getAttribute(AttributeType.HEALTH).get();
        Assertions.assertEquals(hers, demiurge.getMaxHp(), Math.max(1e-6, hers * 1e-9),
                "「等同于昔涟 100% 生命上限」 (hers=" + hers + ", its=" + demiurge.getMaxHp() + ")");
    }
}
