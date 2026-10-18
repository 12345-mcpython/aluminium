package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1409 风堇's ultimate 【雨过天晴】 with her eidolons: the Max-HP share is read from the <b>current</b> row, Rank 3 raises the
 * ultimate by two levels, and Rank 2 adds another +50% while the state is up.
 *
 * <h2>Why the numbers are these</h2>
 * The document (1409_风堇.md) says Rank 3: 「终结技等级+2」 and Rank 2: 「风堇处于【雨过天晴】状态时，我方全体目标生命上限
 * 额外提高 50%」. So a rank-6 风堇 casts the ultimate at Lv12, whose row is {@code [0.1, 200, 0.33, 667.5, 3, 0.12, 240]}
 * - #3 = 0.33 (the share). The capture's screenshot shows exactly 「83% + 667」 = 0.33 + 0.50 and 667.5 rounded, which is
 * what this judge pins.
 *
 * <p>The base HP is the engine's own panel for 1409 at Lv80 ({@code 1195.2864}); a percentage modifier raises the value
 * by {@code base x percent} (the {@code DoubleValue} rule). The flat term is still the literal 600, because
 * {@code MODIFY_ATTR} has no "flat from the current skill row" spelling yet (registered in the file's note).
 */
public class HyacineRainClearMaxHpTest {

    private static final int HYACINE = 1409;
    private static final int ALLY = 1001;
    private static final double BASE_HP = 1086.624;

    @Test
    public void theMaxHpClimbIsTheRowsSharePlusRankTwoPlusTheFlat() {
        double rank0 = climb(0);
        double rank6 = climb(6);
        Assertions.assertEquals(BASE_HP * 0.30 + 600, rank0, 1e-6,
                "E0: Lv10 row #3 = 0.30 and the literal flat 600");
        Assertions.assertEquals(BASE_HP * (0.33 + 0.50) + 667.5, rank6, 1e-6,
                "E6: Lv12 row #3 = 0.33, plus Rank 2's 0.50, plus the Lv12 flat 667.5");
    }

    private static double climb(int rank) {
        Character hyacine = CharacterFactory.create(HYACINE, 80, true, null, null, rank);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        int expectedLevel = rank >= 3 ? 12 : 10;
        Assertions.assertEquals(expectedLevel, hyacine.skillLevel(hyacine.getSkills().get(SkillType.ULTRA)),
                "Rank 3 raises the ultimate by 2 (from the content's Lv10)");
        double before = hyacine.getAttribute(AttributeType.HEALTH).get();
        SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.ULTRA), hyacine,
                List.of(battle.enemies.getFirst()));
        battle.processRequests();
        double after = hyacine.getAttribute(AttributeType.HEALTH).get();
        return after - before;
    }
}
