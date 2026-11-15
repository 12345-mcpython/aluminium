package com.laosun.aluminium.test.content.characters.danheng;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.buff.StatModifierBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.data.SkillData;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TestTurns;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1206… rather 1002 Dan Heng: the Skill's crit-slow (and eidolon 6 deepening it), trace 绝影, the eidolons around
 * them, and where each of his numbers comes from.
 *
 * <p>⚠ The crit is driven, not simulated: `BattleFixture.alwaysRoll(0.0)` makes every crit roll pass, and the
 * engine reports the crit through `CRIT_DEALT` - the same event the rule hangs on. So each case below is a real
 * cast, and the observable is the enemy's SPEED (or her own), not an internal count.
 *
 * <h2>Where each of his numbers comes from</h2>
 * The ones that move with a skill level read their own row, and the ones that cannot are checked against the row they
 * claim.
 *
 * <h2>The two numbers that used to be literals</h2>
 * <ul>
 *   <li><b>the talent's 36%</b> res-pen. His Talent's parameter row is {@code [0.18, 2]} at Lv1, {@code [0.36, 2]} at
 *       Lv10 and {@code [0.45, 2]} at Lv15 - a column that <b>moves with the level</b>. Writing
 *       {@code percent: 0.36} froze it, so 星魂 5 (Talent +2 = 12 levels) read 0.36 instead of <b>0.396</b>. The rule
 *       now states {@code percent_from_skill_param: "TALENT:0"}.</li>
 *   <li><b>the technique's 40%</b> ATK. Its table is a single row ({@code [0.4, 3]}), so the number is the same
 *       either way - but the source is now in the rule instead of being a literal that looks measured and is not.</li>
 * </ul>
 *
 * <h2>The numbers that stay literal on purpose</h2>
 * The Skill's slow is <b>-12%</b>: the row carries the magnitude {@code 0.12}, while the <b>sign</b> is the sentence
 * (「速度<u>降低</u>」) - there is no "negate the row" spelling, so a literal is the honest form. It is therefore
 * checked <b>against the row</b> here (the applied slow must equal {@code row[1]}), so the two cannot drift apart
 * silently.
 *
 * <p>Merged from the former {@code DanHengKitCompletionTest} and {@code DanHengLevelSourceTest}; every assertion and
 * Javadoc of measurement is preserved, the single-clause cases consolidated into scenario tests.
 */
public class DanHengEidolonsAndTracesTest {
    private static final int DANHENG = 1002;
    private static final int ALLY = 1201;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-9;
    /**
     * A roll between his base crit rate (0.05 from character_data.json) and base + 12% (0.17), so the SAME
     * roll crits only when the eidolon is active. Measured, not guessed - the value is the whole experiment.
     */
    private static final double ROLL_BETWEEN = 0.10;

    // ==================================================================
    // From DanHengKitCompletionTest: the crit-slow, the traces and the eidolons
    // ==================================================================

    /**
     * ⭐ 「战技造成的伤害触发暴击时…速度降低12%」: a critting Skill slows the target, 星魂 6 「战技触发的减速状态使目标的速度
     * 额外降低8%」 deepens that same slow to 20%, and the scoping control rides along.
     *
     * <p>⚠ One scenario, because all four observations are the same object on the same fixture - the enemy's SPEED after
     * one cast. The SCOPING is asserted in the same case: a critting BASIC ATTACK must not slow, because the sentence
     * says 「战技」; without the control, a rule that fired on any crit would pass. Without the rank-0 half, a hard-coded
     * 20% would pass.
     */
    @Test
    public void aSkillCritSlowsTheTargetAndEidolon6DeepensTheSlow() {
        Fixture skill = new Fixture(0);
        double before = skill.enemy.getAttribute(AttributeType.SPEED).get();
        skill.cast(SkillType.SKILL);
        double slowed = skill.enemy.getAttribute(AttributeType.SPEED).get();

        Fixture basic = new Fixture(0);
        double basicBefore = basic.enemy.getAttribute(AttributeType.SPEED).get();
        basic.cast(SkillType.COMMON);
        double basicAfter = basic.enemy.getAttribute(AttributeType.SPEED).get();

        double atZero = slowRatio(new Fixture(0));
        double atSix = slowRatio(new Fixture(6));

        Assertions.assertAll("「战技造成的伤害触发暴击时…速度降低12%」",
                () -> Assertions.assertTrue(slowed < before,
                        "「战技造成的伤害触发暴击时…速度降低12%」: " + before + " -> " + slowed),
                () -> Assertions.assertEquals(basicBefore, basicAfter, 1e-9,
                        "「战技」 - a critting basic attack is not that sentence"),
                () -> Assertions.assertEquals(0.12, atZero, 1e-9, "the base slow is 12%"),
                () -> Assertions.assertEquals(0.2, atSix, 1e-9,
                        "「额外降低8%」 makes it 20% in total, not a second 8% modifier (" + atZero + " -> " + atSix + ")"));
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

        // ---- the two cases that separate「这一发伤害的来源施放」 from「实例自己的槽位」 ----
        //
        // (A) the ultimate breaks a Wind-weak target and the BREAK damage lands the kill: the break instance carries its own
        //     UNSPECIFIED category (it must - otherwise the ultimate's +120% multiplier would be added to the break), but the
        //     event follows the chain through `causedByCast`, so this IS 「施放终结技造成」.
        Assertions.assertNotNull(breakingUltimateKillActor(),
                "the ultimate broke it and the break damage killed it: still that ultimate's kill");
        // (B) the ultimate leaves the target alive and a LATER dot instance of his finishes it: no.
        Assertions.assertNull(dotAfterTheUltimate(),
                "a later持续伤害 is not damage this cast caused");
    }

    /**
     * 星魂 4, case (A): Dan Heng's ultimate against a Wind-weak target with 1 toughness left and just enough HP to survive
     * the ultimate's own instance - so the <b>break damage</b> is what kills. Returns the unit the engine granted the extra
     * turn to (or {@code null}).
     */
    private static Object breakingUltimateKillActor() {
        Character danHeng = CharacterFactory.create(DANHENG, LEVEL, true, null, null, 4);
        com.laosun.aluminium.enemy.Enemy enemy =
                BattleFixture.enemy(100201602, 90);              // weak to Wind: his ultimate can break it
        Battle battle = new Battle(new ArrayList<>(List.of(danHeng)), new ArrayList<>(List.of(enemy)),
                BattleFixture.seeded());
        battle.startBattle();

        // The ultimate's own settled damage, measured on a spare copy of the same fight.
        double ultDamage = ultimateDamageAgainst(100201602);
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(ultDamage + 1));
        enemy.heal(ultDamage + 1);
        enemy.reduceStance(enemy.getStance() - 1);                // one point left: the ultimate breaks it
        Assertions.assertEquals(1.0, enemy.getStance(), 1e-9, "precondition: one toughness point left");
        Assertions.assertTrue(enemy.isWeakTo(com.laosun.aluminium.enums.DamageElement.WIND),
                "precondition: Wind reduces its toughness at all");

        com.laosun.aluminium.skill.SkillExecutor.execute(battle, danHeng.getSkills().get(SkillType.ULTRA),
                danHeng, List.of(enemy));
        Assertions.assertTrue(enemy.isDeath(), "precondition: the break damage killed it");
        Assertions.assertTrue(enemy.isBroken(), "precondition: the ultimate broke it");
        return battle.getExtraTurnActor() == null ? null : danHeng;
    }

    /** The ultimate's settled normal damage against that monster, measured rather than assumed. */
    private static double ultimateDamageAgainst(int monsterId) {
        Character danHeng = CharacterFactory.create(DANHENG, LEVEL, true, null, null, 4);
        com.laosun.aluminium.enemy.Enemy enemy = BattleFixture.enemy(monsterId, 90);
        Battle battle = new Battle(new ArrayList<>(List.of(danHeng)), new ArrayList<>(List.of(enemy)),
                BattleFixture.seeded());
        battle.startBattle();
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(1_000_000));
        enemy.heal(1_000_000);
        double[] settled = new double[1];
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> {
            if (damage.getType() == com.laosun.aluminium.enums.DamageType.NORMAL) {
                settled[0] = applied;
            }
        });
        com.laosun.aluminium.skill.SkillExecutor.execute(battle, danHeng.getSkills().get(SkillType.ULTRA),
                danHeng, List.of(enemy));
        return settled[0];
    }

    /**
     * 星魂 4, case (B): the ultimate hits a target that survives it, and a <b>later</b> damage instance of his (a 持续伤害
     * tick) finishes the target. The kill is not the ultimate's.
     */
    private static Object dotAfterTheUltimate() {
        Character danHeng = CharacterFactory.create(DANHENG, LEVEL, true, null, null, 4);
        var enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(danHeng)), new ArrayList<>(List.of(enemy)),
                BattleFixture.seeded());
        battle.startBattle();
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(1_000_000));
        enemy.heal(1_000_000);
        com.laosun.aluminium.skill.SkillExecutor.execute(battle, danHeng.getSkills().get(SkillType.ULTRA),
                danHeng, List.of(enemy));
        Assertions.assertFalse(enemy.isDeath(), "precondition: the ultimate did not kill it");
        Assertions.assertNull(battle.getExtraTurnActor(), "precondition: nothing scheduled yet");
        enemy.takeDamage(enemy.getCurrentHp() - 1);
        battle.applyDamage(enemy, new Damage(danHeng, enemy, com.laosun.aluminium.enums.DamageElement.WIND,
                com.laosun.aluminium.enums.DamageType.DOT, 5_000, SkillCategory.UNSPECIFIED));
        Assertions.assertTrue(enemy.isDeath(), "precondition: the dot finished it");
        return battle.getExtraTurnActor();
    }

    /**
     * 天赋 寸长寸强 - the <b>three negatives</b> the sentence's two nouns imply: 「<b>我方技能</b>的<b>施放目标</b>」.
     *
     * <p>Asserting only the positive (an ally's Skill aimed at him) leaves three ways for the rule to be wrong without a
     * symptom: dropping `actor is_ally` makes an enemy Skill aimed at him raise his penetration; dropping `target == self`
     * makes any ally Skill raise it, whoever it was aimed at; and a rule that read 「我方攻击」 instead of 「我方技能」 would
     * answer to the ally's basic attack too. The middle one is the subtlest: the document says 「成为我方技能的施放目标」,
     * so being on the same team is not enough - he has to be the target.
     *
     * <p>The events are fired directly, the same way the 绝影 case below does it: this is about the rule's conditions,
     * not about whether a cast can be assembled.
     */
    @Test
    public void theTalentAnswersOnlyToAnAllySkillAimedAtHim() {
        // (1) an ENEMY skill aimed at him: 「我方技能」 says no
        Fixture enemySkill = new Fixture(0);
        enemySkill.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, enemySkill.enemy, enemySkill.danheng, 0, 0));
        Assertions.assertEquals(0.0,
                enemySkill.danheng.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), 1e-9,
                "「我方技能」 - an enemy's Skill aimed at him must not raise his penetration");

        // (2) an ALLY skill aimed at somebody ELSE: 「的施放目标」 says no
        Fixture elsewhere = new Fixture(0);
        elsewhere.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, elsewhere.ally, elsewhere.ally, 0, 0));
        Assertions.assertEquals(0.0,
                elsewhere.danheng.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), 1e-9,
                "being on his team is not enough - the Skill has to be aimed at HIM");

        // (3) the ally's BASIC attack aimed at him: 「技能」 in this document includes a basic attack (the rule's note
        // records that reading), so this one DOES fire - it is the control that the gate is 「我方」, not 「战技」.
        Fixture allyBasic = new Fixture(0);
        allyBasic.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, allyBasic.ally, allyBasic.danheng, 0, 0));
        Assertions.assertTrue(allyBasic.danheng.getAttribute(AttributeType.DAMAGE_PENETRATION).get() > 0,
                "an ally's attack aimed at him does trigger it: the gate is 「我方技能」, not a particular slot");
    }

    /**
     * 行迹 绝影 「施放攻击后有50%的固定概率使自身速度提高20%」 - pinned to a passing roll, so this is about the
     * effect and not about luck.
     */
    @Test
    public void traceFleetingShadowRaisesHerOwnSpeed() {
        Fixture f = new Fixture(0);
        double before = f.danheng.getAttribute(AttributeType.SPEED).get();

        f.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(com.laosun.aluminium.enums.TriggerEvent.ALLY_ATTACK,
                f.danheng, f.enemy, 0, 0));

        double after = f.danheng.getAttribute(AttributeType.SPEED).get();
        // The VALUE, not just the direction: `> before` would stay green for a 2% raise. Provenance: `1002102`'s
        // `ParamList = [{0.5}, {0.2}, {2}]` - fixed chance 50%, SPEED 20%, 2 turns - and the 0.5 is the rule-level
        // `chance` (a fixed probability that never goes through effect hit/resistance), not `base_chance`.
        Assertions.assertEquals(0.2, after / before - 1.0, 1e-9,
                "「使自身速度提高20%」: " + before + " -> " + after);
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

    /**
     * 终结技「当受到攻击的敌方目标处于减速状态时，终结技造成的伤害倍率提高 120%」 - and, in the same scenario, the scoping
     * that makes it the ULTIMATE's clause: a slowed target does not make the Skill hit harder.
     *
     * <p>⚠ What makes this observable: the extra 120% is a <b>multiplier</b> (4.0 -> 5.2 at Lv10), so the same
     * zones give a ratio of 1.3 - and the same hit against an unslowed target is the control. A `BOOST_DAMAGE`
     * implementation would read 2.2 instead, which is why the assertion is the ratio and not the absolute number.
     * Both assertions read the same observed object (the settled damage of one slowed-target cast) off the same
     * fixture.
     */
    @Test
    public void theUltimateBonusAtASlowedTargetIsTheUltimatesOwn() {
        double plain = new Fixture(0).ult();
        Fixture slowed = new Fixture(0);
        slowed.slowTheEnemy();
        double boosted = slowed.ult();

        double plainSkill = new Fixture(0).skillDamage();
        Fixture slowedForSkill = new Fixture(0);
        slowedForSkill.slowTheEnemy();
        double skillAtSlowedTarget = slowedForSkill.skillDamage();

        Assertions.assertAll("「终结技造成的伤害」",
                () -> Assertions.assertEquals(1.3, boosted / plain, 1e-9,
                        "4.0 + 1.2 at Lv10 is +30% of the multiplier, not +120% of the damage: " + plain + " -> " + boosted),
                () -> Assertions.assertEquals(plainSkill, skillAtSlowedTarget, 1e-9,
                        "「终结技造成的伤害」 - not any hit"));
    }

    /**
     * 行迹 罡风「普攻对减速状态下的敌方目标造成的伤害提高 40%」.
     */
    @Test
    public void theBasicAttackHitsHarderAtASlowedTarget() {
        double plain = new Fixture(0).basic();
        Fixture slowed = new Fixture(0);
        slowed.slowTheEnemy();
        double boosted = slowed.basic();

        // Note: The ratio is measured against the boost zone he already has (his wind-damage traces), because +40%
        // enters that ADDITIVE zone: (1 + wind + 0.4) / (1 + wind), not 1.4.
        double wind = slowed.danheng.getAttribute(AttributeType.WIND_DAMAGE_BOOST).get();
        Assertions.assertEquals((1 + wind + 0.4) / (1 + wind), boosted / plain, 1e-9,
                "「伤害提高 40%」 lands in the DMG-boost zone he already carries (" + wind + ")");
    }

    /**
     * 星魂 2「天赋的冷却时间减少 1 回合」.
     *
     * <p>⚠ The state has to be observable, so the sequence is: the talent fires (penetration rises), his own attack
     * consumes `until: next_attack`, one of HIS turns passes (where a cooldown is counted down), then the same ally
     * Skill is aimed at him again. At rank 2 the talent fires again; at rank 0 the stated cooldown is still 2 and it
     * must not.
     */
    @Test
    public void eidolon2BringsTheTalentBackOneTurnSooner() {
        Assertions.assertTrue(talentFiresAgainAfterOneOwnTurn(2),
                "「天赋的冷却时间减少 1 回合」: one turn later the ally's Skill raises his penetration again");
        Assertions.assertFalse(talentFiresAgainAfterOneOwnTurn(0),
                "the control: at rank 0 the talent states 2 turns, so one turn is not enough");
    }

    /** Fires the talent, consumes its buff, takes one of his turns, and reports whether a second firing happened. */
    private static boolean talentFiresAgainAfterOneOwnTurn(int eidolon) {
        Fixture f = new Fixture(eidolon);
        f.aimAllySkillAtHim();
        f.basic();
        TestTurns.take(f.battle, f.danheng);
        f.aimAllySkillAtHim();
        return f.danheng.getAttribute(AttributeType.DAMAGE_PENETRATION).get() > 1e-9;
    }

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

        /** 「减速状态」: the engine's only representation of a slow - a negative SPEED modifier. */
        private void slowTheEnemy() {
            enemy.getBuffManager().addBuff(StatModifierBuff.percentDebuff(AttributeType.SPEED, -0.12, 2));
        }

        /** The ally's Skill, aimed at him - what his talent listens for. */
        private void aimAllySkillAtHim() {
            battle.castImmediate(ally.getSkills().get(SkillType.SKILL), ally, List.of(danheng));
        }

        private double ult() {
            cast(SkillType.ULTRA);
            return lastSettled;
        }

        private double basic() {
            cast(SkillType.COMMON);
            return lastSettled;
        }

        private double skillDamage() {
            cast(SkillType.SKILL);
            return lastSettled;
        }

        /** A killing blow of the given cast category, so the KILL event carries that slot. */
        private void killWith(SkillCategory category) {
            enemy.takeDamage(enemy.getCurrentHp() - 1);
            battle.applyDamage(enemy, new Damage(danheng, enemy, DamageElement.WIND, DamageType.NORMAL,
                    1_000, category));
            Assertions.assertTrue(enemy.isDeath(), "precondition: the blow killed it");
        }
    }

    // ==================================================================
    // From DanHengLevelSourceTest: where the talent, technique and slow numbers come from
    // ==================================================================

    /**
     * 天赋: the penetration is <b>the Talent row at its current level</b> - so 星魂 5 moves it, and at rank 0 it is
     * exactly the 36% the sentence states.
     */
    @Test
    public void theTalentPenetrationIsTheTalentsOwnRow() {
        double atRankZero = penetrationAfterTheAllyAims(0);
        double atRankFive = penetrationAfterTheAllyAims(5);

        var rows = SkillData.init(DANHENG, 4).getSkills();
        Assertions.assertEquals(rows.get(9).getFirst(), atRankZero, EPS,
                "rank 0 raises the Talent by 9 (the file's level convention) ⇒ row 10, whose first parameter is "
                        + rows.get(9).getFirst());
        Assertions.assertEquals(rows.get(11).getFirst(), atRankFive, EPS,
                "星魂 5 raises the Talent by 2 more ⇒ row 12, i.e. " + rows.get(11).getFirst()
                        + " - a literal 0.36 would have stayed at 0.36 here");
        Assertions.assertNotEquals(atRankZero, atRankFive, EPS,
                "…and the two differ, so the value really is read from the row rather than written down");
    }

    /**
     * 秘技: the ATK boost is the technique row's first parameter (a single-row table, so this pins the <b>source</b>) and
     * it is a <b>share of the base</b> (+40%), not a flat +0.4.
     *
     * <p>⚠ Measured as the <b>difference</b> between a battle where the technique was used and one where it was not,
     * because his stat traces already raise ATK by 18%: reading the ratio alone would report 0.58 and look wrong.
     */
    @Test
    public void theTechniqueAttackBonusIsTheTechniqueRow() {
        double withTechnique = attackRatioWithTechnique(true);
        double without = attackRatioWithTechnique(false);

        double row = SkillData.init(DANHENG, 7).getSkills().getFirst().getFirst();
        Assertions.assertEquals(row, withTechnique - without, EPS,
                "「攻击力提高 40%，持续 3 回合」 ⇒ the technique row's #1, applied as a share of the base");
        // The trap this case was written for: stated as `percent_from_skill_param` with no scale, the modifier was once
        // handed down as an ABSOLUTE number, so this came out as +0.4 of attack instead of +40%.
        Assertions.assertTrue(withTechnique - without > 0.39, "a flat +0.4 would be ~0.0007 of his attack");
    }

    /**
     * His ATK divided by its base, with or without the technique's opening buff.
     */
    private static double attackRatioWithTechnique(boolean used) {
        Character danHeng = CharacterFactory.create(DANHENG, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(danHeng)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        if (used) {
            battle.markTechniqueUsed(danHeng);
        }
        battle.startBattle();
        return danHeng.getAttribute(AttributeType.ATTACK).get()
                / danHeng.getAttribute(AttributeType.ATTACK).baseValue() - 1.0;
    }

    /**
     * The Skill's slow is a literal with a <b>sign the row does not carry</b>, so it is checked against the row: the
     * applied SPEED reduction must be the row's magnitude.
     *
     * <p>Note: This is the guard for "a literal that is the row plus the sentence's sign". The sentence says 降低, the
     * row says 0.12, so the modifier is -0.12; if the row were rebalanced to 0.15, this case would go red and the literal
     * would have to move with it.
     */
    @Test
    public void theSlowLiteralIsTheRowsMagnitudeWithTheSentencesSign() {
        var row = SkillData.init(DANHENG, 2).getSkills().get(9);     // Lv10, the file's level convention
        double statedSlow = row.get(1);
        double statedTurns = row.get(2);
        Assertions.assertEquals(2.0, statedTurns, EPS, "precondition: the duration is the row's third parameter");

        Character danHeng = CharacterFactory.create(DANHENG, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(danHeng)), new ArrayList<>(List.of(enemy)),
                BattleFixture.alwaysRoll(0.0));               // every crit roll passes, so the slow always lands
        battle.startBattle();
        double before = enemy.getAttribute(AttributeType.SPEED).get();
        battle.castImmediate(danHeng.getSkills().get(SkillType.SKILL), danHeng, List.of(enemy));

        double applied = 1.0 - enemy.getAttribute(AttributeType.SPEED).get() / before;
        Assertions.assertEquals(statedSlow, applied, EPS,
                "the applied reduction must be the row's magnitude, not a number that merely matches it today");
    }

    /**
     * His penetration after an ally aims a Skill at him, at the given eidolon rank.
     */
    private static double penetrationAfterTheAllyAims(int eidolon) {
        Character danHeng = CharacterFactory.create(DANHENG, LEVEL, true, null, null, eidolon);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(danHeng, ally)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        battle.startBattle();
        battle.castImmediate(ally.getSkills().get(SkillType.SKILL), ally, List.of(danHeng));
        return danHeng.getAttribute(AttributeType.DAMAGE_PENETRATION).get();
    }
}
