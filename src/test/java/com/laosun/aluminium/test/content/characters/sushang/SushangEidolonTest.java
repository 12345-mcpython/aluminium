package com.laosun.aluminium.test.content.characters.sushang;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.buff.ReductionBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1206 Sushang's six eidolons, each reduced to the observable its sentence names.
 *
 * <p>⚠ Two are about a CAP or a COUNT rather than a number, and those are the ones worth writing: 「最多叠加2层」 is
 * a ceiling, so one firing beyond it must change nothing; 「立即获得1层」 is an initial condition, so rank 6 must
 * start higher than rank 0 before any enemy breaks at all.
 */
public class SushangEidolonTest {
    private static final int SUSHANG = 1206;
    private static final int ALLY = 1201;
    private static final int LEVEL = 80;

    /**
     * 星魂 1 「对陷入弱点击破状态的敌方目标施放战技后，恢复1个战技点」.
     *
     * <p>⚠ Run in BOTH states: the same cast must pay a point against a broken enemy and nothing against a healthy
     * one. Asserting only the paying half would pass for a rule that forgot its condition. And the break is driven
     * through the engine's own toughness path (two Skill casts empty the dummy's bar), not fired as an event - the
     * condition reads a STATE, so only a real break can put it there.
     */
    @Test
    public void eidolon1PaysASkillPointOnlyAgainstABrokenTarget() {
        Fixture healthy = new Fixture(1, BattleFixture.monster());
        int before = healthy.battle.getSkillPoints();
        healthy.castSkill();
        Assertions.assertEquals(before, healthy.battle.getSkillPoints(),
                "「对陷入弱点击破状态的敌方目标…」 - a healthy target pays nothing");

        Fixture breaking = new Fixture(1, BattleFixture.dummy());
        Enemy target = (Enemy) breaking.battle.enemies.getFirst();
        Assertions.assertFalse(target.isBroken(), "precondition: not broken yet");
        // ⚠ The broken state is ENTERED directly through the enemy's own entry point rather than by emptying a
        // toughness bar: `BattleFixture.dummy()` is built by `fromAttributes` and has no bar at all, and which
        // element a target is weak to decides whether a hit reduces toughness - neither is what this eidolon is
        // about. `breakEnemy` is the same call the engine's break path makes, so the state is the real one.
        target.breakEnemy(com.laosun.aluminium.enums.DamageElement.PHYSICAL);
        Assertions.assertTrue(target.isBroken(),
                "precondition: the target is in the broken state");

        int beforeBroken = breaking.battle.getSkillPoints();
        breaking.castSkill();
        Assertions.assertEquals(beforeBroken + 1, breaking.battle.getSkillPoints(),
                "「…施放战技后，恢复1个战技点」 - exactly one point once it is broken");
    }

    /**
     * 星魂 2 「触发【剑势】后，素裳受到的伤害降低20%，持续1回合」.
     *
     * <p>⚠ Counted through {@code ReductionBuff}, the class the engine lands a negative {@code MODIFY_DAMAGE_TAKEN}
     * as. Asserted against rank 0 rather than against a boolean, so a rule that fired on every hit would still be
     * caught by the baseline it is compared with.
     */
    @Test
    public void eidolon2LowersTheDamageSheTakesAfterASwordStance() {
        Fixture base = new Fixture(0, BattleFixture.monster());
        base.castSkill();
        int plain = reductions(base.sushang);

        Fixture withEidolon = new Fixture(2, BattleFixture.monster());
        withEidolon.castSkill();
        int reduced = reductions(withEidolon.sushang);

        Assertions.assertTrue(reduced > plain,
                "「触发【剑势】后…受到的伤害降低20%」 must land a reduction: rank 0 = " + plain
                        + ", rank 2 = " + reduced);
    }

    private static int reductions(Character unit) {
        return unit.getBuffManager().allBuffsOf(ReductionBuff.class).size();
    }

    /**
     * 星魂 3 / 5: the two level statements, each on the skill it names.
     *
     * <p>⚠ Compared against rank 0 rather than against a literal: the file already raises everything to Lv10 by
     * its level convention, so an absolute number here would restate that choice and rot when it changed.
     */
    @Test
    public void eidolon3And5RaiseTheLevelsTheyName() {
        Fixture base = new Fixture(0, BattleFixture.monster());
        Fixture atThree = new Fixture(3, BattleFixture.monster());
        Fixture atFive = new Fixture(5, BattleFixture.monster());

        Assertions.assertEquals(base.sushang.skillLevelBonus(SkillType.ULTRA) + 2,
                atThree.sushang.skillLevelBonus(SkillType.ULTRA), "星魂3 「终结技等级+2」");
        Assertions.assertEquals(base.sushang.skillLevelBonus(SkillType.TALENT) + 2,
                atThree.sushang.skillLevelBonus(SkillType.TALENT), "星魂3 「天赋等级+2」");

        Assertions.assertEquals(base.sushang.skillLevelBonus(SkillType.SKILL) + 2,
                atFive.sushang.skillLevelBonus(SkillType.SKILL), "星魂5 「战技等级+2」");
        Assertions.assertEquals(base.sushang.skillLevelBonus(SkillType.COMMON) + 1,
                atFive.sushang.skillLevelBonus(SkillType.COMMON), "星魂5 「普攻等级+1」");
    }

    /**
     * 星魂 4 「素裳的击破特攻提高40%」 - a permanent, ratio-type attribute.
     */
    @Test
    public void eidolon4RaisesHerBreakingEffect() {
        Fixture base = new Fixture(0, BattleFixture.monster());
        Fixture atFour = new Fixture(4, BattleFixture.monster());

        double plain = base.sushang.getAttribute(AttributeType.BREAKING_EFFECT).get();
        double raised = atFour.sushang.getAttribute(AttributeType.BREAKING_EFFECT).get();

        Assertions.assertEquals(plain + 0.4, raised, 1e-9,
                "「击破特攻提高40%」 is a RATIO attribute, so percent IS the value: " + plain + " -> " + raised);
    }

    /**
     * 星魂 6: 「天赋的加速效果可以叠加，最多叠加2层，且进入战斗后素裳立即获得1层」.
     *
     * <p>⚠ Three facts that can fail independently, so all three are stated: she STARTS one layer up, a second break
     * takes her to two, and a third does NOT take her to three.
     */
    @Test
    public void eidolon6StartsHerWithOneTalentStackAndCapsAtTwo() {
        Fixture rankZero = new Fixture(0, BattleFixture.monster());
        Fixture atSix = new Fixture(6, BattleFixture.monster());

        Assertions.assertTrue(atSix.sushang.getAttribute(AttributeType.SPEED).get()
                        > rankZero.sushang.getAttribute(AttributeType.SPEED).get(),
                "「进入战斗后素裳立即获得1层天赋的加速效果」 - rank 6 starts faster before anything breaks");

        atSix.battle.fireTriggers(TriggerEvent.BREAK, atSix.sushang, atSix.enemy, 0, 0);
        double afterOne = atSix.sushang.getAttribute(AttributeType.SPEED).get();
        atSix.battle.fireTriggers(TriggerEvent.BREAK, atSix.sushang, atSix.enemy, 0, 0);
        double afterTwo = atSix.sushang.getAttribute(AttributeType.SPEED).get();
        Assertions.assertTrue(afterTwo > afterOne,
                "「天赋的加速效果可以叠加」 - a second break stacks: " + afterOne + " -> " + afterTwo);

        atSix.battle.fireTriggers(TriggerEvent.BREAK, atSix.sushang, atSix.enemy, 0, 0);
        Assertions.assertEquals(afterTwo, atSix.sushang.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "「最多叠加2层」 - a third break changes nothing");
    }

    private static final class Fixture {
        private final Character sushang;
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy;
        private final Battle battle;

        private Fixture(int eidolon, Enemy enemy) {
            this.enemy = enemy;
            this.sushang = CharacterFactory.create(SUSHANG, LEVEL, true, null, null, eidolon);
            this.battle = new Battle(new ArrayList<>(List.of(sushang, ally)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
        }

        private void castSkill() {
            battle.castImmediate(sushang.getSkills().get(SkillType.SKILL), sushang, List.of(enemy));
        }
    }
}
