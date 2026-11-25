package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.MemospriteSpec;
import com.laosun.aluminium.data.RelicTriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.*;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.buff.StatModifierBuff;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.enemy.SummonFactory;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.DoubleValue;

/**
 * A content pass over the {@code Writable now:} backlog - five relic abilities whose every clause
 * already had a spelling, written out.
 *
 * <p><b>Why a content pass needs tests at all.</b> Each of these was "already expressible", which is exactly the
 * kind of change that can be wrong in ways nothing reports: the wrong attribute, the wrong tier, a number read
 * from the wrong row, or a stacking default that silently replaces instead of adding. Four of the five registered
 * reasons carried a figure that <b>no parameter supports</b> - 102's Basic-ATK share (12% vs param 0.1), 111's
 * energy (8 vs param 3), 310's threshold (50% vs param 0.3) and 313's per-stack CRIT DMG (which is why 313 is
 * still not authored) - so every number below is asserted against the file that was written, not against prose.
 *
 * <p>313 (无主荒星茨冈尼亚, Sigonia, the Unclaimed Desolation) stays registered, and its reason is rewritten: "when an enemy target is killed" needs "the one
 * who died is an ENEMY", and {@code KILL} fires for any death with the victim as {@code target} - the condition
 * DSL has no camp variable (F-5). A wrong number and a missing spelling in one entry.
 *
 * <p><b>Scenario grouping (L3 consolidation).</b> Each test method below is one scenario over one fixture
 * shape - the wearer alone, the wearer with a teammate, the wearer with its memosprite, or the wearer casting on
 * an ally - and each clause that used to stand alone is now an {@code assertAll} entry whose message starts with
 * the relic set number. No expected value, tolerance or wording was dropped in the fold.
 */
public class RelicAbilityBatchTest {
    private static final double EPS = 1e-6;

    /**
     * Himeko (姬子) - carries no rule file of her own, so only the set's rules are in play.
     */
    private static final int WEARER = 1003;
    /**
     * The "someone else" in the cases that need a second character. Deliberately the SAME rule-less character as
     * {@link #WEARER} rather than a colourful one: Guinaifen (桂乃芬, 1210) was the first draft and her own trace is
     * "对陷入灼烧状态的敌方目标造成的伤害提高20%" -- which is exactly the debuff the Pioneer case applies, so she
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
    /**
     * "对己方角色施放" family, authored with the `is_ally` condition.
     */
    private static final int MESSENGER = 114;
    private static final int WATCHMAKER = 118;
    private static final int SACERDOS = 121;
    private static final int SKILL_SLOT = 2;
    private static final int ULT_SLOT = 3;
    /**
     * Amphoreus, the Eternal Land (生命的翁法罗斯): the derived-value set (its bonus is a function of Max Energy).
     */
    private static final int AMPHOREUS = 328;
    /**
     * Izumo Gensei and Takama Divine Realm (出云显世与高天神国): the party-composition set (its CRIT Rate needs a teammate on the same Path).
     */
    private static final int IZUMO = 314;
    /**
     * Talia: Kingdom of Banditry (盗贼公国塔利亚): the weakness-gated set (its Break Effect needs a fire-weak enemy).
     */
    private static final int BANDITRY = 316;
    /**
     * 逐火者的航迹 (Revelry by the Sea): the damage-category set (its boost touches DoT only).
     */
    private static final int REVELRY = 322;

    // ==================================================================
    // 102 / 122 / 107 - the wearer alone: scoped damage boosts
    // ==================================================================

    /**
     * One lone wearer, three suits: the Basic ATK scope of 102, the Skill/Ultimate scope of 122 and the
     * next-attack window of 107.
     *
     * <p>Same fixture in all three - a single character, no teammate, no memosprite - so the three clauses are one
     * scenario; each assertion still names its suit, its parameter and the trap it guards against.
     */
    @Test
    public void theWearerAloneGainsItsScopedDamageBoosts() {
        Character musketeer = wearing(MUSKETEER);
        new Battle(List.of(musketeer), List.of(dummy()), BattleFixture.seeded()).startBattle();

        Character scholar = wearing(SCHOLAR);
        Battle scholarBattle = new Battle(List.of(scholar), List.of(dummy()), BattleFixture.seeded());
        scholarBattle.startBattle();
        double scholarSkill = boostOf(scholar, AttributeType.SKILL_DAMAGE_BOOST);
        double scholarUltimate = boostOf(scholar, AttributeType.ULTIMATE_DAMAGE_BOOST);
        scholarBattle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, scholar, null, 0, 0));
        double scholarSkillAfterUltimate = boostOf(scholar, AttributeType.SKILL_DAMAGE_BOOST);

        Character firesmith = wearing(FIRESMITH);
        Battle firesmithBattle = new Battle(List.of(firesmith), List.of(dummy()), BattleFixture.seeded());
        firesmithBattle.startBattle();
        double firesmithSkill = boostOf(firesmith, AttributeType.SKILL_DAMAGE_BOOST);
        // Measured as a delta: the relic's random sub-stats can carry FIRE_DAMAGE_BOOST themselves, which is
        // how the first draft of this case came out at 0.444.
        double fireBefore = boostOf(firesmith, AttributeType.FIRE_DAMAGE_BOOST);
        firesmithBattle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, firesmith, null, 0, 0));
        double fireAfterUltimate = boostOf(firesmith, AttributeType.FIRE_DAMAGE_BOOST);
        firesmithBattle.castImmediate(new DefaultSkill(WEARER, 1, 1), firesmith,
                List.of(firesmithBattle.enemyUnits().getFirst()));
        double fireAfterAttack = boostOf(firesmith, AttributeType.FIRE_DAMAGE_BOOST);

        Assertions.assertAll("102 - the Basic ATK scope (+10%), not every cast",
                () -> Assertions.assertEquals(0.1, boostOf(musketeer, AttributeType.BASIC_ATTACK_DAMAGE_BOOST), EPS,
                        "[102 basic ATK] param #2 is 0.1 -- the registered reason's 12% came from the English sentence"),
                () -> Assertions.assertEquals(0, boostOf(musketeer, AttributeType.SKILL_DAMAGE_BOOST), EPS,
                        "[102 not skills] 「普攻造成的伤害」 (DMG dealt by Basic ATKs) is one scope, not every cast"));

        Assertions.assertAll("122 - Skill/Ultimate +20%, and the extra +25% adds on top",
                () -> Assertions.assertEquals(0.2, scholarSkill, EPS, "[122 skill boost] param #1"),
                () -> Assertions.assertEquals(0.2, scholarUltimate, EPS, "[122 ultimate boost] param #1"),
                () -> Assertions.assertEquals(0.45, scholarSkillAfterUltimate, EPS,
                        "[122 extra skill DMG] 0.20 + 0.25: 「额外提高」 (raised by an extra amount) adds, which is what max_stacks: 2 is for"));

        Assertions.assertAll("107 - Skill +12%, then a fire boost for exactly one attack",
                () -> Assertions.assertEquals(0.12, firesmithSkill, EPS, "[107 skill boost] param #1"),
                () -> Assertions.assertEquals(fireBefore + 0.12, fireAfterUltimate, EPS,
                        "[107 fire boost after ultimate] param #2"),
                () -> Assertions.assertEquals(fireBefore, fireAfterAttack, EPS,
                        "[107 one attack only] 「下一次攻击」 (the next attack): the attack has happened, so the boost is gone"));
    }

    // ==================================================================
    // 310 / 311 / 328 - the wearer alone: threshold-gated bonuses
    // ==================================================================

    /**
     * One lone wearer with a single forcing attribute, three suits: 310's Effect RES threshold (a party CRIT DMG
     * buff that lands on the wearer too), 311's nested SPD tiers and 328's Max-Energy curve with its cap.
     *
     * <p>Same fixture shape in all three (one character, one attribute set before the battle), so the threshold
     * clauses are one scenario; each assertion still names its suit and its tier.
     */
    @Test
    public void theWearerAloneGainsItsThresholdGatedBonuses() {
        double keelAbove = brokenKeelCritDamageAt(0.35) - bareCritDamage();
        double keelBelow = brokenKeelCritDamageAt(0.25) - bareCritDamage();
        double glamothBelowFirst = damageBoostAtSpeed(130);
        double glamothSecond = damageBoostAtSpeed(135);
        double glamothTop = damageBoostAtSpeed(160);
        double amphoreusFortyOver = grantedDamageBoost(240);
        double amphoreusCapped = grantedDamageBoost(400);
        double amphoreusBelow = grantedDamageBoost(150);

        Assertions.assertAll("310 - EFFECT RES >= 30%: the whole side's CRIT DMG +10%",
                () -> Assertions.assertEquals(0.1, keelAbove, EPS,
                        "[310 above threshold] param #2 is 0.3 -- the registered reason said 50%, which no parameter supports"),
                () -> Assertions.assertEquals(0, keelBelow, EPS, "[310 below threshold] below it nothing is granted"));

        Assertions.assertAll("311 - the two SPD tiers are exclusive",
                () -> Assertions.assertEquals(0, glamothBelowFirst, EPS,
                        "[311 below first tier] below the first threshold"),
                () -> Assertions.assertEquals(0.12, glamothSecond, EPS, "[311 second tier] param #4"),
                () -> Assertions.assertEquals(0.18, glamothTop, EPS,
                        "[311 top tier] param #5 alone -- without the lower tier''s upper bound this would be 0.30"));

        Assertions.assertAll("328 - the Max-Energy curve and its 32% cap",
                () -> Assertions.assertEquals(0.002 * (240 - 200), amphoreusFortyOver, 1e-6,
                        "[328 forty over] 240 energy is 40 points over the threshold: 0.2% x 40 = 8%"),
                () -> Assertions.assertEquals(0.32, amphoreusCapped, 1e-6,
                        "[328 capped] 「最多提高#3[i]%」 (raises it by at most #3[i]%) -- past 360 the cap holds, and the lower tier is excluded by its own upper bound"),
                () -> Assertions.assertEquals(0, amphoreusBelow, 1e-6,
                        "[328 below threshold] below the threshold nothing is granted at all (and 0.002 x 150 - 0.4 would have been negative)"));
    }

    // ==================================================================
    // 316 - a weakness-gated buff
    // ==================================================================

    /**
     * 316 Talia: Kingdom of Banditry (盗贼公国塔利亚): hitting a <b>fire-weak</b> enemy raises the wearer's Break Effect; anyone else does not.
     *
     * <p>Note: Both sides in one case: the condition is the whole rule, and a rule that ignored the weakness would pass a
     * test that only checked the fire-weak enemy.
     */
    @Test
    public void theBanditryBreakEffectNeedsAFireWeakEnemy() {
        // 2026-10-19: the text says 40% for 1 turn; the shipped file said 0.2 / 2 turns and is fixed.
        double fireWeak = breakEffectAfterHit(true);
        double notFireWeak = breakEffectAfterHit(false);

        Assertions.assertAll("316 - the fire-weak gate",
                () -> Assertions.assertEquals(0.4, fireWeak, 1e-6,
                        "[316 fire-weak enemy] 「命中具有火属性弱点的敌方目标时」 (when hitting an enemy target with a Fire Weakness) -- +40% for 1 turn"),
                () -> Assertions.assertEquals(0, notFireWeak, 1e-6,
                        "[316 no fire weakness] the same hit on an enemy without that weakness grants nothing"));
    }

    // ==================================================================
    // 322 - a damage-CATEGORY boost (DoT only)
    // ==================================================================

    /**
     * 322 逐火者的航迹 (Revelry by the Sea): the DoT-only boost appears once ATK passes 2400, again at 3600, and not
     * below - and the <b>same</b> DoT tick hits harder once the wearer holds it.
     *
     * <p>Note: The two halves were separate cases and the pair is the point: an attribute nothing reads would look
     * exactly like a working rule, so both the granted modifier and the damage instance are asserted here, over the
     * same fixture (one lone wearer at a forced ATK) and the same clause.
     */
    @Test
    public void theRevelryDotBoostIsGrantedAndReachesTheTick() {
        double tierTwelve = dotBoostAtAttack(2500);
        double tierTwentyFour = dotBoostAtAttack(4000);
        double belowFirstTier = dotBoostAtAttack(2000);
        double plain = firstDotDamage(2000);
        double boosted = firstDotDamage(2500);

        Assertions.assertAll("322 - the DoT-only tiers and the tick they must reach",
                () -> Assertions.assertEquals(0.12, tierTwelve, 1e-6,
                        "[322 >= 2400] \"when it is greater than or equal to 2400 ... raise it by 12%\" (「大于等于 2400 时…提高 12%」)"),
                () -> Assertions.assertEquals(0.24, tierTwentyFour, 1e-6,
                        "[322 >= 3600] 「大于等于 3600 时…提高 24%」 (at 3600 or above ... raises it by 24%) -- the lower tier is excluded by its own upper bound"),
                () -> Assertions.assertEquals(0, belowFirstTier, 1e-6,
                        "[322 below 2400] below 2400 nothing is granted"),
                () -> Assertions.assertTrue(plain > 0,
                        "[322 DoT precondition] precondition: the DoT tick dealt damage (" + plain + ")"),
                () -> Assertions.assertTrue(boosted > plain,
                        "[322 DoT reaches the tick] 「使装备者造成的持续伤害额外提高 12%」 (raises the DoT the wearer deals by an extra 12%) must reach the tick (" + boosted + " vs " + plain + ")"));
    }

    // ==================================================================
    // 111 / 117 / 120 - the wearer with ONE teammate: only her own actions count
    // ==================================================================

    /**
     * The wearer with one rule-less teammate, three suits: 111's break energy, 117's damage against a debuffed
     * enemy and 120's Ultimate boost after a follow-up. In every clause the question is the same - does the rule
     * answer to the wearer, or to whoever on the field happens to act - so the clauses share a scenario.
     *
     * <p>The teammate is {@link #ALLY}, the same rule-less character as the wearer, rather than 1210: Guinaifen's
     * own trace boosts her damage against a burning target, which is exactly the state the 117 clause applies, so
     * she would have measured her own rule instead of the set's.
     */
    @Test
    public void theWearersRulesAnswerOnlyToHerOwnActions() {
        Character thief = wearing(THIEF);
        Character thiefAlly = CharacterFactory.create(ALLY, LEVEL);
        Battle thiefBattle = new Battle(List.of(thief, thiefAlly), List.of(dummy()), BattleFixture.seeded());
        thiefBattle.startBattle();
        double energyBefore = thief.getCurrentEnergy();
        thiefBattle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.BREAK, thiefAlly, dummy(), 0, 0));
        double energyAfterTeammateBreak = thief.getCurrentEnergy();
        thiefBattle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.BREAK, thief, dummy(), 0, 0));
        double energyAfterOwnBreak = thief.getCurrentEnergy();

        Character pioneer = wearing(PIONEER);
        pioneer.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0));
        Character pioneerAlly = CharacterFactory.create(ALLY, LEVEL);
        pioneerAlly.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0));
        Enemy debuffed = dummy();
        Enemy clean = dummy();
        Battle pioneerBattle = new Battle(List.of(pioneer, pioneerAlly), List.of(debuffed, clean),
                BattleFixture.seeded());
        pioneerBattle.startBattle();
        debuffed.getBuffManager().addBuff(new com.laosun.aluminium.buff.DotBuff(
                pioneer, DamageElement.FIRE, 1, 2));      // any debuff: the count is what the text asks for

        double onDebuffed = hit(pioneerBattle, pioneer, debuffed);
        double onClean = hit(pioneerBattle, pioneer, clean);
        // "装备者造成的伤害": the wearer's rule must not boost a teammate's hit on the same enemy.
        double teammateOnDebuffed = hit(pioneerBattle, pioneerAlly, debuffed);
        double teammateOnClean = hit(pioneerBattle, pioneerAlly, clean);

        Character valorous = wearing(VALOROUS);
        Character valorousAlly = CharacterFactory.create(ALLY, LEVEL);
        Battle valorousBattle = new Battle(List.of(valorous, valorousAlly), List.of(dummy()), BattleFixture.seeded());
        valorousBattle.startBattle();
        valorousBattle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.FOLLOW_UP, valorousAlly, dummy(), 0, 0));
        double ultimateAfterTeammateFollowUp = boostOf(valorous, AttributeType.ULTIMATE_DAMAGE_BOOST);
        valorousBattle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.FOLLOW_UP, valorous, dummy(), 0, 0));
        double ultimateAfterOwnFollowUp = boostOf(valorous, AttributeType.ULTIMATE_DAMAGE_BOOST);

        Assertions.assertAll("111 - only the wearer's own break pays",
                () -> Assertions.assertEquals(energyBefore, energyAfterTeammateBreak, EPS,
                        "[111 teammate's break] 「装备者击破」 (a break by the wearer): a teammate's break is not hers"),
                () -> Assertions.assertEquals(energyBefore + 3, energyAfterOwnBreak, EPS,
                        "[111 own break] param #2 is 3 -- the registered reason's 8 has no parameter behind it"));

        Assertions.assertAll("117 - +12% against a debuffed enemy, and only for the wearer",
                () -> Assertions.assertTrue(onClean > 0, "[117 precondition] precondition: the clean hit landed"),
                () -> Assertions.assertEquals(0.12, onDebuffed / onClean - 1, 1e-6,
                        "[117 debuffed enemy] param #1 is 0.12 and it applies to the instance being settled"),
                () -> Assertions.assertEquals(teammateOnClean, teammateOnDebuffed, EPS,
                        "[117 not the teammate] a rule on the wearer's table is not a rule on everyone's damage"));

        Assertions.assertAll("120 - only the wearer's own follow-up grants the Ultimate boost",
                () -> Assertions.assertEquals(0, ultimateAfterTeammateFollowUp, EPS,
                        "[120 teammate's follow-up] 「装备者施放追加攻击时」 (when the wearer casts a follow-up attack): a teammate's follow-up is not hers"),
                () -> Assertions.assertEquals(0.36, ultimateAfterOwnFollowUp, EPS,
                        "[120 own follow-up] param #2 is 0.36 -- the registered reason said 12%"));
    }

    // ==================================================================
    // 127 - the memosprite condition, with a teammate on the field
    // ==================================================================

    /**
     * 127 Three effects on one trigger, and the summon condition is what keeps the third reachable.
     *
     * <p>Without the memosprite the rule must do <b>nothing</b>, not throw: the effect that names
     * {@code target: "summon"} fails loudly when nothing is out, so "若装备者的忆灵在场" is load-bearing.
     */
    @Test
    public void theShatteredWorldNeedsItsMemospriteAndThenBoostsBoth() {
        Character wearer = wearing(SHATTERED_WORLD);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(wearer, ally), List.of(dummy()), BattleFixture.seeded());
        battle.startBattle();
        double wearerHp = wearer.getMaxHp();
        double allyBoost = boostOf(ally, AttributeType.ALL_DAMAGE_TYPE_BOOST);

        Assertions.assertAll("127 - with no memosprite out the rule does nothing at all",
                () -> Assertions.assertDoesNotThrow(() -> battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, wearer, null, 0, 0)),
                        "[127 no memosprite] no memosprite out: the condition keeps the summon-targeted effect from being reached"),
                () -> Assertions.assertEquals(wearerHp, wearer.getMaxHp(), EPS,
                        "[127 nothing granted] …so nothing was granted"));

        // A fixture memosprite rather than SummonFactory.memosprite(master): the wearer is deliberately the
        // rule-less character, and summoning 1413's or 1402's would drag their own rules into the measurement.
        // The engine's real summon path, not a hand-placed fixture: SUMMONED is fired for what
        // Battle.summon* places (it is recorded in justSummoned), so a fixture added straight to the roster
        // would announce nothing -- which the first draft of this case demonstrated by measuring 0.0.
        Summon evey = battle.summon(wearer, MONSTER, 1);
        battle.processRequests();                                // where SUMMONED is fired, by design
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, wearer, null, 0, 0));

        double bareHp = CharacterFactory.create(WEARER, LEVEL).getMaxHp();
        double hpDelta = wearer.getMaxHp() - wearerHp;
        boolean memospriteHasHpBuff = evey.getBuffManager().countBuffs(StatModifierBuff.class) > 0;
        double allyBoostAfter = boostOf(ally, AttributeType.ALL_DAMAGE_TYPE_BOOST);
        // the file states the sentence's disjunction as ONE duration with two ends. Asserted on the shipped
        // 4-piece rather than on a hand-made rule, because the shape of the JSON is half of what this guarantees.
        List<TriggerTable.CompiledRule> rules = RelicTriggerTables.of(SHATTERED_WORLD).at(4)
                .matching(TriggerEvent.SKILL_CAST,
                        new TriggerTable.TriggerContext(wearer, wearer, null, 0, 0, null, battle));

        Assertions.assertAll("127 - with the memosprite out, every half lands",
                () -> Assertions.assertEquals(0.24 * bareHp, hpDelta, 0.5,
                        "[127 wearer max HP +24%] 「装备者…生命上限提高 24%」 (the wearer's ... Max HP is raised by 24%) -- asserted as the DELTA, because a percentage modifier on a base "
                                + "attribute adds 24% of the character's base Max HP while the relic's own sub-stats "
                                + "already carry their own HP%"),
                () -> Assertions.assertTrue(memospriteHasHpBuff,
                        "[127 memosprite max HP] 「及其忆灵」 (and its memosprite): the memosprite carries its own copy of the Max HP buff"),
                () -> Assertions.assertEquals(allyBoost + 0.15, allyBoostAfter, EPS,
                        "[127 whole party +15%] 「我方全体造成的伤害提高 15%」 (all allies deal 15% more DMG) reaches a teammate too"),
                () -> Assertions.assertEquals(1, rules.size(),
                        "[127 one rule, two ends] the shipped 4-piece states the sentence's disjunction as one rule"),
                () -> {
                    for (EffectSpec effect : rules.getFirst().effects()) {
                        Assertions.assertEquals(List.of("next_attack", "next_skill"), effect.getUntil(),
                                "[127 duration] 「持续至装备者下次施放普攻或战技后」 (lasts until the wearer next casts a Basic ATK or Skill) -- a list, so a support Skill ends it too");
                    }
                });
    }

    // ==================================================================
    // 124 / 319 / 320 - "装备者及其忆灵" reaches a LATE arrival
    // ==================================================================

    /**
     * The memosprite half of a pre-battle effect lands when the memosprite arrives: 319 (HP >= 5000 -> CRIT DMG
     * +28%, and nothing below it), 124 (SPD < 95 -> CRIT Rate) and 320 (SPD >= 180 -> OUTGOING_HEALING_BOOST).
     *
     * <p>Why this shape is one scenario: every clause has the same fixture - one lone wearer, one forced
     * attribute, the engine's real summon path - and the same two observation points, the wearer at battle start
     * and the memosprite on arrival.
     *
     * <p>Why the second rule per tier is needed at all: `target: "summon"` cannot be filed at BATTLE_START
     * (nothing may be out, and the selector fails loudly rather than silently missing), so each tier carries a
     * second rule on SUMMONED. Both halves are asserted: the wearer gets hers at the start, the memosprite gets
     * hers when it appears - including for a wearer whose kit summons it mid-fight, which is the case the single
     * BATTLE_START rule could not serve at all.
     */
    @Test
    public void preBattleMemospriteEffectsLandOnArrivalAndRespectTheirThresholds() {
        Character demesne = wearing(SERENE_DEMESNE);             // 谧宁拾骨地 (Bone Collection's Serene Demesne): HP >= 5000 -> CRIT DMG +28%
        demesne.setAttribute(AttributeType.HEALTH, new DoubleValue(6_000));
        Battle demesneBattle = new Battle(List.of(demesne), List.of(dummy()), BattleFixture.seeded());
        demesneBattle.startBattle();
        double demesneWearerBoost = boostOf(demesne, AttributeType.CRIT_ATTACK);
        Summon demesneSprite = demesneBattle.summon(demesne, MONSTER, 1);
        demesneBattle.processRequests();                         // where SUMMONED is fired, by design
        double demesneSpriteBoost = boostOf(demesneSprite, AttributeType.CRIT_ATTACK);

        Character demesneBelow = wearing(SERENE_DEMESNE);
        demesneBelow.setAttribute(AttributeType.HEALTH, new DoubleValue(4_000));
        Battle demesneBelowBattle = new Battle(List.of(demesneBelow), List.of(dummy()), BattleFixture.seeded());
        demesneBelowBattle.startBattle();
        Summon demesneBelowSprite = demesneBelowBattle.summon(demesneBelow, MONSTER, 1);
        demesneBelowBattle.processRequests();                    // where SUMMONED is fired, by design
        double demesneBelowSpriteBoost = boostOf(demesneBelowSprite, AttributeType.CRIT_ATTACK);

        Character poet = wearing(POET);                          // 哀歌覆国的诗人 (Poet of Mourning Collapse): SPD < 110 / < 95
        poet.setAttribute(AttributeType.SPEED, new DoubleValue(90));
        Battle poetBattle = new Battle(List.of(poet), List.of(dummy()), BattleFixture.seeded());
        poetBattle.startBattle();
        Summon poetSprite = poetBattle.summon(poet, MONSTER, 1);
        poetBattle.processRequests();                            // where SUMMONED is fired, by design
        // Measured on the memosprite: its panel states HEALTH and SPEED only, so its CRIT Rate IS the buff
        // (0.32). The wearer's own value is unusable as an absolute -- the relic's random sub-stats carry
        // CRIT Rate of their own, which is how the first draft read 0.69394.
        double poetSpriteCritRate = boostOf(poetSprite, AttributeType.CRIT_CHANCE);
        double poetWearerCritRate = boostOf(poet, AttributeType.CRIT_CHANCE);

        Character tree = wearing(RAPT_BROODING);                 // 渊思寂虑的巨树 (Giant Tree of Rapt Brooding): SPD >= 135 / >= 180
        tree.setAttribute(AttributeType.SPEED, new DoubleValue(190));
        Battle treeBattle = new Battle(List.of(tree), List.of(dummy()), BattleFixture.seeded());
        treeBattle.startBattle();
        Summon treeSprite = treeBattle.summon(tree, MONSTER, 1);
        treeBattle.processRequests();                            // where SUMMONED is fired, by design
        double treeWearerHealing = boostOf(tree, AttributeType.OUTGOING_HEALING_BOOST);
        double treeSpriteHealing = boostOf(treeSprite, AttributeType.OUTGOING_HEALING_BOOST);

        Assertions.assertAll("124 / 319 / 320 - 「装备者及其忆灵」 reaches a late arrival",
                () -> Assertions.assertTrue(demesneWearerBoost > bareCritDamage(),
                        "[319 wearer at start] the wearer was granted hers at the start"),
                () -> Assertions.assertEquals(0.28, demesneSpriteBoost, EPS,
                        "[319 memosprite on arrival] and the memosprite gets the same 28% when it arrives -- not at battle start, when it was not "
                                + "there yet. (Asserted as the raw value, not against the wearer's total: hers includes "
                                + "the base 0.5 CRIT DMG every character has, and the memosprite's panel gives it none.)"),
                () -> Assertions.assertEquals(0, demesneBelowSpriteBoost, EPS,
                        "[319 below 5000] 4000 is below 「大于等于 5000 点」 (at least 5000 points) -- and HEALTH is a base attribute, so the literal is absolute"),
                () -> Assertions.assertEquals(0.32, poetSpriteCritRate, 1e-6,
                        "[124 below 95] 「小于 95」 (below 95) alone: without the upper tier's exclusion this would be 0.52"),
                () -> Assertions.assertTrue(poetWearerCritRate > bareCritRate(),
                        "[124 wearer at start] and the wearer was granted hers at the start"),
                () -> Assertions.assertEquals(0.2, treeWearerHealing, 1e-6,
                        "[320 upper tier on the wearer] the upper tier on the wearer, with no lower tier added on top"),
                () -> Assertions.assertEquals(0.2, treeSpriteHealing, 1e-6,
                        "[320 on the memosprite] and on the memosprite"));
    }

    // ==================================================================
    // 114 / 118 / 121 - "cast on an allied character" (`target is_ally`)
    // ==================================================================

    /**
     * The `is_ally` family: one wearer, one 1210 teammate, one cast aimed at her.
     *
     * <p>114 Messenger Traversing Hackerspace (骇域漫游的信使): the wearer's Ultimate <b>on an ally</b> speeds the
     * whole side up - and aimed at an enemy it does nothing at all. Both halves are in this scenario on purpose:
     * the contrast is the whole reason the condition exists (`actor == self` alone would fire for a damaging
     * ultimate too), and a test that only checked the ally half would pass for a rule that never looks at the
     * target. 118 Watchmaker, Master of Dream Machinations (梦游者钟表匠) is the same trigger with a ratio
     * attribute (+30% Break Effect, 2 turns); 121 Sacerdos' Relived Ordeal (祭司的旧日祭礼) is the Skill half,
     * which raises THAT ally's CRIT DMG and stacks to the cap it states.
     */
    @Test
    public void castsOnAnAllyReachTheAimedAllyAndTheParty() {
        Character messenger = wearing(MESSENGER);
        Character messengerAlly = CharacterFactory.create(1210, LEVEL);
        Battle messengerBattle = new Battle(List.of(messenger, messengerAlly), List.of(dummy()), BattleFixture.seeded());
        messengerBattle.startBattle();
        double speedBefore = boostOf(messengerAlly, AttributeType.SPEED);

        messengerBattle.castImmediate(new DefaultSkill(WEARER, ULT_SLOT, 1), messenger, List.of(messengerAlly));
        double speedAfterAllyUltimate = boostOf(messengerAlly, AttributeType.SPEED);

        Battle aimedAtEnemy = new Battle(List.of(wearing(MESSENGER), CharacterFactory.create(1210, LEVEL)),
                List.of(dummy()), BattleFixture.seeded());
        aimedAtEnemy.startBattle();
        Character second = aimedAtEnemy.characters().get(1);
        double secondBefore = boostOf(second, AttributeType.SPEED);

        aimedAtEnemy.castImmediate(new DefaultSkill(WEARER, ULT_SLOT, 1), aimedAtEnemy.characters().getFirst(),
                List.of(aimedAtEnemy.enemyUnits().getFirst()));
        double secondAfterEnemyAimedUltimate = boostOf(second, AttributeType.SPEED);

        Character watchmaker = wearing(WATCHMAKER);
        Character watchmakerAlly = CharacterFactory.create(1210, LEVEL);
        Battle watchmakerBattle = new Battle(List.of(watchmaker, watchmakerAlly), List.of(dummy()),
                BattleFixture.seeded());
        watchmakerBattle.startBattle();
        double breakEffectBefore = boostOf(watchmakerAlly, AttributeType.BREAKING_EFFECT);

        watchmakerBattle.castImmediate(new DefaultSkill(WEARER, ULT_SLOT, 1), watchmaker, List.of(watchmakerAlly));
        double breakEffectAfter = boostOf(watchmakerAlly, AttributeType.BREAKING_EFFECT);

        Character sacerdos = wearing(SACERDOS);
        Character sacerdosAlly = CharacterFactory.create(1210, LEVEL);
        Battle sacerdosBattle = new Battle(List.of(sacerdos, sacerdosAlly), List.of(dummy()), BattleFixture.seeded());
        sacerdosBattle.startBattle();
        double critDamageBefore = boostOf(sacerdosAlly, AttributeType.CRIT_ATTACK);

        sacerdosBattle.castImmediate(new DefaultSkill(WEARER, SKILL_SLOT, 1), sacerdos, List.of(sacerdosAlly));
        double critDamageAfterOneCast = boostOf(sacerdosAlly, AttributeType.CRIT_ATTACK);
        sacerdosBattle.castImmediate(new DefaultSkill(WEARER, SKILL_SLOT, 1), sacerdos, List.of(sacerdosAlly));
        double critDamageAfterTwoCasts = boostOf(sacerdosAlly, AttributeType.CRIT_ATTACK);

        Assertions.assertAll("114 - the Ultimate hastes the party only when it targets an ally",
                () -> Assertions.assertEquals(speedBefore * 0.12, speedAfterAllyUltimate - speedBefore, 1e-6,
                        "[114 aimed at an ally] 「我方全体速度提高#1[i]%」 (all allies' SPD is raised by #1[i]%) -- #1 = 0.12, a share of the target's base speed"),
                () -> Assertions.assertEquals(secondBefore, secondAfterEnemyAimedUltimate, 1e-6,
                        "[114 aimed at an enemy] aimed at an ENEMY: 「对己方角色」 (on one of our own characters) does not hold, so nothing is granted"));

        Assertions.assertAll("118 - the same trigger, a ratio attribute",
                () -> Assertions.assertEquals(0.3, breakEffectAfter - breakEffectBefore, 1e-6,
                        "[118 break effect] 「击破特攻提高#1[i]%」 (Break Effect is raised by #1[i]%) -- a ratio attribute, so 0.3 is an absolute +30%"));

        Assertions.assertAll("121 - the Skill raises THAT ally's CRIT DMG, and stacks twice",
                () -> Assertions.assertEquals(0.18, critDamageAfterOneCast - critDamageBefore, 1e-6,
                        "[121 aimed ally] 「使该目标暴击伤害提高#1[i]%」 (raises that target's CRIT DMG by #1[i]%) -- #1 = 0.18, on the unit the cast was AIMED at"),
                () -> Assertions.assertEquals(0.36, critDamageAfterTwoCasts - critDamageBefore, 1e-6,
                        "[121 two stacks] 「最多叠加#3[i]层」 -- two casts, two stacks (the engine's default would have REPLACED the first)"));
    }

    // ==================================================================
    // 314 - the party-composition condition
    // ==================================================================

    /**
     * 314 Izumo Gensei and Takama Divine Realm (出云显世与高天神国): the CRIT Rate arrives only when a teammate walks the wearer's Path.
     *
     * <p>Note: The value is read off the <b>modifier the rule granted</b>, not off the resolved attribute: the wearer is
     * built with a relic suit, whose random sub-stats may carry CRIT Rate of their own (relic sub-stats become
     * attributes at build time, so a `StatModifierBuff` on that attribute can only have come from a rule).
     */
    @Test
    public void theIzumoCritRateNeedsATeammateOnTheSamePath() {
        double samePath = grantedCritRateWith(1013);
        double otherPath = grantedCritRateWith(1210);

        Assertions.assertAll("314 - the same-Path teammate condition",
                () -> Assertions.assertEquals(0.12, samePath, 1e-6,
                        "[314 same Path] Herta (黑塔) is Erudition (erudition), like the wearer: \"if there is at least one ally on the same Path as the wearer\" (「若至少存在一名与装备者命途相同的队友」)"),
                () -> Assertions.assertEquals(0, otherPath, 1e-6,
                        "[314 other Path] Guinaifen (桂乃芬) is Nihility (虚无): the condition does not hold, so nothing is granted"));
    }

    /**
     * The modifier the rule granted on all-damage boost, or 0 when it granted none.
     */
    private static double grantedDamageBoost(double maxEnergy) {
        Character wearer = wearing(AMPHOREUS);
        wearer.setMaxEnergy(maxEnergy);
        Battle battle = new Battle(List.of(wearer), List.of(dummy()), BattleFixture.seeded());
        battle.startBattle();

        for (com.laosun.aluminium.buff.StatModifierBuff buff
                : wearer.getBuffManager().allBuffsOf(com.laosun.aluminium.buff.StatModifierBuff.class)) {
            if (buff.getAttribute() == AttributeType.ALL_DAMAGE_TYPE_BOOST) {
                return buff.getValue();
            }
        }
        return 0;
    }

    /**
     * The CRIT Rate modifier the suit granted, or 0 when it granted none.
     */
    private static double grantedCritRateWith(int allyCid) {
        Character wearer = wearing(IZUMO);
        Character ally = CharacterFactory.create(allyCid, LEVEL);
        Battle battle = new Battle(List.of(wearer, ally), List.of(dummy()), BattleFixture.seeded());
        battle.startBattle();

        for (com.laosun.aluminium.buff.StatModifierBuff buff
                : wearer.getBuffManager().allBuffsOf(com.laosun.aluminium.buff.StatModifierBuff.class)) {
            if (buff.getAttribute() == AttributeType.CRIT_CHANCE) {
                return buff.getValue();
            }
        }
        return 0;
    }

    /**
     * The wearer's Break Effect after it deals one instance of damage to an enemy (weak to fire or not).
     */
    private static double breakEffectAfterHit(boolean fireWeak) {
        Character wearer = wearing(BANDITRY);
        Enemy target = dummy();
        // The fixture monster is naturally fire-weak, so the negative case has to take that away.
        target.setStanceWeak(java.util.Set.of());
        if (fireWeak) {
            target.setStanceWeak(java.util.Set.of(DamageElement.FIRE));
        }
        Battle battle = new Battle(List.of(wearer), List.of(target), BattleFixture.seeded());
        battle.startBattle();
        double before = boostOf(wearer, AttributeType.BREAKING_EFFECT);

        battle.castImmediate(new DefaultSkill(WEARER, 1, 1), wearer, List.of(target));

        return boostOf(wearer, AttributeType.BREAKING_EFFECT) - before;
    }

    /**
     * How much HP one DoT tick takes off the enemy, with the wearer at this ATK.
     */
    private static double firstDotDamage(double attack) {
        Character wearer = wearing(REVELRY);
        wearer.setAttribute(AttributeType.ATTACK, new DoubleValue(attack));
        Enemy enemy = dummy();
        Battle battle = new Battle(List.of(wearer), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        enemy.getBuffManager().addBuff(
                new com.laosun.aluminium.buff.DotBuff(wearer, DamageElement.FIRE, 100, 3));
        double before = enemy.getCurrentHp();

        for (int guard = 0; guard < 40; guard++) {
            battle.stepForward();
            if (battle.queue().getCurrentActor().getCanHit() == enemy) {
                battle.beforeMove();                  // where the DoT ticks (Battle.tickDots)
                break;
            }
            battle.afterMove();
        }
        return before - enemy.getCurrentHp();
    }

    /**
     * The DoT-boost modifier the suit granted to a wearer with this ATK, or 0 when it granted none.
     */
    private static double dotBoostAtAttack(double attack) {
        Character wearer = wearing(REVELRY);
        wearer.setAttribute(AttributeType.ATTACK, new DoubleValue(attack));
        Battle battle = new Battle(List.of(wearer), List.of(dummy()), BattleFixture.seeded());
        battle.startBattle();

        for (com.laosun.aluminium.buff.StatModifierBuff buff
                : wearer.getBuffManager().allBuffsOf(com.laosun.aluminium.buff.StatModifierBuff.class)) {
            if (buff.getAttribute() == AttributeType.DOT_DAMAGE_BOOST) {
                return buff.getValue();
            }
        }
        return 0;
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /**
     * A minimal memosprite for a master whose own file has none, placed the way summonMemosprite does.
     */
    private static Summon fixtureMemosprite(Battle battle, Character master) {
        Summon summon = SummonFactory.memosprite(master, new MemospriteSpec("fixture", "RelicAbilityBatchTest", null,
                List.of(new MemospriteSpec.Panel("HEALTH", 0.5, null),
                        new MemospriteSpec.Panel("SPEED", null, 999.0))));
        summon.setMaster(master);
        battle.addCombatant(summon);
        battle.processRequests();
        return summon;
    }

    /**
     * The wearer at a stated speed, with set 311 on: the scoped boost it grants.
     */
    private static double damageBoostAtSpeed(double speed) {
        Character wearer = wearing(GLAMOTH);
        wearer.setAttribute(AttributeType.SPEED, new DoubleValue(speed));
        Battle battle = new Battle(List.of(wearer), List.of(dummy()), BattleFixture.seeded());
        battle.startBattle();
        return boostOf(wearer, AttributeType.ALL_DAMAGE_TYPE_BOOST);
    }

    private static Character wearing(int setId) {
        return CharacterFactory.create(WEARER, LEVEL, true, null,
                RelicFactory.suit(setId, STAR, RELIC_LEVEL));
    }

    /**
     * The wearer at a stated Effect RES, with the set on: her CRIT DMG (the party buff lands on her too).
     */
    private static double brokenKeelCritDamageAt(double effectRes) {
        Character wearer = wearing(BROKEN_KEEL);
        wearer.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(effectRes));
        Battle battle = new Battle(List.of(wearer), List.of(dummy()), BattleFixture.seeded());
        battle.startBattle();
        return wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
    }

    private static double bareCritRate() {
        return CharacterFactory.create(WEARER, LEVEL).getAttribute(AttributeType.CRIT_CHANCE).get();
    }

    private static double bareCritDamage() {
        return CharacterFactory.create(WEARER, LEVEL).getAttribute(AttributeType.CRIT_ATTACK).get();
    }

    /**
     * One settled hit with no crit and no weakness games, so two runs differ only by the rule under test.
     */
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
