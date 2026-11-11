package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1409 Hyacine, from her own file.
 *
 * <p>The rules cover only what a skill's data does NOT do. Measured this round: the HEAL op is exact (`owner_max_hp` 10% + 100 healed
 * 219.52864 against a declared 219.52864000000002 when isolated on TURN_START), while a skill also heals from its own data - so a rule that
 * repeats the document's heal double-counts it. Her state and the party Max HP raise are therefore the shipped clauses.
 */
public class HyacineTest {
    private static final int HYACINE = 1409;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    /**
     * Note: "风堇进入[雨过天晴]状态" and "我方全体目标生命上限提高30.00%".
     */
    @Test
    public void herUltimateMarksHerAndRaisesThePartysMaxHp() {
        Character hyacine = CharacterFactory.create(HYACINE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(hyacine, ally), List.of(enemy), fixed());
        battle.startBattle();
        double allyMaxBefore = ally.getMaxHp();
        double herMaxBefore = hyacine.getMaxHp();

        battle.castImmediate(hyacine.getSkills().get(SkillType.ULTRA), hyacine, List.of(ally));

        Assertions.assertTrue(hyacine.getBuffManager().hasState("雨过天晴"),
                "「风堇进入【雨过天晴】状态」 (Hyacine enters the After Rain (【雨过天晴】) state)");
        Assertions.assertTrue(ally.getMaxHp() > allyMaxBefore, "「我方全体目标生命上限提高30.00%」 (all allies' Max HP is raised by 30.00%) -- ally");
        Assertions.assertTrue(hyacine.getMaxHp() > herMaxBefore, "「我方全体」 (all allies) includes Hyacine herself");
    }


    private static Random fixed() {
        return BattleFixture.alwaysRoll(1.0);
    }
}
