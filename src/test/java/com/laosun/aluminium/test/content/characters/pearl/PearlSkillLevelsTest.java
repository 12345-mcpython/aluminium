package com.laosun.aluminium.test.content.characters.pearl;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Character 1503 (Pearl): the level convention and the two level-granting Eidolons.
 *
 * <p>Reads `CanHit.skillLevel(Skill)` -- the effective level -- not `Skill.getLevel()`, which is the base
 * level and excludes every raise by design.
 */
public class PearlSkillLevelsTest {
    private static final int PEARL = 1503;
    private static final int ALLY = 1204;

    @Test
    public void theConventionAndTheTwoEidolonsMoveTheEffectiveLevel() {
        int[] none = levels(0);
        int[] three = levels(3);
        int[] five = levels(5);
        System.out.println("[pearl_levels] E0 ULTRA=" + none[0] + " COMMON=" + none[1]
                + " | E3 ULTRA=" + three[0] + " COMMON=" + three[1]
                + " | E5 SKILL=" + five[2] + " TALENT=" + five[3]);

        Assertions.assertEquals(10, none[0], "the convention alone brings the Ultimate to 10");
        Assertions.assertEquals(10, none[1], "and the basic too");
        Assertions.assertEquals(12, three[0], "Eidolon 3 adds 2 to the Ultimate");
        // Note: E3's "+1 to the basic" is filed but does NOT take effect, because the basic's table has 10 rows and the
        // level is therefore already at its cap. That is the engine's bound (see CanHit.skillLevel), and it is what
        // keeps the skill working: measured, without the clamp this level read 11, the damage path found no row 11 and
        // returned silently, and Pearl's enhanced basic dealt a flat 0 at every Eidolon from 3 up - with no error.
        Assertions.assertEquals(10, three[1], "and 1 to the basic, which is already at its table's 10-row cap");
        Assertions.assertEquals(12, five[2], "Eidolon 5 adds 2 to the Skill");
        Assertions.assertEquals(12, five[3], "and 2 to the talent");
    }

    /**
     * Effective levels for a Pearl at the given Eidolon rank: ULTRA, COMMON, SKILL, TALENT.
     */
    private static int[] levels(int rank) {
        Battle battle = new Battle(List.of(CharacterFactory.create(PEARL, 80, true, null, null, rank),
                CharacterFactory.create(ALLY, 80)),
                List.of(EnemyFactory.create(1002011, 100, 1)), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character pearl = battle.characters().get(0);
        return new int[]{
                pearl.skillLevel(pearl.getSkills().get(SkillType.ULTRA)),
                pearl.skillLevel(pearl.getSkills().get(SkillType.COMMON)),
                pearl.skillLevel(pearl.getSkills().get(SkillType.SKILL)),
                pearl.skillLevel(pearl.getSkills().get(SkillType.TALENT)),
        };
    }
}
