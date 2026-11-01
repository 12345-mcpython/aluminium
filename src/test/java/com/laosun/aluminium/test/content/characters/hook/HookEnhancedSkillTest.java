package com.laosun.aluminium.test.content.characters.hook;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1109 虎克's ultimate: 「施放终结技后，下一次施放的战技得到强化，强化后的战技能够同时对指定敌方单体及其相邻目标造成伤害」.
 *
 * <p>The enhancement is a {@code REPLACE_SKILL} swap: the SKILL slot is pointed at data row 110909, whose
 * {@code skill_effect} is <b>Blast</b> - so the adjacent targets are the data row's own shape, not a rule. The swap lives
 * `until: next_skill`, which is the sentence's "next Skill": a basic attack in between must NOT consume it.
 */
public class HookEnhancedSkillTest {

    private static final int LEVEL = 80;
    private static final int HOOK = 1109;

    /**
     * The normal Skill hits one enemy, the enhanced one hits the neighbour too, and the swap ends with that cast.
     */
    @Test
    public void theEnhancedSkillHitsTheNeighbourAndThenReverts() {
        Fixture f = new Fixture();
        f.castUltimate();
        // A basic attack between the ultimate and the Skill must not consume the swap.
        f.cast(SkillType.COMMON, f.main);
        f.castSkillAndMeasure(f.main);
        Assertions.assertTrue(f.neighbourLost(), "after the ultimate the Skill is Blast: 「及其相邻目标」");
        Assertions.assertTrue(f.mainLost(), "and the aimed enemy is still hit");

        f.reset();
        f.castSkillAndMeasure(f.main);
        Assertions.assertTrue(f.mainLost(), "the reverted Skill still hits its target");
        Assertions.assertFalse(f.neighbourLost(), "the swap ended with the first Skill - no second Blast");
    }

    /**
     * Without the ultimate the Skill is the ordinary single-target row.
     */
    @Test
    public void theOrdinarySkillHitsOneEnemy() {
        Fixture f = new Fixture();
        f.castSkillAndMeasure(f.main);
        Assertions.assertTrue(f.mainLost());
        Assertions.assertFalse(f.neighbourLost(), "the ordinary row is SingleAttack");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character hook;
        private final Enemy main = BattleFixture.monster();
        private final Enemy neighbour = BattleFixture.monster();
        private final Battle battle;
        private double mainBefore;
        private double neighbourBefore;

        private Fixture() {
            hook = CharacterFactory.create(HOOK, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(hook)),
                    new ArrayList<>(List.of(main, neighbour)), BattleFixture.seeded());
            battle.startBattle();
        }

        private void castUltimate() {
            battle.castImmediate(hook.getSkills().get(SkillType.ULTRA), hook, List.of(main));
        }

        private void cast(SkillType slot, Enemy target) {
            battle.castImmediate(hook.getSkills().get(slot), hook, List.of(target));
        }

        private void reset() {
            mainBefore = main.getCurrentHp();
            neighbourBefore = neighbour.getCurrentHp();
        }

        private void castSkillAndMeasure(Enemy target) {
            reset();
            cast(SkillType.SKILL, target);
        }

        private boolean mainLost() {
            return main.getCurrentHp() < mainBefore;
        }

        private boolean neighbourLost() {
            return neighbour.getCurrentHp() < neighbourBefore;
        }
    }
}

