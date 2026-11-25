package com.laosun.aluminium.test.content.characters.natasha;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Natasha (1105): the two skill-level Eidolons, the Eidolon that pays energy when she is hit, the two heals with the
 * regeneration that rides on the Skill, the outgoing-heal trace, the talent's low-HP bonus, and the technique that
 * weakens every enemy.
 *
 * <h2>What the sections below are about</h2>
 *
 * <ul>
 *   <li><b>星魂 3 对症下药 / 星魂 5 医治未病.</b> 「战技等级+2 / 普攻等级+1」 and 「终结技等级+2 / 天赋等级+2」: four different
 *       skills across the two, so neither rule may be the other with a wrong token. Measured through
 *       {@code skillLevelBonus(slot)}, which reads the raise itself rather than the level a skill ends up at -- the
 *       resulting level depends on the data row's own base level, so asserting on it would pin the data row. ⚠ Deliberately
 *       NOT asserted: the 「最多不超过15级 / 10级」 caps, which the precedents (1103, 1109, 1215) also leave to the data
 *       rather than clamping in a rule.</li>
 *   <li><b>星魂 4 妙手回春.</b> 「受到攻击后，额外恢复5点能量。」 Two failures are possible and both are measured rather than
 *       assumed: the <b>gate</b> (the clause belongs to Eidolon 4, so at rank 0 no bonus is paid -- stated as a
 *       <b>difference of differences</b>, because taking a hit already pays {@link Constant#ENERGY_GAIN_HIT} energy on its
 *       own), and <b>whose hit</b> ({@code TAKING_HIT} is fired with the <b>attacker</b> as actor and the <b>victim</b> as
 *       target (Battle.java:1730), so the rule reads {@code when: ["target == self"]}; without it a hit on a teammate would
 *       top Natasha up from across the field, and since the baseline gain goes to the victim that error would look like a
 *       plausible number rather than an absurd one).</li>
 *   <li><b>The heals and the regeneration.</b> The Skill heals one aimed ally by 「10.50%生命上限+280」 and the Ultimate
 *       heals the whole side by 「13.80%生命上限+368」, both read off the healer's <b>settled max HP</b> ({@code scale:
 *       self_max_hp}), both times the Healer (医者) trace's +10%; the regeneration settles on each of the carrier's turns
 *       (three here, because the Recuperation (调理) trace adds a turn) and a second cast <b>restarts</b> it instead of
 *       piling another one on.</li>
 *   <li><b>行迹 医者.</b> 「娜塔莎提供的治疗量提高10%」 -- the provider's side, read through {@code OUTGOING_HEALING_BOOST}
 *       as a ratio between two healers healing the same kind of target by the same base amount (the upstream row
 *       AvatarSkillTreeConfig, PointID 1105102, states {@code [0.1]}).</li>
 *   <li><b>天赋 生机焕发.</b> 「为当前生命值百分比小于等于30%的我方目标提供治疗时，娜塔莎的治疗量提高50%，该效果对持续治疗效果也会生效。」
 *       -- every case compares the <b>same heal</b> above the threshold against one at or below it rather than asserting an
 *       absolute amount, so a bonus that never applies and one that always applies are both caught; the regeneration tick
 *       must carry it too, and the threshold is inclusive (「小于等于30%」).</li>
 *   <li><b>秘技 催眠研习.</b> 「立即攻击敌人…对敌方随机单体造成等同于娜塔莎80%攻击力的物理属性伤害，并有100%的基础概率使敌方每个单体目标陷入虚弱状态。虚弱状态下的敌方目标对我方造成的伤害降低30%，持续1回合。」
 *       -- 「随机单体」 is ONE enemy while 「每个单体目标」 is EVERY enemy, so the two halves are measured separately; the
 *       Weaken half is asserted on {@code ALL_DAMAGE_TYPE_BOOST -0.3} because the buff itself carries no number; and the
 *       gate is {@code self has_state sys:technique}, so every case contrasts against the same battle without it.</li>
 * </ul>
 *
 * <p>These were six small files in the same package; one class keeps the character together without changing any
 * assertion.
 *
 * <p><b>L3:</b> the single-clause cases that read one clause on one fixture and one observed object are grouped into the
 * scenario tests below, one {@code Assertions.assertAll} per fixture, so a failing clause still names itself. Nothing was
 * dropped: every expected value, tolerance and message is the one the separate cases carried, different fixtures stay in
 * different tests, and this class has no rejection path that could have been folded into a positive one.
 */
public class NatashaTest {

    private static final int NATASHA = 1105;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int TEAMMATE = 1109;
    private static final int ALLY = 1002;
    /**
     * A healer with no such rule, used as the control.
     */
    private static final int PLAIN = 1002;
    private static final int TARGET_ALLY = 1210;
    private static final double BASE_HEAL = 500;

    /** A monster with no special resistance, so only the technique is being measured. */
    private static final int TECHNIQUE_MONSTER = 1003010;

    /**
     * What the low-HP ratio measures: the talent's 1.5 <b>times</b> her 行迹 医者's 1.1.
     *
     * <p>Both factors are present on the low-HP side because both are rules in her table, and the comparison side is
     * built by emptying that table -- so the measured ratio is {@code 1.5 × 1.1}. The talent's own share is recovered
     * by dividing by the healthy ratio (which carries 医者 but not the talent).
     *
     * <p>Pinned this way because the two neighbouring implementations differ by exactly these numbers: adding the 50
     * as <b>percentage points</b> to the outgoing-healing zone would give 1.6, and ignoring 医者 would give 1.5.
     */
    private static final double TALENT_TIMES_MEDIC = 1.5 * 1.1;

    /**
     * The ally's HP fraction when it is meant to be <b>above</b> the talent's threshold.
     *
     * <p>Deliberately not 1.0: at full HP a heal restores 0, so a comparison there would be 0 vs 0.
     */
    private static final double HEALTHY_FRACTION = 0.8;

    /**
     * The ally's HP fraction when it is meant to be <b>below</b> the threshold.
     */
    private static final double LOW_FRACTION = 0.2;

    // ==================================================================
    // 星魂 3 对症下药 / 星魂 5 医治未病 -- the two skill-level Eidolons
    // ==================================================================

    /**
     * At rank 0 neither Eidolon is unlocked, so no slot carries a raise.
     *
     * <p>This is the case that fails if either rule loses its {@code min_eidolon}.
     */
    @Test
    public void noEidolonMeansNoRaise() {
        for (SkillType slot : List.of(SkillType.SKILL, SkillType.COMMON, SkillType.ULTRA, SkillType.TALENT)) {
            Assertions.assertEquals(0, bonus(slot, 0),
                    "rank 0 has no skill-level Eidolon, so " + slot + " must carry no raise");
        }
    }

    /**
     * 星魂 3 raises the Skill by 2 and the basic attack by 1, 星魂 5 the Ultimate and the Talent by 2 -- and neither says
     * anything about the other's slots.
     *
     * <p>One scenario, because all nine readings come off the same fixture ({@code bonus(slot, rank)}: one battle at one
     * Eidolon rank, read through {@code skillLevelBonus}) and observe the same object, the raise a slot carries. The
     * last three readings pin the two Eidolons as independent: rank 5's Skill is weighed against rank 3's, and rank 3's
     * Ultimate is asked to be untouched because the other rank pays it.
     */
    @Test
    public void theTwoEidolonsRaiseTheirOwnSlotsAndNothingElse() {
        Assertions.assertAll("星魂 3 对症下药 / 星魂 5 医治未病",
                () -> Assertions.assertEquals(2, bonus(SkillType.SKILL, 3), "「战技等级+2」"),
                () -> Assertions.assertEquals(1, bonus(SkillType.COMMON, 3), "「普攻等级+1」"),
                () -> Assertions.assertEquals(0, bonus(SkillType.ULTRA, 3), "星魂 3 says nothing about the Ultimate"),
                () -> Assertions.assertEquals(0, bonus(SkillType.TALENT, 3), "星魂 3 says nothing about the Talent"),
                () -> Assertions.assertEquals(2, bonus(SkillType.ULTRA, 5), "「终结技等级+2」"),
                () -> Assertions.assertEquals(2, bonus(SkillType.TALENT, 5), "「天赋等级+2」"),
                () -> Assertions.assertEquals(0, bonus(SkillType.SKILL, 5) - bonus(SkillType.SKILL, 3),
                        "rank 5 adds nothing to the Skill beyond 星魂 3's own +2"),
                () -> Assertions.assertEquals(0, bonus(SkillType.ULTRA, 3),
                        "the Ultimate raise belongs to rank 5, not to rank 3"),
                () -> Assertions.assertEquals(2, bonus(SkillType.SKILL, 5),
                        "rank 5 still carries 星魂 3's Skill raise, and 星魂 5 adds nothing on top of it"));
    }

    /**
     * The raise a slot carries at the given rank.
     *
     * @param slot    which skill
     * @param eidolon her Eidolon rank
     * @return the levels this battle has raised that slot by
     */
    private static int bonus(SkillType slot, int eidolon) {
        // The file carries the corpus-wide level convention (+9 to every slot, so every number matches the Lv10 row the
        // document quotes), so this asks for the raise ABOVE rank 0 rather than the absolute bonus.
        return level(slot, eidolon) - level(slot, 0);
    }

    private static int level(SkillType slot, int eidolon) {
        Character natasha = CharacterFactory.create(NATASHA, LEVEL, false, null, null, eidolon);
        Battle battle = new Battle(List.of(natasha),
                List.of(EnemyFactory.create(MONSTER, LEVEL, 1)), BattleFixture.alwaysRoll(1.0));
        battle.startBattle();
        return battle.characters().getFirst().skillLevelBonus(slot);
    }

    // ==================================================================
    // 星魂 4 妙手回春 -- energy from a hit taken
    // ==================================================================

    /**
     * At Eidolon 4 she gains the hit's usual energy <b>plus 5</b>; at Eidolon 0, only the usual, and a hit on a
     * <b>teammate</b> pays her nothing at all.
     *
     * <p>One scenario, because all four readings come off the same fixture ({@code energyGained(eidolon, targetHer)}:
     * one battle, one hit, the energy credited to her) and observe the same object. The gate and the victim are both
     * measured rather than assumed: the baseline {@link Constant#ENERGY_GAIN_HIT} goes to the victim, so a hit on an ally
     * credits <b>that ally</b>, and a rule missing {@code target == self} would top Natasha up from across the field --
     * which the {@code onMate == 0} reading catches. The last reading contrasts the same hit on herself so that the 15
     * there is visibly the standard gain plus the rule's 5 rather than the rule alone.
     */
    @Test
    public void theEnergyFromAHitIsEidolonFoursAndOnlyForHitsOnHer() {
        double rank4 = energyGainedFromHitOnHer(4);
        double rank0 = energyGainedFromHitOnHer(0);
        double onMate = energyGainedFromHitOnTeammate(4);
        double onHer = energyGainedFromHitOnHer(4);

        Assertions.assertAll("星魂 4 妙手回春: 受到攻击后，额外恢复5点能量",
                () -> Assertions.assertEquals(Constant.ENERGY_GAIN_HIT, rank0, 1e-9,
                        "a hit always pays the standard gain; at Eidolon 0 that is all it pays"),
                () -> Assertions.assertEquals(5.0, rank4 - rank0, 1e-9,
                        "「受到攻击后，额外恢复5点能量」 -- the rule's own share, measured against the same hit at "
                                + "Eidolon 0: " + rank4 + " vs " + rank0),
                () -> Assertions.assertEquals(0.0, onMate, 1e-9,
                        "an ally's hit credits that ally; the rule is about hits on HER, so she gains nothing"),
                () -> Assertions.assertEquals(Constant.ENERGY_GAIN_HIT + 5.0, onHer, 1e-9,
                        "a hit on her pays the standard gain plus the rule's 5: " + onHer));
    }

    /**
     * Energy Natasha gains when one hit lands on her.
     *
     * @param eidolon her Eidolon rank
     * @return the energy credited
     */
    private static double energyGainedFromHitOnHer(int eidolon) {
        return energyGained(eidolon, true);
    }

    /**
     * Energy Natasha gains when one hit lands on a teammate.
     *
     * @param eidolon her Eidolon rank
     * @return the energy credited
     */
    private static double energyGainedFromHitOnTeammate(int eidolon) {
        return energyGained(eidolon, false);
    }

    /**
     * Builds the battle, lands one hit on the chosen victim and reports what Natasha gained.
     *
     * @param eidolon    her Eidolon rank
     * @param targetHer  {@code true} to hit Natasha, {@code false} to hit her teammate
     * @return the energy credited to Natasha
     */
    private static double energyGained(int eidolon, boolean targetHer) {
        Character natasha = CharacterFactory.create(NATASHA, LEVEL, false, null, null, eidolon);
        Character mate = CharacterFactory.create(TEAMMATE, LEVEL, false, null, null, 0);
        Battle battle = new Battle(List.of(natasha, mate),
                List.of(EnemyFactory.create(MONSTER, LEVEL, 1)), BattleFixture.alwaysRoll(1.0));
        battle.startBattle();

        Character victim = targetHer ? natasha : mate;
        double before = natasha.getCurrentEnergy();

        Enemy attacker = battle.enemyUnits().getFirst();
        battle.applyDamage(victim, new Damage(attacker, victim, DamageElement.PHYSICAL, DamageType.NORMAL, 100));

        return natasha.getCurrentEnergy() - before;
    }

    // ==================================================================
    // The heals, the regeneration and the Healer (医者) trace
    // ==================================================================

    /**
     * The heal really is a share of her max HP plus the constant, and it goes to the aimed ally.
     */
    @Test
    public void herSkillHealsTheAimedAlly() {
        Fixture f = new Fixture();
        // Note: A fraction of max HP, not a flat 3000: a level-80 ally has far less than that, and a flat 3000 would kill the
        // ally the heal is for (measured: the hit (命中) assertion reads 0.0).
        f.ally.takeDamage(f.ally.getMaxHp() * 0.5);
        double before = f.ally.getCurrentHp();
        // The Healer (医者) trace (+10% outgoing healing) is on the same file, so the settled heal is the row times 1.1.
        // The file carries the corpus-wide level convention (+9 to every slot), so the engine reads the Lv10 row the
        // document quotes: [0.105, 280]. The +10% is the Healer (医者) trace, live on this character.
        double expected = (0.105 * f.natasha.getMaxHp() + 280) * 1.1;

        double herBefore = f.natasha.getCurrentHp();
        f.castSkillOn(f.ally);

        Assertions.assertAll("「立即为指定我方单体回复等同于娜塔莎10.50%生命上限+280的生命值」",
                () -> Assertions.assertEquals(Math.min(f.ally.getMaxHp(), before + expected), f.ally.getCurrentHp(), 1.0,
                        "「立即为指定我方单体回复等同于娜塔莎10.50%生命上限+280的生命值」 (immediately restores HP to a designated single ally equal to 10.50% of Natasha (娜塔莎)'s Max HP + 280)"),
                () -> Assertions.assertEquals(herBefore, f.natasha.getCurrentHp(), 1.0,
                        "「指定我方单体」 (a designated single ally) is the ally, not her -- ⚠ the first version compared the ALLY's HP with hers"));
    }

    /**
     * Her ultimate heals the whole side.
     */
    @Test
    public void herUltimateHealsEveryAlly() {
        Fixture f = new Fixture();
        f.natasha.takeDamage(f.natasha.getMaxHp() * 0.5);
        f.ally.takeDamage(f.ally.getMaxHp() * 0.5);   // Note: otherwise "我方全体" (all of our side) has nothing to restore on the ally
        double allyBefore = f.ally.getCurrentHp();
        double herBefore = f.natasha.getCurrentHp();

        // Note: The target set is the CALLER's: the engine heals the units it is handed (the skill's own AoE shape is
        // resolved upstream), so both allies are named here.
        f.battle.castImmediate(f.natasha.getSkills().get(SkillType.ULTRA), f.natasha, List.of(f.ally, f.natasha));

        double expected = (0.138 * f.natasha.getMaxHp() + 368) * 1.1;   // Lv10 row [0.138, 368], plus the Healer (医者) trace
        Assertions.assertAll("「立即为我方全体回复等同于娜塔莎13.80%生命上限+368」",
                () -> Assertions.assertEquals(Math.min(f.ally.getMaxHp(), allyBefore + expected), f.ally.getCurrentHp(), 1.0,
                        "「立即为我方全体回复等同于娜塔莎13.80%生命上限+368」 (the Lv10 row the prose quotes; the engine runs Lv1)"),
                () -> Assertions.assertEquals(Math.min(f.natasha.getMaxHp(), herBefore + expected), f.natasha.getCurrentHp(), 1.0,
                        "…and 「我方全体」 (all of our side) includes her"));
    }

    /**
     * The Healer (医者) trace raises the outgoing healing, which is read from the healer while the amount is computed.
     */
    @Test
    public void herTraceRaisesHerOutgoingHealing() {
        Fixture f = new Fixture();
        double boost = f.natasha.getAttribute(com.laosun.aluminium.enums.AttributeType.OUTGOING_HEALING_BOOST).get();
        Assertions.assertEquals(0.1, boost, 1e-6,
                "「娜塔莎提供的治疗量提高10%」 (the healing Natasha provides is raised by 10%) -- the first shipped content to grant this attribute");
    }

    /**
     * The regeneration settles on each of the carrier's turns, and then stops.
     *
     * <p>Note: This is the case a plain turn count cannot express: "同时目标每回合开始时为其回复…持续2回合" cannot be a
     * {@code TURN_START} rule, because buffs are counted down by the early tick that runs <b>before</b> that event - a
     * {@code turns: 2} state would heal once where the document says twice. `APPLY_REGEN` settles beside the DOT pass,
     * which runs before the countdown, so two turns really are two heals (three here: the Recuperation (调理) trace adds a turn).
     */
    @Test
    public void theRegenerationTicksOnEachOfTheCarriersTurns() {
        Fixture f = new Fixture();
        f.ally.takeDamage(f.ally.getMaxHp() * 0.5);
        f.castSkillOn(f.ally);
        double perTick = (0.072 * f.natasha.getMaxHp() + 192) * 1.1;    // Lv10 row #2/#5, plus the Healer (医者) trace
        double afterCast = f.ally.getCurrentHp();

        f.allyTurn();
        Assertions.assertEquals(Math.min(f.ally.getMaxHp(), afterCast + perTick), f.ally.getCurrentHp(), 1.0,
                "first turn start: 「目标每回合开始时为其回复…」 (restores ... to the target at the start of every turn)");

        double afterFirst = f.ally.getCurrentHp();
        f.allyTurn();
        Assertions.assertEquals(Math.min(f.ally.getMaxHp(), afterFirst + perTick), f.ally.getCurrentHp(), 1.0,
                "second turn start: a `turns: 2` regeneration settles twice, which is the whole point of settling before "
                        + "the duration is counted down");

        double afterThird = f.ally.getCurrentHp();
        f.allyTurn();
        Assertions.assertEquals(Math.min(f.ally.getMaxHp(), afterThird + perTick), f.ally.getCurrentHp(), 1.0,
                "…and a third time, because the Traces (行迹) Recuperation (调理) trace lengthens it by one turn");
        double afterFourth = f.ally.getCurrentHp();
        f.allyTurn();
        Assertions.assertEquals(afterFourth, f.ally.getCurrentHp(), 1.0, "…and then it is over");
    }

    /**
     * Note: A second cast <b>restarts</b> the regeneration instead of piling another one on: one tick's worth, not two.
     *
     * <p>This case exists because refresh is easy to miss: flipping
     * {@code RegenBuff.isSameKind} to {@code false} (i.e. letting them stack) leaves every assertion green (measured
     * mutation m2, 0 red). "同时目标每回合开始时为其回复…" describes one regeneration that a new cast restarts.
     */
    @Test
    public void aSecondCastRefreshesTheRegeneration() {
        Fixture f = new Fixture();
        // Note: A deep gap on purpose: at 50% the two casts' instant heals already fill the ally, the cap clamps both
        // expectations to max HP, and a STACKED pair would have passed this case too (measured: mutation m2 stayed 0 red).
        f.ally.takeDamage(f.ally.getMaxHp() * 0.9);
        double perTick = (0.072 * f.natasha.getMaxHp() + 192) * 1.1;

        f.castSkillOn(f.ally);
        f.castSkillOn(f.ally);

        double before = f.ally.getCurrentHp();
        f.allyTurn();
        Assertions.assertEquals(Math.min(f.ally.getMaxHp(), before + perTick), f.ally.getCurrentHp(), 1.0,
                "two casts, one regeneration: a stacked pair would settle twice in the same turn");
    }

    private static final class Fixture {
        private final Character natasha = CharacterFactory.create(NATASHA, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle = new Battle(List.of(natasha, ally), List.of(enemy), fixed());

        private Fixture() {
            battle.startBattle();
        }

        private void castSkillOn(Character target) {
            battle.castImmediate(natasha.getSkills().get(SkillType.SKILL), natasha, List.of(target));
        }

        /**
         * One full turn of the ally: the moment "目标每回合开始时" would name.
         */
        private void allyTurn() {
            battle.setCurrentMove(battle.queue().snapshot().stream()
                    .filter(signal -> signal.getCanHit() == ally)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no signal for the ally")));
            battle.beforeMove();
            battle.afterMove();
        }
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }

    /**
     * The healing the given healer computes for a fixed base amount.
     *
     * <p>Note: {@code Battle.heal} returns the HP <b>actually restored</b> and caps it at what is missing, so a first
     * version of this test damaged the target by half its HP and then compared two heals that were BOTH clipped to the
     * same 315 HP -- reading 1.0 and looking exactly like a boost that does not work. {@code calculateHeal} is the same
     * formula ({@code base  x  (1 + outgoing)  x  (1 + taken)}) without that cap, which is the part the trace changes.
     */
    private static double computedHeal(int healerCid) {
        Character healer = CharacterFactory.create(healerCid, LEVEL);
        Character target = CharacterFactory.create(TARGET_ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(healer, target), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        return battle.calculateHeal(healer, target, BASE_HEAL);
    }

    /**
     * trace (行迹) "医者": "娜塔莎<b>提供的</b>治疗量提高10%" - the provider's side, read from the healer.
     *
     * <p>The reader is {@code OUTGOING_HEALING_BOOST}, which {@code Battle.heal} takes from the unit applying the heal, so the
     * number has to show up as a ratio between two healers healing the same kind of target by the same base amount. The
     * upstream row (AvatarSkillTreeConfig, PointID 1105102) states {@code [0.1]}, which is where the 10% comes from.
     */
    @Test
    public void herTraceRaisesTheHealingSheProvidesByTenPercent() {
        double boosted = computedHeal(NATASHA);
        double plain = computedHeal(PLAIN);

        Assertions.assertTrue(plain > 0, "the control must compute something, or the ratio below means nothing");
        Assertions.assertEquals(1.1, boosted / plain, 1e-6,
                "\"the healing Natasha (娜塔莎) provides is raised by 10%\" (「娜塔莎提供的治疗量提高10%」) -- "
                        + "boosted " + boosted + " vs plain " + plain);
    }

    // ==================================================================
    // 天赋 生机焕发 -- the +50% bonus only on a recipient at 30% HP or lower
    // ==================================================================

    /**
     * A healthy ally gets no bonus; a low-HP ally gets +50%; and the threshold is inclusive, so exactly 30% shares it.
     *
     * <h2>Why this compares ratios rather than amounts</h2>
     * Her 行迹 医者 (+10% outgoing healing) is <b>also</b> a rule in the same table, so the "without the talent" side
     * is built by emptying the whole table and therefore loses the 医者 factor too. The two sides legitimately differ
     * by 1.1 before the talent is considered, and an absolute comparison would be measuring 医者 rather than the
     * talent.
     *
     * <p>The talent's own share is isolated by dividing the two ratios: measured, a low-HP ally gives
     * {@code 825/500 = 1.65 = 1.5 × 1.1} while a healthy one gives {@code 550/500 = 1.1}. So the bonus is exactly the
     * 1.5 the sentence states, and it is absent above the threshold.
     *
     * <p>⚠ Measured on {@code calculateHeal}, not on what lands: {@code heal} clamps to the recipient's missing HP,
     * and at 80% HP an ally is missing only ~316, so 500 and 825 would both land as 316 and no case below could tell
     * them apart.
     *
     * <p>One scenario, because all five readings come off the same fixture ({@code healed(registerTalent, fraction)}:
     * one battle with or without her table, one ally placed at a fraction of its HP) and observe the same object, the
     * heal Natasha computes. 「小于等于30%」 is the boundary of that one threshold: a `&lt;` instead of `&lt;=` would
     * pass the 20% and 80% readings and quietly drop the 30% one.
     */
    @Test
    public void theBonusAppliesAtOrBelowThirtyPercent() {
        double healthyWithout = healed(false, HEALTHY_FRACTION);
        double healthyWith = healed(true, HEALTHY_FRACTION);
        double lowWithout = healed(false, LOW_FRACTION);
        double lowWith = healed(true, LOW_FRACTION);
        double atThresholdWithout = healed(false, 0.3);
        double atThresholdWith = healed(true, 0.3);

        double healthyRatio = healthyWith / healthyWithout;
        double lowRatio = lowWith / lowWithout;

        Assertions.assertAll("天赋 生机焕发: 小于等于30%",
                // Precondition, so a zero on either control side cannot make a ratio look right.
                () -> Assertions.assertTrue(healthyWithout > 0 && lowWithout > 0,
                        "precondition: both heals compute to something"),
                // The claim, stated once: the talent's own share is exactly the 50% the sentence gives, and the side
                // that differs between the two ratios is the talent alone -- so dividing them out cancels 医者.
                () -> Assertions.assertEquals(1.5, lowRatio / healthyRatio, 0.02,
                        "the talent's own share -- the ratio of the ratios -- must be exactly 50%: "
                                + lowRatio + " / " + healthyRatio + " (raw heals " + lowWith + ", " + lowWithout
                                + ", " + healthyWith + ", " + healthyWithout + ")"),
                // The healthy side must carry NO talent. It does read 1.1, and that 1.1 is 医者 rather than the
                // talent -- the assertion is on it not being multiplied by 1.5, which is the whole difference.
                () -> Assertions.assertEquals(1.1, healthyRatio, 0.02,
                        "an ally above the threshold must get the 医者 trace and nothing else: "
                                + healthyWith + " vs " + healthyWithout),
                () -> Assertions.assertEquals(TALENT_TIMES_MEDIC, lowRatio, 0.02,
                        "「治疗量提高50%」 on an ally at 20% HP: " + lowWith + " vs " + lowWithout),
                () -> Assertions.assertEquals(TALENT_TIMES_MEDIC, atThresholdWith / atThresholdWithout, 0.05,
                        "「小于等于30%」 includes exactly 30%: " + atThresholdWith + " vs " + atThresholdWithout));
    }

    /**
     * A regeneration tick carries the bonus: 「该效果对持续治疗效果也会生效」.
     *
     * <p>The engine applies it for free because {@code Battle.tickRegens} heals from {@code regen.getSource()},
     * the original applier -- but "for free" is a claim about the code, and this is the case that would go red if
     * somebody changed the tick to heal from the recipient instead.
     */
    @Test
    public void aRegenerationTickCarriesTheBonusToo() {
        double without = regenerationTick(false);
        double with = regenerationTick(true);

        Assertions.assertTrue(without > 0, "precondition: the tick heals");
        Assertions.assertEquals(TALENT_TIMES_MEDIC, with / without, 0.05,
                "the regeneration tick must carry the same +50%: " + with + " vs " + without);
    }

    /**
     * Measures the heal Natasha <b>computes</b> for an ally at the given HP fraction.
     *
     * <p>⚠ <b>Why {@code calculateHeal} and not {@code heal}.</b> {@code heal} returns what actually landed, which
     * {@code CanHit.heal} clamps to the recipient's missing HP. At 80% HP an ally is missing only ~316, so a bonus of
     * 500 and one of 825 both land as 316 and are indistinguishable -- the first version of this test measured the
     * clamped number and therefore could not detect an unconditional bonus at all. The computed amount is the
     * quantity the talent scales, so it is what the cases must compare.
     *
     * @param registerTalent whether her talent rule is in effect
     * @param allyHpFraction the ally's HP as a fraction of its maximum
     * @return the healing amount as computed, before the cap
     */
    private static double healed(boolean registerTalent, double allyHpFraction) {
        Character natasha = CharacterFactory.create(NATASHA, LEVEL, false, null, null, 0);
        Character ally = CharacterFactory.create(1109, LEVEL, false, null, null, 0);
        positionAt(ally, allyHpFraction);

        Battle battle = talentBattle(natasha, List.of(natasha, ally), registerTalent);
        return battle.calculateHeal(natasha, ally, 500);
    }

    /**
     * Runs one real regeneration tick on a low-HP ally and returns the HP it restored.
     *
     * <p>The regeneration is attached by hand rather than through her Skill, so this measures the <b>tick's</b>
     * healing path -- which is the path 「该效果对持续治疗效果也会生效」 is about -- and not her Skill's data row.
     *
     * <p>Unlike the cases above this can use the landed amount, because the ally is placed low enough that the
     * missing-HP clamp is not reached: at {@link #LOW_FRACTION} it is missing far more than the heal computes to, so
     * what lands <b>is</b> the computed amount. {@code tickRegens} also reports what it really healed, which is the
     * number that would silently stay flat if the tick healed from the recipient rather than the applier.
     *
     * @param registerTalent whether her talent rule is in effect
     * @return the HP the ally gained from one tick
     */
    private static double regenerationTick(boolean registerTalent) {
        Character natasha = CharacterFactory.create(NATASHA, LEVEL, false, null, null, 0);
        Character ally = CharacterFactory.create(1109, LEVEL, false, null, null, 0);

        Battle battle = talentBattle(natasha, List.of(natasha, ally), registerTalent);
        ally.getBuffManager().addBuff(
                new com.laosun.aluminium.buff.RegenBuff(natasha, "持续治疗", 500, 2));
        positionAt(ally, LOW_FRACTION);

        return battle.tickRegens(ally);
    }

    /**
     * Builds the battle, optionally without her talent rule.
     *
     * <p>Removing it is done by emptying her table, so the comparison isolates this one rule: everything else she
     * carries (traces, the level convention) is identical on both sides.
     *
     * @param natasha          the healer
     * @param party            the party
     * @param registerTalent   whether to keep her table
     * @return a started battle
     */
    private static Battle talentBattle(Character natasha, List<Character> party, boolean registerTalent) {
        Battle battle = new Battle(party, List.of(EnemyFactory.create(MONSTER, LEVEL, 1)),
                BattleFixture.alwaysRoll(1.0));
        if (!registerTalent) {
            natasha.setTriggerTable(new com.laosun.aluminium.trigger.TriggerTable(NATASHA, List.of()));
        }
        battle.startBattle();
        return battle;
    }

    /**
     * Puts a unit at a fraction of its maximum HP.
     *
     * <p>By consuming the difference rather than healing down, so the result is exact; {@code consumeHp} is the
     * engine's own "pay HP as a price" and does not fire damage events.
     *
     * @param unit     the unit to place
     * @param fraction the target fraction of its maximum HP
     */
    private static void positionAt(Character unit, double fraction) {
        double want = unit.getMaxHp() * fraction;
        double excess = unit.getCurrentHp() - want;
        if (excess > 0) {
            unit.consumeHp(excess);
        } else if (excess < 0) {
            unit.heal(-excess);
        }
    }

    // ==================================================================
    // 秘技 催眠研习 -- one enemy struck, every enemy Weakened
    // ==================================================================

    /**
     * Every enemy is Weakened, and the gate is what did it.
     *
     * <p>The two readings are the same board with and without the technique -- {@code techniqueBattle(true)} against
     * {@code techniqueBattle(false)} -- so the gate ({@code self has_state sys:technique}) is measured rather than
     * assumed: the Weaken, not the sentence alone, is what has to disappear.
     */
    @Test
    public void everyEnemyIsWeakenedWhenTheTechniqueWasUsed() {
        Battle with = techniqueBattle(true);
        Battle without = techniqueBattle(false);
        with.startBattle();
        without.startBattle();

        Assertions.assertAll("「使敌方每个单体目标陷入虚弱状态」 and its technique gate",
                () -> Assertions.assertEquals(3, weakened(with),
                        "「使敌方每个单体目标陷入虚弱状态」 -- all three enemies, not just the one hit"),
                () -> Assertions.assertEquals(0, weakened(without),
                        "without the technique the same battle must weaken nobody -- the `self has_state sys:technique` "
                                + "gate is why it happened"));
    }

    /**
     * The strike hits exactly ONE enemy, every enemy is Weakened, and the reduction is the number the sentence states.
     *
     * <p>The sentence uses two different selectors: 「随机单体」 is ONE enemy while 「每个单体目标」 is EVERY enemy, so
     * the damage is dealt to one, exactly one enemy must have lost HP, and all three carry the state.
     *
     * <p>The Weaken is asserted on {@code ALL_DAMAGE_TYPE_BOOST} rather than on the {@code 虚弱} buff, because the buff
     * is a name without a value: a rule that applied only the buff would show the state and change no number, and a case
     * that checked for the state would pass.
     *
     * <p>The damage itself is measured as "somebody lost HP" rather than as a figure: the amount that lands is
     * post-mitigation, and pinning it here would pin the defence formula rather than this rule.
     *
     * <p>One scenario, because all three readings come off the same fixture ({@code techniqueBattle(true)}: three
     * monsters with no resistances, the technique released) and observe the same object, the enemy board after the
     * battle starts.
     */
    @Test
    public void theTechniqueStrikesOneEnemyAndWeakensEveryEnemy() {
        Battle battle = techniqueBattle(true);
        battle.startBattle();

        Assertions.assertAll("秘技 催眠研习: 一个单体被击中，每个单体目标虚弱",
                () -> {
                    for (Enemy enemy : battle.enemyUnits()) {
                        Assertions.assertEquals(-0.3, enemy.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                                "「虚弱状态下的敌方目标对我方造成的伤害降低30%」 on " + enemy.getName()
                                        + " -- the buff alone would carry no number");
                    }
                },
                () -> {
                    int hurt = 0;
                    for (Enemy enemy : battle.enemyUnits()) {
                        if (enemy.getCurrentHp() < enemy.getMaxHp()) {
                            hurt++;
                        }
                    }
                    Assertions.assertEquals(1, hurt,
                            "「对敌方随机单体造成…物理属性伤害」 -- one enemy, while the Weaken goes to every enemy");
                },
                () -> Assertions.assertTrue(battle.enemyUnits().stream().anyMatch(e -> e.getCurrentHp() < e.getMaxHp()),
                        "the technique's damage half must deal damage"));
    }

    /**
     * Three freezable-in-no-way monsters with no resistances, at the given technique state.
     *
     * @param withTechnique whether the battle is told the technique was used
     * @return the battle, not started
     */
    private static Battle techniqueBattle(boolean withTechnique) {
        Character natasha = CharacterFactory.create(NATASHA, LEVEL, false, null, null, 0);
        List<Enemy> enemies = List.of(
                EnemyFactory.create(TECHNIQUE_MONSTER, LEVEL, 1),
                EnemyFactory.create(TECHNIQUE_MONSTER, LEVEL, 1),
                EnemyFactory.create(TECHNIQUE_MONSTER, LEVEL, 1));

        Battle battle = new Battle(List.of(natasha), enemies, BattleFixture.alwaysRoll(0.0),
                withTechnique ? Battle.Techniques.released(NATASHA) : null);
        return battle;
    }

    /**
     * How many of the battle's enemies carry the {@code 虚弱} state.
     *
     * @param battle the battle
     * @return the count
     */
    private static int weakened(Battle battle) {
        int n = 0;
        for (Enemy enemy : battle.enemyUnits()) {
            if (enemy.getBuffManager().hasState("debuff:weakened")) {
                n++;
            }
        }
        return n;
    }
}
