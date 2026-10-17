package com.laosun.aluminium.test.content.characters.danheng;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1206… rather 1002 Dan Heng: the Skill's crit-slow (and eidolon 6 deepening it), trace 绝影, and eidolons 3/4/5.
 *
 * <p>⚠ The crit is driven, not simulated: `BattleFixture.alwaysRoll(0.0)` makes every crit roll pass, and the
 * engine reports the crit through `CRIT_DEALT` - the same event the rule hangs on. So each case below is a real
 * cast, and the observable is the enemy's SPEED (or her own), not an internal count.
 */
public class DanHengKitCompletionTest {
    private static final int DANHENG = 1002;
    private static final int ALLY = 1201;
    private static final int LEVEL = 80;
    /**
     * A roll between his base crit rate (0.05 from character_data.json) and base + 12% (0.17), so the SAME
     * roll crits only when the eidolon is active. Measured, not guessed - the value is the whole experiment.
     */
    private static final double ROLL_BETWEEN = 0.10;

    /**
     * ⭐ 「战技造成的伤害触发暴击时…速度降低12%」: a critting Skill slows the target.
     *
     * <p>⚠ And the SCOPING is asserted in the same case: a critting BASIC ATTACK must not slow, because the
     * sentence says 「战技」. Without the control, a rule that fired on any crit would pass.
     */
    @Test
    public void onlyASkillCritSlowsTheTarget() {
        Fixture skill = new Fixture(0);
        double before = skill.enemy.getAttribute(AttributeType.SPEED).get();
        skill.cast(SkillType.SKILL);
        double slowed = skill.enemy.getAttribute(AttributeType.SPEED).get();
        Assertions.assertTrue(slowed < before,
                "「战技造成的伤害触发暴击时…速度降低12%」: " + before + " -> " + slowed);

        Fixture basic = new Fixture(0);
        double basicBefore = basic.enemy.getAttribute(AttributeType.SPEED).get();
        basic.cast(SkillType.COMMON);
        Assertions.assertEquals(basicBefore, basic.enemy.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "「战技」 - a critting basic attack is not that sentence");
    }

    /**
     * 星魂 6 「战技触发的减速状态使目标的速度额外降低8%」: at rank 6 the slow is 12% + 8% = 20%.
     */
    @Test
    public void eidolon6DeepensTheSlowToTwentyPercent() {
        double atZero = slowRatio(new Fixture(0));
        double atSix = slowRatio(new Fixture(6));

        Assertions.assertEquals(0.12, atZero, 1e-9, "the base slow is 12%");
        Assertions.assertEquals(0.2, atSix, 1e-9,
                "「额外降低8%」 makes it 20% in total, not a second 8% modifier (" + atZero + " -> " + atSix + ")");
    }

    /**
     * 星魂 3 / 5: the level statements, each on the slots it names.
     */
    @Test
    public void eidolons3And5RaiseTheLevelsTheyName() {
        Fixture base = new Fixture(0);
        Fixture atThree = new Fixture(3);
        Fixture atFive = new Fixture(5);

        Assertions.assertEquals(base.danheng.skillLevelBonus(SkillType.SKILL) + 2,
                atThree.danheng.skillLevelBonus(SkillType.SKILL), "星魂3 「战技等级+2」");
        Assertions.assertEquals(base.danheng.skillLevelBonus(SkillType.COMMON) + 1,
                atThree.danheng.skillLevelBonus(SkillType.COMMON), "星魂3 「普攻等级+1」");
        Assertions.assertEquals(base.danheng.skillLevelBonus(SkillType.ULTRA) + 2,
                atFive.danheng.skillLevelBonus(SkillType.ULTRA), "星魂5 「终结技等级+2」");
        Assertions.assertEquals(base.danheng.skillLevelBonus(SkillType.TALENT) + 2,
                atFive.danheng.skillLevelBonus(SkillType.TALENT), "星魂5 「天赋等级+2」");
    }

    /**
     * 星魂 4 「施放终结技消灭敌方目标时，丹恒立即行动」 - only from the ULTIMATE, and only at rank 4.
     *
     * <p>⚠ Three cases, because two of them can pass for the wrong reason. Without the rank-0 case, dropping
     * `min_eidolon` from the rule leaves every assertion green - measured: that mutation survived until this
     * case was added, since both remaining checks were run at rank 4 where the gate cannot be observed.
     */
    @Test
    public void eidolon4GrantsAnExtraTurnOnlyForAnUltimateKillAtRankFour() {
        Fixture ultimate = new Fixture(4);
        Assertions.assertNull(ultimate.battle.getExtraTurnActor(), "precondition: nothing scheduled");
        ultimate.killWith(SkillCategory.ULTRA);
        Assertions.assertSame(ultimate.danheng, ultimate.battle.getExtraTurnActor(),
                "「施放终结技消灭敌方目标时，丹恒立即行动」");

        Fixture basic = new Fixture(4);
        basic.killWith(SkillCategory.NORMAL);
        Assertions.assertNull(basic.battle.getExtraTurnActor(),
                "「施放终结技」 - a kill from another slot is not that sentence");

        Fixture noEidolon = new Fixture(0);
        noEidolon.killWith(SkillCategory.ULTRA);
        Assertions.assertNull(noEidolon.battle.getExtraTurnActor(),
                "at rank 0 the eidolon is not active, so even an ultimate kill pays nothing");
    }

    /**
     * 行迹 绝影 「施放攻击后有50%的固定概率使自身速度提高20%」 - pinned to a passing roll, so this is about the
     * effect and not about luck.
     */
    @Test
    public void traceFleetingShadowRaisesHerOwnSpeed() {
        Fixture f = new Fixture(0);
        double before = f.danheng.getAttribute(AttributeType.SPEED).get();

        f.battle.fireTriggers(com.laosun.aluminium.enums.TriggerEvent.ALLY_ATTACK,
                f.danheng, f.enemy, 0, 0);

        Assertions.assertTrue(f.danheng.getAttribute(AttributeType.SPEED).get() > before,
                "「使自身速度提高20%」: " + before + " -> " + f.danheng.getAttribute(AttributeType.SPEED).get());
    }

    /**
     * 星魂 1 「当击中的敌方目标当前生命值百分比大于等于50%时，暴击率提高12%」.
     *
     * <p>⚠ How this is made observable: the dice are pinned to a value that sits BETWEEN his own crit rate and
     * that rate + 12%, so the same hit crits only when the eidolon is active. The observable is the settled
     * damage (a crit multiplies it), read through the engine's own observer - not the modifier's presence, which
     * would not say whether the crit roll ever saw it.
     *
     * <p>⚠ And the condition is asserted: the same comparison against a target below half HP must show NO
     * difference, or the rule would be "always +12%".
     *
     * <p>⚠ What this case does NOT distinguish, measured: `instance: true` vs a plain stat modifier. Both raise
     * the crit chance the roll reads, so dropping `instance` from the rule leaves this case green (mutation
     * tested). The difference is lifetime - a stat modifier would linger on Dan Heng, an instance one dies with
     * the hit - and `until: next_attack` ends a stat modifier on the very attack it was applied for, so no
     * post-hit assertion can separate them either. The content states `instance` because the sentence is about
     * "the enemy target that was hit", not about Dan Heng for a while; the rule's own note records that choice.
     */
    @Test
    public void eidolon1RaisesCritChanceOnlyAgainstAHealthyTarget() {
        double healthyNo = settled(Skin.NORMAL, ROLL_BETWEEN);
        double healthyYes = settled(Skin.NORMAL, ROLL_BETWEEN, 1);
        Assertions.assertTrue(healthyYes > healthyNo,
                "「暴击率提高12%」 must turn this roll into a crit: " + healthyNo + " -> " + healthyYes);

        double lowNo = settled(Skin.LOW, ROLL_BETWEEN);
        double lowYes = settled(Skin.LOW, ROLL_BETWEEN, 1);
        Assertions.assertEquals(lowNo, lowYes, 1e-9,
                "「当前生命值百分比大于等于50%」 - below half HP the eidolon adds nothing: "
                        + lowNo + " vs " + lowYes);
    }

    private enum Skin { NORMAL, LOW }

    /** Settles one WIND hit of the given category against a full or half-dead enemy, and returns the amount. */
    private static double settled(Skin skin, double roll) {
        return settled(skin, roll, 0);
    }

    private static double settled(Skin skin, double roll, int eidolon) {
        Fixture f = new Fixture(eidolon, roll);
        if (skin == Skin.LOW) {
            f.enemy.takeDamage(f.enemy.getCurrentHp() * 0.6);
        }
        f.battle.applyDamage(f.enemy, new Damage(f.danheng, f.enemy, DamageElement.WIND,
                DamageType.NORMAL, 1_000, SkillCategory.BPSKILL));
        return f.lastSettled;
    }

    /** The fraction by which one Skill crit lowers the target's SPEED. */
    private static double slowRatio(Fixture f) {
        double before = f.enemy.getAttribute(AttributeType.SPEED).get();
        f.cast(SkillType.SKILL);
        return 1.0 - f.enemy.getAttribute(AttributeType.SPEED).get() / before;
    }

    private static final class Fixture {
        private final Character danheng;
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;
        private double lastSettled;

        private Fixture(int eidolon) {
            this(eidolon, 0.0);
        }

        private Fixture(int eidolon, double roll) {
            danheng = CharacterFactory.create(DANHENG, LEVEL, true, null, null, eidolon);
            battle = new Battle(new ArrayList<>(List.of(danheng, ally)),
                    new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(roll));
            battle.addDamageObserver(new Battle.DamageObserver() {
                @Override
                public void onDamageSettled(Damage damage, double applied, double hpLoss, double shieldAbsorbed,
                                            boolean died) {
                    lastSettled = applied;
                }
            });
            battle.startBattle();
        }

        private void cast(SkillType slot) {
            battle.castImmediate(danheng.getSkills().get(slot), danheng, List.of(enemy));
        }

        /** A killing blow of the given cast category, so the KILL event carries that slot. */
        private void killWith(SkillCategory category) {
            enemy.takeDamage(enemy.getCurrentHp() - 1);
            battle.applyDamage(enemy, new Damage(danheng, enemy, DamageElement.WIND, DamageType.NORMAL,
                    1_000, category));
            Assertions.assertTrue(enemy.isDeath(), "precondition: the blow killed it");
        }
    }
}
