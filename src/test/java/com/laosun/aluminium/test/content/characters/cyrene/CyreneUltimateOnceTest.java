package com.laosun.aluminium.test.content.characters.cyrene;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 「单场战斗中只能施放 1 次」 - 1415's ultimate (141503) is the first row that states it.
 *
 * <p><b>Why a new kind of gate.</b> Every castability the engine had was about the <i>present</i>: energy, skill points,
 * a turn, a counter ({@code castable_when}). This one is about <b>history</b> - the first cast spends the permission and
 * no later one may happen in the same battle. It is recorded by the battle when the cast is <b>committed</b>, so a cast
 * that the other gates refuse does not spend it.
 */
public class CyreneUltimateOnceTest {

    private static final int CYRENE = 1415;
    private static final int ALLY = 1001;

    private record Scene(Battle battle, Character cyrene, Skill ultimate) {
    }

    private static Scene scene() {
        Character cyrene = CharacterFactory.create(CYRENE, 80);
        Battle battle = new Battle(List.of(cyrene), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        return new Scene(battle, cyrene, cyrene.getSkills().get(SkillType.ULTRA));
    }

    /**
     * The first cast happens; the second one is refused.
     */
    @Test
    public void theUltimateIsCastableOnceInABattle() {
        Scene scene = scene();
        Assertions.assertTrue(scene.battle.castImmediate(scene.ultimate, scene.cyrene,
                        List.of(scene.battle.enemies().getFirst())),
                "the first cast is allowed");
        Assertions.assertFalse(scene.battle.castImmediate(scene.ultimate, scene.cyrene,
                        List.of(scene.battle.enemies().getFirst())),
                "「单场战斗中只能施放 1 次」: the second cast must be refused");
    }

    /**
     * A refused cast must NOT spend the single use: the gate answers false before the permission is recorded, so the
     * ultimate is still castable once something else was refused.
     */
    @Test
    public void aRefusedCastDoesNotSpendTheSingleUse() {
        Character cyrene = CharacterFactory.create(CYRENE, 80);
        Battle battle = new Battle(List.of(cyrene), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        Skill ultimate = cyrene.getSkills().get(SkillType.ULTRA);

        // A refused cast never reaches the marking: this one is refused for having no target list at all, which is the
        // same "the gate answered false before anything was spent" path the single-use check sits on.
        Assertions.assertFalse(battle.castImmediate(ultimate, cyrene, null),
                "a cast with no target list is refused");
        Assertions.assertTrue(battle.castImmediate(ultimate, cyrene, List.of(battle.enemies().getFirst())),
                "so the one allowed cast is still available");
    }

    /**
     * A NEW battle starts with the permission again - the record is per battle, not per file.
     */
    @Test
    public void aNewBattleAllowsItAgain() {
        Scene first = scene();
        Assertions.assertTrue(first.battle.castImmediate(first.ultimate, first.cyrene,
                List.of(first.battle.enemies().getFirst())));

        Scene second = scene();
        Assertions.assertTrue(second.battle.castImmediate(second.ultimate, second.cyrene,
                        List.of(second.battle.enemies().getFirst())),
                "another battle's ultimate is not spent by the previous one");
    }
}
