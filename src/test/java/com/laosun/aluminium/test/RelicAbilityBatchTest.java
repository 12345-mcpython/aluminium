package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.data.RelicTriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.beans.MemospriteSpec;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.StatModifierBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.enemy.SummonFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The 2026-09-28 content pass over the {@code Writable now:} backlog — five relic abilities whose every clause
 * already had a spelling, written out.
 *
 * <p><b>Why a content pass needs tests at all.</b> Each of these was "already expressible", which is exactly the
 * kind of change that can be wrong in ways nothing reports: the wrong attribute, the wrong tier, a number read
 * from the wrong row, or a stacking default that silently replaces instead of adding. Four of the five registered
 * reasons carried a figure that <b>no parameter supports</b> — 102's Basic-ATK share (12% vs param 0.1), 111's
 * energy (8 vs param 3), 310's threshold (50% vs param 0.3) and 313's per-stack CRIT DMG (which is why 313 is
 * still not authored) — so every number below is asserted against the file that was written, not against prose.
 *
 * <p>313 (无主荒星茨冈尼亚) stayed registered, and its reason was rewritten: 「当敌方目标被消灭时」 needs "the one
 * who died is an ENEMY", and {@code KILL} fires for any death with the victim as {@code target} — the condition
 * DSL has no camp variable (F-5). A wrong number and a missing spelling in one entry.
 */
public class RelicAbilityBatchTest {
    private static final double EPS = 1e-6;

    /** 姬子 — carries no rule file of her own, so only the set's rules are in play. */
    private static final int WEARER = 1003;
    /**
     * The "someone else" in the cases that need a second character. Deliberately the SAME rule-less character as
     * {@link #WEARER} rather than a colourful one: 桂乃芬 (1210) was the first draft and her own trace is
     * 「对陷入灼烧状态的敌方目标造成的伤害提高20%」 -- which is exactly the debuff the Pioneer case applies, so she
     * boosted her own hit by 20% and the case measured her rule instead of the set's.
     */
    private static final int ALLY = WEARER;
    private static final int LEVEL = 80;
    private static final int STAR = 5;
    private static final int RELIC_LEVEL = 15;
    private static final int MONSTER = 1002011;

    private static final int MUSKETEER = 102;
    private static final int THIEF = 111;
    private static final int PIONEER = 117;
    private static final int SCHOLAR = 122;
    private static final int BROKEN_KEEL = 310;
    private static final int FIRESMITH = 107;
    private static final int VALOROUS = 120;
    private static final int GLAMOTH = 311;
    private static final int SHATTERED_WORLD = 127;
    private static final int POET = 124;
    private static final int SERENE_DEMESNE = 319;
    private static final int RAPT_BROODING = 320;
    /** 「对己方角色施放」 family, authored 2026-09-27 with the `is_ally` condition. */
    private static final int MESSENGER = 114;
    private static final int WATCHMAKER = 118;
    private static final int SACERDOS = 121;
    private static final int SKILL_SLOT = 2;
    private static final int ULT_SLOT = 3;
    /** 生命的翁法罗斯: the derived-value set (its bonus is a function of Max Energy). */
    private static final int AMPHOREUS = 328;
    /** 出云显世与高天神国: the party-composition set (its CRIT Rate needs a teammate on the same Path). */
    private static final int IZUMO = 314;
    /** 盗贼公国塔利亚: the weakness-gated set (its Break Effect needs a fire-weak enemy). */
    private static final int BANDITRY = 316;

    // ==================================================================
    // 102 — 普攻伤害 +10%
    // ==================================================================

    /** The Basic ATK half is a rule; the SPD half is the data path's `properties` stat (and is not repeated). */
    @Test
    public void theMusketeerBoostsBasicAttacksAndNotSkills() {
        Character wearer = wearing(MUSKETEER);
        new Battle(List.of(wearer), List.of(dummy()), new Random(0)).startBattle();

        Assertions.assertEquals(0.1, boostOf(wearer, AttributeType.BASIC_ATTACK_DAMAGE_BOOST), EPS,
                "param #2 is 0.1 -- the registered reason's 12% came from the English sentence");
        Assertions.assertEquals(0, boostOf(wearer, AttributeType.SKILL_DAMAGE_BOOST), EPS,
                "「普攻造成的伤害」 is one scope, not every cast");
    }

    // ==================================================================
    // 111 — 击破弱点后回能
    // ==================================================================

    /** Only the wearer's own break pays, and the amount is the parameter's 3. */
    @Test
    public void theThiefRegainsEnergyOnItsOwnBreak() {
        Character wearer = wearing(THIEF);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(wearer, ally), List.of(dummy()), new Random(0));
        battle.startBattle();
        double before = wearer.getCurrentEnergy();

        battle.fireTriggers(TriggerEvent.BREAK, ally, dummy(), 0, 0);
        Assertions.assertEquals(before, wearer.getCurrentEnergy(), EPS,
                "「装备者击破」: a teammate's break is not hers");

        battle.fireTriggers(TriggerEvent.BREAK, wearer, dummy(), 0, 0);
        Assertions.assertEquals(before + 3, wearer.getCurrentEnergy(), EPS,
                "param #2 is 3 -- the registered reason's 8 has no parameter behind it");
    }

    // ==================================================================
    // 117 — 对受负面状态影响的敌人增伤
    // ==================================================================

    /**
     * The boost is 12%, it reaches the instance being settled, and it is <b>hers</b>.
     *
     * <p>Measured against an identical monster without a debuff: the two settle through the same zones, so the
     * ratio is the boost itself. Crit is pinned to 0 so the ratio is exact.
     */
    @Test
    public void thePioneerBoostsHerDamageAgainstDebuffedEnemies() {
        Character wearer = wearing(PIONEER);
        wearer.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0));
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        ally.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0));
        Enemy debuffed = dummy();
        Enemy clean = dummy();
        Battle battle = new Battle(List.of(wearer, ally), List.of(debuffed, clean), new Random(0));
        battle.startBattle();
        debuffed.getBuffManager().addBuff(new com.laosun.aluminium.models.buff.DotBuff(
                wearer, DamageElement.FIRE, 1, 2));      // any debuff: the count is what the text asks for

        double onDebuffed = hit(battle, wearer, debuffed);
        double onClean = hit(battle, wearer, clean);

        Assertions.assertTrue(onClean > 0, "precondition: the clean hit landed");
        Assertions.assertEquals(0.12, onDebuffed / onClean - 1, 1e-6,
                "param #1 is 0.12 and it applies to the instance being settled");

        // 「装备者造成的伤害」: the wearer's rule must not boost a teammate's hit on the same enemy.
        double allyOnDebuffed = hit(battle, ally, debuffed);
        double allyOnClean = hit(battle, ally, clean);
        Assertions.assertEquals(allyOnClean, allyOnDebuffed, EPS,
                "a rule on the wearer's table is not a rule on everyone's damage");
    }

    // ==================================================================
    // 122 — 战技/终结技增伤，终结技后下一次战技额外 +25%
    // ==================================================================

    /**
     * The two scoped boosts are permanent, and the extra 25% <b>adds</b> to the 20% rather than replacing it.
     *
     * <p>This is the case that would have shipped a silent 10-point error: 「额外提高」 means on top, while the
     * default stack cap is 1 and two modifiers on one attribute then replace each other — so the file states
     * {@code max_stacks: 2}.
     */
    @Test
    public void theScholarAddsItsExtraSkillDamageOnTop() {
        Character wearer = wearing(SCHOLAR);
        Battle battle = new Battle(List.of(wearer), List.of(dummy()), new Random(0));
        battle.startBattle();

        Assertions.assertEquals(0.2, boostOf(wearer, AttributeType.SKILL_DAMAGE_BOOST), EPS, "param #1");
        Assertions.assertEquals(0.2, boostOf(wearer, AttributeType.ULTIMATE_DAMAGE_BOOST), EPS, "param #1");

        battle.fireTriggers(TriggerEvent.ULT_CAST, wearer, null, 0, 0);

        Assertions.assertEquals(0.45, boostOf(wearer, AttributeType.SKILL_DAMAGE_BOOST), EPS,
                "0.20 + 0.25: 「额外提高」 adds, which is what max_stacks: 2 is for");
    }

    // ==================================================================
    // 310 — 效果抵抗 ≥ 30% 时我方全体暴击伤害 +10%
    // ==================================================================

    /** The threshold is 30%, it is a fraction, and it reaches the whole side. */
    @Test
    public void theBrokenKeelAppliesAboveItsThreshold() {
        Assertions.assertEquals(0.1,
                brokenKeelCritDamageAt(0.35) - bareCritDamage(), EPS,
                "param #2 is 0.3 -- the registered reason said 50%, which no parameter supports");
        Assertions.assertEquals(0,
                brokenKeelCritDamageAt(0.25) - bareCritDamage(), EPS,
                "below it nothing is granted");
    }

    // ==================================================================
    // 107, 120, 311, 127 -- the second pass over the backlog
    // ==================================================================

    /** The Skill boost is permanent, and the fire boost is consumed by the next attack. */
    @Test
    public void theFiresmithBoostsTheNextAttackAfterItsUltimate() {
        Character wearer = wearing(FIRESMITH);
        Battle battle = new Battle(List.of(wearer), List.of(dummy()), new Random(0));
        battle.startBattle();
        Assertions.assertEquals(0.12, boostOf(wearer, AttributeType.SKILL_DAMAGE_BOOST), EPS, "param #1");

        // Measured as a delta: the relic's random sub-stats can carry FIRE_DAMAGE_BOOST themselves, which is
        // how the first draft of this case came out at 0.444.
        double before = boostOf(wearer, AttributeType.FIRE_DAMAGE_BOOST);
        battle.fireTriggers(TriggerEvent.ULT_CAST, wearer, null, 0, 0);
        Assertions.assertEquals(before + 0.12, boostOf(wearer, AttributeType.FIRE_DAMAGE_BOOST), EPS, "param #2");

        battle.castImmediate(new DefaultSkill(WEARER, 1, 1), wearer, List.of(battle.enemyUnits().getFirst()));
        Assertions.assertEquals(before, boostOf(wearer, AttributeType.FIRE_DAMAGE_BOOST), EPS,
                "「下一次攻击」: the attack has happened, so the boost is gone");
    }

    /** Only the wearer's own follow-up grants it, and the share is the parameter's 36%. */
    @Test
    public void theValorousBoostsUltimateDamageAfterItsOwnFollowUp() {
        Character wearer = wearing(VALOROUS);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(wearer, ally), List.of(dummy()), new Random(0));
        battle.startBattle();

        battle.fireTriggers(TriggerEvent.FOLLOW_UP, ally, dummy(), 0, 0);
        Assertions.assertEquals(0, boostOf(wearer, AttributeType.ULTIMATE_DAMAGE_BOOST), EPS,
                "「装备者施放追加攻击时」: a teammate's follow-up is not hers");

        battle.fireTriggers(TriggerEvent.FOLLOW_UP, wearer, dummy(), 0, 0);
        Assertions.assertEquals(0.36, boostOf(wearer, AttributeType.ULTIMATE_DAMAGE_BOOST), EPS,
                "param #2 is 0.36 -- the registered reason said 12%");
    }

    /** The two nested thresholds must not both apply: 160 speed grants 18%, not 12% + 18%. */
    @Test
    public void theGlamothTiersAreExclusive() {
        Assertions.assertEquals(0, damageBoostAtSpeed(130), EPS, "below the first threshold");
        Assertions.assertEquals(0.12, damageBoostAtSpeed(135), EPS, "param #4");
        Assertions.assertEquals(0.18, damageBoostAtSpeed(160), EPS,
                "param #5 alone -- without the lower tier''s upper bound this would be 0.30");
    }

    /**
     * Three effects on one trigger, and the summon condition is what keeps the third reachable.
     *
     * <p>Without the memosprite the rule must do <b>nothing</b>, not throw: the effect that names
     * {@code target: "summon"} fails loudly when nothing is out, so 「若装备者的忆灵在场」 is load-bearing.
     */
    @Test
    public void theShatteredWorldNeedsItsMemospriteAndThenBoostsBoth() {
        Character wearer = wearing(SHATTERED_WORLD);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(wearer, ally), List.of(dummy()), new Random(0));
        battle.startBattle();
        double wearerHp = wearer.getMaxHp();
        double allyBoost = boostOf(ally, AttributeType.ALL_DAMAGE_TYPE_BOOST);

        Assertions.assertDoesNotThrow(() -> battle.fireTriggers(TriggerEvent.SKILL_CAST, wearer, null, 0, 0),
                "no memosprite out: the condition keeps the summon-targeted effect from being reached");
        Assertions.assertEquals(wearerHp, wearer.getMaxHp(), EPS, "…so nothing was granted");

        // A fixture memosprite rather than SummonFactory.memosprite(master): the wearer is deliberately the
        // rule-less character, and summoning 1413's or 1402's would drag their own rules into the measurement.
        // The engine's real summon path, not a hand-placed fixture: SUMMONED is fired for what
        // Battle.summon* places (it is recorded in justSummoned), so a fixture added straight to the roster
        // would announce nothing -- which the first draft of this case demonstrated by measuring 0.0.
        Summon evey = battle.summon(wearer, MONSTER, 1);
        battle.processRequests();                                // where SUMMONED is fired, by design
        battle.fireTriggers(TriggerEvent.SKILL_CAST, wearer, null, 0, 0);

        double bareHp = CharacterFactory.create(WEARER, LEVEL).getMaxHp();
        Assertions.assertEquals(0.24 * bareHp, wearer.getMaxHp() - wearerHp, 0.5,
                "「装备者…生命上限提高 24%」 -- asserted as the DELTA, because a percentage modifier on a base "
                        + "attribute adds 24% of the character's base Max HP while the relic's own sub-stats "
                        + "already carry their own HP%");
        Assertions.assertTrue(evey.getBuffManager().countBuffs(StatModifierBuff.class) > 0,
                "「及其忆灵」: the memosprite carries its own copy of the Max HP buff");
        Assertions.assertEquals(allyBoost + 0.15, boostOf(ally, AttributeType.ALL_DAMAGE_TYPE_BOOST), EPS,
                "「我方全体造成的伤害提高 15%」 reaches a teammate too");

        // M-38: the file states the sentence's disjunction as ONE duration with two ends. Asserted on the shipped
        // 4-piece rather than on a hand-made rule, because the shape of the JSON is half of what this guarantees.
        List<TriggerTable.CompiledRule> rules = RelicTriggerTables.of(SHATTERED_WORLD).at(4)
                .matching(TriggerEvent.SKILL_CAST,
                        new TriggerTable.TriggerContext(wearer, wearer, null, 0, 0, null, battle));
        Assertions.assertEquals(1, rules.size());
        for (EffectSpec effect : rules.getFirst().effects()) {
            Assertions.assertEquals(List.of("next_attack", "next_skill"), effect.getUntil(),
                    "「持续至装备者下次施放普攻或战技后」 -- a list, so a support Skill ends it too");
        }
    }

    // ==================================================================
    // 124 / 319 / 320 -- 「装备者及其忆灵」 reaches a LATE arrival (M-39)
    // ==================================================================

    /**
     * The memosprite half of a pre-battle effect lands when the memosprite arrives.
     *
     * <p>This is the decision M-39 needed: `target: "summon"` cannot be filed at BATTLE_START (nothing may be
     * out, and the selector fails loudly rather than silently missing), so each tier carries a second rule on
     * SUMMONED. Both halves are asserted: the wearer gets hers at the start, the memosprite gets hers when it
     * appears — including for a wearer whose kit summons it mid-fight, which is the case the single BATTLE_START
     * rule could not serve at all.
     */
    @Test
    public void aPreBattleMemospriteEffectLandsOnArrival() {
        Character wearer = wearing(SERENE_DEMESNE);               // 谧宁拾骨地: HP >= 5000 -> CRIT DMG +28%
        wearer.setAttribute(AttributeType.HEALTH, new DoubleValue(6_000));
        Battle battle = new Battle(List.of(wearer), List.of(dummy()), new Random(0));
        battle.startBattle();
        double wearerBoost = boostOf(wearer, AttributeType.CRIT_ATTACK);

        // The engine's real summon path, not a hand-placed fixture: SUMMONED is fired for what
        // Battle.summon* places (it is recorded in justSummoned), so a fixture added straight to the roster
        // would announce nothing -- which the first draft of this case demonstrated by measuring 0.0.
        Summon evey = battle.summon(wearer, MONSTER, 1);
        battle.processRequests();                                // where SUMMONED is fired, by design

        Assertions.assertTrue(wearerBoost > bareCritDamage(), "the wearer was granted hers at the start");
        Assertions.assertEquals(0.28, boostOf(evey, AttributeType.CRIT_ATTACK), EPS,
                "and the memosprite gets the same 28% when it arrives -- not at battle start, when it was not "
                        + "there yet. (Asserted as the raw value, not against the wearer's total: hers includes "
                        + "the base 0.5 CRIT DMG every character has, and the memosprite's panel gives it none.)");
    }

    /** Below the threshold nothing is granted, to either unit. */
    @Test
    public void aPreBattleMemospriteEffectRespectsItsThreshold() {
        Character wearer = wearing(SERENE_DEMESNE);
        wearer.setAttribute(AttributeType.HEALTH, new DoubleValue(4_000));
        Battle battle = new Battle(List.of(wearer), List.of(dummy()), new Random(0));
        battle.startBattle();

        // The engine's real summon path, not a hand-placed fixture: SUMMONED is fired for what
        // Battle.summon* places (it is recorded in justSummoned), so a fixture added straight to the roster
        // would announce nothing -- which the first draft of this case demonstrated by measuring 0.0.
        Summon evey = battle.summon(wearer, MONSTER, 1);
        battle.processRequests();                                // where SUMMONED is fired, by design

        Assertions.assertEquals(0, boostOf(evey, AttributeType.CRIT_ATTACK), EPS,
                "4000 is below 「大于等于 5000 点」 -- and HEALTH is a base attribute, so the literal is absolute");
    }

    /** The nested tiers hold on both halves: 90 speed grants 32%, not 20% + 32%. */
    @Test
    public void thePoetsNestedTiersHoldOnBothHalves() {
        Character wearer = wearing(POET);                        // 哀歌覆国的诗人: SPD < 110 / < 95
        wearer.setAttribute(AttributeType.SPEED, new DoubleValue(90));
        Battle battle = new Battle(List.of(wearer), List.of(dummy()), new Random(0));
        battle.startBattle();
        // The engine's real summon path, not a hand-placed fixture: SUMMONED is fired for what
        // Battle.summon* places (it is recorded in justSummoned), so a fixture added straight to the roster
        // would announce nothing -- which the first draft of this case demonstrated by measuring 0.0.
        Summon evey = battle.summon(wearer, MONSTER, 1);
        battle.processRequests();                                // where SUMMONED is fired, by design

        // Measured on the memosprite: its panel states HEALTH and SPEED only, so its CRIT Rate IS the buff
        // (0.32). The wearer's own value is unusable as an absolute -- the relic's random sub-stats carry
        // CRIT Rate of their own, which is how the first draft read 0.69394.
        Assertions.assertEquals(0.32, boostOf(evey, AttributeType.CRIT_CHANCE), 1e-6,
                "「小于 95」 alone: without the upper tier's exclusion this would be 0.52");
        Assertions.assertTrue(boostOf(wearer, AttributeType.CRIT_CHANCE) > bareCritRate(),
                "and the wearer was granted hers at the start");
    }

    /** The healing tiers reach the memosprite too, through OUTGOING_HEALING_BOOST. */
    @Test
    public void theGiantTreesHealingTiersReachTheMemosprite() {
        Character wearer = wearing(RAPT_BROODING);               // 渊思寂虑的巨树: SPD >= 135 / >= 180
        wearer.setAttribute(AttributeType.SPEED, new DoubleValue(190));
        Battle battle = new Battle(List.of(wearer), List.of(dummy()), new Random(0));
        battle.startBattle();
        // The engine's real summon path, not a hand-placed fixture: SUMMONED is fired for what
        // Battle.summon* places (it is recorded in justSummoned), so a fixture added straight to the roster
        // would announce nothing -- which the first draft of this case demonstrated by measuring 0.0.
        Summon evey = battle.summon(wearer, MONSTER, 1);
        battle.processRequests();                                // where SUMMONED is fired, by design

        Assertions.assertEquals(0.2, boostOf(wearer, AttributeType.OUTGOING_HEALING_BOOST), 1e-6,
                "the upper tier on the wearer, with no lower tier added on top");
        Assertions.assertEquals(0.2, boostOf(evey, AttributeType.OUTGOING_HEALING_BOOST), 1e-6,
                "and on the memosprite");
    }

    // ==================================================================
    // 114 / 118 / 121 — 「对己方角色施放」 (`target is_ally`, 2026-09-27)
    // ==================================================================

    /**
     * 114 骇域漫游的信使: the wearer's Ultimate <b>on an ally</b> speeds the whole side up — and aimed at an enemy it
     * does nothing at all.
     *
     * <p>⚠ Both halves in one case on purpose: the contrast is the whole reason the condition exists (`actor == self`
     * alone would fire for a damaging ultimate too), and a test that only checked the ally half would pass for a rule
     * that never looks at the target.
     */
    @Test
    public void theMessengerUltimateHastesThePartyOnlyWhenItTargetsAnAlly() {
        Character wearer = wearing(MESSENGER);
        Character ally = CharacterFactory.create(1210, LEVEL);
        Battle battle = new Battle(List.of(wearer, ally), List.of(dummy()), new Random(0));
        battle.startBattle();
        double speedBefore = boostOf(ally, AttributeType.SPEED);

        battle.castImmediate(new DefaultSkill(WEARER, ULT_SLOT, 1), wearer, List.of(ally));

        Assertions.assertEquals(speedBefore * 0.12, boostOf(ally, AttributeType.SPEED) - speedBefore, 1e-6,
                "「我方全体速度提高#1[i]%」 -- #1 = 0.12, a share of the target's base speed");

        Battle aimedAtEnemy = new Battle(List.of(wearing(MESSENGER), CharacterFactory.create(1210, LEVEL)),
                List.of(dummy()), new Random(0));
        aimedAtEnemy.startBattle();
        Character second = aimedAtEnemy.characters.get(1);
        double secondBefore = boostOf(second, AttributeType.SPEED);

        aimedAtEnemy.castImmediate(new DefaultSkill(WEARER, ULT_SLOT, 1), aimedAtEnemy.characters.getFirst(),
                List.of(aimedAtEnemy.enemyUnits().getFirst()));

        Assertions.assertEquals(secondBefore, boostOf(second, AttributeType.SPEED), 1e-6,
                "aimed at an ENEMY: 「对己方角色」 does not hold, so nothing is granted");
    }

    /** 118 梦游者钟表匠: the same trigger, a ratio attribute (+30% Break Effect, 2 turns). */
    @Test
    public void theWatchmakerUltimateRaisesThePartysBreakEffect() {
        Character wearer = wearing(WATCHMAKER);
        Character ally = CharacterFactory.create(1210, LEVEL);
        Battle battle = new Battle(List.of(wearer, ally), List.of(dummy()), new Random(0));
        battle.startBattle();
        double before = boostOf(ally, AttributeType.BREAKING_EFFECT);

        battle.castImmediate(new DefaultSkill(WEARER, ULT_SLOT, 1), wearer, List.of(ally));

        Assertions.assertEquals(0.3, boostOf(ally, AttributeType.BREAKING_EFFECT) - before, 1e-6,
                "「击破特攻提高#1[i]%」 -- a ratio attribute, so 0.3 is an absolute +30%");
    }

    /** 121 祭司的旧日祭礼: the Skill on an ally raises THAT ally's CRIT DMG, and it stacks to the cap it states. */
    @Test
    public void theSacerdosSkillBuffsTheAimedAllyAndStacksTwice() {
        Character wearer = wearing(SACERDOS);
        Character ally = CharacterFactory.create(1210, LEVEL);
        Battle battle = new Battle(List.of(wearer, ally), List.of(dummy()), new Random(0));
        battle.startBattle();
        double before = boostOf(ally, AttributeType.CRIT_ATTACK);

        battle.castImmediate(new DefaultSkill(WEARER, SKILL_SLOT, 1), wearer, List.of(ally));
        Assertions.assertEquals(0.18, boostOf(ally, AttributeType.CRIT_ATTACK) - before, 1e-6,
                "「使该目标暴击伤害提高#1[i]%」 -- #1 = 0.18, on the unit the cast was AIMED at");

        battle.castImmediate(new DefaultSkill(WEARER, SKILL_SLOT, 1), wearer, List.of(ally));
        Assertions.assertEquals(0.36, boostOf(ally, AttributeType.CRIT_ATTACK) - before, 1e-6,
                "「最多叠加#3[i]层」 -- two casts, two stacks (the engine's default would have REPLACED the first)");
    }

    // ==================================================================
    // 328 — a derived value read off MAX ENERGY, with a cap
    // ==================================================================

    /**
     * 328 生命的翁法罗斯: 「能量上限 ≥ 200 点，每超过 1 点使造成的伤害提高 0.2%，最多提高 32%」.
     *
     * <p>Three points on the curve, which is what the sentence actually says: below the threshold <b>nothing</b>,
     * 40 points over it <b>0.08</b>, and past 360 the <b>32% cap</b>. ⚠ The value is read off the modifier the rule
     * grants rather than off the resolved attribute: the wearer is built <b>with</b> a relic suit, so its random
     * sub-stats may already carry a damage boost of their own (the trap that made an earlier case read 0.444 instead
     * of 0.12).
     */
    @Test
    public void theAmphoreusBoostGrowsWithMaxEnergyAndStopsAtItsCap() {
        Assertions.assertEquals(0.002 * (240 - 200), grantedDamageBoost(240), 1e-6,
                "240 energy is 40 points over the threshold: 0.2% x 40 = 8%");
        Assertions.assertEquals(0.32, grantedDamageBoost(400), 1e-6,
                "「最多提高#3[i]%」 -- past 360 the cap holds, and the lower tier is excluded by its own upper bound");
        Assertions.assertEquals(0, grantedDamageBoost(150), 1e-6,
                "below the threshold nothing is granted at all (and 0.002 x 150 - 0.4 would have been negative)");
    }

    /** The modifier the rule granted on 全部伤害提高, or 0 when it granted none. */
    private static double grantedDamageBoost(double maxEnergy) {
        Character wearer = wearing(AMPHOREUS);
        wearer.setMaxEnergy(maxEnergy);
        Battle battle = new Battle(List.of(wearer), List.of(dummy()), new Random(0));
        battle.startBattle();

        for (com.laosun.aluminium.models.buff.StatModifierBuff buff
                : wearer.getBuffManager().allBuffsOf(com.laosun.aluminium.models.buff.StatModifierBuff.class)) {
            if (buff.getAttribute() == AttributeType.ALL_DAMAGE_TYPE_BOOST) {
                return buff.getValue();
            }
        }
        return 0;
    }

    // ==================================================================
    // 314 — the party-composition condition
    // ==================================================================

    /**
     * 314 出云显世与高天神国: the CRIT Rate arrives only when a teammate walks the wearer's Path.
     *
     * <p>⚠ The value is read off the <b>modifier the rule granted</b>, not off the resolved attribute: the wearer is
     * built with a relic suit, whose random sub-stats may carry CRIT Rate of their own (relic sub-stats become
     * attributes at build time, so a `StatModifierBuff` on that attribute can only have come from a rule).
     */
    @Test
    public void theIzumoCritRateNeedsATeammateOnTheSamePath() {
        Assertions.assertEquals(0.12, grantedCritRateWith(1013), 1e-6,
                "黑塔 is 智识, like the wearer: 「若至少存在一名与装备者命途相同的队友」");
        Assertions.assertEquals(0, grantedCritRateWith(1210), 1e-6,
                "桂乃芬 is 虚无: the condition does not hold, so nothing is granted");
    }

    /** The CRIT Rate modifier the suit granted, or 0 when it granted none. */
    private static double grantedCritRateWith(int allyCid) {
        Character wearer = wearing(IZUMO);
        Character ally = CharacterFactory.create(allyCid, LEVEL);
        Battle battle = new Battle(List.of(wearer, ally), List.of(dummy()), new Random(0));
        battle.startBattle();

        for (com.laosun.aluminium.models.buff.StatModifierBuff buff
                : wearer.getBuffManager().allBuffsOf(com.laosun.aluminium.models.buff.StatModifierBuff.class)) {
            if (buff.getAttribute() == AttributeType.CRIT_CHANCE) {
                return buff.getValue();
            }
        }
        return 0;
    }

    // ==================================================================
    // 316 — a weakness-gated buff
    // ==================================================================

    /**
     * 316 盗贼公国塔利亚: hitting a <b>fire-weak</b> enemy raises the wearer's Break Effect; anyone else does not.
     *
     * <p>⚠ Both sides in one case: the condition is the whole rule, and a rule that ignored the weakness would pass a
     * test that only checked the fire-weak enemy.
     */
    @Test
    public void theBanditryBreakEffectNeedsAFireWeakEnemy() {
        Assertions.assertEquals(0.2, breakEffectAfterHit(true), 1e-6,
                "「命中具有火属性弱点的敌方目标时」 -- +20% for 2 turns");
        Assertions.assertEquals(0, breakEffectAfterHit(false), 1e-6,
                "the same hit on an enemy without that weakness grants nothing");
    }

    /** The wearer's Break Effect after it deals one instance of damage to an enemy (weak to fire or not). */
    private static double breakEffectAfterHit(boolean fireWeak) {
        Character wearer = wearing(BANDITRY);
        Enemy target = dummy();
        // The fixture monster is naturally fire-weak, so the negative case has to take that away.
        target.setStanceWeak(java.util.Set.of());
        if (fireWeak) {
            target.setStanceWeak(java.util.Set.of(DamageElement.FIRE));
        }
        Battle battle = new Battle(List.of(wearer), List.of(target), new Random(0));
        battle.startBattle();
        double before = boostOf(wearer, AttributeType.BREAKING_EFFECT);

        battle.castImmediate(new DefaultSkill(WEARER, 1, 1), wearer, List.of(target));

        return boostOf(wearer, AttributeType.BREAKING_EFFECT) - before;
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /** A minimal memosprite for a master whose own file has none, placed the way summonMemosprite does. */
    private static Summon fixtureMemosprite(Battle battle, Character master) {
        Summon summon = SummonFactory.memosprite(master, new MemospriteSpec("fixture", "RelicAbilityBatchTest", null,
                List.of(new MemospriteSpec.Panel("HEALTH", 0.5, null),
                        new MemospriteSpec.Panel("SPEED", null, 999.0))));
        summon.setMaster(master);
        battle.allies.add(summon);
        battle.addRequestItems.add(summon);
        battle.processRequests();
        return summon;
    }

    /** The wearer at a stated speed, with set 311 on: the scoped boost it grants. */
    private static double damageBoostAtSpeed(double speed) {
        Character wearer = wearing(GLAMOTH);
        wearer.setAttribute(AttributeType.SPEED, new DoubleValue(speed));
        Battle battle = new Battle(List.of(wearer), List.of(dummy()), new Random(0));
        battle.startBattle();
        return boostOf(wearer, AttributeType.ALL_DAMAGE_TYPE_BOOST);
    }

    private static Character wearing(int setId) {
        return CharacterFactory.create(WEARER, LEVEL, true, null,
                RelicFactory.suit(setId, STAR, RELIC_LEVEL));
    }

    /** The wearer at a stated Effect RES, with the set on: her CRIT DMG (the party buff lands on her too). */
    private static double brokenKeelCritDamageAt(double effectRes) {
        Character wearer = wearing(BROKEN_KEEL);
        wearer.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(effectRes));
        Battle battle = new Battle(List.of(wearer), List.of(dummy()), new Random(0));
        battle.startBattle();
        return wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
    }

    private static double bareCritRate() {
        return CharacterFactory.create(WEARER, LEVEL).getAttribute(AttributeType.CRIT_CHANCE).get();
    }

    private static double bareCritDamage() {
        return CharacterFactory.create(WEARER, LEVEL).getAttribute(AttributeType.CRIT_ATTACK).get();
    }

    /** One settled hit with no crit and no weakness games, so two runs differ only by the rule under test. */
    private static double hit(Battle battle, Character attacker, Enemy target) {
        return battle.applyDamage(target,
                new Damage(attacker, target, DamageElement.PHYSICAL, DamageType.NORMAL, 1_000));
    }

    private static double boostOf(CanHit unit, AttributeType attribute) {
        return unit.getAttribute(attribute).get();
    }

    private static Enemy dummy() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }
}
