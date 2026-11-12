package com.laosun.aluminium.test.content.characters.hook;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

/**
 * 1109 虎克 clause coverage: every section of {@code 1109_虎克.md}, plus the readings that pin the two Eidolons nobody had written.
 *
 * <p><b>What was added on 2026-11-02</b> (a section-by-section scan found 星魂 1 and 星魂 4 had no rule at all, even though the
 * file's own gap text claimed only three approximations were left):
 * <ul>
 *   <li><b>Eidolon 1</b> — 「战技强化后造成的伤害提高 20%」. The gate is the enhanced form itself: {@code damage_skill_key == 9}
 *       (the loader's slot that {@code REPLACE_SKILL} installs). ⚠ {@code from_skill_id} does <b>not</b> work on
 *       {@code DEALING_DAMAGE}: its payload carries no cast facts — measured, the reading was identical at E0 and E1.</li>
 *   <li><b>Eidolon 4</b> — 「使指定敌方目标的相邻目标也陷入等同于战技的dot:burn状态」. Needed a new selector,
 *       {@code adjacent_enemies}: {@code random_adjacent_enemy} answers only one of the two neighbours.</li>
 * </ul>
 */
public class HookClauseCoverageTest {

    private static final int HOOK = 1109;

    /**
     * Eidolon 1: 「战技强化后造成的伤害提高 20%」 — measured on the enhanced Skill's main target.
     *
     * <p>E0 <b>1430.016034048001</b> → E1 <b>1640.755239065601</b>. Note the ratio is not 1.2: {@code BOOST_DAMAGE} adds into
     * the damage-boost zone rather than multiplying the settled hit (the same shape 1013 and 1412 record), so the honest
     * assertion is the two measured numbers, not a ratio.
     */
    @Test
    public void eidolon1BoostsOnlyTheEnhancedSkill() {
        Assertions.assertEquals(1430.016034048001, enhancedSkillLoss(0), 1e-6, "E0: the enhanced Skill's own row");
        Assertions.assertEquals(1640.755239065601, enhancedSkillLoss(1), 1e-6,
                "E1: 「战技强化后造成的伤害提高 20%」");

        // The other half: a PLAIN Skill (no Ultimate first) must not be boosted -- 「强化后」 says which form it is.
        Assertions.assertEquals(plainSkillLoss(0), plainSkillLoss(1), 1e-6,
                "a plain Skill is not the enhanced form, so Eidolon 1 leaves it alone");
    }

    /**
     * Eidolon 4: 「使指定敌方目标的相邻目标也陷入等同于战技的dot:burn状态」.
     *
     * <p>Measured with three enemies: after the Skill burns the middle one and a basic attack triggers the talent, both
     * neighbours are burning at E4 and neither is at E0.
     */
    @Test
    public void eidolon4BurnsTheNeighbours() {
        Assertions.assertArrayEquals(new boolean[]{false, false},
                neighboursBurning(0), "control: at E0 the neighbours are untouched");
        Assertions.assertArrayEquals(new boolean[]{true, true},
                neighboursBurning(4), "「相邻目标也陷入…dot:burn状态」 — both of them");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * Casts the Ultimate (which installs the enhanced Skill) then the Skill on the first enemy; returns the HP it lost.
     */
    private static double enhancedSkillLoss(int eidolon) {
        Fixture f = new Fixture(eidolon, 1);
        f.battle.castImmediate(f.hook.getSkills().get(SkillType.ULTRA), f.hook, List.of(f.enemies.getFirst()));
        f.battle.processRequests();
        return f.measureSkillHit(0);
    }

    private static double plainSkillLoss(int eidolon) {
        Fixture f = new Fixture(eidolon, 1);
        return f.measureSkillHit(0);
    }

    private static boolean[] neighboursBurning(int eidolon) {
        Fixture f = new Fixture(eidolon, 3);
        f.battle.castImmediate(f.hook.getSkills().get(SkillType.SKILL), f.hook, List.of(f.enemies.get(1)));
        f.battle.processRequests();
        f.battle.castImmediate(f.hook.getSkills().get(SkillType.COMMON), f.hook, List.of(f.enemies.get(1)));
        f.battle.processRequests();
        return new boolean[]{
                f.enemies.get(0).getBuffManager().hasState("dot:burn"),
                f.enemies.get(2).getBuffManager().hasState("dot:burn")};
    }

    private static final class Fixture {
        private final Character hook;
        private final List<Enemy> enemies = new ArrayList<>();
        private final Battle battle;

        private Fixture(int eidolon, int enemyCount) {
            hook = CharacterFactory.create(HOOK, 80, false, null, null, eidolon);
            for (int i = 0; i < enemyCount; i++) {
                enemies.add(BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL));
            }
            battle = new Battle(List.of(hook), List.copyOf(enemies), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        /** Casts the Skill at enemy {@code index} and returns the HP that enemy lost. */
        private double measureSkillHit(int index) {
            Enemy target = enemies.get(index);
            double before = target.getCurrentHp();
            battle.castImmediate(hook.getSkills().get(SkillType.SKILL), hook, List.of(target));
            battle.processRequests();
            return before - target.getCurrentHp();
        }
    }
}
