package com.laosun.aluminium.test.content.characters.hook;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1109 虎克's Eidolon 2 and 6, the two clauses its file already states.
 *
 * <p>E2 「战技使敌方目标陷入的灼烧状态持续时间增加 1 回合」 is a {@code MODIFY_RULE} on the burn rule, so the check is the
 * burn's own duration (2 → 3). E6 「对灼烧状态下的敌方目标造成的伤害提高 20%」 is a {@code BOOST_DAMAGE} on the settling
 * instance, so the check is the damage ratio against a burning and a healthy enemy with otherwise identical stats.
 */
public class HookEidolonTest {

    private static final int LEVEL = 80;
    private static final int HOOK = 1109;

    /**
     * Eidolon 2: the Skill's burn lasts one turn longer.
     */
    @Test
    public void eidolonTwoLengthensTheBurn() {
        Assertions.assertEquals(2, burnTurns(0), "the base row states 2 turns");
        Assertions.assertEquals(3, burnTurns(2), "「持续时间增加 1 回合」");
    }

    /**
     * Eidolon 6: the same hit is 20% stronger against a burning target - compared across EIDOLON RANKS, not across
     * enemies. A burning enemy also draws the talent's extra instance (「对灼烧目标追加 100% 攻击力」), so the two enemies'
     * totals differ by 1.71 for a reason that has nothing to do with E6; the rank-to-rank ratio cancels it.
     */
    @Test
    public void eidolonSixBoostsDamageAgainstBurningTargets() {
        Assertions.assertEquals(1.0, rankRatio(false), 1e-6, "a healthy target: rank 6 changes nothing");
        Assertions.assertEquals(1.2, rankRatio(true), 1e-6, "「对灼烧状态下的敌方目标造成的伤害提高 20%」");
    }

    private static double rankRatio(boolean burning) {
        // Rank 6 against rank 5, not against rank 0: her other Eidolons also move the numbers, and this is the
        // clause under test.
        return damage(6, burning) / damage(5, burning);
    }

    private static double damage(int eidolon, boolean burning) {
        Character hook = CharacterFactory.create(HOOK, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(hook, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.99));
        battle.startBattle();
        if (burning) {
            enemy.getBuffManager().addBuff(new DotBuff(hook, DamageElement.FIRE, 10, 3, 0, "灼烧"));
        }
        double before = enemy.getCurrentHp();
        battle.castImmediate(hook.getSkills().get(SkillType.COMMON), hook, List.of(enemy));
        return before - enemy.getCurrentHp();
    }
    // ==================================================================

    private static int burnTurns(int eidolon) {
        Character hook = CharacterFactory.create(HOOK, LEVEL, true, null, null, eidolon);
        // A monster with NO Fire weakness: a Fire break would attach its own burn (3 turns) and blur the count.
        Enemy enemy = BattleFixture.enemy(100201602, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(hook, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
        battle.startBattle();
        battle.castImmediate(hook.getSkills().get(SkillType.SKILL), hook, List.of(enemy));
        List<DotBuff> dots = enemy.getBuffManager().activeBuffs().stream()
                .filter(DotBuff.class::isInstance).map(DotBuff.class::cast)
                .filter(dot -> dot.getElement() == DamageElement.FIRE).toList();
        Assertions.assertEquals(1, dots.size(), "the Skill lands exactly one burn (no Fire break on this target)");
        return dots.getFirst().remainingTurns();
    }
}


