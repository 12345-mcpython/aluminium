package com.laosun.aluminium.test.content.characters.archer;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
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
 * 1015 Archer (Archer): the talent's follow-up, the technique, and the three traces - the parts of his kit that are not the
 * 【回路连接】loop (that one is {@link ArcherLoopTest}).
 *
 * <p>⚠ Each case is written as a <b>contrast</b>, because every one of these clauses has a "does nothing" twin that a
 * one-sided assertion cannot see: the follow-up needs Charge AND a <em>teammate</em>; the SP-cap trace moves a number that
 * is 5 by default; the crit-damage trace needs the pool at 4 (its cap, in the same fixture, is 7).
 */
public class ArcherKitTest {
    private static final int ARCHER = 1015;
    private static final int ALLY = 1204;
    private static final String CHARGE = "充能";

    /**
     * 「当Archer的队友对敌方目标施放攻击后，Archer消耗1点充能…恢复1个战技点」.
     */
    @Test
    public void aTeammatesAttackSpendsOneChargeForAFollowUpAndASkillPoint() {
        Fixture f = new Fixture(1);
        // Note: the battle already opens with 1 Charge - that is trace 1015102, pinned by its own case below.
        Assertions.assertEquals(1, f.archer.getResources().value(CHARGE), "precondition: 行迹给了 1 点开场充能");
        int skillsPointsBefore = f.battle.getSkillPoints();

        int followUps = f.allyAttacks();

        System.out.println("[1015] 队友攻击后：追击 " + followUps + " 次 ; 充能 " + f.archer.getResources().value(CHARGE)
                + " ; 战技点 " + skillsPointsBefore + " -> " + f.battle.getSkillPoints());
        Assertions.assertEquals(1, followUps, "一次队友攻击 ⇒ 一次追加攻击");
        Assertions.assertEquals(0, f.archer.getResources().value(CHARGE), "消耗 1 点充能");
        Assertions.assertEquals(skillsPointsBefore + 1, f.battle.getSkillPoints(), "并恢复 1 个战技点");
    }

    /**
     * Without Charge it does nothing - and, in particular, does not "spend" a point that is not there.
     */
    @Test
    public void withoutChargeThereIsNoFollowUp() {
        Fixture f = new Fixture(1);
        f.archer.getResources().spend(CHARGE, 1);
        Assertions.assertEquals(0, f.archer.getResources().value(CHARGE), "precondition: 充能花光");
        int skillsPointsBefore = f.battle.getSkillPoints();
        Assertions.assertEquals(0, f.allyAttacks(), "没有充能 ⇒ 不发动追加攻击");
        Assertions.assertEquals(skillsPointsBefore, f.battle.getSkillPoints(), "也就不恢复战技点");
    }

    /**
     * And 「**队友**」 excludes herself: her own attack does not trigger her talent.
     */
    @Test
    public void herOwnAttackDoesNotTriggerTheTalent() {
        Fixture f = new Fixture(1);
        Assertions.assertEquals(1, f.archer.getResources().value(CHARGE), "precondition: 有 1 点充能");
        List<Damage> settled = new ArrayList<>();
        f.battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        f.battle.castImmediate(f.archer.getSkills().get(SkillType.COMMON), f.archer,
                List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        long followUps = settled.stream().filter(d -> d.getType() == DamageType.ADDITIONAL).count();
        System.out.println("[1015] 她自己攻击后：追击 " + followUps + " 次 ; 充能 " + f.archer.getResources().value(CHARGE));
        Assertions.assertEquals(0, followUps, "「**队友**对敌方目标施放攻击后」—— 她自己那一下不算");
        Assertions.assertEquals(1, f.archer.getResources().value(CHARGE), "充能也没有被扣");
    }

    /**
     * 秘技 千里眼: entering battle hits every enemy and grants 1 Charge - and only when the technique was declared.
     */
    @Test
    public void theTechniqueHitsEveryEnemyAndGrantsOneCharge() {
        Battle withTechnique = battle(2, true);
        double hp = 0;
        for (Enemy enemy : withTechnique.enemyUnits()) {
            hp += enemy.getMaxHp() - enemy.getCurrentHp();
        }
        System.out.println("[1015] 秘技开局: 敌方合计掉血 " + hp + " ; 充能 "
                + withTechnique.characters().getFirst().getResources().value(CHARGE));
        Assertions.assertTrue(hp > 0, "「对敌方全体造成…伤害」");
        Assertions.assertEquals(withoutTechniqueCharge() + 1,
                withTechnique.characters().getFirst().getResources().value(CHARGE),
                "「并获得 1 点充能」—— 与同一场没声明秘技相比**正好多 1 点**（开场那 1 点来自行迹，不算进来）");

        Battle withoutTechnique = battle(2, false);
        double untouched = 0;
        for (Enemy enemy : withoutTechnique.enemyUnits()) {
            untouched += enemy.getMaxHp() - enemy.getCurrentHp();
        }
        Assertions.assertEquals(0.0, untouched, 1e-9, "没声明秘技 ⇒ 什么都不发生（这是门那一半）");
    }

    /**
     * The Charge a battle opens with when the technique was NOT declared: trace 1015102's 1 point.
     */
    private static int withoutTechniqueCharge() {
        return battle(1, false).characters().getFirst().getResources().value(CHARGE);
    }

    /**
     * 行迹 正义伙伴 (1 Charge at battle start) and 投影魔术 (skill point cap +2).
     */
    @Test
    public void theTracesRaiseTheChargeAndTheSkillPointCap() {
        Fixture f = new Fixture(1);
        System.out.println("[1015] 开场充能 " + f.archer.getResources().value(CHARGE)
                + " ; 战技点上限 " + f.battle.getSkillPointMax());
        Assertions.assertEquals(1, f.archer.getResources().value(CHARGE), "「进入战斗时获得 1 点充能」");
        Assertions.assertEquals(5 + 2, f.battle.getSkillPointMax(), "「战技点上限额外增加 2 点」（标准上限 5）");
    }

    /**
     * 行迹 守护者: 「若战技点大于等于 4 点，暴击伤害提高 120%，持续 1 回合」 - the count, not the cap (the cap is 7 here).
     */
    @Test
    public void theCritDamageTraceReadsThePoolNotTheCap() {
        double atThree = critDamageGainFromASkillPointGain(3);
        double atFour = critDamageGainFromASkillPointGain(4);
        System.out.println("[1015] 获得战技点后的暴伤: 手里 3 点 +" + atThree + " ; 手里 4 点 +" + atFour
                + "（上限是 7 点）");
        Assertions.assertEquals(0.0, atThree, 1e-9,
                "手里 3 点 < 4 ⇒ 不触发（拿**上限** 7 冒充会在这里红）");
        Assertions.assertEquals(1.2, atFour, 1e-9, "手里 4 点 ⇒ 暴击伤害 +120%");
    }

    // ==================================================================

    /**
     * How much CRIT DMG the trace grants when a skill point is gained while the pool holds {@code skillPoints}.
     */
    private static double critDamageGainFromASkillPointGain(int skillPoints) {
        Fixture f = new Fixture(1);
        while (f.battle.getSkillPoints() > skillPoints) {
            f.battle.getSkillPointPolicy().spend();
        }
        while (f.battle.getSkillPoints() < skillPoints) {
            f.battle.getSkillPointPolicy().gain(1);
        }
        double before = f.archer.getAttribute(AttributeType.CRIT_ATTACK).get();
        f.battle.fireTriggers(TriggerEvent.SKILL_POINT_GAINED, f.archer, f.archer, 0, 1);
        f.battle.processRequests();
        return f.archer.getAttribute(AttributeType.CRIT_ATTACK).get() - before;
    }

    private static Battle battle(int enemies, boolean technique) {
        List<Enemy> foes = new ArrayList<>();
        for (int i = 0; i < enemies; i++) {
            foes.add(BattleFixture.dummy());
        }
        Battle battle = new Battle(List.of(CharacterFactory.create(ARCHER, 80), CharacterFactory.create(ALLY, 80)),
                foes, BattleFixture.alwaysRoll(0.9999));
        if (technique) {
            battle.markTechniqueUsed(battle.characters().getFirst());
        }
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    private static final class Fixture {
        private final Character archer;
        private final Character ally;
        private final Battle battle;

        private Fixture(int enemies) {
            this.battle = battle(enemies, false);
            this.archer = battle.characters().getFirst();
            this.ally = battle.characters().get(1);
        }

        /**
         * The teammate attacks the first enemy; returns how many ADDITIONAL instances it settled (i.e. the follow-ups).
         */
        private int allyAttacks() {
            List<Damage> settled = new ArrayList<>();
            battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
            battle.castImmediate(ally.getSkills().get(SkillType.COMMON), ally,
                    List.of(battle.enemyUnits().getFirst()));
            battle.processRequests();
            return (int) settled.stream().filter(d -> d.getType() == DamageType.ADDITIONAL).count();
        }
    }
}
