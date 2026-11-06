package com.laosun.aluminium.test.content.characters.aglaea;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1402 阿格莱雅's Eidolon level additions: 星魂 3 「战技等级+2；普攻等级+1」, 星魂 5 「终结技等级+2；天赋等级+2」.
 *
 * <p>The two rules existed before this test, but each carried one effect that mapped a <b>memosprite</b> slot (1140201 onto
 * COMMON, 1140203 onto ULTRA) - a wrong number that loaded and ran. Read through {@code skillLevel}, the one resolver every
 * row read goes through, so a raise that never reached the skill cannot pass.
 */
public class AglaeaEidolonLevelsTest {
    private static final int AGLAEA = 1402;
    private static final int LEVEL = 80;

    @Test
    public void theThirdEidolonRaisesTheSkillAndTheBasic() {
        // The file's level convention reads COMMON at 6 and SKILL at 10; 星魂 3 adds +2 to the skill and +1 to the basic.
        Assertions.assertEquals(10, level(SkillType.SKILL, 2), "before 星魂 3 the skill sits at the document's level");
        Assertions.assertEquals(12, level(SkillType.SKILL, 3), "「战技等级+2」");
        Assertions.assertEquals(6, level(SkillType.COMMON, 2), "before 星魂 3 the basic sits at the document's level");
        Assertions.assertEquals(7, level(SkillType.COMMON, 3), "「普攻等级+1」");
    }

    @Test
    public void theFifthEidolonRaisesTheUltimateAndTheTalent() {
        Assertions.assertEquals(10, level(SkillType.ULTRA, 4), "before 星魂 5 the ultimate sits at the document's level");
        Assertions.assertEquals(12, level(SkillType.ULTRA, 5), "「终结技等级+2」");
        Assertions.assertEquals(12, level(SkillType.TALENT, 5), "「天赋等级+2」");
    }

    /** The slot's level in a battle of that Eidolon rank. */
    private static int level(SkillType slot, int eidolon) {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL, false, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return aglaea.skillLevel(aglaea.getSkills().get(slot));
    }
}
