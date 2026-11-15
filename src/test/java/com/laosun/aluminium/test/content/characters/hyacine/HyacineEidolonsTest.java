package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1409 风堇's Eidolon kit (星魂 1-6), the level rows her eidolons move, and her traces - read through the numbers the
 * rules actually use.
 *
 * <ul>
 *   <li>Rank 1: while 【雨过天晴】 is up, 「施放攻击后立即回复等同于风堇 8% 生命上限的生命值」;</li>
 *   <li>Rank 2: 「我方目标生命值降低时，速度提高 30%，持续 2 回合」, plus 「风堇处于【雨过天晴】状态时，我方全体目标生命上限
 *       额外提高 50%」;</li>
 *   <li>Rank 3 / 5: 「终结技等级+2」 / 「战技等级+2」 / 「天赋等级+2」 / 「忆灵技等级+1」 - all read through
 *       {@code skillLevel}, the one resolver every row read goes through;</li>
 *   <li>Rank 4: 「行迹【暴风停歇】获得强化，每超过 1 点速度，风堇和小伊卡的暴击伤害额外提高 2%」;</li>
 *   <li>Rank 6: 「小伊卡在场时，我方全体目标的全属性抗性穿透提高 20%」 - a state of the field, not an arrival.</li>
 * </ul>
 *
 * <p>The "why" of each group is on the case that measures it: the row-scaled numbers are asserted at <b>both ends</b> of
 * the level range (a literal equal to the Lv10 row is right at E0 and silently stale the moment an eidolon raises the
 * slot), and the trace's four facts (strict gate, point bound, inherit-not-double-count, coexist with another HEALTH
 * clause) sit on three cases - the gate and the point bound share one scenario, the other two keep their own.
 *
 * <p>These were six small files in the same package; one class keeps the eidolons, levels and traces together without
 * changing any assertion.
 */
public class HyacineEidolonsTest {

    private static final int HYACINE = 1409;
    private static final int ALLY = 1001;
    private static final int CYRENE = 1415;
    private static final double EPS = 1e-9;
    /** The row-level cases use the looser delta the source file chose (a flat {@code 1e-6} point). */
    private static final double ROW_EPS = 1e-6;
    /** 1409's raw HEALTH base at Lv80. */
    private static final double BASE_HP = 1086.624;
    /** {@code 1409/3} Lv10 = {@code [0.1, 200, 0.3, 600, 3, 0.12, 240]}; Lv12 = {@code [0.11, 222.5, …]}. */
    private static final double ULT_SHARE_LV10 = 0.1;
    private static final double ULT_FLAT_LV10 = 200;
    private static final double ULT_SHARE_LV12 = 0.11;
    private static final double ULT_FLAT_LV12 = 222.5;
    /** {@code 1409/4} Lv10 {@code #3 = 0.8}; Lv12 (Eidolon 5) {@code #3 = 0.88}. */
    private static final double TALENT_SHARE_LV10 = 0.8;
    private static final double TALENT_SHARE_LV12 = 0.88;

    /**
     * Rank 1: 「施放攻击后立即回复等同于风堇 8% 生命上限的生命值」.
     */
    @Test
    public void rankOneHealsTheAttackerForEightPercentOfHerMaxHp() {
        Character hyacine = CharacterFactory.create(HYACINE, 80, true, null, null, 6);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.ULTRA), hyacine,
                List.of(battle.enemies().getFirst()));
        battle.processRequests();
        double hyacineMax = hyacine.getAttribute(com.laosun.aluminium.enums.AttributeType.HEALTH).get();

        ally.consumeHp(ally.getAttribute(com.laosun.aluminium.enums.AttributeType.HEALTH).get() * 0.5);
        double wounded = ally.getCurrentHp();
        SkillExecutor.execute(battle, ally.getSkills().get(SkillType.COMMON), ally,
                List.of(battle.enemies().getFirst()));
        battle.processRequests();

        double healed = ally.getCurrentHp() - wounded;
        Assertions.assertTrue(healed > 0, "an attack heals the attacker while char:1409/after_the_rain is up");
        // 0.088, not 0.08: this fixture builds her at Eidolon 6, and her Eidolon 3 already raised the Skill by two, so the
        // row the rule reads is Lv12 (0.08 at Lv10, 0.088 at Lv12). The rule reads the row precisely so this follows.
        Assertions.assertEquals(Math.min(hyacineMax * 0.088, ally.getAttribute(com.laosun.aluminium.enums.AttributeType.HEALTH).get() - wounded),
                healed, 1e-6, "「回复等同于风堇 8% 生命上限」: " + healed);
    }

    /**
     * Rank 2: 「我方目标生命值降低时，速度提高 30%，持续 2 回合」.
     */
    @Test
    public void rankTwoRaisesSpeedWhenAnAllyLosesHp() {
        Character hyacine = CharacterFactory.create(HYACINE, 80, true, null, null, 6);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        double base = ally.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).baseValue();
        double speedBefore = ally.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get();

        // Two corrections against the version that was @Disabled until 2026-10-18:
        //   ① the rule's target is `target` = the unit that LOST HP, not 风堇 -- the old assertion read the wrong unit;
        //   ② `ally.takeDamage(...)` bypasses Battle, so HP_LOST was never emitted. Only Battle.applyDamage settles a
        //      hit and pays out the event (CanHit.takeDamage's own javadoc says so).
        battle.applyDamage(ally, new com.laosun.aluminium.damage.Damage(hypotheticalAttacker(battle), ally,
                com.laosun.aluminium.enums.DamageElement.PHYSICAL,
                com.laosun.aluminium.enums.DamageType.NORMAL, ally.getMaxHp() * 0.1));
        battle.processRequests();

        double speedAfter = ally.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get();
        // 0.33, not 0.3: at Eidolon 6 her Eidolon 3 has already raised the Ultimate by two, so the row is Lv12.
        Assertions.assertEquals(base * 0.33, speedAfter - speedBefore, 1e-6,
                "Rank 2: 「我方目标生命值降低时，速度提高 30%」 (before=" + speedBefore + ", after=" + speedAfter
                        + ", base=" + base + ")");
    }

    /**
     * Rank 6, second half: 「<b>小伊卡在场时</b>，我方全体目标的全属性<u>抗性穿透</u>提高 <b>20%</b>」.
     *
     * <p>Two things are being measured, and they are different facts:
     * <ul>
     *   <li>the number -- {@code DAMAGE_PENETRATION} is the all-type spelling (it subtracts from whatever element the hit
     *       already resolved, see {@code Battle}'s resistance zone), and 0.2 is a ratio attribute, so the share lands as
     *       absolute points;</li>
     *   <li>the <b>gate</b> -- 「小伊卡在场时」 is an event, not a loadout fact: the aura must be absent at
     *       {@code BATTLE_START} and present only once a summon has actually entered the field. Reading it off
     *       {@code BATTLE_START} would have been the cheap version, and it would have given a wrong number for the whole
     *       first stretch of every fight.</li>
     * </ul>
     *
     * <p>⚠ The first half of the same sentence (「清空累计治疗数值改为 12%」) is registered, not written: the tally that
     * the memosprite's skill 1 clears is that skill's own mechanic and has no rule yet.
     */
    @Test
    public void rankSixGivesTheWholeSideResistancePenetrationOnceIcaIsOut() {
        EidolonSixFixture f = new EidolonSixFixture(6);
        Assertions.assertEquals(0.0, f.pen(f.hyacine), EPS,
                "precondition: 「小伊卡在场时」 -- nothing before a summon has entered the field");
        Assertions.assertEquals(0.0, f.pen(f.ally), EPS,
                "precondition: the aura is not a BATTLE_START fact");

        f.skillCast();     // 战技召唤小伊卡 ⇒ SUMMONED

        Assertions.assertTrue(f.battle.memospriteOf(f.hyacine) != null, "precondition: 小伊卡 is out");
        Assertions.assertEquals(0.2, f.pen(f.hyacine), EPS, "「我方全体…抗性穿透提高 20%」 (the caster)");
        Assertions.assertEquals(0.2, f.pen(f.ally), EPS, "「我方全体…抗性穿透提高 20%」 (an ally)");
        CanHit ika = f.battle.memospriteOf(f.hyacine);
        Assertions.assertEquals(0.2, ika.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), EPS,
                "「我方全体目标」 is the CAMP, so the memosprite in it is included too");
    }

    /** Rank 6 is what turns the aura on: the same fixture at E0 gives nothing. */
    @Test
    public void theSkillGivesNothingWithoutRankSix() {
        EidolonSixFixture f = new EidolonSixFixture(0);
        f.skillCast();
        Assertions.assertTrue(f.battle.memospriteOf(f.hyacine) != null, "precondition: 小伊卡 is out");
        Assertions.assertEquals(0.0, f.pen(f.hyacine), EPS, "Rank 6 is what turns the aura on");
        Assertions.assertEquals(0.0, f.pen(f.ally), EPS, "Rank 6 is what turns the aura on");
    }

    /**
     * The other half of the same Rank 6 aura: 「小伊卡<b>在场时</b>」 is a state of the field.
     *
     * <h2>Why "在场时" is not "on arrival"</h2>
     * The aura used to be granted only when <b>小伊卡 herself</b> entered the field ({@code SUMMONED} + {@code actor ==
     * summon}), to whoever was standing at that moment. So a memosprite summoned <b>later</b> - 德谬歌, 晴空乐手, or
     * another team's 长夜 - never got it, even though 小伊卡 was on the field the whole time. The clause reads as a state
     * of the field, so every arrival while she is out must grant it.
     *
     * <p>The assertion is on the <b>late</b> arrival's own attribute: it is the only reading that fails when the aura is
     * tied to her arrival, and the control (小伊卡 already has it) pins that the fixture really is in the aura's state.
     */
    @Test
    public void aMemospriteSummonedAfterIkaStillGetsTheAura() {
        Character hyacine = CharacterFactory.create(HYACINE, 80, true, null, null, 6);
        Character cyrene = CharacterFactory.create(CYRENE, 80);
        Battle battle = new Battle(List.of(hyacine, cyrene), List.of(BattleFixture.dummy()),
                BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        battle.summonMemosprite(hyacine);
        battle.processRequests();
        Summon ika = (Summon) battle.memospriteOf(hyacine);
        Assertions.assertNotNull(ika, "precondition: 小伊卡 is out, so the aura is up");
        Assertions.assertEquals(0.2, ika.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), EPS,
                "precondition: the aura reached the units standing when she arrived");

        // The LATE arrival: another character's memosprite, summoned after 小伊卡.
        battle.summonMemosprite(cyrene);
        battle.processRequests();
        Summon demiurge = (Summon) battle.memospriteOf(cyrene);
        Assertions.assertNotNull(demiurge, "precondition: 德谬歌 arrived after 小伊卡");

        Assertions.assertEquals(0.2, demiurge.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), EPS,
                "「小伊卡在场时」 is a state of the field: a memosprite that arrives later must get the 20% too");
        Assertions.assertEquals(0.2, hyacine.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), EPS,
                "and it is still not stacked on the units that already had it (replace, not add)");
    }

    /** The aura is Rank 6's: at E0 a late arrival gets nothing. */
    @Test
    public void withoutRankSixNothingIsGranted() {
        Character hyacine = CharacterFactory.create(HYACINE, 80);
        Character cyrene = CharacterFactory.create(CYRENE, 80);
        Battle battle = new Battle(List.of(hyacine, cyrene), List.of(BattleFixture.dummy()),
                BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        battle.summonMemosprite(hyacine);
        battle.processRequests();
        battle.summonMemosprite(cyrene);
        battle.processRequests();

        Summon demiurge = (Summon) battle.memospriteOf(cyrene);
        Assertions.assertEquals(0.0, demiurge.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), EPS,
                "the aura is Rank 6's; at E0 a late arrival gets nothing");
    }

    /**
     * The ultimate 【雨过天晴】 with her eidolons: the Max-HP share is read from the <b>current</b> row, Rank 3 raises the
     * ultimate by two levels, and Rank 2 adds another +50% while the state is up.
     *
     * <h2>Why the numbers are these</h2>
     * The document (1409_风堇.md) says Rank 3: 「终结技等级+2」 and Rank 2: 「风堇处于【雨过天晴】状态时，我方全体目标生命上限
     * 额外提高 50%」. So a rank-6 风堇 casts the ultimate at Lv12, whose row is {@code [0.1, 200, 0.33, 667.5, 3, 0.12,
     * 240]} - #3 = 0.33 (the share). The capture's screenshot shows exactly 「83% + 667」 = 0.33 + 0.50 and 667.5 rounded,
     * which is what this judge pins.
     *
     * <p>The base HP is the engine's own panel for 1409 at Lv80 ({@code 1195.2864}); a percentage modifier raises the
     * value by {@code base x percent} (the {@code DoubleValue} rule). The flat term is still the literal 600, because
     * {@code MODIFY_ATTR} has no "flat from the current skill row" spelling yet (registered in the file's note).
     */
    @Test
    public void theMaxHpClimbIsTheRowsSharePlusRankTwoPlusTheFlat() {
        double rank0 = climb(0);
        double rank6 = climb(6);
        Assertions.assertEquals(BASE_HP * 0.30 + 600, rank0, 1e-6,
                "E0: Lv10 row #3 = 0.30 and the literal flat 600");
        Assertions.assertEquals(BASE_HP * (0.33 + 0.50) + 667.5, rank6, 1e-6,
                "E6: Lv12 row #3 = 0.33, plus Rank 2's 0.50, plus the Lv12 flat 667.5");
    }

    /**
     * Rank 3 / 5: 「忆灵技等级+1」 is filed on the summoner at data slot 1, 「战技等级+2」 and 「天赋等级+2」 on her own
     * slots - and the memosprite's read stays 10 because the spec's level is already the table's cap
     * 「最多不超过10级」.
     */
    @Test
    public void rankThreeRaisesIkachisSkillByOne() {
        Character hyacine = CharacterFactory.create(HYACINE, 80, true, null, null, 6);
        Battle battle = new Battle(List.of(hyacine), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        com.laosun.aluminium.entity.Summon ikachi = battle.summonServant(hyacine);
        Assertions.assertNotNull(ikachi, "precondition: her talent summons 小伊卡");
        Assertions.assertEquals(1, hyacine.memospriteSkillLevelBonus(1),
                "Rank 3: 「忆灵技等级+1」 is filed on the summoner at data slot 1");
        Assertions.assertEquals(10, ikachi.skillLevel(ikachi.skillAt(1)),
                "…and the read stays 10 because the spec's level is already the table's cap 「最多不超过10级」");
        Assertions.assertEquals(12, hyacine.skillLevel(hyacine.getSkills().get(SkillType.SKILL)),
                "Rank 5: 「战技等级+2」 (10 + 2)");
        Assertions.assertEquals(12, hyacine.skillLevel(hyacine.getSkills().get(SkillType.TALENT)),
                "Rank 5: 「天赋等级+2」 (10 + 2)");
    }

    /**
     * Her ultimate's heal 「为我方全体目标回复等同于风堇 <b>#1% 生命上限</b> + <b>#2</b>」, asserted at <b>both ends</b> of
     * the level range: E0 reads the Lv10 row ({@code 0.1 / 200}) and E3 reads the Lv12 row ({@code 0.11 / 222.5}).
     *
     * <h2>Why "both ends" is the whole point</h2>
     * The heal used to be <b>literals</b> equal to the <b>Lv10</b> row. A literal is right at the file's own convention
     * and silently stale the moment an eidolon raises the slot: <b>E3</b> takes the ultimate to Lv12. An assertion on the
     * base level alone cannot tell a literal from a row read - which is how it stayed wrong.
     *
     * <h2>The heal's base is HER max HP, measured</h2>
     * The heal's {@code scale} is {@code owner_max_hp} = <b>风堇's own</b> Max HP, taken <b>before</b> the same cast's
     * 「雨过天晴」 raises it. A probe measured her max HP at cast time as {@code 1195.2864} (it becomes
     * {@code 2121.2736} right afterwards) and the ally's gain as {@code 319.52864 = 0.1 x 1195.2864 + 200} at E0 - the
     * <b>same</b> {@code 319.52864} at E3, which is the drift this judge pins.
     */
    @Test
    public void theUltimateHealsTheRowsShareAtBothLevels() {
        RowLevelFixture e0 = new RowLevelFixture(0);
        double[] e0Result = e0.castUltAndMeasureAllyHeal();
        double e0Expected = ULT_SHARE_LV10 * e0Result[1] + ULT_FLAT_LV10;

        RowLevelFixture e3 = new RowLevelFixture(3);
        int e3UltLevel = e3.hyacine.skillLevel(e3.hyacine.getSkills().get(SkillType.ULTRA));
        double[] e3Result = e3.castUltAndMeasureAllyHeal();
        double e3Expected = ULT_SHARE_LV12 * e3Result[1] + ULT_FLAT_LV12;
        double e3Literal = ULT_SHARE_LV10 * e3Result[1] + ULT_FLAT_LV10;

        Assertions.assertAll("ultimate heal reads the current row",
                () -> Assertions.assertEquals(e0Expected, e0Result[0], Math.max(ROW_EPS, e0Expected * 1e-9),
                        "E0 「#1% 生命上限 + #2」 at Lv10, base = HER max HP at cast time (" + e0Result[1] + ")"),
                () -> Assertions.assertEquals(12, e3UltLevel,
                        "precondition: Eidolon 3 raised the ultimate to Lv12"),
                () -> Assertions.assertEquals(e3Expected, e3Result[0], Math.max(ROW_EPS, e3Expected * 1e-9),
                        "E3 must read the Lv12 row (" + e3Expected + "); the old literal would heal " + e3Literal));
    }

    /** 「小伊卡伤害提高 #3%」 per healing layer, asserted at both ends of the row: Lv10 = 0.8 at E0, Lv12 = 0.88 at E5. */
    @Test
    public void theTalentsPerLayerBoostReadsTheRowAtBothLevels() {
        RowLevelFixture e0 = new RowLevelFixture(0);
        e0.healOnce();
        double lv10 = e0.ikachiBoost();

        RowLevelFixture e5 = new RowLevelFixture(5);
        int talentLevel = e5.hyacine.skillLevel(e5.hyacine.getSkills().get(SkillType.TALENT));
        e5.healOnce();
        double lv12 = e5.ikachiBoost();

        Assertions.assertAll("talent per-layer boost reads the current row",
                () -> Assertions.assertEquals(TALENT_SHARE_LV10, lv10, ROW_EPS,
                        "「小伊卡伤害提高 #3%」 at Lv10 = 0.8 for one healing layer"),
                () -> Assertions.assertEquals(12, talentLevel,
                        "precondition: Eidolon 5 raised the talent to Lv12"),
                () -> Assertions.assertEquals(TALENT_SHARE_LV12, lv12, ROW_EPS,
                        "E5 「#3%」 at Lv12 = 0.88 -- the old literal 0.8 would be short here"));
    }

    /**
     * Her trace 【暴风停歇】 (1409103) and Rank 4, which strengthens it.
     *
     * <pre>
     * trace  : 「风堇的速度大于 200 时，她与小伊卡的生命上限提高 20%，之后每超出 1 点速度，
     *           风堇与小伊卡提供的治疗量提高 1%，最多计入 200 点超出的速度。」
     * Rank 4 : 「行迹【暴风停歇】获得强化，每超过 1 点速度，风堇和小伊卡的暴击伤害额外提高 2%。」
     * </pre>
     *
     * <p>Four things are being separated, and each has bitten this repository in another coat:
     * <ul>
     *   <li>the gate is <b>strictly</b> above 200 (「大于」), so 200 exactly must give nothing;</li>
     *   <li>the healing share is bounded by <b>points</b> (「最多计入 200 点超出的速度」), so 400 and 500 read the
     *       same -- dropping the bound would give 300% where the document says 200%;</li>
     *   <li>小伊卡 must <b>not</b> get a second +20% Max HP: its panel is 50% of the master's <em>resolved</em> sheet, so
     *       her raise already flows through, and a second modifier would double-count it;</li>
     *   <li>the +20% must not displace <b>another</b> HEALTH clause: the identifier for "same kind" is (attribute,
     *       modifierType, sourceRole) and does not include the name, so two {@code BATTLE_START} HEALTH modifiers from the
     *       same owner would evict each other. Measured: with {@code coexist} the technique's 20% and the trace's 20% add
     *       up; without it the trace wins and the technique's is gone. The <em>ultimate</em> is a different case -- it
     *       carries {@code max_stacks: 2}, so it stacks either way.</li>
     * </ul>
     *
     * <p>This case measures the first two; the other two are their own cases below.
     */
    @Test
    public void theTraceGateIsStrictAndItsHealingShareIsCappedByPoints() {
        Character slow = hyacine(0, 200.0);      // 「大于 200」 ⇒ 200 exactly is NOT enough
        Character fast = hyacine(0, 250.0);
        double slowMax = maxHpAfterStart(slow);
        double fastMax = maxHpAfterStart(fast);
        double base = fast.getAttribute(AttributeType.HEALTH).baseValue();

        double nothing = healBoost(200.0);
        double fiftyPoints = healBoost(250.0);
        double twoHundredPoints = healBoost(400.0);
        double threeHundredPoints = healBoost(500.0);

        Assertions.assertAll("【暴风停歇】: the 200 gate and the point bound",
                () -> Assertions.assertEquals(slowMax, slow.getAttribute(AttributeType.HEALTH).get(), EPS,
                        "precondition: at exactly 200 the gate is closed"),
                () -> Assertions.assertEquals(slowMax + base * 0.20, fastMax, Math.max(EPS, base * 1e-12),
                        "「速度大于 200 时…生命上限提高 20%」 (slow=" + slowMax + ", fast=" + fastMax + ", base="
                                + base + ")"),
                () -> Assertions.assertEquals(0.0, nothing, EPS,
                        "「之后每超出 1 点」: 200 exactly is still nothing"),
                () -> Assertions.assertEquals(0.5, fiftyPoints, EPS, "50 points above 200 ⇒ 50%"),
                () -> Assertions.assertEquals(2.0, twoHundredPoints, EPS, "200 points above 200 ⇒ 200%"),
                () -> Assertions.assertEquals(2.0, threeHundredPoints, EPS,
                        "「最多计入 200 点超出的速度」 -- 300 points above reads the SAME as 200"));
    }

    /** 小伊卡 inherits the raised sheet and the healing share, but is not raised twice. */
    @Test
    public void ikachiInheritsTheRaisedMaxHpAndTheHealingShareButIsNotRaisedTwice() {
        Character hyacine = hyacine(0, 250.0);
        Battle battle = started(hyacine);
        battle.summonMemosprite(hyacine);
        battle.processRequests();

        CanHit ika = battle.memospriteOf(hyacine);
        Assertions.assertNotNull(ika, "precondition: 小伊卡 is out");
        double hers = hyacine.getAttribute(AttributeType.HEALTH).get();
        Assertions.assertEquals(hers * 0.5, ika.getAttribute(AttributeType.HEALTH).get(), Math.max(EPS, hers * 1e-12),
                "「等同于风堇 50% 生命上限」 is read off her RESOLVED sheet, so the +20% arrives through the inherit "
                        + "(a second +20% here would be a double count; hers=" + hers + ", its="
                        + ika.getAttribute(AttributeType.HEALTH).get() + ")");
        Assertions.assertEquals(0.5, ika.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get(), EPS,
                "「风堇与**小伊卡**提供的治疗量提高」 -- the share has to be written on the memosprite too, "
                        + "because a modifier on an attribute is not inherited");
    }

    /** Rank 4's CRIT DMG per point of speed, which keeps the trace's own bound. */
    @Test
    public void rankFourAddsTwoPercentCritDamagePerPointOfSpeedUpToTheTracesBound() {
        Assertions.assertEquals(1.0, critGain(4, 250.0), EPS, "50 points above 200 ⇒ 2% × 50");
        Assertions.assertEquals(4.0, critGain(4, 500.0), EPS,
                "the strengthened trace keeps the bound: 200 points ⇒ 4.0, not 6.0");
        Assertions.assertEquals(0.0, critGain(0, 250.0), EPS, "the trace itself gives no CRIT DMG -- that is Rank 4");
    }

    /**
     * The trace's +20% is one HEALTH clause among others, and it neither evicts nor is evicted: the ultimate's own
     * 30% + 600 is ADDED on top (it carries {@code max_stacks: 2}, so it stacks rather than evicting), and the
     * technique's 20% is a second, different sentence on the same attribute that adds up too.
     */
    @Test
    public void theTracesMaxHpClauseAddsWithTheUltimateAndTheTechnique() {
        Character ultimate = hyacine(0, 250.0);
        Battle ultimateBattle = started(ultimate);
        double ultimateBase = ultimate.getAttribute(AttributeType.HEALTH).baseValue();
        double ultimateBefore = ultimate.getAttribute(AttributeType.HEALTH).get();
        SkillExecutor.execute(ultimateBattle, ultimate.getSkills().get(SkillType.ULTRA), ultimate,
                List.of(ultimateBattle.enemies().getFirst()));
        ultimateBattle.processRequests();
        double ultimateGain = ultimate.getAttribute(AttributeType.HEALTH).get() - ultimateBefore;

        Character technique = hyacine(0, 250.0);
        Battle techniqueBattle = new Battle(List.of(technique, CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.dummy()), BattleFixture.seeded());
        techniqueBattle.markTechniqueUsed(technique);
        double techniqueBefore = technique.getAttribute(AttributeType.HEALTH).get();
        techniqueBattle.startBattle();
        techniqueBattle.processRequests();
        double techniqueBase = technique.getAttribute(AttributeType.HEALTH).baseValue();
        double techniqueAfter = technique.getAttribute(AttributeType.HEALTH).get();

        Assertions.assertAll("the trace's 20% coexists",
                () -> Assertions.assertEquals(ultimateBase * 0.30 + 600.0, ultimateGain,
                        Math.max(EPS, ultimateBase * 1e-12),
                        "the ultimate's 30% + 600 is ADDED to the trace's 20%: the ultimate carries max_stacks 2, so it "
                                + "stacks rather than evicting (this holds with or without the trace's coexist)"),
                () -> Assertions.assertEquals(techniqueBefore + techniqueBase * (0.20 + 0.20), techniqueAfter,
                        Math.max(EPS, techniqueBase * 1e-12),
                        "the technique's 20% and the trace's 20% are two different sentences on the same attribute: "
                                + "「生命上限提高 20%」(秘技) and 「生命上限提高 20%」(暴风停歇). Only one of them "
                                + "counting would be a silent wrong number, so this pins the SUM."));
    }

    private static Character hyacine(int eidolon, double speed) {
        Character c = CharacterFactory.create(HYACINE, 80, true, null, null, eidolon);
        // The trace reads the RESOLVED Speed, and no light cone in the fixture reaches 200; setting it here is the
        // honest way to reach the gate.
        c.setAttribute(AttributeType.SPEED, new DoubleValue(speed));
        return c;
    }

    private static Battle started(Character hyacine) {
        Battle battle = new Battle(List.of(hyacine, CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    private static double healBoost(double speed) {
        Character c = hyacine(0, speed);
        started(c);
        return c.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get();
    }

    private static double maxHpAfterStart(Character c) {
        started(c);
        return c.getAttribute(AttributeType.HEALTH).get();
    }

    private static double critGain(int eidolon, double speed) {
        Character base = hyacine(0, speed);
        Character ranked = hyacine(eidolon, speed);
        started(base);
        started(ranked);
        return ranked.getAttribute(AttributeType.CRIT_ATTACK).get() - base.getAttribute(AttributeType.CRIT_ATTACK).get();
    }

    private static double climb(int rank) {
        Character hyacine = CharacterFactory.create(HYACINE, 80, true, null, null, rank);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        int expectedLevel = rank >= 3 ? 12 : 10;
        Assertions.assertEquals(expectedLevel, hyacine.skillLevel(hyacine.getSkills().get(SkillType.ULTRA)),
                "Rank 3 raises the ultimate by 2 (from the content's Lv10)");
        double before = hyacine.getAttribute(AttributeType.HEALTH).get();
        SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.ULTRA), hyacine,
                List.of(battle.enemies().getFirst()));
        battle.processRequests();
        double after = hyacine.getAttribute(AttributeType.HEALTH).get();
        return after - before;
    }

    private static CanHit hypotheticalAttacker(Battle battle) {
        return battle.enemies().getFirst();
    }

    private static final class EidolonSixFixture {
        final Battle battle;
        final Character hyacine;
        final Character ally;

        EidolonSixFixture(int eidolon) {
            this.hyacine = CharacterFactory.create(HYACINE, 80, true, null, null, eidolon);
            this.ally = CharacterFactory.create(ALLY, 80);
            this.battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.dummy()),
                    BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        void skillCast() {
            SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine,
                    List.of(battle.enemies().getFirst()));
            battle.processRequests();
        }

        double pen(CanHit unit) {
            return unit.getAttribute(AttributeType.DAMAGE_PENETRATION).get();
        }
    }

    private static final class RowLevelFixture {
        final Battle battle;
        final Character hyacine;
        final Character ally;
        final Summon ikachi;

        RowLevelFixture(int eidolon) {
            this.hyacine = CharacterFactory.create(HYACINE, 80, true, null, null, eidolon);
            this.ally = CharacterFactory.create(ALLY, 80);
            this.battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.dummy()),
                    BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
            battle.summonMemosprite(hyacine);
            battle.processRequests();
            this.ikachi = (Summon) battle.memospriteOf(hyacine);
            Assertions.assertNotNull(ikachi, "precondition: 小伊卡 is out");
            ally.consumeHp(ally.getAttribute(AttributeType.HEALTH).get() * 0.5);
        }

        /** @return {what the ALLY gained, 风堇's max HP at cast time} */
        double[] castUltAndMeasureAllyHeal() {
            hyacine.setCurrentEnergy(hyacine.getMaxEnergy());
            double herMaxHpAtCast = hyacine.getMaxHp();
            double before = ally.getCurrentHp();
            Assertions.assertTrue(battle.castUltra(hyacine, List.of(battle.enemies().getFirst())),
                    "precondition: the ultimate went off");
            return new double[]{ally.getCurrentHp() - before, herMaxHpAtCast};
        }

        void healOnce() {
            SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine, List.of(ally));
            battle.processRequests();
        }

        double ikachiBoost() {
            return ikachi.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        }
    }
}
