package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * A raised skill level cannot exceed its own parameter table - and the reason is not tidiness.
 *
 * <p>Measured, before the bound existed: 1503 Pearl (真珠) at Eidolon 3+ had COMMON resolved to level 11 against a
 * 10-row table, and her enhanced basic dealt a <b>flat 0</b> - `SkillExecutor`'s damaging path returns silently when
 * the row is out of range, and its generated-effect path answers 0. No error, no log, no failing test. The shape ships
 * on 1103 / 1104 / 1109 / 1207 and more, whose raises also resolve past their tables.
 *
 * <p>So this case pins two things at once: that the effective level stops at the table, and that a skill whose raise
 * would overshoot still DEALS DAMAGE. The second is the one that matters - it is what was silently broken.
 */
public class SkillLevelTableBoundTest {
    private static final int PEARL = 1503;
    private static final int ALLY = 1204;

    /**
     * At an Eidolon whose raise would overshoot, the level is the table size and the skill still hits.
     *
     * <p>Eidolon 3 grants "普攻等级+1" and the file's convention already sits COMMON at 10, so the raise asks for 11.
     */
    @Test
    public void aRaisePastTheTableStopsAtTheTableAndTheSkillStillWorks() {
        Character pearl = CharacterFactory.create(PEARL, 80, false, null, null, 3);
        Battle battle = new Battle(List.of(pearl, CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character her = battle.characters().getFirst();
        var common = her.getSkills().get(SkillType.COMMON);
        int rows = common.getData().getSkills().size();
        int level = her.skillLevel(common);
        System.out.println("[level-bound] COMMON at E3: level=" + level + " table rows=" + rows);

        Assertions.assertEquals(rows, level,
                "the effective level stops at the table: the row count IS the documented cap");
        var victim = battle.enemyUnits().getFirst();
        double before = victim.getCurrentHp();
        battle.castImmediate(common, her, List.of(victim));
        battle.processRequests();
        double dealt = before - victim.getCurrentHp();
        System.out.println("[level-bound] the basic dealt " + dealt);
        Assertions.assertTrue(dealt > 0,
                "and the skill still deals damage - without the bound this level is past the table and the damage"
                        + " path returns silently, i.e. the basic does nothing at all");
    }

    /**
     * The silent-zero half, pinned on its own so the level assertion above cannot shadow it.
     *
     * <p>Without the bound the level is past the table and `SkillExecutor`'s damaging path returns without doing
     * anything - so this reads the damage alone, with no precondition about the level. Measured under the mutation:
     * the basic deals <b>0.0</b>.
     */
    @Test
    public void theBasicStillDealsDamageWhenTheRaiseWouldOvershoot() {
        Character pearl = CharacterFactory.create(PEARL, 80, false, null, null, 3);
        Battle battle = new Battle(List.of(pearl, CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character her = battle.characters().getFirst();
        var victim = battle.enemyUnits().getFirst();
        double before = victim.getCurrentHp();
        battle.castImmediate(her.getSkills().get(SkillType.COMMON), her, List.of(victim));
        battle.processRequests();
        double dealt = before - victim.getCurrentHp();
        System.out.println("[level-bound] damage with a would-overshoot raise = " + dealt);
        Assertions.assertTrue(dealt > 0,
                "a raise that would overshoot must not make the skill deal nothing - that is the silent failure the"
                        + " table bound removes");
    }

    /**
     * The same at E0, where nothing overshoots: the convention's own level is inside the table.
     */
    @Test
    public void theConventionsOwnLevelIsInsideTheTable() {
        Character pearl = CharacterFactory.create(PEARL, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(pearl, CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character her = battle.characters().getFirst();
        var common = her.getSkills().get(SkillType.COMMON);
        int rows = common.getData().getSkills().size();
        System.out.println("[level-bound] COMMON at E0: level=" + her.skillLevel(common) + " table rows=" + rows);
        Assertions.assertTrue(her.skillLevel(common) <= rows,
                "the convention's 10 is within the table (" + rows + " rows)");
        Assertions.assertEquals(rows, her.skillLevel(common),
                "and it is the last row, which is why the Eidolon raise has nowhere to go");
    }
}
