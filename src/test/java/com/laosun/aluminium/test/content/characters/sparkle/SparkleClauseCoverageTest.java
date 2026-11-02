package com.laosun.aluminium.test.content.characters.sparkle;

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
 * 1306 花火: Eidolon 4 — 「终结技可以额外恢复 1 点战技点，天赋的战技点上限增加效果额外增加 1 点」.
 *
 * <p>Two halves, and the second is the one that is easy to miss: the talent raises the party's skill-point <b>cap</b> by 2, and this
 * Eidolon raises <b>that effect</b> by 1 — not "grants one more point". Measured as a cap, because a point grant would be spent.
 */
public class SparkleClauseCoverageTest {

    private static final int SPARKLE = 1306;

    /**
     * The cap half: 2 (talent) + 1 (Eidolon 4) over the engine's base.
     */
    @Test
    public void eidolon4RaisesTheSkillPointCapByOneMore() {
        Fixture e4 = new Fixture(4);
        int baseCap = new Fixture(0).battle.getSkillPointMax();
        e4.castUltimate();
        Assertions.assertEquals(baseCap + 1, e4.battle.getSkillPointMax(),
                "「天赋的战技点上限增加效果额外增加 1 点」 — one MORE on top of the talent's own raise");
    }

    /**
     * The point half: the Ultimate pays its own 4 plus this Eidolon's 1 (clamped at the cap).
     */
    @Test
    public void eidolon4PaysOneMorePointWithTheUltimate() {
        Fixture e4 = new Fixture(4);
        int before = e4.battle.getSkillPoints();
        e4.castUltimate();
        Assertions.assertEquals(Math.min(e4.battle.getSkillPointMax(), before + 5), e4.battle.getSkillPoints(),
                "「终结技可以额外恢复 1 点战技点」 — the Ultimate's own 4 plus this Eidolon's 1");

        Fixture e0 = new Fixture(0);
        int beforeE0 = e0.battle.getSkillPoints();
        e0.castUltimate();
        Assertions.assertEquals(Math.min(e0.battle.getSkillPointMax(), beforeE0 + 4), e0.battle.getSkillPoints(),
                "control: at E0 the Ultimate pays 4");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character sparkle;
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;

        private Fixture(int eidolon) {
            sparkle = CharacterFactory.create(SPARKLE, 80, false, null, null, eidolon);
            battle = new Battle(List.of(sparkle), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        private void castUltimate() {
            battle.castImmediate(sparkle.getSkills().get(SkillType.ULTRA), sparkle, List.of(sparkle));
            battle.processRequests();
        }
    }
}



