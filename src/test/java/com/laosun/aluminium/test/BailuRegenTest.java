package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 白露 (1211), from her own file (2026-09-28): 【生息】 and the trace that rides on it.
 *
 * <p><b>What it needed.</b> The round-63 per-target conditions: 「对于<b>没有</b>【生息】的我方目标…附上【生息】，对于<b>已拥有</b>【生息】的我方目标…
 * 延长 1 回合」 is two branches of one sentence over the same selector, and `target_when` is what keeps them apart.
 *
 * <p><b>What is registered</b> (the file's notes): the Skill's random double heal with its decaying multiplier, the
 * talent's 「该效果可以触发 2 次」 (registered whole, because shipping the heal without the count would heal on every hit)
 * and its death prevention, 行迹 持明龙脉, and the eidolons.
 */
public class BailuRegenTest {
    private static final int BAILU = 1211;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** The fresh branch: allies without 【生息】 get it. */
    @Test
    public void theUltimateGivesRegenerationToThoseWithoutIt() {
        Fixture f = new Fixture();

        f.castUltimate();

        Assertions.assertTrue(f.ally.getBuffManager().hasState("生息"), "「对于没有【生息】的我方目标…附上【生息】」");
        Assertions.assertTrue(f.bailu.getBuffManager().hasState("生息"), "…and 「我方全体」 includes her");
    }

    /** ⚠ An ally that already has it is <b>extended</b>, not re-applied — the other branch of the same sentence. */
    @Test
    public void anAllyThatAlreadyHasItIsExtended() {
        Fixture f = new Fixture();
        // ⚠ A hand-built StateBuff carries no `buffName` (only `APPLY_BUFF` sets one), so the precondition counts STATE
        // buffs rather than named ones -- `stacksOf` is about names and would read 0 here.
        f.ally.getBuffManager().addBuff(new com.laosun.aluminium.models.buff.StateBuff("生息", 1));
        int statesBefore = f.ally.getBuffManager().allBuffsOf(com.laosun.aluminium.models.buff.StateBuff.class).size();

        f.castUltimate();

        Assertions.assertTrue(f.ally.getBuffManager().hasState("生息"), "still there (extended, not reset)");
        Assertions.assertEquals(statesBefore,
                f.ally.getBuffManager().allBuffsOf(com.laosun.aluminium.models.buff.StateBuff.class).size(),
                "「该效果不可叠加」: the second branch EXTENDS the state instead of applying another one");
    }

    /** 行迹 鳞渊福泽 rides on the state: the bearer takes 10% less while it lasts. */
    @Test
    public void theTraceReducesDamageTakenForTheBearers() {
        Fixture f = new Fixture();
        // ⚠ Counted rather than "is it non-empty": the reduction zone is also used elsewhere, so an emptiness assertion
        // would have been a claim about the whole battle rather than about this cast.
        int before = f.ally.getBuffManager().allBuffsOf(
                com.laosun.aluminium.models.buff.ReductionBuff.class).size();

        f.castUltimate();

        Assertions.assertTrue(f.ally.getBuffManager().allBuffsOf(
                        com.laosun.aluminium.models.buff.ReductionBuff.class).size() > before,
                "「拥有【生息】的角色受到的伤害降低10%」 -- the reduction goes up with the state");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static final class Fixture {
        private final Character bailu = CharacterFactory.create(BAILU, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        private final Battle battle = new Battle(List.of(bailu, ally), List.of(enemy), fixed());

        private Fixture() {
            battle.startBattle();
        }

        private void castUltimate() {
            battle.castImmediate(bailu.getSkills().get(SkillType.ULTRA), bailu, List.of(ally, bailu));
        }
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }
}
