package com.laosun.aluminium.test.content.characters.cyrene;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1415 昔涟's <b>enhanced basic</b> 「向着爱与明天♪」 (141508) and the 【追忆】 its first clause states.
 *
 * <p>The row's sentence is 「获得 <b>#2</b> 点【追忆】并对指定敌方单体造成…#3%…随后对敌方全体…#1%…」 - #2 is 3 at every level,
 * and it is a row value, so the rule reads {@code cast_param} 1 rather than a literal.
 *
 * <p>⚠ <b>Why the ordinary basic needed a gate.</b> The enhanced basic is still a {@code Normal} attack, so the
 * ordinary 「获得 #2 点」 (+1) rule fired for it as well: 1 + 3 where the document says 3. The ordinary rule now says
 * "not while 【往昔的涟漪】 holds" - which is exactly when slot 8 is installed - and both sentences stay true.
 */
public class CyreneRecollectionTest {

    private static final int CYRENE = 1415;
    private static final String RECOLLECTION = "追忆";

    /**
     * Inside the ripple the enhanced row pays 3 and nothing else fires.
     */
    @Test
    public void theSharpenedBasicPaysThreeAndOnlyThree() {
        Character cyrene = CharacterFactory.create(CYRENE, 80);
        Battle battle = new Battle(List.of(cyrene), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        SkillExecutor.execute(battle, cyrene.getSkills().get(SkillType.ULTRA), cyrene,
                List.of(battle.enemies().getFirst()));
        battle.processRequests();
        Skill enhanced = cyrene.getSkills().get(SkillType.COMMON);
        Assertions.assertTrue(enhanced.getData().getDescription().contains("向着爱与明天"),
                "precondition: the ultimate installed the enhanced basic");
        double before = cyrene.getResources().get(RECOLLECTION).exactValue();

        SkillExecutor.execute(battle, enhanced, cyrene, List.of(battle.enemies().getFirst()));
        battle.processRequests();

        Assertions.assertEquals(3.0, cyrene.getResources().get(RECOLLECTION).exactValue() - before, 1e-9,
                "「获得 #2 点【追忆】」 = 3; the ordinary basic's +1 must not also fire inside the ripple");
    }

    /**
     * Outside the ripple the ordinary basic still pays its own 1 - the gate must not swallow that sentence.
     */
    @Test
    public void theOrdinaryBasicStillPaysOneOutsideTheRipple() {
        Character cyrene = CharacterFactory.create(CYRENE, 80);
        Battle battle = new Battle(List.of(cyrene), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        double before = cyrene.getResources().get(RECOLLECTION).exactValue();

        SkillExecutor.execute(battle, cyrene.getSkills().get(SkillType.COMMON), cyrene,
                List.of(battle.enemies().getFirst()));
        battle.processRequests();

        Assertions.assertEquals(before + 1.0, cyrene.getResources().get(RECOLLECTION).exactValue(), 1e-9,
                "the ordinary basic's own 「获得 #2 点」 (1) still fires when 【往昔的涟漪】 is not up");
    }
}
