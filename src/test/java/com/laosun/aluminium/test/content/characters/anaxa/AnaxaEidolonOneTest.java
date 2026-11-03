package com.laosun.aluminium.test.content.characters.anaxa;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1405 夏洛特: Eidolon 1's first half — 「首次施放战技后，恢复 1 个战技点」.
 *
 * <p>The gap had said this needed a 「首次」 counter. It does not: {@code once_per_battle} is exactly that counter, and it is a
 * boolean per rule rather than a quota — which is why it answers 「首次」 and not 「每场 N 次」 (1211's separate gap).
 */
public class AnaxaEidolonOneTest {

    private static final int ANAXA = 1405;

    @Test
    public void theFirstSkillRefundsAPointAndTheSecondDoesNot() {
        Scene scene = new Scene();
        int before = scene.battle.getSkillPoints();

        scene.castSkill();
        int afterFirst = scene.battle.getSkillPoints();
        scene.castSkill();
        int afterSecond = scene.battle.getSkillPoints();

        int firstGain = afterFirst - before;
        int secondGain = afterSecond - afterFirst;
        Assertions.assertEquals(firstGain - 1, secondGain,
                "「首次施放战技后恢复 1 个战技点」 — the second cast pays the Skill's own cost only ("
                        + firstGain + " then " + secondGain + ")");
    }

    private static final class Scene {
        private final Character anaxa;
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;

        private Scene() {
            anaxa = CharacterFactory.create(ANAXA, 80, false, null, null, 1);
            battle = new Battle(List.of(anaxa), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        private void castSkill() {
            battle.castImmediate(anaxa.getSkills().get(SkillType.SKILL), anaxa, List.of(enemy));
            battle.processRequests();
        }
    }
}
