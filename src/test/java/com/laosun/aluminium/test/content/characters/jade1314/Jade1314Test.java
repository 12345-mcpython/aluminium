package com.laosun.aluminium.test.content.characters.jade1314;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.buff.StackBuff;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1314 翡翠 (Jade): one canonical judge for the character -- her Skill's 【收债人】 (debt collector) clause, the 【当品】
 * (Pawned Asset) counter she ships, the traces and Eidolon rank that read it, and the talent charge those Eidolons extend.
 *
 * <p>This file merges the former {@code JadeCollectorAttackTest}, {@code JadeDebtCollectorTest},
 * {@code JadeEidolonThresholdTest}, {@code JadeGoodsAttackTest}, {@code JadeLiveGoodsTest},
 * {@code JadeReverseRepoTest}, {@code JadeStackTest} and {@code JadeTalentAndEidolonsTest}: every case, every assertion and
 * every helper moved unchanged, and each former class note now sits on the case it explains.
 *
 * <p><b>Why the talent comes before its Eidolons.</b> Both Eidolon clauses in that half are ABOUT the talent's follow-up
 * (「天赋的追加攻击伤害提高 32%」) and about the charge it spends (「额外获得 1/2 点充能」), so writing them on an unwritten
 * talent would have been a rule with nothing to amend. The charge is read through her own resource, the follow-up through the
 * enemy's HP: 「每击中 1 名」 is a PER-HIT gain, so one attack of two hits must pay twice.
 */
public class Jade1314Test {

    private static final int JADE = 1314;
    private static final int ALLY = 1001;
    /**
     * The talent/eidolon half's ally: that fixture was written against 1002, so it keeps its own unit rather than being
     * folded onto {@link #ALLY} -- a different ally is a different panel, and a pure merge may not swap either.
     */
    private static final int TALENT_ALLY = 1002;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-9;
    private static final String COLLECTOR = "收债人";
    private static final String GOODS = "当品";
    private static final String CHARGE = "充能";

    // ==================================================================
    // The Skill, first clause: 指定我方单体成为【收债人】, the +30 SPEED,
    // and the duration spent on HER turn boundaries
    // ==================================================================

    /**
     * Note: 1314 翡翠's Skill, first clause: 「指定我方单体成为【收债人】，使其速度提高 30 点，持续 3 回合…若翡翠成为【收债人】时，
     * 无法获得速度提高效果…翡翠每回合开始时【收债人】状态持续回合数减 1」.
     *
     * <p>Three things are pinned: the ally becomes the collector and is faster by 30 POINTS (not 30%), her own case gets the state
     * but not the speed, and the duration is spent on HER turn boundaries ({@code ticks_on: "self"}), not the ally's.
     */
    @Test
    public void anAllyBecomesTheCollectorAndGetsThirtySpeed() {
        DebtFixture f = new DebtFixture();
        double before = f.ally.getAttribute(AttributeType.SPEED).get();
        f.skill(f.ally);

        Assertions.assertTrue(f.ally.getBuffManager().hasState(COLLECTOR), "「指定我方单体成为【收债人】」");
        Assertions.assertEquals(before + 30, f.ally.getAttribute(AttributeType.SPEED).get(), 1e-6,
                "「速度提高 30 点」 - points, not 30%");
    }

    @Test
    public void sheCanBeTheCollectorButGetsNoSpeed() {
        DebtFixture f = new DebtFixture();
        double before = f.jade.getAttribute(AttributeType.SPEED).get();
        f.skill(f.jade);

        Assertions.assertTrue(f.jade.getBuffManager().hasState(COLLECTOR), "the state is applied to her too");
        Assertions.assertEquals(before, f.jade.getAttribute(AttributeType.SPEED).get(), 1e-6,
                "「若翡翠成为【收债人】时，无法获得速度提高效果」");
    }

    @Test
    public void herTurnSpendsTheCollectorsDuration() {
        DebtFixture f = new DebtFixture();
        f.skill(f.ally);
        int before = f.ally.getBuffManager().allBuffsOf(StateBuff.class).getFirst().remainingTurns();
        Assertions.assertEquals(3, before, "precondition: 3 turns");

        // Three of HER turn boundaries (early + after). The state sits on the ally, its clock on Jade.
        for (int i = 0; i < 3; i++) {
            f.ally.getBuffManager().tickForeign(f.jade, true);
            f.ally.getBuffManager().tickForeign(f.jade, false);
        }
        Assertions.assertFalse(f.ally.getBuffManager().hasState(COLLECTOR),
                "「翡翠每回合开始时【收债人】状态持续回合数减 1」 - 3 turns spent on HER boundaries");
    }

    // ==================================================================
    // The Skill, second clause: the collector's extra hit and the 2% HP
    // it costs the collector
    // ==================================================================

    /**
     * The collector's attack carries an extra 25%-of-Jade hit, and the collector pays 2% of its own Max HP.
     *
     * <p>Note: 1314 翡翠's Skill, second clause: 「【收债人】施放攻击后，对每个击中的敌方目标造成 1 次等同于翡翠 25% 攻击力的量子属性附加伤害，
     * 并消耗【收债人】生命上限 2% 的生命值…若翡翠成为【收债人】时…攻击后不消耗生命值」.
     */
    @Test
    public void theCollectorsAttackCarriesAHitAndCostsHp() {
        CollectorFixture f = new CollectorFixture();
        f.makeCollector(f.ally);
        double allyHpBefore = f.ally.getCurrentHp();
        double enemyBefore = f.enemy.getCurrentHp();

        f.attack(f.ally);

        double cost = allyHpBefore - f.ally.getCurrentHp();
        System.out.println("[1314] cost=" + cost + " expected=" + (0.02 * f.ally.getMaxHp()));
        Assertions.assertEquals(0.02 * f.ally.getMaxHp(), cost, 1e-6,
                "「消耗【收债人】生命上限 2% 的生命值」");

        double loss = enemyBefore - f.enemy.getCurrentHp();
        double plainLoss = new CollectorFixture().attackLoss();
        Assertions.assertTrue(loss > plainLoss, "「每个击中的敌方目标…附加伤害」 - the attack is bigger");
    }

    /**
     * When JADE is the collector, she pays no HP.
     */
    @Test
    public void jadeHerselfPaysNothing() {
        CollectorFixture f = new CollectorFixture();
        f.makeCollector(f.jade);
        double before = f.jade.getCurrentHp();
        f.attack(f.jade);
        Assertions.assertEquals(before, f.jade.getCurrentHp(), 1e-9,
                "「若翡翠成为【收债人】时…攻击后不消耗生命值」");
    }

    // ==================================================================
    // The 【当品】 counter: the trace's one layer, the technique's fifteen,
    // and 2.40% critical damage per layer
    // ==================================================================

    /**
     * Note: 1314 Jade (翡翠)'s [当品]: one layer at the enemy's battle entry, fifteen from her technique, and 2.40% critical damage each.
     *
     * <p>Both the counter's name and the values come from the data; the technique half is measured with and without
     * `markTechniqueUsed`, which is what its `self has_state Technique (秘技)` gate reads.
     */
    @Test
    public void theTechniqueGrantsFifteenAndTheTraceOne() {
        Assertions.assertEquals(1, layers(false), "trace (行迹): one layer when a battle starts");
        Assertions.assertEquals(16, layers(true), "technique (秘技): fifteen more, so sixteen in total");
    }

    /**
     * 2.40% per layer.
     *
     * <p>Note: The absolute delta carries the unit's own inherent +0.5 critical damage (measured: 0.524 for one layer, where
     * the clause owns 0.024), so the clause's share is pinned twice: the measured one-layer total, and the difference
     * between sixteen layers and one, which is 15 x 2.4% and does not care what the unit carries.
     */
    @Test
    public void eachLayerAddsTwoPointFourPercentCriticalDamage() {
        Assertions.assertEquals(0.524, criticalDamage(false), EPS, "one layer on top of the inherent 0.5");
        Assertions.assertEquals(0.36, criticalDamage(true) - criticalDamage(false), EPS,
                "fifteen more layers are 15 x 2.40%");
    }

    @Test
    public void eachLayerAddsHalfAPercentAttack() {
        StackFixture one = fixture(false);
        StackFixture sixteen = fixture(true);
        double base = one.jade.getAttribute(AttributeType.ATTACK).baseValue();
        double delta = sixteen.jade.getAttribute(AttributeType.ATTACK).get()
                - one.jade.getAttribute(AttributeType.ATTACK).get();
        Assertions.assertEquals(0.075 * base, delta, base * 1e-6,
                "fifteen more layers are 15 x 0.50% of the base");
    }

    // ==================================================================
    // The 逆回购 trace: one layer per enemy that ENTERS the battle
    // ==================================================================

    /**
     * Note: 1314 翡翠's trace 逆回购, the half the engine can already state: 「在敌方目标进入战斗时，翡翠获得 1 层【当品】」.
     *
     * <p>The pre-placed roster is covered by the BATTLE_START rule (that is "entered battle" too), so this judge is about a
     * LATER arrival: {@code ENEMY_ENTERED} adds one layer, and only then.
     */
    @Test
    public void aNewEnemyPutsOneLayerOnHer() {
        Character jade = CharacterFactory.create(JADE, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(jade, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        battle.startBattle();
        int atStart = jade.getBuffManager().stacksOf(GOODS);

        battle.addEnemy(BattleFixture.monster());
        int afterEntry = jade.getBuffManager().stacksOf(GOODS);
        System.out.println("[1314] goods " + atStart + " -> " + afterEntry);
        Assertions.assertEquals(atStart + 1, afterEntry, "「在敌方目标进入战斗时，翡翠获得 1 层【当品】」");

        battle.addEnemy(BattleFixture.monster());
        Assertions.assertEquals(afterEntry + 1, jade.getBuffManager().stacksOf(GOODS),
                "every entry pays, not only the first");
    }

    // ==================================================================
    // The 绝当品 trace: 0.50% ATK per layer, read live
    // ==================================================================

    /**
     * Ten layers are +5% ATK, and the buff follows a later change.
     *
     * <p>Note: 1314 翡翠's trace 绝当品: 「每层【当品】额外使翡翠的攻击力提高 0.50%」.
     *
     * <p>The counter lives on HER, and it rises during the battle - so the modifier has to read the count live
     * ({@code per_stack_live}); a snapshot would freeze her ATK at the value it had when the aura landed.
     */
    @Test
    public void everyGoodsLayerRaisesHerAttack() {
        Battle battle = battle();
        Character jade = battle.characters().getFirst();
        double before = jade.getAttribute(AttributeType.ATTACK).get();
        // The share is of the ATTRIBUTE's base, and her panel already sits above it (weapon/relic share), so the DELTA is
        // the honest measurement - a ratio against the panel would read +4.2% for a +5% share.
        double base = jade.getAttribute(AttributeType.ATTACK).baseValue();

        addGoods(jade, 10);
        double ten = jade.getAttribute(AttributeType.ATTACK).get();
        System.out.println("[1314] base=" + base + " before=" + before + " 10 layers=" + ten);
        Assertions.assertEquals(0.005 * 10 * base, ten - before, 1e-6, "「每层【当品】额外使攻击力提高 0.50%」");

        addGoods(jade, 10);
        double twenty = jade.getAttribute(AttributeType.ATTACK).get();
        Assertions.assertEquals(0.005 * 10 * base, twenty - ten, 1e-6, "the LIVE count follows a later change");
    }

    // ==================================================================
    // The same 0.50% clause measured as a share, with the auras NOT
    // re-applied when layers arrive
    // ==================================================================

    /**
     * Four more layers mean four more 0.50% on ATTACK and four more 2.40% on CRIT DMG.
     *
     * <p>Note: "每层[当品]额外使翡翠的攻击力提高 0.50%" follows the layers.
     *
     * <p>Measured before: the clause had no spelling at all -- ATTACK is a flat attribute, the derived form yields an absolute
     * number and a plain modifier yields ONE layer's share. Both of her per_stack clauses also froze the count at attach time,
     * so layers gained later did not follow. With `per_stack_live` the share is re-read.
     */
    @Test
    public void theAurasFollowTheLayers() {
        Character jade = CharacterFactory.create(JADE, LEVEL, false, null, null, 0);
        Battle battle = new Battle(List.of(jade),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        int start = jade.getBuffManager().stacksOf(GOODS);
        Assertions.assertTrue(start > 0, "precondition: her rules grant the first layers");
        double attackShare = share(jade, AttributeType.ATTACK);
        // CRIT DMG's base is zero (the 50% everyone starts with is itself a modifier), so its reading is absolute
        double critBefore = jade.getAttribute(AttributeType.CRIT_ATTACK).get();
        System.out.println("[jade] layers=" + start + " attackShare=" + attackShare);

        // four more layers, added directly: the auras are NOT re-applied
        for (int i = 0; i < 4; i++) {
            jade.getBuffManager().addBuff(new StackBuff(GOODS, 9, true, 50));
        }
        Assertions.assertEquals(start + 4, jade.getBuffManager().stacksOf(GOODS));

        double attackAfter = share(jade, AttributeType.ATTACK);
        double critAfter = jade.getAttribute(AttributeType.CRIT_ATTACK).get();
        System.out.println("[jade] after -> attackShare=" + attackAfter);
        Assertions.assertEquals(0.005 * 4, attackAfter - attackShare, 1e-9,
                "the attack aura has to follow four more layers");
        System.out.println("[jade] crit " + critBefore + " -> " + critAfter);
        Assertions.assertEquals(0.024 * 4, critAfter - critBefore, 1e-9,
                "the derived crit-damage clause follows too, in absolute units");
    }

    // ==================================================================
    // Eidolon 2: the 15-layer threshold on the same counter
    // ==================================================================

    /**
     * Note: 1314 Jade (翡翠)'s 星魂 (Eidolon) 2: "when [当品] stacks to 15 layers, Jade's CRIT Rate is increased by 18%" -- a threshold on the counter she now ships.
     *
     * <p>Two ways, and the difference is the whole clause: with the technique her battle-start total is sixteen, so the
     * threshold holds; without it she has one layer, so nothing is stated.
     */
    @Test
    public void sixteenLayersPassTheThresholdAndOneDoesNot() {
        double withTechnique = critChance(true);
        double without = critChance(false);
        // Note: A difference, not an absolute: the unit carries the game's inherent +0.05 crit chance, so `get() - baseValue()`
        // mixes the two (measured: 0.23 where the clause owns 0.18).
        Assertions.assertEquals(0.18, withTechnique - without, EPS,
                "at 15 stacks of Pawned Asset (【当品】), CRIT Rate is raised by 18%");
        // The absence of the clause still reads the game's inherent 5% crit chance, so the control is 0.05.
        Assertions.assertEquals(0.05, without, EPS, "one layer is below the threshold, so only the inherent remains");
    }

    // ==================================================================
    // The talent: 「每击中 1 名敌方目标均可获得 1 点充能」 -> 8 charge ->
    // the follow-up, and the Eidolons built on it
    // ==================================================================

    /**
     * 「每击中 1 名敌方目标均可获得 1 点充能」 - and it is per hit, not per attack.
     */
    @Test
    public void theTalentPaysOneChargePerHit() {
        Run run = new Run(0);
        run.attack(run.jade, 2);
        Assertions.assertEquals(2, run.charge(), "two targets hit, two points of charge");
        run.attack(run.jade, 1);
        Assertions.assertEquals(3, run.charge(), "a third hit is a third point");
    }

    /**
     * 「翡翠**或**成为【收债人】的目标」 - the collector's attacks pay her charge too.
     */
    @Test
    public void theCollectorsAttacksPayHerCharge() {
        Run run = new Run(0);
        run.attack(run.ally, 2);
        Assertions.assertEquals(0, run.charge(), "no state, no charge");

        run.makeAllyTheCollector();
        run.attack(run.ally, 2);
        Assertions.assertEquals(2, run.charge(), "now the collector's two hits pay");
    }

    /**
     * 「充能到达 8 点后，消耗 8 点充能，发动 1 次追加攻击…立即获得 5 层【当品】」.
     */
    @Test
    public void eightChargePaysTheFollowUpAndFiveGoods() {
        Run run = new Run(0);
        for (int i = 0; i < 3; i++) {
            run.attack(run.jade, 2);                      // 6 charge, below the threshold
        }
        Assertions.assertEquals(6, run.charge());
        double before = run.enemy.getCurrentHp();

        run.attack(run.jade, 2);                          // 8 charge: the follow-up fires

        Assertions.assertEquals(0, run.charge(), "「消耗 8 点充能」");
        Assertions.assertTrue(before - run.enemy.getCurrentHp() > 0, "the follow-up deals damage");
        // 5 from the follow-up plus the 1 her trace (逆回购) grants at battle start.
        Assertions.assertEquals(6, run.stacks(GOODS), "「发动…追加攻击时立即获得 5 层【当品】」");
    }

    /**
     * Eidolon 1, the charge half: 「击中数量等于 2/1 名时，翡翠额外获得 1/2 点充能」.
     *
     * <p>⚠ The extra is ADDITIVE: the talent still pays one point per hit, so a two-hit collector attack is 2 + 1 at E1
     * and 2 at E0.
     */
    @Test
    public void theFirstEidolonPaysExtraChargeForSmallHits() {
        Run twoHitsE0 = new Run(0);
        twoHitsE0.makeAllyTheCollector();
        twoHitsE0.attack(twoHitsE0.ally, 2);
        Assertions.assertEquals(2, twoHitsE0.charge(), "E0 pays only the talent own point per hit");

        Run twoHitsE1 = new Run(1);
        twoHitsE1.makeAllyTheCollector();
        twoHitsE1.attack(twoHitsE1.ally, 2);
        Assertions.assertEquals(3, twoHitsE1.charge(), "「等于 2 名…额外获得 1 点」");

        Run oneHitE1 = new Run(1);
        oneHitE1.makeAllyTheCollector();
        oneHitE1.attack(oneHitE1.ally, 1);
        Assertions.assertEquals(3, oneHitE1.charge(), "one hit: 1 from the talent + 2 extra");

        Run threeHitsE1 = new Run(1);
        threeHitsE1.makeAllyTheCollector();
        threeHitsE1.attack(threeHitsE1.ally, 3);
        Assertions.assertEquals(3, threeHitsE1.charge(),
                "three hits are not one of the sentence tiers (exactly 2 or exactly 1)");
    }

    /**
     * 「每层【当品】使暴击伤害提高 2.40%」 - a LIVE reading: the stacks the follow-up just granted are already in the panel.
     */
    @Test
    public void everyGoodRaisesHerCritDamage() {
        Run run = new Run(0);
        double bare = run.jade.getAttribute(AttributeType.CRIT_ATTACK).get();
        for (int i = 0; i < 3; i++) {
            run.attack(run.jade, 2);                      // 6 charge
        }
        run.attack(run.jade, 2);                          // the follow-up grants 5 层
        Assertions.assertEquals(6, run.stacks(GOODS), "5 from the follow-up + 1 from the trace at battle start");
        // `bare` already carries the trace layer (1 当品 at battle start), so the DELTA is the five the follow-up added.
        Assertions.assertEquals(5 * 0.024, run.jade.getAttribute(AttributeType.CRIT_ATTACK).get() - bare, 1e-6,
                "每层 +2.40% read through `per_stack_live`");
    }

    public void theFirstEidolonRaisesTheFollowUpsDamageByThirtyTwoPercent() {
        double plain = followUpDamage(0);
        double raised = followUpDamage(1);
        System.out.println("[1314-E1] follow-up damage: E0=" + plain + " ; E1=" + raised);
        Assertions.assertEquals(1.32, raised / plain, 1e-6, "「天赋的追加攻击伤害提高 32%」 - multiplicative");
    }

    /**
     * Eidolon 6: she becomes a collector, with 20% QUANTUM penetration.
     */
    @Test
    public void theSixthEidolonMakesHerACollectorWithQuantumPenetration() {
        Run e5 = new Run(5);
        Run e6 = new Run(6);
        Assertions.assertFalse(e5.jade.getBuffManager().hasState(COLLECTOR), "E5 is not one");
        Assertions.assertTrue(e6.jade.getBuffManager().hasState(COLLECTOR), "「同时翡翠获得【收债人】状态」");
        Assertions.assertEquals(0.2, e6.jade.getAttribute(AttributeType.DAMAGE_PENETRATION_QUANTUM).get()
                - e5.jade.getAttribute(AttributeType.DAMAGE_PENETRATION_QUANTUM).get(), 1e-9,
                "「量子属性抗性穿透提高 20%」");
    }

    // ==================================================================
    // 秘技: one 50%-of-ATK quantum hit at battle start
    // ==================================================================

    /**
     * 秘技: 「进入战斗后对敌方全体造成等同于翡翠 50% 攻击力的量子属性伤害」 - one hit, aimed at every enemy.
     *
     * <p>The old note called this 「10 段 / 0.5 份额」 and left it unwritten as a map-layer effect. Both parts were wrong: the
     * row's `10` is the SECONDS of 【盲从】, and the battle-layer hit is ordinary vocabulary.
     */
    @Test
    public void theTechniqueDealsOneQuantumHitAtBattleStart() {
        Run plain = new Run(0);
        Run withTechnique = new Run(0, true);

        Assertions.assertEquals(0.0, plain.enemy.getCurrentHp() - plain.enemy.getAttribute(AttributeType.HEALTH).get(), 1e-6,
                "without the technique the enemy is untouched at battle start");
        Assertions.assertTrue(plain.enemy.getCurrentHp() - withTechnique.enemy.getCurrentHp() > 0,
                "「对敌方全体造成等同于翡翠 50% 攻击力的量子属性伤害」");
        // 15 from the technique plus the 1 her trace grants at battle start.
        Assertions.assertEquals(16, withTechnique.jade.getBuffManager().stacksOf(GOODS), "「立即获得 15 层【当品】」");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static double critChance(boolean technique) {
        Character jade = CharacterFactory.create(JADE, LEVEL, true, null, null, 2);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(jade), List.of(enemy), BattleFixture.seeded());
        if (technique) {
            battle.markTechniqueUsed(jade);
        }
        battle.startBattle();
        Assertions.assertTrue(jade.getBuffManager().stacksOf(GOODS) >= (technique ? 15 : 1),
                "precondition: the counter reached " + jade.getBuffManager().stacksOf(GOODS));
        return jade.getAttribute(AttributeType.CRIT_CHANCE).get()
                - jade.getAttribute(AttributeType.CRIT_CHANCE).baseValue();
    }

    private static int layers(boolean technique) {
        StackFixture f = fixture(technique);
        return f.jade.getBuffManager().stacksOf(GOODS);
    }

    private static double criticalDamage(boolean technique) {
        StackFixture f = fixture(technique);
        return f.jade.getAttribute(AttributeType.CRIT_ATTACK).get()
                - f.jade.getAttribute(AttributeType.CRIT_ATTACK).baseValue();
    }

    private static StackFixture fixture(boolean technique) {
        Character jade = CharacterFactory.create(JADE, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(jade), List.of(enemy), BattleFixture.seeded());
        if (technique) {
            battle.markTechniqueUsed(jade);
        }
        battle.startBattle();
        return new StackFixture(jade);
    }

    private static Battle battle() {
        Character jade = CharacterFactory.create(JADE, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(jade, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        battle.startBattle();
        return battle;
    }

    private static void addGoods(Character jade, int layers) {
        for (int i = 0; i < layers; i++) {
            jade.getBuffManager().addBuff(new com.laosun.aluminium.buff.StackBuff("当品", 1, true, 50));
        }
    }

    /**
     * A battle where the trace's rule is the only source of ATK change: with no 当品 layers the panel equals a character
     * built without the rule? That cannot be done here, so the check is that the panel does not move when nothing is added.
     */
    private static double offBattle() {
        Battle battle = battle();
        Character jade = battle.characters().getFirst();
        double before = jade.getAttribute(AttributeType.ATTACK).get();
        return jade.getAttribute(AttributeType.ATTACK).get() - before;
    }

    private static double share(Character unit, AttributeType attribute) {
        double base = unit.getAttribute(attribute).baseValue();
        return (unit.getAttribute(attribute).get() - base) / base;
    }

    /** The follow-up's damage at a rank: charge to 8 and measure the enemy's loss. */
    private static double followUpDamage(int eidolon) {
        Run run = new Run(eidolon);
        for (int i = 0; i < 3; i++) {
            run.attack(run.jade, 2);                      // 6
        }
        double before = run.enemy.getCurrentHp();
        run.attack(run.jade, 2);                          // 8: the follow-up
        return before - run.enemy.getCurrentHp();
    }

    // ==================================================================

    private static final class DebtFixture {
        private final Character jade;
        private final Character ally;
        private final Battle battle;

        private DebtFixture() {
            jade = CharacterFactory.create(JADE, LEVEL);
            ally = CharacterFactory.create(ALLY, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(jade, ally)),
                    new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
            battle.startBattle();
        }

        private void skill(Character target) {
            battle.castImmediate(jade.getSkills().get(SkillType.SKILL), jade, List.of(target));
        }
    }

    private static final class CollectorFixture {
        private final Character jade;
        private final Character ally;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private CollectorFixture() {
            jade = CharacterFactory.create(JADE, LEVEL);
            ally = CharacterFactory.create(ALLY, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(jade, ally)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.alwaysRoll(0.99));
            battle.startBattle();
        }

        private void makeCollector(Character who) {
            battle.castImmediate(jade.getSkills().get(SkillType.SKILL), jade, List.of(who));
            Assertions.assertTrue(who.getBuffManager().hasState(COLLECTOR), "precondition: the collector state is up");
        }

        private void attack(Character who) {
            battle.castImmediate(who.getSkills().get(SkillType.COMMON), who, List.of(enemy));
        }

        private double attackLoss() {
            double before = enemy.getCurrentHp();
            attack(ally);
            return before - enemy.getCurrentHp();
        }
    }

    private static final class Run {
        private final Character jade;
        private final Character ally;
        private final Enemy enemy;
        private final Battle battle;

        Run(int eidolon) {
            this(eidolon, false);
        }

        Run(int eidolon, boolean technique) {
            jade = CharacterFactory.create(JADE, LEVEL);
            jade.setEidolonRank(eidolon);
            // A crit roll would make every damage ratio a coin toss, so it is pinned off.
            jade.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0));
            ally = CharacterFactory.create(TALENT_ALLY, LEVEL);
            ally.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0));
            enemy = BattleFixture.monster();
            if (technique) {
                jade.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff("sys:technique", 1));
            }
            battle = new Battle(List.of(jade, ally), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        void attack(Character actor, int hitCount) {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, actor, enemy, hitCount, 0));
            battle.processRequests();
        }

        /** Her Skill puts 【收债人】 on a target; cast it at the ally. */
        void makeAllyTheCollector() {
            battle.castImmediate(new com.laosun.aluminium.skill.DefaultSkill(JADE, 2, LEVEL), jade, List.of(ally));
            battle.processRequests();
        }

        int charge() {
            return jade.getResources().get(CHARGE).getValue();
        }

        int stacks(String buff) {
            return jade.getBuffManager().stacksOf(buff);
        }
    }

    private record StackFixture(Character jade) {
    }
}
