package com.laosun.aluminium.test.content.characters.seele;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1102 希儿: the Skill's speed buff and the Eidolon that lets it stack.
 *
 * <p><b>What was missing.</b> 「使希儿的速度提高 25%，持续 2 回合」 had no rule at all — and the gap that mentioned it said
 * 「把现有的速度 buff 改成可叠」, presupposing a buff that did not exist. So the base clause was absent and the Eidolon had
 * nothing to raise; both are here now.
 */
public class SeeleClauseCoverageTest {

    private static final int SEELE = 1102;

    /**
     * 战技: 「速度提高 25%，持续 2 回合」 — one layer, whether she casts once or twice.
     */
    @Test
    public void theSkillRaisesHerSpeedByOneLayer() {
        Fixture f = new Fixture(0);
        double base = f.seele.getAttribute(AttributeType.SPEED).get();
        f.castSkill();
        double afterOne = f.seele.getAttribute(AttributeType.SPEED).get();
        Assertions.assertTrue(afterOne > base, "「使希儿的速度提高 25%」 (" + base + " -> " + afterOne + ")");
        f.castSkill();
        Assertions.assertEquals(afterOne, f.seele.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "at E0 the buff does not stack");
    }

    /**
     * 星魂 2: 「战技的加速效果可以叠加，最多叠加 2 层」 — the second cast adds a second layer.
     */
    @Test
    public void eidolon2LetsTheBuffStackTwice() {
        Fixture f = new Fixture(2);
        double base = f.seele.getAttribute(AttributeType.SPEED).get();
        f.castSkill();
        double afterOne = f.seele.getAttribute(AttributeType.SPEED).get();
        f.castSkill();
        double afterTwo = f.seele.getAttribute(AttributeType.SPEED).get();

        Assertions.assertTrue(afterTwo > afterOne, "「可以叠加，最多叠加 2 层」 (" + afterOne + " -> " + afterTwo + ")");
        Assertions.assertEquals(base * 1.5, afterTwo, 1e-6, "two layers of 25% on the base speed");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character seele;
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;

        private Fixture(int eidolon) {
            seele = CharacterFactory.create(SEELE, 80, false, null, null, eidolon);
            battle = new Battle(List.of(seele), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        private void castSkill() {
            battle.castImmediate(seele.getSkills().get(SkillType.SKILL), seele, List.of(enemy));
            battle.processRequests();
        }
    }
}
