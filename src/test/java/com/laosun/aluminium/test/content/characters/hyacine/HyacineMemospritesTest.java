package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1409 风堇's memosprite 小伊卡: what it pays on arrival, what it does when it acts, what it leaves behind when it goes
 * away - and the gate that says「小伊卡」 means <b>her</b> memosprite, not <b>a</b> memosprite.
 *
 * <ul>
 *   <li>忆灵技3 【展翼，奔向日辉】: 「被召唤时，为风堇恢复 15 点能量，首次被召唤时，额外恢复 30 点能量」 (param [15, 30]);</li>
 *   <li>忆灵技2: 雨过天晴下风堇施放技能后，小伊卡立即获得额外回合并自动施放忆灵技1, and 「小伊卡施放技能后使自身所有持续效果
 *       持续回合数减 1」;</li>
 *   <li>忆灵技2, first clause: the memosprite pays 4% of its OWN Max HP and heals whoever lost HP for 2% of 风堇 Max HP +
 *       20;</li>
 *   <li>忆灵技4: 「消失时，使风堇行动提前 30%」, and the Rank 6 aura 「小伊卡在场时…」 is taken back;</li>
 *   <li>忆灵技1 【乌云乌云快走开！】: 「对敌方全体造成等同于本场战斗风堇与小伊卡累计治疗数值 #1% 的风属性伤害并清空累计治疗
 *       数值的 50%」 (Rank 6: 12%).</li>
 * </ul>
 *
 * <p>The four "小伊卡 did something" clauses are also asked the one question a <b>one-memosprite</b> battle cannot ask:
 * does the gate mean <b>my</b> memosprite, or <b>a</b> memosprite? Every clause was written with {@code actor
 * is_summon}, which reads "the actor is a memosprite" -- true for <em>anyone's</em> memosprite, so a judge that fields
 * one memosprite per battle cannot tell the two apart. The spelling that says "mine" is {@code actor == summon}, and the
 * {@code OwnMemospriteFixture} below keeps 风堇's own memosprite on the field as the control while another character's
 * memosprite acts.
 *
 * <p>These were eight small files in the same package; one class keeps the memosprite together without changing any
 * assertion.
 */
public class HyacineMemospritesTest {

    private static final int HYACINE = 1409;
    private static final int ALLY = 1001;
    private static final int HEALTHY = 1002;
    /** 1413 长夜's owner - the "someone else's memosprite" the gates are asked about. */
    private static final int EVERNIGHT = 1413;
    private static final String TALLY = "internal:1409/ika_healing_total";
    private static final double EPS = 1e-9;
    /** The charity case measures a difference between two battles, so it keeps its own looser delta. */
    private static final double CHARITY_EPS = 1e-6;
    /** 风堇's skill row {@code 1409/2} at Lv10: {@code #3} and {@code #4} belong to 小伊卡 alone. */
    private static final double IKACHI_SHARE_AT_LV10 = 0.10;
    private static final double IKACHI_FLAT_AT_LV10 = 200;
    /** 「#1」 at the memosprite skill's level 10 row. */
    private static final double RAINCLOUDS_SHARE_AT_LV10 = 0.28;
    /** The data-driven instance's floor with ATK = 0 (measured). */
    private static final double DATA_FLOOR = 1.0;

    /**
     * 忆灵技3 【展翼，奔向日辉】 (data slot 5, ServantID 11409):
     *
     * <pre>
     * 「被召唤时，为风堇恢复 15 点能量，首次被召唤时，额外恢复 30 点能量。」 param = [15, 30]
     * </pre>
     *
     * <p>Two different facts on the same arrival: the 15 is paid on <b>every</b> arrival (a memosprite that perished can
     * be summoned again), the 30 only on the <b>first</b> one -- which is what {@code once_per_battle} means in this
     * repository (8006's Rank 1 is the shipped precedent for exactly that word).
     *
     * <p>为什么值得钉：这两句话共用同一个事件（{@code SUMMONED}），合成一条规则就得给 {@code amount} 一个「第一次
     * 才加」的开关，而那种开关不存在；两条规则反而各自对应原文的一小句。少了 {@code once_per_battle} 时第二次召唤
     * 会变成 45，多了则第一次只有 15 —— 两种错都不会报错，只会给出一个不对的能量数。
     */
    @Test
    public void everyArrivalPaysHerEnergyAndOnlyTheFirstPaysTheBonus() {
        Character hyacine = CharacterFactory.create(HYACINE, 80);
        Battle battle = battle(hyacine);
        double beforeFirst = hyacine.getCurrentEnergy();

        battle.summonMemosprite(hyacine);
        battle.processRequests();
        double afterFirst = hyacine.getCurrentEnergy();
        CanHit ika = battle.memospriteOf(hyacine);
        Assertions.assertNotNull(ika, "precondition: 小伊卡 is out");

        // 「首次」 is once per BATTLE, so the second arrival must be told apart from the first. `perish()` is the
        // engine's "it left, nobody killed it" (no DEATH payout), which is exactly what a memosprite that simply goes
        // away does.
        ika.perish();
        battle.processRequests();
        double beforeSecond = hyacine.getCurrentEnergy();
        battle.summonMemosprite(hyacine);
        battle.processRequests();
        double secondGain = hyacine.getCurrentEnergy() - beforeSecond;

        Assertions.assertAll("忆灵技3: arrival energy",
                () -> Assertions.assertEquals(15.0 + 30.0, afterFirst - beforeFirst, EPS,
                        "「被召唤时…恢复 15 点能量，首次被召唤时，额外恢复 30 点」 (before=" + beforeFirst + ", after="
                                + afterFirst + ")"),
                () -> Assertions.assertEquals(15.0, secondGain, EPS,
                        "the +30 is 「首次被召唤时」 only -- a later arrival pays the flat 15"));
    }

    /**
     * 忆灵技2 in one scenario, read on the same fixture and the same free turn: after 风堇 casts her Skill under 雨过天晴,
     * 小伊卡 immediately gains an extra turn and auto-casts 忆灵技1, and 「小伊卡施放技能后使自身所有持续效果持续回合数减 1」.
     *
     * <p>One turn off -- the cast's own -1, and NOTHING from the free turn it acted on (3 would have become 1 before the
     * free-turn fix).
     */
    @Test
    public void theFreeTurnCastsItsSlotOneAndTicksItsOwnEffects() {
        Character hyacine = CharacterFactory.create(1409, 80);
        Battle battle = new Battle(List.of(hyacine, CharacterFactory.create(1001, 80)),
                List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.ULTRA), hyacine,
                List.of(battle.enemies().getFirst()));
        battle.processRequests();
        Summon ika = (Summon) battle.memospriteOf(hyacine);
        Assertions.assertNotNull(ika, "precondition: 小伊卡 is out");
        ika.getBuffManager().addBuff(new StateBuff("测试持续效果", 3));

        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine,
                List.of(battle.enemies().getFirst()));
        battle.processRequests();

        Assertions.assertAll("忆灵技2: free turn and its own state clock",
                () -> Assertions.assertTrue(settled.stream().anyMatch(d -> d.getAttacker() == ika),
                        "「并自动施放【乌云乌云快走开！】」: an instance from the memosprite settled (instances="
                                + settled.size() + ")"),
                () -> Assertions.assertEquals(2, ika.getBuffManager().activeBuffs().getFirst().remainingTurns(),
                        "「施放技能后使自身所有持续效果持续回合数减 1」: 3 -> 2"));
    }

    /**
     * 风堇's skill, the half that had no reader: 「**为小伊卡回复**等同于风堇 <b>#3%</b> 生命上限 + <b>#4</b> 的生命值」.
     *
     * <p>The row is {@code 1409/2} = {@code [0.08, 160, 0.10, 200]} at Lv10: {@code #1/#2} are the party heal (delivered
     * by the skill data) and {@code #3/#4} belong to 小伊卡 alone. A judge that only measures the party heal cannot see
     * this half at all - 小伊卡's own HP is what moves here.
     */
    @Test
    public void theSkillHealsIkachiByItsOwnShareOfHerMaxHpPlusFlat() {
        Character hyacine = CharacterFactory.create(HYACINE, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.dummy()),
                BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        battle.summonMemosprite(hyacine);
        battle.processRequests();
        Summon ika = (Summon) battle.memospriteOf(hyacine);
        Assertions.assertNotNull(ika, "precondition: 小伊卡 is out");

        // Leave room for the heal on BOTH sides, so the amount is what the rule delivers and not the cap.
        ally.consumeHp(ally.getMaxHp() * 0.5);
        // It receives BOTH halves now: 「我方全体」 (0.08/160) and 「为小伊卡」 (0.10/200). Leave nearly all of its
        // bar empty so two heals fit under its own cap.
        ika.consumeHp(ika.getMaxHp() * 0.98);
        double herMaxHpBefore = hyacine.getMaxHp();
        double ikaBefore = ika.getCurrentHp();

        SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine, List.of(ally));
        battle.processRequests();

        // Both clauses land on it: it is 我方一员 (0.08/160) AND the one 「为小伊卡」 names (0.10/200).
        double expected = (0.08 + IKACHI_SHARE_AT_LV10) * herMaxHpBefore + 160 + IKACHI_FLAT_AT_LV10;
        Assertions.assertEquals(expected, ika.getCurrentHp() - ikaBefore, Math.max(1e-6, expected * 1e-9),
                "「为小伊卡回复 #3% + #4」 on top of 「我方全体 #1% + #2」 at Lv10 = 0.18 x " + herMaxHpBefore + " + 360");
    }

    /**
     * 忆灵技2, first clause: the memosprite pays 4% of its OWN Max HP and heals whoever lost HP for 2% of 风堇 Max HP +
     * 20.
     *
     * <p>The heal is measured as the DIFFERENCE between two battles (with and without 小伊卡), never by predicting the HP
     * after the hit: a {@code Damage} goes through the defence and resistance zones, so "20% of Max HP" is not what the
     * victim loses. Assuming it was made this rule look like it paid twice.
     */
    @Test
    public void ikachiPaysAndHealsWhoeverLostHp() {
        Run withoutIka = new Run(false);
        Run withIka = new Run(true);
        double itsHpBefore = withIka.ika.getCurrentHp();

        withIka.hitOnce();
        withoutIka.hitOnce();

        double paid = itsHpBefore - withIka.ika.getCurrentHp();
        Assertions.assertEquals(withIka.ika.getMaxHp() * 0.04, paid, Math.max(CHARITY_EPS, itsHpBefore * 1e-9),
                "paid 4% of 小伊卡 OWN Max HP (its=" + withIka.ika.getMaxHp() + ", paid=" + paid + ")");

        double heal = withIka.woundedAfter - withoutIka.woundedAfter;
        double expected = withIka.hyacine.getAttribute(AttributeType.HEALTH).get() * 0.02 + 20;
        Assertions.assertEquals(expected, heal, Math.max(CHARITY_EPS, expected * 1e-6),
                "2% of 风堇 Max HP + 20 to the ally that lost HP (with=" + withIka.woundedAfter + ", without="
                        + withoutIka.woundedAfter + ")");

        Assertions.assertEquals(withoutIka.healthyAfter, withIka.healthyAfter, CHARITY_EPS,
                "an ally that took no damage is not healed");
    }

    /**
     * 忆灵技4 「消失时，使风堇行动提前 30%」 and the other half of her Rank 6 aura (「小伊卡在场时…」 -- so it has to be
     * gone when the memosprite is not there), both read on the same departure.
     *
     * <p>Both hang on {@code SUMMON_LEFT}, the event that says "the memosprite left the field": a memosprite can go away
     * without being killed ({@code CanHit.perish()} is the engine's "it went away, nobody killed it", and by its own
     * javadoc it emits no {@code HpLoss} / {@code Kill}), so {@code DEATH} is not the same fact.
     *
     * <p>「使风堇行动提前 30%」 is a share of her REMAINING wait, so the clock after is 70% of the one before.
     */
    @Test
    public void whenIkachiLeavesTheAuraGoesAndHerActionAdvances() {
        LeavesFixture f = new LeavesFixture(6);
        Assertions.assertEquals(0.2, f.pen(f.hyacine), EPS, "precondition: the aura is up while 小伊卡 is out");
        double waitBefore = f.remainingWaitOf(f.hyacine);

        f.ika.perish();
        f.battle.processRequests();
        double waitAfter = f.remainingWaitOf(f.hyacine);
        double hyacinePenAfter = f.pen(f.hyacine);
        double allyPenAfter = f.pen(f.ally);

        // The readout is the action bar's own snapshot (Queue.snapshot + getTimeRemaining); the battle clock does not
        // move.
        Assertions.assertAll("忆灵技4: departure",
                () -> Assertions.assertEquals(0.0, hyacinePenAfter, EPS,
                        "「小伊卡在场时」 -- the aura must not outlive the memosprite"),
                () -> Assertions.assertEquals(0.0, allyPenAfter, EPS, "and it was a party-wide aura"),
                () -> Assertions.assertEquals(waitBefore * 0.7, waitAfter, Math.max(1e-9, waitBefore * 1e-9),
                        "「消失时，使风堇行动提前 30%」 (remaining before=" + waitBefore + ", after=" + waitAfter + ")"));
    }

    /** The aura is Rank 6's; E0 has nothing to take back. */
    @Test
    public void withoutRankSixNothingIsGivenBack() {
        LeavesFixture f = new LeavesFixture(0);
        double before = f.pen(f.hyacine);
        f.ika.perish();
        f.battle.processRequests();
        Assertions.assertEquals(before, f.pen(f.hyacine), EPS, "the aura is Rank 6's; E0 has nothing to take back");
    }

    /**
     * 「对敌方全体造成等同于本场战斗风堇与小伊卡累计治疗数值 #1%」: another character's memosprite casting ITS slot 1
     * must not be counted as 小伊卡's cast.
     *
     * <p>The same clause at Rank 6, where 「清空改为 12%」 is a <b>twin rule</b> (`min_eidolon: 6`) - so this gate has to
     * be right in two places, and a judge that only fields E0 would let the twin drift back. Both ends are read on the
     * fixture that keeps 小伊卡 on the field as the control.
     */
    @Test
    public void anotherCharactersMemospriteCastIsNotHerOwnAtEitherRank() {
        OwnMemospriteFixture e0 = new OwnMemospriteFixture(0);
        double tallyBeforeE0 = e0.tally();
        Assertions.assertTrue(tallyBeforeE0 > 0, "precondition: the heal fed the tally");
        List<Damage> byIka = e0.castOtherMemospriteSlotOne();
        double tallyAfterE0 = e0.tally();

        OwnMemospriteFixture e6 = new OwnMemospriteFixture(6);
        double tallyBeforeE6 = e6.tally();
        Assertions.assertTrue(tallyBeforeE6 > 0, "precondition: the heal fed the tally");
        e6.castOtherMemospriteSlotOne();
        double tallyAfterE6 = e6.tally();

        Assertions.assertAll("小伊卡's cast gate, at E0 and Rank 6",
                () -> Assertions.assertTrue(byIka.isEmpty(),
                        "the damage is 「小伊卡对敌方全体造成…」 -- 长夜 casting its own slot 1 is not 小伊卡's cast, but "
                                + byIka.size() + " instance(s) were attributed to 小伊卡"),
                () -> Assertions.assertEquals(tallyBeforeE0, tallyAfterE0, EPS,
                        "and 「清空累计治疗数值的 50%」 is 小伊卡's cast's consequence, not anyone's: tally was "
                                + tallyBeforeE0 + ", now " + tallyAfterE0),
                () -> Assertions.assertEquals(tallyBeforeE6, tallyAfterE6, EPS,
                        "Stars 6: 「清空累计治疗数值改为 12%」 is still 小伊卡's cast's consequence (tally was "
                                + tallyBeforeE6 + ", now " + tallyAfterE6 + ")"));
    }

    /**
     * 「消失时，使风堇行动提前 30%」 and the Rank 6 aura's 「小伊卡在场时」: another character's memosprite leaving is
     * not 小伊卡 leaving.
     */
    @Test
    public void anotherCharactersMemospriteLeavingIsNotHerOwn() {
        OwnMemospriteFixture f = new OwnMemospriteFixture(6);
        double waitBefore = f.remainingWaitOf(f.hyacine);
        double penBefore = f.hyacine.getAttribute(AttributeType.DAMAGE_PENETRATION).get();
        Assertions.assertEquals(0.2, penBefore, EPS, "precondition: the Rank 6 aura is up while 小伊卡 is out");

        f.longNight.perish();
        f.battle.processRequests();

        Assertions.assertEquals(waitBefore, f.remainingWaitOf(f.hyacine), Math.max(EPS, waitBefore * EPS),
                "长夜 leaving is not 小伊卡 leaving, so 风堇's action must not move (was " + waitBefore + ", now "
                        + f.remainingWaitOf(f.hyacine) + ")");
        Assertions.assertEquals(penBefore, f.hyacine.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), EPS,
                "and the aura 「小伊卡在场时」 must stay up: 小伊卡 is still there");
    }

    /**
     * 「小伊卡施放技能后使自身所有持续效果持续回合数减 1」: another character's memosprite acting must not shorten
     * 小伊卡's clock.
     */
    @Test
    public void anotherCharactersMemospriteActingDoesNotShortenItsOwnEffects() {
        OwnMemospriteFixture f = new OwnMemospriteFixture(0);
        f.ika.getBuffManager().addBuff(new StateBuff("测试", 3));
        Assertions.assertEquals(3, f.turnsOf(f.ika, "测试"), "precondition: three turns to start with");

        f.castOtherMemospriteSlotOne();

        Assertions.assertEquals(3, f.turnsOf(f.ika, "测试"),
                "「小伊卡施放技能后」 means 小伊卡's own cast -- 长夜 acting must not take a turn off her state");
    }

    /**
     * 忆灵技1 【乌云乌云快走开！】 (data slot 1 of ServantID 11409) -- one cast, both halves of the sentence:
     *
     * <pre>
     * 「对敌方全体造成等同于本场战斗风堇与小伊卡累计治疗数值 #1% 的风属性伤害并清空累计治疗数值的 50%。」
     * params: [0.1, 0.5] .. [0.28, 0.5]      Rank 6: 「清空累计治疗数值改为 12%」 params [0.12, 0.2]
     * </pre>
     *
     * <p>The damage is a share of the <b>tally</b>, read off the memosprite's own level row -- not of its ATK, which is
     * 0; and the tally is cleared <b>after</b> the damage reads it (clearing first would make the damage 0).
     *
     * <p>⚠ The memosprite's own data row still settles a {@code %ATK} instance, and with ATK = 0 that leaves the
     * engine's <b>1-point floor</b>. The damage assertion therefore names that point instead of hiding it.
     */
    @Test
    public void theCastDealsTheTallysShareThenClearsHalfOfIt() {
        RaincloudsFixture f = new RaincloudsFixture(0);
        double tally = f.tally();
        Assertions.assertTrue(tally > 0, "precondition: something was healed and tallied");

        double dealt = f.castRainclouds();
        double tallyAfter = f.tally();

        // The base is the TALLY (not the memosprite's ATK, which is 0), the data row delivers nothing (skill_effects says
        // `Rules`), and because the rule says `"attacker": "summon"` the instance is the MEMOSPRITE's -- so her talent's
        // +80% per layer (one layer, from the heal that fed the tally) lands on it.
        Assertions.assertAll("忆灵技1: damage and cleanup",
                () -> Assertions.assertTrue(DATA_FLOOR > 0, "kept for the note: the floor is gone"),
                () -> Assertions.assertEquals(tally * RAINCLOUDS_SHARE_AT_LV10 * 1.8, dealt,
                        Math.max(1e-6, tally * 1e-9),
                        "「造成等同于累计治疗数值 #1% 的风属性伤害」 by 小伊卡: tally=" + tally + ", dealt=" + dealt
                                + " (expected tally x " + RAINCLOUDS_SHARE_AT_LV10
                                + " x 1.8 for one talent layer, no data floor)"),
                () -> Assertions.assertEquals(Math.round(tally * 0.5), tallyAfter,
                        "「清空累计治疗数值的 50%」 removes half of what the damage just read (was " + tally + ", now "
                                + tallyAfter + ")"));
    }

    /** Rank 6's 「清空改为 12%」 REPLACES the 50%, it does not stack with it. */
    @Test
    public void rankSixClearsTwelvePercentInsteadOfFifty() {
        RaincloudsFixture f = new RaincloudsFixture(6);
        double tally = f.tally();
        Assertions.assertTrue(tally > 0, "precondition: something was healed and tallied");

        f.castRainclouds();

        // 12% removed, so 88% is left -- NOT 50%-and-then-12% (which would leave 44%).
        Assertions.assertEquals(Math.round(tally * 0.88), f.tally(),
                "Stars 6: 「清空改为 12%」 REPLACES the 50%, it does not stack with it (was " + tally + ", now "
                        + f.tally() + ")");
    }

    private static Battle battle(Character hyacine) {
        Battle battle = new Battle(List.of(hyacine, CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    /** 风堇 + 1413 + a wounded ally, both memsprites out, one heal already counted into the tally. */
    private static final class OwnMemospriteFixture {
        final Battle battle;
        final Character hyacine;
        final Character evernight;
        final Summon ika;
        final Summon longNight;

        OwnMemospriteFixture(int eidolon) {
            this.hyacine = CharacterFactory.create(HYACINE, 80, true, null, null, eidolon);
            this.evernight = CharacterFactory.create(EVERNIGHT, 80);
            Character ally = CharacterFactory.create(ALLY, 80);
            this.battle = new Battle(List.of(hyacine, evernight, ally), List.of(BattleFixture.dummy()),
                    BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
            // Both memsprites are stated by the fixture, not left to a kit rule: hers is summoned by her own skill /
            // ultimate, and the point here is the CONTROL (her own memosprite standing beside someone else's).
            battle.summonMemosprite(hyacine);
            battle.processRequests();
            this.ika = (Summon) battle.memospriteOf(hyacine);
            Summon other = (Summon) battle.memospriteOf(evernight);
            if (other == null) {
                // 1413's own rule summons it at battle start; a fixture that states its own premise must not depend on
                // that (and `summonMemosprite` is idempotent, so this is a no-op when the rule did run).
                other = (Summon) battle.summonMemosprite(evernight);
                battle.processRequests();
            }
            this.longNight = other;
            Assertions.assertNotNull(ika, "precondition: 小伊卡 is out");
            Assertions.assertNotNull(longNight, "precondition: 长夜 is out -- the 'other character's memosprite'");
            ally.consumeHp(ally.getAttribute(AttributeType.HEALTH).get() * 0.5);
            SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine, List.of(ally));
            battle.processRequests();
        }

        double tally() {
            return hyacine.getResources().value(TALLY);
        }

        /** Has the OTHER character's memosprite cast its own data slot 1, and reports what was attributed to 小伊卡. */
        List<Damage> castOtherMemospriteSlotOne() {
            Skill slotOne = longNight.skillAt(1);
            Assertions.assertNotNull(slotOne, "precondition: 长夜 carries data slot 1");
            List<Damage> byIka = new ArrayList<>();
            battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> {
                if (damage.getAttacker() == ika) {
                    byIka.add(damage);
                }
            });
            SkillExecutor.execute(battle, slotOne, longNight, List.of(battle.enemies().getFirst()));
            battle.processRequests();
            return byIka;
        }

        int turnsOf(CanHit unit, String state) {
            return unit.getBuffManager().allBuffsOf(StateBuff.class).stream()
                    .filter(buff -> state.equals(buff.getState()))
                    .mapToInt(StateBuff::remainingTurns)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("the state 「" + state + "」 is gone"));
        }

        /** The unit's remaining wait, read off the action bar's own snapshot. */
        double remainingWaitOf(CanHit unit) {
            for (var signal : battle.queue().snapshot()) {
                if (signal.getCanHit() == unit) {
                    return battle.queue().getTimeRemaining(signal);
                }
            }
            throw new AssertionError(unit.getName() + " is not on the action bar");
        }
    }

    /** 风堇 + a wounded ally, 小伊卡 out, and one heal already counted into the tally. */
    private static final class RaincloudsFixture {
        final Battle battle;
        final Character hyacine;

        RaincloudsFixture(int eidolon) {
            this.hyacine = CharacterFactory.create(HYACINE, 80, true, null, null, eidolon);
            Character ally = CharacterFactory.create(ALLY, 80);
            this.battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
            battle.summonMemosprite(hyacine);
            battle.processRequests();
            ally.consumeHp(ally.getAttribute(AttributeType.HEALTH).get() * 0.5);
            SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine, List.of(ally));
            battle.processRequests();
        }

        double tally() {
            return hyacine.getResources().value(TALLY);
        }

        /** Casts 忆灵技1 and returns what it cost the enemy. */
        double castRainclouds() {
            Summon ika = (Summon) battle.memospriteOf(hyacine);
            Assertions.assertNotNull(ika, "precondition: 小伊卡 is out");
            var enemy = battle.enemies().getFirst();
            double before = enemy.getCurrentHp();
            SkillExecutor.execute(battle, ika.skillAt(1), ika, List.of(enemy));
            battle.processRequests();
            return before - enemy.getCurrentHp();
        }
    }

    /** 风堇 + an ally, 小伊卡 out by her skill, so the Rank 6 aura has something to be taken back from. */
    private static final class LeavesFixture {
        final Battle battle;
        final Character hyacine;
        final Character ally;
        final Summon ika;

        LeavesFixture(int eidolon) {
            this.hyacine = CharacterFactory.create(HYACINE, 80, true, null, null, eidolon);
            this.ally = CharacterFactory.create(ALLY, 80);
            this.battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.dummy()),
                    BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
            SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine,
                    List.of(battle.enemies().getFirst()));
            battle.processRequests();
            this.ika = (Summon) battle.memospriteOf(hyacine);
            Assertions.assertNotNull(ika, "precondition: 小伊卡 is out");
        }

        /** 风堇 的剩余等待，从行动条自己的快照读。 */
        double remainingWaitOf(CanHit unit) {
            for (var signal : battle.queue().snapshot()) {
                if (signal.getCanHit() == unit) {
                    return battle.queue().getTimeRemaining(signal);
                }
            }
            throw new AssertionError(unit.getName() + " is not on the action bar");
        }

        double pen(CanHit unit) {
            return unit.getAttribute(AttributeType.DAMAGE_PENETRATION).get();
        }
    }

    private static final class Run {
        final Battle battle;
        final Character hyacine;
        final Character wounded;
        final Character healthy;
        final Summon ika;
        double woundedAfter;
        double healthyAfter;

        Run(boolean summon) {
            this.hyacine = CharacterFactory.create(HYACINE, 80);
            this.wounded = CharacterFactory.create(ALLY, 80);
            this.healthy = CharacterFactory.create(HEALTHY, 80);
            this.battle = new Battle(List.of(hyacine, wounded, healthy), List.of(BattleFixture.dummy()),
                    BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
            if (summon) {
                SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine,
                        List.of(battle.enemies().getFirst()));
                battle.processRequests();
            }
            this.ika = (Summon) battle.memospriteOf(hyacine);
            if (summon) {
                Assertions.assertNotNull(ika, "precondition: 小伊卡 is out");
            }
        }

        void hitOnce() {
            battle.applyDamage(wounded, new Damage(battle.enemies().getFirst(), wounded, DamageElement.PHYSICAL,
                    DamageType.NORMAL, wounded.getMaxHp() * 0.2));
            battle.processRequests();
            this.woundedAfter = wounded.getCurrentHp();
            this.healthyAfter = healthy.getCurrentHp();
        }
    }
}
