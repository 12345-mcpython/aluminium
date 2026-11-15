package com.laosun.aluminium.test.content.characters.bailu;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Bailu (白露) (1211): [生息] (Regeneration) and the trace that rides on it.
 *
 * <p><b>What it needed.</b> Per-target conditions: "for our targets that <b>do not have</b> [生息] ... apply [生息], and for our targets that <b>already have</b> [生息] ...
 * extend it by 1 turn" is two branches of one sentence over the same selector, and `target_when` is what keeps them apart.
 *
 * <p><b>What is registered</b> (the file's notes): the Skill's random double heal with its decaying multiplier, the
 * talent's "this effect can trigger 2 times" (registered whole, because shipping the heal without the count would heal on every hit)
 * and its death prevention, trace Vidyadhara Ichor Lines (持明龙脉), and the eidolons.
 *
 * <p><b>L3:</b> the fresh branch of the Ultimate's sentence and the trace that rides on it share one board and one cast,
 * so they are one scenario test ({@code theTraceReducesDamageTakenForTheBearers} - the name {@code 1211.json}'s ultimate
 * note cites for the ordering trap) with an {@code Assertions.assertAll}, each assertion keeping the message that names
 * its clause. The "extend" branch and the 持明龙脉 allowance keep their own boards, because each reads a state the
 * merged board does not have.
 */
public class BailuRegenTest {
    private static final int BAILU = 1211;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    /**
     * The fresh branch: allies without [生息] get it — and the trace 鳞渊福泽 rides on the state, so the bearer takes 10%
     * less while it lasts.
     *
     * <p>One board, one cast: the readings are the same {@code castUltimate()}, one of the state and one of the reduction
     * zone that arrives with it. The name is the one {@code 1211.json} cites for the effect-ordering trap (the reduction
     * must be applied before {@code APPLY_BUFF 生息} in the same rule), so it survives the merge.
     */
    @Test
    public void theTraceReducesDamageTakenForTheBearers() {
        Fixture f = new Fixture();
        // Note: Counted rather than "is it non-empty": the reduction zone is also used elsewhere, so an emptiness assertion
        // would have been a claim about the whole battle rather than about this cast.
        int reductionsBefore = f.ally.getBuffManager().allBuffsOf(
                com.laosun.aluminium.buff.ReductionBuff.class).size();

        f.castUltimate();

        Assertions.assertAll("the Ultimate's [char:1211/lifespan] and the trace that rides on it",
                () -> Assertions.assertTrue(f.ally.getBuffManager().hasState("char:1211/lifespan"), "「for our targets that do not have [char:1211/lifespan] (Invigoration) ... apply [char:1211/lifespan] (Invigoration)」"),
                () -> Assertions.assertTrue(f.bailu.getBuffManager().hasState("char:1211/lifespan"), "...and 「all of our side」 includes her"),
                () -> Assertions.assertTrue(f.ally.getBuffManager().allBuffsOf(
                                com.laosun.aluminium.buff.ReductionBuff.class).size() > reductionsBefore,
                        "「characters that have [char:1211/lifespan] (Invigoration) take 10% less damage」 -- the reduction goes up with the state"));
    }

    /**
     * Note: An ally that already has it is <b>extended</b>, not re-applied - the other branch of the same sentence.
     */
    @Test
    public void anAllyThatAlreadyHasItIsExtended() {
        Fixture f = new Fixture();
        // Note: A hand-built StateBuff carries no `buffName` (only `APPLY_BUFF` sets one), so the precondition counts STATE
        // buffs rather than named ones -- `stacksOf` is about names and would read 0 here.
        f.ally.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff("char:1211/lifespan", 1));
        int statesBefore = f.ally.getBuffManager().allBuffsOf(com.laosun.aluminium.buff.StateBuff.class).size();

        f.castUltimate();

        Assertions.assertTrue(f.ally.getBuffManager().hasState("char:1211/lifespan"), "still there (extended, not reset)");
        Assertions.assertEquals(statesBefore,
                f.ally.getBuffManager().allBuffsOf(com.laosun.aluminium.buff.StateBuff.class).size(),
                "「this effect cannot stack」: the second branch EXTENDS the state instead of applying another one");
    }

    /**
     * trace Vidyadhara Ichor Lines (持明龙脉): 「【生息】效果的触发次数增加 1 次」 - the talent's 2 plus this one is 3.
     *
     * <p>Read off the allowance counter rather than by hitting the ally four times: the counter IS the trigger count the
     * sentence raises, and a hit-based test would also depend on the heal landing on a wounded ally.
     */
    @Test
    public void theDragonVeinAddsOneInvigorationTrigger() {
        Fixture f = new Fixture();
        Assertions.assertEquals(3, f.bailu.getBuffManager().stacksOf("internal:1211/lifespan_heal_allowance"),
                "天赋 2 次（TALENT:4）+ 行迹 1 次 = 3");
    }

    // ==================================================================
    // Helpers
    // ==================================================================
    private static final class Fixture {
        private final Character bailu = CharacterFactory.create(BAILU, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle = new Battle(List.of(bailu, ally), List.of(enemy), fixed());

        private Fixture() {
            battle.startBattle();
        }

        private void castUltimate() {
            battle.castImmediate(bailu.getSkills().get(SkillType.ULTRA), bailu, List.of(ally, bailu));
        }
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }
}
